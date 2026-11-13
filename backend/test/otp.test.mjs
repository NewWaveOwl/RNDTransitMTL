import test from 'node:test';
import assert from 'node:assert/strict';
import { otpRequest, createOtpClient, ProviderError } from '../otp.mjs';

const request = {
  origin: { latitude: 45.5, longitude: -73.6 }, destination: { latitude: 45.51, longitude: -73.59 },
  modes: ['bus', 'metro', 'walk'], departure: '2026-11-12T18:30:00-05:00',
};

test('arbitrary coordinates, departure and allowed modes reach OTP', () => {
  const { query, variables } = otpRequest(request);
  assert.equal(variables.originLat, 45.5);
  assert.equal(variables.destinationLon, -73.59);
  assert.equal(variables.departure, request.departure);
  assert.deepEqual(variables.modes.transit.transit, [{ mode: 'BUS' }, { mode: 'SUBWAY' }]);
  assert.deepEqual(variables.modes.transit.access, ['WALK']);
  assert.equal(variables.modes.transitOnly, false);
  assert.ok(!query.includes('stopLocationId'));
  assert.ok(query.replaceAll(/\s/g, '').includes('excludeRealTimeUpdates:$excludeRealtime'));
  assert.equal(variables.excludeRealtime, false);
});

test('street-only search does not force transit', () => {
  const { variables } = otpRequest({ ...request, modes: ['bike'] });
  assert.equal(variables.modes.directOnly, true);
  assert.deepEqual(variables.modes.direct, ['BICYCLE']);
});

test('provider failure is distinct from an empty valid itinerary result', async () => {
  await assert.rejects(createOtpClient('http://localhost/otp', async () => ({ ok: false, status: 503 }))(request), ProviderError);
  const body = { data: { planConnection: { edges: [], routingErrors: [] } } };
  assert.deepEqual(await createOtpClient('http://localhost/otp', async () => ({ ok: true, json: async () => body }))(request), body);
});
