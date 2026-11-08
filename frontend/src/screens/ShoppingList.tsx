import { useState } from 'react'
import { errorMessage } from '../api/client.ts'
import { addShoppingItem, checkShoppingItem, checkShoppingProduct, putAway } from '../api/shopping.ts'
import { undoStockChange } from '../api/stock.ts'
import type { ShoppingItem, ShoppingList as List } from '../api/types.ts'
import { PlusIcon } from '../components/Icons.tsx'
import { formatQuantity } from '../lib/format.ts'
import { useToast } from '../lib/toast.ts'
import type { ShoppingState } from '../lib/useShopping.ts'
import type { StockState } from '../lib/useStock.ts'

const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? '' : 's'}`

/** Shopping list (DESIGN.md, Screen 3): running low, added by you, in the basket. */
export default function ShoppingList({ shopping, stock }: { shopping: ShoppingState; stock: StockState }) {
  const toast = useToast()
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)
  const list = shopping.list

  async function apply(call: () => Promise<List>) {
    try {
      shopping.set(await call())
    } catch (e) {
      toast.show(errorMessage(e))
      shopping.reload()
    }
  }

  async function add() {
    const name = draft.trim()
    if (!name) return
    try {
      shopping.set(await addShoppingItem(name))
      setDraft('')
    } catch (e) {
      toast.show(errorMessage(e))
    }
  }

  function toggle(item: ShoppingItem) {
    void apply(() =>
      item.id
        ? checkShoppingItem(item.id, !item.checked)
        : checkShoppingProduct(item.productId ?? '', !item.checked),
    )
  }

  /** `count` is what the button said: ticked items that are tracked (free text is just cleared). */
  async function putAwayTicked(count: number) {
    setBusy(true)
    try {
      const result = await putAway()
      shopping.set(result.list)
      void stock.reload()
      const skipped = result.skipped.length > 0 ? ` (${result.skipped.join(', ')}: no place yet)` : ''
      const text = `Put ${plural(count, 'item')} away${skipped}`
      const events = result.eventIds
      toast.show(
        text,
        events.length === 0
          ? undefined
          : () => {
              toast.dismiss()
              void (async () => {
                try {
                  for (const eventId of [...events].reverse()) await undoStockChange(eventId)
                } catch (e) {
                  toast.show(errorMessage(e))
                }
                void stock.reload()
              })()
            },
      )
    } catch (e) {
      toast.show(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const items = list?.items ?? []
  const groups: { title: string; items: ShoppingItem[] }[] = [
    { title: 'Running low at home', items: items.filter((i) => i.source === 'AUTO' && !i.checked) },
    { title: 'Added by you', items: items.filter((i) => i.source === 'MANUAL' && !i.checked) },
    { title: 'In the basket', items: items.filter((i) => i.checked) },
  ].filter((g) => g.items.length > 0)
  const trackedInBasket = items.filter((i) => i.checked && i.productId !== null).length

  return (
    <>
      <header className="screen-header">
        <div className="wordmark">ONHAND</div>
        <h1 className="page-title">Shopping list</h1>
        {list && (
          <div className="page-subtitle">
            {list.toBuy} to buy · {list.inBasket} in the basket
          </div>
        )}
      </header>

      <form
        className="shop-add"
        onSubmit={(e) => {
          e.preventDefault()
          void add()
        }}
      >
        <label className="shop-add__field">
          <span className="sr-only">Add to the shopping list</span>
          <input
            type="text"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="Add something…"
            autoComplete="off"
            enterKeyHint="done"
            maxLength={100}
          />
        </label>
        <button type="submit" className="shop-add__button" aria-label="Add to the list">
          <PlusIcon size={24} strokeWidth={2.4} />
        </button>
      </form>

      <main className={`shop-list${trackedInBasket > 0 ? ' shop-list--card' : ''}`}>
        {groups.map((group) => (
          <section key={group.title} className="place" aria-label={group.title}>
            <h2 className="place__title">{group.title}</h2>
            <ul className="place__card">
              {group.items.map((item) => (
                <ShoppingRow key={item.id ?? item.productId} item={item} onToggle={() => toggle(item)} />
              ))}
            </ul>
          </section>
        ))}
        {list && items.length === 0 && (
          <div className="home-empty">
            <div className="home-empty__title">Nothing to buy</div>
          </div>
        )}
      </main>

      {trackedInBasket > 0 && (
        <div className="put-away">
          <div className="put-away__card">
            <p className="put-away__text">Back home? Mark what you bought as in stock.</p>
            <button type="button" className="put-away__button" onClick={() => void putAwayTicked(trackedInBasket)} disabled={busy}>
              {busy ? 'Putting away…' : `Put ${plural(trackedInBasket, 'item')} away`}
            </button>
          </div>
        </div>
      )}
    </>
  )
}

function ShoppingRow({ item, onToggle }: { item: ShoppingItem; onToggle: () => void }) {
  const tag =
    item.checked || !item.reason
      ? null
      : item.reason === 'OUT'
        ? { label: 'Out', className: 'pill-tag pill-tag--out' }
        : item.reason === 'LOW'
          ? { label: 'Low', className: 'pill-tag pill-tag--low' }
          : { label: `Below ${formatQuantity(item.unit, item.minQuantity ?? 0)}`, className: 'pill-tag pill-tag--low' }
  return (
    <li>
      <label className={`shop-row${item.checked ? ' shop-row--done' : ''}`}>
        <input type="checkbox" checked={item.checked} onChange={onToggle} />
        <span className="shop-row__text">
          <span className="shop-row__name">{item.name}</span>
          {item.places && <span className="shop-row__where">{item.places}</span>}
        </span>
        {tag && <span className={tag.className}>{tag.label}</span>}
      </label>
    </li>
  )
}
