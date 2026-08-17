package com.abnamro.recipe.service;

import com.abnamro.recipe.service.dto.IngredientSelectionDto;
import com.abnamro.recipe.service.dto.RecipeDto;
import com.abnamro.recipe.service.exception.RecipeIngredientNotFoundException;
import com.abnamro.recipe.service.exception.RecipeNotFoundException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abnamro.recipe.model.DietaryProfile;
import com.abnamro.recipe.model.Ingredient;
import com.abnamro.recipe.model.IngredientType;
import com.abnamro.recipe.model.Recipe;
import com.abnamro.recipe.model.RecipeIngredient;
import com.abnamro.recipe.repository.RecipeRepository;
import com.abnamro.recipe.repository.RecipeSearchCriteria;
import com.abnamro.recipe.repository.RecipeSearchRepository;
import com.abnamro.recipe.service.mapper.RecipeDtoMapper;

/**
 * Application service for recipes. Owns the business rules behind the Recipes API:
 * resolving selected ingredients against the shared catalog, deriving the
 * {@link DietaryProfile} from those ingredients, UUID-based identity, and filtered
 * pagination.
 */
@Service
public class RecipeService {

    private final RecipeRepository recipes;
    private final IngredientService ingredientService;
    private final RecipeSearchRepository search;
    private final RecipeDtoMapper recipeDtoMapper;

    public RecipeService(RecipeRepository recipes,
                         IngredientService ingredientService,
                         RecipeSearchRepository search,
                         RecipeDtoMapper recipeDtoMapper) {
        this.recipes = recipes;
        this.ingredientService = ingredientService;
        this.search = search;
        this.recipeDtoMapper = recipeDtoMapper;
    }

    /**
     * Creates a recipe. Every selected ingredient must reference an existing
     * catalog ingredient by its public id; an unknown id yields a
     * {@link RecipeIngredientNotFoundException} (400). The dietary profile is
     * derived from the resolved ingredients and never supplied by the client.
     */
    @Transactional
    public RecipeDto create(String name, int servings, String instructions,
                             List<IngredientSelectionDto> selections) {
        ResolvedSelections resolved = resolveSelections(selections);
        DietaryProfile profile = deriveProfile(resolved.byId().values());
        Recipe saved = recipes.save(new Recipe(
                null, UUID.randomUUID(), name, servings, instructions, profile,
                resolved.recipeIngredients()));
        return recipeDtoMapper.toDto(saved, resolved.byId());
    }

    /**
     * Updates the recipe with the given public UUID, replacing its {@code name},
     * {@code servings}, {@code instructions}, and full ingredient set with the
     * supplied values (full-replace semantics). Like {@link #create}, every
     * selected ingredient must reference an existing catalog ingredient (an unknown
     * id yields a {@link RecipeIngredientNotFoundException}, 400); an unknown recipe
     * id yields a {@link RecipeNotFoundException} (404). The dietary profile is
     * re-derived from the new selection and re-stored in this same transaction, so
     * {@code dietary_profile_attributes} never drifts from the recipe's ingredients.
     */
    @Transactional
    public RecipeDto update(UUID publicId, String name, int servings, String instructions,
                            List<IngredientSelectionDto> selections) {
        Recipe existing = recipes.findByPublicId(publicId)
                .orElseThrow(() -> new RecipeNotFoundException(publicId));
        ResolvedSelections resolved = resolveSelections(selections);
        DietaryProfile profile = deriveProfile(resolved.byId().values());
        Recipe saved = recipes.save(new Recipe(
                existing.id(), existing.publicId(), name, servings, instructions, profile,
                resolved.recipeIngredients()));
        return recipeDtoMapper.toDto(saved, resolved.byId());
    }

    /** Deletes the recipe with the given public UUID, or throws if none exists. */
    @Transactional
    public void delete(UUID publicId) {
        Recipe existing = recipes.findByPublicId(publicId)
                .orElseThrow(() -> new RecipeNotFoundException(publicId));
        recipes.delete(existing);
    }

    /**
     * Resolves each {@link IngredientSelectionDto} against the catalog by its public id,
     * building the (internal-id → {@link Ingredient}) lookup and the
     * {@link RecipeIngredient} join rows in one pass. An unknown ingredient id yields
     * a {@link RecipeIngredientNotFoundException} (400). Shared by {@link #create} and
     * {@link #update} so both apply identical resolution and error behavior.
     */
    private ResolvedSelections resolveSelections(List<IngredientSelectionDto> selections) {
        List<UUID> requestedIds = selections.stream()
                .map(IngredientSelectionDto::ingredientId)
                .toList();
        Map<UUID, Ingredient> ingredientsByPublicId = ingredientService.findByPublicIds(requestedIds).stream()
                .collect(Collectors.toMap(Ingredient::publicId, Function.identity()));

        Map<Long, Ingredient> resolved = new HashMap<>();
        Set<RecipeIngredient> recipeIngredients = new LinkedHashSet<>();
        for (IngredientSelectionDto selection : selections) {
            Ingredient ingredient = ingredientsByPublicId.get(selection.ingredientId());
            if (ingredient == null) {
                throw new RecipeIngredientNotFoundException(selection.ingredientId());
            }
            resolved.put(ingredient.id(), ingredient);
            recipeIngredients.add(new RecipeIngredient(
                    AggregateReference.to(ingredient.id()), selection.quantity(), selection.unit()));
        }
        return new ResolvedSelections(resolved, recipeIngredients);
    }

    /** Returns the recipe with the given public UUID, or throws if none exists. */
    @Transactional(readOnly = true)
    public RecipeDto get(UUID publicId) {
        Recipe recipe = recipes.findByPublicId(publicId)
                .orElseThrow(() -> new RecipeNotFoundException(publicId));
        Map<Long, Ingredient> byId = loadIngredients(List.of(recipe));
        return recipeDtoMapper.toDto(recipe, byId);
    }

    /** Returns a filtered, paginated page of recipes ordered by name. */
    @Transactional(readOnly = true)
    public Page<RecipeDto> list(int page, int size, RecipeSearchCriteria criteria) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name"));
        RecipeSearchRepository.Result result = search.search(criteria, pageable);
        if (result.ids().isEmpty()) {
            return new PageImpl<>(List.of(), pageable, result.totalElements());
        }

        Map<Long, Recipe> byId = new HashMap<>();
        recipes.findAllById(result.ids()).forEach(r -> byId.put(r.id(), r));
        // Preserve the name ordering the search query produced.
        List<Recipe> ordered = result.ids().stream().map(byId::get).filter(Objects::nonNull).toList();

        Map<Long, Ingredient> ingredientsById = loadIngredients(ordered);
        List<RecipeDto> content = ordered.stream()
                .map(r -> recipeDtoMapper.toDto(r, ingredientsById))
                .toList();
        return new PageImpl<>(content, pageable, result.totalElements());
    }

    /** Batch-loads every catalog ingredient referenced by the given recipes. */
    private Map<Long, Ingredient> loadIngredients(List<Recipe> forRecipes) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Recipe recipe : forRecipes) {
            for (RecipeIngredient ri : recipe.ingredients()) {
                ids.add(ri.ingredient().getId());
            }
        }
        Map<Long, Ingredient> byId = new HashMap<>();
        if (!ids.isEmpty()) {
            ingredientService.findByIds(ids).forEach(i -> byId.put(i.id(), i));
        }
        return byId;
    }

    /**
     * Derives the dietary profile from the recipe's ingredient types. Each
     * {@link com.abnamro.recipe.model.DietaryFlag} defines itself by an
     * ingredient-type rule, so this collapses to collecting the set of types
     * present and letting {@link DietaryProfile#from(Set)} evaluate every flag —
     * the same rules the search filter uses.
     *
     * <p><strong>Source of truth for search.</strong> The result is stored on
     * {@code recipe.dietary_profile_attributes}, and {@code RecipeSearchRepository}
     * filters dietary criteria directly against that column (no live ingredient join).
     * The column is authoritative only as long as it is kept in sync: it is written at
     * {@link #create} and re-derived and re-stored at {@link #update}, both within the
     * same transaction as the ingredient change, so it cannot drift from the recipe's
     * ingredients. The one path that would still bypass this is changing an existing
     * catalog ingredient's {@link IngredientType}; there is deliberately no such
     * ingredient-retype endpoint, and if one is ever added it MUST backfill every
     * affected recipe's profile, or dietary search will silently return stale results.
     */
    private static DietaryProfile deriveProfile(Iterable<Ingredient> ingredients) {
        EnumSet<IngredientType> presentTypes = EnumSet.noneOf(IngredientType.class);
        for (Ingredient ingredient : ingredients) {
            presentTypes.add(ingredient.type());
        }
        return DietaryProfile.from(presentTypes);
    }

    /** The catalog ingredients a selection resolves to, plus the join rows to persist. */
    private record ResolvedSelections(Map<Long, Ingredient> byId, Set<RecipeIngredient> recipeIngredients) {
    }
}
