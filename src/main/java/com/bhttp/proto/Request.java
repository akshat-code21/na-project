package com.bhttp.proto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class Request {
    private Request() {}
    public static final int METHOD_GET = 0x01;
    public static final int METHOD_HEAD = 0x02;
    public static final int MAX_PATH = 2048;
    public record Decoded(int method, String path, List<HeaderTable.Header> headers) {}
    public static byte[] encode(int method, String path, List<HeaderTable.Header> headers) throws ProtocolException {
        if (method != METHOD_GET && method != METHOD_HEAD) throw new ProtocolException("bad method byte");
        byte[] pbytes = path.getBytes(StandardCharsets.UTF_8);
        if (pbytes.length == 0 || pbytes.length > MAX_PATH) throw new ProtocolException("bad path length");
        byte[] hblock = HeaderTable.encode(headers);
        ByteBuffer buf = ByteBuffer.allocate(1 + 2 + pbytes.length + 2 + hblock.length);
        buf.put((byte) method);
        buf.putShort((short) pbytes.length);
        buf.put(pbytes);
        buf.putShort((short) headers.size());
        buf.put(hblock);
        return buf.array();
    }
    public static Decoded decode(byte[] payload) throws ProtocolException {
        ByteBuffer buf = ByteBuffer.wrap(payload);
        if (buf.remaining() < 1 + 2 + 2) throw new ProtocolException("REQUEST too short");
        int method = buf.get() & 0xFF;
        if (method != METHOD_GET && method != METHOD_HEAD) throw new ProtocolException("unknown method 0x" + Integer.toHexString(method));
        int plen = buf.getShort() & 0xFFFF;
        if (plen == 0 || plen > MAX_PATH) throw new ProtocolException("bad path len " + plen);
        if (buf.remaining() < plen + 2) throw new ProtocolException("truncated REQUEST path");
        byte[] pb = new byte[plen]; buf.get(pb);
        String path = HeaderTable.strictUtf8(pb, "path");
        if (!path.startsWith("/")) throw new ProtocolException("path must start with /");
        if (path.indexOf(0) >= 0) throw new ProtocolException("path contains NUL");
        int nh = buf.getShort() & 0xFFFF;
        if (nh > HeaderTable.MAX_HEADERS) throw new ProtocolException("too many headers");
        byte[] rest = new byte[buf.remaining()]; buf.get(rest);
        List<HeaderTable.Header> hs = HeaderTable.decode(rest);
        if (hs.size() != nh) throw new ProtocolException("header count mismatch claimed=" + nh + " actual=" + hs.size());
        return new Decoded(method, path, hs);
    }
    public static String methodName(int m) { return m == METHOD_GET ? "GET" : m == METHOD_HEAD ? "HEAD" : "?"; }
}
