# Pruebas manuales en dispositivos

Registro actualizado el 21/10/2026. El equipo confirma que ha probado manualmente
las aplicaciones en dispositivos. Las pruebas automáticas y la inspección de
recursos se documentan en sus informes correspondientes.

## Evidencias disponibles

| Aplicación | Dispositivo o entorno | Comprobación documentada | Fuente |
|---|---|---|---|
| Android | Redmi Note 11 | Prueba física en vertical y horizontal, sin recortes ni solapamientos, confirmada por el equipo. | [README Android](../gestorhogwartsmovil/README.md#validación-y-demostración-de-la-rúbrica). |
| Android | Redmi Pad SE | Prueba física en vertical y horizontal, sin recortes ni solapamientos, confirmada por el equipo. | [README Android](../gestorhogwartsmovil/README.md#validación-y-demostración-de-la-rúbrica). |
| Android | Dispositivo no identificado en el registro de ese caso | Consulta real de 985 alumnos; cierre y reapertura en modo avión con los 985 alumnos conservados en la caché SQLite. | [README Android](../gestorhogwartsmovil/README.md#validación-y-demostración-de-la-rúbrica). |
| Conjunto de aplicaciones | Dispositivos no detallados en la confirmación del 21/10/2026 | El equipo confirma pruebas manuales en dispositivos. No se asigna a esta confirmación una versión, plataforma o caso concreto adicional. | Confirmación del equipo incorporada en esta actualización documental. |

El README Android sitúa su bloque de validación en el 20/10/2026 y registra por
separado la comprobación del campo de URL del 21/10/2026. No identifica la versión
de Android ni el APK concreto de los dos dispositivos físicos; tampoco atribuye
el caso de los 985 alumnos a uno de ellos en particular.

## Comprobaciones automáticas relacionadas

- **JavaFX:** 100 pruebas correctas, JAR generado y carga de cuatro vistas en tres
  idiomas. La carga FXML no incluyó una sesión gráfica interactiva. Véase la
  [revisión JavaFX](javafx-review.md).
- **Android:** el README registra compilación, 17 pruebas unitarias, lint y
  pruebas instrumentadas en emuladores; la comprobación más reciente del campo
  de URL tiene 9 pruebas instrumentadas correctas. Los emuladores no sustituyen
  las pruebas físicas de la tabla anterior.
- **API y Unity:** el [informe de integración](integration-audit.md) conserva el
  CRUD probado contra MariaDB y la importación batch de Unity.

## Datos para completar el registro

Al documentar una nueva prueba o recuperar el detalle de las ya realizadas,
registra estos datos junto al resultado:

| Dato | Contenido |
|---|---|
| Fecha y responsable | Día de la prueba y persona que la realizó. |
| Aplicación y versión | Componente, commit o etiqueta de release y nombre del artefacto. |
| Dispositivo | Modelo, sistema operativo y versión; resolución o escala cuando corresponda. |
| Entorno | API utilizada y motores activos, sin contraseñas ni tokens. |
| Casos y resultado | Acciones realizadas, resultado esperado, resultado observado e incidencias. |
| Evidencias | Capturas, vídeo o registros revisados para retirar datos sensibles. |

El [guion de demo](https://github.com/dm2-ethazi/Reto01-Omni3/wiki/07-Demo-Funcional)
permite repetir el recorrido entre clientes. La comprobación de una release debe
identificar el artefacto utilizado; este registro no atribuye las pruebas previas a
una release específica sin esa información. Docker conserva su estado de
verificación pendiente.
