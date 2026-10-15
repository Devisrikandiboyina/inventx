package org.enggis.enventx.api.service;

import java.util.regex.Pattern;

/**
 * Stores free text as plain text: markup is removed on the way in, so it can never reach another user as HTML
 * regardless of how a client renders it.
 */
public final class TextSanitizer {

    private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern ACTIVE_CONTENT =
            Pattern.compile("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>");
    private static final Pattern TAG = Pattern.compile("(?s)</?[A-Za-z!?][^>]*>");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");

    private TextSanitizer() {
    }

    /** Null stays null; the result is stripped and contains no tags or control characters. */
    public static String clean(String text) {
        if (text == null) {
            return null;
        }
        var result = text.replace("\r\n", "\n").replace('\r', '\n');
        result = COMMENT.matcher(result).replaceAll("");
        result = ACTIVE_CONTENT.matcher(result).replaceAll("");
        result = TAG.matcher(result).replaceAll("");
        result = CONTROL.matcher(result).replaceAll("");
        return result.strip();
    }

    /** Like {@link #clean} but a result that is empty (for example input that was only markup) is a 400. */
    public static String required(String text) {
        var result = clean(text);
        if (result == null || result.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "TEXT_REQUIRED");
        }
        return result;
    }
}