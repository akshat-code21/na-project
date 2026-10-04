package com.bhttp.proto;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class HeaderTable {
    private HeaderTable() {}
    public static final String[] STATIC = { null, ":path", ":status", "content-type", "content-length", "date", "server", "host", "user-agent", "accept", "connection" };
    public static final int MAX_HEADERS = 64;
    public static final int MAX_NAME = 256;
    public static final int MAX_VALUE = 8 * 1024;
    public static final int MAX_BLOCK = 64 * 1024;
    public record Header(String name, String value) {
        public Header { Objects.requireNonNull(name); Objects.requireNonNull(value); }
    }
    public static int indexOf(String name) {
        for (int i = 1; i < STATIC.length; i++) if (STATIC[i].equals(name)) return i;
        return -1;
    }
    public static byte[] encode(List<Header> headers) throws ProtocolException {
        if (headers.size() > MAX_HEADERS) throw new ProtocolException("too many headers");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Header h : headers) {
            String lname = h.name().toLowerCase(Locale.ROOT);
            if (!lname.equals(h.name())) throw new ProtocolException("header name must be lowercase: " + h.name());
            validateName(lname);
            byte[] vbytes = h.value().getBytes(StandardCharsets.UTF_8);
            if (vbytes.length > MAX_VALUE) throw new ProtocolException("header value too large: " + h.name());
            int idx = indexOf(lname);
            if (idx >= 0) { out.write(idx); u16(out, vbytes.length); out.write(vbytes, 0, vbytes.length); }
            else { byte[] nb = lname.getBytes(StandardCharsets.UTF_8); if (nb.length > MAX_NAME) throw new ProtocolException("header name too large"); out.write(0x00); u16(out, nb.length); out.write(nb, 0, nb.length); u16(out, vbytes.length); out.write(vbytes, 0, vbytes.length); }
        }
        if (out.size() > MAX_BLOCK) throw new ProtocolException("header block too large");
        return out.toByteArray();
    }
    public static List<Header> decode(byte[] block) throws ProtocolException {
        List<Header> out = new ArrayList<>();
        ByteBuffer buf = ByteBuffer.wrap(block);
        while (buf.hasRemaining()) {
            if (out.size() >= MAX_HEADERS) throw new ProtocolException("too many headers");
            int d = buf.get() & 0xFF;
            if (d >= 1 && d <= 0x0A) { String name = STATIC[d]; out.add(new Header(name, readValue(buf, name))); }
            else if (d == 0x00) { String name = readName(buf); out.add(new Header(name, readValue(buf, name))); }
            else throw new ProtocolException(String.format("bad header discriminator 0x%02X", d));
        }
        return out;
    }
    private static String readName(ByteBuffer buf) throws ProtocolException {
        int nlen = u16(buf, "name len");
        if (nlen > MAX_NAME) throw new ProtocolException("header name too large");
        if (buf.remaining() < nlen) throw new ProtocolException("truncated header name");
        byte[] nb = new byte[nlen]; buf.get(nb);
        String name = strictUtf8(nb, "header name");
        validateName(name);
        if (!name.equals(name.toLowerCase(Locale.ROOT))) throw new ProtocolException("header name must be lowercase");
        return name;
    }
    private static String readValue(ByteBuffer buf, String forName) throws ProtocolException {
        int vlen = u16(buf, "value len for " + forName);
        if (vlen > MAX_VALUE) throw new ProtocolException("header value too large: " + forName);
        if (buf.remaining() < vlen) throw new ProtocolException("truncated header value: " + forName);
        byte[] vb = new byte[vlen]; buf.get(vb);
        return strictUtf8(vb, "header value " + forName);
    }
    private static void validateName(String n) throws ProtocolException {
        if (n.isEmpty()) throw new ProtocolException("empty header name");
        for (int i = 0; i < n.length(); i++) { char c = n.charAt(i); boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c=='-' || c==':' || c=='_'; if (!ok) throw new ProtocolException("bad header name char: " + c); }
    }
    private static int u16(ByteBuffer buf, String what) throws ProtocolException {
        if (buf.remaining() < 2) throw new ProtocolException("truncated " + what);
        return buf.getShort() & 0xFFFF;
    }
    private static void u16(ByteArrayOutputStream out, int v) { out.write((v>>>8)&0xFF); out.write(v&0xFF); }
    static String strictUtf8(byte[] b, String what) throws ProtocolException {
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString(); }
        catch (CharacterCodingException e) { throw new ProtocolException("bad UTF-8 in " + what, e); }
    }
}
