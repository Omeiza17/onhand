import { useMemo } from 'react'
import TabBar from './components/TabBar.tsx'
import ToastProvider from './components/ToastProvider.tsx'
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
    <ToastProvider>
      {/* Add product is a focused task: its own sticky button instead of the tab bar */}
      <div className={path === '/add' ? 'app app--task' : 'app'}>
        {path === '/add' && <AddProduct key={params.get('name') ?? ''} initialName={params.get('name') ?? ''} stock={stock} />}
        {path === '/shopping' && <ShoppingList toBuy={toBuy} />}
        {path === '/' && <Home stock={stock} />}
        {path !== '/add' && <TabBar path={path} toBuy={toBuy} />}
      </div>
    </ToastProvider>
  )
}
