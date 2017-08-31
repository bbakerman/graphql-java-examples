package com.graphql.example.proxy.relay;

import graphql.relay.ConnectionCursor;
import graphql.relay.DefaultConnectionCursor;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This uses an encoding of page # plus full offset from the page forward
 */
class CursorPageAndOffset {
    private static final java.util.Base64.Encoder encoder = java.util.Base64.getEncoder();
    private static final java.util.Base64.Decoder decoder = java.util.Base64.getDecoder();
    private final static Pattern pagePattern = Pattern.compile("^page=([0-9]*)");
    private final static Pattern offsetPattern = Pattern.compile(".*;offset=([0-9]*)");

    int page;
    int offset;

    CursorPageAndOffset(int page, int offset) {
        this.page = page;
        this.offset = offset;
    }

    int getPage() {
        return page;
    }

    int getOffset() {
        return offset;
    }

    public static CursorPageAndOffset fromCursor(String cursor) {
        String s = decode(cursor);
        Matcher matcher = pagePattern.matcher(s);
        if (!matcher.find()) {
            throwInvalidCursor(s);
        }
        String page = matcher.group(1);

        matcher = offsetPattern.matcher(s);
        if (!matcher.find()) {
            throwInvalidCursor(s);
        }
        String offset = matcher.group(1);
        return new CursorPageAndOffset(Integer.parseInt(page), Integer.parseInt(offset));
    }

    ConnectionCursor toConnectionCursor() {
        return new DefaultConnectionCursor(encode("page=" + page + ";offset=" + offset));
    }

    private String encode(String s) {
        return encoder.encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    static private String decode(String s) {
        return new String(decoder.decode(s), StandardCharsets.UTF_8);
    }

    private static void throwInvalidCursor(String cursor) {
        throw new IllegalArgumentException("Invalid paged cursor provided : " + cursor);
    }
}
