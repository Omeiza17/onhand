# Helm chart (slice 6)

Placeholder. Build this in the last slice. One chart, two workloads:

- **api**: Deployment for `onhand-api` (one replica), liveness/readiness probes
  on the actuator health endpoints, ClusterIP Service on 8080.
- **web**: Deployment for `onhand-web` (nginx, one replica), ClusterIP Service
  on 8080, simple HTTP probe on `/`.
- **Ingress** (k3s ships Traefik by default): one host, two paths —
  `/api` → api Service, `/` → web Service. Same origin, so no CORS.
  Private network only.
- **PostgreSQL**: either an existing in-cluster instance (connection via Secret)
  or a small StatefulSet with a PersistentVolumeClaim. Decide before building.
- DB credentials from a Secret, never in values.yaml.
- Separate image tags per workload in values.yaml so each can be deployed alone.
- Resource requests/limits sized for a homelab.
