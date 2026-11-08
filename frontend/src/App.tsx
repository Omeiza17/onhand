import TabBar from './components/TabBar.tsx'
import ToastProvider from './components/ToastProvider.tsx'
import { useRoute } from './lib/router.ts'
import { useShopping } from './lib/useShopping.ts'
import { useStock } from './lib/useStock.ts'
import AddProduct from './screens/AddProduct.tsx'
import EditProduct from './screens/EditProduct.tsx'
import Home from './screens/Home.tsx'
import ShoppingList from './screens/ShoppingList.tsx'

export default function App() {
  const { path, params, id } = useRoute()
  // Add and Edit product are focused tasks: their own sticky button instead of the tab bar
  const task = path === '/add' || path === '/product'
  const stock = useStock()
  const shopping = useShopping(stock.items)
  const toBuy = shopping.list?.toBuy ?? 0

  return (
    <ToastProvider>
      <div className={task ? 'app app--task' : 'app'}>
        {path === '/add' && <AddProduct key={params.get('name') ?? ''} initialName={params.get('name') ?? ''} stock={stock} />}
        {path === '/product' && id && (
          <EditProduct key={`${id}/${params.get('item')}`} productId={id} itemId={params.get('item')} stock={stock} />
        )}
        {path === '/shopping' && <ShoppingList shopping={shopping} stock={stock} />}
        {path === '/' && <Home stock={stock} />}
        {!task && <TabBar path={path} toBuy={toBuy} />}
      </div>
    </ToastProvider>
  )
}
