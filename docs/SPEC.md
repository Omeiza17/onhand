# Onhand — Product Spec

## Problem

I have a lot of food and ingredients at home and lose track of what I have.
The one question this app must answer, quickly, from my phone:

> **"What do I have at home right now, and where is it?"**

Shopping is the second use: knowing what's low or gone before/while I'm at the shop.

Recipes are **out of scope** — they live in Obsidian.

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
A kind of thing I keep (e.g. "Basmati rice", "Eggs", "Cumin").

| Field | Notes |
|---|---|
| name | required, unique (case-insensitive) |
| category | optional free text or simple list (Grains, Dairy, Spices…) |
| tracking_mode | `COUNTED` or `STATUS` (see below) |
| default_unit | for COUNTED only: `piece`, `g`, `kg`, `ml`, `l`, `pack`, `tin` |
| default_location | optional |
| min_quantity | optional, COUNTED only — below this → goes on shopping list |
| barcode | optional, nullable, unique when present (Phase 2) |

### Tracking modes
- **COUNTED** — amount matters: "3 tins", "1.5 kg". Quick update = +/−.
- **STATUS** — amount not worth logging (spices, oil, salt). Values: `HAVE`, `LOW`, `OUT`.
  Quick update = tap to cycle.

This hybrid is the main thing that keeps the app easier to maintain than tools
that count everything.

### Location
Where things live: Pantry, Fridge, Freezer, etc. User-defined, ordered.

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
   Everything grouped by location, with search. Each row shows quantity or status.
   This is the core of the app.
2. **Quick update**
   Tap a row: +/− for COUNTED, cycle HAVE→LOW→OUT for STATUS. No forms.
   An undo toast after every change.
3. **Add product**
   Name (with autocomplete against existing products), tracking mode, location,
   optional quantity. Under 10 seconds.
4. **Shopping list**
   Generated items + manual items, check off while shopping.

## Out of scope (MVP)

- Recipes, meal planning
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

## Build order (vertical slices)

1. Locations + Products API (Flyway, JdbcClient, integration tests)
2. Stock + StockEvent + quick-update + undo endpoints
3. PWA shell + Home screen + quick update
4. Add-product screen
5. Shopping list (API + screen)
6. Finalise both Dockerfiles + Helm chart for k3s
