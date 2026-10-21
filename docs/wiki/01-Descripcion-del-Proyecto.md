# Descripción del proyecto

**Reto01 · Omni3 / Gestor Hogwarts** permite consultar alumnado almacenado en varias bases de datos mediante una API REST común. El cliente JavaFX ofrece consulta y altas, ediciones y bajas; Android se centra en la consulta y en conservar una copia SQLite para uso sin conexión; Unity ofrece búsqueda y ficha con patronus. El nombre y los tres participantes proceden del README de la rama `javafxapp`.

El problema técnico es que el alumnado está repartido entre Oracle, MariaDB, H2, HSQLDB y Derby. La API presenta una representación `Alumno` única y etiqueta la fuente como `casa`. La solución evita que cada cliente abra conexiones a los cinco motores. El público inmediato son los participantes y evaluadores del reto académico; no hay evidencia de despliegue público estable.

## Objetivo del proyecto

Ofrecer un punto de acceso común al alumnado y reutilizarlo desde escritorio, móvil y Unity. El objetivo funcional es consultar y gestionar registros desde JavaFX, consultar una copia disponible sin conexión desde Android y explorar fichas y patronus desde Unity. El objetivo técnico es separar interfaz, contrato HTTP y persistencia, manteniendo visible la procedencia de los datos.

El recorrido mínimo de evaluación consiste en crear un alumno en MariaDB mediante la API, consultarlo desde los clientes, modificarlo, comprobar la persistencia y eliminarlo. La [demo](07-Demo-Funcional.md) concreta los resultados esperados y los límites de lo comprobado.

## Funciones implementadas

- API: listar, filtrar por prefijo de nombre, consultar por DNI, crear en MariaDB, editar y borrar donde exista el DNI, y leer catálogo de patronus.
- JavaFX: pantallas de estado y alumnado, formularios CRUD y traducciones castellano/euskera/inglés; depende de la API.
- Android: listado, búsqueda, ficha y caché Room con verificación al reconectar; depende de `GET /alumnos` para datos remotos.
- Unity: escena `PatronusDirectory`, búsqueda por nombre y ficha con imagen si la URL existe; depende de `GET /alumnos` y `GET /alumnos/{dni}`.
- `prototipo/`: otra solución FastAPI con sincronización y esquema `ESTUDIANTE`, conservada para trazabilidad. Sus rutas y tablas no forman parte del contrato de la API principal.

## Alcance y límites

La rama integrada deja una ruta de demostración **MariaDB + API + clientes**. La configuración de cinco motores aparece en el código de la API, pero la rama actual no aporta esquemas `ALUMNOS` compatibles ni semillas para los cuatro motores restantes. La Wiki no supone que el prototipo los sustituya: usa otra arquitectura y otra tabla. No hay autenticación ni autorización en los endpoints de alumnado; despliegue expuesto a Internet requeriría ambas. La imagen del patronus de ejemplo queda vacía porque no se suministró una imagen verificable.

Continuar: [arquitectura](02-Arquitectura-del-Sistema.md) · [ejecución](06-Ejecucion-del-Proyecto.md).
