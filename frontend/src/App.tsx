import { useMemo } from 'react'
import TabBar from './components/TabBar.tsx'
import { countToBuy } from './lib/format.ts'
import { useRoute } from './lib/router.ts'
import { useStock } from './lib/useStock.ts'
import AddProduct from './screens/AddProduct.tsx'
import Home from './screens/Home.tsx'
import ShoppingList from './screens/ShoppingList.tsx'

export default function App() {
  const { path, params } = useRoute()
  const stock = useStock()
  const toBuy = useMemo(() => countToBuy(stock.items ?? []), [stock.items])

  return (
    <div className="app">
      {path === '/add' && <AddProduct name={params.get('name') ?? ''} />}
      {path === '/shopping' && <ShoppingList toBuy={toBuy} />}
      {path === '/' && <Home stock={stock} />}
      <TabBar path={path} toBuy={toBuy} />
    </div>
  )
}
