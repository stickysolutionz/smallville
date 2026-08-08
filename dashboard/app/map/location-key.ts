// Agents can be in nested sub-locations, e.g. "Cottage: Bedroom" - resolve
// down to the top level segment so they still group under the right card.
export function resolveMarkerKey(locationPath: string): string {
  return (locationPath || '').split(':')[0].trim();
}
