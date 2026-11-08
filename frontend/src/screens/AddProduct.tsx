import { useEffect, useMemo, useState } from 'react'
import { errorMessage } from '../api/client.ts'
import { createLocation, listLocations } from '../api/locations.ts'
import { createProduct, searchProducts } from '../api/products.ts'
import { addStock, undoStockChange } from '../api/stock.ts'
import type {
  Kind,
  Location,
  Product,
  StockChange,
  StockItem,
  StockStatus,
  TrackingMode,
  Unit,
} from '../api/types.ts'
import { MinusIcon, PlusIcon } from '../components/Icons.tsx'
import CategoryField from '../components/CategoryField.tsx'
import { Link } from '../components/Link.tsx'
import UnitPicker from '../components/UnitPicker.tsx'
import { defaultStep, describe, formatQuantity, num, parseDecimal, unitLabel } from '../lib/format.ts'
import { canonicalCategory } from '../lib/category.ts'
import { navigate } from '../lib/router.ts'
import { useToast } from '../lib/toast.ts'
import type { StockState } from '../lib/useStock.ts'

const KINDS: { value: Kind; label: string }[] = [
  { value: 'FOOD', label: 'Food' },
  { value: 'HOUSEHOLD', label: 'Household' },
]

const STATUSES: { value: StockStatus; label: string }[] = [
  { value: 'HAVE', label: 'Have it' },
  { value: 'LOW', label: 'Low' },
  { value: 'OUT', label: 'Out' },
]


const DECIMAL = /^\d*[.,]?\d*$/
const round3 = (n: number) => Math.round(n * 1000) / 1000

/** Where a tracked product is, for the autocomplete: "Freezer · 485 g", "2 places", "Not at home". */
function whereText(items: StockItem[]): string {
  if (items.length === 0) return 'Not at home'
  if (items.length > 1) return `${items.length} places`
  return `${items[0].locationName} · ${describe(items[0])}`
}

/**
 * Add product (DESIGN.md, Screen 2): a new product and how much of it is at home, in a few
 * taps — or, from the autocomplete, restock one that is already tracked.
 */
export default function AddProduct({ initialName, stock }: { initialName: string; stock: StockState }) {
  const toast = useToast()

  const [name, setName] = useState(initialName)
  const [kind, setKind] = useState<Kind>('FOOD')
  const [category, setCategory] = useState('')
  const [knownCategories, setKnownCategories] = useState<string[]>([])
  const [mode, setMode] = useState<TrackingMode>('COUNTED')
  const [unit, setUnit] = useState<Unit>('piece')
  const [qtyText, setQtyText] = useState('1')
  const [status, setStatus] = useState<StockStatus>('HAVE')
  const [minQuantity, setMinQuantity] = useState(0)

  const [locations, setLocations] = useState<Location[] | null>(null)
  const [pickedLocationId, setPickedLocationId] = useState<string | null>(null)
  const [newPlace, setNewPlace] = useState<string | null>(null)

  const [search, setSearch] = useState<{ query: string; results: Product[] }>({ query: '', results: [] })
  /** an already-tracked COUNTED product being restocked */
  const [restock, setRestock] = useState<Product | null>(null)
  /** set once the product exists, so a retry after a failed stock step doesn't create it twice */
  const [createdProductId, setCreatedProductId] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    listLocations().then(
      (loaded) => !cancelled && setLocations(loaded),
      (e: unknown) => !cancelled && setError(errorMessage(e)),
    )
    return () => {
      cancelled = true
    }
  }, [])

  // autocomplete from 2 typed characters
  const query = name.trim()
  useEffect(() => {
    if (query.length < 2 || restock) return
    let cancelled = false
    const timer = window.setTimeout(() => {
      searchProducts(query).then(
        (results) => !cancelled && setSearch({ query, results }),
        () => {},
      )
    }, 150)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [query, restock])

  const matches = useMemo(() => {
    if (query.length < 2 || search.query !== query) return []
    const q = query.toLowerCase()
    return [...search.results]
      .sort((a, b) => Number(!a.name.toLowerCase().startsWith(q)) - Number(!b.name.toLowerCase().startsWith(q)))
      .slice(0, 3)
  }, [query, search])
  const exactMatch = matches.find((p) => p.name.toLowerCase() === query.toLowerCase())

  const itemsOf = (productId: string) => (stock.items ?? []).filter((i) => i.productId === productId)

  // No silent default (changelog 5): the place you picked; else, for a new product, a suggestion —
  // where the most recently updated item of the selected kind lives (follows the kind until you
  // pick); else nothing, and the button asks you to choose.
  const suggestedLocationId = useMemo(() => {
    const known = new Set((locations ?? []).map((l) => l.id))
    let latest: StockItem | null = null
    for (const item of stock.items ?? []) {
      if (item.kind !== kind || !known.has(item.locationId)) continue
      if (!latest || Date.parse(item.updatedAt) > Date.parse(latest.updatedAt)) latest = item
    }
    return latest?.locationId ?? null
  }, [stock.items, kind, locations])
  const locationId = pickedLocationId ?? (restock ? null : suggestedLocationId)
  const location = locations?.find((l) => l.id === locationId)

  const countedUnit: Unit = restock?.defaultUnit ?? unit
  const step = restock ? (restock.effectiveStepSize ?? defaultStep(countedUnit)) : defaultStep(unit)
  const quantity = parseDecimal(qtyText)
  const counting = restock !== null || mode === 'COUNTED'

  const canSave =
    !saving &&
    locationId !== null &&
    (restock !== null || (query.length > 0 && !exactMatch)) &&
    (!counting || (Number.isFinite(quantity) && quantity >= 0))

  function pickUnit(u: Unit) {
    setUnit(u)
    setQtyText(num(defaultStep(u)))
    setMinQuantity(0)
  }

  function nudge(delta: number) {
    const current = Number.isFinite(quantity) ? quantity : 0
    setQtyText(num(Math.max(0, round3(current + delta))))
  }

  function startRestock(product: Product) {
    const here = itemsOf(product.id)[0]
    setRestock(product)
    setQtyText(num(product.effectiveStepSize ?? defaultStep(product.defaultUnit ?? 'piece')))
    setPickedLocationId(here?.locationId ?? product.defaultLocationId ?? locationId)
    setError(null)
  }

  /** Back home with "{change}" and Undo. */
  function finish(productName: string, before: StockItem | undefined, change: StockChange, placeName: string) {
    navigate('/')
    void stock.reload()
    const after = change.item
    const eventId = change.eventId
    if (!after) return
    if (!eventId) {
      toast.show(`${productName}: already ${describe(after)}`)
      return
    }
    const text = before
      ? `${productName}: ${describe(before)} → ${describe(after)}`
      : `Added ${productName} · ${describe(after)} to ${placeName}`
    toast.show(text, () => {
      toast.dismiss()
      undoStockChange(eventId).then(
        () => void stock.reload(),
        (e: unknown) => {
          toast.show(errorMessage(e))
          void stock.reload()
        },
      )
    })
  }

  /** "Restock" on a STATUS product: one tap, back to Have. */
  async function restockStatus(product: Product) {
    const here = itemsOf(product.id)[0]
    const target = here?.locationId ?? product.defaultLocationId ?? locationId
    if (!target) {
      setError('Choose where it lives first.')
      return
    }
    setSaving(true)
    try {
      const change = await addStock({ productId: product.id, locationId: target, status: 'HAVE' })
      finish(product.name, here, change, change.item?.locationName ?? '')
    } catch (e) {
      setError(errorMessage(e))
      setSaving(false)
    }
  }

  async function save() {
    if (!canSave || !locationId || !location) return
    setSaving(true)
    setError(null)
    try {
      if (restock) {
        const before = itemsOf(restock.id).find((i) => i.locationId === locationId)
        const change = await addStock({ productId: restock.id, locationId, quantity: round3(quantity) })
        finish(restock.name, before, change, location.name)
        return
      }
      let productId = createdProductId
      if (!productId) {
        const product = await createProduct({
          name: query,
          kind,
          category: canonicalCategory(category, knownCategories) || null,
          trackingMode: mode,
          defaultUnit: mode === 'COUNTED' ? unit : null,
          defaultLocationId: locationId,
          minQuantity: mode === 'COUNTED' && minQuantity > 0 ? minQuantity : null,
        })
        productId = product.id
        setCreatedProductId(product.id)
      }
      const change = await addStock(
        mode === 'COUNTED'
          ? { productId, locationId, quantity: round3(quantity) }
          : { productId, locationId, status },
      )
      finish(query, undefined, change, location.name)
    } catch (e) {
      setError(errorMessage(e))
      setSaving(false)
    }
  }

  async function addPlace() {
    const placeName = newPlace?.trim()
    if (!placeName) return
    try {
      const created = await createLocation(placeName)
      setLocations((current) => [...(current ?? []), created])
      setPickedLocationId(created.id)
      setNewPlace(null)
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  return (
    <div className="add">
      <header className="add__header">
        <Link href="/" className="add__cancel">
          Cancel
        </Link>
        <h1 className="add__title">Add product</h1>
        <span className="add__header-spacer" aria-hidden="true" />
      </header>

      <form
        className="add__form"
        onSubmit={(e) => {
          e.preventDefault()
          void save()
        }}
      >
        {/* --- name + autocomplete, or the product being restocked --- */}
        {restock ? (
          <div className="field">
            <div className="field__label">Restocking</div>
            <div className="restock">
              <div className="restock__text">
                <span className="restock__name">{restock.name}</span>
                <span className="restock__where">Already tracked · {whereText(itemsOf(restock.id))}</span>
              </div>
              <button type="button" className="restock__change" onClick={() => setRestock(null)}>
                Change
              </button>
            </div>
          </div>
        ) : (
          <div className="field">
            <label className="field">
              <span className="field__label">Name</span>
              <input
                className="add__name"
                type="text"
                value={name}
                onChange={(e) => {
                  setName(e.target.value)
                  setCreatedProductId(null)
                  setError(null)
                }}
                placeholder="e.g. Basmati rice, Dish soap"
                autoComplete="off"
                autoCapitalize="sentences"
                enterKeyHint="next"
                maxLength={100}
                autoFocus={initialName === ''}
              />
            </label>
            {query.length >= 2 && search.query === query && (
              <ul className="suggest" aria-label="Already tracked">
                {matches.map((p) => (
                  <li key={p.id} className="suggest__row">
                    <div className="suggest__text">
                      <span className="suggest__name">{p.name}</span>
                      <span className="suggest__where">Already tracked · {whereText(itemsOf(p.id))}</span>
                    </div>
                    <button
                      type="button"
                      className={`suggest__restock${p === exactMatch ? ' suggest__restock--match' : ''}`}
                      onClick={() => (p.trackingMode === 'STATUS' ? void restockStatus(p) : startRestock(p))}
                      aria-label={`Restock ${p.name}`}
                    >
                      Restock
                    </button>
                  </li>
                ))}
                <li className="suggest__new">
                  {exactMatch ? `“${exactMatch.name}” is already tracked — restock it above` : `New product: “${query}”`}
                </li>
              </ul>
            )}
          </div>
        )}

        {/* --- what / how (new products only) --- */}
        {!restock && (
          <>
            <div className="field">
              <div className="field__label" id="kind-label">
                What is it?
              </div>
              <div role="group" aria-labelledby="kind-label" className="field__row">
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

            <CategoryField
              value={category}
              onChange={setCategory}
              kind={kind}
              idPrefix="add-category"
              onKnown={setKnownCategories}
            />

            <div className="field">
              <div className="field__label" id="mode-label">
                How do you track it?
              </div>
              <div role="group" aria-labelledby="mode-label" className="field__row">
                <button
                  type="button"
                  className="toggle-card"
                  aria-pressed={mode === 'COUNTED'}
                  onClick={() => setMode('COUNTED')}
                >
                  <span className="toggle-card__title">Count it</span>
                  <span className="toggle-card__hint">3 tins, 485 g, 1 spare</span>
                </button>
                <button
                  type="button"
                  className="toggle-card"
                  aria-pressed={mode === 'STATUS'}
                  onClick={() => setMode('STATUS')}
                >
                  <span className="toggle-card__title">Have / Low / Out</span>
                  <span className="toggle-card__hint">Spices, oil, dish soap</span>
                </button>
              </div>
            </div>
          </>
        )}

        {/* --- how much / right now --- */}
        {counting ? (
          <div className="field field--loose">
            <div className="field__label">{restock ? 'How much did you get?' : 'How much?'}</div>
            <div className="qty">
              <button
                type="button"
                className="round-btn round-btn--lg"
                onClick={() => nudge(-step)}
                aria-label={`Less, by ${formatQuantity(countedUnit, step)}`}
              >
                <MinusIcon size={20} strokeWidth={2.2} />
              </button>
              <label className="qty__box">
                <span className="sr-only">Quantity in {unitLabel(countedUnit)}</span>
                <input
                  type="text"
                  inputMode="decimal"
                  autoComplete="off"
                  value={qtyText}
                  onChange={(e) => {
                    if (DECIMAL.test(e.target.value)) setQtyText(e.target.value)
                  }}
                />
                <span className="qty__unit">{unitLabel(countedUnit)}</span>
              </label>
              <button
                type="button"
                className="round-btn round-btn--lg"
                onClick={() => nudge(step)}
                aria-label={`More, by ${formatQuantity(countedUnit, step)}`}
              >
                <PlusIcon size={20} strokeWidth={2.2} />
              </button>
            </div>
            {!restock && <UnitPicker value={unit} onChange={pickUnit} idPrefix="add-unit" />}
          </div>
        ) : (
          <div className="field">
            <div className="field__label" id="status-label">
              Right now you…
            </div>
            <div role="group" aria-labelledby="status-label" className="field__row">
              {STATUSES.map((s) => (
                <button
                  key={s.value}
                  type="button"
                  className="chip chip--grow"
                  aria-pressed={status === s.value}
                  onClick={() => setStatus(s.value)}
                >
                  {s.label}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* --- where --- */}
        <div className="field">
          <div className="field__label" id="place-label">
            Where does it live?
          </div>
          <div role="group" aria-labelledby="place-label" className="field__wrap">
            {(locations ?? []).map((l) => (
              <button
                key={l.id}
                type="button"
                className="chip"
                aria-pressed={l.id === locationId}
                onClick={() => setPickedLocationId(l.id)}
              >
                {l.name}
              </button>
            ))}
            {newPlace === null && locations && (
              <button type="button" className="chip chip--quiet" onClick={() => setNewPlace('')}>
                + New place
              </button>
            )}
          </div>
          {newPlace !== null && (
            <div className="new-place">
              <label className="new-place__input">
                <span className="sr-only">New place name</span>
                <input
                  type="text"
                  value={newPlace}
                  onChange={(e) => setNewPlace(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault()
                      void addPlace()
                    }
                  }}
                  placeholder="e.g. Pantry, Under the sink"
                  maxLength={60}
                  autoFocus
                />
              </label>
              <button type="button" className="chip" onClick={() => void addPlace()} disabled={!newPlace.trim()}>
                Add
              </button>
              <button type="button" className="chip chip--quiet" onClick={() => setNewPlace(null)}>
                Cancel
              </button>
            </div>
          )}
        </div>

        {/* --- shopping-list threshold (new COUNTED products) --- */}
        {!restock && mode === 'COUNTED' && (
          <div className="min-row">
            <div className="min-row__text">
              <span className="min-row__title" id="min-label">
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
            <div className="min-row__value" aria-live="polite" aria-labelledby="min-label">
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
        )}

        {error && (
          <p className="add__error" role="alert">
            {error}
          </p>
        )}

        <div className="add__footer">
          <button type="submit" className="btn btn--primary add__save" disabled={!canSave}>
            {saving ? 'Adding…' : location ? `Add to ${location.name}` : 'Choose where it lives'}
          </button>
        </div>
      </form>
    </div>
  )
}
