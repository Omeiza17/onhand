import { json, request } from './client.ts'
import type { Kind, StockChange, StockItem, StockStatus, TrackingMode, Unit } from './types.ts'

/** Everything at home, ordered by location then name. `kind` narrows it to FOOD or HOUSEHOLD. */
export function listStock(kind?: Kind): Promise<StockItem[]> {
  return request(kind ? `/stock?kind=${kind}` : '/stock')
}

/**
 * Puts a product at home. COUNTED: adds `quantity` (0 = at home but none left).
 * STATUS: sets `status` (default HAVE). Location defaults to the product's default.
 */
export function addStock(body: {
  productId: string
  locationId?: string
  quantity?: number
  status?: StockStatus
}): Promise<StockChange> {
  return request('/stock', json('POST', body))
}

/** "+" — one tap moves by the product's step size. */
export function increaseStock(id: string): Promise<StockChange> {
  return request(`/stock/${id}/increase`, json('POST'))
}

/** "−" — one tap moves by the product's step size; stops at 0. */
export function decreaseStock(id: string): Promise<StockChange> {
  return request(`/stock/${id}/decrease`, json('POST'))
}

/** Update sheet: "Now I have" (`quantity`) or "I used" (`used`). */
export function setStockQuantity(
  id: string,
  body: { quantity: number } | { used: number },
): Promise<StockChange> {
  return request(`/stock/${id}/quantity`, json('PUT', body))
}

/** STATUS pill: HAVE → LOW → OUT → HAVE. */
export function cycleStockStatus(id: string): Promise<StockChange> {
  return request(`/stock/${id}/cycle`, json('POST'))
}

/** Edit product, new place: moves the whole item there (and makes it the default place). */
export function moveStock(id: string, locationId: string): Promise<StockChange> {
  return request(`/stock/${id}/move`, json('POST', { locationId }))
}

/**
 * Edit product, new tracking mode or unit. To STATUS: nothing else (Have if some is left,
 * else Out). To COUNTED or a new unit: the unit and how much is at home now.
 */
export function convertStock(
  id: string,
  body: { trackingMode: TrackingMode; unit?: Unit; quantity?: number },
): Promise<StockChange> {
  return request(`/stock/${id}/convert`, json('POST', body))
}

/** Undo a change (only an item's latest change can be undone). */
export function undoStockChange(eventId: string): Promise<StockChange> {
  return request(`/stock/events/${eventId}/undo`, json('POST'))
}
