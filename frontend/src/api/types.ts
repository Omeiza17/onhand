// Mirrors the backend DTOs (dev.codingstoic.onhand.*). UUIDs and timestamps are strings.

export type Kind = 'FOOD' | 'HOUSEHOLD'
export type TrackingMode = 'COUNTED' | 'STATUS'
export type Unit = 'piece' | 'g' | 'kg' | 'ml' | 'l' | 'pack' | 'tin'
export type StockStatus = 'HAVE' | 'LOW' | 'OUT'

/** stock.StockItemView — one product at one location, as the Home screen shows it. */
export interface StockItem {
  id: string
  productId: string
  productName: string
  kind: Kind
  category: string | null
  trackingMode: TrackingMode
  unit: Unit | null
  locationId: string
  locationName: string
  locationSortOrder: number
  /** COUNTED only */
  quantity: number | null
  /** COUNTED only: how far one tap on −/+ moves */
  stepSize: number | null
  /** COUNTED only, optional: below it the item belongs on the shopping list */
  minQuantity: number | null
  /** STATUS only */
  status: StockStatus | null
  expiresOn: string | null
  updatedAt: string
}

/** stock.StockChange — pass eventId to undo. item is null after an undo removed it. */
export interface StockChange {
  item: StockItem | null
  eventId: string | null
}

/** product.Product */
export interface Product {
  id: string
  name: string
  kind: Kind
  category: string | null
  trackingMode: TrackingMode
  defaultUnit: Unit | null
  defaultLocationId: string | null
  minQuantity: number | null
  stepSize: number | null
  effectiveStepSize: number | null
  barcode: string | null
}
