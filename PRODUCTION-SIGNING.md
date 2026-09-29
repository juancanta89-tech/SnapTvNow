# Firma de producción de SNAPTVNOW

La APK «VPN Prueba 3» es una instalación de pruebas con el identificador `com.snaptvnow.tv.vpntrial3` y una firma de depuración. La versión de producción usará `com.snaptvnow.tv`. No es una actualización de la APK de pruebas.

## Custodia de la clave

La clave privada debe generarse y conservarse bajo control del propietario. No la publiques en GitHub, no la envíes por chat ni la subas al panel. Guarda dos copias cifradas en ubicaciones separadas junto con el alias y las contraseñas; perder la clave puede impedir actualizar instalaciones distribuidas fuera de Play. Si ya existe una APK instalada con `com.snaptvnow.tv`, compara su certificado antes de decidir si esta clave nueva puede actualizarla.

En un equipo propio con JDK 17+, ejecuta `keytool` de forma interactiva (te pedirá la contraseña sin mostrarla):

```bash
mkdir -p "$HOME/SNAPTVNOW-private"
keytool -genkeypair -storetype PKCS12 -keystore "$HOME/SNAPTVNOW-private/snaptvnow-production.jks" -alias snaptvnow-production -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=SNAPTVNOW, OU=Android, O=SNAPTVNOW"
keytool -list -v -keystore "$HOME/SNAPTVNOW-private/snaptvnow-production.jks" -alias snaptvnow-production
```

Anota la huella SHA-256 del certificado. Comprueba que las copias se pueden abrir con `keytool -list` antes de distribuir la primera APK.

## Secretos privados para la compilación

En el repositorio GitHub, configura el entorno `production` con aprobación obligatoria del propietario. Añade allí cuatro secretos de Actions:

| Nombre | Contenido |
| --- | --- |
| `SNAPTVNOW_KEYSTORE_BASE64` | Contenido del archivo .jks codificado en Base64, en una sola línea |
| `SNAPTVNOW_STORE_PASSWORD` | Contraseña del almacén |
| `SNAPTVNOW_KEY_ALIAS` | `snaptvnow-production` |
| `SNAPTVNOW_KEY_PASSWORD` | Contraseña de la clave (igual a la del almacén PKCS12 si keytool no solicita otra) |

El flujo `Sign production APK` es manual y solo corre desde `main`. Se detiene si faltan secretos, compila con el motor OpenVPN verificado, valida la firma con `apksigner` y deja la APK como artefacto privado de revisión por siete días. No publica ninguna versión a clientes.

Antes de fusionar y ejecutar el flujo: confirmar identificador, número de versión, migración de instalaciones previas, pruebas en varios dispositivos, licencia GPL del motor y política de actualizaciones. La app y el panel siguen pendientes de estas verificaciones para una entrega masiva.
