import test from 'node:test';
import assert from 'node:assert/strict';
import { FareZones, itineraryPolicy, lineResolver, validateRestrictions, loadRouteCatalogue } from '../restrictions.mjs';

const origin = { latitude: 45.5, longitude: -73.6 };
const end = { latitude: 45.51, longitude: -73.6 };
const input = { origin: { point: origin }, modes: ['bus'], zonesByTransport: { bus: ['A'] } };

test('catalogue startup failures identify the endpoint and preserve the network cause', async () => {
  const endpoint = 'http://127.0.0.1:61719/otp/gtfs/v1';
  const cause = new TypeError('fetch failed', { cause: Object.assign(new Error('connect refused'), { code: 'ECONNREFUSED' }) });
  await assert.rejects(loadRouteCatalogue(endpoint, async () => { throw cause; }), error => {
    assert.ok(error.message.includes(endpoint));
    assert.match(error.message, /Start-StmRealtimeStack.ps1/);
    assert.equal(error.cause, cause);
    return true;
  });
  await assert.rejects(loadRouteCatalogue(endpoint, async () => ({ ok: false, status: 503 })), error => {
    assert.match(error.cause.message, /HTTP 503/);
    return true;
  });
});
export function encode(points) {
  let lat = 0, lon = 0;
  function delta(value) {
    let v = value < 0 ? ~(value << 1) : value << 1, text = '';
    while (v >= 32) { text += String.fromCharCode((32 | (v & 31)) + 63); v >>>= 5; }
    return text + String.fromCharCode(v + 63);
  }
  return points.map(point => {
    const a = Math.round(point.latitude * 1e5), b = Math.round(point.longitude * 1e5);
    const text = delta(a - lat) + delta(b - lon); lat = a; lon = b; return text;
  }).join('');
}
function itinerary(points = [origin, end], mode = 'BUS') {
  return { legs: [{ mode, duration: 600, distance: 1200, route: { gtfsId: 'STM:55' },
    legGeometry: { length: points.length, points: encode(points) } }] };
}

test('checks intermediate zones, not just endpoints of a transit leg', () => {
  const zones = { allows: point => point.latitude < 45.503 || point.latitude > 45.507 };
  assert.equal(itineraryPolicy(zones)(itinerary(), input, { point: end }), false);
  assert.equal(itineraryPolicy({ allows: () => true })(itinerary(), input, { point: end }), true);
});

test('rejects unselected modes, unknown zones, and disconnected geometry', () => {
  const accept = itineraryPolicy({ allows: () => true });
  assert.equal(accept(itinerary(undefined, 'RAIL'), input, { point: end }), false);
  assert.equal(itineraryPolicy(null)(itinerary(), input, { point: end }), false);
  assert.equal(accept(itinerary([{ latitude: 45.6, longitude: -73.6 }, end]), input, { point: end }), false);
  assert.throws(() => validateRestrictions({ ...input, zonesByTransport: { bus: ['1'] } }));
});

test('line restrictions map catalogue choices and preserve other enabled modes', () => {
  const resolve = lineResolver([
    { mode: 'SUBWAY', gtfsId: 'STM:2', shortName: '2' },
    { mode: 'BUS', gtfsId: 'STM:55', shortName: '55' },
    { mode: 'SUBWAY', gtfsId: 'STM:1', shortName: '1' },
  ]);
  const resolved = resolve({ modes: ['metro', 'bus'], selectedRouteIds: ['metro:Orange'] });
  assert.deepEqual(resolved.allowedRouteIds.sort(), ['STM:2', 'STM:55']);
  assert.throws(() => resolve({ modes: ['metro'], selectedRouteIds: ['metro:Missing'] }));
});

test('official polygon loader refuses absent or ambiguous zone labels', () => {
  assert.throws(() => new FareZones({ type: 'FeatureCollection', features: [] }));
  const collection = { type: 'FeatureCollection', features: ['A','B','C','D'].map(zone => ({
    properties: { zone }, geometry: { type: 'Polygon', coordinates: [
      [[-74,45],[-73,45],[-73,46],[-74,46],[-74,45]],
    ] },
  })) };
  assert.deepEqual(new FareZones(collection).at(origin), ['A','B','C','D']);
});
