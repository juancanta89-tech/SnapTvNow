# Reproductor de películas y episodios — 1.0.3 (32)

## Causas encontradas en 1.0.2

* La barra tenía el máximo predeterminado de Android (100), pero los cálculos usaban 1000.
* La actualización se detenía cuando ExoPlayer todavía no había informado la duración.
* El cambio de posición solo se enviaba al soltar la barra con el dedo, omitiendo el control remoto.
* Una capa táctil sobre el video interceptaba los controles del reproductor.
* La posición solo se conservaba en campos de la Activity; no existía un marcador persistente por título.

## Solución

Se usan los controles de Media3 PlayerView para VOD: barra en milisegundos, tiempos transcurrido/total,
pausa, avance y retroceso. Los saltos y las flechas de la barra avanzan/retroceden 10 segundos.
La barra se puede arrastrar hasta cualquier posición disponible.

PlaybackHistory guarda un marcador independiente por usuario y por ID de película/episodio.
VodPlaybackSession lo restaura antes de preparar el video; guarda cada cinco segundos, al pausar,
al cambiar de posición y antes de liberar el reproductor. Si se interrumpe el arranque, conserva
el marcador anterior. Al terminar un título, su siguiente reproducción comienza desde el inicio.
El botón «Desde inicio» permite reiniciar en cualquier momento.

El progreso se conserva en cada dispositivo al cerrar y volver a abrir la aplicación.
No se sincroniza entre dispositivos. Una desinstalación o el borrado de los datos elimina esos marcadores.
La reanudación requiere que el archivo/enlace del proveedor permita buscar posiciones.

Los datos no incluyen URLs, contraseñas ni nombres de usuario en texto plano.
Los marcadores se vinculan a la cuenta que inició la reproducción, incluso si cambia la cuenta después.

## Comparación de código

Se revisaron las rutas de reproducción VOD de
[OwnTV](https://github.com/ahXN00/OwnTV/tree/931780b03f7476b5aadd99c75c6a1bf7ec4cce5e):
PlayerHudControls, PlayerHud, MiniPlayer, VodStage, OwnTVShell y MovieViewModel.
Se comparó su manejo de teclas, duración, identidad del contenido y guardado antes de detener el motor.
La referencia usa posiciones en milisegundos, asigna el progreso al perfil/título que inició la sesión
y guarda periódicamente y antes de cerrar el reproductor. Se aplican esos comportamientos en una
implementación propia para SNAPTVNOW, junto con los controles oficiales de Media3.
Esta revisión corresponde al código fuente público; no se ejecutó ni descompiló una APK de OwnTV.

## Verificación

Las pruebas Robolectric ejercitan los controles reales de Media3 con un Player de prueba:
arrastre al 50% del video, avance/retroceso con teclas de transporte y flechas, y duración tardía.
Las pruebas de sesión cubren la escritura de marcadores, pausa, cierre, reapertura, títulos/cuentas
independientes, arranque interrumpido, archivo reemplazado, finalización y cancelación de callbacks.
GitHub Actions ejecuta las pruebas antes de compilar y también antes de firmar la APK de producción.

Estas pruebas automatizadas no sustituyen una prueba del enlace concreto en un celular o TV real.
La clave de firma permanece en el entorno production de GitHub y conserva su aprobación requerida.
