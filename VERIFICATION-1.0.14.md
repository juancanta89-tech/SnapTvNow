# Recuperación y búfer de reproducción 1.0.14

Versión 1.0.14, número interno 43. El cambio aborda reinicios prematuros y mediciones nativas; no certifica todavía estabilidad durante 24 horas en equipos físicos.

## Comportamiento

- La carga de un canal establecido deja de provocar una reconstrucción automática a los tres segundos. El watchdog considera el avance del búfer y las transferencias de red; usa la duración objetivo de segmentos cuando hay un manifiesto HLS.
- Sin progreso, el margen mínimo es 15 segundos. En HLS es dos duraciones objetivo más dos segundos, acotado a 15–45 segundos. Tras 45–90 segundos de carga sin obtener reproducción se programa la recuperación, incluso si siguen llegando bytes; al ejecutar el reintento se aplica el backoff existente de 250 ms a 8 segundos. Las pequeñas actualizaciones de listas no renuevan por sí solas el margen.
- Un timeout se vuelve a comprobar al ejecutar el reintento. Si reaparecen datos, imagen o reproducción antes de ese momento, se conserva el reproductor. Un error fatal de decodificador conserva la reconstrucción, aunque haya tráfico.
- Siguen vigentes la detección de imagen congelada con audio, la recuperación con un decodificador nuevo, la liberación del audio anterior y las cancelaciones al pausar, perder foco, cerrar o cambiar de canal. No se selecciona otro canal automáticamente.
- TV y VOD usan parámetros separados. TV: objetivo de 20–40 segundos de contenido, inicio de 2 segundos y recuperación de 6 segundos; VOD: 30–60 segundos, inicio de 2,5 segundos y recuperación de 5 segundos. Los objetivos de bytes son 32/48 MiB con prioridad de tamaño. No son límites de memoria total de la aplicación ni garantizan que el proveedor suministre ese contenido.
- Se conserva la posición de directo que decide Media3; no se introduce un salto manual ni un retraso fijo de emisión. Una recuperación puede tardar más al esperar una reserva de contenido suficiente.
- Conexión HTTP: 10 segundos; espera de lectura: 20 segundos. Las transferencias que progresan conservan la conexión, sujetas al watchdog anterior.

## Medición

Los informes nativos incorporan diferencias de los contadores reales de fotogramas renderizados/perdidos y del tráfico de red leído por el reproductor. `decoded` representa renderizados más perdidos; no contabiliza descartes intencionales al buscar. Se preservan los límites y campos del contrato de métricas desplegado. Los bytes incluyen cargas de vídeo y metadatos; no representan ancho de banda facturado. No se guardan URL, títulos, cabeceras ni identidad de equipos en estos contadores. Android y Fire TV siguen compartiendo el grupo Android del servidor.

## Validación

Las regresiones añaden fuentes silenciosas, segmentos de 12 segundos, avance lento de búfer y bytes, metadatos sin señal, datos que nunca producen reproducción, recuperación durante backoff, error fatal durante un timeout, callbacks duplicados y recuperación de vídeo tras buffering prolongado. Conservan las pruebas de dos horas de reloj simulado, cambio de canal, pausa, foco de audio y reemplazo de decodificador. Se comprueban también diferencias de métricas, ausencia de medición, límites del servidor y transferencias concurrentes.

El [control de código de la PR 11](https://github.com/juancanta89-tech/SnapTvNow/actions/runs/38169218331) terminó correctamente: 180 pruebas, cero fallos, errores u omisiones, y APK de depuración compilado. El informe XML se descargó y comprobó contra el SHA-256 publicado por GitHub. Las suites de recuperación, métricas y transferencias contienen respectivamente 27, 6 y 2 pruebas; la duración de dos horas corresponde a un reloj simulado. Detalles reproducibles en `native-test-verification-1.0.14.json`.

El [control integrado en main](https://github.com/juancanta89-tech/SnapTvNow/actions/runs/38169640662) y la [firma de producción](https://github.com/juancanta89-tech/SnapTvNow/actions/runs/38169640700) también terminaron correctamente, sobre `ccd2ef44d61cce7df395b93a0b8531dcb45e284a`. La revisión del entorno protegido `production` se aprobó para esa ejecución; se mantuvieron las protecciones y la clave privada permaneció en el runner.

El APK de producción contiene `com.snaptvnow.tv`, versión 1.0.14 / 43, sin modo de depuración. Se verificaron localmente la firma APK v2 y el digest de su contenido, además de la comprobación con `apksigner` en CI. El certificado coincide con 1.0.13, por lo que permite actualizar esa instalación sin desinstalar. SHA-256 del APK: `4122e1d90338d16fa687fe6f8707f74da0c568586ad1730384caf9c0579aa2ee`. Detalles en `production-apk-verification-1.0.14.json`.

No hay acceso al Android ni al Fire TV físicos del propietario. Las pruebas de señales reales durante 24 horas, capacidad del proveedor y alternativa 720 del mismo canal permanecen pendientes. Este cambio mejora el tratamiento de una señal lenta o interrumpida; no repara una fuente 1080 caída en el proveedor ni puede generar vídeo que la fuente no entrega.

Referencias: https://developer.android.com/reference/androidx/media3/exoplayer/DefaultLoadControl.Builder y https://developer.android.com/reference/androidx/media3/exoplayer/analytics/AnalyticsListener.
