import { json, request } from './client.ts'
import type { NewProduct, Product } from './types.ts'

/** Products whose name contains `query` (case-insensitive), by name. */
export function searchProducts(query: string): Promise<Product[]> {
  return request(`/products?q=${encodeURIComponent(query)}`)
}

export function createProduct(product: NewProduct): Promise<Product> {
  return request('/products', json('POST', product))
}

/** How far one tap on −/+ moves; `null` resets to the unit default. */
export function setStepSize(productId: string, stepSize: number | null): Promise<Product> {
  return request(`/products/${productId}/step-size`, json('PUT', { stepSize }))
}
