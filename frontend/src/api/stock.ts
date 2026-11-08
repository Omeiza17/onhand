import { json, request } from './client.ts'
import type { Kind, StockChange, StockItem } from './types.ts'

/** Everything at home, ordered by location then name. `kind` narrows it to FOOD or HOUSEHOLD. */
export function listStock(kind?: Kind): Promise<StockItem[]> {
  return request(kind ? `/stock?kind=${kind}` : '/stock')
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

/** Undo a change (only an item's latest change can be undone). */
export function undoStockChange(eventId: string): Promise<StockChange> {
  return request(`/stock/events/${eventId}/undo`, json('POST'))
}
