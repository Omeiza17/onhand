import { Link } from '../components/Link.tsx'

/** Placeholder until slice 5 builds the shopping list (DESIGN.md, Screen 3). */
export default function ShoppingList({ toBuy }: { toBuy: number }) {
  return (
    <>
      <header className="screen-header">
        <div className="wordmark">ONHAND</div>
        <h1 className="page-title">Shopping list</h1>
        <div className="page-subtitle">{toBuy} to buy</div>
      </header>
      <div className="placeholder">
        <p>The shopping list arrives in a later slice.</p>
        <Link href="/" className="pill-link">
          Back home
        </Link>
      </div>
    </>
  )
}
