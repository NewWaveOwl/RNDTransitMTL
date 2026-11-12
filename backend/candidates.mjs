import { geoPoint, distance, inServiceArea, inPolygon } from './geo.mjs';
import { ProviderError } from './otp.mjs';

/** Random GeoPoint candidates from public transit stops; OTP still verifies pedestrian access. */
export class RandomPointGenerator {
  constructor(destinations, { random = Math.random, polygon = null } = {}) {
    this.random = random;
    this.polygon = polygon;
    this.destinations = destinations.filter(candidate => {
      try { return inServiceArea(geoPoint(candidate.point)) && inPolygon(candidate.point, polygon); }
      catch { return false; }
    });
  }

  /** Finite, deduplicated pool; faraway destinations cannot consume all the routing attempts. */
  generate(origin, { limit = 32, minSeparationMeters = 250, maxDistanceMeters = 50_000, excluded = [] } = {}) {
    geoPoint(origin);
    const pool = this.destinations.filter(({ point }) => {
      const separation = distance(origin, point);
      return separation >= minSeparationMeters && separation <= maxDistanceMeters &&
        excluded.every(other => distance(point, other) >= 100);
    });
    const result = [];
    for (let i = 0; i < pool.length && result.length < limit; i++) {
      const index = i + Math.min(pool.length - i - 1, Math.floor(this.random() * (pool.length - i)));
      [pool[i], pool[index]] = [pool[index], pool[i]];
      if (result.every(other => distance(other.point, pool[i].point) >= 100)) result.push(pool[i]);
    }
    return result;
  }
}

/** Read once at startup from the graph actually serving routes. No client-supplied destinations. */
export async function loadDestinations(endpoint, { signal, fetchImpl = fetch } = {}) {
  const response = await fetchImpl(endpoint, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, signal,
    body: JSON.stringify({ query: 'query DestinationPool { stops { gtfsId name lat lon zoneId } }' }),
  });
  if (!response.ok) throw new ProviderError('Could not load the OTP destination pool.');
  const body = await response.json();
  if (body.errors?.length || !Array.isArray(body.data?.stops)) throw new ProviderError('OTP omitted the stop catalogue.');
  return body.data.stops.map(stop => ({
    id: stop.gtfsId, label: stop.name, zoneId: stop.zoneId,
    point: { latitude: stop.lat, longitude: stop.lon },
  }));
}
