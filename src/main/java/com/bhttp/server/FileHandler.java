package com.bhttp.server;

import com.bhttp.proto.ProtocolException;
import com.bhttp.util.IOUtil;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

public final class FileHandler {
    private final Path root;
    private final Path realRoot;
    public FileHandler(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        this.realRoot = this.root.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }
    public Path getRoot() { return root; }
    public record Resolved(Path file, boolean exists, boolean isDir) {}
    public Path resolve(String rawPath) throws ProtocolException, IOException {
        if (rawPath == null || rawPath.isEmpty() || !rawPath.startsWith("/")) throw new ProtocolException("path must start with /");
        if (rawPath.indexOf(0) >= 0) throw new ProtocolException("path contains NUL");
        if (rawPath.length() > 2048) throw new ProtocolException("path too long");
        String noFrag = rawPath;
        int h = noFrag.indexOf('#'); if (h >= 0) noFrag = noFrag.substring(0, h);
        int q = noFrag.indexOf('?'); if (q >= 0) noFrag = noFrag.substring(0, q);
        if (noFrag.isEmpty()) noFrag = "/";
        String decoded = IOUtil.percentDecode(noFrag);
        if (decoded.indexOf(0) >= 0) throw new ProtocolException("path contains NUL");
        String rel = decoded.startsWith("/") ? decoded.substring(1) : decoded;
        Path p = root.resolve(rel).normalize();
        if (!p.startsWith(root)) throw new ProtocolException("path escapes root");
        if (Files.isSymbolicLink(p)) {
            Path real = p.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path target = real.toAbsolutePath().normalize();
            boolean inside;
            try { inside = target.startsWith(realRoot); } catch (Exception e) { inside = false; }
            if (!inside) throw new ProtocolException("symlink escapes root");
        }
        if (Files.isDirectory(p)) p = p.resolve("index.html");
        else if (decoded.endsWith("/")) p = p.resolve("index.html");
        if (!p.toAbsolutePath().normalize().startsWith(root)) throw new ProtocolException("path escapes root");
        return p;
    }
    public InputStream open(Path file) throws IOException { return Files.newInputStream(file, StandardOpenOption.READ); }
}
