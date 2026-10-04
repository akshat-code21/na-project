package com.bhttp;

import com.bhttp.proto.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CodecTest {
    @Test public void headerIndexedRoundTrip() throws Exception {
        var hs = List.of(new HeaderTable.Header("host", "x:1"), new HeaderTable.Header("accept", "*/*"));
        var back = HeaderTable.decode(HeaderTable.encode(hs));
        assertEquals(hs, back);
    }
    @Test public void headerLiteralRoundTrip() throws Exception {
        var hs = List.of(new HeaderTable.Header("x-custom", "v"));
        assertEquals(hs, HeaderTable.decode(HeaderTable.encode(hs)));
    }
    @Test public void badDiscrim() {
        assertThrows(ProtocolException.class, () -> HeaderTable.decode(new byte[]{(byte)0x0B, 0, 0}));
    }
    @Test public void uppercaseRejected() {
        assertThrows(ProtocolException.class, () -> HeaderTable.encode(List.of(new HeaderTable.Header("Host", "x"))));
    }
    @Test public void requestRoundTrip() throws Exception {
        var hs = List.of(new HeaderTable.Header("host", "h:1"));
        byte[] enc = Request.encode(Request.METHOD_GET, "/index.html", hs);
        var d = Request.decode(enc);
        assertEquals("/index.html", d.path());
        assertEquals(Request.METHOD_GET, d.method());
        assertEquals(hs, d.headers());
    }
    @Test public void requestBadMethod() throws Exception {
        var hs = List.of(new HeaderTable.Header("host", "h"));
        byte[] enc = Request.encode(1, "/a", hs);
        enc[0] = 0x09;
        assertThrows(ProtocolException.class, () -> Request.decode(enc));
    }
    @Test public void responseRoundTrip() throws Exception {
        var hs = List.of(new HeaderTable.Header("content-type", "text/html"));
        byte[] enc = Response.encode(200, hs);
        var d = Response.decode(enc);
        assertEquals(200, d.status());
        assertEquals(hs, d.headers());
    }
    @Test public void headerCountMismatch() throws Exception {
        var hs = List.of(new HeaderTable.Header("host", "h"));
        byte[] enc = Request.encode(1, "/a", hs);
        enc[enc.length - 10]++;
        assertThrows(ProtocolException.class, () -> Request.decode(enc));
    }
}
