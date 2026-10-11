# Validación de MFA y perfil Android / Fire TV

Revisión del 11 de octubre de 2026. Versión firmada y verificada: 1.0.13, número interno 42, paquete com.snaptvnow.tv. El certificado y paquete coinciden con la APK 1.0.12 del propietario.

El acceso al administrador SNAP en producción se completó hoy con el autenticador del propietario. El MFA ya está activado; no se reemplazó el autenticador ni se renovaron códigos de recuperación. La comprobación del MFA de SNAP es independiente de la configuración de seguridad de GitHub y Cloudflare.

## Correcciones comprobables

- Un fallo de telemetría no interrumpe la lectura o envío de favoritos, progreso e idiomas. El informe conserva su identificador para reintentar.
- Un cambio pedido durante un envío obtiene otro turno de sincronización; el acuse del envío anterior no elimina el cambio nuevo.
- Un refresco con el mismo idioma conserva la pista concreta seleccionada. Cambiar el idioma remoto elimina solo la selección del tipo correspondiente, sin reiniciar ni adelantar el video.
- «Automático» elimina la preferencia de idioma anterior del tipo seleccionado.

La revisión `934f044a2231475fe467653e0deaef46b9c41b7c` pasó 164 pruebas, sin errores, fallos ni omisiones: 11 de perfil nativo, 10 de pistas de audio/subtítulos y 16 de recuperación en directo, además de las demás suites. [Android source check](https://github.com/juancanta89-tech/SnapTvNow/actions/runs/38163498056) compiló la APK debug. Las pruebas existentes cubren identidad original del catálogo, separación entre usuarios y servicios, progreso en segundos, cambios sin conexión, eliminación remota y respuestas tardías tras cerrar la sesión. Detalles en `native-test-verification.json`.

El administrador principal en producción rechazó un acceso con su contraseña correcta y sin segundo factor con `401 mfa_required`. El runtime real workerd/SQLite pasó su ensayo de MFA, revocación de sesiones y almacenamiento de perfil. El servidor staging desplegado pasó diez comprobaciones de perfil entre tres sesiones HTTP independientes más una cuenta distinta; ambas cuentas temporales se eliminaron y se cerraron sus sesiones. Detalles en `remote-profile-verification.json`. Estas sesiones simulaban los mensajes de Android, TV y web: no ejecutaban la APK ni un dispositivo físico.

La [firma de producción de 1.0.13](https://github.com/juancanta89-tech/SnapTvNow/actions/runs/38163498051) fue aprobada por el revisor obligatorio `juancanta89-tech` y terminó correctamente. La APK de producción está disponible para validación del propietario. Se comprobó su firma criptográfica, el resumen del contenido y el certificado de actualización frente a 1.0.12; también coinciden los hashes con el artefacto y los registros de GitHub. El archivo ocupa 44.401.069 bytes. Detalles en `production-apk-verification-1.0.13.json`. La ejecución de firma volvió a ejecutar las pruebas unitarias antes de compilar la versión release.

## Prueba física pendiente

Usar la misma cuenta de cliente SNAP y la misma APK firmada en el Galaxy Z Fold 5 y el Fire TV. Confirmar la versión instalada desde Ajustes / Aplicaciones; abrir la aplicación después de actualizar.

| Caso | Acción | Resultado que se debe confirmar |
| --- | --- | --- |
| Favoritos | Añadir una película en Android. Abrir Mi lista y pulsar Actualizar en Fire TV y la web. Quitarla en Fire TV y actualizar Android. | Aparece y desaparece en los otros equipos, sin duplicados. |
| Progreso | Reproducir una película durante dos minutos en Android; salir del reproductor. Actualizar Continuar viendo en Fire TV y reanudar. Repetir a la inversa. | Retoma cerca del punto guardado, sin saltar al inicio. |
| Idiomas | Usar un título que realmente tenga español e inglés. Elegir inglés y subtítulos; comprobar Fire TV y web. Cambiar a Automático o Desactivados y volver a comprobar. | Se conserva la preferencia disponible; el refresco no deshace la pista local ni reinicia el video. |
| TV en directo | ESPN 1 COL 1080 durante 15 minutos; cambiar cinco veces entre un canal 720 y uno 1080. | Imagen y sonido continuos, cambios recuperables, sin audio duplicado. |
| Recuperación | Desconectar la red brevemente, cambiar un favorito, restablecerla y volver a la app. | El cambio pendiente llega al otro dispositivo. |
| Cuenta distinta | Cerrar sesión y entrar con otro usuario de pruebas. | No aparecen los favoritos o el progreso de la cuenta anterior. |

No reproducir simultáneamente en más equipos de los permitidos por la cuenta. Registrar modelo, versión de Fire OS/Android, versión instalada, título/canal, duración, resultado y mensaje de error literal. No incluir contraseñas, tokens, URLs firmadas ni códigos del autenticador en capturas o informes.

El 11 de octubre se verificó previamente la decodificación real de fuentes ESPN/HBO/Discovery a través de la API web y Xtream; aquello no era una prueba en Android o Fire TV. La prueba física y una observación continua de 24 horas siguen pendientes hasta recibir sus resultados.
