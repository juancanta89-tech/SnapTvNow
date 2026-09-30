# Firma de producción de SNAPTVNOW

La APK «VPN Prueba 3» es una instalación de pruebas con el identificador `com.snaptvnow.tv.vpntrial3` y una firma de depuración. La versión de producción usará `com.snaptvnow.tv`. No es una actualización de la APK de pruebas.

## Custodia de la clave

La clave privada debe generarse y conservarse bajo control del propietario. No la publiques en GitHub, no la envíes por chat ni la subas al panel. Guarda dos copias privadas en ubicaciones separadas y conserva la contraseña en un gestor seguro; perder la clave puede impedir actualizar instalaciones distribuidas fuera de Play. Si ya existe una APK instalada con `com.snaptvnow.tv`, compara su certificado antes de decidir si esta clave nueva puede actualizarla.

La clave creada por el propietario desde Termux es un almacén PKCS12 llamado `snaptvnow-production.jks`, con alias `snaptvnow`, RSA de 4096 bits y validez de 12000 días. Se creó de manera interactiva con:

```bash
keytool -genkeypair -keystore snaptvnow-production.jks -storetype PKCS12 -alias snaptvnow -keyalg RSA -keysize 4096 -validity 12000 -dname "CN=SNAPTVNOW, OU=Android, O=SNAPTVNOW"
```

La contraseña y el archivo no se almacenan en el repositorio. Comprueba que la copia de respaldo se puede abrir con `keytool -list -v -keystore snaptvnow-production.jks -alias snaptvnow` y anota la huella SHA-256 del certificado antes de distribuir la primera APK.

## Secretos privados para la compilación

En el repositorio GitHub, usa el entorno `production` con aprobación del propietario. Añade allí cuatro secretos de Actions:

| Nombre | Contenido |
| --- | --- |
| `SNAPTVNOW_KEYSTORE_BASE64` | Contenido del archivo .jks codificado en Base64, en una sola línea |
| `SNAPTVNOW_STORE_PASSWORD` | Contraseña del almacén |
| `SNAPTVNOW_KEY_ALIAS` | `snaptvnow` |
| `SNAPTVNOW_KEY_PASSWORD` | La misma contraseña del almacén PKCS12 |

El flujo `Sign production APK` es manual y solo corre desde `main`. Se detiene si faltan secretos, compila con el motor OpenVPN verificado, valida la firma con `apksigner` y deja la APK como artefacto privado de revisión por siete días. No publica ninguna versión a clientes.

Antes de fusionar y ejecutar el flujo: confirmar identificador, número de versión, migración de instalaciones previas, pruebas en varios dispositivos, licencia GPL del motor y política de actualizaciones. La app y el panel siguen pendientes de estas verificaciones para una entrega masiva.
