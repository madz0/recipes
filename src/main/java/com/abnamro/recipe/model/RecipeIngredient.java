package com.abnamro.recipe.model;

import java.math.BigDecimal;

import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Value object owned by the {@link Recipe} aggregate: the join row linking a
 * recipe to an {@link Ingredient} (a separate aggregate, referenced via
 * {@link AggregateReference}) together with the quantity and unit used.
 * <p>
 * It intentionally has no {@code @Id} — as an element of a {@code Set}
 * mapped collection it is identified by its parent's back-reference
 * ({@code recipe_id}).
 */
@Table("recipe_ingredient")
public record RecipeIngredient(
        AggregateReference<Ingredient, Long> ingredient,
        BigDecimal quantity,
        MeasurementUnit unit
) {
}
