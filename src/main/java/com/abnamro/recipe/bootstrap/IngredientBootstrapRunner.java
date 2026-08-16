package com.abnamro.recipe.bootstrap;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

import com.abnamro.recipe.model.Ingredient;
import com.abnamro.recipe.repository.IngredientRepository;

import tools.jackson.databind.MappingIterator;
import tools.jackson.databind.ObjectMapper;

/**
 * On startup, when {@code recipe.bootstrap.enabled=true}, upserts the ingredient
 * catalog bundled in {@code ingredients.json} into the database.
 *
 * <p>Designed to scale to very large seed files: the JSON is <em>streamed</em>
 * element-by-element (never fully materialised in memory) and rows are flushed to
 * the database in batches. Each batch costs one existence query plus batched
 * INSERT/UPDATE statements, so the total number of round-trips grows with
 * {@code rows / BATCH_SIZE} rather than with the number of rows. Upsert is keyed
 * on the unique {@code ingredient.name}, so repeated startups are idempotent.
 */
@Component
public class IngredientBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngredientBootstrapRunner.class);

    private static final String SEED_RESOURCE = "ingredients.json";
    private static final int BATCH_SIZE = 1_000;

    private static final String INSERT_SQL =
            "INSERT INTO ingredient (public_id, name, type) VALUES (:publicId, :name, :type)";
    private static final String UPDATE_SQL =
            "UPDATE ingredient SET type = :type WHERE id = :id";

    private final IngredientBootstrapProperties properties;
    private final IngredientRepository ingredientRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public IngredientBootstrapRunner(IngredientBootstrapProperties properties,
                                     IngredientRepository ingredientRepository,
                                     NamedParameterJdbcTemplate jdbc,
                                     ObjectMapper objectMapper) {
        this.properties = properties;
        this.ingredientRepository = ingredientRepository;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            log.info("Ingredient bootstrap disabled (recipe.bootstrap.enabled=false); skipping.");
            return;
        }
        bootstrap();
    }

    /**
     * Streams the seed resource and flushes it to the database in batches.
     * Returns the totals so callers/tests can assert what happened.
     */
    public Result bootstrap() {
        log.info("Bootstrapping ingredients from classpath:{}", SEED_RESOURCE);
        Result result = new Result();
        List<IngredientSeed> buffer = new ArrayList<>(BATCH_SIZE);

        ClassPathResource resource = new ClassPathResource(SEED_RESOURCE);
        try (InputStream in = resource.getInputStream();
             MappingIterator<IngredientSeed> it =
                     objectMapper.readerFor(IngredientSeed.class).readValues(in)) {
            while (it.hasNext()) {
                buffer.add(it.next());
                if (buffer.size() == BATCH_SIZE) {
                    flush(buffer, result);
                    buffer.clear();
                }
            }
            if (!buffer.isEmpty()) {
                flush(buffer, result);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to bootstrap ingredients from " + SEED_RESOURCE, e);
        }

        log.info("Ingredient bootstrap complete: {} inserted, {} updated, {} unchanged.",
                result.inserted, result.updated, result.skipped);
        return result;
    }

    /**
     * Upserts one batch: a single existence query to find which names already
     * exist, then one batched INSERT for new rows and one batched UPDATE for rows
     * whose type changed. Unchanged rows are left untouched.
     */
    private void flush(List<IngredientSeed> batch, Result result) {
        Map<String, Ingredient> existingByName = new HashMap<>();
        for (Ingredient existing : ingredientRepository.findByNameIn(
                batch.stream().map(IngredientSeed::name).toList())) {
            existingByName.put(existing.name(), existing);
        }

        List<SqlParameterSource> inserts = new ArrayList<>();
        List<SqlParameterSource> updates = new ArrayList<>();
        for (IngredientSeed seed : batch) {
            Ingredient existing = existingByName.get(seed.name());
            if (existing == null) {
                inserts.add(new MapSqlParameterSource()
                        .addValue("publicId", UUID.randomUUID())
                        .addValue("name", seed.name())
                        .addValue("type", seed.type().name()));
            } else if (existing.type() != seed.type()) {
                updates.add(new MapSqlParameterSource()
                        .addValue("id", existing.id())
                        .addValue("type", seed.type().name()));
            } else {
                result.skipped++;
            }
        }

        if (!inserts.isEmpty()) {
            jdbc.batchUpdate(INSERT_SQL, inserts.toArray(SqlParameterSource[]::new));
            result.inserted += inserts.size();
        }
        if (!updates.isEmpty()) {
            jdbc.batchUpdate(UPDATE_SQL, updates.toArray(SqlParameterSource[]::new));
            result.updated += updates.size();
        }
    }

    /** Cumulative outcome of a bootstrap run. */
    public static final class Result {
        private int inserted;
        private int updated;
        private int skipped;

        public int inserted() {
            return inserted;
        }

        public int updated() {
            return updated;
        }

        public int skipped() {
            return skipped;
        }

        public int total() {
            return inserted + updated + skipped;
        }
    }
}
