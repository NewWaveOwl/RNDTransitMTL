/** Coordinate-based OTP 2.10 client. Agency credentials belong to OTP/its feed bridge. */
export const modeNames = Object.freeze({ walk: 'WALK', bike: 'BICYCLE', bus: 'BUS', metro: 'SUBWAY', train: 'RAIL' });

export const query = `query GenerateTrip(
  $originLat: CoordinateValue!, $originLon: CoordinateValue!,
  $destinationLat: CoordinateValue!, $destinationLon: CoordinateValue!,
  $departure: OffsetDateTime!, $modes: PlanModesInput!, $filters: [TransitFilterInput!], $excludeRealtime: Boolean!
) {
  planConnection(
    origin: {location: {coordinate: {latitude: $originLat, longitude: $originLon}}}
    destination: {location: {coordinate: {latitude: $destinationLat, longitude: $destinationLon}}}
    dateTime: {earliestDeparture: $departure}
    modes: $modes
    preferences: {transit: {filters: $filters, timetable: {excludeRealTimeUpdates: $excludeRealtime}}}
    first: 4
  ) {
    routingErrors { code inputField }
    edges { node {
      duration waitingTime
      legs {
        mode distance duration realTime
        route { gtfsId shortName longName }
        start { scheduledTime estimated { time delay } }
        end { scheduledTime estimated { time delay } }
        alerts { alertHash alertHeaderText alertDescriptionText activityPeriods { start end } }
        trip { pattern { code alerts { alertHash alertHeaderText alertDescriptionText activityPeriods { start end } } } }
        legGeometry { length points }
      }
    } }
  }
}`;

/** Selections are allowed modes. Walking access/egress remains necessary for transit. */
export function otpRequest({ origin, destination, modes, departure, filters }) {
  const transit = modes.filter(mode => !['walk', 'bike'].includes(mode));
  const direct = modes.filter(mode => ['walk', 'bike'].includes(mode)).map(mode => modeNames[mode]);
  return {
    query,
    variables: {
      originLat: origin.latitude, originLon: origin.longitude,
      destinationLat: destination.latitude, destinationLon: destination.longitude,
      departure,
      modes: {
        direct, directOnly: transit.length === 0, transitOnly: direct.length === 0,
        ...(transit.length ? { transit: {
          access: ['WALK'], egress: ['WALK'], transfer: ['WALK'],
          transit: transit.map(mode => ({ mode: modeNames[mode] })),
        } } : {}),
      },
      filters: filters ?? null, excludeRealtime: transit.length === 0,
    },
    extensions: { realtimeFeeds: [
      ...(transit.some(mode => mode === 'bus' || mode === 'metro') ? ['STM'] : []),
      ...(transit.includes('train') ? ['EXO_TRAINS'] : []),
    ] },
  };
}

export class ProviderError extends Error {}

/** The caller supplies a bounded abort signal shared by all candidate requests. */
export function createOtpClient(endpoint, fetchImpl = fetch) {
  const url = new URL(endpoint);
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('OTP_URL must be HTTP(S).');
  return async (request, signal) => {
    const response = await fetchImpl(url, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(otpRequest(request)), signal,
    });
    if (!response.ok) throw new ProviderError(`OTP returned HTTP ${response.status}.`);
    const body = await response.json();
    if (body.errors?.length || !Array.isArray(body.data?.planConnection?.edges)) {
      throw new ProviderError('OTP returned an invalid planning response.');
    }
    return body;
  };
}
