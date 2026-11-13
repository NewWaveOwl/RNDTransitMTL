import { geoPoint, distance, inServiceArea, inPolygon, destinationPoint } from './geo.mjs';

/** Random WGS84 destinations anywhere in the search area, including parks; OTP validates access. */
export class RandomPointGenerator {
  constructor({ random = Math.random, polygon = null } = {}) {
    Object.assign(this, { random, polygon });
  }

  /** Uniform-area samples in a geographic annulus, with bounded rejection for holes and exclusions. */
  generate(origin, { limit = 32, minSeparationMeters = 250, maxDistanceMeters = 50_000, excluded = [] } = {}) {
    geoPoint(origin);
    if (!Number.isInteger(limit) || limit < 1 || limit > 128 || !Number.isFinite(maxDistanceMeters) ||
        !Number.isFinite(minSeparationMeters) || minSeparationMeters < 0 || maxDistanceMeters < minSeparationMeters) return [];
    const result = [];
    const earthRadius = 6_371_008.8;
    const inner = Math.cos(minSeparationMeters / earthRadius);
    const outer = Math.cos(maxDistanceMeters / earthRadius);
    for (let draws = 0; draws < limit * 64 && result.length < limit; draws++) {
      const radius = earthRadius * Math.acos(Math.max(-1, Math.min(1, inner - this.random() * (inner - outer))));
      const point = destinationPoint(origin, radius, this.random() * 2 * Math.PI);
      if (!inServiceArea(point) || !inPolygon(point, this.polygon) ||
          excluded.some(other => distance(point, other) < 100) ||
          result.some(other => distance(point, other.point) < 100)) continue;
      result.push({ label: 'Random destination', point });
    }
    return result;
  }
}
