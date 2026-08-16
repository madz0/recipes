# Recipes

A small recipes service built with **Spring Boot 4.1**, **Java 25**, and **Spring Data JDBC**
on **PostgreSQL**, with **Liquibase** managing the schema.

## Domain

The catalog is modelled as Spring Data JDBC aggregates (Java records) under
`com.abnamro.recipe.model`: `Dish`, `Ingredient`, `Recipe`, and `RecipeIngredient`.
`DietaryProfile` is stored as a Postgres `jsonb` column via the converters in
`com.abnamro.recipe.config`.

## Running locally

A PostgreSQL instance is provided via Docker Compose (`compose.yaml`) and started
automatically by Spring Boot's Docker Compose support:

```sh
mvn spring-boot:run
```

## Ingredient bootstrap

The app can seed the shared ingredient catalog from a bundled resource,
`src/main/resources/ingredients.json` (~200 ingredients across all `IngredientType`
values). It is **opt-in** and controlled by a single property:

```properties
# default: false — no seeding happens
recipe.bootstrap.enabled=true
```

When enabled, `IngredientBootstrapRunner` runs on startup and **upserts** the seed
data, keyed on the unique `ingredient.name`, so repeated startups are idempotent.

It is built to scale to very large seed files:

- the JSON is **streamed** element-by-element (never fully held in memory), and
- rows are flushed to the DB in **batches** — one existence query plus batched
  `INSERT`/`UPDATE` per batch, so round-trips grow with `rows / BATCH_SIZE` rather
  than per row.

Enable it for a single run without editing config:

```sh
mvn spring-boot:run -Dspring-boot.run.arguments=--recipe.bootstrap.enabled=true
```

## Tests

There is a single integration test (`IngredientBootstrapIT`). Being a full
`@SpringBootTest`, it is named `*IT` and runs under the **Failsafe** plugin in the
`integration-test`/`verify` phases — so `mvn verify` (and `mvn install`) run it,
while `mvn test` is reserved for unit tests (there are none yet). The **same test**
runs against either database — the choice is made by a Maven profile, not by the
test. The full Liquibase changelog is applied in both cases.

- **Default — in-memory H2** (PostgreSQL mode), fast and **no Docker required**:

  ```sh
  mvn verify
  ```

- **Real PostgreSQL** via a Testcontainers JDBC URL, enabled with the `postgres`
  profile (requires a running Docker engine):

  ```sh
  mvn verify -Ppostgres
  ```

### Testcontainers on Colima

This project uses **Colima** as the Docker engine. Export the socket env vars so
Testcontainers can find it before running against PostgreSQL:

```sh
colima start
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"

mvn verify -Ppostgres
```

If you use a non-default Colima profile, confirm the socket path with
`colima status` or `docker context inspect` and adjust `DOCKER_HOST` accordingly.

## TODO

- **Coordinate ingredient bootstrap across multiple pods.** Today, if several
  replicas start at the same time, each one independently re-ingests the full
  `ingredients.json`, competing over the same rows and racing on the
  `uq_ingredient_name` constraint — wasted work and noisy constraint-violation
  errors. Make ingestion cooperative / safe under concurrency, e.g.:
  - take a Postgres **advisory lock** (`pg_advisory_lock`) around the bootstrap so
    only one pod ingests at a time;
  - elect a single ingesting pod, or record a one-shot **"bootstrap completed"
    marker** row that other pods check and skip; and/or
  - switch the write to `INSERT ... ON CONFLICT (name) DO NOTHING/UPDATE` so
    concurrent inserts are harmless and duplicate work is cheaply skipped.
