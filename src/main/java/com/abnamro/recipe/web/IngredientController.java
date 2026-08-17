package com.abnamro.recipe.web;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final IngredientMapper mapper;

    public IngredientController(IngredientService service, IngredientMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Ingredient> createIngredient(IngredientCreateRequest request) {
        var created = service.create(request.getName(), mapper.toDomainType(request.getType()));
        Ingredient dto = mapper.toDto(created);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(dto.getId())
                .toUri();
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<IngredientPage> listIngredients(Integer page, Integer size, IngredientType type) {
        var result = service.list(page, size, mapper.toDomainType(type));
        return ResponseEntity.ok(mapper.toPageDto(result));
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Ingredient> getIngredient(UUID id) {
        return ResponseEntity.ok(mapper.toDto(service.get(id)));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteIngredient(UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
