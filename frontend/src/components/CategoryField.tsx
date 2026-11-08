import { useEffect, useMemo, useState } from 'react'
import { listProducts } from '../api/products.ts'
import type { Kind, Product } from '../api/types.ts'

const MAX_CHIPS = 12

/** The categories already in use, per kind, in their stored spelling (sorted, no duplicates). */
function useCategories(kind: Kind): string[] {
  const [products, setProducts] = useState<Product[]>([])
  useEffect(() => {
    let cancelled = false
    listProducts().then(
      (loaded) => !cancelled && setProducts(loaded),
      () => {},
    )
    return () => {
      cancelled = true
    }
  }, [])
  return useMemo(() => {
    const seen = new Map<string, string>()
    for (const p of products) {
      if (p.kind !== kind || !p.category) continue
      const key = p.category.toLowerCase()
      if (!seen.has(key)) seen.set(key, p.category)
    }
    return [...seen.values()].sort((a, b) => a.localeCompare(b))
  }, [products, kind])
}

interface Props {
  value: string
  onChange: (value: string) => void
  kind: Kind
  idPrefix: string
  /** told the categories in use, so the form can save the existing spelling */
  onKnown?: (known: string[]) => void
}

/** "Category" (DESIGN.md, Screen 2): optional text, with the kind's categories as chips. */
export default function CategoryField({ value, onChange, kind, idPrefix, onKnown }: Props) {
  const known = useCategories(kind)
  useEffect(() => onKnown?.(known), [known, onKnown])

  const typed = value.trim().toLowerCase()
  const exact = known.find((c) => c.toLowerCase() === typed)
  const chips = exact
    ? [exact]
    : known.filter((c) => typed === '' || c.toLowerCase().includes(typed)).slice(0, MAX_CHIPS)

  return (
    <div className="field">
      <label className="field__label" htmlFor={`${idPrefix}-input`}>
        Category <span className="field__optional">· Optional</span>
      </label>
      <input
        id={`${idPrefix}-input`}
        className="text-input"
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="e.g. Grains, Cleaning"
        autoComplete="off"
        autoCapitalize="sentences"
        enterKeyHint="done"
        maxLength={50}
      />
      {chips.length > 0 && (
        <div role="group" aria-label="Categories you use" className="field__wrap">
          {chips.map((c) => (
            <button
              key={c}
              type="button"
              className="chip"
              aria-pressed={c === exact}
              onClick={() => onChange(c === exact ? '' : c)}
            >
              {c}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
