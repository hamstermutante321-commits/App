# Registro de validación — 0.1.0

Fecha: 25 de octubre de 2026, zona America/Lima. Entorno: emulador Android API 36, x86_64, resolución 1080 × 2400, con Android System WebView e Internet. No se probó un teléfono físico.

## Resultados

| Comprobación | Resultado observado |
| --- | --- |
| Reglas de red | 28 aserciones Java aprobadas. Publicidad conocida filtrada; hosts engañosos, recursos de video y endpoints de reproducción preservados. |
| Navegación y enlaces | 63 aserciones Java aprobadas, incluyendo Unicode, búsquedas, HTTPS, credenciales, puertos, enlaces cortos, Shorts y parámetros de tiempo/listas. |
| Script | 8 casos Node aprobados. Guardas, idempotencia, limpieza, campos de publicidad y preservación/restauración del video se prueban con dobles de DOM. |
| Compilación | `:app:assembleDebug` y `:app:lintDebug` aprobados. Cero errores de lint; quedan dos avisos cosméticos del icono adaptativo. |
| Inicio | APK instalado y Activity iniciada sin cierre inesperado. Bienvenida y sitio móvil de YouTube visibles. |
| Enlace compartido | ACTION_SEND con `https://youtu.be/jNQXAC9IVRw` llegó a `https://m.youtube.com/watch?v=jNQXAC9IVRw`. |
| Reproducción real | «Me at the zoo» llegó a 19,021 de 19,021 segundos, con readyState 4 y sin error de video. También se comprobó su reproducción al reiniciar desde el control de replay. |
| Inyección | Se observó el runtime de protección activo en el WebView real. |
| Filtrado real de red | La sesión mostró entre 2 y 5 peticiones filtradas al cargar inicio y video. No se interpreta ese contador como número de anuncios de video. |
| Pantalla completa | Activación del botón web mediante herramientas de prueba: controles nativos ocultos durante pantalla completa y restaurados al pulsar Atrás. Sin error de AndroidRuntime. |
| Escudo y persistencia | El script de prueba Android desactivó el escudo, reinició el proceso, confirmó la preferencia y volvió a activarlo. |
| Opciones y horizontal | Menú de opciones abierto; búsqueda, ajustes y navegación disponibles tras girar el emulador. Revisada captura horizontal. |

## Lo que sigue pendiente

No se midió la eliminación de anuncios de video reales: el video de prueba no presentó un anuncio que permita una comparación controlada. No se garantiza cobertura total. Las pruebas de omisión del script utilizan casos simulados y no sustituyen esa comprobación.

Pendientes: dispositivos físicos y Android 8–15; diversos inventarios publicitarios/cuentas/regiones; Shorts y listas en reproducción real; inicio de sesión y contenido restringido; lector de pantalla y fuentes grandes; recuperación de errores de red y cierre del proceso de renderizado. No se verificó publicación en Google Play ni firma de distribución.

La automatización de GitHub vuelve a ejecutar pruebas, compilación y lint en cada push y adjunta un APK debug al resultado. Su resultado remoto debe consultarse en Actions; la validación local no implica que una ejecución remota ya haya terminado.
