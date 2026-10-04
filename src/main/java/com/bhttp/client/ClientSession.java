package com.bhttp.client;

import com.bhttp.proto.*;
import com.bhttp.util.Hexdump;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public final class ClientSession {
    private ClientSession() {}
    public record Result(int status, List<HeaderTable.Header> headers, long bodyBytes) {}
    public static Result fetch(String host, int port, String path, boolean head, boolean verbose, OutputStream bodyOut) throws IOException {
        List<HeaderTable.Header> reqHs = new ArrayList<>();
        reqHs.add(new HeaderTable.Header("host", host + ":" + port));
        reqHs.add(new HeaderTable.Header("user-agent", "bcurl/1.0"));
        reqHs.add(new HeaderTable.Header("accept", "*/*"));
        reqHs.add(new HeaderTable.Header("connection", "keep-alive"));
        byte[] reqPayload;
        try { reqPayload = Request.encode(head ? Request.METHOD_HEAD : Request.METHOD_GET, path, reqHs); }
        catch (ProtocolException e) { throw new IOException("bad request: " + e.getMessage(), e); }
        Frame req = new Frame(FrameType.REQUEST, Flags.END_STREAM | Flags.END_HEADERS, 1, reqPayload);
        Socket sock = new Socket();
        sock.connect(new InetSocketAddress(host, port), 5000);
        sock.setSoTimeout(10000);
        try (Socket s = sock; DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream())); DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()))) {
            FrameIO.writeFrame(out, req);
            if (verbose) System.err.print(Hexdump.dump("->", req));
            Integer status = null;
            List<HeaderTable.Header> respHs = null;
            long bodyBytes = 0;
            boolean done = false;
            while (!done) {
                Frame f;
                try { f = FrameIO.readFrame(in); }
                catch (ProtocolException e) { throw new IOException("protocol error: " + e.getMessage(), e); }
                if (f == null) throw new EOFException("server closed before END_STREAM");
                if (verbose) System.err.print(Hexdump.dump("<-", f));
                if (!FrameType.isKnown(f.type())) continue;
                if (f.requestId() != 1) throw new IOException("mismatched request-id " + f.requestId());
                if (f.type() == FrameType.RESPONSE) {
                    Response.Decoded r;
                    try { r = Response.decode(f.payload()); } catch (ProtocolException e) { throw new IOException("bad RESPONSE: " + e.getMessage(), e); }
                    status = r.status(); respHs = r.headers();
                    if ((f.flags() & Flags.END_STREAM) != 0) done = true;
                } else if (f.type() == FrameType.DATA) {
                    if (status == null) throw new IOException("DATA before RESPONSE");
                    bodyOut.write(f.payload());
                    bodyBytes += f.payload().length;
                    if ((f.flags() & Flags.END_STREAM) != 0) done = true;
                } else { throw new IOException("unexpected REQUEST on client"); }
            }
            bodyOut.flush();
            if (status == null) throw new EOFException("no RESPONSE received");
            return new Result(status, respHs, bodyBytes);
        }
    }
}
