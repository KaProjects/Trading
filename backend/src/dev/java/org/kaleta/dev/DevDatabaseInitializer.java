package org.kaleta.dev;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Builds the dev database once, when the app starts.
 *
 * It used to be built from an INIT on the JDBC URL, which H2 runs for every connection: the
 * moment the pool opened a second one the tables were dropped and rebuilt underneath whatever
 * was using the first, which is where the stray "Table not found" failures came from. Running
 * the scripts here instead leaves the data alone for the rest of the run, so what the app writes
 * stays written.
 */
@ApplicationScoped
@IfBuildProperty(name = "dev.seed.images", stringValue = "true")
public class DevDatabaseInitializer
{
    @Inject
    DataSource dataSource;

    void onStart(@Observes @Priority(1) StartupEvent event)
    {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            if (alreadyBuilt(statement)) {
                Log.info("Dev database is already in place; leaving it as it is");
                return;
            }

            statement.execute("RUNSCRIPT FROM 'sql/createTables.sql'");
            statement.execute("RUNSCRIPT FROM 'classpath:createDevDb.sql'");
            Log.info("Dev database created from sql/createTables.sql and createDevDb.sql");
        } catch (SQLException exception) {
            throw new IllegalStateException("the dev database could not be created", exception);
        }
    }

    private static boolean alreadyBuilt(Statement statement) throws SQLException
    {
        try (ResultSet tables = statement.executeQuery(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = 'COMPANY'")) {
            return tables.next() && tables.getInt(1) > 0;
        }
    }
}
