# Prompts for each slice

Use these in the desktop app's Code tab (Local session, project folder = repo root).
Start each slice in **Plan** mode, review the plan, then switch to Manual or
Accept edits to implement. Commit after each slice.

## Slice 0 — scaffold
> Read CLAUDE.md, backend/CLAUDE.md, frontend/CLAUDE.md and docs/SPEC.md.
> Scaffold the backend (Spring Boot, latest stable 4.x — check first) and the
> frontend (Vite + React + TS) exactly as those files describe. No features yet:
> just an app that starts, a health endpoint, a Flyway V1 that creates nothing
> or a placeholder, a frontend that shows "Onhand", and the Vite /api proxy.
> Confirm `./mvnw verify` and `npm run build` both pass.

## Slice 1 — locations + products
> Implement slice 1 from docs/SPEC.md: Location and Product, Flyway migrations,
> JdbcClient repositories, REST endpoints under /api/v1, validation with
> ProblemDetail errors, and Testcontainers integration tests.

## Slice 2 — stock + events + undo
> Implement slice 2: StockItem, StockEvent, the quick-update endpoints for both
> tracking modes, and "undo last action". Stock change and event must be written
> in one transaction — include a test that proves it.

## Slice 3 — Home screen
> Implement slice 3: PWA shell (manifest, installable) and the Home screen
> grouped by location with search, plus quick update with an undo toast.
> Mobile-first per frontend/CLAUDE.md.

## Slice 4 — Add product
## Slice 5 — Shopping list
## Slice 6 — containers + Helm
> Implement slice 6: verify and finish backend/Dockerfile and frontend/Dockerfile
> (check current base image tags), then build the Helm chart described in
> deploy/helm/onhand/README.md — two Deployments, two Services, one Ingress
> routing /api and /. Ask me how Postgres should be provided before writing it.
