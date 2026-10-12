# SNAPTVNOW 1.0.16 · actualizaciones directas

La versión instalada se lee del dispositivo y Buscar actualizaciones consulta el panel de inmediato, sin esperar el intervalo de los avisos automáticos. El resultado distingue una respuesta válida sin versión nueva de una conexión fallida. Una actualización disponible abre el descargador interno.

El panel publicado conserva su identidad y datos existentes. Añade carga en fragmentos de 5 MiB, progreso, reintento, cancelación, borrador separado, lectura de versión/código desde la APK, verificación del certificado de producción y firma v2 con su digest completo. Las publicaciones usan una revisión y rechazan versiones antiguas. Las APK publicadas tienen URL inmutable y descarga directa con HEAD y Range. El cliente verifica SHA-256, tamaño, paquete, código y firma antes de solicitar la instalación.

## Evidencia

- Código Android integrado mediante PR #13; fuente de la APK: `9d6776d6891d8fd608096d1655f0a3eb6256cf89`.
- Android source check 38313780563: 211 pruebas, 0 fallos, 0 errores y 0 omisiones. Incluye metadatos de actualización, integridad de descarga y conservación del instalador.
- Firma de producción 38314399585: completada desde el entorno protegido existente; no se cambió ni generó otra clave de producción.
- APK: paquete `com.snaptvnow.tv`, versión 1.0.16, código 45, 44410600 bytes.
- SHA-256: `3cbff3df9f65ffdb65dba66074447f83289f6d559331e54517b416051fd4ef7a`.
- Certificado SHA-256: `069517313d68df21e9ba3110db2e498e9b42aeb8f3f4553d84953e946cd27165`; coincide con 1.0.15. Firma v2 y digest de los bytes comprobados independientemente.
- Panel: versión 18, fuente `6e187ca1a2b74262bc1f661fc8ae22699ddbee94`, publicación completada. Cinco grupos de pruebas sin fallos, incluyendo una carga de varias partes, verificación, publicación, descarga Range, aislamiento de borradores y acceso no autorizado.
- El endpoint desplegado de actualización conserva la publicación anterior; una carga anónima devuelve 401. Publicar el panel no publica automáticamente una APK a clientes.

## Validación pendiente en equipos

Instalar 1.0.16 sobre 1.0.15 en celular y Fire TV, comprobar conservación de datos y navegación con control remoto, seleccionar la APK desde el panel real y confirmar la instalación desde una app anterior. No se controló hardware físico; las pruebas no certifican esos escenarios. La revisión visual de navegador del panel no se ejecutó porque la capacidad control-browser requerida no estaba disponible.
