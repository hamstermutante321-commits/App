# Privacidad de ClaroTube 0.1.0

ClaroTube no opera servidores, no tiene registro propio y no añade SDK de publicidad o analíticas. No recoge ni envía estadísticas del contador a un servidor. Las reglas de protección están empaquetadas con la aplicación.

## Datos en el dispositivo

- Preferencia de protección y confirmación de bienvenida en SharedPreferences.
- Cookies, caché, historial y almacenamiento del sitio gestionados por Android WebView.
- Contador de peticiones filtradas en memoria durante la sesión.

En Opciones → Borrar datos de navegación puedes eliminar los datos web de ClaroTube. Eliminar los datos de la aplicación desde los ajustes de Android también elimina las preferencias. Se deshabilitan las copias de seguridad de datos de la app.

## Conexiones a terceros

Al cargar YouTube, el dispositivo se conecta directamente con YouTube, Google y los proveedores utilizados por el sitio. Estos servicios pueden recibir tu dirección IP, información del dispositivo, interacciones y cookies. Sus propias políticas y opciones de consentimiento se aplican; ClaroTube no proporciona anonimato. Las cookies de terceros se desactivan en WebView, lo que puede limitar algunas funciones.

Los enlaces ajenos a los hosts admitidos de YouTube se abren en otra aplicación únicamente por una interacción del usuario. Las redirecciones externas automáticas se detienen. El inicio de sesión que requiera abrir un navegador puede no trasladar esa sesión al WebView.

## Permisos

Solo se solicita INTERNET. No se solicita acceso a archivos, contactos, micrófono, ubicación, cámara ni VPN. No se instala un certificado, no se modifica el sistema y no se interviene en otras aplicaciones.

## Diseño de seguridad

Se valida HTTPS y el host antes de navegar o inyectar filtros. No se usa addJavascriptInterface. Se deshabilita acceso a archivos y contenido local y no se permiten recursos HTTP mixtos. Los errores de certificado se cancelan. La depuración de WebView solo está habilitada en compilaciones debug.
