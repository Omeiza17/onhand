# Onhand

Self-hosted app to see what food I have at home, where it is, and what to buy.

- Spec: [docs/SPEC.md](docs/SPEC.md)
- Slice prompts: [docs/FIRST-PROMPTS.md](docs/FIRST-PROMPTS.md)
- Backend: `backend/` (Spring Boot API → `onhand-api` image)
- Frontend: `frontend/` (PWA on nginx → `onhand-web` image)
- Deploy: `deploy/helm/` (k3s: two Deployments, one Ingress)

## Getting started

```bash
git init && git add . && git commit -m "Project skeleton"
```

Then open this folder in the Claude desktop app (Code tab → Local → Select folder)
and run the Slice 0 prompt from `docs/FIRST-PROMPTS.md`.
