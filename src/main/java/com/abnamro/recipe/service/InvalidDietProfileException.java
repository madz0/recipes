package com.abnamro.recipe.service;

/**
 * Thrown when the {@code dietProfiles} filter contains a token that is not a known
 * dietary flag (optionally sign-prefixed). Per the Recipes API contract this is a
 * client error, so the web layer maps it to {@code 400 Bad Request}.
 */
public class InvalidDietProfileException extends RuntimeException {

    private final String token;

    public InvalidDietProfileException(String token, String allowed) {
        super("Unknown dietProfiles flag '" + token + "'. Allowed flags: " + allowed
                + " (each optionally prefixed with '-').");
        this.token = token;
    }

    public String token() {
        return token;
    }
}
