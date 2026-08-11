package example.org.nightout.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaVersionFlywayMigrationStrategyTest {

    @Test
    void firstRunCreatesSchemaAndStoresVersion() throws SQLException {
        Flyway flyway = newFlyway();

        strategy("1", false).migrate(flyway);

        assertThat(tableExists(flyway, "clubs")).isTrue();
        assertThat(tableExists(flyway, "spring_session")).isTrue();
        assertThat(storedSchemaVersion(flyway)).isEqualTo("1");
    }

    @Test
    void sameVersionRunsMigrationsWithoutDroppingData() throws SQLException {
        Flyway flyway = newFlyway();
        strategy("1", false).migrate(flyway);
        insertClub(flyway, "HALO", "halo");

        strategy("1", false).migrate(flyway);

        assertThat(rowCount(flyway, "clubs")).isEqualTo(1);
        assertThat(storedSchemaVersion(flyway)).isEqualTo("1");
    }

    @Test
    void changedVersionWithResetAllowedDropsAndRebuildsSchema() throws SQLException {
        Flyway flyway = newFlyway();
        strategy("1", false).migrate(flyway);
        insertClub(flyway, "HALO", "halo");

        strategy("2", true).migrate(flyway);

        assertThat(tableExists(flyway, "clubs")).isTrue();
        assertThat(tableExists(flyway, "spring_session")).isTrue();
        assertThat(rowCount(flyway, "clubs")).isZero();
        assertThat(storedSchemaVersion(flyway)).isEqualTo("2");
    }

    @Test
    void changedVersionWithoutResetAllowedFailsStartupAndPreservesData() throws SQLException {
        Flyway flyway = newFlyway();
        strategy("1", false).migrate(flyway);
        insertClub(flyway, "HALO", "halo");

        assertThatThrownBy(() -> strategy("2", false).migrate(flyway))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NIGHTOUT_SCHEMA_RESET_ALLOWED=true");

        assertThat(rowCount(flyway, "clubs")).isEqualTo(1);
        assertThat(storedSchemaVersion(flyway)).isEqualTo("1");
    }

    private Flyway newFlyway() {
        String databaseName = "nightout_schema_" + UUID.randomUUID().toString().replace("-", "");
        return Flyway.configure()
                .dataSource("jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration")
                .schemas("public")
                .cleanDisabled(false)
                .load();
    }

    private SchemaVersionFlywayMigrationStrategy strategy(String version, boolean resetAllowed) {
        AppProperties properties = new AppProperties();
        properties.setSchemaVersion(version);
        properties.setSchemaResetAllowed(resetAllowed);
        return new SchemaVersionFlywayMigrationStrategy(properties);
    }

    private void insertClub(Flyway flyway, String name, String slug) throws SQLException {
        try (Connection connection = dataSource(flyway).getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO clubs (name, slug, city, area) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, name);
            statement.setString(2, slug);
            statement.setString(3, "Cape Town");
            statement.setString(4, "Cape Town");
            statement.executeUpdate();
        }
    }

    private long rowCount(Flyway flyway, String tableName) throws SQLException {
        try (Connection connection = dataSource(flyway).getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    private String storedSchemaVersion(Flyway flyway) throws SQLException {
        try (Connection connection = dataSource(flyway).getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT schema_version FROM nightout_schema_state WHERE id = ?")) {
            statement.setString(1, "app");
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    private boolean tableExists(Flyway flyway, String tableName) throws SQLException {
        try (Connection connection = dataSource(flyway).getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = ?")) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getLong(1) > 0;
            }
        }
    }

    private DataSource dataSource(Flyway flyway) {
        return flyway.getConfiguration().getDataSource();
    }
}
