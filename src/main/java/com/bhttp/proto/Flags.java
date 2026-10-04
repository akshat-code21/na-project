package com.bhttp.proto;

/** Per-type flag bits. Reserved bits MUST be 0 on send, ignored on receive. */
public final class Flags {
    private Flags() {}
    public static final int END_STREAM = 0x01;
    public static final int END_HEADERS = 0x02;
}
