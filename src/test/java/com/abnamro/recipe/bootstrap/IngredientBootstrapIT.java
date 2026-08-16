package com.abnamro.recipe.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;

import com.abnamro.recipe.model.Ingredient;
import com.abnamro.recipe.model.IngredientType;
import com.abnamro.recipe.repository.IngredientRepository;

/**
 * Bootstraps the ingredient catalog against the real Liquibase schema.
 *
 * <p>The database is chosen at runtime, not by the test: {@code mvn verify} runs it
 * against an in-memory H2 (no Docker), while {@code mvn verify -Ppostgres} repoints
 * the datasource at a real PostgreSQL via a Testcontainers JDBC URL. The same
 * changelog and the same assertions apply to both.
 *
 * <p>Named {@code *IT} so it runs under the Failsafe plugin in the
 * {@code integration-test}/{@code verify} phases rather than under Surefire in
 * {@code test}.
 */
@SpringBootTest
@ActiveProfiles("test")
class IngredientBootstrapIT {

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private IngredientBootstrapRunner runner;

    @Test
    void seedsCatalogOnStartupAndIsIdempotent() {
        long seeded = ingredientRepository.count();
        assertThat(seeded).isGreaterThanOrEqualTo(199);

        // Re-running must not insert or update anything — every row is unchanged.
        IngredientBootstrapRunner.Result rerun = runner.bootstrap();
        assertThat(rerun.inserted()).isZero();
        assertThat(rerun.updated()).isZero();
        assertThat(rerun.skipped()).isEqualTo((int) seeded);
        assertThat(ingredientRepository.count()).isEqualTo(seeded);
    }

    @Test
    void nameUniqueConstraintIsEnforced() {
        Ingredient existing = ingredientRepository.findByNameIn(List.of("Beef sirloin")).get(0);

        assertThatThrownBy(() ->
                ingredientRepository.save(new Ingredient(null, UUID.randomUUID(), existing.name(), IngredientType.MEAT)))
                .isInstanceOf(DataAccessException.class);
    }
}
