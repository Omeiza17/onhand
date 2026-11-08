import type { StockItem, StockStatus } from '../api/types.ts'
import { describe, formatQuantity, isBelowMinimum, NEXT_STATUS, STATUS_LABEL } from '../lib/format.ts'
import { MinusIcon, PlusIcon } from './Icons.tsx'

interface Props {
  item: StockItem
  onDecrease: (item: StockItem) => void
  onIncrease: (item: StockItem) => void
  onOpenSheet: (item: StockItem, opener: HTMLButtonElement) => void
  onCycle: (item: StockItem) => void
}

/** One product at one location: name + category, then −/amount/+ (COUNTED) or a status pill. */
export default function StockRow({ item, onDecrease, onIncrease, onOpenSheet, onCycle }: Props) {
  const name = item.productName
  return (
    <li className="row">
      <div className="row__text">
        <div className="row__name">{name}</div>
        <div className="row__meta">
          {item.category && <span>{item.category}</span>}
          {isBelowMinimum(item) && <span className="row__on-list">· On list</span>}
        </div>
      </div>

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

function CountedButtons({ item, onDecrease, onIncrease, onOpenSheet }: Omit<Props, 'onCycle'>) {
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
      <button
        type="button"
        className="row__amount"
        onClick={(e) => onOpenSheet(item, e.currentTarget)}
        aria-label={`${item.productName}: ${amount}. Set exact amount`}
        aria-haspopup="dialog"
      >
        {amount}
      </button>
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
