package com.abnamro.recipe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.abnamro.recipe.bootstrap.IngredientBootstrapProperties;

@SpringBootApplication
@EnableConfigurationProperties(IngredientBootstrapProperties.class)
public class Main {

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
