# Recipes

A small recipes service built with **Spring Boot 4.1**, **Java 25**, and **Spring Data JDBC**
on **PostgreSQL**, with **Liquibase** managing the schema.

## Domain

The catalog is modelled as Spring Data JDBC aggregates (Java records) under
`com.abnamro.recipe.model`: `Ingredient`, `Recipe`, and `RecipeIngredient`.
`DietaryProfile` is stored as a Postgres `jsonb` column via the converters in
`com.abnamro.recipe.config`.

## Architecture

A single Maven module under package `com.abnamro.recipe`, layered
**`web → service → repository → model`** (`web/`, `service/` + `service/dto/`,
`repository/`, `model/`, `config/`, `bootstrap/`; entrypoint `Main.java`). The key
decisions, each with its rationale:

- **API-first (contract-first) with OpenAPI.** The specs in
  `src/main/resources/openapi/` (`recipes-api.yaml`, `ingredients-api.yaml`) are the source
  of truth; openapi-generator emits `interfaceOnly` API interfaces plus `api.model.*` DTOs
  into `target/generated-sources/openapi`, and the controllers (`RecipeController`,
  `IngredientController`) are hand-written implementations of those interfaces. The contract
  can't silently drift from the code.
- **Opaque API identity.** The internal `Long id` is never exposed; a unique `public_id`
  UUID surrogate is the identity every API path uses, keeping the database key private.
- **MapStruct at the web boundary.** `RecipeMapper` / `IngredientMapper`
  (`componentModel = "spring"`) translate between the internal `RecipeDto`/domain records and
  the generated `api.model.*` DTOs, so mapping is compile-time and boilerplate-free.
- **Spring Data JDBC aggregates as Java records** (`model/`): `Recipe` is the aggregate root
  owning a `@MappedCollection` of `RecipeIngredient`; `Ingredient` is a separate root.
  `DietaryProfile` is a value object persisted as JSON.
- **Dialect-aware persistence.** `RecipeSearchRepository` and `config/JdbcConfig` each detect
  Postgres vs H2 once from `DataSource` metadata: Postgres uses `jsonb` containment,
  `tsvector`/GIN full-text search and a `PGobject` converter pair, while H2 falls back to
  `LIKE` and a VARCHAR-JSON converter pair. One codebase runs the same suite on both DBs
  (see [Tests](#tests)).
- **Split read path for search.** The multi-filter recipe listing can't be a Spring Data
  derived query, so `RecipeSearchRepository` resolves the matching recipe **ids + total
  count** with dynamic SQL (`NamedParameterJdbcTemplate`) and `RecipeService` then
  re-hydrates the full aggregates via `findAllById`, preserving order.
- **RFC 9457 problem+json errors.** `spring.mvc.problemdetails.enabled=true` plus
  `web/GlobalExceptionHandler` (`@RestControllerAdvice`) map domain exceptions to a
  consistent error body.

The deeper "why" behind the domain shape lives in the sections below —
[Scope & assumptions](#scope--assumptions) (single-language/no-i18n),
[No separate dish resource](#no-separate-dish-resource), and [Security](#security).

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

## Database & indexing

The schema (PostgreSQL 15.2, managed by **Liquibase**) is three tables — `ingredient`,
`recipe`, and the `recipe_ingredient` join table — each with a primary key assigned from a
per-table sequence. All indexes are declared in a single changelog,
`src/main/resources/db/changelog/changes/0001-create-tables.xml`. Everything the service
uses is **core PostgreSQL**: there is deliberately **no `CREATE EXTENSION`** (no `pg_trgm`,
no `unaccent`).

Recipe search combines optional, AND-ed filters over four dimensions —
**instructions** (`instructionsContains`), **dietary profile** (`dietProfiles`),
**ingredients** (include/exclude by name), and **servings**. Recipe `name` is used only for
ordering the page (`ORDER BY LOWER(r.name), r.id`), never as a filter. Two purpose-built GIN
indexes cover the two filters that would otherwise be expensive, alongside the unique btree
indexes that back point lookups:

| Index | Definition | Serves | Why this index |
|---|---|---|---|
| `idx_recipe_search_vector` | `GIN (search_vector)` over a **STORED generated** `to_tsvector('english', coalesce(instructions, ''))` column | `instructionsContains` → `search_vector @@ plainto_tsquery('english', :q)` | GIN is the index type built for full-text `@@` matching — word/stem-based (`roast` matches `roasted`) and far more scalable than a leading-wildcard `LIKE '%…%'`. The column is generated + STORED so Postgres maintains the vector; it is never mapped on the `Recipe` record. |
| `idx_recipe_dietary_profile` | `GIN (dietary_profile_attributes jsonb_path_ops)` | `dietProfiles` → `dietary_profile_attributes @> :flag::jsonb` | GIN indexes `jsonb` containment. The **`jsonb_path_ops`** operator class is the smaller, faster class specialised for the `@>` operator — the only jsonb operator the search uses. It turns a dietary filter into a bitmap index scan on `recipe`, avoiding a correlated join over `recipe_ingredient`/`ingredient`. |
| `uq_ingredient_public_id`, `uq_ingredient_name`, `uq_recipe_public_id` | unique **btree** (implicit from the unique constraints) | point lookups — `findByPublicId`, bootstrap upsert by `name`, ingredient resolution | btree is the right structure for equality and uniqueness; these constraints double as the lookup indexes for by-id fetches and ingredient-name resolution. |

The choices behind those definitions:

- **Why GIN, not GiST or btree, for both search columns.** GIN is optimised for the
  "many keys per row → does this row contain key X" shape that both `tsvector` full-text and
  `jsonb` containment need. It trades slower writes for fast lookups, which suits a
  read-mostly recipe catalog.
- **Why `jsonb_path_ops` specifically.** It builds a smaller index and answers `@>` faster
  than the default `jsonb_ops`. The trade-off — it can't serve key-existence (`?`) or other
  non-containment operators — is free here, because dietary search only ever asks `@>`.
- **Why no `pg_trgm` / trigram / GiST.** Substring or fuzzy matching is not a requirement:
  instruction search is intentionally word/stem-based full-text (hence `oven` does not match
  `ovenware`), so no trigram extension or GiST index is introduced.
- **Postgres-only, H2-skipped.** Both GIN changeSets are `dbms="postgresql"`, so the
  Docker-free H2 test run skips them and the dialect-aware `RecipeSearchRepository` falls
  back to `LIKE`; the real full-text and jsonb indexes are exercised under `-Ppostgres`
  (see [Tests](#tests)).

## Security

Authentication is **deliberately minimal** — enough to show the service is not left open,
not a production identity system. It is HTTP Basic with a single built-in user configured
via `spring.security.user.*` in `application.properties`; the wiring lives in
`com.abnamro.recipe.config.SecurityConfig` (a stateless filter chain — CSRF disabled, no
session — requiring every request to be authenticated).

### Authentication

| | |
|---|---|
| Scheme | HTTP Basic |
| User | `recipes` |
| Password | `recipes-demo` (local/demo default) |
| Roles | `USER`, `ADMIN` (the one user holds **all** roles) |

Supply the password per-environment via the `SPRING_SECURITY_USER_PASSWORD` environment
variable; the checked-in value is for local use only. A request without valid credentials
is rejected with **401 Unauthorized**.

```sh
# authenticated
curl -u recipes:recipes-demo http://localhost:8080/api/v1/ingredients

# no credentials → 401
curl -i http://localhost:8080/api/v1/ingredients
```

### Authorization

Authorization is enforced with method-level `@PreAuthorize` on the controllers
(`@EnableMethodSecurity`), mapping each action to the role appropriate for it — reads
require `USER`, writes require `ADMIN`:

| Method + path | Action | Required role |
|---|---|---|
| `GET /api/v1/recipes`, `GET /api/v1/recipes/{id}` | read | `USER` |
| `POST /api/v1/recipes` | create | `ADMIN` |
| `GET /api/v1/ingredients`, `GET /api/v1/ingredients/{id}` | read | `USER` |
| `POST /api/v1/ingredients` | create | `ADMIN` |
| `DELETE /api/v1/ingredients/{id}` | delete | `ADMIN` |

Since the single user holds both roles, it can exercise every endpoint; the annotations
document and enforce the intended privilege for each action.

### SQL injection safety

The service is **not vulnerable to SQL injection** because no user input is ever
concatenated into SQL — every value is passed as a bound parameter:

- **Derived queries.** `RecipeRepository` and `IngredientRepository` are Spring Data JDBC
  repositories; their query methods (`findByPublicId`, `findByType`, `findByNameIn`, …) are
  translated to parameterized statements by Spring Data. No SQL string is authored by hand.
- **Dynamic search.** `RecipeSearchRepository` is the only place that builds SQL manually,
  for the multi-filter recipe search. It uses `NamedParameterJdbcTemplate` with a
  `MapSqlParameterSource`, and **every user-supplied value is a bound named parameter**
  (`:servings`, `:includeNames`, `:excludeNames`, `:instructionsContains`, `:limit`,
  `:offset`, …). The `String.formatted(where)` templating only stitches in the repository's
  own **static predicate string literals**, never user input, and the one computed
  parameter *name* (`"diet_" + flag.name()`) is derived from the `DietaryFlag` **enum**, not
  from attacker-controlled text. Because values reach the driver only as bind parameters,
  they cannot break out of their placeholder and alter the query structure.

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

If using **Colima** as the Docker engine. Export the socket env vars so
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
