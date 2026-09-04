package com.flowerfarm.automation.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    /** Serves until the client closes stdin. Stdout remains protocol-only. */
    public void serve(InputStream input, OutputStream output, PrintStream diagnostics)
            throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(output, StandardCharsets.UTF_8));
        ObjectWriter jsonWriter = mapper.writer().without(SerializationFeature.INDENT_OUTPUT);
        diagnostics.println("FlowersForever MCP stdio adapter ready");
        InputFrame frame;
        while ((frame = readFrame(reader)) != null) {
            if (!frame.oversized() && frame.contents().isBlank()) {
                continue;
            }
            ObjectNode response;
            try {
                if (frame.oversized()) {
                    response = handler.inputLimitExceeded(maxFrameCharacters);
                } else {
                    JsonNode request = jsonReader.readTree(frame.contents());
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

    /** Drains an oversized frame before accepting the client's next message. */
    private InputFrame readFrame(BufferedReader reader) throws IOException {
        StringBuilder contents = new StringBuilder(Math.min(maxFrameCharacters, 4096));
        boolean consumed = false;
        boolean oversized = false;
        int next;
        while ((next = reader.read()) != -1) {
            consumed = true;
            if (next == '\n') {
                break;
            }
            if (next == '\r') {
                reader.mark(1);
                int following = reader.read();
                if (following != -1 && following != '\n') {
                    reader.reset();
                }
                break;
            }
            if (contents.length() < maxFrameCharacters) {
                contents.append((char) next);
            } else {
                oversized = true;
            }
        }
        return consumed ? new InputFrame(oversized ? "" : contents.toString(), oversized) : null;
    }

    private record InputFrame(String contents, boolean oversized) {
    }
}
