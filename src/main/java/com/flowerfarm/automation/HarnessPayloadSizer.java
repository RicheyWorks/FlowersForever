package com.flowerfarm.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/** Measures serialized payloads without retaining a second JSON copy in memory. */
final class HarnessPayloadSizer {
    private final ObjectWriter writer;
    private final long oversizedWeight;

    HarnessPayloadSizer(ObjectMapper mapper, long budget) {
        if (budget < 0 || budget == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Payload budget must allow an oversized sentinel");
        }
        writer = Objects.requireNonNull(mapper, "mapper").writer();
        oversizedWeight = budget + 1;
    }

    long size(Object... values) {
        CountingSink sink = new CountingSink(oversizedWeight);
        try {
            writer.writeValue(sink, values);
            return sink.count;
        } catch (IOException | RuntimeException exception) {
            // An unmeasurable result must not bypass eviction or lose its execution receipt.
            return oversizedWeight;
        }
    }

    long combined(long first, long second) {
        return first >= oversizedWeight - second ? oversizedWeight : first + second;
    }

    private static final class CountingSink extends OutputStream {
        private final long limit;
        private long count;

        private CountingSink(long limit) {
            this.limit = limit;
        }

        @Override
        public void write(int value) throws IOException {
            add(1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            add(length);
        }

        private void add(int length) throws IOException {
            if (length >= limit - count) {
                count = limit;
                throw new IOException("Replay payload exceeds its accounting budget");
            }
            count += length;
        }
    }
}
