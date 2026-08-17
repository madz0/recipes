package com.abnamro.recipe.web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;

import com.abnamro.recipe.api.model.DietaryProfile;
import com.abnamro.recipe.api.model.MeasurementUnit;
import com.abnamro.recipe.api.model.Recipe;
import com.abnamro.recipe.api.model.RecipeIngredient;
import com.abnamro.recipe.api.model.RecipeIngredientSelection;
import com.abnamro.recipe.api.model.RecipePage;
import com.abnamro.recipe.model.DietaryFlag;
import com.abnamro.recipe.service.InvalidDietProfileException;
import com.abnamro.recipe.service.RecipeService;
import com.abnamro.recipe.service.RecipeView;

/**
 * Maps between the persistence-layer recipe aggregate (plus the resolved
 * ingredient catalog) and the OpenAPI-generated DTOs. Domain types are referenced
 * by fully-qualified name because their simple names clash with the generated DTOs.
 */
final class RecipeMapper {

    private RecipeMapper() {
    }

    /** API create-request selections → domain selections (enum converted, null-safe). */
    static List<RecipeService.IngredientSelection> toDomainSelections(List<RecipeIngredientSelection> selections) {
        if (selections == null) {
            return List.of();
        }
        return selections.stream()
                .map(s -> new RecipeService.IngredientSelection(
                        s.getIngredientId(), s.getQuantity(), toDomainUnit(s.getUnit())))
                .toList();
    }

    /**
     * Parses the {@code dietProfiles} query tokens into a required-value-per-flag map.
     * Each token is a {@link DietaryFlag#token()} optionally prefixed with {@code -}
     * (negation): {@code vegan} → require {@code vegan=true}, {@code -gluten} →
     * require {@code gluten=false}. A flag listed with both signs cancels out (dropped
     * — no restriction). An unrecognized token yields {@link InvalidDietProfileException}
     * (400). Returns an empty map when there are no tokens.
     */
    static Map<DietaryFlag, Boolean> toDietaryFilters(List<String> dietProfiles) {
        Map<DietaryFlag, Boolean> effective = new LinkedHashMap<>();
        if (dietProfiles == null) {
            return effective;
        }
        Set<DietaryFlag> cancelled = EnumSet.noneOf(DietaryFlag.class);
        for (String raw : dietProfiles) {
            if (raw == null) {
                continue;
            }
            for (String part : raw.split(",")) {
                String token = part.trim();
                if (token.isEmpty()) {
                    continue;
                }
                boolean negated = token.startsWith("-");
                String name = negated ? token.substring(1).trim() : token;
                DietaryFlag flag = DietaryFlag.fromToken(name);
                if (flag == null) {
                    throw new InvalidDietProfileException(token, allowedFlagTokens());
                }
                boolean required = !negated;
                if (cancelled.contains(flag)) {
                    continue;
                }
                Boolean existing = effective.get(flag);
                if (existing == null) {
                    effective.put(flag, required);
                } else if (existing != required) {
                    // Same flag requested both true and false → the client doesn't care.
                    effective.remove(flag);
                    cancelled.add(flag);
                }
            }
        }
        return effective;
    }

    private static String allowedFlagTokens() {
        return java.util.Arrays.stream(DietaryFlag.values())
                .map(DietaryFlag::token)
                .collect(Collectors.joining(", "));
    }

    /** Include + exclude ingredient names parsed from the unified {@code ingredients} param. */
    record IngredientFilters(List<String> include, List<String> exclude) {
    }

    /**
     * Parses the {@code ingredients} query tokens into include/exclude name lists, in the same
     * form as {@link #toDietaryFilters}: each token is an ingredient name optionally prefixed
     * with {@code -} (negation). A bare name requires the recipe to <em>contain</em> that
     * ingredient; {@code -name} requires it to <em>not contain</em> it. Names are compared
     * case-insensitively; a name listed with both signs cancels out (dropped — no restriction).
     * Returns empty lists when there are no tokens. The final lower-casing/dedup for the SQL is
     * done by {@code RecipeSearchRepository}.
     */
    static IngredientFilters toIngredientFilters(List<String> ingredients) {
        Map<String, Boolean> effective = new LinkedHashMap<>(); // lower(name) -> include(true)/exclude(false)
        if (ingredients == null) {
            return new IngredientFilters(List.of(), List.of());
        }
        Set<String> cancelled = new HashSet<>();
        for (String raw : ingredients) {
            if (raw == null) {
                continue;
            }
            for (String part : raw.split(",")) {
                String token = part.trim();
                if (token.isEmpty()) {
                    continue;
                }
                boolean negated = token.startsWith("-");
                String name = negated ? token.substring(1).trim() : token;
                if (name.isEmpty()) {
                    continue;
                }
                String key = name.toLowerCase(Locale.ROOT);
                boolean include = !negated;
                if (cancelled.contains(key)) {
                    continue;
                }
                Boolean existing = effective.get(key);
                if (existing == null) {
                    effective.put(key, include);
                } else if (existing != include) {
                    // Same name required to be both present and absent → the client doesn't care.
                    effective.remove(key);
                    cancelled.add(key);
                }
            }
        }
        List<String> include = new ArrayList<>();
        List<String> exclude = new ArrayList<>();
        effective.forEach((name, isInclude) -> (isInclude ? include : exclude).add(name));
        return new IngredientFilters(include, exclude);
    }

    /** A recipe view with its pre-joined ingredients → the API {@code Recipe} DTO. */
    static Recipe toDto(RecipeView view) {
        List<RecipeIngredient> ingredients = view.ingredients().stream()
                .map(RecipeMapper::toIngredientDto)
                .sorted(Comparator.comparing(RecipeIngredient::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new Recipe(
                view.publicId(),
                view.name(),
                view.servings(),
                view.instructions(),
                ingredients,
                toProfileDto(view.dietaryProfile()));
    }

    /** A page of recipe views → the API {@code RecipePage}. */
    static RecipePage toPageDto(Page<RecipeView> page) {
        List<Recipe> content = page.getContent().stream().map(RecipeMapper::toDto).toList();
        return new RecipePage(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    private static RecipeIngredient toIngredientDto(RecipeView.Ingredient i) {
        RecipeIngredient dto = new RecipeIngredient(i.publicId(), i.name());
        dto.setQuantity(i.quantity());
        dto.setUnit(toDtoUnit(i.unit()));
        return dto;
    }

    private static DietaryProfile toProfileDto(com.abnamro.recipe.model.DietaryProfile profile) {
        return new DietaryProfile(
                profile.is(DietaryFlag.VEGETARIAN),
                profile.is(DietaryFlag.VEGAN),
                profile.is(DietaryFlag.MEAT),
                profile.is(DietaryFlag.GLUTEN),
                profile.is(DietaryFlag.WHEAT),
                profile.is(DietaryFlag.NUT));
    }

    private static com.abnamro.recipe.model.MeasurementUnit toDomainUnit(MeasurementUnit unit) {
        return unit == null ? null : com.abnamro.recipe.model.MeasurementUnit.valueOf(unit.name());
    }

    private static MeasurementUnit toDtoUnit(com.abnamro.recipe.model.MeasurementUnit unit) {
        return unit == null ? null : MeasurementUnit.valueOf(unit.name());
    }
}
