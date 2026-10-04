package com.bhttp.proto;

/** Semantic framing error. Caller maps to 400 RESPONSE when frame boundary is known. */
public class ProtocolException extends java.io.IOException {
    public ProtocolException(String msg) { super(msg); }
    public ProtocolException(String msg, Throwable cause) { super(msg, cause); }
}
