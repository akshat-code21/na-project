package com.bhttp.proto;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable binary frame: 9-byte header + payload.
 * Header layout (big-endian): Length(24) | Type(8) | Flags(8) | R(1)+RequestId(31).
 */
public record Frame(int type, int flags, int requestId, byte[] payload) {
    public static final int HEADER_LEN = 9;
    public static final int MAX_FRAME = 16_777_215; // 2^24 - 1

    public Frame {
        if ((type & ~0xFF) != 0) throw new IllegalArgumentException("type must be 0..255");
        if ((flags & ~0xFF) != 0) throw new IllegalArgumentException("flags must be 0..255");
        if ((requestId & 0x80000000) != 0) throw new IllegalArgumentException("requestId top bit must be 0");
        if (requestId < 0) throw new IllegalArgumentException("requestId must be >= 0");
        Objects.requireNonNull(payload, "payload");
        if (payload.length > MAX_FRAME) throw new IllegalArgumentException("payload too large");
    }

    public int length() { return payload.length; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Frame f)) return false;
        return type == f.type && flags == f.flags && requestId == f.requestId
                && Arrays.equals(payload, f.payload);
    }

    @Override
    public int hashCode() {
        int h = Objects.hash(type, flags, requestId);
        return 31 * h + Arrays.hashCode(payload);
    }

    @Override
    public String toString() {
        return "Frame{type=0x%02X flags=0x%02X id=%d len=%d}".formatted(type, flags, requestId, payload.length);
    }
}
