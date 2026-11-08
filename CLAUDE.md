# Onhand — repo guide for Claude

Home food-inventory app. Read `docs/SPEC.md` before any feature work — it is the
source of truth for scope. If a request conflicts with the spec, say so before
building.

## Repo layout

- `backend/`  — Spring Boot API (Maven). See `backend/CLAUDE.md`.
- `frontend/` — PWA (Vite). See `frontend/CLAUDE.md`.
- `deploy/`   — Helm chart for k3s.
- `docs/`     — spec and decision notes.

Two containers, deployed independently:
- `onhand-api` — built from `backend/Dockerfile` (Spring Boot, API only).
- `onhand-web` — built from `frontend/Dockerfile` (nginx serving the PWA).

The k3s Ingress routes `/api` → api and `/` → web, so the browser sees one
origin (no CORS). The backend knows nothing about the frontend: it does not
serve static files. Each toolchain stays independent — Maven builds
`backend/`, npm builds `frontend/`. Do NOT add frontend-maven-plugin or make
Maven drive npm.

## How to work in this repo

- **Check current documentation before writing framework code or config.**
  Versions move (Spring Boot 4.x, Vite, etc.). Do not rely on memory for
  starter names, property names or APIs — verify, and prefer the native API
  over a workaround.
- Work in **vertical slices** (see "Build order" in `docs/SPEC.md`). One slice
  per session. Plan first, then implement.
- Keep changes small and reviewable. Do not refactor unrelated code.
- Every backend slice ships with tests that pass. Run them before declaring
  a slice done.
- Do not add dependencies without saying why in the summary.
- Stop and ask before: changing the data model in `docs/SPEC.md`, adding a new
  service/container, adding auth, or deleting files.

## Commands

Backend (from `backend/`):
- Build + test: `./mvnw verify`
- Run: `./mvnw spring-boot:run` (needs Postgres; see backend/CLAUDE.md)

Frontend (from `frontend/`):
- Install: `npm install`
- Dev server: `npm run dev` (proxies `/api` to `http://localhost:8080`)
- Build: `npm run build`

Containers:
- API: `docker build -t onhand-api:dev backend/`
- Web: `docker build -t onhand-web:dev frontend/`

## Conventions

- API base path: `/api/v1`. JSON, camelCase fields.
- Errors: RFC 9457 Problem Details (Spring's built-in ProblemDetail support).
- IDs: UUID.
- Timestamps: UTC in storage and API; the frontend formats for display.
- Units are metric.
