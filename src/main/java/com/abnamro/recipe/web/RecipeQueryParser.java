package com.abnamro.recipe.web;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.abnamro.recipe.model.DietaryFlag;
import com.abnamro.recipe.service.exception.InvalidDietProfileException;

/**
 * Parses the recipe-search query parameters ({@code dietProfiles}, {@code ingredients})
 * into the filter shapes consumed by {@code RecipeSearchCriteria}. This is parsing
 * logic with business rules (sign handling, cancel-out, unknown-token rejection), not
 * object mapping, so it lives outside the MapStruct {@link RecipeMapper}.
 */
final class RecipeQueryParser {

    private RecipeQueryParser() {
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
}
