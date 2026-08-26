package com.flowerfarm;

import com.flowerfarm.config.AppDataBootstrap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;

@SpringBootApplication
public class FlowerFarmApp {

    public static void main(String[] args) {
        AppDataBootstrap.configure();
        // Preserve the existing --cli flag UX while mapping it to a Spring profile.
        // Users can still run:  java -jar flowerfarm.jar --cli
        // or directly:          java -jar flowerfarm.jar --spring.profiles.active=cli
        SpringApplication app = new SpringApplication(FlowerFarmApp.class);
        // This is a desktop-first application. Spring Boot otherwise defaults
        // java.awt.headless to true when the JVM property is not supplied.
        // Explicit values such as the Docker image's -Djava.awt.headless=true
        // still take precedence.
        app.setHeadless(false);
        boolean releaseSmoke = Arrays.asList(args).contains("--release-smoke");
        if (Arrays.asList(args).contains("--cli")) {
            app.setAdditionalProfiles("cli");
        } else if (releaseSmoke) {
            app.setAdditionalProfiles("release-smoke");
        }
        ConfigurableApplicationContext context = app.run(args);
        if (releaseSmoke) {
            try {
                writeReleaseSmokeMarker();
            } finally {
                context.close();
            }
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
