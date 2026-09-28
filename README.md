# SNAPTVNOW Android

Proyecto Android de SNAPTVNOW. La verificación automática compila el código fuente y no genera ni publica una APK.

## Estado VPN

La app carga del panel las ubicaciones VPN habilitadas. La selección aún no conecta: falta integrar y probar el motor OpenVPN 3 con `VpnService` y un canal de credenciales autenticado. No publicar una APK como VPN funcional hasta completar una prueba de conexión, tráfico y desconexión en un dispositivo Android.

La contraseña Surfshark y la clave de firma de producción no deben añadirse al repositorio ni a los artefactos de compilación.
