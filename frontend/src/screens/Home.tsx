import { useMemo, useRef, useState } from 'react'
import { ApiError, errorMessage } from '../api/client.ts'
import { setStepSize } from '../api/products.ts'
import {
  cycleStockStatus,
  decreaseStock,
  increaseStock,
  setStockQuantity,
  undoStockChange,
} from '../api/stock.ts'
import type { Kind, StockChange, StockItem } from '../api/types.ts'
import { SearchIcon } from '../components/Icons.tsx'
import StockRow, { type SheetMode } from '../components/StockRow.tsx'
import UpdateSheet from '../components/UpdateSheet.tsx'
import { describe, isOnList } from '../lib/format.ts'
import { Link } from '../components/Link.tsx'
import { useToast } from '../lib/toast.ts'
import type { StockState } from '../lib/useStock.ts'

type Filter = 'ALL' | Kind

const FILTERS: { value: Filter; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'FOOD', label: 'Food' },
  { value: 'HOUSEHOLD', label: 'Household' },
]

interface Place {
  id: string
  name: string
  items: StockItem[]
}

/** Groups items (already ordered by location, then name) into one card per location. */
function groupByLocation(items: StockItem[]): Place[] {
  const places: Place[] = []
  for (const item of items) {
    const last = places[places.length - 1]
    if (last && last.id === item.locationId) last.items.push(item)
    else places.push({ id: item.locationId, name: item.locationName, items: [item] })
  }
  return places
}

/** "What's at home": everything grouped by location, with search, filter and quick update. */
export default function Home({ stock }: { stock: StockState }) {
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState<Filter>('ALL')
  const [sheet, setSheet] = useState<{ itemId: string; opener: HTMLElement; mode: SheetMode } | null>(null)
  const toast = useToast()
  // latest request per item: a slower, older response must not overwrite a newer one
  const latest = useRef(new Map<string, number>())
  const sequence = useRef(0)

  const all = useMemo(() => stock.items ?? [], [stock.items])
  const q = query.trim().toLowerCase()
  const visible = useMemo(
    () =>
      all.filter(
        (i) => (filter === 'ALL' || i.kind === filter) && (q === '' || i.productName.toLowerCase().includes(q)),
      ),
    [all, filter, q],
  )
  const places = useMemo(() => groupByLocation(visible), [visible])
  const placeCount = useMemo(() => new Set(all.map((i) => i.locationId)).size, [all])
  const sheetItem = sheet ? all.find((i) => i.id === sheet.itemId) : undefined

  async function undo(before: StockItem, eventId: string) {
    toast.dismiss()
    latest.current.set(before.id, ++sequence.current)
    try {
      const change = await undoStockChange(eventId)
      stock.replace(before.id, change.item)
    } catch (e) {
      toast.show(
        e instanceof ApiError && e.status === 409 ? `Can’t undo: ${before.productName} changed since` : errorMessage(e),
      )
      void stock.reload()
    }
  }

  /** Runs one change and shows "{Name}: {old} → {new}" with Undo. */
  async function run(item: StockItem, call: () => Promise<StockChange>) {
    const seq = ++sequence.current
    latest.current.set(item.id, seq)
    try {
      const change = await call()
      if (latest.current.get(item.id) !== seq) {
        // a newer tap on this item already landed; resync instead of guessing
        void stock.reload()
        return
      }
      stock.replace(item.id, change.item)
      const eventId = change.eventId
      if (eventId && change.item) {
        toast.show(`${item.productName}: ${describe(item)} → ${describe(change.item)}`, () => void undo(item, eventId))
      }
    } catch (e) {
      toast.show(errorMessage(e))
      void stock.reload()
    }
  }

  async function changeStep(item: StockItem, stepSize: number) {
    try {
      const product = await setStepSize(item.productId, stepSize)
      stock.setStepSize(item.productId, product.effectiveStepSize)
    } catch (e) {
      toast.show(errorMessage(e))
    }
  }

  return (
    <>
      <header className="screen-header">
        <div className="wordmark">ONHAND</div>
        <h1 className="page-title">What’s at home</h1>
        {stock.items && (
          <div className="page-subtitle">
            {all.length} {all.length === 1 ? 'item' : 'items'} in {placeCount} {placeCount === 1 ? 'place' : 'places'}
          </div>
        )}
      </header>

      <div className="home-controls">
        <label className="search">
          <span className="sr-only">Search what’s at home</span>
          <SearchIcon size={20} />
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search rice, eggs, dish soap…"
            autoComplete="off"
            enterKeyHint="search"
          />
        </label>
        <div role="group" aria-label="Show" className="home-filters">
          {FILTERS.map((f) => (
            <button
              key={f.value}
              type="button"
              className="chip chip--sm"
              aria-pressed={filter === f.value}
              onClick={() => setFilter(f.value)}
            >
              {f.label}
            </button>
          ))}
        </div>
      </div>

      <main className="home-list">
        {stock.error && !stock.items && (
          <div className="home-empty">
            <div className="home-empty__title">{stock.error}</div>
            <button type="button" className="pill-link" onClick={() => void stock.reload()}>
              Try again
            </button>
          </div>
        )}

        {places.map((place) => (
          <section key={place.id} className="place" aria-labelledby={`place-${place.id}`}>
            <h2 id={`place-${place.id}`} className="place__title">
              {place.name} · {place.items.length}
            </h2>
            <ul className="place__card">
              {place.items.map((item) => (
                <StockRow
                  key={item.id}
                  item={item}
                  onDecrease={(i) => void run(i, () => decreaseStock(i.id))}
                  onIncrease={(i) => void run(i, () => increaseStock(i.id))}
                  onCycle={(i) => void run(i, () => cycleStockStatus(i.id))}
                  onList={isOnList(item, all)}
                  onOpenSheet={(i, opener, mode) => {
                    toast.dismiss()
                    setSheet({ itemId: i.id, opener, mode })
                  }}
                />
              ))}
            </ul>
          </section>
        ))}

        {stock.items && places.length === 0 && <EmptyState query={query.trim()} filter={filter} total={all.length} />}
      </main>

      {sheetItem && sheet && (
        <UpdateSheet
          item={sheetItem}
          initialMode={sheet.mode}
          returnFocusTo={sheet.opener}
          onClose={() => setSheet(null)}
          onStepChange={(i, s) => void changeStep(i, s)}
          onSubmit={(i, body) => {
            setSheet(null)
            void run(i, () => setStockQuantity(i.id, body))
          }}
        />
      )}
    </>
  )
}

function EmptyState({ query, filter, total }: { query: string; filter: Filter; total: number }) {
  if (query !== '') {
    return (
      <div className="home-empty">
        <div className="home-empty__title">Nothing called “{query}” at home</div>
        <Link href={`/add?name=${encodeURIComponent(query)}`} className="pill-link">
          Add it
        </Link>
      </div>
    )
  }
  if (total === 0) {
    return (
      <div className="home-empty">
        <div className="home-empty__title">Nothing at home yet</div>
        <Link href="/add" className="pill-link">
          Add a product
        </Link>
      </div>
    )
  }
  return (
    <div className="home-empty">
      <div className="home-empty__title">No {filter === 'FOOD' ? 'food' : 'household'} items at home</div>
    </div>
  )
}
