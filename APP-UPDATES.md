# Actualizaciones de SNAPTVNOW

Desde 1.0.12 (número interno 41), el botón «Descargar» del aviso abre la descarga dentro de SNAPTVNOW. Al terminar se abre el instalador del dispositivo con la APK local, sin abrir Silk ni depender de Downloader. El usuario confirma la instalación. No se cambia el flujo de actualización de contenido.

La primera instalación de esta versión debe hacerse con el método anterior, o descargando la nueva APK desde Downloader. Las versiones anteriores conservan su comportamiento hasta instalar esta actualización.

## Publicar una actualización

1. Compilar y revisar la APK firmada con la clave de producción del propietario.
2. Subir esa APK a un enlace HTTPS directo.
3. En el panel, publicar el número interno y la versión visible reales de esa APK, junto con su enlace. Para 1.0.12 son 41 y 1.0.12: no usar el enlace de la APK 1.0.11.

Android 8 y versiones posteriores pueden pedir autorizar a SNAPTVNOW como origen de instalación. En Fire TV la opción suele estar en Mi Fire TV → Opciones para desarrolladores → Instalar apps desconocidas → SNAPTVNOW. Al volver del permiso, la app ofrece la instalación sin descargar de nuevo. El usuario puede cancelar y reintentar.

## Comprobaciones y límites

- La descarga admite las redirecciones HTTPS de GitHub, sin adjuntar credenciales de suscriptores. Rechaza redirecciones a HTTP, archivos vacíos, descargas truncadas y archivos mayores de 200 MiB.
- Antes de instalar, comprueba el paquete, el número de versión publicado y la misma firma de la instalación actual. No admite una rotación de certificado con este flujo.
- La APK permanece en almacenamiento privado; el instalador recibe una URI de contenido con permiso temporal de lectura. No se necesita acceso a los archivos del usuario.
- Cancelar la descarga elimina su archivo parcial. Una APK descargada y verificada se puede reutilizar para reintentar la instalación. Si el sistema mata la app durante una descarga, al volver a iniciarla se descarga desde el comienzo.
- La instalación requiere la confirmación del sistema y el permiso del dispositivo. Las pruebas automatizadas no sustituyen la comprobación en Fire TV y Android TV reales.

## Panel directo y comprobación manual · 1.0.16 (45)

Cuenta y configuración muestra la versión instalada obtenida del paquete del dispositivo. Buscar actualizaciones consulta el panel aunque la comprobación automática esté dentro de su intervalo o el aviso se haya pospuesto. Una versión superior permite Descargar e instalar; los errores de conexión permiten reintentar.

El panel SNAPTVNOW Control admite Seleccionar APK desde el dispositivo, carga por fragmentos de 5 MiB, reanudación de fragmentos ya recibidos al reseleccionar el mismo archivo, cancelación y un borrador separado de la versión publicada. La carga no publica la actualización. La versión y el código se leen del AndroidManifest de la APK después de verificar la firma v2 y el digest del contenido completo, con el certificado de producción vigente. Publicar actualización exige un código superior, archivo completo y novedades. La publicación usa una revisión para rechazar ediciones simultáneas obsoletas.

Las APK publicadas se descargan por HTTPS desde el propio panel. Las URL son específicas de cada archivo; no se reemplazan sus bytes durante una descarga. La app nueva comprueba también el tamaño y SHA-256 publicados, incluyendo APK previamente descargadas. Las versiones 1.0.12–1.0.15 conservan su descargador interno y pueden recibir esta APK a través del mismo endpoint de actualización, aunque todavía no muestran el nuevo botón manual.

La firma de producción y su entorno protegido de GitHub se conservan. Publicar el panel no publica automáticamente una APK a clientes. La primera prueba de instalación de 1.0.16 sobre 1.0.15 debe confirmar conservación de datos, permiso de instalación y navegación con control remoto en los equipos reales.
