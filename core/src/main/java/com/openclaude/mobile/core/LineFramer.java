package com.openclaude.mobile.core;

import java.nio.charset.StandardCharsets;

/**
 * Turns a byte stream into complete lines.
 *
 * <p>Written in Java, and used from Kotlin, for two reasons. First, framing has to
 * happen on bytes: an InputStreamReader can be handed a buffer that ends mid
 * sequence and will happily emit a replacement character, which then corrupts a
 * JSON line. Second, this is the one piece of the protocol hot path where an
 * allocation per line is measurable on a phone, and a plain byte array with an
 * index is the cheapest correct thing.
 *
 * <p>Not thread safe. One framer per stream, used from one thread.
 */
public final class LineFramer {

    private final byte[] buffer;
    private int length;

    public LineFramer(int capacity) {
        if (capacity < 64) {
            throw new IllegalArgumentException("capacity too small: " + capacity);
        }
        this.buffer = new byte[capacity];
        this.length = 0;
    }

    /**
     * Appends bytes, dropping the oldest data if a single line exceeds the buffer.
     * A dropped line is reported by {@link #overflowed()} so the caller can decide
     * whether to kill the stream.
     */
    public void append(byte[] src, int offset, int count) {
        if (count <= 0) {
            return;
        }
        if (length + count > buffer.length) {
            int drop = (length + count) - buffer.length;
            if (drop >= length) {
                // Whole buffer is being replaced.
                length = 0;
                int keep = Math.min(count, buffer.length);
                System.arraycopy(src, offset + count - keep, buffer, 0, keep);
                length = keep;
                overflowed = true;
                return;
            }
            System.arraycopy(buffer, drop, buffer, 0, length - drop);
            length -= drop;
            overflowed = true;
        }
        System.arraycopy(src, offset, buffer, length, count);
        length += count;
    }

    private boolean overflowed = false;

    public boolean overflowed() {
        return overflowed;
    }

    public void clearOverflow() {
        overflowed = false;
    }

    /** Returns the next complete line without its terminator, or null. */
    public String nextLine() {
        for (int i = 0; i < length; i++) {
            if (buffer[i] == '\n') {
                int end = i;
                if (end > 0 && buffer[end - 1] == '\r') {
                    end--;
                }
                String line = new String(buffer, 0, end, StandardCharsets.UTF_8);
                int remaining = length - (i + 1);
                if (remaining > 0) {
                    System.arraycopy(buffer, i + 1, buffer, 0, remaining);
                }
                length = remaining;
                return line;
            }
        }
        return null;
    }

    public int pendingBytes() {
        return length;
    }

    public void reset() {
        length = 0;
        overflowed = false;
    }
}
