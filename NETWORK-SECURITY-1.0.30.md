# SNAPTVNOW 1.0.30 · Restricción preventiva de HTTP

Actualización de la misma aplicación `com.snaptvnow.tv`, versión 1.0.30, código 59.
Se conserva la firma de producción existente; no se genera ni rota una clave.

La versión 1.0.29 permitía HTTP para cualquier destino. La nueva APK lo deniega
por defecto. En Android 7/API 24 y posteriores conserva únicamente las excepciones
exactas `ccf.center` y `tv.newplytv.online`, sin incluir subdominios. Ambos nombres
estaban en la configuración pública vigente consultada el 3 de noviembre de 2026;
la entrada principal y el respaldo de SNAPTVNOW siguen siendo HTTPS. Los puertos
no se pueden restringir mediante Network Security Config: la excepción es por host.
No se añaden excepciones para DNS nuevos, IP de redirección ni proveedores no inventariados.

Los carteles HTTP fuera de las excepciones se intentan mediante HTTPS en el mismo
host, conservando ruta y consulta; un puerto 80 pasa al puerto HTTPS predeterminado.
No hay retorno a HTTP cuando HTTPS falla. Un sitio sin HTTPS compatible o una
redirección a otro host HTTP bloqueado puede dejar su imagen sin cargar. Esto no
modifica el catálogo ni inventa otro origen de video.

Se mantienen el reproductor, sus rangos, buffers, tiempos y rechazo de redirecciones
entre HTTP y HTTPS; las listas HLS, segmentos y claves quedan sujetos a la política
del host de cada solicitud. Un destino HTTP nuevo necesita revisión y una nueva
excepción compilada o una ruta HTTPS compatible. Permitir dos hosts heredados no
cifra el tráfico que todavía los utilice y no demuestra que existiera una filtración.
No se promete una mejora de velocidad, audio o estabilidad por este cambio preventivo.

## Compatibilidad

- Se conserva minSdk 23 y targetSdk 35. En Android 6/API 23, la política por dominio
  todavía no existe: `usesCleartextTraffic=false` implica **solo HTTPS**. La entrada
  propia HTTPS permanece disponible, pero los dos respaldos HTTP no lo están en ese SO.
- Las APK BoxPanel reciben sus DNS dinámicamente. Su política anterior se conserva
  en un recurso separado, seleccionado solo al compilar una identidad BoxPanel.
  Esta corrección se limita a SNAPTVNOW; no declara restringido el HTTP de BoxPanel.
- HTTPS sigue usando la validación de certificados del sistema. No se agregan CA,
  certificados de prueba, confianza insegura ni excepciones para control, VPN o APK.
- La instalación debe realizarse encima de la APK nativa anterior. No desinstalar
  para la prueba de conservación de cuenta, favoritos y progreso.

## Validación antes de distribuir

El control estático comprueba la política, los hosts exactos y el aislamiento de
recursos BoxPanel. Las pruebas instrumentadas verifican la política efectiva de
Android, acceso permitido/bloqueado y solicitudes multimedia mediante servidores
locales, sin cuentas ni señales reales. Se conservan las regresiones de reproducción,
actualizador, perfiles y audio MP2/AAC/AC3/EAC3. Compilar o probar un emulador no
certifica reproducción continua, sonido ni instalación OTA en el Firestick físico.

La APK firmada debe declarar código 59, versión 1.0.30 y el certificado SHA-256
`069517313d68df21e9ba3110db2e498e9b42aeb8f3f4553d84953e946cd27165`.
La entrega de un archivo para probar no publica por sí sola un aviso OTA general.

Referencia de plataforma: https://developer.android.com/privacy-and-security/security-config
