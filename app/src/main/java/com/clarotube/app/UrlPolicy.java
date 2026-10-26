package com.clarotube.app;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates navigation targets and converts shared YouTube links to mobile URLs. */
public final class UrlPolicy {
    public static final String HOME = "https://m.youtube.com/";

    private static final Set<String> PAGE_HOSTS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")));
    private static final Set<String> SHORT_LINK_PARAMETERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "t", "start", "time_continue", "list", "index")));
    private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private UrlPolicy() {
    }

    public static boolean isYouTubePage(String url) {
        URI uri = parsePage(url);
        return uri != null && PAGE_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT));
    }

    public static String resolveInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return HOME;
        }
        String value = input.trim();
        boolean absolute = SCHEME.matcher(value).find();
        String candidate;
        if (absolute) {
            candidate = value;
        } else if (value.startsWith("//")) {
            candidate = "https:" + value;
        } else if (looksLikeAddress(value)) {
            candidate = "https://" + value;
        } else {
            try {
                return HOME + "results?search_query=" + URLEncoder.encode(value, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException ignored) {
                return null; // UTF-8 is required by every Android and Java runtime.
            }
        }

        URI uri = parsePage(candidate);
        if (uri == null) {
            return null;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!PAGE_HOSTS.contains(host)) {
            return null;
        }

        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        // These YouTube pages can immediately forward navigation to another site.
        if (path.startsWith("//") || atOrBelow(path, "/redirect")
                || atOrBelow(path, "/attribution_link")) {
            return null;
        }

        String query = uri.getRawQuery();
        if (host.equals("youtu.be")) {
            String id = path.substring(1);
            if (id.endsWith("/")) {
                id = id.substring(0, id.length() - 1);
            }
            if (!VIDEO_ID.matcher(id).matches()) {
                return null;
            }
            path = "/watch";
            query = "v=" + id + retainedShortLinkParameters(query);
        }

        StringBuilder normalized = new StringBuilder("https://m.youtube.com").append(path);
        if (query != null && !query.isEmpty()) {
            normalized.append('?').append(query);
        }
        String fragment = uri.getRawFragment();
        if (fragment != null && !fragment.isEmpty()) {
            normalized.append('#').append(fragment);
        }
        try {
            return new URI(normalized.toString()).toASCIIString();
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private static URI parsePage(String url) {
        if (url == null) {
            return null;
        }
        final URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            return null;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
            return null;
        }
        return uri;
    }

    private static boolean looksLikeAddress(String value) {
        if (containsWhitespace(value)) {
            return false;
        }
        int end = value.length();
        for (char separator : new char[] {'/', '?', '#'}) {
            int at = value.indexOf(separator);
            if (at >= 0 && at < end) {
                end = at;
            }
        }
        String authority = value.substring(0, end);
        return authority.indexOf('.') >= 0 || authority.equalsIgnoreCase("localhost")
                || authority.startsWith("[");
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String retainedShortLinkParameters(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return "";
        }
        StringBuilder retained = new StringBuilder();
        for (String part : rawQuery.split("&")) {
            int equals = part.indexOf('=');
            String name = equals < 0 ? part : part.substring(0, equals);
            if (SHORT_LINK_PARAMETERS.contains(name)) {
                retained.append('&').append(part);
            }
        }
        return retained.toString();
    }

    private static boolean atOrBelow(String path, String endpoint) {
        return path.equals(endpoint) || path.startsWith(endpoint + "/");
    }
}
