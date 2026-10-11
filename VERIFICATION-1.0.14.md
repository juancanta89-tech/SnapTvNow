# Recuperación y búfer de reproducción 1.0.14

Versión 1.0.14, número interno 43. El cambio aborda reinicios prematuros y mediciones nativas; no certifica todavía estabilidad durante 24 horas en equipos físicos.

## Comportamiento

- La carga de un canal establecido deja de provocar una reconstrucción automática a los tres segundos. El watchdog considera el avance del búfer y las transferencias de red; usa la duración objetivo de segmentos cuando hay un manifiesto HLS.
- Sin progreso, el margen mínimo es 15 segundos. En HLS es dos duraciones objetivo más dos segundos, acotado a 15–45 segundos. La carga total sin obtener reproducción queda acotada a 45–90 segundos, incluso si siguen llegando bytes. Las pequeñas actualizaciones de listas no renuevan por sí solas el margen.
- Un timeout se vuelve a comprobar al ejecutar el reintento. Si reaparecen datos, imagen o reproducción antes de ese momento, se conserva el reproductor. Un error fatal de decodificador conserva la reconstrucción, aunque haya tráfico.
- Siguen vigentes la detección de imagen congelada con audio, la recuperación con un decodificador nuevo, la liberación del audio anterior y las cancelaciones al pausar, perder foco, cerrar o cambiar de canal. No se selecciona otro canal automáticamente.
- TV y VOD usan parámetros separados. TV: objetivo de 20–40 segundos de contenido, inicio de 2 segundos y recuperación de 6 segundos; VOD: 30–60 segundos, inicio de 2,5 segundos y recuperación de 5 segundos. Los objetivos de bytes son 32/48 MiB con prioridad de tamaño. No son límites de memoria total de la aplicación ni garantizan que el proveedor suministre ese contenido.
- Se conserva la posición de directo que decide Media3; no se introduce un salto manual ni un retraso fijo de emisión. Una recuperación puede tardar más al esperar una reserva de contenido suficiente.
- Conexión HTTP: 10 segundos; espera de lectura: 20 segundos. Las transferencias que progresan conservan la conexión, sujetas al watchdog anterior.

## Medición

Los informes nativos incorporan diferencias de los contadores reales de fotogramas renderizados/perdidos y del tráfico de red leído por el reproductor. `decoded` representa renderizados más perdidos; no contabiliza descartes intencionales al buscar. Se preservan los límites y campos del contrato de métricas desplegado. Los bytes incluyen cargas de vídeo y metadatos; no representan ancho de banda facturado. No se guardan URL, títulos, cabeceras ni identidad de equipos en estos contadores. Android y Fire TV siguen compartiendo el grupo Android del servidor.

## Validación

Las regresiones añaden fuentes silenciosas, segmentos de 12 segundos, avance lento de búfer y bytes, metadatos sin señal, datos que nunca producen reproducción, recuperación durante backoff, error fatal durante un timeout, callbacks duplicados y recuperación de vídeo tras buffering prolongado. Conservan las pruebas de dos horas de reloj simulado, cambio de canal, pausa, foco de audio y reemplazo de decodificador. Se comprueban también diferencias de métricas, ausencia de medición, límites del servidor y transferencias concurrentes.

Compilación, resultados unitarios y firma de producción: pendientes de la ejecución de CI para esta revisión. No hay acceso al Android ni al Fire TV físicos del propietario. Las pruebas de señales reales durante 24 horas, capacidad del proveedor y alternativa 720 del mismo canal permanecen pendientes.

Referencias: https://developer.android.com/reference/androidx/media3/exoplayer/DefaultLoadControl.Builder y https://developer.android.com/reference/androidx/media3/exoplayer/analytics/AnalyticsListener.
