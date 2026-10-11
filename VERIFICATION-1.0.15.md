# Recuperación limitada de canales 1.0.15

Versión 1.0.15 / 44. Conserva el búfer y las mediciones de 1.0.14. Tras tres reconstrucciones automáticas que no logran estabilizar el canal, el siguiente fallo detiene la recuperación y permite reintentar, elegir una alternativa 720 identificada o volver a canales. Treinta segundos continuos de reproducción saludable restablecen el contador. Un timeout que se cancela porque vuelve la señal no consume un intento real.

La alternativa exige etiquetas explícitas 1080 y 720, el mismo nombre restante (incluidos región, idioma y número de canal), sección, carpeta, ID de categoría y origen/cuenta de reproducción. En la API administrada exige también el mismo servidor de `smn_profile`. Identidades EPG contradictorias y varias alternativas coincidentes impiden ofrecerla. No se deduce equivalencia entre HD/FHD ni se selecciona otra calidad automáticamente. La etiqueta del catálogo no confirma resolución ni disponibilidad real de la fuente.

Mientras se espera una elección se detienen el watchdog y el reproductor. El giro de pantalla o regreso desde segundo plano conserva las opciones sin descargar de nuevo la señal. Las respuestas de una sesión anterior no pueden cambiar el nuevo canal. Los botones admiten toque y navegación con mando; los controles de transporte no reinician la señal por debajo de las opciones. La elección 720 no modifica automáticamente favoritos ni posiciones VOD.

Las regresiones cubren coincidencia única, ambigüedad, proveedor/cuenta, carpeta/EPG, selección explícita, cuatro fallos consecutivos, callbacks antiguos, pausa, segundo plano y configuración. La compilación y ejecución de la suite están pendientes al preparar este cambio; se registrarán los resultados verificados antes de entregar el APK.

Pendiente en equipos físicos: Android y Fire TV, navegación con mando, fuentes ESPN 1080/720 reales y una sesión de 24 horas. La consulta de métricas de producción no se completó porque el acceso al panel fue declinado. Este cambio no repara una fuente caída del proveedor.
