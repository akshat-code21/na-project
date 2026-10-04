package com.bhttp;

import com.bhttp.server.ConnectionHandler;
import com.bhttp.server.FileHandler;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BServe {
    private BServe() {}
    public static void main(String[] args) throws Exception {
        boolean verbose = false;
        int ai = 0;
        if (args.length > 0 && args[0].equals("-v")) { verbose = true; ai = 1; }
        if (args.length - ai != 2) { System.err.println("usage: bserve [-v] <root> <port>"); System.exit(2); return; }
        Path root = Paths.get(args[ai]);
        int port = Integer.parseInt(args[ai+1]);
        if (!Files.isDirectory(root)) { System.err.println("root not a directory: " + root); System.exit(2); return; }
        FileHandler files = new FileHandler(root);
        final boolean vb = verbose;
        ExecutorService pool = makePool();
        Runtime.getRuntime().addShutdownHook(new Thread(pool::shutdownNow));
        try (ServerSocket ss = new ServerSocket(port)) {
            System.err.println("bserve root=" + root.toAbsolutePath() + " port=" + ss.getLocalPort());
            while (true) {
                Socket s = ss.accept();
                try { s.setSoTimeout(60000); } catch (Exception ignored) {}
                pool.submit(new ConnectionHandler(s, files, vb));
            }
        }
    }
    private static ExecutorService makePool() {
        try { return (ExecutorService) Executors.class.getMethod("newVirtualThreadPerTaskExecutor").invoke(null); }
        catch (Exception e) { return Executors.newCachedThreadPool(); }
    }
}
