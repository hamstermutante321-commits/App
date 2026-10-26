# ClaroTube para Android

Cliente independiente para ver el sitio móvil de YouTube con protección experimental frente a anuncios. Interfaz en español y tema oscuro. Android 8.0 o superior.

## Qué incluye

- Búsqueda y enlaces de videos, Shorts y listas de YouTube.
- Filtros locales de peticiones publicitarias conocidas, también para service workers.
- Ocultación de espacios publicitarios e intento de omitir anuncios detectados en el reproductor.
- Escudo para activar o desactivar la protección y recargar la página.
- Contador de **peticiones filtradas** durante la sesión; no equivale a anuncios de video eliminados.
- Pantalla completa, navegación y apertura de enlaces compartidos desde otras apps.
- Borrado de cookies, caché, historial y almacenamiento web.
- Sin cuenta propia, backend, analíticas añadidas, VPN, root ni permisos de almacenamiento.

## Alcance y límites

La protección se aplica **dentro de ClaroTube**. No modifica ni bloquea anuncios en la aplicación oficial de YouTube. Los filtros son experimentales: YouTube puede cambiar sus páginas, anuncios y mecanismos de reproducción. No se garantiza eliminar todos los anuncios. Si un video falla, desactiva el escudo y recarga.

Los mensajes de consentimiento y avisos del servicio se mantienen. El inicio de sesión, videos restringidos, DRM y otras funciones del sitio pueden estar limitados en WebView. La app utiliza el reproductor del sitio y no descarga videos ni ofrece reproducción en segundo plano.

## Instalar una compilación de prueba

1. Descarga el APK de prueba entregado con esta versión.
2. Ábrelo en tu Android y habilita la instalación desde esa fuente si el sistema lo solicita.
3. Abre ClaroTube, acepta la bienvenida y busca o pega un enlace de YouTube.

El APK `debug` está firmado para desarrollo y no es una publicación de Google Play. La clave de desarrollo depende del equipo que lo compila; dos equipos pueden generar APK que no se actualicen entre sí. Para distribución estable hay que configurar una firma privada de publicación.

## Compilar

Requisitos: JDK 17 o superior compatible con Gradle, Android SDK con plataforma 36 y herramientas de compilación. El proyecto usa Java 17, Android Gradle Plugin 9.1.0 y Gradle 9.3.1. No necesita Flutter.

Define la ruta del SDK en `local.properties` (archivo local ignorado):

```properties
sdk.dir=C\:/ruta/a/Android/Sdk
```

En Windows:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
.\tool\test.ps1
```

En macOS o Linux:

```sh
bash gradlew :app:assembleDebug :app:lintDebug
sh tool/test.sh
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`. Android Studio puede abrir la carpeta del repositorio directamente.

## Privacidad

Los filtros están incluidos en la app y no se descargan de un servidor. No se añade telemetría. El contenido se solicita directamente a YouTube y sus proveedores, que reciben datos de conexión y pueden utilizar cookies según sus propias políticas. La app conserva localmente la preferencia de protección y los datos normales de WebView. No se expone un puente JavaScript hacia funciones nativas y se bloquean conexiones HTTP, archivos locales y recursos de contenido locales.

Más detalles: [privacidad](docs/PRIVACY.md), [arquitectura](docs/ARCHITECTURE.md) y [pruebas](docs/TESTING.md).

ClaroTube no está afiliado a YouTube ni a Google. Las marcas pertenecen a sus titulares.
