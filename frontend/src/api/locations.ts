import { json, request } from './client.ts'
import type { Location } from './types.ts'

/** All locations in their display order. */
export function listLocations(): Promise<Location[]> {
  return request('/locations')
}

/** New location at the end of the list. */
export function createLocation(name: string): Promise<Location> {
  return request('/locations', json('POST', { name }))
}
