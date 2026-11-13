import test from 'node:test';
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { Readable } from 'node:stream';
import { createServer, supabaseAuthorizer } from '../server.mjs';
import { InputError } from '../generation.mjs';

// Exercise the real HTTP handler with Node request streams; no external services or sockets required.
function call(server, { method = 'POST', url = '/v1/trips/generate', body = '{}', headers = {} } = {}) {
  const req = Readable.from([Buffer.from(body)]);
  Object.assign(req, { method, url, headers: { 'content-type': 'application/json', ...headers } });
  const res = new EventEmitter();
  const result = new Promise(resolve => {
    res.writeHead = code => { res.code = code; };
    res.end = text => { res.writableEnded = true; resolve({ code: res.code, body: JSON.parse(text) }); };
  });
  server.emit('request', req, res);
  return { result, disconnect: () => res.emit('close') };
}

test('HTTP API forwards one request and returns the accepted result', async () => {
  let calls = 0;
  const server = createServer({ generation: { generate: async input => { calls++; return { status: 'success', origin: input.origin }; } } });
  const result = await call(server, { body: '{"origin":"exact pin"}' }).result;
  assert.deepEqual(result, { code: 200, body: { status: 'success', origin: 'exact pin' } });
  assert.equal(calls, 1);
  assert.equal((await call(server, { method: 'GET', url: '/health' }).result).code, 200);
});

test('invalid JSON, oversized input, authentication and provider outages have distinct outcomes', async () => {
  const noProviderCall = createServer({ generation: { generate: async () => { throw new InputError('Invalid origin'); } } });
  assert.equal((await call(noProviderCall, { body: '{broken' }).result).code, 400);
  assert.equal((await call(noProviderCall, { body: 'x'.repeat(17_000) }).result).code, 400);
  assert.equal((await call(noProviderCall).result).body.message, 'Invalid origin');
  assert.equal((await call(createServer({ generation: {}, authorize: async () => false })).result).code, 401);
  const offline = createServer({ generation: { generate: async () => { throw new Error('Secret provider details'); } } });
  const failure = await call(offline).result;
  assert.equal(failure.code, 503);
  assert.ok(!JSON.stringify(failure).includes('Secret'));
});

test('disconnect cancels work and the concurrency cap rejects overlapping requests', async () => {
  let signal;
  const server = createServer({ maxConcurrent: 1, generation: { generate: async (_, value) => {
    signal = value;
    return new Promise((resolve, reject) => value.addEventListener('abort', () => reject(value.reason), { once: true }));
  } } });
  const first = call(server);
  await new Promise(resolve => setImmediate(resolve));
  assert.equal((await call(server).result).code, 429);
  first.disconnect();
  assert.equal(signal.aborted, true);
});

test('hosted authentication verifies a Supabase token without embedding it in a URL', async () => {
  const authorize = supabaseAuthorizer('https://example.supabase.co', 'publishable', async (url, options) => {
    assert.equal(url.href, 'https://example.supabase.co/auth/v1/user');
    assert.equal(options.headers.Authorization, 'Bearer user-token');
    return { ok: true, status: 200 };
  });
  assert.equal(await authorize(undefined, new AbortController().signal), false);
  assert.equal(await authorize('Bearer user-token', new AbortController().signal), true);
});
