package com.flowerfarm;

import com.flowerfarm.automation.mcp.McpDoctorBootstrap;
import com.flowerfarm.automation.mcp.McpDoctorRunner;
import com.flowerfarm.backup.RestoreStartup;
import com.flowerfarm.config.AppDataBootstrap;
import com.flowerfarm.update.HeadlessUpdateCommand;
import com.flowerfarm.update.HeadlessUpdateLog;
import com.flowerfarm.update.UpdateLaunchGuard;
import com.flowerfarm.update.UpdateSettings;
import com.flowerfarm.update.UpdateStartup;
import com.flowerfarm.update.UpdateState;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@SpringBootApplication
public class FlowerFarmApp {

    public static void main(String[] args) {
        if (HeadlessUpdateCommand.requested(args)) {
            // Test-only release check; never falls through to the desktop window.
            System.exit(runHeadlessUpdateCheck(args));
            return;
        }
        boolean mcpDoctor = Arrays.asList(args).contains("--mcp-doctor");
        if (mcpDoctor) {
            McpDoctorBootstrap.prepare();
        }
        AppDataBootstrap.configure();
        // Preserve the existing --cli flag UX while mapping it to a Spring profile.
        // Users can still run:  java -jar flowerfarm.jar --cli
        // or directly:          java -jar flowerfarm.jar --spring.profiles.active=cli
        SpringApplication app = new SpringApplication(FlowerFarmApp.class);
        // This is a desktop-first application. Spring Boot otherwise defaults
        // java.awt.headless to true when the JVM property is not supplied.
        // Explicit values such as the Docker image's -Djava.awt.headless=true
        // still take precedence.
        boolean mcp = mcpDoctor || Arrays.asList(args).contains("--mcp");
        app.setHeadless(mcp);
        boolean releaseSmoke = Arrays.asList(args).contains("--release-smoke");
        if (mcp) {
            if (mcpDoctor) {
                app.setAdditionalProfiles("mcp", "mcp-doctor");
            } else {
                app.setAdditionalProfiles("mcp");
            }
            app.setWebApplicationType(WebApplicationType.NONE);
            app.setBannerMode(Banner.Mode.OFF);
        } else if (Arrays.asList(args).contains("--cli")) {
            app.setAdditionalProfiles("cli");
        } else if (releaseSmoke) {
            app.setAdditionalProfiles("release-smoke");
        }
        boolean desktop = !mcp && !releaseSmoke && !Arrays.asList(args).contains("--cli");
        ConfigurableApplicationContext context;
        if (desktop) {
            context = startDesktopWithUpdateGuard(app, args);
            if (context == null) {
                return;
            }
        } else {
            context = app.run(args);
        }
        if (mcpDoctor) {
            int exitCode = context.getBean(McpDoctorRunner.class).getExitCode();
            context.close();
            if (exitCode != 0) {
                System.exit(exitCode);
            }
        } else if (mcp) {
            context.close();
        } else if (releaseSmoke) {
            try {
                writeReleaseSmokeMarker();
            } finally {
                context.close();
            }
        }
    }

    /**
     * The first launch after an automatic update must pass its database health
     * check; if Spring cannot start, the pre-update backup is restored and the
     * previous version reinstalled (see docs/AUTO_UPDATES.md).
     */
    private static ConfigurableApplicationContext startDesktopWithUpdateGuard(SpringApplication app, String[] args) {
        UpdateLaunchGuard guard = UpdateStartup.guardForThisProcess();
        AtomicReference<String[]> datasource = new AtomicReference<>(new String[3]);
        captureDatasource(app, datasource);
        app.addInitializers(context -> context.getBeanFactory().registerSingleton("updateLaunchGuard", guard));
        UpdateStartup.Result<ConfigurableApplicationContext> result;
        try {
            result = UpdateStartup.start(guard, () -> RestoreStartup.forThisProcess().start(() -> app.run(args),
                            () -> shutdownEmbeddedDatabase(datasource.get()),
                            guard.decision() == UpdateState.Decision.NORMAL),
                    () -> shutdownEmbeddedDatabase(datasource.get()), FlowerFarmApp::tellUser);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (com.flowerfarm.backup.DamagedRecordsException damaged) {
            tellUser(damaged.getMessage());
            System.exit(2);
            return null;
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
        if (result.started().isEmpty()) {
            System.exit(result.exitCode());
            return null;
        }
        return result.started().get();
    }

    private static void captureDatasource(SpringApplication app, AtomicReference<String[]> datasource) {
        app.addListeners(event -> {
            if (event instanceof ApplicationEnvironmentPreparedEvent prepared) {
                var environment = prepared.getEnvironment();
                datasource.set(new String[] {environment.getProperty("spring.datasource.url"),
                        environment.getProperty("spring.datasource.username", "sa"),
                        environment.getProperty("spring.datasource.password", "")});
            }
        });
    }

    /**
     * The test-only headless update path for the real-install release check
     * (docs/AUTO_UPDATES.md). Refuses before touching any folder unless
     * explicitly enabled; returns the process exit code.
     */
    static int runHeadlessUpdateCheck(String[] args) {
        boolean enabled = HeadlessUpdateCommand.gateOpen(System::getProperty, System::getenv);
        HeadlessUpdateCommand.Request request;
        try {
            request = HeadlessUpdateCommand.parse(args);
        } catch (IllegalArgumentException invalid) {
            System.err.println("Headless update check refused: " + invalid.getMessage());
            return HeadlessUpdateCommand.Exit.REFUSED.code();
        }
        if (!enabled) {
            return new HeadlessUpdateLog(request.log(), System.err, Clock.systemUTC(), request.mode().name())
                    .finish(HeadlessUpdateCommand.Exit.REFUSED, HeadlessUpdateCommand.notEnabledMessage());
        }
        AppDataBootstrap.configure();
        UpdateSettings settings = UpdateSettings.fromSystemProperties();
        HeadlessUpdateLog log = new HeadlessUpdateLog(
                Optional.of(request.log().orElse(settings.updatesDirectory().resolve(HeadlessUpdateCommand.DEFAULT_LOG))),
                System.out, Clock.systemUTC(), request.mode().name());
        HeadlessUpdateCommand command = HeadlessUpdateCommand.forThisProcess(true, settings, log);
        if (request.mode() == HeadlessUpdateCommand.Mode.APPLY) {
            return command.apply(request);
        }
        SpringApplication app = new SpringApplication(FlowerFarmApp.class);
        app.setHeadless(true);
        // The packaged-runtime acceptance profile: full persistence and services, no web server, no window.
        app.setAdditionalProfiles("release-smoke");
        UpdateLaunchGuard guard = command.guard();
        AtomicReference<String[]> datasource = new AtomicReference<>(new String[3]);
        captureDatasource(app, datasource);
        app.addInitializers(context -> context.getBeanFactory().registerSingleton("updateLaunchGuard", guard));
        return command.verifyLaunch(guard,
                () -> RestoreStartup.forThisProcess().start(() -> app.run(),
                        () -> shutdownEmbeddedDatabase(datasource.get()),
                        guard.decision() == UpdateState.Decision.NORMAL),
                () -> shutdownEmbeddedDatabase(datasource.get()),
                context -> HeadlessUpdateCommand.recordCounts(context.getBean(DataSource.class)),
                ConfigurableApplicationContext::close);
    }

    private static void shutdownEmbeddedDatabase(String[] datasource) {
        String url = datasource[0];
        if (url == null || !url.startsWith("jdbc:h2:")) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(url + ";IFEXISTS=TRUE",
                datasource[1], datasource[2]);
             Statement statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        } catch (java.sql.SQLException | RuntimeException alreadyClosed) {
            // Nothing holds the records open.
        }
    }

    private static void tellUser(String message) {
        System.err.println(message);
        if (!GraphicsEnvironment.isHeadless()) {
            javax.swing.JOptionPane.showMessageDialog(null, message, "FlowersForever update",
                    javax.swing.JOptionPane.WARNING_MESSAGE);
        }
    }

    private static void writeReleaseSmokeMarker() {
        Path marker = Path.of(System.getProperty("flowerfarm.data-dir"), "release-smoke.ok");
        try {
            Files.writeString(marker, "Packaged runtime started successfully at " + Instant.now() + "\n");
        } catch (IOException exception) {
            throw new IllegalStateException("Packaged runtime started but could not write its smoke marker.",
                    exception);
        }
    }
}
