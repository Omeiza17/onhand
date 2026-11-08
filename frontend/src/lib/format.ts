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

/** Default step per unit — mirrors the backend's Unit.defaultStep. */
export function defaultStep(unit: Unit): number {
  switch (unit) {
    case 'g':
      return 100
    case 'kg':
    case 'l':
      return 0.5
    case 'ml':
      return 250
    default:
      return 1
  }
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

/** COUNTED at 0: out, shown like the Out pill (DESIGN.md "COUNTED at 0"). */
export function isOut(item: StockItem): boolean {
  return item.trackingMode === 'COUNTED' && (item.quantity ?? 0) === 0
}

/**
 * COUNTED and on the shopping list, per the spec: the product's total across all places is 0,
 * or below its minimum. `all` is every stock item (for the total).
 */
export function isOnList(item: StockItem, all: StockItem[]): boolean {
  if (item.trackingMode !== 'COUNTED') return false
  const total = all.filter((i) => i.productId === item.productId).reduce((sum, i) => sum + (i.quantity ?? 0), 0)
  return total === 0 || (item.minQuantity !== null && total < item.minQuantity)
}


/** Parses typed decimals, accepting "," or "." as separator. NaN when not a number. */
export function parseDecimal(text: string): number {
  const t = text.trim().replace(',', '.')
  return t === '' || t === '.' ? NaN : Number(t)
}
