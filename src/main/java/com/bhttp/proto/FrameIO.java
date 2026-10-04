package com.bhttp.proto;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

/**
 * The ONLY class that touches the wire framing.
 * All multi-byte ints big-endian (network order).
 * Uses readFully loops so partial TCP reads are handled.
 */
public final class FrameIO {
    private FrameIO() {}

    public static void writeFrame(DataOutputStream out, Frame f) throws IOException {
        int len = f.payload().length;
        out.writeByte((len >>> 16) & 0xFF);
        out.writeByte((len >>> 8) & 0xFF);
        out.writeByte(len & 0xFF);
        out.writeByte(f.type() & 0xFF);
        out.writeByte(f.flags() & 0xFF);
        out.writeInt(f.requestId() & 0x7FFFFFFF);
        if (len > 0) out.write(f.payload());
        out.flush();
    }

    /**
     * Reads one frame. Returns null ONLY on clean EOF before any header byte
     * (peer closed connection between requests). Throws EOFException /
     * ProtocolException on truncated / malformed streams.
     */
    public static Frame readFrame(DataInputStream in) throws IOException {
        byte[] hdr = new byte[Frame.HEADER_LEN];
        int first;
        try {
            first = in.read();
        } catch (EOFException e) {
            return null;
        }
        if (first == -1) return null; // clean EOF
        hdr[0] = (byte) first;
        try {
            in.readFully(hdr, 1, Frame.HEADER_LEN - 1);
        } catch (EOFException e) {
            throw new ProtocolException("truncated frame header: only "
                    + "partial 9-byte header available", e);
        }
        int len = ((hdr[0] & 0xFF) << 16) | ((hdr[1] & 0xFF) << 8) | (hdr[2] & 0xFF);
        int type = hdr[3] & 0xFF;
        int flags = hdr[4] & 0xFF;
        int requestId = ((hdr[5] & 0xFF) << 24) | ((hdr[6] & 0xFF) << 16)
                | ((hdr[7] & 0xFF) << 8) | (hdr[8] & 0xFF);
        if ((requestId & 0x80000000) != 0)
            throw new ProtocolException("reserved top bit of request-id must be 0");
        if (requestId == 0)
            throw new ProtocolException("request-id 0 is illegal in v1 (connection-level reserved)");
        if (len > Frame.MAX_FRAME)
            throw new ProtocolException("frame length " + len + " exceeds MAX_FRAME " + Frame.MAX_FRAME);
        byte[] payload = new byte[len];
        if (len > 0) {
            try {
                in.readFully(payload);
            } catch (EOFException e) {
                throw new ProtocolException(
                        "truncated payload: header claimed " + len + " bytes but stream ended", e);
            }
        }
        return new Frame(type, flags, requestId, payload);
    }
}
