# SNAPTVNOW Android

Proyecto Android de SNAPTVNOW. La verificación automática compila el código fuente y no genera ni publica una APK.

## Estado VPN

La app carga las ubicaciones VPN habilitadas. Al elegir una, solicita el perfil al panel con las credenciales de la línea, pide el permiso VPN de Android y arranca el motor OpenVPN. El permiso del sistema no se puede omitir. Esta rama todavía requiere probar conexión, tráfico, reconexión y desconexión en un dispositivo Android antes de distribuir una APK.

La compilación obtiene el motor `ics-openvpn` desde el código público de `mysteriumnetwork/openvpn_dart`, revisión `e0b86e5c4f0c05a9fc3aa1ca9311275663657b68`, y verifica el SHA-256 del AAR en CI. Su [procedencia y fuente correspondiente](https://github.com/mysteriumnetwork/openvpn_dart/blob/e0b86e5c4f0c05a9fc3aa1ca9311275663657b68/android/localmaven/PROVENANCE.md) están documentadas allí. `ics-openvpn` usa GPL-2.0: cualquier APK distribuida con este motor necesita cumplir sus obligaciones de código fuente y avisos de licencia.

La entrega directa de una contraseña VPN compartida a un dispositivo autorizado permite que alguien con control de ese dispositivo la extraiga. El panel exige una línea activa y entrega el perfil solo por HTTPS, pero eso no equivale a credenciales individuales revocables. Ante una filtración se debe rotar la contraseña Surfshark.

La contraseña Surfshark y la clave de firma de producción no deben añadirse al repositorio ni a los artefactos de compilación.
