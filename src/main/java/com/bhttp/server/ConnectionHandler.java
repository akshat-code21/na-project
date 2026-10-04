package com.bhttp.server;

import com.bhttp.proto.*;
import com.bhttp.util.Hexdump;
import com.bhttp.util.IOUtil;
import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public final class ConnectionHandler implements Runnable {
    public static final int CHUNK = 16 * 1024;
    private final Socket sock;
    private final FileHandler files;
    private final boolean verbose;
    public ConnectionHandler(Socket sock, FileHandler files, boolean verbose) { this.sock = sock; this.files = files; this.verbose = verbose; }
    @Override public void run() {
        try (Socket s = sock; DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream())); DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()))) {
            while (true) {
                Frame f;
                try { f = FrameIO.readFrame(in); }
                catch (ProtocolException e) { trySend400(out, 1, e.getMessage()); return; }
                if (f == null) return;
                if (verbose) System.err.print(Hexdump.dump("<-", f));
                if (!FrameType.isKnown(f.type())) continue;
                if (f.type() != FrameType.REQUEST) { trySend400(out, f.requestId(), "expected REQUEST"); continue; }
                handleRequest(f, out);
            }
        } catch (Exception e) { System.err.println("conn closed: " + e.getMessage()); }
    }
    private void handleRequest(Frame f, DataOutputStream out) throws IOException {
        Request.Decoded req;
        try { req = Request.decode(f.payload()); }
        catch (ProtocolException e) { trySend400(out, f.requestId(), e.getMessage()); return; }
        boolean head = req.method() == Request.METHOD_HEAD;
        Path file;
        try { file = files.resolve(req.path()); }
        catch (ProtocolException e) { sendStatus(out, f.requestId(), 400, "text/plain; charset=utf-8", ("Bad Request: "+e.getMessage()+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8), head); return; }
        catch (IOException e) { sendStatus(out, f.requestId(), 500, "text/plain; charset=utf-8", "Internal Error\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), head); return; }
        if (!Files.isRegularFile(file)) { sendStatus(out, f.requestId(), 404, "text/plain; charset=utf-8", "Not Found\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), head); return; }
        long size;
        try { size = Files.size(file); } catch (IOException e) { sendStatus(out, f.requestId(), 500, "text/plain; charset=utf-8", "Internal Error\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), head); return; }
        String mime = MimeTypes.forName(file.getFileName().toString());
        List<HeaderTable.Header> hs = new ArrayList<>();
        hs.add(new HeaderTable.Header("content-type", mime));
        hs.add(new HeaderTable.Header("content-length", Long.toString(size)));
        hs.add(new HeaderTable.Header("date", IOUtil.imfDate()));
        hs.add(new HeaderTable.Header("server", "bserve/1.0"));
        byte[] rp;
        try { rp = Response.encode(200, hs); } catch (ProtocolException e) { return; }
        boolean noBody = head || size == 0;
        Frame resp = new Frame(FrameType.RESPONSE, noBody ? Flags.END_STREAM : 0, f.requestId(), rp);
        try { FrameIO.writeFrame(out, resp); } catch (IOException e) { return; }
        if (verbose) System.err.print(Hexdump.dump("->", resp));
        if (noBody) return;
        try (InputStream fis = files.open(file)) {
            byte[] buf = new byte[CHUNK];
            long left = size;
            while (left > 0) {
                int n = fis.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) break;
                left -= n;
                byte[] chunk = new byte[n]; System.arraycopy(buf, 0, chunk, 0, n);
                int flags = (left == 0) ? Flags.END_STREAM : 0;
                Frame d = new Frame(FrameType.DATA, flags, f.requestId(), chunk);
                FrameIO.writeFrame(out, d);
                if (verbose) System.err.print(Hexdump.dump("->", d));
            }
        } catch (IOException e) { return; }
    }
    private void trySend400(DataOutputStream out, int id, String msg) {
        int useId = (id == 0) ? 1 : id;
        try { sendStatus(out, useId, 400, "text/plain; charset=utf-8", ("Bad Request: "+msg+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8), false); }
        catch (Exception ignored) {}
    }
    private void sendStatus(DataOutputStream out, int id, int status, String ctype, byte[] body, boolean headOnly) throws IOException {
        List<HeaderTable.Header> hs = new ArrayList<>();
        hs.add(new HeaderTable.Header("content-type", ctype));
        hs.add(new HeaderTable.Header("content-length", Long.toString(headOnly ? 0 : body.length)));
        hs.add(new HeaderTable.Header("date", IOUtil.imfDate()));
        hs.add(new HeaderTable.Header("server", "bserve/1.0"));
        byte[] rp;
        try { rp = Response.encode(status, hs); } catch (ProtocolException e) { return; }
        boolean noBody = headOnly || body.length == 0;
        // For error with body: RESPONSE END_STREAM=0 then single DATA END_STREAM=1
        Frame resp = new Frame(FrameType.RESPONSE, noBody ? Flags.END_STREAM : 0, id, rp);
        FrameIO.writeFrame(out, resp);
        if (verbose) System.err.print(Hexdump.dump("->", resp));
        if (!noBody) { Frame d = new Frame(FrameType.DATA, Flags.END_STREAM, id, body); FrameIO.writeFrame(out, d); if (verbose) System.err.print(Hexdump.dump("->", d)); }
    }
}
