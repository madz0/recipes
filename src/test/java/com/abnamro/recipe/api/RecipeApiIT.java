package com.abnamro.recipe.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
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

import com.abnamro.recipe.model.Ingredient;
import com.abnamro.recipe.model.IngredientType;
import com.abnamro.recipe.service.IngredientService;
import com.abnamro.recipe.api.model.MeasurementUnit;
import com.abnamro.recipe.api.model.Recipe;
import com.abnamro.recipe.api.model.RecipeCreateRequest;
import com.abnamro.recipe.api.model.RecipeIngredientSelection;
import com.abnamro.recipe.api.model.RecipePage;

/**
 * End-to-end HTTP test of the Recipes API against the real application stack
 * (controller → service → Spring Data JDBC → Liquibase schema). Runs on H2 by
 * default and on a real Postgres under {@code -Ppostgres}; named {@code *IT} so it
 * runs under Failsafe.
 *
 * <p>This suite exercises <strong>only</strong> the Recipes HTTP API. The catalog
 * ingredients each test needs are just fixtures, so they are seeded directly
 * through {@link IngredientService} rather than the Ingredients HTTP API (which is
 * covered by {@code IngredientApiIT}). Ingredient names are unique and assertions
 * check membership by id (never absolute totals), so the suite is independent of
 * the seeded catalog and of other tests. Filter tests isolate their recipes so the
 * same assertions hold whether the {@code instructionsContains} filter runs the
 * Postgres full-text branch or the H2 {@code LIKE} fallback.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@DisplayName("Recipes API — end-to-end HTTP tests")
class RecipeApiIT {

    private static final String RECIPES = "/api/v1/recipes";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private IngredientService ingredientService;

    // --- helpers -----------------------------------------------------------

    /** Seeds a uniquely-named catalog ingredient directly via the service (test fixture). */
    private Ingredient createIngredient(IngredientType type) {
        return ingredientService.create("IT ingredient " + UUID.randomUUID(), type);
    }

    private static RecipeIngredientSelection selection(UUID ingredientId) {
        return new RecipeIngredientSelection(ingredientId)
                .quantity(BigDecimal.valueOf(100))
                .unit(MeasurementUnit.GRAMS);
    }

    private RecipeCreateRequest recipeRequest(String instructions, UUID... ingredientIds) {
        return new RecipeCreateRequest(
                "IT recipe " + UUID.randomUUID(),
                4,
                instructions,
                List.of(ingredientIds).stream().map(RecipeApiIT::selection).toList());
    }

    private Recipe createRecipe(RecipeCreateRequest request) {
        ResponseEntity<Recipe> response = rest.postForEntity(RECIPES, request, Recipe.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private List<UUID> listIds(String query) {
        RecipePage page = rest.getForEntity(RECIPES + query, RecipePage.class).getBody();
        assertThat(page).isNotNull();
        return page.getContent().stream().map(Recipe::getId).toList();
    }

    // --- create ------------------------------------------------------------

    /**
     * Given a recipe whose only ingredient is a vegetable, when it is created then
     * the API responds 201 Created with a {@code Location} header, a body echoing
     * the name, servings and ingredient details, and a dietary profile derived from
     * the ingredient types as vegetarian & vegan (meat/gluten/wheat/nut all false).
     */
    @DisplayName("POST creates a recipe: 201 + Location + vegetarian profile derived from ingredients")
    @Test
    void createReturns201WithLocationAndDerivedVegetarianProfile() {
        Ingredient vegetable = createIngredient(IngredientType.VEGETABLE);
        RecipeCreateRequest request = recipeRequest("Roast in the oven for 30 minutes.", vegetable.publicId());

        ResponseEntity<Recipe> response = rest.postForEntity(RECIPES, request, Recipe.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Recipe body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getId()).isNotNull();
        assertThat(body.getName()).isEqualTo(request.getName());
        assertThat(body.getServings()).isEqualTo(4);
        assertThat(body.getIngredients()).singleElement()
                .satisfies(ri -> {
                    assertThat(ri.getIngredientId()).isEqualTo(vegetable.publicId());
                    assertThat(ri.getName()).isEqualTo(vegetable.name());
                    assertThat(ri.getUnit()).isEqualTo(MeasurementUnit.GRAMS);
                });
        assertThat(body.getDietaryProfile().getVegetarian()).isTrue();
        assertThat(body.getDietaryProfile().getVegan()).isTrue();
        assertThat(body.getDietaryProfile().getMeat()).isFalse();
        assertThat(body.getDietaryProfile().getGluten()).isFalse();
        assertThat(body.getDietaryProfile().getWheat()).isFalse();
        assertThat(body.getDietaryProfile().getNut()).isFalse();
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().toString()).endsWith(RECIPES + "/" + body.getId());
    }

    /**
     * Given a recipe containing meat, wheat and nut ingredients, when it is created
     * then the derived dietary profile flags meat, gluten, wheat and nut as true and
     * vegetarian/vegan as false.
     */
    @DisplayName("Dietary profile reflects meat/wheat/nut ingredient types")
    @Test
    void dietaryProfileIsDerivedFromIngredientTypes() {
        Ingredient meat = createIngredient(IngredientType.MEAT);
        Ingredient wheat = createIngredient(IngredientType.WHEAT);
        Ingredient nut = createIngredient(IngredientType.NUT);

        Recipe body = createRecipe(recipeRequest(
                "Cook everything together.", meat.publicId(), wheat.publicId(), nut.publicId()));

        assertThat(body.getDietaryProfile().getMeat()).isTrue();
        assertThat(body.getDietaryProfile().getVegetarian()).isFalse();
        assertThat(body.getDietaryProfile().getVegan()).isFalse();
        assertThat(body.getDietaryProfile().getGluten()).isTrue();
        assertThat(body.getDietaryProfile().getWheat()).isTrue();
        assertThat(body.getDietaryProfile().getNut()).isTrue();
    }

    /**
     * Given a create request that references an ingredient id not in the catalog,
     * when it is POSTed then the API rejects it with 400 Bad Request and an
     * {@code application/problem+json} error body.
     */
    @DisplayName("POST with an unknown ingredient id → 400 problem+json")
    @Test
    void createWithUnknownIngredientReturns400ProblemJson() {
        RecipeCreateRequest request = recipeRequest("Mix well.", UUID.randomUUID());

        ResponseEntity<String> response = rest.postForEntity(RECIPES, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    /**
     * Given a create request with an empty ingredient list, when it is POSTed then
     * the API rejects it with 400 Bad Request (at least one ingredient is required).
     */
    @DisplayName("POST with no ingredients → 400")
    @Test
    void createWithNoIngredientsReturns400() {
        RecipeCreateRequest request = new RecipeCreateRequest("IT empty", 2, "Do nothing.", List.of());

        ResponseEntity<String> response = rest.postForEntity(RECIPES, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Given a create request with a blank recipe name, when it is POSTed then the
     * API rejects it with 400 Bad Request (name is mandatory).
     */
    @DisplayName("POST with a blank name → 400")
    @Test
    void createWithBlankNameReturns400() {
        Ingredient vegetable = createIngredient(IngredientType.VEGETABLE);
        RecipeCreateRequest request =
                new RecipeCreateRequest("", 2, "Chop.", List.of(selection(vegetable.publicId())));

        ResponseEntity<String> response = rest.postForEntity(RECIPES, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- get ---------------------------------------------------------------

    /**
     * Given a freshly created recipe, when it is fetched by id then the API returns
     * 200 with that recipe; and given an id that does not exist, when it is fetched
     * then the API returns 404 with an {@code application/problem+json} body.
     */
    @DisplayName("GET by id returns the recipe; an unknown id → 404 problem+json")
    @Test
    void getByIdReturnsRecipeThen404ForUnknown() {
        Ingredient vegetable = createIngredient(IngredientType.VEGETABLE);
        UUID id = createRecipe(recipeRequest("Steam gently.", vegetable.publicId())).getId();

        ResponseEntity<Recipe> found = rest.getForEntity(RECIPES + "/" + id, Recipe.class);
        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody().getId()).isEqualTo(id);

        ResponseEntity<String> unknown = rest.getForEntity(RECIPES + "/" + UUID.randomUUID(), String.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    // --- list & filters ----------------------------------------------------

    /**
     * Given several recipes exist, when the first page is requested with
     * {@code size=5} then the response reports page 0, size 5, {@code first=true},
     * and the returned names are ordered case-insensitively ascending.
     */
    @DisplayName("GET list is paginated and ordered by name (case-insensitive)")
    @Test
    void listIsPaginatedAndOrderedByName() {
        Ingredient vegetable = createIngredient(IngredientType.VEGETABLE);
        createRecipe(recipeRequest("Boil.", vegetable.publicId()));
        createRecipe(recipeRequest("Fry.", vegetable.publicId()));

        RecipePage page = rest.getForEntity(RECIPES + "?page=0&size=5", RecipePage.class).getBody();

        assertThat(page).isNotNull();
        assertThat(page.getPage()).isEqualTo(0);
        assertThat(page.getSize()).isEqualTo(5);
        assertThat(page.getFirst()).isTrue();
        assertThat(page.getContent().stream().map(Recipe::getName).toList())
                .isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
    }

    /**
     * Given one vegetable-only recipe and one meat recipe, when the list is filtered
     * by {@code dietProfiles=vegetarian} then it includes the veg recipe and excludes
     * the meat one; {@code dietProfiles=-meat} behaves identically, and
     * {@code dietProfiles=meat} is the inverse.
     */
    @DisplayName("dietProfiles vegetarian / -meat / meat separate meat from non-meat recipes")
    @Test
    void filterByVegetarianSeparatesMeatFromNonMeat() {
        Ingredient vegetable = createIngredient(IngredientType.VEGETABLE);
        Ingredient meat = createIngredient(IngredientType.MEAT);
        UUID veg = createRecipe(recipeRequest("Grill the veg.", vegetable.publicId())).getId();
        UUID carnivore = createRecipe(recipeRequest("Grill the steak.", meat.publicId())).getId();

        assertThat(listIds("?dietProfiles=vegetarian&size=100")).contains(veg).doesNotContain(carnivore);
        assertThat(listIds("?dietProfiles=-meat&size=100")).contains(veg).doesNotContain(carnivore);
        assertThat(listIds("?dietProfiles=meat&size=100")).contains(carnivore).doesNotContain(veg);
    }

    /**
     * Given a vegan (vegetable-only) recipe and a vegetarian-but-not-vegan (dairy)
     * recipe, when filtered by {@code dietProfiles=vegetarian} both appear (vegetarian
     * is the looser diet), but {@code dietProfiles=vegan} returns only the vegan one —
     * the vegetarian ⊇ vegan hierarchy holds without any special-casing.
     */
    @DisplayName("dietProfiles=vegetarian also returns vegan recipes; =vegan excludes dairy recipes")
    @Test
    void vegetarianSearchIncludesVeganButVeganExcludesDairy() {
        Ingredient vegetable = createIngredient(IngredientType.VEGETABLE);
        Ingredient dairy = createIngredient(IngredientType.DAIRY);
        UUID vegan = createRecipe(recipeRequest("Toss the salad.", vegetable.publicId())).getId();
        UUID cheesy = createRecipe(recipeRequest("Melt the cheese.", dairy.publicId())).getId();

        List<UUID> vegetarian = listIds("?dietProfiles=vegetarian&size=100");
        assertThat(vegetarian).contains(vegan).contains(cheesy);

        List<UUID> veganOnly = listIds("?dietProfiles=vegan&size=100");
        assertThat(veganOnly).contains(vegan).doesNotContain(cheesy);
    }

    /**
     * Given a dairy recipe (vegetarian but not vegan) and a meat recipe, when the list
     * is filtered by {@code dietProfiles=-meat} the dairy recipe is returned and the meat
     * one excluded. This pins that {@code meat=false} containment matches a non-meat recipe
     * regardless of its other flags — not only vegetable-only (fully vegan) recipes.
     */
    @DisplayName("dietProfiles=-meat returns a non-vegan (dairy) recipe, excludes meat")
    @Test
    void negatedMeatReturnsNonVeganDairyRecipe() {
        Ingredient dairy = createIngredient(IngredientType.DAIRY);
        Ingredient meat = createIngredient(IngredientType.MEAT);
        UUID cheesy = createRecipe(recipeRequest("Melt the cheese.", dairy.publicId())).getId();
        UUID carnivore = createRecipe(recipeRequest("Sear the steak.", meat.publicId())).getId();

        assertThat(listIds("?dietProfiles=-meat&size=100")).contains(cheesy).doesNotContain(carnivore);
    }

    /**
     * Given a nut recipe and a nut-free recipe, {@code dietProfiles=-nut} excludes the
     * nut one, and {@code dietProfiles=-gluten} keeps a gluten-free recipe while
     * dropping a wheat (gluten) one.
     */
    @DisplayName("dietProfiles negation filters allergens (-nut, -gluten)")
    @Test
    void filterByNegatedAllergenFlags() {
        Ingredient nut = createIngredient(IngredientType.NUT);
        Ingredient wheat = createIngredient(IngredientType.WHEAT);
        Ingredient glutenFree = createIngredient(IngredientType.GLUTEN_FREE_WHEAT);
        UUID nutty = createRecipe(recipeRequest("Crush the nuts.", nut.publicId())).getId();
        UUID wheaty = createRecipe(recipeRequest("Knead the dough.", wheat.publicId())).getId();
        UUID gfree = createRecipe(recipeRequest("Cook the rice.", glutenFree.publicId())).getId();

        assertThat(listIds("?dietProfiles=-nut&size=100")).doesNotContain(nutty).contains(gfree);
        assertThat(listIds("?dietProfiles=-gluten&size=100")).contains(gfree).doesNotContain(wheaty);
    }

    /**
     * Given a wheat (gluten) recipe, {@code dietProfiles=gluten,-gluten} lists the flag
     * with both signs, which cancels out — so the recipe is returned just as it would be
     * with no dietary filter at all.
     */
    @DisplayName("dietProfiles=gluten,-gluten cancels out (no restriction)")
    @Test
    void conflictingSignsCancelOut() {
        Ingredient wheat = createIngredient(IngredientType.WHEAT);
        UUID wheaty = createRecipe(recipeRequest("Bake the bread.", wheat.publicId())).getId();

        assertThat(listIds("?dietProfiles=gluten,-gluten&size=100")).contains(wheaty);
    }

    /**
     * Given a {@code dietProfiles} token that is not a known dietary flag, when the
     * list is requested then the API rejects it with 400 Bad Request and an
     * {@code application/problem+json} error body.
     */
    @DisplayName("Unknown dietProfiles flag → 400 problem+json")
    @Test
    void unknownDietProfileFlagReturns400ProblemJson() {
        ResponseEntity<String> response = rest.getForEntity(RECIPES + "?dietProfiles=bogus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    /**
     * Given a recipe with a distinctive servings count, when the list is filtered by
     * that exact {@code servings} value it is included, and when filtered by a
     * different value it is excluded (the filter matches exactly, not a range).
     */
    @DisplayName("Filter by exact servings count")
    @Test
    void filterByServings() {
        Ingredient vegetable = createIngredient(IngredientType.VEGETABLE);
        RecipeCreateRequest request = new RecipeCreateRequest(
                "IT servings " + UUID.randomUUID(), 97, "Simmer.", List.of(selection(vegetable.publicId())));
        UUID id = createRecipe(request).getId();

        assertThat(listIds("?servings=97&size=100")).contains(id);
        assertThat(listIds("?servings=96&size=100")).doesNotContain(id);
    }

    /**
     * Given a recipe containing ingredients a and b, when the list is filtered by
     * {@code ingredients=a,b} (both bare = must contain) it is included; but when filtered
     * for a plus a third ingredient c the recipe lacks, it is excluded — bare names require
     * ALL to be present, not any.
     */
    @DisplayName("ingredients (bare names) require ALL to be present")
    @Test
    void filterByIncludeIngredientsRequiresAll() {
        Ingredient a = createIngredient(IngredientType.VEGETABLE);
        Ingredient b = createIngredient(IngredientType.VEGETABLE);
        Ingredient c = createIngredient(IngredientType.VEGETABLE);
        Recipe recipe = createRecipe(recipeRequest("Combine a and b.", a.publicId(), b.publicId()));

        assertThat(listIds("?size=100&ingredients=" + enc(a.name()) + "," + enc(b.name())))
                .contains(recipe.getId());
        // Requires ALL: adding an ingredient the recipe lacks excludes it.
        assertThat(listIds("?size=100&ingredients=" + enc(a.name()) + "," + enc(c.name())))
                .doesNotContain(recipe.getId());
    }

    /**
     * Given a recipe that uses ingredient a, when the list is filtered by
     * {@code ingredients=-a} (negated = must not contain) the recipe is removed; and when
     * filtered to exclude an ingredient the recipe does not use, it is still returned.
     */
    @DisplayName("ingredients (negated names) remove recipes containing that ingredient")
    @Test
    void filterByExcludeIngredientsRemovesMatches() {
        Ingredient a = createIngredient(IngredientType.VEGETABLE);
        Recipe recipe = createRecipe(recipeRequest("Uses a.", a.publicId()));

        assertThat(listIds("?size=100&ingredients=-" + enc(a.name()))).doesNotContain(recipe.getId());
        assertThat(listIds("?size=100&ingredients=-" + enc("IT ingredient no-such-" + UUID.randomUUID())))
                .contains(recipe.getId());
    }

    /**
     * Given a recipe that uses ingredient a but not b, when the list is filtered by
     * {@code ingredients=a,-b} (contain a AND not contain b) the recipe is returned;
     * flipping to {@code ingredients=-a,b} (not a AND contain b) excludes it — the single
     * param combines include and exclude just like {@code dietProfiles}.
     */
    @DisplayName("ingredients combines include and exclude in one param (a,-b)")
    @Test
    void filterByIngredientsCombinesIncludeAndExclude() {
        Ingredient a = createIngredient(IngredientType.VEGETABLE);
        Ingredient b = createIngredient(IngredientType.VEGETABLE);
        Recipe recipe = createRecipe(recipeRequest("Uses a only.", a.publicId()));

        assertThat(listIds("?size=100&ingredients=" + enc(a.name()) + ",-" + enc(b.name())))
                .contains(recipe.getId());
        assertThat(listIds("?size=100&ingredients=-" + enc(a.name()) + "," + enc(b.name())))
                .doesNotContain(recipe.getId());
    }

    /**
     * Given a recipe whose instructions contain a distinctive standalone word, when
     * the list is filtered by {@code instructionsContains} for that word it is
     * included, and for a non-matching token it is excluded. The word is chosen so
     * the assertions hold under both the Postgres full-text branch and the H2
     * {@code LIKE} fallback.
     */
    @DisplayName("instructionsContains matches a whole word (Postgres FTS & H2 LIKE)")
    @Test
    void filterByInstructionsContainsMatchesWholeWord() {
        Ingredient vegetable = createIngredient(IngredientType.VEGETABLE);
        // A distinctive, standalone word: matches under both the Postgres tsvector
        // full-text branch and the H2 LIKE fallback.
        String token = "sizzlewok" + UUID.randomUUID().toString().replace("-", "");
        UUID id = createRecipe(recipeRequest("Heat the " + token + " until hot.", vegetable.publicId())).getId();

        assertThat(listIds("?size=100&instructionsContains=" + token)).contains(id);
        assertThat(listIds("?size=100&instructionsContains=nomatchtoken" + token)).doesNotContain(id);
    }

    /**
     * Given a page {@code size} above the allowed maximum, when the list is
     * requested then the API rejects it with 400 Bad Request and an
     * {@code application/problem+json} error body.
     */
    @DisplayName("Page size above the allowed maximum → 400 problem+json")
    @Test
    void invalidPageSizeReturns400ProblemJson() {
        ResponseEntity<String> response = rest.getForEntity(RECIPES + "?size=101", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    private static String enc(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
