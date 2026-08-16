package com.abnamro.recipe.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

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
class IngredientApiIT {

    private static final String BASE = "/api/v1/ingredients";

    @Autowired
    private TestRestTemplate rest;

    private static String uniqueName() {
        return "Test ingredient " + UUID.randomUUID();
    }

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

    @Test
    void getUnknownIdReturns404() {
        ResponseEntity<String> response = rest.getForEntity(BASE + "/" + UUID.randomUUID(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

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

    @Test
    void invalidPageSizeReturns400ProblemJson() {
        ResponseEntity<String> response = rest.getForEntity(BASE + "?size=101", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void invalidTypeValueReturns400() {
        ResponseEntity<String> response = rest.getForEntity(BASE + "?type=NOT_A_TYPE", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
