package com.flowerfarm.automation.farm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Maps arbitrary spreadsheet or provider records into a discovered farm-data schema. */
@Component
public class FarmDataMappingService {

    private static final int MAX_SOURCE_KEY_LENGTH = 240;
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("M/d/uuuu", Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("M/d/uu", Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("M-d-uuuu", Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT));
    private static final Map<String, List<String>> ALIASES = aliases();

    private final ObjectReader jsonCellReader;

    public FarmDataMappingService(ObjectMapper mapper) {
        this.jsonCellReader = mapper.copy().reader()
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    public MappingSuggestion suggest(
            Map<String, Object> schema, Collection<String> sourceFields) {
        Map<String, Map<String, Object>> properties = properties(schema);
        List<String> sources = normalizedSourceFields(sourceFields);
        Set<String> used = new HashSet<>();
        Map<String, String> mapping = new LinkedHashMap<>();
        List<FieldMatch> matches = new ArrayList<>();
        Map<String, List<String>> ambiguous = new LinkedHashMap<>();

        for (String canonical : orderedCanonicalFields(schema, properties.keySet())) {
            List<ScoredField> candidates = sources.stream()
                    .filter(source -> !used.contains(source))
                    .map(source -> new ScoredField(source, score(canonical, source)))
                    .filter(candidate -> candidate.score() >= 0.70)
                    .sorted(Comparator.comparingDouble(ScoredField::score).reversed()
                            .thenComparing(ScoredField::field))
                    .toList();
            if (candidates.isEmpty()) {
                continue;
            }
            double bestScore = candidates.get(0).score();
            List<ScoredField> tied = candidates.stream()
                    .filter(candidate -> Double.compare(candidate.score(), bestScore) == 0)
                    .toList();
            if (tied.size() > 1) {
                ambiguous.put(canonical, tied.stream().map(ScoredField::field).toList());
                continue;
            }
            ScoredField best = tied.get(0);
            mapping.put(canonical, best.field());
            used.add(best.field());
            matches.add(new FieldMatch(
                    canonical, best.field(), best.score(), reason(best.score())));
        }

        List<String> requiredUnmapped = required(schema).stream()
                .filter(field -> !mapping.containsKey(field))
                .toList();
        List<String> unmappedSource = sources.stream()
                .filter(field -> !used.contains(field))
                .toList();
        return new MappingSuggestion(
                mapping, matches, ambiguous, requiredUnmapped, unmappedSource);
    }

    public MappingPreview preview(
            String recordType, Map<String, Object> schema,
            Map<?, ?> suppliedMapping, Collection<?> sourceRecords,
            Map<?, ?> suppliedDefaults, Collection<String> sourceKeyFields) {
        List<Map<String, Object>> rows = sourceRows(sourceRecords);
        List<String> sourceFields = sourceFields(rows);
        MappingSuggestion suggestion = suggest(schema, sourceFields);
        Map<String, String> mapping = new LinkedHashMap<>(suggestion.fieldMapping());
        Map<String, String> explicitMapping = mapping(suppliedMapping, schema, rows);
        mapping.putAll(explicitMapping);
        Map<String, Object> defaults = defaults(suppliedDefaults, schema);
        List<String> keyFields = normalizedSourceFields(sourceKeyFields);
        if (!keyFields.isEmpty() && explicitMapping.containsKey("sourceKey")) {
            throw new IllegalArgumentException(
                    "source-key-fields cannot be combined with a sourceKey field mapping");
        }
        if (!keyFields.isEmpty()) {
            mapping.remove("sourceKey");
        }
        validateSourcePaths(keyFields, rows, "source-key-fields");

        List<Map<String, Object>> mappedRows = new ArrayList<>();
        List<MappingIssue> issues = new ArrayList<>();
        Map<String, Map<String, Object>> properties = properties(schema);
        Set<String> required = Set.copyOf(required(schema));
        for (int index = 0; index < rows.size(); index++) {
            Map<String, Object> target = new LinkedHashMap<>();
            mapRow(index + 1, rows.get(index), mapping, defaults,
                    keyFields, properties, required, target, issues);
            mappedRows.add(Map.copyOf(target));
        }
        validateSourceKeys(mappedRows, issues);

        Set<Integer> invalidRows = new HashSet<>();
        issues.forEach(issue -> invalidRows.add(issue.row()));
        List<String> usedTopLevel = mapping.values().stream()
                .map(FarmDataMappingService::topLevel)
                .toList();
        List<String> unmappedSource = sourceFields.stream()
                .filter(field -> !usedTopLevel.contains(field))
                .filter(field -> !keyFields.contains(field))
                .toList();
        List<String> unmappedCanonical = properties.keySet().stream()
                .filter(field -> !mapping.containsKey(field))
                .filter(field -> !defaults.containsKey(field))
                .filter(field -> !field.equals("sourceKey") || keyFields.isEmpty())
                .sorted()
                .toList();
        List<String> requiredUnmapped = required.stream()
                .filter(field -> !mapping.containsKey(field))
                .filter(field -> !defaults.containsKey(field))
                .sorted()
                .toList();
        boolean sourceKeysPresent = mappedRows.stream().allMatch(row ->
                row.containsKey("sourceKey") && !String.valueOf(row.get("sourceKey")).isBlank())
                && issues.stream().noneMatch(issue -> issue.field().equals("sourceKey"));
        return new MappingPreview(
                recordType, Map.copyOf(mapping), Map.copyOf(defaults), keyFields,
                mappedRows, issues, unmappedSource, unmappedCanonical, requiredUnmapped,
                rows.size(), rows.size() - invalidRows.size(), invalidRows.size(),
                issues.isEmpty(), sourceKeysPresent);
    }

    private static void validateSourceKeys(
            List<Map<String, Object>> rows, List<MappingIssue> issues) {
        Map<String, Integer> firstRows = new LinkedHashMap<>();
        for (int index = 0; index < rows.size(); index++) {
            Object raw = rows.get(index).get("sourceKey");
            if (raw == null) {
                continue;
            }
            String key = String.valueOf(raw);
            int rowNumber = index + 1;
            if (key.length() > MAX_SOURCE_KEY_LENGTH) {
                issues.add(new MappingIssue(rowNumber, "sourceKey",
                        "Source key cannot exceed " + MAX_SOURCE_KEY_LENGTH + " characters"));
            }
            Integer firstRow = firstRows.putIfAbsent(key, rowNumber);
            if (firstRow != null) {
                issues.add(new MappingIssue(rowNumber, "sourceKey",
                        "Source key duplicates row " + firstRow
                                + "; every source row must have a unique key"));
            }
        }
    }

    private void mapRow(
            int rowNumber, Map<String, Object> source, Map<String, String> mapping,
            Map<String, Object> defaults, List<String> sourceKeyFields,
            Map<String, Map<String, Object>> properties, Set<String> required,
            Map<String, Object> target, List<MappingIssue> issues) {
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            PathValue raw = readPath(source, entry.getValue());
            if (raw.exists() && !blank(raw.value())) {
                putCoerced(rowNumber, entry.getKey(), raw.value(), properties, target, issues);
            }
        }
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            if (!target.containsKey(entry.getKey()) && !blank(entry.getValue())) {
                putCoerced(rowNumber, entry.getKey(), entry.getValue(),
                        properties, target, issues);
            }
        }
        if (!sourceKeyFields.isEmpty()) {
            try {
                target.put("sourceKey", sourceKey(source, sourceKeyFields));
            } catch (IllegalArgumentException exception) {
                issues.add(new MappingIssue(rowNumber, "sourceKey", exception.getMessage()));
            }
        }
        for (String field : required) {
            if (!target.containsKey(field) || blank(target.get(field))) {
                issues.add(new MappingIssue(
                        rowNumber, field, "Required canonical field is missing"));
            }
        }
    }

    private void putCoerced(
            int rowNumber, String field, Object raw,
            Map<String, Map<String, Object>> properties,
            Map<String, Object> target, List<MappingIssue> issues) {
        try {
            target.put(field, coerce(raw, properties.get(field), field));
        } catch (IllegalArgumentException exception) {
            issues.add(new MappingIssue(rowNumber, field, exception.getMessage()));
        }
    }

    private Object coerce(Object raw, Map<String, Object> schema, String field) {
        String type = String.valueOf(schema.get("type"));
        return switch (type) {
            case "string" -> stringValue(raw, schema, field);
            case "integer" -> integerValue(raw, field);
            case "number" -> numberValue(raw, field);
            case "boolean" -> booleanValue(raw, field);
            case "array" -> arrayValue(raw, schema, field);
            case "object" -> objectValue(raw, schema, field);
            default -> throw new IllegalArgumentException(
                    "Unsupported canonical type '" + type + "'");
        };
    }

    private Object stringValue(Object raw, Map<String, Object> schema, String field) {
        if ("date".equals(schema.get("format"))) {
            return dateValue(raw, field).toString();
        }
        String value = scalarText(raw, field);
        Object options = schema.get("enum");
        if (options instanceof Collection<?> values) {
            return values.stream()
                    .map(String::valueOf)
                    .filter(option -> option.equalsIgnoreCase(value))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Value '" + value + "' is not one of " + values));
        }
        return value;
    }

    private static String scalarText(Object raw, String field) {
        if (!(raw instanceof String || raw instanceof Number || raw instanceof Boolean)) {
            throw new IllegalArgumentException(
                    "Field '" + field + "' must contain a scalar value");
        }
        return String.valueOf(raw).trim();
    }

    private LocalDate dateValue(Object raw, String field) {
        if (raw instanceof Number number) {
            try {
                long serial = new BigDecimal(number.toString()).longValueExact();
                if (serial > 0 && serial < 3_000_000) {
                    return LocalDate.of(1899, 12, 30).plusDays(serial);
                }
            } catch (ArithmeticException | NumberFormatException ignored) {
                // Fractional or nonfinite serials must not round into calendar dates.
            }
        }
        String value = String.valueOf(raw).trim();
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next documented spreadsheet date format.
            }
        }
        throw new IllegalArgumentException(
                "Field '" + field + "' must be an ISO or common spreadsheet date");
    }

    private static long integerValue(Object raw, String field) {
        BigDecimal number = decimal(raw, field);
        try {
            return number.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Field '" + field + "' must be a whole number", exception);
        }
    }

    private static double numberValue(Object raw, String field) {
        double value = decimal(raw, field).doubleValue();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Field '" + field + "' must be finite");
        }
        return value;
    }

    private static BigDecimal decimal(Object raw, String field) {
        if (raw instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        String value = String.valueOf(raw).trim()
                .replace(",", "")
                .replace("$", "")
                .replace("%", "");
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Field '" + field + "' must be numeric", exception);
        }
    }

    private static boolean booleanValue(Object raw, String field) {
        if (raw instanceof Boolean value) {
            return value;
        }
        String value = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "true", "yes", "y", "1", "active" -> true;
            case "false", "no", "n", "0", "inactive" -> false;
            default -> throw new IllegalArgumentException(
                    "Field '" + field + "' must be true/false or yes/no");
        };
    }

    private List<Object> arrayValue(
            Object raw, Map<String, Object> schema, String field) {
        Object value = raw;
        Map<String, Object> itemSchema = schemaMap(schema.get("items"), field + " items");
        if (raw instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.startsWith("[")) {
                value = parseJson(trimmed, List.class, field);
            } else if ("string".equals(itemSchema.get("type"))) {
                value = List.of(trimmed.split("\\s*[,;]\\s*"));
            }
        }
        if (!(value instanceof Collection<?> collection)) {
            throw new IllegalArgumentException("Field '" + field + "' must be an array");
        }
        List<Object> converted = new ArrayList<>();
        int index = 0;
        for (Object item : collection) {
            converted.add(coerce(item, itemSchema, field + "[" + index + "]"));
            index++;
        }
        return List.copyOf(converted);
    }

    private Map<String, Object> objectValue(
            Object raw, Map<String, Object> schema, String field) {
        Object value = raw instanceof String text
                ? parseJson(text, Map.class, field) : raw;
        if (!(value instanceof Map<?, ?> source)) {
            throw new IllegalArgumentException("Field '" + field + "' must be an object");
        }
        Map<String, Map<String, Object>> childProperties = properties(schema);
        Set<String> childRequired = Set.copyOf(required(schema));
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String child = String.valueOf(entry.getKey());
            if (!childProperties.containsKey(child)) {
                throw new IllegalArgumentException(
                        "Field '" + field + "' contains unknown property '" + child + "'");
            }
            if (!blank(entry.getValue())) {
                result.put(child, coerce(
                        entry.getValue(), childProperties.get(child), field + "." + child));
            }
        }
        for (String child : childRequired) {
            if (!result.containsKey(child) || blank(result.get(child))) {
                throw new IllegalArgumentException(
                        "Field '" + field + "' requires property '" + child + "'");
            }
        }
        return Map.copyOf(result);
    }

    private Object parseJson(String value, Class<?> type, String field) {
        try {
            return jsonCellReader.forType(type).readValue(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Field '" + field + "' contains invalid JSON", exception);
        }
    }

    private static Map<String, String> mapping(
            Map<?, ?> supplied, Map<String, Object> schema,
            List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> properties = properties(schema);
        Map<String, String> result = new LinkedHashMap<>();
        if (supplied == null) {
            return result;
        }
        for (Map.Entry<?, ?> entry : supplied.entrySet()) {
            String canonical = String.valueOf(entry.getKey()).trim();
            String source = entry.getValue() == null
                    ? "" : String.valueOf(entry.getValue()).trim();
            if (!properties.containsKey(canonical)) {
                throw new IllegalArgumentException(
                        "Unknown canonical field '" + canonical + "' in field-mapping");
            }
            if (source.isBlank()) {
                throw new IllegalArgumentException(
                        "field-mapping source for '" + canonical + "' is blank");
            }
            result.put(canonical, source);
        }
        validateSourcePaths(result.values(), rows, "field-mapping");
        return result;
    }

    private Map<String, Object> defaults(
            Map<?, ?> supplied, Map<String, Object> schema) {
        Map<String, Map<String, Object>> properties = properties(schema);
        Map<String, Object> result = new LinkedHashMap<>();
        if (supplied == null) {
            return result;
        }
        for (Map.Entry<?, ?> entry : supplied.entrySet()) {
            String canonical = String.valueOf(entry.getKey()).trim();
            if (!properties.containsKey(canonical)) {
                throw new IllegalArgumentException(
                        "Unknown canonical field '" + canonical + "' in defaults");
            }
            if (!blank(entry.getValue())) {
                result.put(canonical,
                        coerce(entry.getValue(), properties.get(canonical), canonical));
            }
        }
        return result;
    }

    private static List<Map<String, Object>> sourceRows(Collection<?> sourceRecords) {
        if (sourceRecords == null || sourceRecords.isEmpty()) {
            throw new IllegalArgumentException("source-records must contain at least one object");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        int index = 0;
        for (Object record : sourceRecords) {
            if (!(record instanceof Map<?, ?> values)) {
                throw new IllegalArgumentException(
                        "Source record " + (index + 1) + " must be an object");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            values.forEach((key, value) -> row.put(String.valueOf(key), value));
            rows.add(Collections.unmodifiableMap(row));
            index++;
        }
        return List.copyOf(rows);
    }

    private static List<String> sourceFields(List<Map<String, Object>> rows) {
        Set<String> fields = new LinkedHashSet<>();
        rows.forEach(row -> fields.addAll(row.keySet()));
        return List.copyOf(fields);
    }

    private static void validateSourcePaths(
            Collection<String> paths, List<Map<String, Object>> rows, String label) {
        for (String path : paths) {
            if (rows.stream().noneMatch(row -> readPath(row, path).exists())) {
                throw new IllegalArgumentException(
                        label + " references unavailable source field '" + path + "'");
            }
        }
    }

    private static PathValue readPath(Map<String, Object> source, String path) {
        if (source.containsKey(path)) {
            return new PathValue(true, source.get(path));
        }
        Object current = source;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> values)) {
                return new PathValue(false, null);
            }
            if (values.containsKey(part)) {
                current = values.get(part);
                continue;
            }
            List<?> matches = values.keySet().stream()
                    .filter(candidate -> normalized(String.valueOf(candidate))
                            .equals(normalized(part)))
                    .toList();
            if (matches.size() > 1) {
                throw new IllegalArgumentException(
                        "Source path '" + path + "' is ambiguous at '" + part
                                + "'; use the exact source field name");
            }
            if (matches.isEmpty()) {
                return new PathValue(false, null);
            }
            current = values.get(matches.get(0));
        }
        return new PathValue(true, current);
    }

    private static String sourceKey(
            Map<String, Object> source, List<String> fields) {
        List<String> values = new ArrayList<>();
        for (String field : fields) {
            PathValue value = readPath(source, field);
            if (!value.exists() || blank(value.value())) {
                throw new IllegalArgumentException(
                        "Source key field '" + field + "' is missing or blank");
            }
            String text = scalarText(value.value(), field);
            values.add(fields.size() == 1 ? text
                    : field.length() + ":" + field + "=" + text.length() + ":" + text);
        }
        String key = (fields.size() == 1 ? "" : "composite-") + String.join("|", values);
        return key.length() <= MAX_SOURCE_KEY_LENGTH ? key : "sha256-" + sha256(key);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> properties(Map<String, Object> schema) {
        Object value = schema.get("properties");
        if (!(value instanceof Map<?, ?> properties)) {
            throw new IllegalArgumentException("Canonical schema has no properties");
        }
        return (Map<String, Map<String, Object>>) properties;
    }

    private static Map<String, Object> schemaMap(Object value, String label) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("Canonical schema has no " + label);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, nested) -> result.put(String.valueOf(key), nested));
        return result;
    }

    private static List<String> required(Map<String, Object> schema) {
        Object value = schema.get("required");
        if (!(value instanceof Collection<?> fields)) {
            return List.of();
        }
        return fields.stream().map(String::valueOf).toList();
    }

    private static List<String> orderedCanonicalFields(
            Map<String, Object> schema, Collection<String> fields) {
        Set<String> required = new LinkedHashSet<>(required(schema));
        List<String> ordered = new ArrayList<>(required);
        fields.stream().filter(field -> !required.contains(field)).sorted().forEach(ordered::add);
        return ordered;
    }

    private static List<String> normalizedSourceFields(Collection<String> fields) {
        if (fields == null) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String field : fields) {
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("Source field names cannot be blank");
            }
            if (!unique.add(field.trim())) {
                throw new IllegalArgumentException(
                        "Duplicate source field '" + field.trim() + "'");
            }
        }
        return List.copyOf(unique);
    }

    private static double score(String canonical, String source) {
        String expected = normalized(canonical);
        String actual = normalized(source);
        if (expected.equals(actual)) {
            return 1.0;
        }
        if (ALIASES.getOrDefault(canonical, List.of()).stream()
                .map(FarmDataMappingService::normalized)
                .anyMatch(actual::equals)) {
            return 0.92;
        }
        if (expected.length() >= 4 && actual.length() >= 4
                && (expected.contains(actual) || actual.contains(expected))) {
            return 0.70;
        }
        return 0;
    }

    private static String reason(double score) {
        if (score == 1.0) {
            return "normalized exact match";
        }
        if (score >= 0.90) {
            return "known farm-data alias";
        }
        return "partial field-name match";
    }

    private static String normalized(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    private static String topLevel(String path) {
        int separator = path.indexOf('.');
        return separator < 0 ? path : path.substring(0, separator);
    }

    private static boolean blank(Object value) {
        return value == null || value instanceof String text && text.isBlank();
    }

    private static Map<String, List<String>> aliases() {
        return Map.ofEntries(
                Map.entry("sourceKey", List.of(
                        "id", "record id", "row id", "external id", "legacy id", "uuid", "key")),
                Map.entry("name", List.of(
                        "item", "item name", "product", "product name", "title", "full name")),
                Map.entry("cropName", List.of(
                        "crop", "crop name", "flower", "flower name", "product name")),
                Map.entry("category", List.of("type", "item type", "product type", "class")),
                Map.entry("price", List.of("retail price", "selling price", "unit price")),
                Map.entry("salePrice", List.of("sale price", "selling price", "retail price")),
                Map.entry("unitPrice", List.of("unit price", "price each", "rate")),
                Map.entry("cost", List.of("unit cost", "wholesale cost", "purchase cost")),
                Map.entry("packCost", List.of("pack cost", "case cost", "purchase cost")),
                Map.entry("quantity", List.of(
                        "qty", "count", "on hand", "quantity on hand", "stock", "inventory")),
                Map.entry("unit", List.of("uom", "measure", "unit of measure")),
                Map.entry("active", List.of("available", "enabled", "is active")),
                Map.entry("tags", List.of("labels", "keywords", "tag list")),
                Map.entry("notes", List.of("note", "description", "comments", "memo")),
                Map.entry("contactName", List.of("contact", "contact name", "primary contact")),
                Map.entry("customerName", List.of("customer", "customer name", "client")),
                Map.entry("customerId", List.of("customer id", "client id", "account id")),
                Map.entry("customerType", List.of("customer type", "account type", "channel")),
                Map.entry("phone", List.of("telephone", "mobile", "phone number")),
                Map.entry("harvestDate", List.of("harvest date", "cut date", "picked date")),
                Map.entry("lotCode", List.of(
                        "lot", "lot code", "batch", "batch code", "traceability code")),
                Map.entry("grade", List.of("grade", "quality", "quality grade")),
                Map.entry("lossQuantity", List.of(
                        "loss", "loss quantity", "waste quantity", "culls", "discarded")),
                Map.entry("storageLocation", List.of(
                        "storage", "storage location", "cooler", "cooler location", "shelf")),
                Map.entry("orderDate", List.of("order date", "sale date", "invoice date")),
                Map.entry("observedOn", List.of("observed on", "observation date", "date")),
                Map.entry("bedOrField", List.of(
                        "bed", "field", "location", "bed field", "plot", "block")),
                Map.entry("summary", List.of("subject", "headline", "summary")),
                Map.entry("observation", List.of("observation", "finding", "details")),
                Map.entry("performedBy", List.of("performed by", "worker", "employee", "crew")),
                Map.entry("followUpDate", List.of("follow up", "follow up date", "review date")),
                Map.entry("taskId", List.of("task id", "job id", "work id")),
                Map.entry("actualMinutes", List.of(
                        "actual minutes", "labor minutes", "minutes", "duration")),
                Map.entry("inputCost", List.of("input cost", "material cost", "materials")),
                Map.entry("fieldObservation", List.of(
                        "field observation", "result", "worker notes")),
                Map.entry("designType", List.of("design type", "recipe type", "arrangement type")),
                Map.entry("laborMinutes", List.of("labor minutes", "minutes", "build time")),
                Map.entry("laborRatePerHour", List.of(
                        "labor rate", "hourly rate", "labor rate per hour")),
                Map.entry("overheadPercent", List.of("overhead", "overhead percent")),
                Map.entry("wastePercent", List.of("waste", "waste percent", "shrink")),
                Map.entry("seasonYear", List.of("season", "season year", "year")),
                Map.entry("sowDate", List.of("sow date", "seed date", "seeding date")),
                Map.entry("transplantDate", List.of(
                        "transplant date", "planting date", "plant out date")),
                Map.entry("harvestStart", List.of("harvest start", "first harvest")),
                Map.entry("harvestEnd", List.of("harvest end", "last harvest")),
                Map.entry("bedFeet", List.of("bed feet", "row feet", "linear feet")),
                Map.entry("expectedStems", List.of(
                        "expected stems", "projected stems", "yield", "stem target")),
                Map.entry("spacingInches", List.of("spacing", "spacing inches", "plant spacing")),
                Map.entry("rowsPerBed", List.of("rows per bed", "bed rows", "rows")),
                Map.entry("sowDaysFromFrost", List.of(
                        "sow days from frost", "sow offset", "seed offset")),
                Map.entry("transplantDaysFromFrost", List.of(
                        "transplant days from frost", "transplant offset")),
                Map.entry("daysToHarvest", List.of("days to harvest", "dtm", "days to maturity")),
                Map.entry("harvestWindowDays", List.of(
                        "harvest window", "harvest window days", "bloom window")),
                Map.entry("successionDays", List.of(
                        "succession", "succession days", "succession interval")),
                Map.entry("stemsPerPlant", List.of("stems per plant", "stems plant")),
                Map.entry("germinationPercent", List.of(
                        "germination", "germination percent", "germ rate")),
                Map.entry("soilPrepLeadDays", List.of("soil prep lead", "prep lead days")),
                Map.entry("supportDaysAfterPlanting", List.of(
                        "support days", "netting days", "support offset")));
    }

    public record FieldMatch(
            String canonicalField, String sourceField, double confidence, String reason) {
    }

    public record MappingSuggestion(
            Map<String, String> fieldMapping,
            List<FieldMatch> matches,
            Map<String, List<String>> ambiguous,
            List<String> requiredUnmapped,
            List<String> unmappedSourceFields) {

        public MappingSuggestion {
            fieldMapping = Map.copyOf(fieldMapping);
            matches = List.copyOf(matches);
            ambiguous = Map.copyOf(ambiguous);
            requiredUnmapped = List.copyOf(requiredUnmapped);
            unmappedSourceFields = List.copyOf(unmappedSourceFields);
        }
    }

    public record MappingIssue(int row, String field, String message) {
    }

    public record MappingPreview(
            String recordType,
            Map<String, String> fieldMapping,
            Map<String, Object> defaults,
            List<String> sourceKeyFields,
            List<Map<String, Object>> records,
            List<MappingIssue> issues,
            List<String> unmappedSourceFields,
            List<String> unmappedCanonicalFields,
            List<String> requiredUnmapped,
            int rowCount,
            int validCount,
            int invalidCount,
            boolean ready,
            boolean durableSourceKeysReady) {

        public MappingPreview {
            fieldMapping = Map.copyOf(fieldMapping);
            defaults = Map.copyOf(defaults);
            sourceKeyFields = List.copyOf(sourceKeyFields);
            records = List.copyOf(records);
            issues = List.copyOf(issues);
            unmappedSourceFields = List.copyOf(unmappedSourceFields);
            unmappedCanonicalFields = List.copyOf(unmappedCanonicalFields);
            requiredUnmapped = List.copyOf(requiredUnmapped);
        }
    }

    private record ScoredField(String field, double score) {
    }

    private record PathValue(boolean exists, Object value) {
    }
}
