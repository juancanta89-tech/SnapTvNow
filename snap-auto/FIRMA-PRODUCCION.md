# Firma estable de SNAP Auto

La clave `SNAP-Auto-production.p12` se creó fuera del repositorio. Su alias es
`snap-auto-production`. Guárdala junto con la contraseña en un lugar privado y
mantén una segunda copia segura. Las siguientes versiones deben usar **la misma
clave** y un `versionCode` mayor.

El flujo de GitHub genera `app-release-unsigned.apk` sin acceso a la clave. Para
firmarla en una computadora con Java y Android SDK Build Tools 35:

```bash
read -rs SNAP_AUTO_KEYPASS
export SNAP_AUTO_KEYPASS
bash sign-release.sh app-release-unsigned.apk SNAP-Auto-release.apk \
  /ruta/privada/SNAP-Auto-production.p12 /ruta/android-sdk/build-tools/35.0.0
unset SNAP_AUTO_KEYPASS
```

El script alinea el APK, firma con esquemas v2 y v3 y verifica el certificado.
Nunca firmes una APK de depuración como versión definitiva. Para distribuir
actualizaciones directas, conserva el mismo identificador de aplicación y la
misma clave. La v1.12 de depuración tenía otra firma: exporta primero un backup
SQLite y, si Android no permite actualizarla, desinstala esa versión, instala
la versión de producción y restaura el backup.

No publiques el `.p12`, la contraseña, un archivo `.env` ni una copia de la
clave como artefacto de CI. Si eliges Google Play App Signing en el futuro,
define por separado el uso de clave de firma y clave de carga antes de publicar.
