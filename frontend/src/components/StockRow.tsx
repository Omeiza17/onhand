import type { StockItem, StockStatus } from '../api/types.ts'
import { describe, formatQuantity, isOut, NEXT_STATUS, STATUS_LABEL } from '../lib/format.ts'
import { MinusIcon, PlusIcon } from './Icons.tsx'
import { Link } from './Link.tsx'

export type SheetMode = 'used' | 'now'

interface Props {
  item: StockItem
  /** the product is on the shopping list (shows "· On list") */
  onList: boolean
  onDecrease: (item: StockItem) => void
  onIncrease: (item: StockItem) => void
  onOpenSheet: (item: StockItem, opener: HTMLButtonElement, mode: SheetMode) => void
  onCycle: (item: StockItem) => void
}

/** One product at one location: name + category, then −/amount/+ (COUNTED) or a status pill. */
export default function StockRow({ item, onList, onDecrease, onIncrease, onOpenSheet, onCycle }: Props) {
  const name = item.productName
  return (
    <li className="row">
      {/* the whole name area opens Edit product for this row */}
      <Link
        href={`/product/${item.productId}?item=${item.id}`}
        className="row__text row__link"
      >
        <span className="row__name">
          {name}
          <span className="sr-only">, edit</span>
        </span>
        <span className="row__meta">
          {item.category && <span>{item.category}</span>}
          {onList && <span className="row__on-list">· On list</span>}
        </span>
      </Link>

      {item.trackingMode === 'COUNTED' ? (
        <div className="row__counter">
          <CountedButtons item={item} onDecrease={onDecrease} onIncrease={onIncrease} onOpenSheet={onOpenSheet} />
        </div>
      ) : (
        item.status && <StatusPill name={name} status={item.status} onClick={() => onCycle(item)} />
      )}
    </li>
  )
}

function CountedButtons({ item, onDecrease, onIncrease, onOpenSheet }: Omit<Props, 'onCycle' | 'onList'>) {
  const step = formatQuantity(item.unit, item.stepSize ?? 1)
  const amount = describe(item)
  return (
    <>
      <button
        type="button"
        className="round-btn"
        onClick={() => onDecrease(item)}
        disabled={(item.quantity ?? 0) <= 0}
        aria-label={`Use ${step} ${item.productName}`}
      >
        <MinusIcon size={18} strokeWidth={2.2} />
      </button>
      {isOut(item) ? (
        // at 0 the amount reads "Out" like the Out pill; the sheet opens on "Now I have"
        <button
          type="button"
          className="pill pill--out row__out"
          onClick={(e) => onOpenSheet(item, e.currentTarget, 'now')}
          aria-label={`${item.productName}: out. Set amount`}
          aria-haspopup="dialog"
        >
          Out
        </button>
      ) : (
        <button
          type="button"
          className="row__amount"
          onClick={(e) => onOpenSheet(item, e.currentTarget, 'used')}
          aria-label={`${item.productName}: ${amount}. Set exact amount`}
          aria-haspopup="dialog"
        >
          {amount}
        </button>
      )}
      <button
        type="button"
        className="round-btn"
        onClick={() => onIncrease(item)}
        aria-label={`Add ${step} ${item.productName}`}
      >
        <PlusIcon size={18} strokeWidth={2.2} />
      </button>
    </>
  )
}

function StatusPill({ name, status, onClick }: { name: string; status: StockStatus; onClick: () => void }) {
  const label = STATUS_LABEL[status]
  const next = STATUS_LABEL[NEXT_STATUS[status]].toLowerCase()
  return (
    <button
      type="button"
      className={`pill pill--${status.toLowerCase()}`}
      onClick={onClick}
      aria-label={`${name}: ${label.toLowerCase()}. Tap to mark ${next}`}
    >
      {label}
    </button>
  )
}
