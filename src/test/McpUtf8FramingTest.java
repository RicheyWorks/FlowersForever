package com.flowerfarm.automation.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessProperties;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class McpUtf8FramingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rejectsMalformedUtf8BeforeDispatchAndRecoversAtTheNextFrame() throws Exception {
        for (byte[] malformed : List.of(
                bytes(0x80), bytes(0xc0, 0xaf), bytes(0xc3, 0x28),
                bytes(0xed, 0xa0, 0x80), bytes(0xf4, 0x90, 0x80, 0x80),
                bytes(0xf0, 0x9f))) {
            byte[] input = concatenate(utf8("{\"id\":\""), malformed,
                    utf8("\"}\n{\"id\":\"next\"}\n"));
            Result result = serve(new ByteArrayInputStream(input), 512);
            assertThat(result.responses()).hasSize(2);
            assertThat(result.responses().get(0).path("error").path("code").asInt())
                    .as("invalid bytes must not be replaced and dispatched")
                    .isEqualTo(-32700);
            assertThat(result.dispatched()).extracting(node -> node.path("id").asText())
                    .containsExactly("next");
        }
    }

    @Test
    void keepsValidUnicodeIncludingAnExplicitReplacementCharacter() throws Exception {
        String id = "Tulip 🌷 / 雪 / é / \ufffd";
        Result result = serve(new ByteArrayInputStream(
                utf8(mapper.writeValueAsString(java.util.Map.of("id", id)) + "\n")), 512);
        assertThat(result.dispatched()).hasSize(1);
        assertThat(result.dispatched().get(0).path("id").asText()).isEqualTo(id);
        assertThat(result.responses().get(0).path("id").asText()).isEqualTo(id);
    }

    @Test
    void preservesCodePointsSplitAcrossInputReadsAndDecoderBuffers() throws Exception {
        for (int boundaryOffset = 0; boundaryOffset < 4; boundaryOffset++) {
            String id = "a".repeat(8192 - 7 - boundaryOffset) + "🌷雪é";
            byte[] input = utf8(mapper.writeValueAsString(java.util.Map.of("id", id)) + "\n");
            InputStream fragmented = new ByteArrayInputStream(input) {
                @Override
                public synchronized int read(byte[] destination, int offset, int length) {
                    return super.read(destination, offset, Math.min(length, 3));
                }
            };
            Result result = serve(fragmented, 9000);
            assertThat(result.dispatched()).hasSize(1);
            assertThat(result.dispatched().get(0).path("id").asText()).isEqualTo(id);
        }
    }

    @Test
    void countsDecodedCharactersAndRecoversAfterAnOversizedUnicodeFrame() throws Exception {
        int limit = 256;
        String prefix = "{\"id\":\"";
        String suffix = "\"}";
        String valid = prefix + "雪".repeat(limit - prefix.length() - suffix.length()) + suffix;
        String oversized = prefix + "雪".repeat(limit - prefix.length() - suffix.length() + 1) + suffix;
        Result result = serve(new ByteArrayInputStream(utf8(
                oversized + "\r\n" + valid + "\r\n")), limit);
        assertThat(result.responses()).hasSize(2);
        assertThat(result.responses().get(0).path("error").path("message").asText())
                .contains("exceeds 256");
        assertThat(result.dispatched()).hasSize(1);
        assertThat(result.dispatched().get(0)).isEqualTo(mapper.readTree(valid));
    }

    @Test
    void drainsAnOversizedFrameRejectedAtTheFirstDecoderBuffer() throws Exception {
        Result result = serve(new ByteArrayInputStream(utf8(
                "{\"id\":\"" + "x".repeat(30_000) + "\"}\n{\"id\":\"next\"}\n")), 256);
        assertThat(result.responses()).hasSize(2);
        assertThat(result.responses().get(0).path("error").path("message").asText())
                .contains("exceeds 256");
        assertThat(result.dispatched()).extracting(node -> node.path("id").asText())
                .containsExactly("next");
    }

    @Test
    void rejectsAnInvalidContinuationAcrossDecoderBuffersAndDrainsTheRemainder() throws Exception {
        String prefix = "{\"id\":\"";
        Result result = serve(new ByteArrayInputStream(concatenate(
                utf8(prefix + "x".repeat(8191 - prefix.length())),
                bytes(0xe2, 0x28, 0xa1),
                utf8("x".repeat(20_000) + "\"}\n{\"id\":\"next\"}\n"))), 9000);
        assertThat(result.responses()).hasSize(2);
        assertThat(result.responses().get(0).path("error").path("code").asInt())
                .isEqualTo(-32700);
        assertThat(result.dispatched()).extracting(node -> node.path("id").asText())
                .containsExactly("next");
    }

    @Test
    void malformedFramesRecoverAcrossAllSupportedLineEndingsAndAtEndOfInput() throws Exception {
        for (String ending : List.of("\n", "\r\n", "\r")) {
            Result result = serve(new ByteArrayInputStream(concatenate(
                    utf8("{\"id\":\""), bytes(0xff), utf8("\"}" + ending),
                    utf8("{\"id\":\"last\"}"))), 512);
            assertThat(result.responses()).hasSize(2);
            assertThat(result.responses().get(0).path("error").path("code").asInt())
                    .isEqualTo(-32700);
            assertThat(result.dispatched()).extracting(node -> node.path("id").asText())
                    .containsExactly("last");
        }
        Result finalInvalid = serve(new ByteArrayInputStream(concatenate(
                utf8("{\"id\":\""), bytes(0xe2, 0x82))), 512);
        assertThat(finalInvalid.responses()).hasSize(1);
        assertThat(finalInvalid.responses().get(0).path("error").path("code").asInt())
                .isEqualTo(-32700);
        assertThat(finalInvalid.dispatched()).isEmpty();
    }

    private Result serve(InputStream input, int limit) throws Exception {
        List<JsonNode> dispatched = new ArrayList<>();
        McpProtocolHandler handler = spy(new McpProtocolHandler(
                mock(HarnessGateway.class), new HarnessProperties(), mapper));
        doAnswer(invocation -> {
            JsonNode request = invocation.getArgument(0);
            dispatched.add(request);
            return mapper.createObjectNode().put("jsonrpc", "2.0")
                    .set("id", request.path("id"));
        }).when(handler).handle(any());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new McpStdioServer(handler, mapper, limit).serve(input, output,
                new PrintStream(new ByteArrayOutputStream()));
        List<JsonNode> responses = new ArrayList<>();
        for (String line : output.toString(StandardCharsets.UTF_8).lines().toList()) {
            responses.add(mapper.readTree(line));
        }
        return new Result(dispatched, responses);
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }

    private static byte[] concatenate(byte[]... parts) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            output.write(part);
        }
        return output.toByteArray();
    }

    private record Result(List<JsonNode> dispatched, List<JsonNode> responses) { }
}
