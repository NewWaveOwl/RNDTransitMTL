import test from 'node:test';
import assert from 'node:assert/strict';
import { RandomPointGenerator } from '../candidates.mjs';
import { center, distance, inServiceArea, inPolygon } from '../geo.mjs';
function seeded(seed) { return () => ((seed = (1664525 * seed + 1013904223) >>> 0) / 2 ** 32); }

test('seeded geographic samples need no stop or POI catalogue and remain bounded and distinct', () => {
  const generate = () => new RandomPointGenerator({ random: seeded(7) }).generate(center, { limit: 16, maxDistanceMeters: 3000 });
  assert.deepEqual(generate(), generate());
  const points = generate().map(it => it.point);
  assert.equal(points.length, 16);
  assert.ok(points.every(point => distance(center, point) >= 250 && distance(center, point) <= 3000 && inServiceArea(point)));
  assert.ok(points.every((point, i) => points.slice(i + 1).every(other => distance(point, other) >= 100)));
});
test('samples a park interior without requiring a nearby transit stop', () => {
  const origin = { latitude: 45.56, longitude: -73.57 };
  const polygon = { type: 'Polygon', coordinates: [[[-73.58,45.55],[-73.56,45.55],[-73.56,45.57],[-73.58,45.57],[-73.58,45.55]]] };
  const points = new RandomPointGenerator({ random: seeded(12), polygon }).generate(origin, { limit: 8, maxDistanceMeters: 500 });
  assert.equal(points.length, 8);
  assert.ok(points.every(candidate => inPolygon(candidate.point, polygon)));
});
test('same origin can produce different destinations and exclude previous proposals', () => {
  const generator = new RandomPointGenerator({ random: seeded(11) });
  const first = generator.generate(center, { limit: 1 })[0];
  const next = generator.generate(center, { limit: 1, excluded: [first.point] })[0];
  assert.ok(distance(first.point, next.point) >= 100);
});
test('polygon holes and impossible radii terminate within a fixed sampling limit', () => {
  let draws = 0;
  const polygon = { type: 'Polygon', coordinates: [
    [[-74,45],[-73,45],[-73,46],[-74,46],[-74,45]],
    [[-73.7,45.4],[-73.5,45.4],[-73.5,45.6],[-73.7,45.6],[-73.7,45.4]],
  ] };
  const generator = new RandomPointGenerator({ random: () => { draws++; return .5; }, polygon });
  assert.deepEqual(generator.generate(center, { limit: 2, maxDistanceMeters: 1000 }), []);
  assert.equal(draws, 2 * 64 * 2);
  assert.deepEqual(generator.generate(center, { maxDistanceMeters: 10 }), []);
  assert.throws(() => generator.generate({ latitude: NaN, longitude: 0 }));
});
