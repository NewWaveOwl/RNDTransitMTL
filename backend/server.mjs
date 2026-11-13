import http from 'node:http';
import { pathToFileURL, fileURLToPath } from 'node:url';
import { RandomPointGenerator } from './candidates.mjs';
import { createOtpClient, ProviderError } from './otp.mjs';
import { TripGenerationService, InputError } from './generation.mjs';
import { loadFareZones, loadRouteCatalogue, lineResolver, itineraryPolicy, validateRestrictions } from './restrictions.mjs';

/** Local by default. Hosted deployments require Supabase authentication and HTTPS termination. */
export function createServer({ generation, authorize = async () => true, maxConcurrent = 4 }) {
  let active = 0;
  const send = (res, code, data) => {
    if (res.destroyed) return;
    res.writeHead(code, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
    res.end(JSON.stringify(data));
  };
  return http.createServer(async (req, res) => {
    if (req.url === '/health' && req.method === 'GET') return send(res, 200, { status: 'ready' });
    if (req.url !== '/v1/trips/generate' || req.method !== 'POST') return send(res, 404, { status: 'not_found' });
    if (active >= maxConcurrent) return send(res, 429, { status: 'unavailable', message: 'Trip planner is busy. Try again.' });
    active++;
    const cancelled = new AbortController();
    res.on('close', () => { if (!res.writableEnded) cancelled.abort(new Error('Client disconnected.')); });
    try {
      if (!(await authorize(req.headers.authorization, cancelled.signal))) return send(res, 401, { status: 'unauthorized' });
      if (!req.headers['content-type']?.startsWith('application/json')) throw new InputError('Send application/json.');
      const chunks = [];
      let bytes = 0;
      for await (const chunk of req) {
        bytes += chunk.length;
        if (bytes > 16_384) throw new InputError('Request is too large.');
        chunks.push(chunk);
      }
      let input;
      try { input = JSON.parse(Buffer.concat(chunks).toString('utf8')); }
      catch { throw new InputError('Invalid JSON request.'); }
      send(res, 200, await generation.generate(input, cancelled.signal));
    } catch (error) {
      if (cancelled.signal.aborted) return;
      if (error instanceof InputError) send(res, 400, { status: 'invalid_input', message: error.message });
      else send(res, 503, { status: 'unavailable', message: 'Routing service unavailable. Try again.' });
    } finally { active--; }
  });
}

export function supabaseAuthorizer(url, publishableKey, fetchImpl = fetch) {
  const endpoint = new URL('/auth/v1/user', url);
  if (endpoint.protocol !== 'https:') throw new Error('SUPABASE_URL must use HTTPS.');
  return async (authorization, signal) => {
    if (!authorization?.startsWith('Bearer ')) return false;
    const response = await fetchImpl(endpoint, {
      headers: { Authorization: authorization, apikey: publishableKey },
      signal: AbortSignal.any([signal, AbortSignal.timeout(5000)]),
    });
    if (response.status >= 500) throw new ProviderError('Authentication service unavailable.');
    return response.ok;
  };
}

async function main() {
  const endpoint = process.env.OTP_URL ?? 'http://127.0.0.1:18086/otp/gtfs/v1';
  const catalogueEndpoint = process.env.OTP_CATALOGUE_URL ?? endpoint;
  const host = process.env.HOST ?? '127.0.0.1';
  const port = Number(process.env.PORT ?? 18088);
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid PORT.');
  const authenticated = Boolean(process.env.SUPABASE_URL && process.env.SUPABASE_PUBLISHABLE_KEY);
  if (!['127.0.0.1', '::1', 'localhost'].includes(host) && !authenticated) {
    throw new Error('Public binding requires SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY.');
  }
  const [fareZones, routes] = await Promise.all([
    loadFareZones(process.env.FARE_ZONES_FILE ?? fileURLToPath(new URL('./data/fare-zones-2026.geojson', import.meta.url))),
    loadRouteCatalogue(catalogueEndpoint),
  ]);
  const resolveLines = lineResolver(routes);
  const generation = new TripGenerationService({
    candidates: new RandomPointGenerator(), route: createOtpClient(endpoint),
    prepare: input => { validateRestrictions(input); return resolveLines(input); },
    accept: itineraryPolicy(fareZones, resolveLines),
  });
  const server = createServer({ generation, authorize: authenticated ?
    supabaseAuthorizer(process.env.SUPABASE_URL, process.env.SUPABASE_PUBLISHABLE_KEY) : undefined });
  server.requestTimeout = 20_000;
  server.headersTimeout = 10_000;
  server.listen(port, host, () => console.log(`Trip generation ready at http://${host}:${port} (random geographic destinations).`));
  for (const name of ['SIGTERM', 'SIGINT']) process.on(name, () => server.close());
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch(error => { console.error(error.message); process.exitCode = 1; });
}
