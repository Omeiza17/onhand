import { useState } from 'react'
import type { Unit } from '../api/types.ts'

/** Unit chips in two labelled rows (DESIGN.md); the rarer count units sit behind "More". */
const MEASURED_UNITS: Unit[] = ['g', 'kg', 'ml', 'l']
const COUNT_UNITS: Unit[] = ['piece', 'pack', 'tin', 'bottle', 'roll']
const MORE_UNITS: Unit[] = ['jar', 'box', 'bag', 'carton', 'tube', 'tub', 'bar', 'sachet', 'bunch']

interface Props {
  value: Unit
  onChange: (unit: Unit) => void
  disabled?: boolean
  /** prefix for element ids, so two pickers never clash */
  idPrefix: string
}

/** Weight / volume: g kg ml l · Count: piece pack tin bottle roll More. */
export default function UnitPicker({ value, onChange, disabled = false, idPrefix }: Props) {
  const [more, setMore] = useState(false)
  // a selected "More" unit stays visible in the Count row even when the rest are collapsed
  const countUnits = more ? [...COUNT_UNITS, ...MORE_UNITS] : MORE_UNITS.includes(value) ? [...COUNT_UNITS, value] : COUNT_UNITS

  const chip = (u: Unit) => (
    <button
      key={u}
      type="button"
      className="chip"
      aria-pressed={value === u}
      disabled={disabled}
      onClick={() => onChange(u)}
    >
      {u}
    </button>
  )

  return (
    <div className="unit-rows">
      <div className="unit-row">
        <div className="unit-row__label" id={`${idPrefix}-measured`}>
          Weight / volume
        </div>
        <div role="group" aria-labelledby={`${idPrefix}-measured`} className="field__wrap">
          {MEASURED_UNITS.map(chip)}
        </div>
      </div>
      <div className="unit-row">
        <div className="unit-row__label" id={`${idPrefix}-count`}>
          Count
        </div>
        <div role="group" aria-labelledby={`${idPrefix}-count`} className="field__wrap">
          {countUnits.map(chip)}
          {!more && (
            <button
              type="button"
              className="chip chip--quiet"
              aria-expanded={false}
              disabled={disabled}
              onClick={() => setMore(true)}
            >
              More
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
