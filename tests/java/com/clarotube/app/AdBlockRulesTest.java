package com.clarotube.app;

/** Standalone checks: run with java, without Android or JUnit. */
public final class AdBlockRulesTest {
    private static int assertions;

    public static void main(String[] args) {
        blocksKnownAdTraffic();
        rejectsMisleadingHosts();
        preservesPlaybackAndOrdinaryResources();
        handlesBadInput();
        System.out.println("AdBlockRulesTest: " + assertions + " assertions passed");
    }

    private static void blocksKnownAdTraffic() {
        blocked("https://doubleclick.net/");
        blocked("https://googleads.g.doubleclick.net/pagead/ads?client=ca-pub-1");
        blocked("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js");
        blocked("https://www.googleadservices.com/pagead/aclk");
        blocked("https://www.youtube.com/pagead/interaction/?ad_type=video");
        blocked("https://m.youtube.com/youtubei/v1/ads");
        blocked("https://www.youtube.com/api/stats/ads");
    }

    private static void rejectsMisleadingHosts() {
        allowed("https://googleads.g.doubleclick.net.evil.example/pagead/ads");
        allowed("https://notdoubleclick.net/pagead/ads");
        allowed("https://doubleclick.net.evil.example/pagead/ads");
        allowed("https://evil.example/doubleclick.net/pagead/ads");
        allowed("https://www.youtube.com.evil.example/pagead/interaction/");
        allowed("https://youtube.com%2Eevil.example/pagead/interaction/");
    }

    private static void preservesPlaybackAndOrdinaryResources() {
        allowed("https://rr1---sn.example.googlevideo.com/videoplayback?itag=18");
        allowed("https://www.youtube.com/youtubei/v1/player?key=abc");
        allowed("https://www.youtube.com/youtubei/v1/ads-extra");
        allowed("https://www.youtube.com/pageadvertisement");
        allowed("https://www.youtube.com/watch?v=ABC123");
        allowed("https://m.youtube.com/shorts/ABC123");
        allowed("https://i.ytimg.com/vi/ABC123/default.jpg");
        allowed("https://www.youtube.com/s/player/abcd/base.js");
        allowed("https://www.youtube.com/s/desktop/abcd/cssbin/www-main.css");
        allowed("https://accounts.google.com/ServiceLogin");
    }

    private static void handlesBadInput() {
        allowed(null);
        allowed("");
        allowed("not a URL");
        allowed("https://");
        allowed("javascript:alert(1)");
    }

    private static void blocked(String url) {
        verify(true, url);
    }

    private static void allowed(String url) {
        verify(false, url);
    }

    private static void verify(boolean expected, String url) {
        assertions++;
        boolean actual = AdBlockRules.shouldBlock(url);
        if (actual != expected) {
            throw new AssertionError("shouldBlock(" + url + ") expected " + expected + " but was " + actual);
        }
    }
}
