package example.org.nightout.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class SchemaVersionFlywayMigrationStrategy implements FlywayMigrationStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchemaVersionFlywayMigrationStrategy.class);
    private static final String STATE_TABLE = "nightout_schema_state";
    private static final String STATE_ID = "app";

    private final AppProperties properties;

    public SchemaVersionFlywayMigrationStrategy(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void migrate(Flyway flyway) {
        String requestedVersion = requireSchemaVersion(properties.getSchemaVersion());
        String storedVersion = readStoredVersion(flyway);

        if (storedVersion == null) {
            LOGGER.info("No CrowdCam schema version marker found; applying migrations and recording version {}.", requestedVersion);
            flyway.migrate();
            writeStoredVersion(flyway, requestedVersion);
            return;
        }

        if (storedVersion.equals(requestedVersion)) {
            LOGGER.info("CrowdCam schema version {} is current; applying any pending migrations.", requestedVersion);
            flyway.migrate();
            return;
        }

        if (!properties.isSchemaResetAllowed()) {
            throw new IllegalStateException("CrowdCam database schema version is " + storedVersion
                    + " but NIGHTOUT_SCHEMA_VERSION is " + requestedVersion
                    + ". Set NIGHTOUT_SCHEMA_RESET_ALLOWED=true to drop and rebuild the configured Flyway schema, "
                    + "or set NIGHTOUT_SCHEMA_VERSION back to " + storedVersion + ".");
        }

        LOGGER.warn("CrowdCam schema version changed from {} to {}; dropping and rebuilding configured Flyway schema.",
                storedVersion, requestedVersion);
        flyway.clean();
        flyway.migrate();
        writeStoredVersion(flyway, requestedVersion);
    }

    private String readStoredVersion(Flyway flyway) {
        DataSource dataSource = flyway.getConfiguration().getDataSource();
        try (Connection connection = dataSource.getConnection()) {
            if (!stateTableExists(connection)) {
                return null;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT schema_version FROM " + STATE_TABLE + " WHERE id = ?")) {
                statement.setString(1, STATE_ID);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getString(1);
                    }
                    return null;
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read CrowdCam schema version marker.", ex);
        }
    }

    private void writeStoredVersion(Flyway flyway, String version) {
        DataSource dataSource = flyway.getConfiguration().getDataSource();
        try (Connection connection = dataSource.getConnection()) {
            createStateTable(connection);
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM " + STATE_TABLE + " WHERE id = ?")) {
                delete.setString(1, STATE_ID);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + STATE_TABLE + " (id, schema_version, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)")) {
                insert.setString(1, STATE_ID);
                insert.setString(2, version);
                insert.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not write CrowdCam schema version marker.", ex);
        }
    }

    private boolean stateTableExists(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = ?")) {
            statement.setString(1, STATE_TABLE);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getLong(1) > 0;
            }
        }
    }

    private void createStateTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS nightout_schema_state (
                        id VARCHAR(64) PRIMARY KEY,
                        schema_version VARCHAR(64) NOT NULL,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
    }

    private String requireSchemaVersion(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("NIGHTOUT_SCHEMA_VERSION must not be blank.");
        }
        return version.trim();
    }
}
