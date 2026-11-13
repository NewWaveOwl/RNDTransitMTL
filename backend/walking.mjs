/** Product-defined paces; clients choose a name and cannot inject an arbitrary speed. */
export const walkingSpeedsKmh = Object.freeze({ Amble: 3, Walk: 4.8, Dash: 6.8 });
export const walkingSpeedMps = pace => walkingSpeedsKmh[pace] / 3.6;
/** One extra minute for every routed kilometre, including fractional kilometres. */
export const orientationBufferSeconds = meters => Math.ceil(meters * 60 / 1000);
