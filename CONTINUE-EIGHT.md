# Continuar viendo — 1.0.8

La pantalla conserva como máximo 8 películas y 8 series por cuenta y dispositivo. Todo muestra hasta 16 títulos; los filtros muestran el número de títulos. Los más recientes aparecen primero. Una serie ocupa una sola posición con su último capítulo.

Al agregar un noveno título del mismo tipo, sale el menos reciente. La salida queda guardada: quitar un título visible no hace reaparecer los que ya salieron. Volver a reproducir uno de esos títulos lo devuelve al principio.

En celular, cada fila incluye miniatura, nombre, capítulo/minuto, progreso y un menú ⋯ con «Quitar de Continuar viendo». Mantener pulsada la fila también abre ese menú. En TV, los títulos se presentan en cinco columnas con selección visible y botones Continuar y Quitar de Continuar viendo accesibles con el control remoto. Las celdas vacías no se pueden seleccionar.

Quitar una serie oculta la serie completa de esta lista. Se conservan los minutos de todos sus capítulos, los capítulos vistos y la recomendación del último/siguiente episodio. Quitar una película conserva su minuto para la siguiente reproducción. La actualización conserva los marcadores de versiones anteriores; cargar un catálogo no vuelve a mostrar títulos quitados.

Las miniaturas proceden del catálogo real. Se guardan únicamente píxeles JPEG en la caché privada, con un máximo de 32 imágenes de hasta 320 píxeles por lado. Borrar esa caché puede retirar portadas, sin borrar el progreso. No se persisten URLs del proveedor ni credenciales en el historial.

Las pruebas automatizadas cubren límites independientes, expulsión, eliminación, cuentas, reapertura, migración, selección de capítulos, menús y navegación por foco en TV. Se ejecutan en GitHub Actions junto con las pruebas existentes y la compilación Android. La validación física en celulares y TVs corresponde a la APK firmada y sigue pendiente.
