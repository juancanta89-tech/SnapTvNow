# SNAPTVNOW TV: conexión al panel real

La app está configurada en modo real (`demo: false`) con el gateway:

https://snaptvnow-control.juancanta89.chatgpt.site/api/tv

Se utiliza el mismo usuario/contraseña de la línea. El gateway consulta los servidores habilitados del panel, valida estado y vencimiento, y crea una sesión cifrada de hasta 30 días. Al reiniciar, la app revalida la línea. Cerrar sesión revoca su acceso y los enlaces de reproducción vinculados.

El catálogo y los episodios se solicitan al panel. Los enlaces HTTPS de reproducción llevan tickets cifrados; los nombres del servidor original y las credenciales no aparecen en el enlace que recibe la app. El gateway conserva rangos HTTP de vídeo y reescribe listas HLS, incluidos segmentos, claves y listas anidadas.

El servidor original configurado por el administrador puede utilizar HTTP. El tramo TV-panel sí usa HTTPS; esto no convierte en cifrado un tramo HTTP del proveedor. La autorización y disponibilidad del catálogo siguen dependiendo del proveedor. No se transforma ni convierte un códec incompatible.

Para volver a una demo aislada, cambiar `demo` a `true` y reconstruir. El paquete entregado aquí utiliza acceso real. No incluye cuentas ni contraseñas.

Verificación: once pruebas de app y pruebas de gateway con proveedor simulado. La firma Samsung, instalación en TVs y reproducción con una cuenta real siguen pendientes. No se ha publicado en tiendas.
