import test from 'node:test';
import assert from 'node:assert/strict';
import { TripGenerationService, InputError } from '../generation.mjs';
import { ProviderError } from '../otp.mjs';

const now = Date.parse('2026-11-12T18:00:00-05:00');
const input = { origin: { label: 'Start', point: { latitude: 45.5, longitude: -73.6 } }, plannedMinutes: 30, modes: ['walk'] };
const candidates = { generate: () => [1, 2, 3].map(id => ({ label: `Stop ${id}`, point: { latitude: 45.51 + id / 1000, longitude: -73.6 } })) };
const response = (arrivalMinutes, duration = 600) => ({ data: { planConnection: { routingErrors: [], edges: [{ node: {
  duration, legs: [{ mode: 'WALK', end: { scheduledTime: new Date(now + arrivalMinutes * 60_000).toISOString() } }],
} }] } } });
const service = options => new TripGenerationService({ candidates, now: () => now, ...options });

test('skips no-route and over-budget candidates, then accepts a reachable itinerary', async () => {
  let calls = 0;
  const planner = service({ route: async () => [
    { data: { planConnection: { routingErrors: [{ code: 'NO_TRANSIT_CONNECTION' }], edges: [] } } }, response(31), response(22),
  ][calls++] });
  const result = await planner.generate(input);
  assert.equal(result.status, 'success');
  assert.equal(result.attempts, 3);
  assert.equal(result.elapsedSeconds, 22 * 60);
  assert.deepEqual(result.origin, input.origin);
  assert.equal(result.otp.data.planConnection.edges.length, 1);
});

test('initial waiting counts even when OTP itinerary duration is short', async () => {
  assert.equal((await service({ route: async () => response(40, 300) }).generate(input)).status, 'no_trip');
});

test('preserves an OSM origin reference and discards client-injected routing controls', async () => {
  const origin = { ...input.origin, osmReference: { type: 'Node', id: 12345 } };
  const result = await service({ route: async request => {
    assert.equal(request.filters, undefined);
    assert.equal(request.allowedRouteIds, undefined);
    return response(20);
  } }).generate({ ...input, origin, filters: ['injected'], allowedRouteIds: ['injected'], maxAttempts: 100000 });
  assert.deepEqual(result.origin, origin);
});

test('caps candidate attempts and distinguishes provider failure', async () => {
  const result = await service({ maxAttempts: 2, candidates: { generate: (_, options) => candidates.generate().slice(0, options.limit) },
    route: async () => response(40) }).generate(input);
  assert.equal(result.attempts, 2);
  await assert.rejects(service({ route: async () => { throw new Error('Network failed'); } }).generate(input), ProviderError);
  await assert.rejects(service({ route: async () => response(10) }).generate({ ...input, modes: ['rem'] }), InputError);
});

test('cancellation aborts the in-flight OTP request and stops generation', async () => {
  const controller = new AbortController();
  let calls = 0;
  const promise = service({ route: async (_, signal) => {
    calls++;
    controller.abort(new Error('User cancelled'));
    signal.throwIfAborted();
  } }).generate(input, controller.signal);
  await assert.rejects(promise, /User cancelled/);
  assert.equal(calls, 1);
});

test('elapsed-time cap stops an unresponsive provider', async () => {
  const keepAlive = setInterval(() => {}, 1000);
  try {
    const result = await service({ timeoutMillis: 10, route: (_, signal) => new Promise((resolve, reject) => {
      signal.addEventListener('abort', () => reject(signal.reason), { once: true });
    }) }).generate(input);
    assert.equal(result.status, 'no_trip');
    assert.match(result.message, /timed out/);
  } finally { clearInterval(keepAlive); }
});
