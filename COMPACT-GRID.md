# Cuadrícula elegida — versión 1.0.5 (34)

Implementa la opción 2 aprobada: cuatro columnas en ventanas de celular abierto de al menos
600 dp y cinco en Android TV/Fire TV. Un celular en horizontal sigue siendo un celular;
el modo TV se detecta mediante UI_MODE_TYPE_TELEVISION o la característica Leanback.
En la pantalla cerrada o una ventana estrecha se mantienen dos columnas legibles.
Abrir, cerrar o rotar el celular recalcula la cuadrícula usando el tamaño actual de la ventana.

Los canales muestran el logo completo con FIT_CENTER y el nombre centrado debajo en dos líneas,
sin superponer el texto sobre el logo. Usan fondo azul oscuro, borde turquesa de selección,
corazón independiente y un icono de TV cuando el proveedor no entrega una imagen válida.
Los logos proceden del catálogo real; las ilustraciones de las propuestas no se añaden a la app.
La misma cuadrícula sirve para categorías, catálogos, favoritos y resultados de búsqueda.
Las películas y series conservan su carátula vertical.

Cada página añade ocho filas y conserva las tarjetas previas. Ver más permite alcanzar el
catálogo completo y dirige el foco a las nuevas tarjetas; desaparece al llegar al final.
Los huecos de la última fila no reciben foco ni reproducen contenido.
Se mantienen las funciones de pistas de audio, subtítulos y reanudación de las versiones previas.

Las pruebas comprueban medidas y columnas reales de vistas Android, límites de tamaño,
paginación más allá de 300 títulos, disposición de logo y nombre, interacción con favoritos
y foco del mando. No sustituyen una comprobación física del celular y la TV.
