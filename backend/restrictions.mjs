import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { dirname } from 'node:path';
import { distance, inPolygon, inServiceArea } from './geo.mjs';
import { decodePolyline, InputError } from './generation.mjs';
import { modeNames } from './otp.mjs';

export const fareZoneSource = 'https://donnees.artm.quebec/catalogue/dataset/9b19ddd0-f10d-45b9-9cee-8896bacc012d/resource/88c53302-58f7-4d35-9231-dc3d48dce008/download/zone_tarifaire_2026.geojson';

/** ARTM polygons, never the missing STM / legacy numeric exo GTFS zone_id field. */
export class FareZones {
  constructor(collection) {
    if (collection?.type !== 'FeatureCollection') throw new Error('Fare zones must be a GeoJSON FeatureCollection.');
    this.features = collection.features.map(feature => {
      const values = Object.values(feature.properties ?? {}).filter(value => typeof value === 'string')
        .map(value => /^(?:zone\s*)?([ABCD])$/i.exec(value.trim())?.[1]?.toUpperCase()).filter(Boolean);
      const zones = [...new Set(values)];
      if (zones.length !== 1 || !['Polygon', 'MultiPolygon'].includes(feature.geometry?.type)) {
        throw new Error('An ARTM feature has an unknown zone or geometry.');
      }
      return { zone: zones[0], geometry: feature.geometry };
    });
    if (new Set(this.features.map(feature => feature.zone)).size !== 4) throw new Error('Expected all four ARTM fare zones.');
  }
  at(point) { return this.features.filter(feature => inPolygon(point, feature.geometry)).map(feature => feature.zone); }
  allows(point, selected) { return this.at(point).some(zone => selected.includes(zone)); }
}

export async function loadFareZones(path, fetchImpl = fetch) {
  let text;
  try { text = await readFile(path, 'utf8'); }
  catch (error) {
    if (error.code !== 'ENOENT') throw error;
    const response = await fetchImpl(fareZoneSource, { signal: AbortSignal.timeout(30_000) });
    if (!response.ok) throw new Error('Could not download official ARTM fare-zone polygons.');
    text = await response.text();
    new FareZones(JSON.parse(text)); // Validate before keeping a cached copy.
    await mkdir(dirname(path), { recursive: true });
    await writeFile(path, text, 'utf8');
  }
  return new FareZones(JSON.parse(text));
}

export function validateRestrictions(input) {
  const transit = input.modes.filter(mode => !['walk', 'bike'].includes(mode));
  const zones = input.zonesByTransport ?? {};
  if (!zones || typeof zones !== 'object' || Array.isArray(zones) ||
      Object.keys(zones).some(mode => !transit.includes(mode))) throw new InputError('Invalid transport-zone selections.');
  for (const mode of transit) {
    const selected = zones[mode];
    if (!Array.isArray(selected) || !selected.length || selected.length > 4 ||
        new Set(selected).size !== selected.length || selected.some(zone => !['A', 'B', 'C', 'D'].includes(zone))) {
      throw new InputError(`Choose valid transit zones for ${mode}.`);
    }
  }
  if (input.selectedRouteIds !== undefined && (!Array.isArray(input.selectedRouteIds) || input.selectedRouteIds.length > 50 ||
      input.selectedRouteIds.some(id => typeof id !== 'string' || id.length > 120))) throw new InputError('Invalid selected lines.');
}

/** Resolve UI line names to GTFS identifiers from OTP's current graph, including métro colors. */
export function lineResolver(routes) {
  const colors = { Orange: '2', Green: '1', Blue: '5', Yellow: '4' };
  return input => {
    const selected = input.selectedRouteIds ?? [];
    if (!selected.length) return input;
    const allowed = new Set();
    const limitedModes = new Set();
    for (const id of selected) {
      const [mode, ...parts] = id.split(':');
      if (!input.modes.includes(mode) || ['walk', 'bike'].includes(mode)) throw new InputError('Selected line requires its transport mode.');
      const label = parts.join(':');
      const matches = routes.filter(route => route.mode === modeNames[mode] &&
        (route.gtfsId === id || route.shortName === (colors[label] ?? label) ||
         (mode === 'train' && route.longName?.startsWith(`${label} -`))));
      if (!matches.length) throw new InputError(`Selected line is unavailable: ${id}.`);
      matches.forEach(route => allowed.add(route.gtfsId));
      limitedModes.add(mode);
    }
    // Include unrestricted selected modes too; otherwise a train choice could accidentally ban buses.
    routes.filter(route => input.modes.some(mode => !limitedModes.has(mode) && modeNames[mode] === route.mode))
      .forEach(route => allowed.add(route.gtfsId));
    return { ...input, allowedRouteIds: [...allowed], filters: [{ include: [{ routes: [...allowed] }] }] };
  };
}

/** Reject unknown modes, disconnected geometry, and every transit leg crossing an unselected zone. */
export function itineraryPolicy(fareZones, resolveLines = value => value) {
  return (itinerary, input, destination) => {
    const prepared = resolveLines(input);
    let previous = input.origin.point;
    let routedDistance = 0;
    try {
      for (const leg of itinerary.legs) {
        const mode = Object.keys(modeNames).find(key => modeNames[key] === leg.mode);
        const accessWalk = mode === 'walk' && input.modes.some(value => !['walk', 'bike'].includes(value));
        if (!mode || (!input.modes.includes(mode) && !accessWalk)) return false;
        if (!Number.isFinite(leg.distance) || leg.distance < 0 || !Number.isFinite(leg.duration) || leg.duration < 0) return false;
        routedDistance += leg.distance;
        const points = decodePolyline(leg.legGeometry?.points);
        if (points.length < 2 || points.length !== leg.legGeometry.length || distance(previous, points[0]) > 35) return false;
        const zones = input.zonesByTransport?.[mode];
        if (!['walk', 'bike'].includes(mode)) {
          if (!leg.route?.gtfsId || !zones || !fareZones) return false;
          if (prepared.allowedRouteIds && !prepared.allowedRouteIds.includes(leg.route.gtfsId)) return false;
          // Inspect vertices and densify long segments so a sparse train shape cannot skip a zone.
          for (let i = 0; i < points.length; i++) {
            if (!fareZones.allows(points[i], zones)) return false;
            if (i === 0) continue;
            const a = points[i - 1], b = points[i];
            const steps = Math.ceil(distance(a, b) / 100);
            for (let step = 1; step < steps; step++) {
              const point = { latitude: a.latitude + (b.latitude - a.latitude) * step / steps,
                longitude: a.longitude + (b.longitude - a.longitude) * step / steps };
              if (!fareZones.allows(point, zones)) return false;
            }
          }
        }
        if (!points.every(inServiceArea)) return false;
        previous = points.at(-1);
      }
      return routedDistance > 0 && distance(previous, destination.point) <= 35;
    } catch { return false; }
  };
}

export async function loadRouteCatalogue(endpoint, fetchImpl = fetch) {
  const response = await fetchImpl(endpoint, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, signal: AbortSignal.timeout(30_000),
    body: JSON.stringify({ query: 'query RoutingLines { routes { gtfsId mode shortName longName } }' }),
  });
  const body = await response.json();
  if (!response.ok || body.errors?.length || !Array.isArray(body.data?.routes)) throw new Error('OTP line catalogue unavailable.');
  return body.data.routes;
}
