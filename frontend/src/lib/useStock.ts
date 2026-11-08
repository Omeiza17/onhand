import { useCallback, useEffect, useState } from 'react'
import { errorMessage } from '../api/client.ts'
import { listStock } from '../api/stock.ts'
import type { StockItem } from '../api/types.ts'

export interface StockState {
  /** null until the first load */
  items: StockItem[] | null
  error: string | null
  reload: () => Promise<void>
  /** Puts a changed item in place; `null` removes it (e.g. undo of the add that created it). */
  replace: (itemId: string, next: StockItem | null) => void
  /** A product's step size changed — update it on every location that holds it. */
  setStepSize: (productId: string, stepSize: number | null) => void
}

/** All stock, loaded once and refreshed whenever the app comes back to the foreground. */
export function useStock(): StockState {
  const [items, setItems] = useState<StockItem[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    try {
      setItems(await listStock())
      setError(null)
    } catch (e) {
      setError(errorMessage(e))
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    listStock().then(
      (loaded) => {
        if (!cancelled) setItems(loaded)
      },
      (e: unknown) => {
        if (!cancelled) setError(errorMessage(e))
      },
    )
    const onVisible = () => {
      if (document.visibilityState === 'visible') void reload()
    }
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      cancelled = true
      document.removeEventListener('visibilitychange', onVisible)
    }
  }, [reload])

  const replace = useCallback((itemId: string, next: StockItem | null) => {
    setItems((current) => {
      if (!current) return current
      return next ? current.map((i) => (i.id === itemId ? next : i)) : current.filter((i) => i.id !== itemId)
    })
  }, [])

  const setStepSize = useCallback((productId: string, stepSize: number | null) => {
    setItems((current) => current?.map((i) => (i.productId === productId ? { ...i, stepSize } : i)) ?? current)
  }, [])

  return { items, error, reload, replace, setStepSize }
}
