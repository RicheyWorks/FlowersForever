package com.flowerfarm.automation.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessManifest;
import com.flowerfarm.automation.HarnessPolicyException;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.automation.HarnessResponse;
import com.flowerfarm.automation.HarnessToolAnnotations;
import com.flowerfarm.automation.HarnessToolDefinition;
import com.flowerfarm.security.SecretRedactor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Maps MCP JSON-RPC messages to the transport-neutral automation gateway. */
@Component
public class McpProtocolHandler {

    public static final String CURRENT_PROTOCOL_VERSION = "2026-07-28";
    public static final String LEGACY_PROTOCOL_VERSION = "2025-11-25";
    public static final String SERVER_NAME = "flowersforever-farm-harness";
    public static final String SERVER_VERSION = "1.2.0";

    private static final int INVALID_REQUEST = -32600;
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;
    private static final int INTERNAL_ERROR = -32603;
    private static final int UNSUPPORTED_PROTOCOL_VERSION = -32022;
    private static final long DISCOVERY_TTL_MILLIS = 300_000;
    private static final String PROTOCOL_VERSION_KEY =
            "io.modelcontextprotocol/protocolVersion";
    private static final String CLIENT_CAPABILITIES_KEY =
            "io.modelcontextprotocol/clientCapabilities";
    private static final String SERVER_INFO_KEY =
            "io.modelcontextprotocol/serverInfo";
    private static final String EXTENSION_PREFIX =
            "com.richeyworks.flowersforever/";
    private static final ObjectWriter CANONICAL_IDENTITY_WRITER = new ObjectMapper().writer();

    private final HarnessGateway gateway;
    private final HarnessProperties properties;
    private final ObjectMapper mapper;
    private final ObjectMapper argumentMapper;
    private final ObjectWriter legacyIdentityWriter;
    private final ObjectWriter legacyCompactIdentityWriter;
    private final ObjectWriter legacyPrettyIdentityWriter;
    private boolean legacyInitialized;

    public McpProtocolHandler(
            HarnessGateway gateway, HarnessProperties properties, ObjectMapper mapper) {
        this.gateway = gateway;
        this.properties = properties;
        this.mapper = mapper;
        this.argumentMapper = mapper.copy()
                .disable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        this.legacyIdentityWriter = mapper.writer();
        this.legacyCompactIdentityWriter = mapper.writer()
                .without(SerializationFeature.INDENT_OUTPUT);
        this.legacyPrettyIdentityWriter = mapper.writer(new DefaultPrettyPrinter());
    }

    /** Handles one parsed JSON-RPC message, returning null for notifications. */
    public ObjectNode handle(JsonNode message) {
        JsonNode id = usableId(message == null ? null : message.get("id"));
        try {
            ObjectNode request = requireRequest(message);
            String method = request.path("method").asText();
            if (!request.has("id")) {
                return null;
            }
            ObjectNode params = params(request);
            return switch (method) {
                case "server/discover" -> result(id, discover(params));
                case "initialize" -> result(id, initialize(params));
                case "ping" -> result(id, emptyResult(isModern(params)));
                case "tools/list" -> result(id, listTools(params));
                case "tools/call" -> callTool(id, params);
                default -> error(id, METHOD_NOT_FOUND,
                        "Method not found: " + method, null);
            };
        } catch (McpProtocolException exception) {
            return error(id, exception.code(), exception.getMessage(), exception.data());
        } catch (RuntimeException exception) {
            return error(id, INTERNAL_ERROR, "Internal server error", null);
        }
    }

    /** Builds a JSON-RPC parse-error response for malformed input framing. */
    public ObjectNode parseError() {
        return error(null, -32700, "Parse error: invalid JSON", null);
    }

    public ObjectNode inputLimitExceeded(int maxCharacters) {
        return error(null, -32700,
                "Parse error: MCP frame exceeds " + maxCharacters + " characters", null);
    }

    /** Builds a JSON-RPC internal-error response for an unexpected adapter failure. */
    public ObjectNode internalError() {
        return error(null, INTERNAL_ERROR, "Internal server error", null);
    }

    private ObjectNode discover(ObjectNode params) {
        requireModern(params);
        ObjectNode result = modernResult();
        ArrayNode supported = result.putArray("supportedVersions");
        supported.add(CURRENT_PROTOCOL_VERSION);
        result.set("capabilities", capabilities());
        result.put("instructions", "Use farm-data tools to inspect schemas, map prior-app "
                + "or spreadsheet rows, preview imports, and operate farm records. "
                + "Only tools enabled by the farm operator are listed.");
        result.put("ttlMs", DISCOVERY_TTL_MILLIS);
        result.put("cacheScope", "private");
        return result;
    }

    private ObjectNode initialize(ObjectNode params) {
        if (!params.path("protocolVersion").isTextual()
                || !params.path("capabilities").isObject()
                || !params.path("clientInfo").path("name").isTextual()
                || !params.path("clientInfo").path("version").isTextual()) {
            throw protocol(INVALID_PARAMS,
                    "initialize requires protocolVersion, capabilities, and clientInfo");
        }
        legacyInitialized = true;
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", LEGACY_PROTOCOL_VERSION);
        result.set("capabilities", capabilities());
        result.set("serverInfo", serverInfo());
        result.put("instructions", "Use the farm-data tools to inspect, map, preview, "
                + "import, and maintain farm records.");
        return result;
    }

    private ObjectNode listTools(ObjectNode params) {
        boolean modern = requireSupportedEra(params);
        HarnessManifest manifest = gateway.manifest(properties.getToken());
        ArrayNode tools = mapper.createArrayNode();
        manifest.tools().stream()
                .filter(HarnessToolDefinition::allowed)
                .sorted(Comparator.comparing(HarnessToolDefinition::name))
                .map(this::mcpTool)
                .forEach(tools::add);
        ObjectNode result = modern ? modernResult() : mapper.createObjectNode();
        result.set("tools", tools);
        if (modern) {
            result.put("ttlMs", DISCOVERY_TTL_MILLIS);
            result.put("cacheScope", "private");
        }
        return result;
    }

    private ObjectNode callTool(JsonNode id, ObjectNode params) {
        boolean modern;
        try {
            modern = requireSupportedEra(params);
        } catch (McpProtocolException exception) {
            return error(id, exception.code(), exception.getMessage(), exception.data());
        }
        JsonNode nameNode = params.get("name");
        if (nameNode == null || !nameNode.isTextual() || nameNode.asText().isBlank()) {
            return error(id, INVALID_PARAMS, "tools/call requires a tool name", null);
        }
        String name = nameNode.asText();
        JsonNode suppliedArguments = params.get("arguments");
        if (suppliedArguments != null && !suppliedArguments.isObject()) {
            return error(id, INVALID_PARAMS, "tools/call arguments must be an object", null);
        }

        HarnessManifest manifest;
        try {
            manifest = gateway.manifest(properties.getToken());
        } catch (RuntimeException exception) {
            return toolError(id, modern, name, exception);
        }
        HarnessToolDefinition tool = manifest.tools().stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst().orElse(null);
        if (tool == null) {
            return error(id, INVALID_PARAMS, "Unknown or unavailable tool: " + name, null);
        }

        ObjectNode originalArguments = suppliedArguments == null
                ? mapper.createObjectNode() : (ObjectNode) suppliedArguments.deepCopy();
        ObjectNode argumentNode = originalArguments.deepCopy();
        try {
            Instant expiresAt = expiration(tool, argumentNode);
            ObjectNode identityArguments = argumentNode.deepCopy();
            if (expiresAt != null) {
                identityArguments.put("_expiresAt", expiresAt.toString());
            }
            String requestId = requestId(id, name, identityArguments, CANONICAL_IDENTITY_WRITER);
            Map<String, Object> arguments = argumentMapper.convertValue(numericArguments(argumentNode, false),
                    mapper.getTypeFactory().constructMapType(
                            LinkedHashMap.class, String.class, Object.class));
            HarnessCommand command = new HarnessCommand(
                    requestId, tool.action(), arguments, expiresAt);
            HarnessResponse response = replayCommand(id, name, tool, originalArguments, command);
            if (response == null) {
                if (!tool.allowed()) {
                    return unavailableTool(id, name);
                }
                rejectAmbiguousNumericReplay(id, name, tool, originalArguments, command);
                response = gateway.execute(properties.getToken(), tool.pluginId(), command);
            }
            return result(id, toolResult(modern, mapper.valueToTree(response),
                    !response.success()));
        } catch (HarnessPolicyException exception) {
            return unavailableTool(id, name);
        } catch (RuntimeException exception) {
            if (!tool.allowed()) {
                return unavailableTool(id, name);
            }
            return toolError(id, modern, name, exception);
        }
    }

    private HarnessResponse replayCommand(JsonNode id, String name, HarnessToolDefinition tool,
                                          ObjectNode originalArguments, HarnessCommand command) {
        HarnessResponse canonical = gateway.replay(properties.getToken(), tool.pluginId(), command)
                .orElse(null);
        if (canonical != null) {
            return canonical;
        }
        Set<String> legacyIds = new LinkedHashSet<>();
        legacyIds.add(requestId(id, name, originalArguments, legacyIdentityWriter));
        legacyIds.add(requestId(id, name, originalArguments, legacyCompactIdentityWriter));
        legacyIds.add(requestId(id, name, originalArguments, legacyPrettyIdentityWriter));
        legacyIds.remove(command.requestId());
        for (String legacyId : legacyIds) {
            HarnessCommand legacyCommand = new HarnessCommand(legacyId, command.action(),
                    command.arguments(), command.expiresAt());
            HarnessResponse legacy = gateway.replay(
                    properties.getToken(), tool.pluginId(), legacyCommand).orElse(null);
            if (legacy != null) {
                return legacy;
            }
        }
        return null;
    }

    private ObjectNode unavailableTool(JsonNode id, String name) {
        return error(id, INVALID_PARAMS, "Unknown or unavailable tool: " + name, null);
    }

    private void rejectAmbiguousNumericReplay(JsonNode id, String name, HarnessToolDefinition tool,
                                             ObjectNode originalArguments, HarnessCommand command) {
        JsonNode roundedId = numericArguments(id, true);
        ObjectNode roundedArguments = (ObjectNode) numericArguments(originalArguments, true);
        // Old parsers rounded before hashing. Such receipts cannot establish which exact
        // decimal request was accepted, but must still prevent repeating an existing effect.
        if (canonical(id).equals(canonical(roundedId))
                && canonical(originalArguments).equals(canonical(roundedArguments))) {
            return;
        }
        Set<String> ambiguousIds = new LinkedHashSet<>();
        ambiguousIds.add(requestId(roundedId, name, roundedArguments, legacyIdentityWriter));
        ambiguousIds.add(requestId(roundedId, name, roundedArguments, legacyCompactIdentityWriter));
        ambiguousIds.add(requestId(roundedId, name, roundedArguments, legacyPrettyIdentityWriter));
        roundedArguments.remove("_expiresAt");
        Map<String, Object> legacyArguments = argumentMapper.convertValue(roundedArguments,
                mapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        for (String legacyId : ambiguousIds) {
            HarnessCommand legacyCommand;
            try {
                legacyCommand = new HarnessCommand(legacyId, command.action(),
                        legacyArguments, command.expiresAt());
            } catch (IllegalArgumentException exception) {
                // Old nonfinite argument values could not pass the command boundary.
                return;
            }
            if (gateway.replay(properties.getToken(), tool.pluginId(), legacyCommand).isPresent()) {
                throw new IllegalArgumentException("A legacy rounded numeric request has an existing receipt; "
                        + "review that receipt before submitting a new request id");
            }
        }
    }

    private JsonNode numericArguments(JsonNode value, boolean rounded) {
        if (value.isObject()) {
            ObjectNode copy = mapper.createObjectNode();
            value.properties().forEach(entry ->
                    copy.set(entry.getKey(), numericArguments(entry.getValue(), rounded)));
            return copy;
        }
        if (value.isArray()) {
            ArrayNode copy = mapper.createArrayNode();
            value.forEach(item -> copy.add(numericArguments(item, rounded)));
            return copy;
        }
        if (value.isFloatingPointNumber()) {
            return rounded || isNegativeZero(value)
                    ? DoubleNode.valueOf(value.doubleValue()) : DecimalNode.valueOf(value.decimalValue());
        }
        return value;
    }

    private static boolean isNegativeZero(JsonNode value) {
        return value.isDouble() && Double.doubleToRawLongBits(value.doubleValue())
                == Double.doubleToRawLongBits(-0.0d);
    }

    private ObjectNode toolError(
            JsonNode id, boolean modern, String toolName, RuntimeException exception) {
        String message = SecretRedactor.redact(exception.getMessage(), 240);
        if (message == null || message.isBlank()) {
            message = "Tool execution failed";
        }
        ObjectNode structured = mapper.createObjectNode();
        structured.put("success", false);
        structured.put("tool", toolName);
        structured.put("error", message);
        return result(id, toolResult(modern, structured, true));
    }

    private ObjectNode toolResult(boolean modern, JsonNode structured, boolean isError) {
        ObjectNode result = modern ? modernResult() : mapper.createObjectNode();
        ObjectNode text = mapper.createObjectNode();
        text.put("type", "text");
        try {
            text.put("text", mapper.writeValueAsString(structured));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize tool result", exception);
        }
        result.putArray("content").add(text);
        result.set("structuredContent", structured);
        result.put("isError", isError);
        return result;
    }

    private ObjectNode mcpTool(HarnessToolDefinition tool) {
        ObjectNode result = mapper.createObjectNode();
        result.put("name", tool.name());
        result.put("title", title(tool.action()));
        result.put("description", tool.description());
        ObjectNode schema = mapper.valueToTree(tool.inputSchema());
        if (tool.annotations().freshnessRequired()) {
            ObjectNode schemaProperties = (ObjectNode) schema.withObject("properties");
            ObjectNode expires = mapper.createObjectNode();
            expires.put("type", "string");
            expires.put("format", "date-time");
            expires.put("description", "UTC expiry no more than 30 seconds in the future.");
            schemaProperties.set("_expiresAt", expires);
            ArrayNode required = schema.withArray("required");
            required.add("_expiresAt");
        }
        result.set("inputSchema", schema);
        result.set("annotations", annotations(tool.annotations()));
        ObjectNode metadata = mapper.createObjectNode();
        metadata.put(EXTENSION_PREFIX + "pluginId", tool.pluginId());
        metadata.put(EXTENSION_PREFIX + "action", tool.action());
        metadata.put(EXTENSION_PREFIX + "risk", tool.risk().name());
        metadata.put(EXTENSION_PREFIX + "physicalWorld",
                tool.annotations().physicalWorldHint());
        result.set("_meta", metadata);
        return result;
    }

    private ObjectNode annotations(HarnessToolAnnotations source) {
        ObjectNode annotations = mapper.createObjectNode();
        annotations.put("readOnlyHint", source.readOnlyHint());
        annotations.put("destructiveHint", source.destructiveHint());
        annotations.put("idempotentHint", source.idempotentHint());
        annotations.put("openWorldHint", source.openWorldHint());
        return annotations;
    }

    private boolean requireSupportedEra(ObjectNode params) {
        if (hasProtocolVersion(params)) {
            requireModern(params);
            return true;
        }
        if (!legacyInitialized) {
            throw protocol(INVALID_PARAMS,
                    "Legacy clients must initialize before calling server methods");
        }
        return false;
    }

    private void requireModern(ObjectNode params) {
        JsonNode metadata = params.get("_meta");
        if (metadata == null || !metadata.isObject()) {
            throw protocol(INVALID_PARAMS, "Request requires MCP _meta metadata");
        }
        JsonNode requested = metadata.get(PROTOCOL_VERSION_KEY);
        if (requested == null || !requested.isTextual()) {
            throw protocol(INVALID_PARAMS, "Request requires an MCP protocol version");
        }
        if (!CURRENT_PROTOCOL_VERSION.equals(requested.asText())) {
            ObjectNode data = mapper.createObjectNode();
            data.putArray("supported").add(CURRENT_PROTOCOL_VERSION);
            data.put("requested", requested.asText());
            throw protocol(UNSUPPORTED_PROTOCOL_VERSION,
                    "Unsupported protocol version", data);
        }
        if (!metadata.path(CLIENT_CAPABILITIES_KEY).isObject()) {
            throw protocol(INVALID_PARAMS,
                    "Request requires MCP client capabilities metadata");
        }
    }

    private boolean isModern(ObjectNode params) {
        if (!hasProtocolVersion(params)) {
            return false;
        }
        requireModern(params);
        return true;
    }

    private boolean hasProtocolVersion(ObjectNode params) {
        JsonNode metadata = params.get("_meta");
        if (metadata == null) {
            return false;
        }
        if (!metadata.isObject()) {
            throw protocol(INVALID_PARAMS, "MCP _meta metadata must be an object");
        }
        // Legacy clients also send _meta, e.g. for progressToken. Only the
        // namespaced protocol version selects the stateless protocol era.
        return metadata.has(PROTOCOL_VERSION_KEY);
    }

    private ObjectNode requireRequest(JsonNode message) {
        if (message == null || !message.isObject()
                || !"2.0".equals(message.path("jsonrpc").asText())
                || !message.path("method").isTextual()
                || message.path("method").asText().isBlank()) {
            throw protocol(INVALID_REQUEST, "Invalid JSON-RPC request");
        }
        if (message.has("id") && usableId(message.get("id")) == null) {
            throw protocol(INVALID_REQUEST, "JSON-RPC id must be a string or number");
        }
        return (ObjectNode) message;
    }

    private ObjectNode params(ObjectNode request) {
        JsonNode params = request.get("params");
        if (params == null || params.isNull()) {
            return mapper.createObjectNode();
        }
        if (!params.isObject()) {
            throw protocol(INVALID_PARAMS, "Request params must be an object");
        }
        return (ObjectNode) params;
    }

    private Instant expiration(HarnessToolDefinition tool, ObjectNode arguments) {
        JsonNode expiresAt = arguments.remove("_expiresAt");
        if (expiresAt == null) {
            return null;
        }
        if (!tool.annotations().freshnessRequired()) {
            throw new IllegalArgumentException(
                    "Tool '" + tool.name() + "' does not accept _expiresAt");
        }
        if (!expiresAt.isTextual()) {
            throw new IllegalArgumentException("_expiresAt must be an ISO-8601 timestamp");
        }
        try {
            return Instant.parse(expiresAt.asText());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "_expiresAt must be an ISO-8601 UTC timestamp", exception);
        }
    }

    private String requestId(JsonNode id, String name, ObjectNode arguments, ObjectWriter writer) {
        try {
            String material = canonical(id).toString() + '\u0000' + name + '\u0000'
                    + writer.writeValueAsString(canonical(arguments));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "mcp-" + HexFormat.of().formatHex(
                    digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new IllegalStateException("Could not create MCP request identity", exception);
        }
    }

    private JsonNode canonical(JsonNode value) {
        if (value.isFloatingPointNumber()) {
            if (isNegativeZero(value) || value.isDouble() && !Double.isFinite(value.doubleValue())) {
                return value;
            }
            BigDecimal decimal = value.decimalValue().stripTrailingZeros();
            double legacy = decimal.doubleValue();
            // Keep established identities where the ordinary double spelling is exact.
            // Higher precision, overflow, and underflow retain the full decimal value.
            return Double.isFinite(legacy) && BigDecimal.valueOf(legacy).compareTo(decimal) == 0
                    ? DoubleNode.valueOf(legacy) : DecimalNode.valueOf(decimal);
        }
        if (value.isObject()) {
            ObjectNode sorted = mapper.createObjectNode();
            java.util.TreeSet<String> names = new java.util.TreeSet<>();
            value.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                sorted.set(name, canonical(value.get(name)));
            }
            return sorted;
        }
        if (value.isArray()) {
            ArrayNode array = mapper.createArrayNode();
            value.forEach(item -> array.add(canonical(item)));
            return array;
        }
        return value;
    }

    private ObjectNode capabilities() {
        ObjectNode capabilities = mapper.createObjectNode();
        ObjectNode tools = mapper.createObjectNode();
        tools.put("listChanged", false);
        capabilities.set("tools", tools);
        return capabilities;
    }

    private ObjectNode modernResult() {
        ObjectNode result = mapper.createObjectNode();
        result.put("resultType", "complete");
        ObjectNode metadata = mapper.createObjectNode();
        metadata.set(SERVER_INFO_KEY, serverInfo());
        result.set("_meta", metadata);
        return result;
    }

    private ObjectNode emptyResult(boolean modern) {
        return modern ? modernResult() : mapper.createObjectNode();
    }

    private ObjectNode serverInfo() {
        ObjectNode info = mapper.createObjectNode();
        info.put("name", SERVER_NAME);
        info.put("title", "FlowersForever Farm Harness");
        info.put("version", SERVER_VERSION);
        info.put("description", "AI tools for farm data migration and operations.");
        return info;
    }

    private ObjectNode result(JsonNode id, ObjectNode value) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id == null ? mapper.nullNode() : id);
        response.set("result", value);
        return response;
    }

    private ObjectNode error(JsonNode id, int code, String message, JsonNode data) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id == null ? mapper.nullNode() : id);
        ObjectNode error = mapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        if (data != null) {
            error.set("data", data);
        }
        response.set("error", error);
        return response;
    }

    private static JsonNode usableId(JsonNode id) {
        return id != null && (id.isTextual() || id.isNumber()) ? id : null;
    }

    private static String title(String action) {
        StringBuilder title = new StringBuilder();
        for (String word : action.split("-")) {
            if (!title.isEmpty()) {
                title.append(' ');
            }
            title.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return title.toString();
    }

    private static McpProtocolException protocol(int code, String message) {
        return new McpProtocolException(code, message, null);
    }

    private static McpProtocolException protocol(
            int code, String message, JsonNode data) {
        return new McpProtocolException(code, message, data);
    }

    private static final class McpProtocolException extends RuntimeException {

        private final int code;
        private final JsonNode data;

        private McpProtocolException(int code, String message, JsonNode data) {
            super(message == null ? "MCP protocol error" : message);
            this.code = code;
            this.data = data;
        }

        private int code() {
            return code;
        }

        private JsonNode data() {
            return data;
        }
    }
}
