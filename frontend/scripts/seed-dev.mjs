// Loads the example household from docs/design (Main.dc.html) through the API, so the
// At home screen has something to show. Dev only. Safe to re-run: existing locations
// and products (matched by name) are left alone.
//
//   npm run seed                                   # backend on localhost:8080
//   API=http://localhost:8081/api/v1 npm run seed  # another port

const API = process.env.API ?? 'http://localhost:8080/api/v1'

const PLACES = ['Pantry', 'Fridge', 'Freezer', 'Under the sink', 'Bathroom']

// [name, category, kind, place, quantity-or-status, unit, step (if not the unit default), min]
const ITEMS = [
  ['Basmati rice', 'Grains', 'FOOD', 'Pantry', 1.5, 'kg', null, 0.5],
  ['Chickpeas', 'Tins', 'FOOD', 'Pantry', 3, 'tin', null, 1],
  ['Cumin', 'Spices', 'FOOD', 'Pantry', 'HAVE'],
  ['Olive oil', 'Oils', 'FOOD', 'Pantry', 'LOW'],
  ['Eggs', 'Dairy & eggs', 'FOOD', 'Fridge', 4, 'piece', null, 4],
  ['Milk', 'Dairy & eggs', 'FOOD', 'Fridge', 1, 'l', null, 1],
  ['Butter', 'Dairy & eggs', 'FOOD', 'Fridge', 'LOW'],
  ['Spinach', 'Veg', 'FOOD', 'Fridge', 'OUT'],
  ['Chicken breast', 'Meat', 'FOOD', 'Freezer', 485, 'g', null, 200],
  ['Garden peas', 'Veg', 'FOOD', 'Freezer', 500, 'g', 250, 250],
  ['Floor cleaner (spare)', 'Cleaning', 'HOUSEHOLD', 'Under the sink', 1, 'piece', null, 1],
  ['Dish soap', 'Cleaning', 'HOUSEHOLD', 'Under the sink', 'LOW'],
  ['Bin bags', 'Cleaning', 'HOUSEHOLD', 'Under the sink', 1, 'pack', null, 1],
  ['Toilet paper', 'Toiletries', 'HOUSEHOLD', 'Bathroom', 2, 'pack', null, 1],
  ['Shampoo', 'Toiletries', 'HOUSEHOLD', 'Bathroom', 'HAVE'],
]

async function call(method, path, body) {
  const res = await fetch(API + path, {
    method,
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  if (!res.ok) throw new Error(`${method} ${path} → ${res.status} ${text}`)
  return text ? JSON.parse(text) : undefined
}

const lower = (s) => s.toLowerCase()

const locations = new Map((await call('GET', '/locations')).map((l) => [lower(l.name), l.id]))
for (const name of PLACES) {
  if (!locations.has(lower(name))) locations.set(lower(name), (await call('POST', '/locations', { name })).id)
}

const existing = new Set((await call('GET', '/products')).map((p) => lower(p.name)))
let added = 0
for (const [name, category, kind, place, state, unit, step, min] of ITEMS) {
  if (existing.has(lower(name))) continue
  const locationId = locations.get(lower(place))
  const counted = typeof state === 'number'
  const product = await call('POST', '/products', {
    name,
    category,
    kind,
    trackingMode: counted ? 'COUNTED' : 'STATUS',
    defaultUnit: counted ? unit : undefined,
    minQuantity: counted ? min : undefined,
    defaultLocationId: locationId,
  })
  if (step) await call('PUT', `/products/${product.id}/step-size`, { stepSize: step })
  const { item } = await call('POST', '/stock', { productId: product.id, quantity: counted ? state : undefined })
  // a STATUS product starts at HAVE; cycle to LOW or OUT
  for (let s = 'HAVE'; !counted && s !== state; s = s === 'HAVE' ? 'LOW' : 'OUT') {
    await call('POST', `/stock/${item.id}/cycle`)
  }
  added++
}
console.log(`Seeded ${added} products (${ITEMS.length - added} already there) in ${PLACES.length} places via ${API}`)
