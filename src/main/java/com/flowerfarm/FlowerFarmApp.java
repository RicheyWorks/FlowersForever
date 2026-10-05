package com.flowerfarm;

import com.flowerfarm.automation.mcp.McpDoctorBootstrap;
import com.flowerfarm.automation.mcp.McpDoctorRunner;
import com.flowerfarm.backup.RestoreStartup;
import com.flowerfarm.config.AppDataBootstrap;
import com.flowerfarm.update.UpdateLaunchGuard;
import com.flowerfarm.update.UpdateStartup;
import com.flowerfarm.update.UpdateState;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ConfigurableApplicationContext;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

@SpringBootApplication
public class FlowerFarmApp {

    public static void main(String[] args) {
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
        app.addListeners(event -> {
            if (event instanceof ApplicationEnvironmentPreparedEvent prepared) {
                var environment = prepared.getEnvironment();
                datasource.set(new String[] {environment.getProperty("spring.datasource.url"),
                        environment.getProperty("spring.datasource.username", "sa"),
                        environment.getProperty("spring.datasource.password", "")});
            }
        });
        app.addInitializers(context -> context.getBeanFactory().registerSingleton("updateLaunchGuard", guard));
        UpdateStartup.Result<ConfigurableApplicationContext> result;
        try {
            result = UpdateStartup.start(guard, () -> RestoreStartup.forThisProcess().start(() -> app.run(args),
                            () -> shutdownEmbeddedDatabase(datasource.get()),
                            guard.decision() == UpdateState.Decision.NORMAL),
                    () -> shutdownEmbeddedDatabase(datasource.get()), FlowerFarmApp::tellUser);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
        if (result.started().isEmpty()) {
            System.exit(result.exitCode());
            return null;
        }
        return result.started().get();
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
