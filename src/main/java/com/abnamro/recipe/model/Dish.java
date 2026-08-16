package com.abnamro.recipe.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Aggregate root representing a dish (e.g. "Lasagne"), classified by its
 * {@link DietType}. A dish can have many {@link Recipe recipes}.
 */
@Table("dish")
public record Dish(
        @Id Long id,
        String name,
        DietType type
) {
}
