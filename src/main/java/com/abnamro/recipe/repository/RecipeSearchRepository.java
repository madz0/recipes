package com.abnamro.recipe.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.sql.DataSource;

import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Executes the dynamic, multi-filter recipe listing query. The combination of
 * optional AND-ed filters, "contains all / none of these ingredient names", and
 * full-text search over instructions cannot be expressed as a Spring Data JDBC
 * derived method, so the SQL is built here with {@link NamedParameterJdbcTemplate}
 * (the same tool the ingredient bootstrap uses).
 *
 * <p>The query resolves matching recipe primary keys (ordered by name, paged) plus
 * a total count; the service re-hydrates the {@link com.abnamro.recipe.model.Recipe}
 * aggregates from those ids.
 *
 * <p><strong>Text search.</strong> {@code instructionsContains} is dialect-aware:
 * on PostgreSQL it uses the {@code search_vector} generated tsvector column and a
 * GIN index ({@code @@ plainto_tsquery}) for fast word/stem-based full-text search;
 * on H2 (the Docker-free default test run, which has no tsvector support) it falls
 * back to a portable case-insensitive {@code LIKE}. The dialect is detected once
 * from the {@link DataSource} metadata.
 */
@Repository
public class RecipeSearchRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final boolean postgres;

    public RecipeSearchRepository(NamedParameterJdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.postgres = detectPostgres(dataSource);
    }

    /** Result of a filtered search: the ordered page of recipe ids plus the overall total. */
    public record Result(List<Long> ids, long totalElements) {
    }

    /**
     * Returns the ids of recipes matching {@code criteria}, ordered by name and
     * limited to the requested page, together with the total match count.
     */
    public Result search(RecipeSearchCriteria criteria, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = buildWhere(criteria, params);

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM recipe r" + where, params, Long.class);
        long totalElements = total == null ? 0L : total;

        if (totalElements == 0) {
            return new Result(List.of(), 0L);
        }

        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());
        List<Long> ids = jdbc.queryForList(
                "SELECT r.id FROM recipe r" + where
                        + " ORDER BY LOWER(r.name), r.id LIMIT :limit OFFSET :offset",
                params, Long.class);

        return new Result(ids, totalElements);
    }

    /** Builds the shared WHERE clause and binds its parameters. Returns "" when unfiltered. */
    private String buildWhere(RecipeSearchCriteria criteria, MapSqlParameterSource params) {
        List<String> predicates = new ArrayList<>();

        criteria.dietaryFilters().forEach((flag, wantTrue) -> {
            String paramName = "diet_" + flag.name();
            if (postgres) {
                // jsonb containment against the denormalized profile column. Every flag key
                // is always present with an explicit boolean, so {"meat":false} containment
                // correctly matches recipes whose meat flag is false — no negation needed.
                predicates.add("r.dietary_profile_attributes @> CAST(:" + paramName + " AS jsonb)");
                params.addValue(paramName, "{\"" + flag.token() + "\":" + wantTrue + "}");
            } else {
                // H2 stores the profile as compact JSON text (explicit booleans, no
                // whitespace), so a deterministic substring match expresses flag == wantTrue.
                predicates.add("r.dietary_profile_attributes LIKE :" + paramName);
                params.addValue(paramName, "%\"" + flag.token() + "\":" + wantTrue + "%");
            }
        });

        if (criteria.servings() != null) {
            predicates.add("r.servings = :servings");
            params.addValue("servings", criteria.servings());
        }

        List<String> include = lowerCasedDistinct(criteria.includeIngredients());
        if (!include.isEmpty()) {
            predicates.add("r.id IN (SELECT ri.recipe_id FROM recipe_ingredient ri"
                    + " JOIN ingredient i ON i.id = ri.ingredient"
                    + " WHERE LOWER(i.name) IN (:includeNames)"
                    + " GROUP BY ri.recipe_id"
                    + " HAVING COUNT(DISTINCT LOWER(i.name)) = :includeCount)");
            params.addValue("includeNames", include);
            params.addValue("includeCount", include.size());
        }

        List<String> exclude = lowerCasedDistinct(criteria.excludeIngredients());
        if (!exclude.isEmpty()) {
            predicates.add("r.id NOT IN (SELECT ri.recipe_id FROM recipe_ingredient ri"
                    + " JOIN ingredient i ON i.id = ri.ingredient"
                    + " WHERE LOWER(i.name) IN (:excludeNames))");
            params.addValue("excludeNames", exclude);
        }

        if (criteria.instructionsContains() != null && !criteria.instructionsContains().isBlank()) {
            if (postgres) {
                predicates.add("r.search_vector @@ plainto_tsquery('english', :instructionsContains)");
                params.addValue("instructionsContains", criteria.instructionsContains());
            } else {
                predicates.add("LOWER(r.instructions) LIKE ('%' || LOWER(:instructionsContains) || '%')");
                params.addValue("instructionsContains", criteria.instructionsContains());
            }
        }

        return predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);
    }

    private static List<String> lowerCasedDistinct(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        return names.stream()
                .map(n -> n.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private static boolean detectPostgres(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            return product != null && product.toLowerCase(Locale.ROOT).contains("postgres");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to detect the database product for recipe search", e);
        }
    }
}
