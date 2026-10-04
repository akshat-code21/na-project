package com.bhttp.server;

import java.util.Map;

public final class MimeTypes {
    private MimeTypes() {}
    private static final Map<String,String> MAP = Map.ofEntries(
        Map.entry("html", "text/html; charset=utf-8"),
        Map.entry("htm", "text/html; charset=utf-8"),
        Map.entry("css", "text/css"),
        Map.entry("js", "text/javascript"),
        Map.entry("json", "application/json"),
        Map.entry("txt", "text/plain; charset=utf-8"),
        Map.entry("png", "image/png"),
        Map.entry("jpg", "image/jpeg"),
        Map.entry("jpeg", "image/jpeg"),
        Map.entry("gif", "image/gif"),
        Map.entry("svg", "image/svg+xml"));
    public static String forName(String name) {
        int d = name.lastIndexOf('.');
        if (d < 0) return "application/octet-stream";
        String ext = name.substring(d + 1).toLowerCase(java.util.Locale.ROOT);
        return MAP.getOrDefault(ext, "application/octet-stream");
    }
}
