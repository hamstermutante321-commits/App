# Pruebas de ClaroTube

## Pruebas automáticas

Desde la raíz del proyecto, ejecutar `powershell -ExecutionPolicy Bypass -File tool/test.ps1` en Windows o `bash tool/test.sh` en Linux/CI. Ambos compilan con `javac --release 17` y ejecutan Java y Node.js sin instalar dependencias. Windows prefiere el JDK incluido con Android Studio y usa el del sistema si no está disponible. Las clases compiladas quedan en `.test-build/`, que Git ignora.

Las pruebas Java ejecutan `main` con aserciones propias: política de URLs, búsquedas UTF-8, enlaces de video/Shorts/listas con tiempo, hosts engañosos, recursos de reproducción permitidos y entradas inválidas. `node --test` ejecuta `protection.js` con dobles del DOM: activación por host/bandera, repetición de la inyección, limpieza y preservación de datos del reproductor. Son pruebas de lógica; no simulan WebView ni la red real.

## Matriz manual pendiente

Resultados ejecutados para esta entrega: [registro de validación](VALIDATION.md). Los escenarios que no figuran como comprobados en ese registro siguen pendientes.

Registrar dispositivo/emulador, versión de Android, versión de la app, conexión, pasos, resultado observado y captura o log cuando falle. Ningún escenario de esta matriz se considera aprobado sin ejecutarlo.

| Área | Pasos | Resultado a comprobar |
| --- | --- | --- |
| Inicio | Instalar APK debug y abrir ClaroTube con red disponible. | Aparece la interfaz y carga `m.youtube.com`; no hay cierre inesperado. |
| Búsqueda | Buscar texto con espacios y acentos, abrir un resultado y volver. | Resultados correctos; atrás vuelve a la página anterior y el título/estado se actualizan. |
| Enlaces | Abrir URL normal, `youtu.be`, Shorts, lista y video con `t=` desde la barra y desde compartir con ClaroTube. | Se navega al contenido esperado y se conserva el tiempo/lista; enlaces externos se tratan según la política sin navegar dentro del WebView. |
| Reproducción | Reproducir video normal y Short, pausar, cambiar calidad si está disponible y entrar/salir de pantalla completa. | Audio/video siguen funcionando; controles nativos y orientación se recuperan al salir. |
| Protección | Activar/desactivar el control, recargar, navegar y reiniciar la app. | El estado visible y la preferencia persisten; los contadores solo cambian ante acciones observadas. Comparar reproducción con protección encendida y apagada. |
| Anuncios | En contenido donde aparezca publicidad, registrar URL/fecha y observar antes, durante y después de la reproducción. | Anotar qué se ocultó u omitió y qué quedó visible. No asumir cobertura completa ni usar la ausencia de un anuncio en una sola reproducción como prueba. |
| Consentimiento y acceso | Probar inicio de sesión y cualquier pantalla de consentimiento o advertencia que aparezca. | La app permite ver e interactuar con esas pantallas; no desaparecen por el filtro. |
| Errores y ciclo de vida | Desconectar red, recargar, reintentar, enviar a segundo plano, volver y girar el dispositivo. | Mensaje/reintento legibles, sin WebView duplicado ni pérdida inesperada de controles. |
| Accesibilidad | Usar lector de pantalla y tamaño de fuente grande; recorrer búsqueda, navegación, protección y ajustes. | Controles etiquetados, enfoque lógico y contenido legible sin superposiciones. |

## Límites

La lógica de URL y DOM se prueba sin Android. Compilar el APK y ejecutar lint verifican integración estática; solo una ejecución en dispositivo/emulador puede confirmar WebView, pantalla completa, service workers, rendimiento y anuncios reales. El inventario de anuncios y el sitio de YouTube cambian con el tiempo, así que los resultados manuales deben indicar fecha y contenido probado.

## Prueba de controles en emulador

Con un emulador encendido, `adb` disponible y el APK debug compilado, ejecutar `pwsh -File tool/smoke-android.ps1`. Requiere PowerShell 7. El script admite `-Serial emulator-5554`, instala el APK, prueba escudo y persistencia, abre opciones, comprueba controles horizontales y restaura la orientación inicial. Rechaza números de serie de teléfonos físicos. Capturas y jerarquías quedan en `artifacts/` (ignoradas por Git).
