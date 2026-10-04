package com.bhttp;

import com.bhttp.client.ClientSession;
import com.bhttp.proto.HeaderTable;
import java.io.OutputStream;
import java.util.List;

public final class BCurl {
    private BCurl() {}
    public static void main(String[] args) throws Exception {
        boolean verbose = false; boolean head = false;
        int ai = 0;
        while (ai < args.length && args[ai].startsWith("-")) {
            if (args[ai].equals("-v")) verbose = true;
            else if (args[ai].equals("--head")) head = true;
            else { System.err.println("usage: bcurl [-v] [--head] host:port/path"); System.exit(2); return; }
            ai++;
        }
        if (args.length - ai != 1) { System.err.println("usage: bcurl [-v] [--head] host:port/path"); System.exit(2); return; }
        String url = args[ai];
        if (url.startsWith("http://")) url = url.substring(7);
        int slash = url.indexOf('/');
        String hp = slash >= 0 ? url.substring(0, slash) : url;
        String path = slash >= 0 ? url.substring(slash) : "/";
        int colon = hp.lastIndexOf(':');
        if (colon < 0) { System.err.println("need host:port/path"); System.exit(2); return; }
        String host = hp.substring(0, colon);
        int port = Integer.parseInt(hp.substring(colon + 1));
        OutputStream bodyOut = System.out;
        ClientSession.Result r;
        try { r = ClientSession.fetch(host, port, path, head, verbose, bodyOut); }
        catch (Exception e) { System.err.println("bcurl: " + e.getMessage()); System.exit(2); return; }
        bodyOut.flush();
        List<HeaderTable.Header> hs = r.headers();
        if (verbose) System.err.println("status=" + r.status() + " bodyBytes=" + r.bodyBytes());
        if (r.status() >= 400) System.exit(1);
    }
}
