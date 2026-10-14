# API Alumnos (Reto 1)

API REST que expone el alumnado de Hogwarts repartido en **5 bases de datos diferentes**
(Oracle, MariaDB, H2, HSQLDB y Derby) con una única interfaz: no importa en qué motor
esté el registro, la API lo localiza y actúa sobre él.

- **Stack**: Python 3.14 · FastAPI · Pydantic v2 · Uvicorn
- **Conexiones**: `oracledb`, `pymysql` (nativas) y `jaydebeapi` + JPype (JDBC) para H2, HSQLDB y Derby
- **Documentación interactiva**: `/docs` (Swagger UI), `/redoc` y `/openapi.json`

---

## Estructura del proyecto

```
proyecto-reto1/
├── .env                    # credenciales y URLs (no se sube a git)
├── .env.example            # plantilla de configuración
├── jars/                   # drivers JDBC: h2.jar, hsqldb.jar, derbyclient.jar, derbyshared.jar
├── pyproject.toml
└── src/proyecto_reto1/
    ├── config.py           # Settings cargados desde .env (rutas absolutas)
    ├── main.py             # app FastAPI (esta API)
    ├── main_tareas.py      # app de ejemplo independiente (API Tareas en memoria)
    ├── api/
    │   └── alumnos.py      # endpoints REST
    └── db/
        ├── base.py         # modelos Pydantic + Protocol del repositorio
        ├── factory.py      # crea las 5 fuentes según DB_ENGINES e inicia la JVM
        ├── jdbc.py         # repositorio genérico JDBC (H2/HSQLDB/Derby)
        ├── h2.py           # RepositorioH2
        ├── hsqldb.py       # RepositorioHSQLDB
        ├── derby.py        # RepositorioDerby
        ├── oracle.py       # RepositorioOracle
        └── mariadb.py      # RepositorioMariaDB
```

## Requisitos

- Python **3.14** y [uv](https://docs.astral.sh/uv/) (o un virtualenv equivalente)
- **Java/JVM** en el `PATH` (necesaria para H2, HSQLDB y Derby vía JDBC)
- Las 5 bases de datos accesibles con los datos cargados (tabla `ALUMNOS` en cada una)
- MariaDB con la tabla `PATRONUS_IMG` cargada (ver [Tabla PATRONUS_IMG](#tabla-patronus_img))

## Instalación y arranque

```bash
uv sync                      # crea .venv e instala dependencias
cp .env.example .env         # y completa credenciales
uv run uvicorn proyecto_reto1.main:app --reload
```

La API queda en `http://127.0.0.1:8000` (Swagger en `http://127.0.0.1:8000/docs`).

## Configuración (`.env`)

| Variable | Descripción |
|---|---|
| `DB_ENGINES` | Motores activos, separados por coma (`oracle,mariadb,h2,hsqldb,derby`). Si quitas uno, la API deja de usarlo |
| `JARS_DIR` | Carpeta con los `.jar` JDBC (por defecto `jars`, resuelta a ruta absoluta) |
| `ORACLE_HOST/PORT/SERVICE/USER/PASSWORD` | Oracle (Ravenclaw) |
| `MARIADB_HOST/PORT/DATABASE/USER/PASSWORD` | MariaDB (Hogwarts, base `r01_central`) |
| `H2_URL/USER/PASSWORD` | H2 (Hufflepuff), URL `jdbc:h2:tcp://localhost:9092/r01db` |
| `HSQLDB_URL/USER/PASSWORD` | HSQLDB (Slytherin), URL `jdbc:hsqldb:hsql://localhost:9001/slytherin` |
| `DERBY_URL/USER/PASSWORD` | Derby (Gryffindor), URL `jdbc:derby://localhost:1527/db` |

> **Nota**: `.env` y `JARS_DIR` se resuelven como rutas absolutas respecto a la carpeta `api/`,
> por lo que la API funciona correctamente independientemente del directorio desde el que se ejecute.

Las 5 fuentes y su etiqueta `casa`:

| Motor | `casa` | Tipo de repositorio |
|---|---|---|
| Oracle | Ravenclaw | `RepositorioOracle` (oracledb) |
| MariaDB | Hogwarts | `RepositorioMariaDB` (pymysql) |
| H2 | Hufflepuff | `RepositorioH2` → JDBC |
| HSQLDB | Slytherin | `RepositorioHSQLDB` → JDBC |
| Derby | Gryffindor | `RepositorioDerby` → JDBC |

## Modelo de datos

### Tabla ALUMNOS (en los 5 motores)

| Campo | Tipo | Restricciones en la API |
|---|---|---|
| `ID` | texto (DNI, 9-10 chars) | clave primaria, obligatorio en POST, **no se puede modificar** |
| `NOMBRE` | texto | obligatorio, 1-100 |
| `APELLIDO` | texto | opcional, `null` o `""` → `NULL` |
| `SEXO` | texto | opcional, solo `H`, `M` o `S` |
| `PATRONUS` | texto | opcional, `null` o `""` → `NULL` |

`casa` **no** es una columna: la añade la API a partir del repositorio que respondió.

### Tabla PATRONUS_IMG

Catálogo de patronus con imagen, almacenada **solo en MariaDB** (`r01_central`).
Usada para validar el patronus en el POST y para devolver la imagen en el GET.

| Campo | Tipo | Descripción |
|---|---|---|
| `ID` | INT autoincrement | clave primaria |
| `NOM_ESP` | varchar(100) | nombre en español (único) |
| `NOM_ENG` | varchar(100) | nombre en inglés (único) |
| `NOM_EUS` | varchar(100) | nombre en euskera (único) |
| `URL` | varchar(500) | URL de la imagen del patronus |

Para crear la tabla y cargar los datos:

```bash
mysql -u r01 -p r01_central < createPatronusImg.sql
mysql -u r01 -p r01_central < inserts_patronus_img.sql
```

---

## Endpoints

| Método | Ruta | Éxito | Descripción |
|---|---|---|---|
| `GET` | `/alumnos` | 200 | Lista el alumnado de **las 5** bases de datos |
| `GET` | `/alumnos?nombre={texto}` | 200 | Busca alumnos cuyo nombre **empiece por** el texto indicado |
| `GET` | `/alumnos/{dni}` | 200 | Busca el DNI en cada fuente y devuelve la primera coincidencia con `patronus_url` |
| `POST` | `/alumnos` | 201 | Crea el alumno en MariaDB (valida patronus contra `PATRONUS_IMG`) |
| `PUT` | `/alumnos/{dni}` | 200 | Actualiza el registro en **todas** las fuentes donde exista |
| `DELETE` | `/alumnos/{dni}` | 204 | Borra el registro en **todas** las fuentes donde exista |

Códigos de error comunes: **404** DNI inexistente · **409** DNI duplicado · **422** validación
(patronus no existe en `PATRONUS_IMG` para el idioma indicado) · **503** una fuente no responde.

### GET /alumnos

```bash
curl http://127.0.0.1:8000/alumnos
```

```json
[
  { "id": "10000000Z", "nombre": "Abigail", "apellido": "Pugh", "sexo": "M", "patronus": null, "patronus_url": null, "casa": "Slytherin" }
]
```

### GET /alumnos?nombre={texto}

Búsqueda por **prefijo** de nombre (no distingue mayúsculas/minúsculas). Devuelve los
coincidencias de las 5 bases de datos, ordenadas por nombre.

```bash
curl "http://127.0.0.1:8000/alumnos?nombre=Al"
```

```json
[
  { "id": "10000267M", "nombre": "Alannis", "apellido": null, "sexo": "M", "patronus": null, "patronus_url": null, "casa": "Ravenclaw" },
  { "id": "10000470W", "nombre": "Albert", "apellido": "Nott", "sexo": "H", "patronus": null, "patronus_url": null, "casa": "Gryffindor" },
  { "id": "10000773Y", "nombre": "Alecto", "apellido": "Carrow", "sexo": "M", "patronus": null, "patronus_url": null, "casa": "Hufflepuff" }
]
```

Si no se pasa el parámetro `nombre`, se comporta como `GET /alumnos` (lista completa).

### GET /alumnos/{dni}

```bash
curl http://127.0.0.1:8000/alumnos/10000000Z
```

```json
{
  "id": "10000000Z",
  "nombre": "Abigail",
  "apellido": "Pugh",
  "sexo": "M",
  "patronus": "Lobo",
  "patronus_url": "https://upload.wikimedia.org/wikipedia/commons/6/68/Eurasian_wolf_2.jpg",
  "casa": "Slytherin"
}
```

El campo `patronus_url` se busca en `PATRONUS_IMG` consultando por el valor de `patronus`
en cualquiera de los 3 idiomas (`NOM_ESP`, `NOM_ENG` o `NOM_EUS`). Si el patronus no está
en el catálogo o `patronus` es `null`, el campo se devuelve como `null`.

### POST /alumnos

El campo `patronus` se valida contra la tabla `PATRONUS_IMG`. El campo `patronus_lang`
indica en qué idioma se está enviando el nombre del patronus:

| Valor | Columna consultada | Ejemplo |
|---|---|---|
| `es` (por defecto) | `NOM_ESP` | `"Lobo"` |
| `en` | `NOM_ENG` | `"Wolf"` |
| `eu` | `NOM_EUS` | `"Otso"` |

```bash
curl -X POST http://127.0.0.1:8000/alumnos \
  -H "Content-Type: application/json" \
  -d '{"id":"10009999X","nombre":"Harry","apellido":"Potter","sexo":"H","patronus":"Lobo","patronus_lang":"es"}'
```

```json
{
  "id": "10009999X",
  "nombre": "Harry",
  "apellido": "Potter",
  "sexo": "H",
  "patronus": "Lobo",
  "patronus_url": null,
  "casa": "Hogwarts"
}
```

Ejemplo con euskera:

```bash
curl -X POST http://127.0.0.1:8000/alumnos \
  -H "Content-Type: application/json" \
  -d '{"id":"10009998K","nombre":"Hermione","apellido":"Granger","sexo":"M","patronus":"Otso","patronus_lang":"eu"}'
```

Respuestas:
- `201` creado
- `409` el DNI ya existe en alguna de las 5 fuentes
- `422` el patronus no existe en `PATRONUS_IMG` para el idioma indicado
- `422` datos no válidos (nombre vacío, sexo fuera de HMS, etc.)

### PUT /alumnos/{dni}

Envía **solo los campos que quieras cambiar**; los omitidos no se tocan y los que envíes
como `null` (o `""`) se guardan como `NULL`. El `ID` se toma de la URL y no es editable.
No valida el patronus contra `PATRONUS_IMG` (solo el POST lo valida).

```bash
# cambiar un solo campo
curl -X PUT http://127.0.0.1:8000/alumnos/10000000Z \
  -H "Content-Type: application/json" -d '{"patronus":"Stag"}'

# cambiar varios y limpiar un campo
curl -X PUT http://127.0.0.1:8000/alumnos/10000000Z \
  -H "Content-Type: application/json" -d '{"apellido":"","sexo":"H","patronus":null}'
```

Respuesta `200` con el alumno ya actualizado; `404` si el DNI no está en ninguna fuente;
`422` si el cuerpo está vacío o algún valor no cumple las restricciones.

### DELETE /alumnos/{dni}

```bash
curl -X DELETE http://127.0.0.1:8000/alumnos/10009999X
```

- `204` sin cuerpo si se borró · `404` si el DNI no existe en ninguna fuente · `503` si una fuente no responde.

---

## Cómo funciona por dentro

1. `config.py` lee `.env` (ruta absoluta) y `factory.py` construye la lista de repositorios
   según `DB_ENGINES`. Si hay fuentes JDBC, arranca **una única JVM** (`jdbc.iniciar_jvm`)
   con los `.jar` correspondientes (también resueltos a ruta absoluta).
2. `db/base.py` define el **Protocolo `RepositorioAlumnos`** (`listar`, `buscar_por_nombre`,
   `obtener`, `actualizar`, `eliminar`) y los modelos Pydantic. Todos los motores cumplen
   ese contrato, así que la capa HTTP no distingue entre Oracle, MariaDB, H2, HSQLDB ni Derby.
3. `api/alumnos.py` inyecta las fuentes con `Depends(get_repositorios)` y recorre la lista:
   lectura = primera coincidencia; escritura = todas las coincidencias.
4. Todas las sentencias SQL van **parametrizadas** (`?`, `%s` o `:nombre` según el motor),
   con las columnas blanqueadas por la API, y hacen `commit` explícito.
5. El **buscador por nombre** (`?nombre=`) usa `WHERE UPPER(NOMBRE) LIKE UPPER(prefijo || '%')`
   en cada motor, ordenado por nombre. Se ejecuta en las 5 fuentes y se concatenan los resultados.
6. El **patronus** se valida en el POST consultando `PATRONUS_IMG` en MariaDB (columna según
   `patronus_lang`). En el GET se busca la URL de imagen consultando las 3 columnas de nombre.

## Tests

Hay `pytest` instalado como dependencia; todavía no hay tests en el repositorio:

```bash
uv run pytest
```
