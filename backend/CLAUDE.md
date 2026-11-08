# Backend — Spring Boot API

## Stack (verify current versions before generating the project)

- Java 25 (LTS)
- Spring Boot 4.x — use the latest stable release at the time of scaffolding
  (4.1 was current mid-2026; 4.2 was planned for Nov 2026). Check before choosing.
- Maven with the Maven Wrapper (`mvnw`)
- PostgreSQL + Flyway

## Dependencies — keep it minimal

Spring Boot 4 uses modular starters. Use only:

- `spring-boot-starter-webmvc`        (NOT the old `spring-boot-starter-web` name)
- `spring-boot-starter-jdbc`          — data access via `JdbcClient`
- `spring-boot-starter-flyway` + `flyway-database-postgresql` (check whether still needed)
- `spring-boot-starter-validation`
- `spring-boot-starter-actuator`      — health/readiness probes only; expose nothing else
- PostgreSQL JDBC driver
- Test: the matching `*-test` starters + Testcontainers (PostgreSQL)

Do NOT add: JPA/Hibernate, Spring Data JPA, Spring Security, Lombok, MapStruct,
WebFlux, or a "classic" starter. Ask first if you think one is needed.

## Code layout

```
com.onhand
├── OnhandApplication.java
├── common/        # ProblemDetail handling, shared types
├── location/      # controller, service, repository, dto
├── product/
├── stock/         # StockItem + StockEvent
└── shopping/
```

Package by feature, not by layer. Records for DTOs. Repositories are plain
classes using `JdbcClient` with explicit SQL.

## Rules

- Schema changes only via new Flyway migrations (`V<n>__description.sql`).
  Never edit an applied migration.
- Stock changes write the StockEvent and update StockItem **in one transaction**.
- Validate inputs at the controller boundary; return ProblemDetail on errors.
- Enable virtual threads (`spring.threads.virtual.enabled=true`).
- API only. Do not serve static files or the PWA; that is the `onhand-web`
  container's job. No CORS config is needed: in dev the Vite proxy and in k3s
  the Ingress keep everything same-origin.
- Container image: `backend/Dockerfile` (build context is `backend/`).

## Tests

- Integration tests against real Postgres via Testcontainers. Local Docker
  runtime is Colima — if Testcontainers can't find Docker, set `DOCKER_HOST`
  to Colima's socket rather than switching to H2.
- Each slice: happy path + main validation failures + one transaction/undo test
  where relevant.

## Local run

Postgres for local dev:
`docker run -d --name onhand-db -e POSTGRES_DB=onhand -e POSTGRES_USER=onhand -e POSTGRES_PASSWORD=onhand -p 5432:5432 postgres:17`
(check the current Postgres major version)
