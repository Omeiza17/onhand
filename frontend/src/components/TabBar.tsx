import type { Path } from '../lib/router.ts'
import { Link } from '../components/Link.tsx'
import { HomeIcon, ListIcon, PlusIcon } from './Icons.tsx'

/** Bottom tab bar on every main screen: At home · + (Add product) · Shopping. */
export default function TabBar({ path, toBuy }: { path: Path; toBuy: number }) {
  return (
    <nav className="tabbar" aria-label="Main">
      <Link href="/" className="tabbar__tab" aria-current={path === '/' ? 'page' : undefined}>
        <HomeIcon />
        At home
      </Link>
      <Link href="/add" className="tabbar__add" aria-label="Add product">
        <PlusIcon size={26} strokeWidth={2.4} />
      </Link>
      <Link href="/shopping" className="tabbar__tab" aria-current={path === '/shopping' ? 'page' : undefined}>
        <ListIcon />
        Shopping
        {toBuy > 0 && (
          <>
            <span className="tabbar__badge" aria-hidden="true">
              {toBuy}
            </span>
            <span className="sr-only">, {toBuy} to buy</span>
          </>
        )}
      </Link>
    </nav>
  )
}
