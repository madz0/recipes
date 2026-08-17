package com.abnamro.recipe.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.abnamro.recipe.api.RecipesApi;
import com.abnamro.recipe.api.model.Recipe;
import com.abnamro.recipe.api.model.RecipeCreateRequest;
import com.abnamro.recipe.api.model.RecipePage;
import com.abnamro.recipe.repository.RecipeSearchCriteria;
import com.abnamro.recipe.service.RecipeService;

/**
 * REST adapter implementing the generated {@link RecipesApi} contract. Keeps the
 * web concerns (HTTP status, Location header, DTO mapping) here and delegates the
 * business rules to {@link RecipeService}.
 */
@RestController
public class RecipeController implements RecipesApi {

    private final RecipeService service;

    public RecipeController(RecipeService service) {
        this.service = service;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Recipe> createRecipe(RecipeCreateRequest request) {
        var created = service.create(
                request.getName(),
                request.getServings(),
                request.getInstructions(),
                RecipeMapper.toDomainSelections(request.getIngredients()));
        Recipe dto = RecipeMapper.toDto(created);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(dto.getId())
                .toUri();
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Recipe> getRecipe(UUID id) {
        return ResponseEntity.ok(RecipeMapper.toDto(service.get(id)));
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RecipePage> listRecipes(Integer page, Integer size, List<String> dietProfiles,
                                                  Integer servings, List<String> ingredients,
                                                  String instructionsContains) {
        var parsedIngredients = RecipeMapper.toIngredientFilters(ingredients);
        var criteria = new RecipeSearchCriteria(
                RecipeMapper.toDietaryFilters(dietProfiles),
                servings, parsedIngredients.include(), parsedIngredients.exclude(), instructionsContains);
        var result = service.list(page, size, criteria);
        return ResponseEntity.ok(RecipeMapper.toPageDto(result));
    }
}
