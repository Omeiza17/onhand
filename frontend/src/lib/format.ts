import type { StockItem, StockStatus, Unit } from '../api/types.ts'

/** Rounds to the backend's 3 decimals and drops trailing zeros: 1.500 → "1.5". */
export function num(n: number): string {
  return String(Number(n.toFixed(3)))
}

const MEASURED: ReadonlySet<Unit> = new Set(['g', 'kg', 'ml', 'l'])

/** g/kg/ml/l: amounts are weighed or measured, not counted. */
export function isMeasured(unit: Unit | null): boolean {
  return unit !== null && MEASURED.has(unit)
}

/** English plural of a counted unit: tins, boxes, bunches. */
function plural(unit: Unit): string {
  return unit === 'box' || unit === 'bunch' ? `${unit}es` : `${unit}s`
}

/**
 * Quantity text per DESIGN.md: piece → "4", metric "485 g", other counted units
 * pluralise: "1 bottle", "3 boxes".
 */
export function formatQuantity(unit: Unit | null, n: number): string {
  if (unit === null || unit === 'piece') return num(n)
  if (isMeasured(unit)) return `${num(n)} ${unit}`
  return `${num(n)} ${n === 1 ? unit : plural(unit)}`
}

/** Suffix for the update sheet's input. */
export function unitLabel(unit: Unit | null): string {
  if (unit === null || unit === 'piece') return 'pcs'
  return isMeasured(unit) ? unit : plural(unit)
}

export const STATUS_LABEL: Record<StockStatus, string> = { HAVE: 'Have', LOW: 'Low', OUT: 'Out' }

export const NEXT_STATUS: Record<StockStatus, StockStatus> = { HAVE: 'LOW', LOW: 'OUT', OUT: 'HAVE' }

/** What a row shows: "485 g" or "Low". */
export function describe(item: StockItem): string {
  if (item.trackingMode === 'STATUS') return item.status ? STATUS_LABEL[item.status] : ''
  return formatQuantity(item.unit, item.quantity ?? 0)
}

/** Step-size choices in the update sheet — weighed/measured units only. */
const STEP_CHOICES: Partial<Record<Unit, number[]>> = {
  g: [50, 100, 250, 500],
  kg: [0.1, 0.25, 0.5, 1],
  ml: [100, 250, 500],
  l: [0.25, 0.5, 1],
}

export function stepChoices(unit: Unit | null): number[] {
  return (unit && STEP_CHOICES[unit]) || []
}

/** COUNTED and below the product's minimum → it belongs on the shopping list. */
export function isBelowMinimum(item: StockItem): boolean {
  return (
    item.trackingMode === 'COUNTED' &&
    item.minQuantity !== null &&
    item.quantity !== null &&
    item.quantity < item.minQuantity
  )
}

/**
 * Products that need buying, derived from stock until the shopping list API exists
 * (slice 5): STATUS items that are Low/Out, COUNTED items below their minimum.
 */
export function countToBuy(items: StockItem[]): number {
  const products = new Set<string>()
  for (const item of items) {
    if ((item.trackingMode === 'STATUS' && item.status !== 'HAVE') || isBelowMinimum(item)) {
      products.add(item.productId)
    }
  }
  return products.size
}

/** Parses typed decimals, accepting "," or "." as separator. NaN when not a number. */
export function parseDecimal(text: string): number {
  const t = text.trim().replace(',', '.')
  return t === '' || t === '.' ? NaN : Number(t)
}
