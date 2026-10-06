# Paquetes SNAPTVNOW TV demo 0.1

Esta entrega sigue siendo una demo. No contiene cuentas reales ni está lista para las tiendas. Compatibilidad objetivo: Samsung Tizen 6.0+ y LG webOS TV 6+. No se ha confirmado en televisores físicos; no implica compatibilidad con todos los modelos.

## Preparación

Con Node.js 24, dentro de `smart-tv`:

```sh
npm run build
npm run check
npm test
npm run package:sources
```

El ZIP contiene las carpetas web construidas y las instrucciones. No es un APK ni un paquete que el TV pueda instalar directamente. `SHA256SUMS.txt` identifica los archivos generados.

## LG

Instalar la CLI oficial y generar un paquete real:

```sh
npm install -g @webos-tools/cli
npm run package:webos
```

El IPK se escribe en `packages`. La instalación de prueba requiere Developer Mode, conexión con el TV y su clave. No se afirma que el paquete esté aprobado para la tienda.

## Samsung

Instalar Tizen Studio, Samsung TV Extension y Samsung Certificate Extension. Crear un perfil con certificados Samsung válidos; para pruebas, registrar el DUID del televisor. En una terminal POSIX:

```sh
TIZEN_CERT_PROFILE=miPerfil npm run package:tizen
```

No guardar claves o certificados en GitHub. El script exige un perfil de firma y no crea un WGT falso o sin firma. El Galaxy puede servir para gestionar y descargar el trabajo; instalar en el televisor requiere un equipo de desarrollo conectado a su red. La nube no puede acceder directamente a la red privada del TV.

## Desde el celular

En GitHub abrir Actions → Preparar demo Smart TV. El flujo se ejecuta automáticamente al subir cambios de esta rama; la opción Run workflow estará disponible cuando el flujo exista en la rama predeterminada. Al terminar, abrir la ejecución y descargar el artefacto `SNAPTVNOW-TV-demo`. El flujo genera ZIP e IPK usando la CLI oficial de LG. No instala en el TV ni publica en tiendas. GitHub puede solicitar abrir la descarga en el navegador con la sesión iniciada.

## Pendientes

Firma Samsung, prueba visual, reproducción real, controles y suspensión en Samsung/LG, integración del gateway para sesión persistente, CORS, permisos de dominios de producción, cuentas de revisión y materiales de tienda. No se ha ampliado el soporte a versiones antiguas; eso requiere adaptar y probar cada grupo de modelos.

Referencias oficiales:
- https://webostv.developer.lge.com/develop/tools/cli-installation
- https://developer.samsung.com/smarttv/develop/getting-started/using-sdk/command-line-interface.html

## Preparación adicional del 6 de octubre de 2026

Se añadieron posición de VOD guardada por título/episodio, borrado al finalizar y al cerrar sesión; filtros de búsqueda Todo/TV/Películas/Series; controles de audio/subtítulos que solo seleccionan las pistas expuestas por el dispositivo. Samsung usa las pistas de AVPlay; LG usa audioTracks/textTracks si su motor las expone. La disponibilidad real depende de contenedor, protocolo, códec y modelo.

No se afirma que solo falte un PC para producción. También falta disponer de un gateway HTTPS real compatible con el contrato siguiente, una cuenta de prueba y acceso a televisores. Estos recursos no se incluyen ni se pueden inventar.

### Contrato del panel para acceso persistente

La app en modo real necesita configurar `gatewayUrl` en `src/config.js` y permitir ese dominio en el manifiesto Samsung. El gateway debe implementar POST con JSON:

- `/session`: recibe `{username,password}`, valida la línea y devuelve `{active:true,token}`. La contraseña se usa para autenticar, no se almacena en el navegador.
- `/session/validate`: recibe autorización Bearer y devuelve `{active:true}` o rechaza la sesión.
- `/session/revoke`: invalida el token.
- `/catalog`: recibe `{section}` y devuelve `{items:[{id,title,section,url,seriesId,description}]}`. `seriesId` se usa para series, `url` HTTPS para vídeo.
- `/episodes`: recibe `{seriesId}` y devuelve episodios en el mismo formato con `section:"Series"`.

Las URL de vídeo deben ser tickets temporales HTTPS que no revelen credenciales del proveedor. El servicio debe autorizar cada petición, ocultar el servidor aguas arriba, permitir CORS requerido por los motores TV y limitar peticiones. No es suficiente reemplazar una URL HTTP por HTTPS sin servicio y certificado válidos.

### Secuencia al disponer del PC

1. Instalar herramientas oficiales Samsung/LG y guardar certificados fuera del repositorio.
2. Conectar el PC y Samsung QN55Q60AAFXZA a la misma red; activar modo desarrollador y registrar DUID.
3. Generar WGT firmado y probar primero la demo. Para LG, instalar el IPK mediante Developer Mode.
4. Comprobar OK/flechas/Volver, favoritos, búsqueda, VOD/seek/reanudación, pistas, errores y suspensión.
5. Integrar el gateway real y probar vencimiento, logout y recuperación de sesión.
6. Solo después preparar fichas de tienda, privacidad, soporte, capturas y cuentas de revisión. No se ha enviado ninguna app a tiendas.

Verificación de esta entrega: build/check correctos; 11 pruebas automatizadas aprobadas (reproductores simulados). IPK generado y reconocido por CLI LG 3.2.6. La descarga de Chromium falló por archivo incompleto; no se obtuvo una prueba visual ni reproducción real.
