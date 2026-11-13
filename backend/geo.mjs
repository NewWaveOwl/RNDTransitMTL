/** WGS84 points and the same Montréal coverage circle used by the Android picker. */
const earthRadius = 6_371_008.8;
const rad = degrees => degrees * Math.PI / 180;
export function geoPoint(value) {
  if (!value || !Number.isFinite(value.latitude) || !Number.isFinite(value.longitude) ||
      Math.abs(value.latitude) > 90 || Math.abs(value.longitude) > 180) {
    throw new TypeError('A valid GeoPoint is required.');
  }
  return { latitude: value.latitude, longitude: value.longitude };
}
export function distance(a, b) {
  const h = Math.sin(rad(b.latitude - a.latitude) / 2) ** 2 +
    Math.cos(rad(a.latitude)) * Math.cos(rad(b.latitude)) * Math.sin(rad(b.longitude - a.longitude) / 2) ** 2;
  return 2 * earthRadius * Math.asin(Math.sqrt(Math.min(1, h)));
}
export const center = { latitude: 45.5019, longitude: -73.5673 };
/** Great-circle destination; bearing is radians clockwise from north. */
export function destinationPoint(origin, meters, bearing) {
  const angular = meters / earthRadius;
  const lat = rad(origin.latitude), lon = rad(origin.longitude);
  const latitude = Math.asin(Math.sin(lat) * Math.cos(angular) + Math.cos(lat) * Math.sin(angular) * Math.cos(bearing));
  const longitude = lon + Math.atan2(Math.sin(bearing) * Math.sin(angular) * Math.cos(lat),
    Math.cos(angular) - Math.sin(lat) * Math.sin(latitude));
  return geoPoint({ latitude: latitude * 180 / Math.PI, longitude: ((longitude * 180 / Math.PI + 540) % 360) - 180 });
}
export const radiusMeters = distance(center, { latitude: 45.98474970, longitude: -73.18629259 }) + 5_000;
export const inServiceArea = point => distance(center, point) <= radiusMeters;

/** Optional supported GeoJSON Polygon/MultiPolygon, including holes. Boundaries are included. */
export function inPolygon(point, geometry) {
  if (!geometry) return true;
  const inRing = ring => {
    let inside = false;
    const x = point.longitude, y = point.latitude;
    for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
      const [xi, yi] = ring[i], [xj, yj] = ring[j];
      const cross = (x - xi) * (yj - yi) - (y - yi) * (xj - xi);
      if (Math.abs(cross) < 1e-12 && x >= Math.min(xi, xj) && x <= Math.max(xi, xj) &&
          y >= Math.min(yi, yj) && y <= Math.max(yi, yj)) return true;
      if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside;
    }
    return inside;
  };
  const polygons = geometry.type === 'Polygon' ? [geometry.coordinates] : geometry.coordinates;
  return polygons.some(rings => inRing(rings[0]) && !rings.slice(1).some(inRing));
}
