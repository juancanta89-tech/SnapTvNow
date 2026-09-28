# SNAP Auto — Android

Aplicación original inspirada en las funciones visibles de Auto Text, con interfaz en español y colores SNAPTVNOW. Requiere un teléfono Android con servicio SMS; Fire TV y TV Box normalmente no tienen telefonía SMS.

## Funciones incluidas

- Programar SMS por fecha y hora; repetir cada hora, día, semana, mes o año.
- Crear tareas para hasta 50 destinatarios a la vez, con registros separados, contacto y `{NOMBRE}`.
- Respuesta automática a SMS entrantes y reenvío de SMS a otro número, con filtros por remitente, palabra clave, horario y pausa por remitente.
- Estados pendiente, enviado, fallido, pausado y acción necesaria; historial de aceptación por la red móvil; editar, duplicar, pausar y eliminar tareas.
- Contactos manuales o importados desde CSV UTF-8 (Excel: **Guardar como CSV**), plantillas y copia JSON exportable/restaurable.
- Mensaje de WhatsApp programado como **recordatorio asistido**: la notificación abre el chat con el texto listo; el usuario pulsa **Enviar** y confirma en la app.
- Datos locales, sin servidor ni anuncios. Las copias restauradas quedan pausadas para revisión.
- Backup completo `.sqlite3` desde Ajustes, con nombre fechado, y restauración de backups creados por SNAP Auto. La restauración reemplaza los datos actuales y pausa las tareas importadas.
- Importación del formato Auto Text observado en `autotext_backup_20260928_114321.sqlite3`: tareas programadas SMS y WhatsApp, contactos y plantillas; preserva las filas originales de `futy`, `group_recipient` y `message_template` en el archivo interno. Impide importar dos veces el mismo archivo. Los mensajes futuros con destinatario válido quedan pendientes y reciben alarmas. Los WhatsApp requieren confirmación manual al llegar la notificación.

## Generar el APK

1. Abre esta carpeta en Android Studio con JDK 17 y SDK Android 35; espera la sincronización de Gradle.
2. Menú **Build → Build APK(s)**. El archivo queda en `app/build/outputs/apk/debug/app-debug.apk`.
3. También puedes subir los archivos al **directorio raíz** de un repositorio GitHub y ejecutar **Actions → Build Android APK → Run workflow**. Descarga el artefacto `SNAP-Auto-debug-APK`.
4. Instala la APK en un teléfono Android de pruebas. Concede los permisos de SMS, notificaciones y alarmas si se solicitan.

La APK debug es para pruebas. Para distribuirla a clientes se necesita firma de release propia, pruebas en dispositivos reales y revisión de las reglas de la tienda donde quieras publicarla.

## Prueba mínima

1. En un equipo con SIM y saldo, crea una tarea SMS a tu **propio número** para dentro de unos minutos. Revisa Pendiente, luego el historial y el SMS recibido. La hora puede variar si el sistema no concede alarmas exactas.
2. Desde otro teléfono, envía un SMS de prueba a la SIM con una palabra clave para verificar la regla de respuesta. Usa una pausa de varios minutos para evitar ciclos entre respuestas automáticas.
3. Programa un recordatorio WhatsApp para tu propio número y verifica que el aviso abre el borrador. El envío se hace dentro de WhatsApp.

## Límites conocidos

- No automatiza WhatsApp, WhatsApp Business, Messenger ni Telegram. Envío automático de WhatsApp Business requiere servidor, cuenta y acceso a la API oficial, con autorización de los destinatarios; no debe ponerse un token secreto dentro de una APK.
- No escucha mensajes de WhatsApp ni llamadas perdidas. Las reglas de respuesta y reenvío son **solo SMS**.
- El estado Enviado registra la aceptación por la red móvil, no la entrega al destinatario ni la lectura. El teléfono puede estar apagado, sin SIM, sin cobertura o con restricciones de batería.
- Importa CSV, incluido CSV exportado por Excel; no abre `.xlsx` directamente.
- El importador Auto Text fue adaptado al esquema real de la copia aportada. Si una versión futura de Auto Text cambia sus tablas, la app rechaza la importación sin alterar las tareas actuales. Los envíos ya completados conservan su estado; los pendientes futuros se activan automáticamente. Una migración de la versión 1.2 identifica y activa una sola vez las tareas futuras que quedaron pausadas al importarse. Las reglas de respuesta de WhatsApp se conservan en el archivo consultable desde Ajustes, pero no se ejecutan. Las tareas sin número se deben corregir antes de activarlas.
- Pantalla de tareas con pestañas y contadores Pendientes, Hechas y Fallidas; las pendientes se ordenan por hora ascendente y las realizadas por hora descendente. Muestra permisos faltantes y carga 40 tarjetas por página.
- En Google Play el acceso a SMS está sujeto a condiciones estrictas, como ser la app predeterminada de SMS o encajar en una excepción. Esta compilación está orientada a pruebas privadas; la publicación exige revisar ese requisito.

Código: Java, Android SDK, SQLite y AlarmManager; sin librerías externas de ejecución.

## Cambios de la versión 1.8

- Al volver de la pantalla del permiso de alarmas puntuales, verifica el estado real, confirma el resultado y actualiza la lista. Reprograma las tareas futuras con alarma exacta al concederse.
- El botón de envío SMS solicita solo ese permiso y explica si Android lo concede o lo rechaza. Ajustes muestra el estado de ambos permisos.
- «Añadir desde contactos» solicita acceso a la agenda real del teléfono, muestra nombre y número con selección múltiple y búsqueda. Solo se guardan los contactos elegidos.

## Cambios de la versión 1.7

- La lista de contactos se reconstruye desde las tareas restauradas con números válidos, respetando los contactos editados existentes.
- El selector de destinatarios permite escribir letras del nombre o números, sin perder la selección al cambiar el filtro.
- La búsqueda de tareas filtra la lista mientras se escribe.
- El distintivo verde de WhatsApp usa un icono de teléfono blanco vectorial; evita el emoji rojo mostrado por algunos dispositivos.

## Cambios de la versión 1.6

- Iconos diferenciados en cada tarjeta: TXT para SMS y un teléfono blanco en círculo verde para WhatsApp.
- Botón flotante «+» sobre la lista y selector inferior para programar mensajes, respuesta automática SMS y reenvío automático SMS.

## Cambios de la versión 1.5

- Menú de tres puntos: enviar ahora con confirmación, editar, duplicar, fijar o desfijar, pausar o reactivar, marcar completada sin enviar y eliminar.
- Filtros por hoy, mañana, semana, mes, tareas repetidas, recordatorios, SMS y WhatsApp, además de búsqueda por nombre, número o texto. Telegram y Messenger aparecen como canales aún no disponibles.
- Las tareas fijadas conservan su posición en las listas y en el backup SQLite. El envío inmediato no altera la fecha programada original.

## Cambios de la versión 1.4

- Cada tarea programada muestra el nombre y el número de su destinatario.
- Al abrir una instalación actualizada, se reconstruyen los contactos y las asociaciones a partir de las filas originales del backup de Auto Text conservadas por las versiones anteriores. Nunca se reemplaza un número válido editado por el usuario.
- Si el backup original no contiene un número, la tarea permanece pausada hasta corregirla. En el archivo analizado, 15 tareas programadas tienen ese campo vacío; sus nombres no coinciden de forma única con otro número del backup.
