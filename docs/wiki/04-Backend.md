# Backend: API Alumnos

La aplicación principal es `api/src/proyecto_reto1/main.py` (FastAPI). `main_tareas.py` es una práctica independiente con lista en memoria; sus rutas `/tareas` no pertenecen al Gestor Hogwarts. La API principal no implementa `/health`: para comprobarla sin consultar bases se usa `/openapi.json`; para comprobar bases se usa `/alumnos`.

## Contrato HTTP implementado

| Método y ruta | Entrada y operación | Éxito | Errores relevantes |
|---|---|---|---|
| `GET /alumnos` | `nombre` opcional, prefijo 1–100 caracteres; agrega las fuentes activas. | 200, lista `Alumno[]` | 422 parámetro; 503 fuente fallida. |
| `GET /alumnos/{dni}` | Busca en orden de fuentes, primera coincidencia. | 200, `Alumno` | 404 inexistente; 503 fuente fallida. |
| `GET /patronus` | Consulta `PATRONUS_IMG` en MariaDB. | 200, lista `{nom_eng,url}` | 503 MariaDB ausente o fallida. |
| `POST /alumnos` | JSON `AlumnoEntrada`; DNI único global, patronus validado; inserta en MariaDB. | 201, `Alumno` | 409 duplicado; 422 datos/patronus; 503 fuente fallida. |
| `PUT /alumnos/{dni}` | JSON parcial con nombre, apellido, sexo, patronus o imagen; actualiza cada fuente donde exista. | 200, primer `Alumno` actualizado | 404 inexistente; 422 cuerpo/valores; 503 fuente fallida. |
| `DELETE /alumnos/{dni}` | Borra el DNI de cada fuente donde exista. | 204, sin cuerpo | 404 inexistente; 503 fuente fallida. |

`Alumno` devuelve `id`, `nombre`, `apellido`, `sexo`, `patronus`, `patronus_url`, `imagen` y `casa`. POST exige `id` (1–10) y `nombre` (1–100); el resto es opcional. `sexo` admite `H`, `M` o `S`. PUT no cambia `id`; el modelo rechaza `nombre: null` y cuerpos sin campos útiles. Los errores usan `detail` de FastAPI. No hay autenticación, autorización ni endpoints de métricas o auditoría en esta API.

## Ejemplos reproducibles

Con la [instalación MariaDB local](06-Ejecucion-del-Proyecto.md) y la API en `:8000`:

```bash
curl http://127.0.0.1:8000/openapi.json
curl http://127.0.0.1:8000/alumnos
curl 'http://127.0.0.1:8000/alumnos?nombre=Ada'
curl http://127.0.0.1:8000/patronus
curl -i -X POST http://127.0.0.1:8000/alumnos -H 'Content-Type: application/json' -d '{"id":"DEMO0001","nombre":"Ada","apellido":"Demo","sexo":"M","patronus":"Otter"}'
curl http://127.0.0.1:8000/alumnos/DEMO0001
curl -i -X PUT http://127.0.0.1:8000/alumnos/DEMO0001 -H 'Content-Type: application/json' -d '{"nombre":"Ada Editada"}'
curl -i -X DELETE http://127.0.0.1:8000/alumnos/DEMO0001
```

En PowerShell, usa `curl.exe` en lugar de `curl` para evitar alias de versiones antiguas. No repitas el POST con el mismo DNI sin borrarlo: responderá 409. Si `DB_ENGINES` contiene un motor sin esquema o no disponible, las lecturas responderán 503.

## Flujo y límites

`db/factory.py` crea repositorios por `DB_ENGINES`; `api/alumnos.py` contiene la lógica HTTP; los módulos `db/*.py` ejecutan SQL parametrizado. Los errores de conexión se registran y se transforman en 503, pero el detalle puede incluir el texto de la excepción original: se debe revisar antes de exponer la API. PUT/DELETE en varios motores no tienen transacción global; un fallo intermedio puede dejar una actualización parcial. No hay `request-id` generado por la API aunque el cliente JavaFX envíe uno.

Continuar: [persistencia](05-Persistencia-de-Datos.md) · [demo](07-Demo-Funcional.md).
