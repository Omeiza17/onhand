import { json, request } from './client.ts'
import type { Product } from './types.ts'

/** How far one tap on −/+ moves; `null` resets to the unit default. */
export function setStepSize(productId: string, stepSize: number | null): Promise<Product> {
  return request(`/products/${productId}/step-size`, json('PUT', { stepSize }))
}
