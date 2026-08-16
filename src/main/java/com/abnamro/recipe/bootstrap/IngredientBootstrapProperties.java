package com.abnamro.recipe.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the ingredient bootstrap step.
 *
 * @param enabled when {@code true}, {@link IngredientBootstrapRunner} upserts the
 *                bundled {@code ingredients.json} into the database on startup.
 *                Defaults to {@code false} so bootstrapping is always opt-in.
 */
@ConfigurationProperties(prefix = "recipe.bootstrap")
public record IngredientBootstrapProperties(
        @DefaultValue("false") boolean enabled
) {
}
