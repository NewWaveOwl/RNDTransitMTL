import { geoPoint, inServiceArea } from './geo.mjs';
import { modeNames, ProviderError } from './otp.mjs';

const noRouteCodes = new Set(['LOCATION_NOT_FOUND', 'NO_DIRECT_MODE_CONNECTION', 'NO_STOPS_IN_RANGE',
  'NO_TRANSIT_CONNECTION', 'NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW', 'OUTSIDE_SERVICE_PERIOD',
  'WALKING_BETTER_THAN_TRANSIT', 'OUTSIDE_BOUNDS']);
export class InputError extends Error {}

/** Validate before touching OTP. Attempt counts and timeouts are exclusively server controlled. */
export function validateRequest(input) {
  try { geoPoint(input?.origin?.point); } catch { throw new InputError('Choose a valid starting point.'); }
  if (!inServiceArea(input.origin.point)) throw new InputError('Starting point is outside the supported area.');
  if (typeof input.origin.label !== 'string' || !input.origin.label.trim() || input.origin.label.length > 160) {
    throw new InputError('Choose a named starting point.');
  }
  if (!Number.isInteger(input.plannedMinutes) || input.plannedMinutes < 5 || input.plannedMinutes > 360 || input.plannedMinutes % 5) {
    throw new InputError('Choose 5–360 minutes in five-minute steps.');
  }
  if (!Array.isArray(input.modes) || !input.modes.length || input.modes.length > 5 ||
      new Set(input.modes).size !== input.modes.length || input.modes.some(mode => !Object.hasOwn(modeNames, mode))) {
    throw new InputError('Choose supported transport modes: walking, cycling, STM bus/métro or exo train.');
  }
  if (input.excludedDestinations !== undefined && (!Array.isArray(input.excludedDestinations) || input.excludedDestinations.length > 10)) {
    throw new InputError('Too many previous destinations.');
  }
  try { (input.excludedDestinations ?? []).forEach(geoPoint); } catch { throw new InputError('Invalid previous destination.'); }
  const reference = input.origin.osmReference;
  if (reference != null && (!['Node', 'Way', 'Relation'].includes(reference.type) ||
      !Number.isSafeInteger(reference.id) || reference.id <= 0)) throw new InputError('Invalid origin reference.');
  // Discard client-supplied filters, endpoints, attempt limits and derived policy fields.
  return {
    origin: { label: input.origin.label, point: geoPoint(input.origin.point),
      ...(reference == null ? {} : { osmReference: { type: reference.type, id: reference.id } }) },
    plannedMinutes: input.plannedMinutes, modes: [...input.modes],
    zonesByTransport: input.zonesByTransport, selectedRouteIds: input.selectedRouteIds,
    excludedDestinations: input.excludedDestinations ?? [],
  };
}

/** Decode enough of the geometry to reject missing, malformed or disconnected provider legs. */
export function decodePolyline(value) {
  if (typeof value !== 'string' || value.length > 1_000_000) throw new ProviderError('Invalid route geometry.');
  let index = 0, lat = 0, lon = 0;
  const delta = () => {
    let bits = 0, shift = 0, chunk;
    do {
      if (index >= value.length || shift > 25) throw new ProviderError('Invalid route geometry.');
      chunk = value.charCodeAt(index++) - 63;
      if (chunk < 0 || chunk > 63) throw new ProviderError('Invalid route geometry.');
      bits |= (chunk & 31) << shift;
      shift += 5;
    } while (chunk >= 32);
    return (bits >>> 1) ^ -(bits & 1);
  };
  const points = [];
  while (index < value.length) {
    lat += delta(); lon += delta();
    points.push(geoPoint({ latitude: lat / 100_000, longitude: lon / 100_000 }));
  }
  return points;
}

export function itineraryElapsed(itinerary, departureMillis) {
  const legs = itinerary?.legs;
  if (!Array.isArray(legs) || !legs.length || !Number.isFinite(itinerary.duration) || itinerary.duration <= 0) return null;
  const last = legs.at(-1);
  const end = last.realTime && last.end?.estimated?.time ? last.end.estimated.time : last.end?.scheduledTime;
  const arrival = Date.parse(end);
  const elapsedSeconds = (arrival - departureMillis) / 1000;
  if (!Number.isFinite(elapsedSeconds) || elapsedSeconds <= 0) return null;
  // This includes the wait before the first leg, which itinerary.duration may omit.
  return Math.ceil(Math.max(elapsedSeconds, itinerary.duration));
}

/** One bounded search per GO. Rejected candidates never become application trips. */
export class TripGenerationService {
  constructor({ candidates, route, accept = () => true, prepare = input => input,
    maxAttempts = 16, timeoutMillis = 15_000, now = Date.now }) {
    Object.assign(this, { candidates, route, accept, prepare, maxAttempts, timeoutMillis, now });
  }

  async generate(input, clientSignal = new AbortController().signal) {
    input = validateRequest(input);
    const startedAt = this.now();
    const departure = new Date(startedAt).toISOString();
    const deadline = AbortSignal.timeout(this.timeoutMillis);
    const signal = AbortSignal.any([clientSignal, deadline]);
    const prepared = this.prepare(input);
    const speed = input.modes.includes('train') || input.modes.includes('metro') ? 30 :
      input.modes.includes('bus') ? 15 : input.modes.includes('bike') ? 8 : 2;
    const candidates = this.candidates.generate(input.origin.point, {
      limit: this.maxAttempts, maxDistanceMeters: Math.min(100_000, speed * input.plannedMinutes * 60),
      excluded: input.excludedDestinations ?? [],
    });
    let attempts = 0, providerFailures = 0;
    try {
      for (const candidate of candidates.slice(0, this.maxAttempts)) {
        signal.throwIfAborted();
        attempts++;
        let body;
        try {
          body = await this.route({ ...prepared, origin: input.origin.point, destination: candidate.point, departure }, signal);
        } catch (error) {
          signal.throwIfAborted();
          if (++providerFailures >= 2) throw new ProviderError('Routing service unavailable. Try again.');
          continue;
        }
        signal.throwIfAborted();
        const plan = body.data?.planConnection;
        const errors = plan?.routingErrors ?? [];
        if (errors.some(error => !noRouteCodes.has(error.code))) throw new ProviderError('OTP could not process the route.');
        for (const edge of plan?.edges ?? []) {
          const itinerary = edge.node;
          const elapsedSeconds = itineraryElapsed(itinerary, startedAt);
          if (elapsedSeconds === null || elapsedSeconds > input.plannedMinutes * 60) continue;
          if (!this.accept(itinerary, input, candidate)) continue;
          return {
            status: 'success', origin: input.origin, destination: { label: candidate.label, point: candidate.point },
            elapsedSeconds, departure, attempts,
            otp: { ...body, data: { planConnection: { ...plan, routingErrors: [], edges: [edge] } } },
          };
        }
      }
      if (providerFailures) throw new ProviderError('Routing service unavailable. Try again.');
      return { status: 'no_trip', message: 'No reachable trip fits these choices. Try again or increase the time.', attempts };
    } catch (error) {
      if (clientSignal.aborted) throw clientSignal.reason;
      if (deadline.aborted) return { status: 'no_trip', message: 'Trip search timed out. Try again.', attempts };
      throw error;
    }
  }
}
