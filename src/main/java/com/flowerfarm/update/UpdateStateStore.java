package com.flowerfarm.update;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

/** Reads and atomically writes {@code <updates>/update-state.json}. */
public final class UpdateStateStore {

    public static final String FILE = "update-state.json";

    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

    public UpdateStateStore(Path updatesDirectory) {
        this.directory = updatesDirectory;
    }

    /** The saved state, or an idle state when none exists or it is unreadable. */
    public UpdateState load() {
        Path file = directory.resolve(FILE);
        if (!Files.isRegularFile(file)) {
            return new UpdateState();
        }
        try {
            UpdateState state = mapper.readValue(file.toFile(), UpdateState.class);
            if (state.phase == null) {
                state.phase = UpdateState.Phase.IDLE;
            }
            if (state.failedVersions == null) {
                state.failedVersions = new java.util.LinkedHashSet<>();
            }
            return state;
        } catch (IOException unreadable) {
            return new UpdateState();
        }
    }

    public void save(UpdateState state) {
        try {
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, ".update-state-", ".tmp");
            mapper.writeValue(temporary.toFile(), state);
            Files.move(temporary, directory.resolve(FILE),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot save update progress in " + directory, failure);
        }
    }

    public Set<String> failedVersions() {
        return Set.copyOf(load().failedVersions);
    }

    public Path directory() {
        return directory;
    }
}
