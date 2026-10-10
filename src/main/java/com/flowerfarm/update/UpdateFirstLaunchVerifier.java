package com.flowerfarm.update;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Runs before the desktop window (highest precedence). On the first launch
 * after an update it runs the health check; a failure closes the embedded
 * database and fails startup, which makes {@link UpdateStartup} roll back.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UpdateFirstLaunchVerifier implements ApplicationRunner {

    private final ObjectProvider<UpdateLaunchGuard> guard;
    private final UpdateHealthCheck healthCheck;
    private final ObjectProvider<UpdateActivityLog> activity;
    private final DataSource dataSource;

    public UpdateFirstLaunchVerifier(ObjectProvider<UpdateLaunchGuard> guard, UpdateHealthCheck healthCheck,
                                     ObjectProvider<UpdateActivityLog> activity, DataSource dataSource) {
        this.guard = guard;
        this.healthCheck = healthCheck;
        this.activity = activity;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        UpdateLaunchGuard launchGuard = guard.getIfAvailable();
        if (launchGuard == null) {
            return;
        }
        if (launchGuard.decision() == UpdateState.Decision.VERIFY_FIRST_LAUNCH) {
            try {
                healthCheck.verify();
            } catch (Exception unhealthy) {
                shutdownEmbeddedDatabase(dataSource);
                throw new UpdateHealthCheckFailedException(
                        "the database check failed: " + unhealthy.getMessage(), unhealthy);
            }
            launchGuard.confirmHealthy();
        }
        launchGuard.flushActivity(activity.getIfAvailable(UpdateActivityLog::none));
    }

    /** H2 keeps the file open (DB_CLOSE_DELAY=-1) until told to shut down. */
    public static void shutdownEmbeddedDatabase(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            if (connection.getMetaData().getURL().startsWith("jdbc:h2:")) {
                statement.execute("SHUTDOWN");
            }
        } catch (Exception alreadyClosed) {
            // Already closed or never opened: nothing holds the records.
        }
    }
}
