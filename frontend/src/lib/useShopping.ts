import { useCallback, useEffect, useState } from 'react'
import { getShoppingList } from '../api/shopping.ts'
import type { ShoppingList, StockItem } from '../api/types.ts'

export interface ShoppingState {
  list: ShoppingList | null
  set: (list: ShoppingList) => void
  reload: () => void
}

/**
 * The shopping list (and the tab badge). Running-low items come from stock, so the list is
 * fetched again whenever the stock list changes.
 */
export function useShopping(stockItems: StockItem[] | null): ShoppingState {
  const [list, setList] = useState<ShoppingList | null>(null)
  const [version, setVersion] = useState(0)

  useEffect(() => {
    let cancelled = false
    getShoppingList().then(
      (loaded) => !cancelled && setList(loaded),
      () => {},
    )
    return () => {
      cancelled = true
    }
  }, [stockItems, version])

  const reload = useCallback(() => setVersion((v) => v + 1), [])
  return { list, set: setList, reload }
}
