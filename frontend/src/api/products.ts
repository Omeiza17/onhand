import { json, request } from './client.ts'
import type { NewProduct, Product, ProductPatch } from './types.ts'

/** Products whose name contains `query` (case-insensitive), by name. */
export function searchProducts(query: string): Promise<Product[]> {
  return request(`/products?q=${encodeURIComponent(query)}`)
}

export function getProduct(id: string): Promise<Product> {
  return request(`/products/${id}`)
}

/** Edit product's plain fields; omitted ones stay as they are. */
export function patchProduct(id: string, patch: ProductPatch): Promise<Product> {
  return request(`/products/${id}`, json('PATCH', patch))
}

/** "Remove from Onhand": hidden everywhere, never deleted. */
export function archiveProduct(id: string): Promise<Product> {
  return request(`/products/${id}/archive`, json('POST'))
}

/** Undo of "Remove from Onhand". */
export function unarchiveProduct(id: string): Promise<Product> {
  return request(`/products/${id}/unarchive`, json('POST'))
}

export function createProduct(product: NewProduct): Promise<Product> {
  return request('/products', json('POST', product))
}

/** How far one tap on −/+ moves; `null` resets to the unit default. */
export function setStepSize(productId: string, stepSize: number | null): Promise<Product> {
  return request(`/products/${productId}/step-size`, json('PUT', { stepSize }))
}
