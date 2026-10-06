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
