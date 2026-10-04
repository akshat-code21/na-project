package com.bhttp.proto;

import java.nio.ByteBuffer;
import java.util.List;

public final class Response {
    private Response() {}
    public record Decoded(int status, List<HeaderTable.Header> headers) {}
    public static byte[] encode(int status, List<HeaderTable.Header> headers) throws ProtocolException {
        if (status < 100 || status > 599) throw new ProtocolException("bad status " + status);
        byte[] hblock = HeaderTable.encode(headers);
        ByteBuffer buf = ByteBuffer.allocate(2 + 2 + hblock.length);
        buf.putShort((short) status);
        buf.putShort((short) headers.size());
        buf.put(hblock);
        return buf.array();
    }
    public static Decoded decode(byte[] payload) throws ProtocolException {
        ByteBuffer buf = ByteBuffer.wrap(payload);
        if (buf.remaining() < 4) throw new ProtocolException("RESPONSE too short");
        int status = buf.getShort() & 0xFFFF;
        if (status < 100 || status > 599) throw new ProtocolException("bad status " + status);
        int nh = buf.getShort() & 0xFFFF;
        if (nh > HeaderTable.MAX_HEADERS) throw new ProtocolException("too many headers");
        byte[] rest = new byte[buf.remaining()]; buf.get(rest);
        List<HeaderTable.Header> hs = HeaderTable.decode(rest);
        if (hs.size() != nh) throw new ProtocolException("header count mismatch");
        return new Decoded(status, hs);
    }
}
