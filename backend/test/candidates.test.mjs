import test from 'node:test';
import assert from 'node:assert/strict';
import { RandomPointGenerator } from '../candidates.mjs';
import { center, distance, inPolygon } from '../geo.mjs';

function seeded(seed) { return () => ((seed = (1664525 * seed + 1013904223) >>> 0) / 2 ** 32); }
const pool = Array.from({ length: 30 }, (_, i) => ({ id: String(i), label: `Stop ${i}`, point: {
  latitude: center.latitude + (i + 1) * .002, longitude: center.longitude,
} }));

test('seeded randomness creates reproducible bounded and separated candidates', () => {
  const generate = () => new RandomPointGenerator([...pool, pool[3], { point: { latitude: 0, longitude: 0 } }], { random: seeded(7) })
    .generate(center, { limit: 8, maxDistanceMeters: 3000 });
  assert.deepEqual(generate(), generate());
  const points = generate().map(it => it.point);
  assert.equal(points.length, 8);
  assert.ok(points.every(point => distance(center, point) >= 250 && distance(center, point) <= 3000));
  assert.ok(points.every((point, i) => points.slice(i + 1).every(other => distance(point, other) >= 100)));
});

test('same origin can produce distinct candidates and exclude previous proposals', () => {
  const generator = new RandomPointGenerator(pool, { random: seeded(11) });
  const first = generator.generate(center, { limit: 1 })[0];
  const next = generator.generate(center, { limit: 1, excluded: [first.point] })[0];
  assert.ok(distance(first.point, next.point) >= 100);
});

test('polygon holes, unusable pools, and invalid coordinates do not produce candidates', () => {
  const polygon = { type: 'Polygon', coordinates: [
    [[-74,45],[-73,45],[-73,46],[-74,46],[-74,45]],
    [[-73.7,45.4],[-73.5,45.4],[-73.5,45.6],[-73.7,45.6],[-73.7,45.4]],
  ] };
  assert.equal(inPolygon(center, polygon), false);
  assert.deepEqual(new RandomPointGenerator(pool, { polygon }).generate(center), []);
  assert.throws(() => new RandomPointGenerator(pool).generate({ latitude: NaN, longitude: 0 }));
});
