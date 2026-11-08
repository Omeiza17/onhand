import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import type { StockItem } from '../api/types.ts'
import { formatQuantity, num, parseDecimal, stepChoices, unitLabel } from '../lib/format.ts'

import type { SheetMode as Mode } from './StockRow.tsx'

interface Props {
  item: StockItem
  /** "I used" by default; "Now I have" when opened from an item that's out */
  initialMode?: Mode
  /** element to give focus back to on close */
  returnFocusTo: HTMLElement | null
  onClose: () => void
  onSubmit: (item: StockItem, body: { used: number } | { quantity: number }) => void
  onStepChange: (item: StockItem, stepSize: number) => void
}

const DECIMAL = /^\d*[.,]?\d*$/

/**
 * Bottom sheet for an exact update of a COUNTED item: "I used" (default) or
 * "Now I have", with quick amounts, a live result and — for weighed units — the
 * product's step size.
 */
export default function UpdateSheet({ item, initialMode = 'used', returnFocusTo, onClose, onSubmit, onStepChange }: Props) {
  const current = item.quantity ?? 0
  const step = item.stepSize ?? 1
  const [mode, setMode] = useState<Mode>(initialMode)
  const [text, setText] = useState(initialMode === 'now' ? num(current) : '')
  const sheetRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const titleId = useId()

  // focus in on open, back to the amount button on close
  useEffect(() => {
    inputRef.current?.focus()
    return () => returnFocusTo?.focus()
  }, [returnFocusTo])

  const value = parseDecimal(text)
  const tooMuch = mode === 'used' && value > current
  const valid = Number.isFinite(value) && (mode === 'used' ? value > 0 && !tooMuch : value >= 0)
  const result = mode === 'used' ? Math.max(0, current - (Number.isFinite(value) ? value : 0)) : value
  const resultText = Number.isFinite(result) ? formatQuantity(item.unit, result) : '…'

  const steps = stepChoices(item.unit)
  const quick: { label: string; value: number }[] =
    mode !== 'used'
      ? []
      : [
          ...(steps.length > 0
            ? [
                { label: formatQuantity(item.unit, step), value: step },
                { label: formatQuantity(item.unit, step * 2), value: step * 2 },
              ]
            : [
                { label: '1', value: 1 },
                { label: '2', value: 2 },
              ]),
          { label: 'Half', value: current / 2 },
          { label: 'All of it', value: current },
        ]

  function pickMode(next: Mode) {
    setMode(next)
    setText(next === 'now' ? num(current) : '')
    inputRef.current?.focus()
  }

  function submit() {
    if (!valid) return
    onSubmit(item, mode === 'used' ? { used: value } : { quantity: value })
  }

  // Escape closes; Tab stays inside the sheet
  function onKeyDown(event: KeyboardEvent) {
    if (event.key === 'Escape') {
      event.stopPropagation()
      onClose()
    } else if (event.key === 'Tab') {
      const focusables = sheetRef.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input')
      if (!focusables || focusables.length === 0) return
      const first = focusables[0]
      const last = focusables[focusables.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
  }

  return (
    <div className="sheet-layer" onKeyDown={onKeyDown}>
      <button type="button" className="sheet-scrim" aria-label="Close" tabIndex={-1} onClick={onClose} />
      <div ref={sheetRef} className="sheet" role="dialog" aria-modal="true" aria-labelledby={titleId}>
        <div className="sheet__handle" aria-hidden="true" />
        <div className="sheet__heading">
          <h2 id={titleId} className="sheet__title">
            {item.productName}
          </h2>
          <div className="sheet__subtitle">
            {formatQuantity(item.unit, current)} · {item.locationName}
          </div>
        </div>

        <div role="group" aria-label="Mode" className="sheet__modes">
          <button type="button" className="chip" aria-pressed={mode === 'used'} onClick={() => pickMode('used')}>
            I used
          </button>
          <button type="button" className="chip" aria-pressed={mode === 'now'} onClick={() => pickMode('now')}>
            Now I have
          </button>
        </div>

        <form
          onSubmit={(e) => {
            e.preventDefault()
            submit()
          }}
        >
          <label className="sheet__amount">
            <span className="sr-only">{mode === 'used' ? 'Amount used' : 'Amount you have now'}</span>
            <input
              ref={inputRef}
              type="text"
              inputMode="decimal"
              autoComplete="off"
              placeholder="0"
              value={text}
              aria-invalid={tooMuch || undefined}
              onChange={(e) => {
                if (DECIMAL.test(e.target.value)) setText(e.target.value)
              }}
            />
            <span className="sheet__unit">{unitLabel(item.unit)}</span>
          </label>
        </form>

        {quick.length > 0 && (
          <div role="group" aria-label="Quick amounts" className="sheet__chips">
            {quick.map((q) => (
              <button key={q.label} type="button" className="chip chip--sm" onClick={() => setText(num(q.value))}>
                {q.label}
              </button>
            ))}
          </div>
        )}

        <div className="sheet__result">
          {tooMuch ? (
            <span className="sheet__error">That’s more than the {formatQuantity(item.unit, current)} there is</span>
          ) : (
            <>
              Result:{' '}
              <strong>
                {formatQuantity(item.unit, current)} → {resultText}
              </strong>
            </>
          )}
        </div>

        {steps.length > 0 && (
          <div className="sheet__steps">
            <div className="sheet__steps-label" id={`${titleId}-steps`}>
              − / + buttons move by
            </div>
            <div role="group" aria-labelledby={`${titleId}-steps`} className="sheet__chips">
              {steps.map((s) => (
                <button
                  key={s}
                  type="button"
                  className="chip chip--sm"
                  aria-pressed={s === step}
                  onClick={() => onStepChange(item, s)}
                >
                  {formatQuantity(item.unit, s)}
                </button>
              ))}
            </div>
          </div>
        )}

        <div className="sheet__actions">
          <button type="button" className="btn btn--outline" onClick={onClose}>
            Cancel
          </button>
          <button type="button" className="btn btn--primary" onClick={submit} disabled={!valid}>
            Update
          </button>
        </div>
      </div>
    </div>
  )
}
