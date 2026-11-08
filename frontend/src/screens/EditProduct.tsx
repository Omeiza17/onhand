import { useEffect, useState } from 'react'
import { errorMessage } from '../api/client.ts'
import { listLocations } from '../api/locations.ts'
import { archiveProduct, getProduct, patchProduct, unarchiveProduct } from '../api/products.ts'
import { convertStock, moveStock, undoStockChange } from '../api/stock.ts'
import type { Kind, Location, Product, ProductPatch, StockItem, TrackingMode, Unit } from '../api/types.ts'
import { ArrowRightIcon, ArchiveIcon, MinusIcon, PlusIcon } from '../components/Icons.tsx'
import { Link } from '../components/Link.tsx'
import UnitPicker from '../components/UnitPicker.tsx'
import {
  defaultStep,
  describe,
  formatQuantity,
  parseDecimal,
  stepChoices,
  unitLabel,
} from '../lib/format.ts'
import { navigate } from '../lib/router.ts'
import { useToast } from '../lib/toast.ts'
import type { StockState } from '../lib/useStock.ts'

const KINDS: { value: Kind; label: string }[] = [
  { value: 'FOOD', label: 'Food' },
  { value: 'HOUSEHOLD', label: 'Household' },
]

const DECIMAL = /^\d*[.,]?\d*$/
const round3 = (n: number) => Math.round(n * 1000) / 1000

interface Props {
  productId: string
  /** the Home row this was opened from: moves and conversions apply to it */
  itemId: string | null
  stock: StockState
}

/** Edit product (DESIGN.md, Screen 4): the Add product form, prefilled, for one Home row. */
export default function EditProduct({ productId, itemId, stock }: Props) {
  const [product, setProduct] = useState<Product | null>(null)
  const [locations, setLocations] = useState<Location[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    Promise.all([getProduct(productId), listLocations()]).then(
      ([p, l]) => {
        if (cancelled) return
        setProduct(p)
        setLocations(l)
      },
      (e: unknown) => !cancelled && setLoadError(errorMessage(e)),
    )
    return () => {
      cancelled = true
    }
  }, [productId])

  // the form mounts once the product and the stock list are in, so its state starts prefilled
  const item = stock.items?.find((i) => i.id === itemId && i.productId === productId) ?? null
  const ready = product && locations && stock.items

  return (
    <div className="add">
      <header className="add__header">
        <Link href="/" className="add__cancel">
          Cancel
        </Link>
        <h1 className="add__title">Edit product</h1>
        <span aria-hidden="true" />
      </header>
      {loadError && (
        <p className="add__error add__form" role="alert">
          {loadError}
        </p>
      )}
      {ready && !product.archived && (
        <EditForm
          product={product}
          item={item}
          places={(stock.items ?? []).filter((i) => i.productId === productId).length}
          locations={locations}
          stock={stock}
        />
      )}
      {ready && product.archived && <p className="add__form">This product has been removed from Onhand.</p>}
    </div>
  )
}

interface FormProps {
  product: Product
  item: StockItem | null
  /** in how many places the product is at home */
  places: number
  locations: Location[]
  stock: StockState
}

function EditForm({ product, item, places, locations, stock }: FormProps) {
  const toast = useToast()
  const origUnit: Unit = product.defaultUnit ?? 'piece'

  const [name, setName] = useState(product.name)
  const [kind, setKind] = useState<Kind>(product.kind)
  const [locationId, setLocationId] = useState(item?.locationId ?? product.defaultLocationId ?? '')
  const [mode, setMode] = useState<TrackingMode>(product.trackingMode)
  const [unit, setUnit] = useState<Unit>(origUnit)
  const [step, setStep] = useState<number>(product.effectiveStepSize ?? defaultStep(origUnit))
  const [minQuantity, setMinQuantity] = useState(product.minQuantity ?? 0)
  const [amountText, setAmountText] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const counted = mode === 'COUNTED'
  const wasCounted = product.trackingMode === 'COUNTED'
  const toStatus = wasCounted && !counted
  const fromStatus = !wasCounted && counted
  const unitChanged = wasCounted && counted && unit !== origUnit
  const needsAmount = unitChanged || fromStatus
  const converting = toStatus || needsAmount
  // a mode or unit change applies to the one row; in several places it would be ambiguous
  const canConvert = item !== null && places <= 1
  const moving = item !== null && locationId !== item.locationId
  const origStep = product.effectiveStepSize ?? defaultStep(origUnit)
  const origMin = product.minQuantity ?? 0

  const amount = parseDecimal(amountText)
  const trimmed = name.trim()
  const changed =
    trimmed !== product.name ||
    kind !== product.kind ||
    moving ||
    converting ||
    (counted && (minQuantity !== origMin || step !== origStep))
  const valid = trimmed.length > 0 && (!needsAmount || (Number.isFinite(amount) && amount >= 0))
  const canSave = changed && valid && !saving

  const from = locations.find((l) => l.id === item?.locationId)
  const to = locations.find((l) => l.id === locationId)

  function pickUnit(u: Unit) {
    setUnit(u)
    setStep(defaultStep(u))
    setMinQuantity(0)
    setAmountText('')
  }

  function pickMode(next: TrackingMode) {
    setMode(next)
    setAmountText('')
    if (next === 'COUNTED' && !wasCounted) {
      setUnit('piece')
      setStep(defaultStep('piece'))
      setMinQuantity(0)
    }
    if (next === product.trackingMode) {
      setUnit(origUnit)
      setStep(origStep)
      setMinQuantity(origMin)
    }
  }

  async function save() {
    if (!canSave) return
    setSaving(true)
    setError(null)
    const events: string[] = []
    let after: StockItem | null = item
    try {
      // 1. name and kind first: a clash (409) stops here, before anything at home changes
      const details: ProductPatch = {}
      if (trimmed !== product.name) details.name = trimmed
      if (kind !== product.kind) details.kind = kind
      if (Object.keys(details).length > 0) await patchProduct(product.id, details)

      // 2. mode / unit, with the amount at home made explicit
      if (converting && item) {
        const change = await convertStock(
          item.id,
          toStatus ? { trackingMode: 'STATUS' } : { trackingMode: 'COUNTED', unit, quantity: round3(amount) },
        )
        if (change.eventId) events.push(change.eventId)
        after = change.item
      }

      // 3. minimum and step size, in the (possibly new) unit
      if (counted) {
        const tracking: ProductPatch = {}
        if (converting || minQuantity !== origMin) tracking.minQuantity = minQuantity
        if (converting ? step !== defaultStep(unit) : step !== origStep) tracking.stepSize = step
        if (Object.keys(tracking).length > 0) await patchProduct(product.id, tracking)
      }

      // 4. location: moving is a stock change, recorded and undoable
      if (moving && item) {
        const change = await moveStock(item.id, locationId)
        if (change.eventId) events.push(change.eventId)
        after = change.item
      }
    } catch (e) {
      setError(errorMessage(e))
      setSaving(false)
      void stock.reload()
      return
    }

    navigate('/')
    void stock.reload()
    let text = `Saved ${trimmed}`
    if (moving && from && to) text = `${trimmed}: ${from.name} → ${to.name}`
    else if (converting && item && after) text = `${trimmed}: ${describe(item)} → ${describe(after)}`
    if (events.length === 0) {
      toast.show(text)
      return
    }
    // Undo reverses this save's changes at home, newest first
    toast.show(text, () => {
      toast.dismiss()
      void (async () => {
        try {
          for (const eventId of [...events].reverse()) await undoStockChange(eventId)
        } catch (e) {
          toast.show(errorMessage(e))
        }
        void stock.reload()
      })()
    })
  }

  async function remove() {
    setSaving(true)
    try {
      await archiveProduct(product.id)
    } catch (e) {
      setError(errorMessage(e))
      setSaving(false)
      return
    }
    navigate('/')
    void stock.reload()
    toast.show(`Removed ${product.name} from Onhand`, () => {
      toast.dismiss()
      unarchiveProduct(product.id).then(
        () => void stock.reload(),
        (e: unknown) => toast.show(errorMessage(e)),
      )
    })
  }

  const steps = counted ? stepChoices(unit) : []

  return (
    <form
      className="add__form"
      onSubmit={(e) => {
        e.preventDefault()
        void save()
      }}
    >
      {item && (
        <p className="edit__context">
          Was “{product.name}” · {describe(item)} in {item.locationName}
        </p>
      )}

      <label className="field">
        <span className="field__label">Name</span>
        <input
          className="add__name"
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          autoComplete="off"
          maxLength={100}
        />
      </label>

      <div className="field">
        <div className="field__label" id="edit-kind">
          What is it?
        </div>
        <div role="group" aria-labelledby="edit-kind" className="field__row">
          {KINDS.map((k) => (
            <button
              key={k.value}
              type="button"
              className="chip chip--grow"
              aria-pressed={kind === k.value}
              onClick={() => setKind(k.value)}
            >
              {k.label}
            </button>
          ))}
        </div>
      </div>

      <div className="field">
        <div className="field__label" id="edit-place">
          Where does it live?
        </div>
        <div role="group" aria-labelledby="edit-place" className="field__wrap">
          {locations.map((l) => (
            <button
              key={l.id}
              type="button"
              className="chip"
              aria-pressed={l.id === locationId}
              onClick={() => setLocationId(l.id)}
              disabled={item === null}
            >
              {l.name}
            </button>
          ))}
        </div>
        {moving && item && from && to && (
          <p className="note">
            <ArrowRightIcon size={18} />
            Moves {describe(item)} from {from.name} to {to.name}
          </p>
        )}
      </div>

      <div className="field">
        <div className="field__label" id="edit-mode">
          How do you track it?
        </div>
        <div role="group" aria-labelledby="edit-mode" className="field__row">
          <button
            type="button"
            className="toggle-card"
            aria-pressed={counted}
            onClick={() => pickMode('COUNTED')}
            disabled={!canConvert && !wasCounted}
          >
            <span className="toggle-card__title">Count it</span>
            <span className="toggle-card__hint">3 tins, 485 g, 1 spare</span>
          </button>
          <button
            type="button"
            className="toggle-card"
            aria-pressed={!counted}
            onClick={() => pickMode('STATUS')}
            disabled={!canConvert && wasCounted}
          >
            <span className="toggle-card__title">Have / Low / Out</span>
            <span className="toggle-card__hint">Spices, oil, dish soap</span>
          </button>
        </div>
        {!canConvert && (
          <p className="field__hint">
            {places > 1
              ? `In ${places} places — change how it’s tracked while it’s in one.`
              : 'Not at home — add it first to change how it’s tracked.'}
          </p>
        )}
        {toStatus && item && (
          <p className="note">
            Will be set to {(item.quantity ?? 0) > 0 ? 'Have' : 'Out'} (you have {describe(item)})
          </p>
        )}
      </div>

      {counted && (
        <>
          <div className="field">
            <div className="field__label">Unit</div>
            <UnitPicker value={unit} onChange={pickUnit} disabled={!canConvert} idPrefix="edit-unit" />
          </div>

          {needsAmount && (
            <label className="field">
              <span className="field__label field__label--warn">
                {fromStatus ? 'How much do you have now?' : 'Unit changed — how much do you have now?'}
              </span>
              <span className="qty__box qty__box--start">
                <input
                  type="text"
                  inputMode="decimal"
                  autoComplete="off"
                  placeholder="0"
                  value={amountText}
                  onChange={(e) => {
                    if (DECIMAL.test(e.target.value)) setAmountText(e.target.value)
                  }}
                  required
                />
                <span className="qty__unit">{unitLabel(unit)}</span>
              </span>
            </label>
          )}

          {steps.length > 0 && (
            <div className="field">
              <div className="field__label" id="edit-step">
                − / + buttons move by
              </div>
              <div role="group" aria-labelledby="edit-step" className="field__wrap">
                {steps.map((s) => (
                  <button key={s} type="button" className="chip" aria-pressed={s === step} onClick={() => setStep(s)}>
                    {formatQuantity(unit, s)}
                  </button>
                ))}
              </div>
            </div>
          )}

          <div className="min-row">
            <div className="min-row__text">
              <span className="min-row__title" id="edit-min">
                Add to shopping list below
              </span>
              <span className="min-row__hint">Optional</span>
            </div>
            <button
              type="button"
              className="round-btn"
              onClick={() => setMinQuantity((m) => Math.max(0, round3(m - defaultStep(unit))))}
              aria-label="Lower threshold"
              disabled={minQuantity <= 0}
            >
              <MinusIcon size={16} strokeWidth={2.2} />
            </button>
            <div className="min-row__value" aria-live="polite" aria-labelledby="edit-min">
              {minQuantity === 0 ? 'Off' : formatQuantity(unit, minQuantity)}
            </div>
            <button
              type="button"
              className="round-btn"
              onClick={() => setMinQuantity((m) => round3(m + defaultStep(unit)))}
              aria-label="Raise threshold"
            >
              <PlusIcon size={16} strokeWidth={2.2} />
            </button>
          </div>
        </>
      )}

      {error && (
        <p className="add__error" role="alert">
          {error}
        </p>
      )}

      <div className="edit__remove">
        <button type="button" className="btn btn--outline edit__remove-btn" onClick={() => void remove()} disabled={saving}>
          <ArchiveIcon size={20} />
          Remove from Onhand
        </button>
        <p className="edit__remove-hint">Archived, not deleted. You can undo right after.</p>
      </div>

      <div className="add__footer">
        {changed ? (
          <button type="submit" className="btn btn--primary add__save" disabled={!canSave}>
            {saving ? 'Saving…' : moving ? 'Save and move' : 'Save changes'}
          </button>
        ) : (
          <button type="button" className="btn add__save add__save--idle" aria-disabled="true">
            No changes
          </button>
        )}
      </div>
    </form>
  )
}
