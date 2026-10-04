package com.flowerfarm.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Release version such as {@code 1.2.1}. A trailing qualifier such as
 * {@code -SNAPSHOT} sorts before the same numeric release, so a developer
 * build of 1.2.0 is older than the 1.2.0 release.
 */
public final class AppVersion implements Comparable<AppVersion> {

    private static final String DEVELOPMENT = "0.0.0-dev";

    private final String text;
    private final List<Integer> numbers;
    private final String qualifier;

    private AppVersion(String text, List<Integer> numbers, String qualifier) {
        this.text = text;
        this.numbers = List.copyOf(numbers);
        this.qualifier = qualifier;
    }

    /** Parses {@code major.minor.patch[-qualifier]}; one to four numeric parts are accepted. */
    public static AppVersion parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Version is blank");
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            trimmed = trimmed.substring(1);
        }
        String qualifier = "";
        int dash = trimmed.indexOf('-');
        String numeric = trimmed;
        if (dash >= 0) {
            qualifier = trimmed.substring(dash + 1).toUpperCase(Locale.ROOT);
            numeric = trimmed.substring(0, dash);
            if (qualifier.isBlank()) {
                throw new IllegalArgumentException("Version qualifier is blank: " + value);
            }
        }
        String[] parts = numeric.split("\\.", -1);
        if (parts.length < 1 || parts.length > 4) {
            throw new IllegalArgumentException("Version must have one to four numeric parts: " + value);
        }
        List<Integer> numbers = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 9 || !part.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("Version part is not a number: " + value);
            }
            numbers.add(Integer.parseInt(part));
        }
        while (numbers.size() < 3) {
            numbers.add(0);
        }
        return new AppVersion(trimmed, numbers, qualifier);
    }

    /** The running application's version, from the JAR manifest or an explicit override. */
    public static AppVersion current(String override) {
        String explicit = override == null || override.isBlank() ? null : override.trim();
        if (explicit == null) {
            Package pkg = AppVersion.class.getPackage();
            explicit = pkg == null ? null : pkg.getImplementationVersion();
        }
        if (explicit == null || explicit.isBlank()) {
            explicit = DEVELOPMENT;
        }
        try {
            return parse(explicit);
        } catch (IllegalArgumentException invalid) {
            return parse(DEVELOPMENT);
        }
    }

    public boolean isRelease() {
        return qualifier.isEmpty();
    }

    public boolean isNewerThan(AppVersion other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(AppVersion other) {
        int length = Math.max(numbers.size(), other.numbers.size());
        for (int index = 0; index < length; index++) {
            int left = index < numbers.size() ? numbers.get(index) : 0;
            int right = index < other.numbers.size() ? other.numbers.get(index) : 0;
            if (left != right) {
                return Integer.compare(left, right);
            }
        }
        if (qualifier.isEmpty() != other.qualifier.isEmpty()) {
            return qualifier.isEmpty() ? 1 : -1;
        }
        return qualifier.compareTo(other.qualifier);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AppVersion version && compareTo(version) == 0;
    }

    @Override
    public int hashCode() {
        List<Integer> trimmed = new ArrayList<>(numbers);
        while (trimmed.size() > 1 && trimmed.get(trimmed.size() - 1) == 0) {
            trimmed.remove(trimmed.size() - 1);
        }
        return Objects.hash(trimmed, qualifier);
    }

    @Override
    public String toString() {
        return text;
    }
}
