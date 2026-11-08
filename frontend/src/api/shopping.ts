import { json, request } from './client.ts'
import type { PutAwayResult, ShoppingList } from './types.ts'

export function getShoppingList(): Promise<ShoppingList> {
  return request('/shopping')
}

/** "Add something…" — linked to a tracked product when the name matches. */
export function addShoppingItem(name: string): Promise<ShoppingList> {
  return request('/shopping/items', json('POST', { name }))
}

/** Tick / untick something you added. */
export function checkShoppingItem(id: string, checked: boolean): Promise<ShoppingList> {
  return request(`/shopping/items/${id}/checked`, json('PUT', { checked }))
}

/** Tick / untick a running-low product. */
export function checkShoppingProduct(productId: string, checked: boolean): Promise<ShoppingList> {
  return request(`/shopping/products/${productId}/checked`, json('PUT', { checked }))
}

/** "Put n items away": restock what's ticked and clear it from the list. */
export function putAway(): Promise<PutAwayResult> {
  return request('/shopping/put-away', json('POST'))
}
