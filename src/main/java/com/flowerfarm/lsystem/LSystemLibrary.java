package com.flowerfarm.lsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Saves and loads custom L-System rulesets as JSON under a library directory
 * (default {@code data/lsystems}).
 */
public class LSystemLibrary {

    private static final Logger log = LoggerFactory.getLogger(LSystemLibrary.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path directory;

    public LSystemLibrary() {
        this(Path.of("data", "lsystems"));
    }

    public LSystemLibrary(Path directory) {
        this.directory = directory;
    }

    public Path getDirectory() {
        return directory;
    }

    public Path save(LSystemDefinition definition) throws IOException {
        if (definition == null || definition.getName() == null || definition.getName().isBlank()) {
            throw new IllegalArgumentException("Ruleset name is required.");
        }
        Files.createDirectories(directory);
        String fileName = sanitize(definition.getName()) + ".json";
        Path file = directory.resolve(fileName);
        MAPPER.writeValue(file.toFile(), definition);
        log.info("Saved L-System ruleset '{}' → {}", definition.getName(), file);
        return file;
    }

    public LSystemDefinition load(Path file) throws IOException {
        return MAPPER.readValue(file.toFile(), LSystemDefinition.class);
    }

    public List<Path> listFiles() throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        }
    }

    public List<LSystemDefinition> loadAll() {
        List<LSystemDefinition> out = new ArrayList<>();
        try {
            for (Path p : listFiles()) {
                try {
                    out.add(load(p));
                } catch (IOException e) {
                    log.warn("Skip unreadable ruleset {}: {}", p, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Cannot list L-System library: {}", e.getMessage());
        }
        return out;
    }

    private static String sanitize(String name) {
        return name.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
    }
}
