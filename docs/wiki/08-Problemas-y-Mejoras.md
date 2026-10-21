# Problemas y mejoras

## Resueltos en `integration`

| Hallazgo verificable | Acción |
|---|---|
| Ramas con historias parcialmente independientes y archivos raíz en conflicto. | Rama huérfana y merges de dos padres para cada punta; proyectos conservados en directorios separados. |
| JavaFX no compilaba: `Screen.getVisualBounds()` devuelve `Rectangle2D`, pero `Ventanas.java` usaba `Bounds`. | Sustitución de tipo; 100 pruebas Maven correctas. |
| Android tenía un túnel ngrok fijo en `ApiClient.kt`. | URL desacoplada del cliente HTTP, configurable por Gradle/entorno y editable desde la interfaz; la rama actual conserva un túnel como valor inicial de compilación. |
| API sin esquema MariaDB en la rama principal. | DDL de `ALUMNOS` y `PATRONUS_IMG` montado en inicialización de un volumen nuevo. |

## Pendientes por prioridad

1. **Alta — cinco motores reproducibles.** Añadir DDL y datos coherentes para Oracle, H2, HSQLDB y Derby; probar `DB_ENGINES` completo. El SQL del prototipo usa `ESTUDIANTE` y no cumple este contrato.
2. **Alta — seguridad de la API.** Añadir autenticación y autorización antes de exposición pública; revisar los mensajes 503 que incorporan detalles de excepciones.
3. **Alta — consistencia multi motor.** PUT/DELETE pueden actualizar unas fuentes y fallar en otra; definir política de transacciones, idempotencia y recuperación.
4. **Media — validación de patronus.** PUT no comprueba que el nombre exista en `PATRONUS_IMG`; alinear con POST y valorar una FK física donde proceda.
5. **Media — pruebas integradas.** Crear CI con MariaDB efímera y pruebas de contratos JSON y clientes; repetir la comprobación gráfica conjunta en un entorno identificado. La importación batch de Unity pasó una vez. El primer intento instrumentado Android de la integración falló por memoria; su README documenta después 9 pruebas correctas el 21/10/2026. Esa evidencia posterior no sustituye una prueba conjunta de los tres clientes contra esta instalación MariaDB.
6. **Media — configuración de producción.** URLs y credenciales por entorno, TLS, límites de acceso y configuración de imágenes reales. La URL de Android queda embebida en el APK al compilar.
7. **Baja — documentación histórica.** Ajustar rutas personales y cifras de pruebas en README de componentes cuando sus autores confirmen el entorno objetivo.
8. **Media — eventos y métricas.** Añadir registro estructurado y correlación de `request-id`, auditoría de cambios y métricas de peticiones, errores y latencia. Hoy existen registros de diagnóstico y estados HTTP, pero no un sistema de monitorización persistente ni alertas.

No se atribuyen estas incidencias a experiencias personales del equipo. Son deducciones de código, compilaciones y pruebas ejecutadas durante la integración. Continuar: [conclusión](09-Conclusion.md).
