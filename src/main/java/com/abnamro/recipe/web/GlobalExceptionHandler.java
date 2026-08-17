package com.abnamro.recipe.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.abnamro.recipe.service.DuplicateIngredientNameException;
import com.abnamro.recipe.service.IngredientNotFoundException;
import com.abnamro.recipe.service.InvalidDietProfileException;
import com.abnamro.recipe.service.RecipeIngredientNotFoundException;
import com.abnamro.recipe.service.RecipeNotFoundException;

import jakarta.validation.ConstraintViolationException;

/**
 * Translates domain exceptions into RFC 9457 {@link ProblemDetail} responses
 * ({@code application/problem+json}), matching the {@code Problem} schema in the
 * contract.
 *
 * <p>Validation / malformed-request failures (400) are left to Spring MVC's own
 * ProblemDetail handling, enabled via {@code spring.mvc.problemdetails.enabled=true}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({IngredientNotFoundException.class, RecipeNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not Found");
        return problem;
    }

    /**
     * A recipe referencing an unknown ingredient id. Per the Recipes API contract
     * this is a client error rendered as {@code 400 Bad Request} (not 404).
     */
    @ExceptionHandler(RecipeIngredientNotFoundException.class)
    public ProblemDetail handleRecipeIngredientNotFound(RecipeIngredientNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        return problem;
    }

    /**
     * An unknown token in the {@code dietProfiles} filter. Per the Recipes API
     * contract this is a client error rendered as {@code 400 Bad Request}.
     */
    @ExceptionHandler(InvalidDietProfileException.class)
    public ProblemDetail handleInvalidDietProfile(InvalidDietProfileException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        return problem;
    }

    @ExceptionHandler(DuplicateIngredientNameException.class)
    public ProblemDetail handleConflict(DuplicateIngredientNameException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflict");
        return problem;
    }

    /**
     * Query-parameter constraint violations (e.g. {@code size} out of the 1..100
     * range). The generated API interface is annotated {@code @Validated}, so these
     * surface as {@link ConstraintViolationException} via method validation rather
     * than through Spring MVC's built-in handling.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        return problem;
    }
}
