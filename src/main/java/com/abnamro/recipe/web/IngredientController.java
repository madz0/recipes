package com.abnamro.recipe.web;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.abnamro.recipe.api.IngredientsApi;
import com.abnamro.recipe.api.model.Ingredient;
import com.abnamro.recipe.api.model.IngredientCreateRequest;
import com.abnamro.recipe.api.model.IngredientPage;
import com.abnamro.recipe.api.model.IngredientType;
import com.abnamro.recipe.service.IngredientService;

/**
 * REST adapter implementing the generated {@link IngredientsApi} contract. Keeps
 * the web concerns (HTTP status, Location header, DTO mapping) here and delegates
 * the business rules to {@link IngredientService}.
 */
@RestController
public class IngredientController implements IngredientsApi {

    private final IngredientService service;

    public IngredientController(IngredientService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<Ingredient> createIngredient(IngredientCreateRequest request) {
        var created = service.create(request.getName(), IngredientMapper.toDomainType(request.getType()));
        Ingredient dto = IngredientMapper.toDto(created);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(dto.getId())
                .toUri();
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    public ResponseEntity<IngredientPage> listIngredients(Integer page, Integer size, IngredientType type) {
        var result = service.list(page, size, IngredientMapper.toDomainType(type));
        return ResponseEntity.ok(IngredientMapper.toPageDto(result));
    }

    @Override
    public ResponseEntity<Ingredient> getIngredient(UUID id) {
        return ResponseEntity.ok(IngredientMapper.toDto(service.get(id)));
    }

    @Override
    public ResponseEntity<Void> deleteIngredient(UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
