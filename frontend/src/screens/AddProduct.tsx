import { Link } from '../components/Link.tsx'

/** Placeholder until slice 4 builds the Add product screen (DESIGN.md, Screen 2). */
export default function AddProduct({ name }: { name: string }) {
  return (
    <>
      <header className="screen-header">
        <div className="wordmark">ONHAND</div>
        <h1 className="page-title">Add product</h1>
      </header>
      <div className="placeholder">
        <p>
          Adding {name ? `“${name}”` : 'products'} arrives in the next slice.
        </p>
        <Link href="/" className="pill-link">
          Back home
        </Link>
      </div>
    </>
  )
}
