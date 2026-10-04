package com.bhttp.util;

import com.bhttp.proto.ProtocolException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class IOUtil {
    private IOUtil() {}
    public static String imfDate() {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneId.of("GMT")));
    }
    public static String percentDecode(String s) throws ProtocolException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%') {
                if (i + 2 >= s.length()) throw new ProtocolException("bad percent encoding");
                int hi = Character.digit(s.charAt(i+1), 16);
                int lo = Character.digit(s.charAt(i+2), 16);
                if (hi < 0 || lo < 0) throw new ProtocolException("bad percent encoding");
                sb.append((char)((hi<<4)|lo));
                i += 2;
            } else sb.append(c);
        }
        return sb.toString();
    }
}
