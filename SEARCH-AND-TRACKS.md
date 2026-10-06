# Audio, subtítulos y búsqueda — 1.0.4 (33)

La lupa conserva la sección desde la que se abre y permite elegir Películas, Series o Canales.
Consulta el catálogo completo de esa sección sin category_id, no solamente los títulos de la
carpeta abierta. El análisis del catálogo no se limita a 150 títulos. La búsqueda ignora
mayúsculas y acentos, requiere todas las palabras del texto y muestra la carpeta de cada resultado.
Se filtra en segundo plano y se muestran bloques de 40 resultados con Ver más; el índice completo
permanece asociado a la sesión del cliente y se vuelve a cargar al actualizar contenido.
Los resultados de búsquedas antiguas se descartan cuando cambia la consulta o la pantalla.

El reproductor de películas y episodios incluye Audio y Subtítulos, accesibles por toque y mando.
Media3 identifica las pistas del archivo o manifiesto reproducido. Cada selector muestra sus pistas
reales y cuáles soporta el dispositivo; permite seleccionar una pista, volver a automático y
desactivar subtítulos. Cambia los parámetros del mismo reproductor, sin volver a preparar el video
ni modificar su posición. Conserva la elección cuando reconstruye ese título por rotación.
PlayerView renderiza los subtítulos; no se inventan idiomas ni archivos que el proveedor no entrega.

Las pruebas cubren catálogos de más de 300 títulos en varias carpetas, separación de tipos,
acentos, caché por sesión, fallos y reintentos, selección de pistas, pistas incompatibles,
subtítulos desactivados y la interacción con el diálogo real. Se mantienen las pruebas de avance,
retroceso y reanudación de 1.0.3. La conexión a un servidor y el renderizado/decodificación en una
TV física requieren comprobación en ese dispositivo; no se presentan como pruebas realizadas aquí.
