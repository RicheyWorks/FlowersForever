package com.flowerfarm.automation.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Newline-delimited MCP JSON-RPC transport over stdin/stdout. */
@Component
public class McpStdioServer {

    // Covers a 15 MiB attachment's 20 MiB base64 body plus its JSON envelope.
    public static final int MAX_FRAME_CHARACTERS = 32 * 1024 * 1024;

    private final McpProtocolHandler handler;
    private final ObjectMapper mapper;
    private final ObjectReader jsonReader;
    private final int maxFrameCharacters;

    @Autowired
    public McpStdioServer(McpProtocolHandler handler, ObjectMapper mapper) {
        this(handler, mapper, MAX_FRAME_CHARACTERS);
    }

    McpStdioServer(McpProtocolHandler handler, ObjectMapper mapper, int maxFrameCharacters) {
        if (maxFrameCharacters < 1) {
            throw new IllegalArgumentException("MCP frame size must be positive");
        }
        this.handler = handler;
        this.mapper = mapper;
        this.maxFrameCharacters = maxFrameCharacters;
        // Keep the larger attachment allowance scoped to this transport. A
        // default Jackson string limit otherwise rejects a permitted upload.
        ObjectMapper protocolMapper = mapper.copy();
        StreamReadConstraints constraints = protocolMapper.getFactory().streamReadConstraints()
                .rebuild().maxStringLength(maxFrameCharacters).build();
        protocolMapper.getFactory().setStreamReadConstraints(constraints);
        this.jsonReader = protocolMapper.reader()
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .without(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    /** Serves until the client closes stdin. Stdout remains protocol-only. */
    public void serve(InputStream input, OutputStream output, PrintStream diagnostics)
            throws IOException {
        McpFrameReader reader = new McpFrameReader(input, maxFrameCharacters);
        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(output, StandardCharsets.UTF_8));
        ObjectWriter jsonWriter = mapper.writer().without(SerializationFeature.INDENT_OUTPUT);
        diagnostics.println("FlowersForever MCP stdio adapter ready");
        McpFrameReader.InputFrame frame;
        while ((frame = reader.read()) != null) {
            if (!frame.oversized() && !frame.malformed() && frame.contents().isBlank()) {
                continue;
            }
            ObjectNode response;
            try {
                if (frame.oversized()) {
                    response = handler.inputLimitExceeded(maxFrameCharacters);
                } else if (frame.malformed()) {
                    response = handler.parseError();
                } else {
                    JsonNode request = jsonReader.readTree(frame.contents());
                    request = preserveNegativeZero(frame.contents(), request);
                    response = handler.handle(request);
                }
            } catch (JsonProcessingException exception) {
                response = handler.parseError();
            } catch (RuntimeException exception) {
                diagnostics.println("MCP adapter error: " + exception.getClass().getSimpleName());
                response = handler.internalError();
            }
            if (response != null) {
                writer.write(jsonWriter.writeValueAsString(response));
                writer.newLine();
                writer.flush();
            }
        }
    }

    private JsonNode preserveNegativeZero(String contents, JsonNode request) throws IOException {
        // BigDecimal retains precision and scale but loses zero's sign. Scan the
        // validated frame with the same constrained parser, without a second tree.
        try (JsonParser parser = jsonReader.createParser(contents)) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() != JsonToken.VALUE_NUMBER_FLOAT
                        || !parser.getText().startsWith("-")
                        || parser.getDecimalValue().signum() != 0) {
                    continue;
                }
                JsonPointer pointer = parser.getParsingContext().pathAsPointer();
                DoubleNode negativeZero = DoubleNode.valueOf(-0.0d);
                if (pointer.matches()) {
                    request = negativeZero;
                } else {
                    JsonNode parent = request.at(pointer.head());
                    JsonPointer leaf = pointer.last();
                    if (parent instanceof ObjectNode object) {
                        object.replace(leaf.getMatchingProperty(), negativeZero);
                    } else if (parent instanceof ArrayNode array) {
                        array.set(leaf.getMatchingIndex(), negativeZero);
                    } else {
                        throw new IllegalStateException("Validated JSON number has no container");
                    }
                }
            }
        }
        return request;
    }

}
