import { useSyncExternalStore } from 'react'

// A tiny History-API router: three screens don't need a routing library.

export type Path = '/' | '/add' | '/shopping'

const NAV_EVENT = 'onhand:navigate'

function subscribe(onChange: () => void) {
  window.addEventListener('popstate', onChange)
  window.addEventListener(NAV_EVENT, onChange)
  return () => {
    window.removeEventListener('popstate', onChange)
    window.removeEventListener(NAV_EVENT, onChange)
  }
}

const snapshot = () => window.location.pathname + window.location.search

export function navigate(to: string) {
  if (to === snapshot()) return
  window.history.pushState(null, '', to)
  window.dispatchEvent(new Event(NAV_EVENT))
  window.scrollTo(0, 0)
}

export function useRoute(): { path: Path; params: URLSearchParams } {
  const location = useSyncExternalStore(subscribe, snapshot)
  const url = new URL(location, window.location.origin)
  const path = (['/', '/add', '/shopping'] as const).find((p) => p === url.pathname) ?? '/'
  return { path, params: url.searchParams }
}
