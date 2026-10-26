package com.clarotube.app;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Conservative network rules for ad-only hosts and YouTube ad endpoints. */
public final class AdBlockRules {
    private AdBlockRules() {
    }

    public static boolean shouldBlock(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        final URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            return false;
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || uri.getRawUserInfo() != null) {
            return false;
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http")) {
            return false;
        }
        int port = uri.getPort();
        if (port != -1 && port != (scheme.equals("https") ? 443 : 80)) {
            return false;
        }

        host = host.toLowerCase(Locale.ROOT);
        if (isHostOrSubdomain(host, "doubleclick.net")
                || isHostOrSubdomain(host, "googlesyndication.com")
                || isHostOrSubdomain(host, "googleadservices.com")
                || isHostOrSubdomain(host, "adservice.google.com")) {
            return true;
        }

        String path = uri.getRawPath();
        if (path == null) {
            return false;
        }
        if (isHostOrSubdomain(host, "youtube.com")) {
            return atOrBelow(path, "/pagead")
                    || atOrBelow(path, "/api/stats/ads")
                    || atOrBelow(path, "/get_midroll_info")
                    || atOrBelow(path, "/youtubei/v1/ads");
        }
        return isHostOrSubdomain(host, "google.com") && atOrBelow(path, "/pagead");
    }

    private static boolean isHostOrSubdomain(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    private static boolean atOrBelow(String path, String endpoint) {
        return path.equals(endpoint) || path.startsWith(endpoint + "/");
    }
}
