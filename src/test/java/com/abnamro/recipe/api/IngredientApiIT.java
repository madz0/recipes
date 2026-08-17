package com.abnamro.recipe.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.abnamro.recipe.api.model.Ingredient;
import com.abnamro.recipe.api.model.IngredientCreateRequest;
import com.abnamro.recipe.api.model.IngredientPage;
import com.abnamro.recipe.api.model.IngredientType;

/**
 * End-to-end HTTP test of the Ingredients API against the real application stack
 * (controller → service → Spring Data JDBC → Liquibase schema). Like the bootstrap
 * IT it runs on H2 by default and on a real Postgres under {@code -Ppostgres};
 * named {@code *IT} so it runs under Failsafe.
 *
 * <p>The {@code test} profile seeds the catalog on startup, so tests avoid
 * asserting absolute totals and instead use uniquely-named ingredients they create.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@DisplayName("Ingredients API — end-to-end HTTP tests")
class IngredientApiIT {

    private static final String BASE = "/api/v1/ingredients";

    private TestRestTemplate rest;

    // Wrap the injected template with the demo credentials once, so every request in this
    // class is authenticated (the user holds ADMIN, covering the write endpoints too).
    @Autowired
    void configureAuth(TestRestTemplate template) {
        this.rest = template.withBasicAuth("recipes", "recipes-demo");
    }

    private static String uniqueName() {
        return "Test ingredient " + UUID.randomUUID();
    }

    /**
     * Given a valid ingredient create request, when it is POSTed then the API
     * responds 201 Created with a {@code Location} header pointing at the new
     * resource and a body echoing the persisted id, name and type.
     */
    @DisplayName("POST creates an ingredient: 201 + Location header + echoed body")
    @Test
    void createReturns201WithLocationAndBody() {
        String name = uniqueName();
        ResponseEntity<Ingredient> response =
                rest.postForEntity(BASE, new IngredientCreateRequest(name, IngredientType.WHEAT), Ingredient.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Ingredient body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getId()).isNotNull();
        assertThat(body.getName()).isEqualTo(name);
        assertThat(body.getType()).isEqualTo(IngredientType.WHEAT);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().toString()).endsWith(BASE + "/" + body.getId());
    }

    /**
     * Given an ingredient with a given name already exists, when a second create
     * request reuses that name then the API rejects it with 409 Conflict and an
     * {@code application/problem+json} error body.
     */
    @DisplayName("POST with a duplicate name → 409 problem+json")
    @Test
    void duplicateNameReturns409ProblemJson() {
        String name = uniqueName();
        rest.postForEntity(BASE, new IngredientCreateRequest(name, IngredientType.VEGETABLE), Ingredient.class);

        ResponseEntity<String> conflict =
                rest.postForEntity(BASE, new IngredientCreateRequest(name, IngredientType.VEGETABLE), String.class);

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    /**
     * Walks the full lifecycle of one ingredient: given a freshly created
     * ingredient, GET by id returns it (200); when it is DELETEd it returns 204;
     * then GET by id returns 404 problem+json and a second DELETE also returns 404
     * (delete is not silently idempotent for an already-removed id).
     */
    @DisplayName("GET by id returns it; after DELETE → 404, and re-DELETE → 404")
    @Test
    void getByIdReturnsIngredientThen404AfterDelete() {
        String name = uniqueName();
        UUID id = rest.postForEntity(BASE, new IngredientCreateRequest(name, IngredientType.MEAT), Ingredient.class)
                .getBody().getId();

        ResponseEntity<Ingredient> found = rest.getForEntity(BASE + "/" + id, Ingredient.class);
        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody().getName()).isEqualTo(name);

        ResponseEntity<Void> deleted = rest.exchange(BASE + "/" + id, org.springframework.http.HttpMethod.DELETE,
                null, Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> gone = rest.getForEntity(BASE + "/" + id, String.class);
        assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(gone.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        ResponseEntity<Void> deleteAgain = rest.exchange(BASE + "/" + id, org.springframework.http.HttpMethod.DELETE,
                null, Void.class);
        assertThat(deleteAgain.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Given an id that was never created, when it is requested then the API
     * responds 404 Not Found.
     */
    @DisplayName("GET an unknown id → 404")
    @Test
    void getUnknownIdReturns404() {
        ResponseEntity<String> response = rest.getForEntity(BASE + "/" + UUID.randomUUID(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Given the seeded catalog, when the first page is requested with {@code size=5}
     * then the response reports page 0, size 5, exactly 5 items, {@code first=true},
     * and the returned names are sorted ascending.
     */
    @DisplayName("GET list is paginated and ordered by name")
    @Test
    void listIsPaginatedAndOrdered() {
        ResponseEntity<IngredientPage> response =
                rest.getForEntity(BASE + "?page=0&size=5", IngredientPage.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        IngredientPage page = response.getBody();
        assertThat(page).isNotNull();
        assertThat(page.getPage()).isEqualTo(0);
        assertThat(page.getSize()).isEqualTo(5);
        assertThat(page.getContent()).hasSize(5);
        assertThat(page.getFirst()).isTrue();
        // Names come back sorted ascending.
        assertThat(page.getContent().stream().map(Ingredient::getName).toList())
                .isSorted();
    }

    /**
     * Given at least one ingredient of a given type exists, when the list is
     * filtered by {@code type=NUT} then the result is non-empty and every returned
     * ingredient is of that type.
     */
    @DisplayName("type filter returns only ingredients of that type")
    @Test
    void listWithTypeFilterReturnsOnlyThatType() {
        rest.postForEntity(BASE, new IngredientCreateRequest(uniqueName(), IngredientType.NUT), Ingredient.class);

        ResponseEntity<IngredientPage> response =
                rest.getForEntity(BASE + "?type=NUT&size=100", IngredientPage.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent())
                .isNotEmpty()
                .allMatch(i -> i.getType() == IngredientType.NUT);
    }

    /**
     * Given a page {@code size} above the allowed maximum, when the list is
     * requested then the API rejects it with 400 Bad Request and an
     * {@code application/problem+json} error body.
     */
    @DisplayName("Page size above the allowed maximum → 400 problem+json")
    @Test
    void invalidPageSizeReturns400ProblemJson() {
        ResponseEntity<String> response = rest.getForEntity(BASE + "?size=101", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    /**
     * Given a {@code type} value that is not a known ingredient type, when the list
     * is requested then the API rejects it with 400 Bad Request.
     */
    @DisplayName("Unknown type value → 400")
    @Test
    void invalidTypeValueReturns400() {
        ResponseEntity<String> response = rest.getForEntity(BASE + "?type=NOT_A_TYPE", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
