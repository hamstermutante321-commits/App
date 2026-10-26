package com.clarotube.app;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Standalone checks: run with java, without Android or JUnit. */
public final class UrlPolicyTest {
    private static int assertions;

    public static void main(String[] args) {
        acceptsOnlyApprovedPages();
        resolvesEmptyAndSearchInput();
        normalizesVideoLinks();
        refusesExternalInput();
        System.out.println("UrlPolicyTest: " + assertions + " assertions passed");
    }

    private static void acceptsOnlyApprovedPages() {
        page(true, "https://youtube.com/watch?v=A");
        page(true, "https://www.youtube.com/watch?v=A");
        page(true, "https://m.youtube.com/shorts/A");
        page(true, "https://music.youtube.com/playlist?list=L");
        page(true, "https://youtu.be/A?t=42");
        page(true, "https://youtube.com:443/watch?v=A");

        page(false, null);
        page(false, "");
        page(false, "not a URL");
        page(false, "https://");
        page(false, "http://youtube.com/watch?v=A");
        page(false, "https://youtube.com:444/watch?v=A");
        page(false, "https://other.youtube.com/watch?v=A");
        page(false, "https://youtu.be.evil.example/A");
        page(false, "https://notyoutube.com/watch?v=A");
        page(false, "https://youtube.com.evil.example/watch?v=A");
        page(false, "https://youtube.com./watch?v=A");
        page(false, "https://youtube.com/%ZZ");
        page(false, "https://youtube.com@evil.example/watch?v=A");
        page(false, "https://evil.example@youtube.com/watch?v=A");
        page(false, "javascript:https://youtube.com/watch?v=A");
    }

    private static void resolvesEmptyAndSearchInput() {
        equal(UrlPolicy.HOME, UrlPolicy.resolveInput(null), "null input goes home");
        equal(UrlPolicy.HOME, UrlPolicy.resolveInput("   "), "blank input goes home");

        URI search = uri(UrlPolicy.resolveInput("  café música & amor 🎵  "));
        mobile(search);
        equal("/results", search.getPath(), "search path");
        equal("café música & amor 🎵", query(search, "search_query"), "UTF-8 search term");
    }

    private static void normalizesVideoLinks() {
        URI watch = uri(UrlPolicy.resolveInput("youtube.com/watch?v=Video123&t=1m23s&list=PL123"));
        mobile(watch);
        equal("/watch", watch.getPath(), "watch path");
        equal("Video123", query(watch, "v"), "video id");
        equal("1m23s", query(watch, "t"), "watch timestamp");
        equal("PL123", query(watch, "list"), "playlist context");

        URI shortLink = uri(UrlPolicy.resolveInput("https://youtu.be/Short123?t=45"));
        mobile(shortLink);
        equal("/watch", shortLink.getPath(), "short link becomes watch URL");
        equal("Short123", query(shortLink, "v"), "short link video id");
        equal("45", query(shortLink, "t"), "short link timestamp");

        URI bareShortLink = uri(UrlPolicy.resolveInput("youtu.be/Bare123"));
        mobile(bareShortLink);
        equal("Bare123", query(bareShortLink, "v"), "bare short link video id");

        URI shorts = uri(UrlPolicy.resolveInput("https://www.youtube.com/shorts/Clip123?t=30"));
        mobile(shorts);
        equal("/shorts/Clip123", shorts.getPath(), "Shorts path");
        equal("30", query(shorts, "t"), "Shorts timestamp");

        URI playlist = uri(UrlPolicy.resolveInput("https://youtube.com/playlist?list=PLxyz"));
        mobile(playlist);
        equal("/playlist", playlist.getPath(), "playlist path");
        equal("PLxyz", query(playlist, "list"), "playlist id");
    }

    private static void refusesExternalInput() {
        invalid("https://example.com/watch?v=A");
        invalid("example.com/watch?v=A");
        invalid("https://youtube.com.evil.example/watch?v=A");
        invalid("https://youtu.be.evil.example/A");
        invalid("https://youtube.com@evil.example/watch?v=A");
        invalid("https://evil.example@youtube.com/watch?v=A");
        invalid("https://youtube.com:444/watch?v=A");
        invalid("javascript:alert(1)");
    }

    private static void page(boolean expected, String url) {
        assertions++;
        boolean actual = UrlPolicy.isYouTubePage(url);
        if (actual != expected) {
            throw new AssertionError("isYouTubePage(" + url + ") expected " + expected + " but was " + actual);
        }
    }

    private static void invalid(String input) {
        equal(null, UrlPolicy.resolveInput(input), "reject " + input);
    }

    private static URI uri(String value) {
        assertions++;
        if (value == null) {
            throw new AssertionError("Expected resolved URL, got null");
        }
        try {
            return URI.create(value);
        } catch (IllegalArgumentException error) {
            throw new AssertionError("Invalid resolved URL: " + value, error);
        }
    }

    private static void mobile(URI value) {
        equal("https", value.getScheme(), "resolved scheme");
        equal("m.youtube.com", value.getHost(), "resolved mobile host");
    }

    private static String query(URI value, String key) {
        String raw = value.getRawQuery();
        if (raw != null) {
            for (String pair : raw.split("&")) {
                String[] parts = pair.split("=", 2);
                if (key.equals(URLDecoder.decode(parts[0], StandardCharsets.UTF_8))) {
                    return parts.length == 2
                            ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                            : "";
                }
            }
        }
        return null;
    }

    private static void equal(Object expected, Object actual, String label) {
        assertions++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }
}
