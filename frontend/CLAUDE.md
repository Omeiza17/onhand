# Frontend — PWA

## Stack (verify current versions before scaffolding)

- Vite + React + TypeScript (strict)
- PWA via the current recommended Vite PWA plugin (check docs; manifest +
  service worker for installability; offline is NOT required for MVP)
- No heavy UI kit. Plain CSS (CSS variables) or a small utility approach.
  Ask before adding a component library.

## Design rules

- **Mobile-first.** Design for a phone held in one hand; desktop is secondary.
- Large tap targets (min 44px). Primary actions reachable by thumb.
- The Home screen ("What's at home") is the default route and must load fast.
- Quick update = one tap, with an undo toast. No confirmation dialogs for
  routine changes.
- Light and dark mode via `prefers-color-scheme`.
- **The agreed UI is in `docs/design/DESIGN.md` — read it before any UI work
    and match it.** The `.dc.html` files there are mockups for reference only.

## Code layout

```
src/
├── api/          # typed client for /api/v1 (fetch wrapper, no axios)
├── screens/      # Home, AddProduct, ShoppingList
├── components/
└── styles/
```

## Rules

- All API calls go through `src/api/`. Types mirror the backend DTOs.
- Dev server proxies `/api` → `http://localhost:8080` (configure in vite.config).
- Production build output goes to `dist/`, served by nginx from
  `frontend/Dockerfile` with `frontend/nginx.conf`. The app is served from `/`;
  the API is reached at `/api` on the same origin (routed by the k3s Ingress),
  so the API client uses relative URLs — never a hard-coded backend host.
- If the service worker or manifest filenames change, update the no-cache
  rules in `nginx.conf` to match.
- Keep dependencies minimal; justify each one in the summary.

## Dev helpers

- `API_TARGET=http://localhost:8081 npm run dev` — proxy `/api` to a backend on
  another port (default `http://localhost:8080`).
- `npm run seed` — loads the example household from the design mockups through
  the API (`API=…/api/v1` to target another backend). Re-runnable.
- `npm run generate-pwa-assets` — regenerates the app icons from `public/icon.svg`.
