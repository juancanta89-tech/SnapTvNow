# SNAPTVNOW Android

Proyecto Android de SNAPTVNOW. La verificación automática compila el código fuente y genera una APK de prueba separada; la distribución de producción exige la firma existente.

## Audio en vivo (1.0.20)

La app incorpora el módulo FFmpeg oficial de Media3, compilado desde revisiones fijas con decodificadores de audio. Cubre MPEG Layer II (MP2) sin depender de que Fire TV publique un MediaCodec para ese formato. En TV en vivo, «Audio compatible» está activado inicialmente: prioriza la decodificación por software y entrega PCM estéreo, mezclando el canal central de sonido envolvente hacia ambos altavoces. El video sigue usando su renderer del dispositivo y no se cambia de canal ni de resolución. Películas y series conservan la elección del decodificador del dispositivo con FFmpeg como alternativa para formatos no admitidos.

El botón Audio de TV en vivo permite elegir la pista real, cambiar entre salida compatible/automática y copiar un diagnóstico local sin cuentas, enlaces ni tokens. La preferencia de salida pertenece al dispositivo y no se sincroniza hacia otros TVs. El cambio conserva el canal y la pausa; libera el reproductor anterior antes de abrir el reemplazo.

La procedencia, licencias y reproducción de la compilación se documentan en [audio-decoder/PROVENANCE.md](audio-decoder/PROVENANCE.md). CI debe superar las pruebas Android de MP2/AAC/AC3/EAC3 con PCM no silencioso, además de las regresiones existentes. La audibilidad real en el Fire TV y en el canal reportado requiere comprobación física; las pruebas de muestras generadas no certifican las señales del proveedor.

## Estado VPN

La app carga las ubicaciones VPN habilitadas. Al elegir una, solicita el perfil al panel con las credenciales de la línea, pide el permiso VPN de Android y arranca el motor OpenVPN. El permiso del sistema no se puede omitir. Esta rama todavía requiere probar conexión, tráfico, reconexión y desconexión en un dispositivo Android antes de distribuir una APK.

La compilación obtiene el motor `ics-openvpn` desde el código público de `mysteriumnetwork/openvpn_dart`, revisión `e0b86e5c4f0c05a9fc3aa1ca9311275663657b68`, y verifica el SHA-256 del AAR en CI. Su [procedencia y fuente correspondiente](https://github.com/mysteriumnetwork/openvpn_dart/blob/e0b86e5c4f0c05a9fc3aa1ca9311275663657b68/android/localmaven/PROVENANCE.md) están documentadas allí. `ics-openvpn` usa GPL-2.0: cualquier APK distribuida con este motor necesita cumplir sus obligaciones de código fuente y avisos de licencia.

La entrega directa de una contraseña VPN compartida a un dispositivo autorizado permite que alguien con control de ese dispositivo la extraiga. El panel exige una línea activa y entrega el perfil solo por HTTPS, pero eso no equivale a credenciales individuales revocables. Ante una filtración se debe rotar la contraseña Surfshark.

La contraseña Surfshark y la clave de firma de producción no deben añadirse al repositorio ni a los artefactos de compilación.

## Perfil web/app (1.0.13)

Las cuentas conectadas a `https://api.snaptvnow.com` sincronizan favoritos, progreso y preferencias de idioma mediante la API de perfil. Xtream conserva sus IDs globales de reproducción; `smn_profile` aporta la referencia original de servidor/título/episodio usada por la web. La app no envía credenciales de servidores ajenos a la API de SNAP y no guarda tokens en el almacenamiento del perfil. Las sesiones continúan cifradas con Android Keystore.

Los cambios pendientes se conservan por cuenta para reintentar al volver a primer plano. La mezcla usa fechas y tombstones; una respuesta antigua o de una sesión cerrada no cambia la cuenta actual. Progreso remoto en segundos se convierte a milisegundos y no reemplaza un avance local más reciente. Los títulos remotos se consultan al abrir Mi lista/Continuar viendo; las temporadas se limitan a ocho por carga. El contenido no disponible conserva su referencia sin inventar una URL de reproducción.

Los favoritos y el progreso locales se separan por usuario y servidor. El historial anterior no identificaba el servidor y se conserva almacenado sin mezclarlo automáticamente con una cuenta de otro servicio. Los antiguos valores globales `fav_*` no tenían propietario y no se asignan automáticamente a otro usuario. Cambiar de cuenta borra los favoritos visibles de la sesión anterior. La configuración exige HTTPS y, si no hay una configuración remota o guardada válida, usa el dominio propio. Cada cliente Xtream conserva su servidor: una petición tardía no utiliza el servidor de otra sesión.

Validación requerida antes de distribuir: ejecutar las pruebas Robolectric de sincronización, aislamiento y reproductor, compilar la variante debug, y probar en teléfono/TV con dos cuentas ficticias y la web. La distribución release conserva el applicationId y requiere la clave de firma de producción existente. No generar una clave alternativa para actualizar una APK instalada.

El panel distingue métricas web/Android: primer cuadro, tiempo reproduciendo/cargando, cortes y errores. Android mide bytes de red y cuadros renderizados/descartados cuando Media3 los proporciona. Los informes agregados se reintentan con el mismo identificador, con hasta veinte en memoria; no se guardan con el perfil y se descartan al cerrar la cuenta. No incluyen usuario, dispositivo, título ni URL. No son una medición de facturación.

La guía muestra hasta 24 programas cuando el proveedor los ofrece, decodifica títulos UTF-8/base64, omite programas terminados y marca el actual. Sus errores no interrumpen la reproducción ni se aplican después de cambiar de cuenta.

## Calidad por reproducción (1.0.17)

La app pide un recibo cifrado de monitorización al abrir contenido mediante su API HTTPS. Cada reproductor utiliza un identificador aleatorio temporal para separar reproducciones simultáneas detrás del mismo router. Los informes incluyen el recibo, las dimensiones observadas y su antigüedad; no se persisten recibos ni métricas. El servicio valida usuario, versión de acceso, reserva y reproducción antes de actualizar las barras del panel. Una reconexión descarta el intervalo de transición para la calidad individual; los informes de una conexión anterior no modifican la nueva.

La primera medición se envía aproximadamente a los diez segundos y después cada veinte; el panel refresca cada treinta segundos. La ausencia de datos recientes se muestra como «Sin medición». Un error de monitorización no detiene la reproducción. Los reproductores externos requieren una integración propia para reportar esta información.

La versión 1.0.17 usa código 46, conserva `com.snaptvnow.tv` y debe firmarse con el certificado de producción existente de 1.0.16. La instalación encima de 1.0.16 requiere una prueba física; no desinstalar la versión anterior para esa prueba.
