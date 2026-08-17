package com.abnamro.recipe.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

import javax.sql.DataSource;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;

import tools.jackson.databind.ObjectMapper;

/**
 * Registers the custom Spring Data JDBC converters that map the
 * {@code DietaryProfile} value object to and from its JSON column.
 *
 * <p>The store representation differs by database, so the converter pair is chosen
 * by dialect: PostgreSQL uses a native {@code jsonb} column (converted via a
 * {@code PGobject}), while other databases such as the H2 test database store the
 * JSON as a plain {@code VARCHAR} string. The dialect is detected once from the
 * {@link DataSource} metadata.
 */
@Configuration
public class JdbcConfig extends AbstractJdbcConfiguration {

    private final ObjectMapper objectMapper;
    private final boolean postgres;

    public JdbcConfig(ObjectMapper objectMapper, DataSource dataSource) {
        this.objectMapper = objectMapper;
        this.postgres = detectPostgres(dataSource);
    }

    @Override
    protected List<?> userConverters() {
        if (postgres) {
            return List.of(
                    new DietaryProfileWritingConverter(objectMapper),
                    new DietaryProfileReadingConverter(objectMapper)
            );
        }
        return List.of(
                new DietaryProfileToJsonStringConverter(objectMapper),
                new JsonStringToDietaryProfileConverter(objectMapper)
        );
    }

    private static boolean detectPostgres(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            return product != null && product.toLowerCase(Locale.ROOT).contains("postgres");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to detect the database product for JDBC converters", e);
        }
    }
}
