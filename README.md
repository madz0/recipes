# Recipes

A small recipes service built with **Spring Boot 4.1**, **Java 25**, and **Spring Data JDBC**
on **PostgreSQL**, with **Liquibase** managing the schema.

## Domain

The catalog is modelled as Spring Data JDBC aggregates (Java records) under
`com.abnamro.recipe.model`: `Ingredient`, `Recipe`, and `RecipeIngredient`.
`DietaryProfile` is stored as a Postgres `jsonb` column via the converters in
`com.abnamro.recipe.config`.

## Scope & assumptions

For simplicity, this service is intentionally **single-language (English)** and has **no
internationalization (i18n) support**:

- Ingredient names are stored and returned as plain English strings, and
  `ingredient.name` doubles as the unique business key and the bootstrap upsert key.
- `IngredientType` is returned as its raw enum code (e.g. `MEAT`), not a localized label.
- There is no `MessageSource`, resource bundle, or `Accept-Language` handling.

Adding multi-language support later would require separating **identity** from **display
text** — e.g. a stable, language-neutral `code` as the key plus a per-locale translations
table for names, and a `MessageSource`-backed localized label for `IngredientType` —
because a unique/upsert key must be a single immutable value per ingredient, whereas a
localized name is many values that change over time. This is deliberately left out of the
current scope.

### No separate dish resource

The Recipe API (`src/main/resources/openapi/recipes-api.yaml`) intentionally has **no
separate dish resource**. Each recipe carries its own mandatory `name`, and its dietary
classification (`vegetarian` / `vegan` / `meat`, alongside the `gluten` / `wheat` / `nut`
allergen flags) is **derived from the selected ingredients** rather than stored elsewhere;
`vegan` is stricter than `vegetarian` — it also excludes `DAIRY` and `EGG` ingredients.
For the current requirements — creating recipes and filtering them, including by whether a
recipe is vegetarian — a dedicated `Dish` entity would add no information: the only
dish-level attribute needed is derivable from the ingredients, and a dish name would
merely duplicate the recipe name. If genuinely dish-level attributes are ever needed (e.g.
grouping several recipes under one "Lasagne" dish), a `Dish` entity with a one-to-many
relationship to `Recipe` could be introduced then.

## Recipes API

The Recipes API (`recipes-api.yaml`) supports creating a recipe, fetching one by its
opaque UUID, and listing recipes with pagination and AND-combined filters. It mirrors the
Ingredients slice: a generated `RecipesApi` interface implemented by a hand-written
`RecipeController` → `RecipeService` → Spring Data JDBC.

- **Derived dietary profile.** On create, the server resolves each selected ingredient
  against the catalog (an unknown ingredient id is a `400`) and derives the six-flag
  `DietaryProfile` from the ingredient types. A single `DietaryFlag` enum is the source of
  truth: `vegetarian` = no `MEAT`; `vegan` = no `MEAT`/`DAIRY`/`EGG`; `meat` = any `MEAT`;
  `wheat` = any `WHEAT`/`GLUTEN_FREE_WHEAT`; `gluten` = any `WHEAT`; `nut` = any `NUT`. The
  client never supplies it.
- **Filtering.** The list endpoint combines `dietProfiles`, `servings`, `includeIngredients`
  (contains **all**), `excludeIngredients` (contains **none**), and `instructionsContains`.
  `dietProfiles` is a comma-separated list of dietary flags, each optionally negated with a
  leading `-` (e.g. `?dietProfiles=vegan,-gluten` = vegan **and** gluten-free); a flag given
  with both signs (`gluten,-gluten`) imposes no restriction. Because each flag is defined by
  its ingredient types, `dietProfiles=vegetarian` also returns vegan recipes. The dynamic SQL
  lives in `RecipeSearchRepository` (built with `NamedParameterJdbcTemplate`, since the
  combination can't be a derived query method).

### Text search on instructions

`instructionsContains` is **PostgreSQL full-text search**. The `recipe` table has a STORED
generated `search_vector tsvector` column (`to_tsvector('english', instructions)`) backed
by a **GIN index**, and the query matches with `search_vector @@ plainto_tsquery(...)`.
This gives fast, word/stem-based matching (`roast` matches `roasted`) that scales — at the
cost of substring semantics (`oven` does **not** match `ovenware`; see the contract).

Because `tsvector`/GIN are Postgres-only, the generated column and index live in a
**Postgres-only Liquibase changeSet** (`dbms="postgresql"`), so the Docker-free H2 test run
skips them entirely. On H2, `RecipeSearchRepository` **falls back** to a portable
case-insensitive `LIKE`, detected once from the datasource metadata — so the same test
suite passes on both databases, and real full-text search is exercised under `-Ppostgres`.

## Running locally

A PostgreSQL instance is provided via Docker Compose (`compose.yaml`) and started
automatically by Spring Boot's Docker Compose support:

```sh
mvn spring-boot:run
```

## Ingredient bootstrap

The app can seed the shared ingredient catalog from a bundled resource,
`src/main/resources/ingredients.json` (~210 ingredients across all `IngredientType`
values, including `DAIRY` and `EGG`). It is **opt-in** and controlled by a single property:

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
