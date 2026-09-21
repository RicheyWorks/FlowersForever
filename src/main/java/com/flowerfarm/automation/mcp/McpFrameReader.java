package com.flowerfarm.automation.mcp;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Decodes each bounded UTF-8 frame independently so invalid bytes cannot alter a command. */
final class McpFrameReader {
    private final BufferedInputStream input;
    private final int maxCharacters;
    private boolean skipLineFeed;

    McpFrameReader(InputStream input, int maxCharacters) {
        this.input = new BufferedInputStream(input);
        this.maxCharacters = maxCharacters;
    }

    InputFrame read() throws IOException {
        FrameBuffer frame = new FrameBuffer(maxCharacters);
        boolean consumed = false;
        int next;
        while ((next = input.read()) != -1) {
            if (skipLineFeed) {
                skipLineFeed = false;
                if (next == '\n') {
                    continue;
                }
            }
            consumed = true;
            if (next == '\n' || next == '\r') {
                skipLineFeed = next == '\r';
                break;
            }
            frame.append((byte) next);
        }
        return consumed ? frame.finish() : null;
    }

    record InputFrame(String contents, boolean oversized, boolean malformed) { }

    private static final class FrameBuffer {
        private static final int BUFFER_SIZE = 8192;
        private final ByteBuffer bytes = ByteBuffer.allocate(BUFFER_SIZE);
        private final CharBuffer characters = CharBuffer.allocate(BUFFER_SIZE);
        private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        private final StringBuilder contents;
        private final int maxCharacters;
        private boolean malformed;
        private boolean oversized;

        private FrameBuffer(int maxCharacters) {
            this.maxCharacters = maxCharacters;
            contents = new StringBuilder(Math.min(maxCharacters, 4096));
        }

        private void append(byte value) {
            if (malformed || oversized) {
                return; // Drain the remainder without retaining a rejected frame.
            }
            bytes.put(value);
            if (!bytes.hasRemaining()) {
                decode(false);
            }
        }

        private void decode(boolean end) {
            bytes.flip();
            CoderResult result;
            do {
                characters.clear();
                result = decoder.decode(bytes, characters, end);
                if (result.isError()) {
                    malformed = true;
                    contents.setLength(0);
                    return;
                }
                appendDecoded();
                if (oversized) {
                    return;
                }
            } while (result.isOverflow());
            bytes.compact(); // Retain an incomplete code point for the next byte buffer.
            if (end) {
                characters.clear();
                decoder.flush(characters);
                appendDecoded();
            }
        }

        private void appendDecoded() {
            characters.flip();
            if (characters.remaining() > maxCharacters - contents.length()) {
                oversized = true;
                contents.setLength(0);
            } else {
                contents.append(characters);
            }
        }

        private InputFrame finish() {
            if (!malformed && !oversized) {
                decode(true);
            }
            return new InputFrame(contents.toString(), oversized, malformed);
        }
    }
}
