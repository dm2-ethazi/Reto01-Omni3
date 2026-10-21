# Modelo de datos

Los modelos proceden de `api/src/proyecto_reto1/db/base.py`, las consultas de `db/mariadb.py` y la persistencia móvil de `gestorhogwartsmovil/.../data/StudentDatabase.kt`. El DDL versionado para el recorrido MariaDB está en `database/mariadb/01_schema.sql`.

```mermaid
erDiagram
  ALUMNOS {
    varchar_10 ID PK
    varchar_100 NOMBRE
    varchar_100 APELLIDO "nullable"
    char_1 SEXO "nullable; H M S"
    varchar_100 PATRONUS "nullable; nombre lógico"
    varchar_500 IMAGEN "nullable"
  }
  PATRONUS_IMG {
    int ID PK
    varchar_100 NOM_ENG UK
    varchar_500 URL
  }
  STUDENTS_ROOM {
    text id PK
    text nombre
    text apellido "nullable"
    text sexo "nullable"
    text patronus "nullable"
    text casa "nullable"
    text imagen "nullable"
    int position
  }
  SYNC_STATE_ROOM {
    text key PK
    long syncedAt
  }
  ALUMNOS }o..o| PATRONUS_IMG : "coincidencia por nombre; sin FK"
```

`ALUMNOS.ID` es el DNI textual y clave primaria; `NOMBRE` es obligatorio. El API limita longitud y `SEXO` a `H`, `M` o `S`. `PATRONUS` guarda el nombre inglés y se valida contra `PATRONUS_IMG.NOM_ENG` **solo en POST**; el DDL no declara clave foránea. `PATRONUS_IMG.ID` es autoincremental, `NOM_ENG` único y `URL` texto. El catálogo de ejemplo contiene `Otter` con URL vacía, por lo que no demuestra la imagen.

En la respuesta, `casa` se calcula desde el repositorio que halló la fila. `GET /alumnos/{dni}` completa `patronus_url` consultando MariaDB; el listado no realiza ese enriquecimiento. Ninguno de esos campos es columna de `ALUMNOS`. El esquema de los otros cuatro motores **debe** tener las seis columnas que selecciona la API; esta entrega no lo crea automáticamente.

| Dato | Almacenamiento u origen | Uso |
|---|---|---|
| Identificador, nombre, apellido, sexo, patronus e imagen | Columnas de `ALUMNOS`. | Datos persistidos del alumno. |
| Nombre e imagen de un patronus | `PATRONUS_IMG.NOM_ENG` y `URL`. | Catálogo central consultado por la API. |
| `casa` | Etiqueta del adaptador que devuelve el alumno; Android la conserva en su copia. | Identificar la fuente sin añadir una columna a `ALUMNOS`. |
| `patronus_url` | Consulta por nombre del patronus al obtener una ficha. | Campo de respuesta; puede ser nulo o vacío. |
| `position` y `syncedAt` | Metadatos locales en Room. | Ordenar la copia y conocer su última sincronización correcta. |
| Estado de verificación y número de resultados | Comparación de la instantánea y recuento en el cliente Android. | Información calculada de presentación; no son métricas del backend. |

Room guarda `students` con los campos remotos más `position` para mantener el orden. `sync_state` registra que una colección, incluso vacía, fue sincronizada y la fecha `syncedAt`. No hay clave foránea entre ambas tablas. La versión Room es 1; el esquema exportado está en `app/schemas/`. No se observan migraciones de versiones posteriores.

La dirección seleccionada de la API y la dirección asociada a la última caché se guardan en preferencias locales (`ApiUrlSettings`), fuera de las tablas Room. Al cambiar de servidor, la app solo presenta la caché si corresponde a esa dirección. Se guardan textos de URL de imágenes, no los archivos de imagen.

El prototipo usa `ESTUDIANTE` y un espejo SQLite propios; no se mezclan con estas tablas. Continuar: [persistencia](05-Persistencia-de-Datos.md).
