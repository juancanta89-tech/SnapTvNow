# SNAPTVNOW Smart TV — demo 0.1

Rama `codex/smart-tv-tizen-webos`. Referencia Android: `7ba8a4a14d396c81ef71668fb85ac68e776733ff`, archivos AppConfig, XtreamClient, SessionStore, Catalog y MainActivity. Android permanece intacto.

## Preparar la demo

Node.js 24, sin dependencias npm externas:

```sh
cd smart-tv
npm run build
npm run check
npm test
python3 -m http.server 8765 --directory dist/browser
```

Abrir localhost:8765 y seleccionar Entrar en demo. Build genera `dist/tizen`, `dist/webos` y `dist/browser`, con scripts clásicos compatibles con aplicaciones instaladas. Las carpetas no son paquetes firmados.

TV en vivo simulada (15 canales), PPV HOY simulado, películas, series con episodios, favoritos, búsqueda, cinco tarjetas por fila, diseño turquesa/blanco/oscuro. Flechas navegan, OK selecciona, Volver/Escape regresa, rojo o `f` guarda favoritos. Reproductor: pausa, salto de 10 s en VOD, reintento y cierre; pausa al ocultarse y libera recursos al salir. Samsung usa AVPlay; LG usa vídeo HTML5 nativo webOS. Adaptadores pendientes de hardware.

## Contenido de prueba

Mismo MP4 público usado por Android: https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4. Big Buck Bunny, © 2008 Blender Foundation, [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/), [créditos](https://peach.blender.org/). Todos los canales, eventos y series son etiquetas de prueba del clip, no emisiones ni eventos comerciales. Necesita Internet; descarga y reproducción no verificadas aquí. Iconos propios creados para la demo.

## Panel y persistencia

`src/config.js` arranca con `demo:true`: no hace solicitudes al panel. Para integración autorizada, cambiar a false y reconstruir. Consulta el mismo endpoint Android `https://snaptvnow-control.juancanta89.chatgpt.site/api/app-config`, prueba hasta ocho servidores HTTPS y valida auth, estado Active y caducidad. Implementa categorías/streams live, VOD, series y `get_series_info`; PPV filtra nombres como Android, **sin garantía de que los eventos sean de hoy**. Agenda y derechos deben confirmarse con el proveedor. Buscar carga cuatro catálogos; falta optimizar/paginar para catálogos grandes.

Demo: login y favoritos persistentes. Xtream directo: credenciales solo en memoria, acceso hasta cerrar app. Favoritos almacenados sin URL ni contraseña. No se han probado líneas reales ni modificado el panel. Configuración y servidores necesitan HTTPS, certificados compatibles y CORS desde los orígenes TV.

Login real persistente requiere configurar `gatewayUrl` y desarrollar en el panel un gateway HTTPS con este contrato POST:

| Ruta | Entrada | Respuesta |
| --- | --- | --- |
| /session | username, password | active:true, token |
| /session/validate | Bearer token | active:true |
| /session/revoke | Bearer token | JSON de confirmación |
| /catalog | section y Bearer token | items |
| /episodes | seriesId y Bearer token | items |

Items: id, title, section, description, url HTTPS para reproducir o seriesId para episodios. Gateway: comprobar línea activa/derechos, entregar URLs firmadas sin contraseña, CORS, caducidad y revocación. El token se guarda localmente y se revalida al arrancar. Revisar custodia del token por plataforma: almacenamiento web no equivale a Android Keystore. No usar credenciales reseller.

## Samsung: qué falta

Objetivo inicial Tizen 6.0+, modelos pendientes. Instalar Tizen Studio, SDK/extensiones Samsung TV; crear certificado de autor/distribuidor y registrar DUID del TV. Activar Developer Mode, reiniciar y conectar desde Device Manager o `sdb connect IP:26101`.

Importar `dist/tizen` como proyecto web TV, validar manifiesto y empaquetar con perfil Samsung. Referencia CLI: `tizen package -t wgt -s PERFIL -- dist/tizen`; instalar el WGT con `tizen install -n ARCHIVO.wgt -t TARGET`; lanzar con `tizen run -p SnapTvDemo.SnapTvNow -t TARGET`. Confirmar rutas/nombres con el SDK instalado. Manifiesto demo solo permite storage.googleapis.com: añadir dominios HTTPS concretos de panel, gateway y servidores para integración real.

Probar AVPlay MP4/HLS autorizado, codecs/audio, buffering/error/reintento, cambio de canal, pausa, retorno, escala, foco, teclas multimedia, rojo, filas incompletas, suspensión/reanudación y persistencia tras reiniciar.

## LG: qué falta

Objetivo inicial webOS TV 6+, modelos pendientes. Instalar CLI/SDK oficial, activar Developer Mode y Key Server. Configurar con `ares-setup-device`, obtener clave con `ares-novacom --device TV --getkey`.

Empaquetar `ares-package dist/webos -o dist`; instalar `ares-install --device TV ARCHIVO.ipk`; lanzar `ares-launch --device TV com.snaptvnow.tv.demo`. Validar appinfo con el SDK del modelo. Probar vídeo nativo MP4/HLS autorizado, codecs/audio, CORS/certificados, Magic Remote/puntero, teclas multimedia, Back 461, foco, suspensión y persistencia. Estos comandos son pasos pendientes, no instalaciones efectuadas.

## Comprobaciones disponibles — 2026-10-02

- npm run build: terminado, tres carpetas generadas.
- npm run check: terminado, sintaxis fuentes/bundles y recursos referenciados.
- npm test: terminado, salida 2 archivos aprobados, 0 fallos. Nueve casos: navegación cinco columnas, demo/búsqueda/favoritos, HTTPS, almacenamiento corrupto, PPV, Xtream/failover/episodios, token inactivo, AVPlay simulado y HTML5 simulado.
- Prueba visual en Chromium/servidor local: bloqueada por permisos de sockets; solicitud de red interrumpida. Detenida, sin captura ni reproducción confirmada.
- Clonación por terminal: proxy inaccesible. GitHub se usa para lectura y guardado. La carpeta local contiene solo los archivos nuevos de Smart TV, no el checkout completo de Android.
- Sin Tizen/webOS CLI ni TV/emulador conectado: no se generaron WGT/IPK, no se firmó ni instaló. Sin credenciales de línea de prueba.

## Antes de publicar

Completar pruebas visuales, navegador, emuladores y TVs Samsung/LG; definir años/modelos soportados; integrar gateway persistente; validar CORS, catálogos grandes, red lenta/reconexión y largas sesiones; confirmar derechos, agenda PPV y DRM si aplica. EPG, VPN, anuncios, mensajes y actualización Android no se portaron.

Definir identificadores/versiones de producción, firma, iconos/capturas, privacidad/términos/soporte y cuentas de revisión; pasar validadores oficiales y procesos Samsung Seller Office/LG Seller Lounge. No se publicó en tiendas, no se creó release ni se distribuyeron paquetes a clientes.
