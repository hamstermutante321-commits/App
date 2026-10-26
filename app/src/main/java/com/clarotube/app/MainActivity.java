package com.clarotube.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

/** Owns the browser lifecycle. Remote pages never receive a native JavaScript bridge. */
public final class MainActivity extends Activity implements BrowserChrome.Listener {
    private static final String STATE_BROWSER = "browser";
    private final AtomicInteger blockedRequests = new AtomicInteger();
    private SharedPreferences preferences;
    private volatile boolean protectionEnabled;
    private volatile boolean destroyed;
    private BrowserChrome chrome;
    private WebView webView;
    private FrameLayout root;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private String protectionScript;
    private String lastTrustedUrl = UrlPolicy.HOME;
    private boolean pageFailed;
    private boolean rendererGone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("clarotube", MODE_PRIVATE);
        protectionEnabled = preferences.getBoolean("protection", true);
        try (InputStream input = getAssets().open("protection.js")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            protectionScript = bytes.toString(StandardCharsets.UTF_8.name());
        } catch (IOException exception) {
            protectionEnabled = false;
            protectionScript = "";
            Toast.makeText(this, "No se pudieron cargar los filtros. Reinicia la app.", Toast.LENGTH_LONG).show();
        }
        root = new FrameLayout(this);
        chrome = new BrowserChrome(this, this);
        root.addView(chrome.getRoot(), new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        createBrowser();
        chrome.setProtection(protectionEnabled, 0);

        String incoming = savedInstanceState == null ? incomingUrl(getIntent()) : null;
        Bundle browserState = savedInstanceState == null ? null : savedInstanceState.getBundle(STATE_BROWSER);
        if (incoming != null) {
            navigate(incoming);
        } else if (browserState == null || webView.restoreState(browserState) == null
                || !UrlPolicy.isYouTubePage(webView.getUrl())) {
            navigate(UrlPolicy.HOME);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
        if (!preferences.getBoolean("welcome_seen", false)) {
            new AlertDialog.Builder(this)
                    .setTitle("Bienvenido a ClaroTube")
                    .setMessage("Busca o pega un enlace para ver YouTube aquí. La protección intenta filtrar anuncios dentro de ClaroTube.\n\nYouTube cambia con frecuencia: algunos anuncios pueden aparecer y algunos videos pueden requerir desactivar la protección. La app oficial no se modifica.")
                    .setPositiveButton("Empezar", (dialog, which) -> preferences.edit().putBoolean("welcome_seen", true).apply())
                    .show();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void createBrowser() {
        webView = new WebView(this);
        webView.setBackgroundColor(0xff101513);
        webView.setSaveEnabled(true);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSafeBrowsingEnabled(true);
        settings.setBuiltInZoomControls(false);
        // Keep the platform's actual mobile UA; do not impersonate another browser.
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        webView.setWebViewClient(new BrowserClient());
        webView.setWebChromeClient(new PlayerChrome());
        chrome.getContentContainer().addView(webView, 0, new FrameLayout.LayoutParams(-1, -1));

        ServiceWorkerController controller = ServiceWorkerController.getInstance();
        controller.getServiceWorkerWebSettings().setAllowContentAccess(false);
        controller.getServiceWorkerWebSettings().setAllowFileAccess(false);
        // Do not retain the Activity in the process-wide service worker controller.
        controller.setServiceWorkerClient(createServiceWorkerClient(preferences, blockedRequests));
    }

    private static ServiceWorkerClient createServiceWorkerClient(
            SharedPreferences browserPreferences, AtomicInteger requestCounter) {
        return new ServiceWorkerClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                if (browserPreferences.getBoolean("protection", true)
                        && AdBlockRules.shouldBlock(request.getUrl().toString())) {
                    requestCounter.incrementAndGet();
                    return emptyResponse();
                }
                return null;
            }
        };
    }

    private static WebResourceResponse emptyResponse() {
        return new WebResourceResponse("text/plain", "UTF-8", 204, "No Content",
                Collections.singletonMap("Cache-Control", "no-store"), new ByteArrayInputStream(new byte[0]));
    }

    private void injectProtection(WebView view) {
        if (destroyed || rendererGone || !UrlPolicy.isYouTubePage(view.getUrl())) return;
        if (protectionEnabled) {
            view.evaluateJavascript("window.__clarotubeProtection=true;\n" + protectionScript, null);
        } else {
            view.evaluateJavascript("window.__clarotubeProtection=false;if(typeof window.__clarotubeCleanup==='function'){window.__clarotubeCleanup();}", null);
        }
        chrome.setProtection(protectionEnabled, blockedRequests.get());
    }

    private void navigate(String url) {
        if (rendererGone) {
            recreate();
            return;
        }
        if (!UrlPolicy.isYouTubePage(url)) {
            Toast.makeText(this, "Introduce una búsqueda o un enlace HTTPS de YouTube.", Toast.LENGTH_LONG).show();
            return;
        }
        lastTrustedUrl = url;
        chrome.clearError();
        webView.loadUrl(url);
    }

    @Override
    public void onSubmit(String input) {
        String url = UrlPolicy.resolveInput(input);
        if (url == null) {
            Toast.makeText(this, "Usa una búsqueda o un enlace de YouTube válido.", Toast.LENGTH_LONG).show();
        } else {
            navigate(url);
        }
    }

    @Override public void onHome() { navigate(UrlPolicy.HOME); }
    @Override public void onBack() { handleBack(); }
    @Override public void onReload() {
        if (rendererGone) { recreate(); return; }
        chrome.clearError();
        if (UrlPolicy.isYouTubePage(webView.getUrl())) webView.reload();
        else navigate(lastTrustedUrl);
    }

    @Override
    public void onToggleProtection() {
        protectionEnabled = !protectionEnabled;
        preferences.edit().putBoolean("protection", protectionEnabled).apply();
        chrome.setProtection(protectionEnabled, blockedRequests.get());
        injectProtection(webView);
        onReload();
        Toast.makeText(this, protectionEnabled ? "Protección activada" : "Protección desactivada", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onOpenSettings() {
        new AlertDialog.Builder(this)
                .setTitle("Tu espacio, tus controles")
                .setItems(new String[]{"Cómo funciona la protección", "Abrir página en el navegador", "Borrar datos de navegación", "Acerca de ClaroTube"},
                        (dialog, which) -> {
                            if (which == 0) showProtectionHelp();
                            if (which == 1) openInBrowser(UrlPolicy.isYouTubePage(webView.getUrl()) ? webView.getUrl() : UrlPolicy.HOME);
                            if (which == 2) confirmClearData();
                            if (which == 3) new AlertDialog.Builder(this).setTitle("ClaroTube · 0.1.0")
                                    .setMessage("Cliente independiente de YouTube para Android.\n\nSin registro propio ni analíticas añadidas. YouTube recibe las solicitudes de reproducción y mantiene su propia política de privacidad.\n\nNo está afiliado a YouTube ni a Google. Los filtros son experimentales y pueden necesitar actualizaciones.")
                                    .setPositiveButton("Entendido", null).show();
                        }).setNegativeButton("Cerrar", null).show();
    }

    private void showProtectionHelp() {
        new AlertDialog.Builder(this).setTitle("Protección experimental")
                .setMessage("Filtra peticiones publicitarias conocidas, oculta espacios de anuncios e intenta omitir anuncios identificados por el reproductor.\n\nEl contador muestra peticiones filtradas durante esta sesión; no equivale al número de anuncios de video.\n\nSolo funciona dentro de ClaroTube. No bloquea anuncios en la app oficial ni anuncios integrados por el creador en su video.\n\nSi un video falla, desactiva el escudo y recarga. El inicio de sesión, videos restringidos y reproducción protegida pueden no funcionar en una vista web.")
                .setPositiveButton("Entendido", null).show();
    }

    private void confirmClearData() {
        new AlertDialog.Builder(this).setTitle("¿Borrar datos de navegación?")
                .setMessage("Se borrarán cookies, historial, caché y datos locales de YouTube en ClaroTube. Tendrás que volver a iniciar sesión si corresponde.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Borrar", (dialog, which) -> {
                    if (rendererGone) { recreate(); return; }
                    webView.stopLoading();
                    CookieManager.getInstance().removeAllCookies(removed -> {
                        if (destroyed) return;
                        CookieManager.getInstance().flush();
                        WebStorage.getInstance().deleteAllData();
                        webView.clearCache(true);
                        webView.clearHistory();
                        blockedRequests.set(0);
                        navigate(UrlPolicy.HOME);
                        Toast.makeText(this, "Datos de navegación borrados", Toast.LENGTH_SHORT).show();
                    });
                }).show();
    }

    private void openInBrowser(String url) {
        try {
            Uri uri = Uri.parse(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return;
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(intent);
        } catch (ActivityNotFoundException | IllegalArgumentException exception) {
            Toast.makeText(this, "No se encontró un navegador para abrir este enlace.", Toast.LENGTH_LONG).show();
        }
    }

    private String incomingUrl(Intent intent) {
        if (intent == null) return null;
        String input = Intent.ACTION_SEND.equals(intent.getAction())
                ? intent.getStringExtra(Intent.EXTRA_TEXT) : null;
        if (input == null) return null;
        // Shared descriptions often include text before the URL; only accept YouTube links.
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("https://[^\\s<>]+", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(input);
        while (match.find()) {
            String candidate = match.group();
            if (UrlPolicy.isYouTubePage(candidate)) return UrlPolicy.resolveInput(candidate);
        }
        return UrlPolicy.isYouTubePage(input.trim()) ? UrlPolicy.resolveInput(input.trim()) : null;
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String incoming = incomingUrl(intent);
        if (incoming != null) navigate(incoming);
    }

    // API 33+ uses the platform OnBackInvokedDispatcher registered in onCreate.
    // This override only supports the legacy dispatch path on Android 8–12.
    @SuppressLint("GestureBackNavigation")
    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() { handleBack(); }
    private void handleBack() {
        if (fullscreenView != null) hideFullscreen();
        else if (!rendererGone && webView.canGoBack()) webView.goBack();
        else finish();
    }

    private void hideFullscreen() {
        if (fullscreenView == null) return;
        root.removeView(fullscreenView);
        fullscreenView = null;
        chrome.getRoot().setVisibility(View.VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        WebChromeClient.CustomViewCallback callback = fullscreenCallback;
        fullscreenCallback = null;
        if (callback != null) callback.onCustomViewHidden();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        if (!rendererGone) {
            Bundle browserState = new Bundle();
            webView.saveState(browserState);
            outState.putBundle(STATE_BROWSER, browserState);
        }
        super.onSaveInstanceState(outState);
    }

    @Override protected void onPause() {
        if (webView != null && !rendererGone) {
            webView.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(v){v.pause();});", null);
            webView.onPause();
        }
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (webView != null && !rendererGone) {
            webView.onResume();
            chrome.setProtection(protectionEnabled, blockedRequests.get());
        }
    }

    @Override protected void onDestroy() {
        destroyed = true;
        hideFullscreen();
        if (webView != null && !rendererGone) {
            webView.stopLoading();
            chrome.getContentContainer().removeView(webView);
            webView.destroy();
        }
        super.onDestroy();
    }

    private final class BrowserClient extends WebViewClient {
        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (!request.isForMainFrame()) return !"https".equalsIgnoreCase(request.getUrl().getScheme());
            if (UrlPolicy.isYouTubePage(url)) return false;
            if (request.hasGesture()) openInBrowser(url);
            else Toast.makeText(MainActivity.this, "Este enlace necesita abrirse en el navegador.", Toast.LENGTH_SHORT).show();
            return true;
        }

        @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (protectionEnabled && !request.isForMainFrame() && AdBlockRules.shouldBlock(request.getUrl().toString())) {
                blockedRequests.incrementAndGet();
                view.post(() -> {
                    if (!destroyed) chrome.setProtection(protectionEnabled, blockedRequests.get());
                });
                return emptyResponse();
            }
            return null;
        }

        @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
            pageFailed = false;
            chrome.clearError();
            chrome.setLoading(5);
            if (UrlPolicy.isYouTubePage(url)) lastTrustedUrl = url;
            injectProtection(view);
        }

        @Override public void onPageCommitVisible(WebView view, String url) { injectProtection(view); }
        @Override public void onPageFinished(WebView view, String url) {
            chrome.setLoading(100);
            chrome.setNavigation(view.canGoBack());
            injectProtection(view);
            if (!pageFailed) chrome.clearError();
        }

        @Override public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            if (UrlPolicy.isYouTubePage(url)) lastTrustedUrl = url;
            chrome.setNavigation(view.canGoBack());
            injectProtection(view);
        }

        @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) showLoadError("No se pudo conectar con YouTube. Revisa tu conexión y vuelve a intentarlo.");
        }

        @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
            if (request.isForMainFrame() && response.getStatusCode() >= 400) {
                showLoadError("YouTube respondió con un error (" + response.getStatusCode() + "). Puedes reintentar o abrir la página en tu navegador.");
            }
        }

        @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
            if (error.getUrl().equals(view.getUrl())) {
                showLoadError("No se pudo verificar la conexión segura. Comprueba la fecha del dispositivo y tu conexión.");
            }
        }

        @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            rendererGone = true;
            hideFullscreen();
            chrome.getContentContainer().removeView(view);
            view.destroy();
            pageFailed = true;
            chrome.setLoading(100);
            chrome.showError("El reproductor se cerró. Toca Reintentar para reiniciarlo.", MainActivity.this::recreate);
            return true;
        }

        private void showLoadError(String message) {
            pageFailed = true;
            chrome.setLoading(100);
            chrome.showError(message, MainActivity.this::onReload);
        }
    }

    private final class PlayerChrome extends WebChromeClient {
        @Override public void onProgressChanged(WebView view, int progress) {
            chrome.setLoading(progress);
            if (progress >= 10) injectProtection(view);
        }

        @Override public void onReceivedTitle(WebView view, String title) {
            chrome.setPageTitle(title == null ? "YouTube" : title);
        }

        @Override public void onShowCustomView(View view, CustomViewCallback callback) {
            if (fullscreenView != null) { callback.onCustomViewHidden(); return; }
            fullscreenView = view;
            fullscreenCallback = callback;
            chrome.getRoot().setVisibility(View.GONE);
            root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        @Override public void onHideCustomView() { hideFullscreen(); }
    }
}
