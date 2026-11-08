# Onhand — Product Spec

## Problem

I keep a lot of things at home that get used up and re-bought — food and
ingredients, but also household consumables (cleaning products, toiletries,
toilet paper, bin bags, batteries) — and I lose track of what I have.
The one question this app must answer, quickly, from my phone:

> **"What do I have at home right now, and where is it?"**

Shopping is the second use: knowing what's low or gone before/while I'm at the shop.

**Scope line: things that get used up and re-bought.** Durable belongings
(appliances, tools, chargers) are out of scope — they have no in/out flow and
would clutter the list. Recipes are also **out of scope** — they live in Obsidian.

## Users

- One household, one or two people.
- Used mostly on a phone (installed PWA), sometimes on a laptop.
- Self-hosted on a home k3s cluster, reachable only on the private network.

## Guiding principle

**Updating must be cheaper than not updating.** The app is only as accurate as
the last update. Every design decision should make logging faster:

- Marking something used/finished is **one tap**.
- Adding a new product takes **under 10 seconds**.
- Nothing is required that isn't needed to answer "what do I have?".

## Core concepts

### Product
A kind of thing I keep (e.g. "Basmati rice", "Eggs", "Cumin", "Floor cleaner",
"Toilet paper").

| Field | Notes |
|---|---|
| name | required, unique (case-insensitive) |
| kind | **new** — `FOOD` or `HOUSEHOLD`, required, default `FOOD`. Drives the Home filter. |
| category | optional free text or simple list (Grains, Dairy, Spices, Cleaning, Toiletries…) |
| tracking_mode | `COUNTED` or `STATUS` (see below) |
| default_unit | for COUNTED only. Measured (metric, UN/ECE Rec 20): `g`, `kg`, `ml`, `l`. Counted (UN/ECE Rec 21 package types): `piece`, `pack`, `tin`, `bottle`, `jar`, `roll`, `box`, `bag`, `carton`, `tube`, `tub`, `bar`, `sachet`, `bunch` |
| default_location | optional |
| min_quantity | optional, COUNTED only — below this → goes on shopping list |
| step_size | **new** — COUNTED only, nullable. How much −/+ moves. Null = unit default: counted units 1, g 100, kg 0.5, ml 250, l 0.5. Set from the Home update panel, not on Add product. |
| barcode | optional, nullable, unique when present (Phase 2) |
| archived_at | **new** — nullable timestamp. Set = removed from Home, search, autocomplete and shopping list. Never hard-delete: stock events reference the product. |

### Tracking modes
- **COUNTED** — amount matters: "3 tins", "485 g", "6 rolls". Quick update = +/−
  by the product's step size (one tap), **or** tap the amount to set it exactly
  (see Quick update below).
- **STATUS** — amount not worth logging (spices, oil, salt, floor cleaner).
  Values: `HAVE`, `LOW`, `OUT`. Quick update = tap to cycle.

This hybrid is the main thing that keeps the app easier to maintain than tools
that count everything.

**Recommended pattern for household consumables — track the spare.** For things
you only notice are empty mid-use (cleaning products, shampoo), track spares as
COUNTED with `min_quantity = 1` (e.g. "Floor cleaner: 1 spare"). Opening the last
spare = one tap −, which puts it on the shopping list while a bottle is still in
use. This is guidance, not a separate feature; the model already supports it.

### Location
Where things live. User-defined, ordered. Seed/examples: Pantry, Fridge, Freezer,
Under the sink, Bathroom, Laundry.

### StockItem (current state)
One row per product per location.

| Field | Notes |
|---|---|
| product_id, location_id | unique together |
| quantity | decimal, COUNTED only |
| status | HAVE/LOW/OUT, STATUS only |
| expires_on | optional, nullable (Phase 2 UI; column exists from day one) |
| updated_at | |

### StockEvent (append-only log)
Every change is recorded: `ADDED`, `USED`, `MOVED`, `ADJUSTED`, `STATUS_CHANGED`.

| Field | Notes |
|---|---|
| product_id, location_id | |
| type | enum above |
| quantity_delta | nullable |
| status_from / status_to | nullable |
| occurred_at | |
| note | optional |

Purpose: history and **undo of the last action**. The StockItem table is updated
in the same transaction so reads stay simple.

### ShoppingListItem
- Auto-added when a STATUS product goes `LOW`/`OUT`, or a COUNTED product drops
  below `min_quantity`.
- Can also be added manually (including free-text items that aren't products).
- Checked off when bought; checking off a product item offers to restock it.

## MVP screens

1. **Home — "What's at home"**
   Everything grouped by location, with search and a filter: **All · Food ·
   Household** (by Product `kind`). Each row shows quantity or status.
   This is the core of the app.
2. **Quick update** (on Home, not a separate page)
   - STATUS: tap the pill to cycle HAVE→LOW→OUT.
   - COUNTED, one tap: −/+ moves by `step_size`.
   - COUNTED, exact: tap the amount → bottom panel with "I used" / "Now I have",
     a decimal input, quick amounts (1×step, 2×step, Half, All of it) when using,
     a live result preview, and the product's step-size choices.
     Example: 485 g chicken, used 200 → 285 g.
   - An undo toast after every change. No confirmation dialogs.
3. **Add product**
   Name (with autocomplete against existing products), kind (Food/Household),
   tracking mode, location, optional quantity. The quantity field accepts typed
   decimal input (e.g. 485 g), with +/− as a shortcut. Under 10 seconds.
4. **Edit product** — opened by tapping a product's name on Home.
   Same layout as Add product, prefilled: name, kind, location, tracking mode,
   unit, step size (measured units), min quantity. Rules:
   - **Location change = stock move**, recorded as a `MOVED` StockEvent and
     undoable — not a silent field update.
   - **Mode change needs an explicit conversion:** COUNTED → STATUS sets HAVE if
     quantity > 0, else OUT (shown before saving). STATUS → COUNTED requires the
     current amount.
   - **Unit change requires re-entering the current amount** — no automatic
     conversion between e.g. `bottle` and `ml`.
   - **Remove from Onhand** archives the product (`archived_at`), no confirmation
     dialog; the Home undo toast can restore it.
   - Save is disabled until something changed. Editing stock history is out of scope.
5. **Shopping list**
   Generated items + manual items, check off while shopping.

## Out of scope (MVP)

- Recipes, meal planning
- Durable belongings / home inventory (appliances, tools) — different app
- Grouping the shopping list by shop (supermarket, pharmacy, hardware) — later, if needed
- Barcode scanning (Phase 2)
- Expiry alerts UI (Phase 2; data field exists)
- Prices, nutrition
- Multi-household, user management, roles
- Native mobile apps (the PWA is the mobile app)

## Phases

- **Phase 1 (MVP):** everything above.
- **Phase 2:** barcode scanning in the PWA (WASM decoder — the browser
  BarcodeDetector API is not available on iOS Safari) + Open Food Facts lookup,
  cached into Product. Expiry date entry and "expiring soon" view.
- **Phase 3 (maybe):** log a whole shop from a receipt photo.

## Non-functional

- Runs as **two containers**: `onhand-api` (Spring Boot, API only) and
  `onhand-web` (nginx serving the built PWA). A single Ingress routes `/api` and
  `/` so the app is same-origin. Each can be deployed independently.
- Low footprint: no JPA, no Spring Security in MVP (private network only).
- PostgreSQL, schema managed by Flyway.
- Works acceptably on a phone over home Wi-Fi; offline support is not required
  for MVP.

## Changelog

- **2026-11-08 (4)** — Added the Edit product screen (MVP screen 4). Backend
  impact: Flyway migration adding nullable `archived_at` to product; exclude
  archived products from all listing, search, autocomplete and shopping-list
  queries; `PATCH` product (name, kind, category, unit, step_size, min_quantity;
  name stays unique case-insensitive among non-archived products); move stock to
  another location (writes `MOVED`, undoable); change tracking mode with the
  conversion rules above (writes `ADJUSTED`/`STATUS_CHANGED`, undoable); unit
  change takes the new current quantity; archive and unarchive endpoints
  (unarchive used by undo). Tests for each, including that archived products
  disappear from Home and the shopping list.

- **2026-11-08 (3)** — More household units, following UN/ECE standards: metric
  measures (Rec 20) stay `g`/`kg`/`ml`/`l`; counted containers are named after
  Rec 21 package types — added `bottle`, `jar`, `roll`, `box`, `bag`, `carton`,
  `tube`, `tub`, `bar`, `sachet`, `bunch` (default step 1). Backend impact: new
  Flyway migration widening the unit check; Unit enum records each UN/ECE code.
  Guidance: dried goods you cook in portions (e.g. dried chickpeas) are best in
  `g` with a step of one portion; tinned and dried versions are separate products.

- **2026-11-08 (2)** — Partial use decided: **both** exact entry and per-product
  step size (see Quick update). Backend impact: new Flyway migration adding
  nullable `step_size` (decimal) to product; expose it in product DTOs and allow
  updating it; the stock "set quantity" path must accept an absolute quantity
  ("now I have") as well as a delta ("I used"), each recorded as a StockEvent
  (`ADJUSTED` / `USED`) and undoable like other changes; tests for both.

- **2026-11-08** — Scope widened from food to all used-up-and-re-bought household
  items. Added Product `kind` (FOOD/HOUSEHOLD) + Home filter; household example
  locations; "track the spare" pattern; typed quantity on Add product. Backend
  impact (slices 1–2 already built): new Flyway migration adding `kind` to
  product (NOT NULL, default FOOD), expose it in product DTOs/endpoints, add an
  optional `kind` filter to the stock/Home listing endpoint, tests for both.

## Build order (vertical slices)

1. Locations + Products API (Flyway, JdbcClient, integration tests)
2. Stock + StockEvent + quick-update + undo endpoints
3. PWA shell + Home screen + quick update
4. Add-product screen
5. Edit-product screen (backend from changelog (4) first)
6. Shopping list (API + screen)
7. Finalise both Dockerfiles + Helm chart for k3s