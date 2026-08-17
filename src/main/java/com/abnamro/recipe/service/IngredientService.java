package com.abnamro.recipe.service;

import com.abnamro.recipe.service.exception.DuplicateIngredientNameException;
import com.abnamro.recipe.service.exception.IngredientInUseException;
import com.abnamro.recipe.service.exception.IngredientNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abnamro.recipe.model.Ingredient;
import com.abnamro.recipe.model.IngredientType;
import com.abnamro.recipe.repository.IngredientRepository;
import com.abnamro.recipe.repository.RecipeRepository;

/**
 * Application service for the shared ingredient catalog. Owns the business rules
 * behind the Ingredients API: unique names, UUID-based identity, pagination, and
 * refusing to delete an ingredient that is still used by a recipe.
 */
@Service
public class IngredientService {

    private final IngredientRepository ingredients;
    private final RecipeRepository recipes;

    public IngredientService(IngredientRepository ingredients, RecipeRepository recipes) {
        this.ingredients = ingredients;
        this.recipes = recipes;
    }

    /**
     * Creates a new ingredient, assigning it a fresh public UUID. Names are unique:
     * a duplicate name (detected up front, or on the unique constraint under a race)
     * results in a {@link DuplicateIngredientNameException}.
     */
    @Transactional
    public Ingredient create(String name, IngredientType type) {
        if (ingredients.existsByName(name)) {
            throw new DuplicateIngredientNameException(name);
        }
        try {
            return ingredients.save(new Ingredient(null, UUID.randomUUID(), name, type));
        } catch (DataIntegrityViolationException e) {
            // Lost a race against a concurrent create with the same name.
            throw new DuplicateIngredientNameException(name);
        }
    }

    /**
     * Returns a page of ingredients ordered by name, optionally restricted to a
     * single {@code type}.
     */
    @Transactional(readOnly = true)
    public Page<Ingredient> list(int page, int size, IngredientType type) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name"));
        return type == null
                ? ingredients.findAll(pageable)
                : ingredients.findByType(type, pageable);
    }

    /** Returns the ingredient with the given public UUID, or throws if none exists. */
    @Transactional(readOnly = true)
    public Ingredient get(UUID publicId) {
        return ingredients.findByPublicId(publicId)
                .orElseThrow(() -> new IngredientNotFoundException(publicId));
    }

    /** Returns catalog ingredients matching the given public ids (unknown ids are omitted). */
    @Transactional(readOnly = true)
    public List<Ingredient> findByPublicIds(Collection<UUID> publicIds) {
        return ingredients.findByPublicIdIn(publicIds);
    }

    /** Batch-loads catalog ingredients by internal id. */
    @Transactional(readOnly = true)
    public List<Ingredient> findByIds(Collection<Long> ids) {
        List<Ingredient> result = new ArrayList<>();
        ingredients.findAllById(ids).forEach(result::add);
        return result;
    }

    /**
     * Deletes the ingredient with the given public UUID, or throws if none exists.
     * An ingredient still used by a recipe cannot be deleted
     * ({@link IngredientInUseException}).
     */
    @Transactional
    public void delete(UUID publicId) {
        Ingredient existing = ingredients.findByPublicId(publicId)
                .orElseThrow(() -> new IngredientNotFoundException(publicId));
        if (recipes.countUsagesOfIngredient(existing.id()) > 0) {
            throw new IngredientInUseException(existing.publicId(), existing.name());
        }
        try {
            ingredients.delete(existing);
        } catch (DataIntegrityViolationException e) {
            // Lost a race against a concurrent recipe that just selected this ingredient.
            throw new IngredientInUseException(existing.publicId(), existing.name());
        }
    }
}
