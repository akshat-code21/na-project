package com.bhttp;

import com.bhttp.client.ClientSession;
import com.bhttp.proto.*;
import com.bhttp.server.ConnectionHandler;
import com.bhttp.server.FileHandler;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class IntegrationTest {
    static class Server implements AutoCloseable {
        ServerSocket ss; ExecutorService pool; FileHandler files; Thread acc;
        Server(Path root) throws Exception {
            files = new FileHandler(root);
            ss = new ServerSocket(0);
            pool = Executors.newCachedThreadPool();
            acc = new Thread(() -> { while (!ss.isClosed()) { try { Socket s = ss.accept(); pool.submit(new ConnectionHandler(s, files, false)); } catch (Exception ignored) {} } });
            acc.setDaemon(true); acc.start();
        }
        int port() { return ss.getLocalPort(); }
        public void close() throws Exception { ss.close(); pool.shutdownNow(); }
    }
    static Path freshRoot() throws Exception {
        Path d = Files.createTempDirectory("bhttp");
        Files.writeString(d.resolve("index.html"), "<h1>hi</h1>", StandardCharsets.UTF_8);
        Files.writeString(d.resolve("hello.txt"), "hello", StandardCharsets.UTF_8);
        return d;
    }
    @Test public void get200() throws Exception {
        Path root = freshRoot();
        try (Server sv = new Server(root)) {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            var r = ClientSession.fetch("127.0.0.1", sv.port(), "/hello.txt", false, false, body);
            assertEquals(200, r.status());
            assertEquals("hello", body.toString(StandardCharsets.UTF_8));
        }
    }
    @Test public void rootMapsIndex() throws Exception {
        Path root = freshRoot();
        try (Server sv = new Server(root)) {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            var r = ClientSession.fetch("127.0.0.1", sv.port(), "/", false, false, body);
            assertEquals(200, r.status());
            assertTrue(body.toString(StandardCharsets.UTF_8).contains("hi"));
        }
    }
    @Test public void notFound404() throws Exception {
        Path root = freshRoot();
        try (Server sv = new Server(root)) {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            var r = ClientSession.fetch("127.0.0.1", sv.port(), "/nope", false, false, body);
            assertEquals(404, r.status());
        }
    }
    @Test public void traversal400() throws Exception {
        Path root = freshRoot();
        try (Server sv = new Server(root)) {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            var r = ClientSession.fetch("127.0.0.1", sv.port(), "/../secret", false, false, body);
            assertEquals(400, r.status());
        }
    }
    @Test public void headNoBody() throws Exception {
        Path root = freshRoot();
        try (Server sv = new Server(root)) {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            var r = ClientSession.fetch("127.0.0.1", sv.port(), "/hello.txt", true, false, body);
            assertEquals(200, r.status());
            assertEquals(0, body.size());
        }
    }
    @Test public void keepAliveTwoRequestsOneSocket() throws Exception {
        Path root = freshRoot();
        try (Server sv = new Server(root)) {
            try (Socket s = new Socket("127.0.0.1", sv.port())) {
                s.setSoTimeout(5000);
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
                for (int id : new int[]{1, 3}) {
                    byte[] rp = Request.encode(1, "/hello.txt", List.of(new HeaderTable.Header("host", "h")));
                    FrameIO.writeFrame(out, new Frame(FrameType.REQUEST, Flags.END_STREAM | Flags.END_HEADERS, id, rp));
                    boolean end = false; boolean gotResp = false; ByteArrayOutputStream b = new ByteArrayOutputStream();
                    while (!end) {
                        Frame f = FrameIO.readFrame(in);
                        assertNotNull(f); assertEquals(id, f.requestId());
                        if (f.type() == FrameType.RESPONSE) { gotResp = true; assertEquals(200, Response.decode(f.payload()).status()); if ((f.flags() & Flags.END_STREAM) != 0) end = true; }
                        else if (f.type() == FrameType.DATA) { b.write(f.payload()); if ((f.flags() & Flags.END_STREAM) != 0) end = true; }
                    }
                    assertTrue(gotResp); assertEquals("hello", b.toString(StandardCharsets.UTF_8));
                }
            }
        }
    }
    @Test public void unknownFrameSkipped() throws Exception {
        Path root = freshRoot();
        try (Server sv = new Server(root)) {
            try (Socket s = new Socket("127.0.0.1", sv.port())) {
                s.setSoTimeout(5000);
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
                FrameIO.writeFrame(out, new Frame(0x9F, 0x00, 1, new byte[]{1,2,3,4}));
                byte[] rp = Request.encode(1, "/hello.txt", List.of(new HeaderTable.Header("host", "h")));
                FrameIO.writeFrame(out, new Frame(FrameType.REQUEST, Flags.END_STREAM | Flags.END_HEADERS, 1, rp));
                boolean end = false; boolean gotResp = false;
                while (!end) {
                    Frame f = FrameIO.readFrame(in);
                    assertNotNull(f);
                    if (f.type() == FrameType.RESPONSE) { gotResp = true; if ((f.flags() & Flags.END_STREAM) != 0) end = true; }
                    else if (f.type() == FrameType.DATA) { if ((f.flags() & Flags.END_STREAM) != 0) end = true; }
                }
                assertTrue(gotResp);
            }
        }
    }
    @Test public void binarySafe() throws Exception {
        Path root = freshRoot();
        byte[] rnd = new byte[50000]; new Random(7).nextBytes(rnd);
        Files.write(root.resolve("bin.dat"), rnd);
        try (Server sv = new Server(root)) {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            var r = ClientSession.fetch("127.0.0.1", sv.port(), "/bin.dat", false, false, body);
            assertEquals(200, r.status());
            assertArrayEquals(rnd, body.toByteArray());
        }
    }
}
