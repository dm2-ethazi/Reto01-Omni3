# Reto01 · Omni3 — Gestor Hogwarts

Esta Wiki documenta la rama `integration` de [Reto01-Omni3](https://github.com/koldoaso/Reto01-Omni3). El proyecto reúne una API de alumnado distribuido y tres clientes. El prototipo histórico permanece en `prototipo/` como implementación distinta.

## Recorrido recomendado

1. [Descripción y alcance](01-Descripcion-del-Proyecto.md)
2. [Arquitectura](02-Arquitectura-del-Sistema.md) y [modelo de datos](03-Modelo-de-Datos.md)
3. [Backend y contrato HTTP](04-Backend.md) y [persistencia](05-Persistencia-de-Datos.md)
4. [Instalación y ejecución](06-Ejecucion-del-Proyecto.md)
5. [Demo reproducible](07-Demo-Funcional.md)
6. [Problemas y mejoras](08-Problemas-y-Mejoras.md) y [conclusión](09-Conclusion.md)

## Estado comprobado el 20/10/2026

| Componente | Resultado en este entorno |
|---|---|
| API y MariaDB | CRUD real con TestClient y contenedor aislado: `200, 200, 201, 200, 200, 204, 404`. |
| JavaFX | Compila y pasa 100 pruebas Maven tras corregir `Ventanas.java`. No se abrió la interfaz gráfica. |
| Android | `assembleDebug`, 17 pruebas unitarias y `lintDebug` correctos. El AVD API 37 terminó 5 pruebas instrumentadas, luego el proceso cayó por memoria. |
| Unity | Importación batch con Editor 6000.3.25f1 correcta; la escena no se recorrió manualmente. |
| Cinco motores a la vez | Pendiente: la rama principal no incluye esquemas y semillas compatibles para Oracle, H2, HSQLDB y Derby. |

Esta tabla distingue comprobaciones de esta integración de las afirmaciones históricas que figuran en los README de cada componente.
