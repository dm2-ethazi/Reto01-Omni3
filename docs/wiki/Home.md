# Reto01 · Omni3 — Gestor Hogwarts

Esta Wiki documenta la rama [`integration` de Reto01-Omni3](https://github.com/koldoaso/Reto01-Omni3/tree/integration). El proyecto reúne una API de alumnado distribuido y tres clientes. El prototipo histórico permanece en `prototipo/` como implementación distinta. La base de código revisada para esta edición es el commit [`d48eb92`](https://github.com/koldoaso/Reto01-Omni3/commit/d48eb92b5665f01b9eb27eac5ad000d27f331ff8).

## Repositorio de entrega

El código se encuentra en `https://github.com/koldoaso/Reto01-Omni3.git` y esta documentación en el repositorio independiente `https://github.com/koldoaso/Reto01-Omni3.wiki.git`. Ambos pertenecen al mismo proyecto; `.wiki` se intercala antes de `.git`.

```bash
git clone https://github.com/koldoaso/Reto01-Omni3.wiki.git
```

La entrega documental es esta única Wiki: portada y nueve apartados, con índice lateral y créditos al pie. La carpeta `docs/wiki` del código conserva una copia de lectura, no un segundo proyecto. Los nombres `_Sidebar.md` y `_Footer.md` son los archivos especiales que GitHub usa para mostrar esos elementos en todas las páginas.

## Recorrido recomendado

1. [Descripción del proyecto](01-Descripcion-del-Proyecto.md): problema, objetivo y alcance.
2. [Arquitectura del sistema](02-Arquitectura-del-Sistema.md): esquema, responsabilidades y decisiones.
3. [Modelo de datos](03-Modelo-de-Datos.md): entidades, relaciones y datos almacenados o calculados.
4. [Backend](04-Backend.md): endpoints, validaciones, flujo, eventos y métricas.
5. [Persistencia de datos](05-Persistencia-de-Datos.md): motores, almacenamiento, recuperación e integridad.
6. [Ejecución del proyecto](06-Ejecucion-del-Proyecto.md): requisitos y arranque desde cero.
7. [Demo funcional](07-Demo-Funcional.md): casos, resultados esperados y guion de exposición.
8. [Problemas y mejoras](08-Problemas-y-Mejoras.md): dificultades y prioridades pendientes.
9. [Conclusión](09-Conclusion.md): aprendizajes y valoración técnica.

## Evidencias disponibles al 21/10/2026

| Componente | Resultado en este entorno |
|---|---|
| API y MariaDB | CRUD real con TestClient y contenedor aislado: `200, 200, 201, 200, 200, 204, 404`. |
| JavaFX | Compila y pasa 100 pruebas Maven tras corregir `Ventanas.java`. No se abrió la interfaz gráfica. |
| Android | El README del componente documenta APK, 17 pruebas unitarias, lint y 9 instrumentadas correctas el 21/10/2026; también recoge pruebas físicas del equipo. Sustituye el resultado incompleto del intento de integración anterior. |
| Unity | Importación batch con Editor 6000.3.25f1 correcta; la escena no se recorrió manualmente. |
| Cinco motores a la vez | Pendiente: la rama principal no incluye esquemas y semillas compatibles para Oracle, H2, HSQLDB y Derby. |

Las evidencias de API, JavaFX y Unity proceden del [informe de integración del 20/10/2026](https://github.com/koldoaso/Reto01-Omni3/blob/integration/docs/integration-audit.md). La actualización Android procede de su [README versionado](https://github.com/koldoaso/Reto01-Omni3/blob/integration/gestorhogwartsmovil/README.md). Esta edición de la Wiki no vuelve a ejecutar las pruebas de los componentes.

La documentación describe el alcance implementado y sus límites. La demostración mínima reproducible utiliza MariaDB; la ejecución gráfica conjunta de los tres clientes y la puesta en marcha completa de cinco motores requieren las comprobaciones indicadas en [demo](07-Demo-Funcional.md) y [mejoras](08-Problemas-y-Mejoras.md).
