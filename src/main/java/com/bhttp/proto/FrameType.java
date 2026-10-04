package com.bhttp.proto;

/** Frame type constants. Anything else is unknown and MUST be skipped by Length. */
public final class FrameType {
    private FrameType() {}
    public static final int REQUEST = 0x01;
    public static final int RESPONSE = 0x02;
    public static final int DATA = 0x03;

    public static boolean isKnown(int t) {
        return t == REQUEST || t == RESPONSE || t == DATA;
    }

    public static String name(int t) {
        return switch (t) {
            case REQUEST -> "REQUEST";
            case RESPONSE -> "RESPONSE";
            case DATA -> "DATA";
            default -> "UNKNOWN(0x%02X)".formatted(t);
        };
    }
}
