package com.bhttp;

import com.bhttp.proto.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FrameIOTest {
    static Frame roundTrip(Frame f) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        FrameIO.writeFrame(out, f);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bos.toByteArray()));
        return FrameIO.readFrame(in);
    }
    @Test public void roundTripAllTypes() throws Exception {
        assertEquals(new Frame(0x01, 0x03, 1, new byte[]{1,2,3}), roundTrip(new Frame(0x01, 0x03, 1, new byte[]{1,2,3})));
        assertEquals(new Frame(0x02, 0x00, 7, new byte[0]), roundTrip(new Frame(0x02, 0x00, 7, new byte[0])));
        assertEquals(new Frame(0x03, 0x01, 2147483647, new byte[]{9}), roundTrip(new Frame(0x03, 0x01, 2147483647, new byte[]{9})));
        assertEquals(new Frame(0x9F, 0xFF, 5, new byte[]{1}), roundTrip(new Frame(0x9F, 0xFF, 5, new byte[]{1})));
    }
    @Test public void byteOrderGolden() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        FrameIO.writeFrame(new DataOutputStream(bos), new Frame(0x01, 0x03, 1, new byte[]{0x01,0x02,0x03,0x04,0x05}));
        byte[] b = bos.toByteArray();
        byte[] expect = new byte[]{0x00,0x00,0x05,0x01,0x03,0x00,0x00,0x00,0x01,0x01,0x02,0x03,0x04,0x05};
        assertArrayEquals(expect, b);
    }
    @Test public void cleanEofReturnsNull() throws Exception {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(new byte[0]));
        assertNull(FrameIO.readFrame(in));
    }
    @Test public void truncatedHeaderThrows() {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(new byte[]{0x00,0x00}));
        assertThrows(ProtocolException.class, () -> FrameIO.readFrame(in));
    }
    @Test public void truncatedPayloadThrows() {
        byte[] hdr = new byte[]{0x00,0x00,0x64,0x01,0x03,0x00,0x00,0x00,0x01, 0x01, 0x02};
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(hdr));
        assertThrows(ProtocolException.class, () -> FrameIO.readFrame(in));
    }
    @Test public void zeroIdRejected() {
        byte[] hdr = new byte[]{0x00,0x00,0x00,0x01,0x03,0x00,0x00,0x00,0x00};
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(hdr));
        assertThrows(ProtocolException.class, () -> FrameIO.readFrame(in));
    }
    @Test public void topBitRejected() {
        byte[] hdr = new byte[]{0x00,0x00,0x00,0x01,0x03,(byte)0x80,0x00,0x00,0x01};
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(hdr));
        assertThrows(ProtocolException.class, () -> FrameIO.readFrame(in));
    }
}
