package com.flowerfarm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;

@SpringBootApplication
public class FlowerFarmApp {

    public static void main(String[] args) {
        // Preserve the existing --cli flag UX while mapping it to a Spring profile.
        // Users can still run:  java -jar flowerfarm.jar --cli
        // or directly:          java -jar flowerfarm.jar --spring.profiles.active=cli
        SpringApplication app = new SpringApplication(FlowerFarmApp.class);
        if (Arrays.asList(args).contains("--cli")) {
            app.setAdditionalProfiles("cli");
        }
        app.run(args);
    }
}
