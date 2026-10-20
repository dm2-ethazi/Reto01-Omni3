# Manual de usuario â€” Reto R01

**VersiÃ³n:** 1.0 Â· **Fecha:** 2026-10-01
**Ãmbito:** uso operativo de la API REST y de los clientes

> **Trazabilidad:** punto 3 de los cinco manuales obligatorios establecidos
> por **deducciÃ³n** (no existe pliego en el repositorio; ver
> `01-README.md` Â§5). Presupone el backend ya instalado segÃºn
> `02-manual-instalacion.md`.

---

## Ãndice

1. [Antes de empezar](#1-antes-de-empezar)
2. [Descubrir el estado del sistema: `/health`](#2-descubrir-el-estado-del-sistema-health)
3. [Trabajar con los orÃ­genes: `/connectors`](#3-trabajar-con-los-origenes-connectors)
4. [Sincronizar con la central: `/sync`](#4-sincronizar-con-la-central-sync)
5. [Consultar la central: `/central`](#5-consultar-la-central-central)
6. [Formato de errores](#6-formato-de-errores)
7. [Swagger / documentaciÃ³n interactiva](#7-swagger--documentaciÃ³n-interactiva)
8. [Uso desde los clientes grÃ¡ficos](#8-uso-desde-los-clientes-grÃ¡ficos)
9. [Recorrido tÃ­pico (happy path)](#9-recorrido-tÃ­pico-happy-path)
10. [Buenas prÃ¡cticas y lÃ­mites](#10-buenas-prÃ¡cticas-y-lÃ­mites)

---

## 1. Antes de empezar

- Backend en marcha: `http://127.0.0.1:8000`
- Contenedores `r01-derby`, `r01-hsqldb`, `r01-h2`, `r01-oracle`, `r01-mariadb` en `healthy`.
- Los ejemplos usan `curl`; en PowerShell sustituir `curl` por
  `curl.exe` o usar `Invoke-RestMethod`.

Base de los ejemplos:

```powershell
$api = "http://127.0.0.1:8000"
```

---

## 2. Descubrir el estado del sistema: `/health`

```bash
curl http://127.0.0.1:8000/health
```

**Siempre responde HTTP 200**, incluso con dependencias caÃ­das; el estado
real estÃ¡ en el cuerpo:

```json
{
  "status": "ok",
  "reto": "R01",
  "central": {
    "mariadb":  { "disponible": true,  "detalle": "disponible" },
    "sqlite":   { "disponible": true,  "detalle": "disponible" },
    "pendientes_reintento": 0
  },
  "conectores": {
    "derby":  { "id": "derby",  "disponible": true,  "dialect": "derby",  "url": "jdbc:derby://localhost:1527/r01derby" },
    "hsqldb": { "disponible": true },
    "h2":     { "disponible": true },
    "oracle": { "disponible": true }
  },
  "jvm":   { "arrancada": true, "avisos": [] },
  "errores": [],
  "caidos": [],
  "app":   { "nombre": "R01 - Backend de integracion", "version": "1.0.0" }
}
```

InterpretaciÃ³n:

| Valor | Significado |
|-------|-------------|
| `"status": "ok"` | Todas las dependencias responden |
| `"status": "degraded"` | Algo estÃ¡ caÃ­do; mirar el array `caidos` |
| `caidos` incluye `"central.mariadb"` | MariaDB configurada (`MARIADB_ENABLED=true`) pero sin respuesta |
| `central.mariadb` ausente | MariaDB deshabilitada por diseÃ±o â†’ **no** es una caÃ­da |

---

## 3. Trabajar con los orÃ­genes: `/connectors`

### 3.1 Listar conectores y su estado

```bash
curl http://127.0.0.1:8000/connectors
```

```json
{ "conectores": { "derby": {"id":"derby","nombre":"Apache Derby (contenedor r01-derby)",
  "dialect":"derby","url":"jdbc:derby://localhost:1527/r01derby",
  "disponible":true,"detalle":"disponible"}, "...": {} } }
```

### 3.2 Listar estudiantes de un origen (paginado y filtrado)

```bash
# PÃ¡gina por defecto (offset=0, limit=50)
curl "http://127.0.0.1:8000/connectors/derby/estudiantes"

# PaginaciÃ³n
curl "http://127.0.0.1:8000/connectors/derby/estudiantes?offset=0&limit=2"

# Filtros combinables
curl "http://127.0.0.1:8000/connectors/derby/estudiantes?dni=12345678Z"
curl "http://127.0.0.1:8000/connectors/derby/estudiantes?nombre=alicia"
```

Respuesta (`PaginaEstudiantes`):

```json
{
  "items": [
    { "id": 1, "dni": "12345678Z", "nombre": "Alicia Torres",
      "casa": "Gryffindor", "patronus": "Leon" }
  ],
  "total": 3, "offset": 0, "limit": 2, "conector": "derby"
}
```

Reglas: `offset >= 0`, `1 <= limit <= 500`, `dni` mÃ¡x. 9 caracteres,
`nombre` mÃ¡x. 100. El filtro `nombre` es **parcial e insensible a
mayÃºsculas**; `dni` es exacto.

### 3.3 Alta de un estudiante

```bash
curl -X POST "http://127.0.0.1:8000/connectors/h2/estudiantes" \
  -H "Content-Type: application/json" \
  -d '{"dni":"99887766x","nombre":"  Eva   Prueba ",
       "casa":"Hufflepuff","patronus":"Tejon"}'
```

Respuesta **201** con normalizaciÃ³n aplicada:

```json
{ "id": 6, "dni": "99887766X", "nombre": "Eva Prueba",
  "casa": "Hufflepuff", "patronus": "Tejon" }
```

- El DNI se convierte a **mayÃºsculas** y debe cumplir `12345678Z` o `X1234567Z`.
- Los espacios mÃºltiples se colapsan.
- Los campos extra en el payload se rechazan (`422`, `extra="forbid"`).
- `id` es opcional en el body; si no llega, lo asigna el origen.

### 3.4 Consultar, actualizar y borrar

```bash
# Detalle
curl "http://127.0.0.1:8000/connectors/h2/estudiantes/1"

# ActualizaciÃ³n PARCIAL (sÃ³lo aplica los campos enviados)
curl -X PUT "http://127.0.0.1:8000/connectors/h2/estudiantes/1" \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Eva Prueba Actualizada"}'

# Borrado -> 204 sin cuerpo
curl -X DELETE "http://127.0.0.1:8000/connectors/h2/estudiantes/1"
```

> **Importante:** la escritura sÃ³lo existe sobre **orÃ­genes**
> (`/connectors/...`). La central es de **sÃ³lo lectura**.

---

## 4. Sincronizar con la central: `/sync`

### 4.1 Sincronizar todos los conectores

```bash
curl -X POST "http://127.0.0.1:8000/sync"
```

```json
{
  "conectores": [ { "conector": "derby", "leidos": 10, "insertados": 10,
                    "actualizados": 0, "errores": 0,
                    "duracion_ms": 214.7 } ],
  "insertados": 40, "actualizados": 0, "errores": 0
}
```

### 4.2 Sincronizar un solo conector

```bash
curl -X POST "http://127.0.0.1:8000/sync/derby"
```

```json
{ "conector": "derby", "leidos": 10, "insertados": 10, "actualizados": 0,
  "errores": 0, "errores_transformacion": 0, "detalle_errores": [],
  "central": { "mariadb": {"insertados":10,"actualizados":0,"errores":0},
               "sqlite":  {"insertados":10,"actualizados":0,"errores":0} },
  "pendientes_reintento": 0, "duracion_ms": 123.4 }
```

QuÃ© hace internamente (flujo 4.4):

1. Reintenta los upserts pendientes de la cola de reintentos.
2. Lee el origen **por pÃ¡ginas** (`sync_page_size`, por defecto 100).
3. Transforma cada fila (normalizaciÃ³n + validaciÃ³n de DNI + `origen`).
4. Inserta/actualiza en la **central doble** (MariaDB + SQLite).
5. Devuelve un resumen con `leidos/insertados/actualizados/errores`.

**Es idempotente:** una segunda ejecuciÃ³n devuelve
`insertados=0, actualizados=N`.

Un conector desconocido responde **400** con `C-400`:

```json
{ "error": { "codigo": "C-400",
  "mensaje": "Conector desconocido: 'postgres'",
  "detalle": { "conectores": ["derby","h2","hsqldb","oracle"] } } }
```

---

## 5. Consultar la central: `/central`

### 5.1 BÃºsqueda paginada

```bash
curl "http://127.0.0.1:8000/central/estudiantes?limit=2"
curl "http://127.0.0.1:8000/central/estudiantes?nombre=bruno"
curl "http://127.0.0.1:8000/central/estudiantes?dni=12345678Z"
```

```json
{
  "items": [ { "id": 1, "origen": "derby", "id_origen": 1,
               "dni": "12345678Z", "nombre": "Alicia Torres",
               "casa": "Gryffindor", "patronus": "Leon",
               "actualizado": "2026-10-01T12:00:00",
               "backend": "sqlite" } ],
  "total": 5, "offset": 0, "limit": 2,
  "backend": "sqlite", "filtros": {}
}
```

El campo `origen` da **trazabilidad** (desde quÃ© base vino la fila) y
`backend` indica desde quÃ© motor se respondiÃ³ (MariaDB o SQLite).

### 5.2 Consulta exacta por DNI

```bash
curl "http://127.0.0.1:8000/central/estudiantes/12345678Z"
# DNI no existente -> 404 con envelope C-404
curl "http://127.0.0.1:8000/central/estudiantes/00000000A"
```

El path exige **exactamente 9 caracteres** (`min_length=9, max_length=9`).

### 5.3 ComprobaciÃ³n de consistencia (MariaDB vs SQLite)

```bash
curl "http://127.0.0.1:8000/central/consistencia"
```

```json
{
  "coincide": true,
  "pendientes_reintento": 0,
  "backends": {
    "sqlite":  { "conteos": { "derby": 10, "h2": 10, "hsqldb": 10, "oracle": 10 } },
    "mariadb": { "conteos": { "derby": 10, "h2": 10, "hsqldb": 10, "oracle": 10 } }
  }
}
```

`coincide: true` significa que **conteos y huellas de contenido** coinciden
entre los dos backends. Si hay `pendientes_reintento > 0`, ejecutar de
nuevo `POST /sync` vacÃ­a la cola.

---

## 6. Formato de errores

Todos los errores comparten el mismo envelope:

```json
{ "error": { "codigo": "C-404", "mensaje": "...", "detalle": { } } }
```

| CÃ³digo | HTTP | CuÃ¡ndo |
|--------|------|--------|
| `C-400` | 400 | DNI con formato invÃ¡lido, conector desconocido en `/sync` |
| `C-404` | 404 | Conector o estudiante inexistente |
| `C-422` | 422 | Payload invÃ¡lido o fuera de rango (`limit=0`, body vacÃ­o, campos extra) |
| `C-500` | 500 | Error interno no previsto |
| `C-502` | 502 | Fallo al hablar con un origen/base |
| `C-503` | 503 | Dependencia caÃ­da o no configurada |

Ejemplo real (DNI invÃ¡lido):

```bash
curl -X POST "http://127.0.0.1:8000/connectors/derby/estudiantes" \
  -H "Content-Type: application/json" \
  -d '{"dni":"no-es-dni","nombre":"Ana","casa":"Ravenclaw","patronus":"BÃºho"}'
# 400 -> {"error":{"codigo":"C-400","mensaje":"El DNI no tiene un formato vÃ¡lido ..."}}
```

---

## 7. Swagger / documentaciÃ³n interactiva

| URL | Contenido |
|-----|-----------|
| `http://127.0.0.1:8000/docs` | Swagger UI (probable) |
| `http://127.0.0.1:8000/redoc` | ReDoc |
| `http://127.0.0.1:8000/` | RaÃ­z con nombre, versiÃ³n y enlace a `/docs` |

En Swagger pueden probarse todos los endpoints con *Try it out* sin
instalar clientes.

---

## 8. Uso desde los clientes grÃ¡ficos

### 8.1 Cliente de escritorio (JavaFX + MVVM)

```bash
mvn -f r01/clients/desktop-javafx/pom.xml javafx:run
```

- Barra superior: URL del backend (`http://127.0.0.1:8000` por defecto).
- Operaciones cubiertas por `CentralClient`: `/health`, `/connectors`,
  `/central/consistencia`, `/central/estudiantes[/{dni}]`, CRUD sobre
  `/connectors/{id}/estudiantes` y `POST /sync`.
- El servicio corre en hilo de fondo con callbacks: **nunca bloquea el
  hilo FX**.

### 8.2 Cliente Android

MÃ³dulo `r01/clients/mobile-android/`; mismos endpoints que el escritorio.
Configurar la IP del host en el emulador/dispositivo
(`10.0.2.2` en el emulador de Android para `localhost` del host).

### 8.3 Cliente Unity

MÃ³dulo `r01/clients/unity-patronus/`; consumo de la misma API REST.

---

## 9. Recorrido tÃ­pico (happy path)

| # | AcciÃ³n | Endpoint | Resultado esperado |
|---|--------|----------|--------------------|
| 1 | Verificar salud | `GET /health` | `status: ok` |
| 2 | Ver orÃ­genes | `GET /connectors` | 4 conectores disponibles |
| 3 | Inspeccionar un origen | `GET /connectors/derby/estudiantes` | PÃ¡gina con `total` |
| 4 | Sincronizar todo | `POST /sync` | `errores: 0`, `insertados > 0` |
| 5 | Consultar central | `GET /central/estudiantes` | Filas con `origen` trazado |
| 6 | Verificar paridad | `GET /central/consistencia` | `coincide: true` |
| 7 | Re-sincronizar | `POST /sync` | `insertados: 0`, `actualizados: N` (idempotencia) |

---

## 10. Buenas prÃ¡cticas y lÃ­mites

1. **Paginar siempre**: `limit` mÃ¡ximo 500; no pedir listados completos en bucle.
2. **La central es de lectura**: para modificar datos, hacerlo en el origen
   y volver a sincronizar.
3. **Sincronizar antes de consultar** si se esperan cambios recientes en los orÃ­genes.
4. **No versionar credenciales**: usar siempre variables de entorno.
5. **Consultar `/health` antes de operar** en entornos compartidos: `degraded`
   explica por quÃ© un endpoint puede fallar.
6. `PUT` es **parcial**: enviar sÃ³lo los campos a modificar (los `null` no se aplican).
