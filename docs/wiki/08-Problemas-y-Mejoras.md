# Problemas y mejoras

## Resueltos en `integration`

| Hallazgo verificable | Acción |
|---|---|
| Ramas con historias parcialmente independientes y archivos raíz en conflicto. | Rama huérfana y merges de dos padres para cada punta; proyectos conservados en directorios separados. |
| JavaFX no compilaba: `Screen.getVisualBounds()` devuelve `Rectangle2D`, pero `Ventanas.java` usaba `Bounds`. | Sustitución de tipo; 100 pruebas Maven correctas. |
| Android tenía un túnel ngrok fijo en `ApiClient.kt`. | URL por propiedad Gradle o entorno en `BuildConfig`, con valor de emulador local. |
| API sin esquema MariaDB en la rama principal. | DDL de `ALUMNOS` y `PATRONUS_IMG` montado en inicialización de un volumen nuevo. |

## Pendientes por prioridad

1. **Alta — cinco motores reproducibles.** Añadir DDL y datos coherentes para Oracle, H2, HSQLDB y Derby; probar `DB_ENGINES` completo. El SQL del prototipo usa `ESTUDIANTE` y no cumple este contrato.
2. **Alta — seguridad de la API.** Añadir autenticación y autorización antes de exposición pública; revisar los mensajes 503 que incorporan detalles de excepciones.
3. **Alta — consistencia multi motor.** PUT/DELETE pueden actualizar unas fuentes y fallar en otra; definir política de transacciones, idempotencia y recuperación.
4. **Media — validación de patronus.** PUT no comprueba que el nombre exista en `PATRONUS_IMG`; alinear con POST y valorar una FK física donde proceda.
5. **Media — pruebas integradas.** Crear CI con MariaDB efímera, pruebas de contratos JSON y clientes. Android necesita pruebas instrumentadas en emulador y Unity una importación/ejecución repetible.
   La importación batch de Unity pasó una vez; el AVD Android API 37 de esta integración cerró el proceso instrumentado por falta de memoria después de cinco casos correctos.
6. **Media — configuración de producción.** URLs y credenciales por entorno, TLS, límites de acceso y configuración de imágenes reales. La URL de Android queda embebida en el APK al compilar.
7. **Baja — documentación histórica.** Ajustar rutas personales y cifras de pruebas en README de componentes cuando sus autores confirmen el entorno objetivo.

No se atribuyen estas incidencias a experiencias personales del equipo. Son deducciones de código, compilaciones y pruebas ejecutadas durante la integración. Continuar: [conclusión](09-Conclusion.md).
