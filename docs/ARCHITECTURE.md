# ClaroTube — contrato de integración

App Android nativa (Java 17), API mínima 26, paquete `com.clarotube.app`. Sin dependencias de ejecución externas. Reproduce el sitio móvil de YouTube en WebView. Los filtros son experimentales y no garantizan eliminar todos los anuncios. No modifica ni controla la app oficial.

## Propiedad de archivos

- Orquestador: Gradle, manifiesto, `MainActivity.java`, documentación general, CI, empaquetado e integración.
- Interfaz: `BrowserChrome.java`, todos los recursos en `app/src/main/res/`.
- Motor: `AdBlockRules.java`, `UrlPolicy.java`, `app/src/main/assets/protection.js`.
- Pruebas: `tests/`, `tool/`, `docs/TESTING.md`. Puede leer todo; no editar implementación sin coordinación.

No cambiar archivos del proyecto MP3 situado en la carpeta superior. No hacer commits, push, ni cambiar `.git`; lo hace el orquestador.

## API interfaz

`public final class BrowserChrome` con constructor `BrowserChrome(Activity activity, Listener listener)`.

- `View getRoot()`
- `FrameLayout getContentContainer()` — el orquestador inserta aquí WebView.
- `void setProtection(boolean enabled, int blockedRequests)` — solo peticiones bloqueadas, no todos los anuncios.
- `void setLoading(int progress)` — 0..100.
- `void setPageTitle(String title)`
- `void setNavigation(boolean canGoBack)`
- `void showError(String message, Runnable retry)` — superposición nativa sobre WebView.
- `void clearError()`

`public interface Listener` declara `onSubmit(String input)`, `onHome()`, `onBack()`, `onReload()`, `onToggleProtection()`, `onOpenSettings()`.

Tema requerido `@style/AppTheme`, icono `@drawable/ic_launcher`. Interfaz en español, oscura, accesible, sin datos ficticios. Título ClaroTube, acento verde menta, estado de protección, búsqueda o enlace, contenido web y navegación. Explicar en ayuda que la protección solo aplica dentro de ClaroTube.

## API motor (Java sin dependencias Android)

- `public final class AdBlockRules` con `public static boolean shouldBlock(String url)`.
- `public final class UrlPolicy` con `public static boolean isYouTubePage(String url)` y `public static String resolveInput(String input)`.
- `UrlPolicy.HOME` = `https://m.youtube.com/`.
- `isYouTubePage` acepta solo HTTPS y hosts exactos `youtube.com`, `www.youtube.com`, `m.youtube.com`, `music.youtube.com`, `youtu.be`. Nunca aceptar usuario/contraseña, puertos distintos a 443, subdominios arbitrarios o sufijos engañosos.
- `resolveInput`: búsqueda sin URL -> URL de búsqueda móvil con UTF-8; URL YouTube válida -> URL HTTPS móvil normalizada; `youtu.be/ID` -> `/watch?v=ID`; entradas con esquema o dominio externo -> `null`; vacía -> HOME. Puede aceptar `youtube.com/watch?...` y `youtu.be/ID` sin esquema. Conservar videos, Shorts, listas y timestamps.
- Filtros por host exacto/subdominio y endpoints publicitarios concretos; nunca bloquear todo googlevideo.com, youtubei/v1/player, thumbnails, CSS, login ni recursos normales.

## API JavaScript

`protection.js`: IIFE idempotente, defensiva, solo hosts YouTube, solo si `window.__clarotubeProtection === true`. Usa CSS de anuncios, omite anuncios cuando el reproductor indica estado publicitario, y limpia campos publicitarios conocidos de respuestas del reproductor sin tocar `streamingData`, contenido ni consentimiento. No suprimir advertencias antibloqueador ni consentimiento.

- `window.__clarotubeCleanup()` deshace temporizadores, observadores, CSS y hooks al desactivar.
- `window.__clarotubeStats = {skippedAds: 0, hiddenAds: 0}`; contadores separados. No datos simulados.
- No puente JavaScript nativo, no solicitudes externas, no telemetría.

El orquestador inyecta el script en callbacks de carga, filtra peticiones WebView y service workers, mantiene preferencias, pantalla completa, ciclo de vida y enlaces compartidos.

## Validación

Pruebas Java ejecutables sin JUnit con javac/java para filtros y URLs; `node --test` con dobles del DOM para script. Compilar APK debug y ejecutar lint. No afirmar verificación de anuncios reales sin ejecutar videos reales. Si hay emulador disponible, validar arranque e interacciones nativas.
