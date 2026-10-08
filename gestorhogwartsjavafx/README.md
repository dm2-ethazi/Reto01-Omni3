# Gestor Hogwarts — Cliente de escritorio (JavaFX)

Cliente de escritorio para **consultar, dar de alta, editar y dar de baja** alumnado, gestionar
conectores y lanzar sincronizaciones contra la API central de R01 · Omni3.

**La aplicación habla siempre por HTTP con la API real** de FastAPI (`src/proyecto_reto1/`,
rama `Api`, `http://127.0.0.1:8000`) y consulta las cinco fuentes del `compose.yaml` —MariaDB,
Derby, H2, HSQLDB y Oracle—, devolviendo a cada alumno su fuente de origen. **La aplicación nunca
accede directamente a las bases de datos**: todo pasa por la API HTTP (requisito del contrato).
Consulta, alta, **edición** (`PUT /alumnos/{dni}`) y **baja** (`DELETE /alumnos/{dni}`) están
conectadas con la API; las operaciones que todavía no publica (conectores, sincronización y
cola) se responden como `501 NO_IMPLEMENTADO` con un mensaje legible (§4.3). No existe ningún
modo alternativo ni datos simulados en el cliente: el único cliente es `ClienteAlumnosHttp`.

> Fuente de verdad del proyecto: [`docs/00-contrato-tecnico.md`](docs/00-contrato-tecnico.md).

---

## 1. Características

| Pantalla | Qué hace |
|---|---|
| **Estado del sistema** | Comprueba la API real con `GET /alumnos` (estado, mensaje, momento y `request-id`). |
| **Alumnos** | Consulta unificada con **fuente de origen visible**, filtro por origen, búsqueda por DNI/nombre, alta, edición, baja y consulta de ficha. |
| **Conectores** | Pantalla de fuentes de datos; requiere `GET /connectors` (ver §4.3). |
| **Sincronización** | «Sincronizar ahora» y cola de refrescos; requiere `POST /sync` y `GET /admin/refresh-queue` (ver §4.3). |

Reglas de negocio implementadas:

- **Alta** solo en la central MariaDB: **DNI de 9 caracteres no duplicado**, **patronus obligatorio** y **sin casa**.
- **Edición**: se lee la ficha vigente y se envía `PUT /alumnos/{dni}` con el nombre completo y
  el patronus. La API real aún no versiona, así que no hay control de concurrencia (`version = 0`
  y nunca hay `409`).
- **Baja**: se elimina el registro con `DELETE /alumnos/{dni}`; en los alumnos históricos la
  base de origen aplica su propio comportamiento.
- Los DNI **nunca se muestran completos** en mensajes de error (se enmascaran: `12***78A`).
- Toda petición envía una cabecera **`request-id`** que se muestra en la UI cuando hay error.

Las operaciones que la API todavía no publica (conectores, sincronización y cola) responden
`501 NO_IMPLEMENTADO` con un mensaje legible en la pantalla correspondiente (§4.3).

---

## 2. Requisitos

| Componente | Versión |
|---|---|
| Sistema | Windows 10/11 x86-64 (también funciona en Linux/macOS) |
| Java | **JDK 17 o superior** |
| Maven | **3.9 o superior** |
| JavaFX | **21** (se descarga sola desde Maven Central: `21.0.5`) |

No hay que instalar JavaFX a mano: `pom.xml` ya declara las dependencias.

---

## 3. Puesta en marcha

```bash
cd gestorhogwartsjavafx

mvn clean javafx:run      # compila y abre la ventana
mvn test                  # ejecuta las pruebas (EXIT=0 obligatorio)
```

En Windows, si Maven no está en el `PATH`:

```powershell
& 'ruta\a\apache-maven-3.9.11\bin\mvn.cmd' -f '.\pom.xml' clean javafx:run
```

Al abrir la ventana se ve en la cabecera la **URL base de la API**, que se puede ajustar antes
de cada petición (§4.2).

---

## 4. Cómo conectar con la API

### 4.1. Sin conmutador de datos

No hay nada que activar: la aplicación **siempre** usa `ClienteAlumnosHttp` contra la API real.
No existe `OMNI3_MODO`, `-Domni3.modo` ni datos simulados; si la API no está levantada, las
pantallas muestran el error de conexión con su `request-id`.

### 4.2. URL base

La URL base por defecto es **`http://127.0.0.1:8000`** (constante
`ConfigApi.URL_POR_DEFECTO`, en `api/ConfigApi.java`). El campo de la cabecera de la ventana
muestra esa URL a informativamente; **para apuntar a otro host o puerto** hay dos opciones:

1. Cambiar `ConfigApi.URL_POR_DEFECTO`, o
2. Inyectar la configuración al crear la fábrica:

```java
ConfigApi config = new ConfigApi("http://127.0.0.1:9000", ConfigApi.TIMEOUT_POR_DEFECTO);
FabricaAplicacion fabrica = new FabricaAplicacion(config);
```

### 4.3. Endpoints de la API y cómo los usa el cliente

La API desplegada (rama `Api`, FastAPI de `src/proyecto_reto1/`) publica cinco rutas:

| Método | Ruta | Uso en la app |
|---|---|---|
| `GET` | `/alumnos` | Consulta unificada y pantalla de estado. |
| `GET` | `/alumnos/{dni}` | Ficha de un alumno. |
| `POST` | `/alumnos` | Alta (cuerpo `{id, nombre, apellido?, sexo?, patronus?}`). |
| `PUT` | `/alumnos/{dni}` | Edición (cuerpo `{nombre, apellido?, patronus?}`). |
| `DELETE` | `/alumnos/{dni}` | Baja (responde `204`; `404` si no existe). |

La edición se prepara en dos pasos: se lee la ficha vigente (`GET /alumnos/{dni}`) para conservar
el apellido si el texto editado sigue terminando en él, y después se envía `PUT` con el nombre
completo y el patronus (vacío → `null`, que limpia el campo). Como la API no admite cambiar la
fuente, el campo `casa` de la ficha es de solo lectura.

**Operaciones sin endpoint publicado.** Cuando la API no publica la ruta, el cliente lanza
`ExcepcionApi` con estado **501** y código `NO_IMPLEMENTADO`, y la pantalla muestra ese mensaje en
su estado de error:

| Método | Ruta | Pantalla afectada |
|---|---|---|
| `GET` | `/connectors` | Conectores. |
| `POST` | `/sync` | Sincronización. |
| `GET` | `/admin/refresh-queue` | Cola de refrescos. |

**Mapeo de campos** (`api/ClienteAlumnosHttp.java`): el backend responde
`{id, nombre, apellido, sexo, patronus, casa}` y la app recibe
`{dni, nombre, casa, origen, tipo, version, actualizadoEn, activo}`:

- `id` → `dni` (sin espacios y en mayúsculas); `nombre` + `apellido` → `nombre`.
- `casa` (etiqueta de la fuente que responde) → `origen`: `Hogwarts`→`MARIADB`,
  `Gryffindor`→`DERBY`, `Hufflepuff`→`H2`, `Slytherin`→`HSQLDB`, `Ravenclaw`→`ORACLE`.
- `casa` → `casa` de la ficha; sin `casa` el alumno es `NUEVO`, con `casa` es `HISTORICO`.
- La API no versiona ni marca bajas: `version = 0`, `actualizadoEn = ""`, `activo = true`.
- El **filtro por origen, la búsqueda `q` y la paginación `limit`/`offset` se aplican en el
  cliente**, porque `GET /alumnos` devuelve siempre el listado completo.

**Cabeceras enviadas por el cliente HTTP** (`api/ClienteAlumnosHttp.java`):

- `request-id`: identificador generado por el cliente en cada petición.
- `Accept` / `Content-Type: application/json`.

**Errores**: se aceptan los dos formatos que puede devolver el backend:

```json
{ "error": { "codigo": "DNI_DUPLICADO", "mensaje": "…", "detalle": "…" } }
{ "detail": "No existe ningun alumno con DNI '…'" }
```

Cualquier respuesta con estado `>= 400` se convierte en `ExcepcionApi` (código, mensaje,
`request-id` y estado HTTP) y la pantalla pasa a estado **ERROR**; el `409` se trata aparte como
**CONFLICTO** y ofrece recargar la ficha.

### 4.4. Comprobación rápida de que la API está viva

```bash
curl http://127.0.0.1:8000/alumnos

# edición y baja (sustituye <dni> por un DNI real o de prueba)
curl -X PUT    http://127.0.0.1:8000/alumnos/<dni> -H "Content-Type: application/json" -d '{"patronus":"Cervo"}'
curl -X DELETE http://127.0.0.1:8000/alumnos/<dni>
```

Si contesta con la lista de alumnos, arranca la app y usa la pantalla **Estado del sistema**
(botón «Comprobar ahora») para verificar la conexión desde el cliente.

### 4.5. Datos cargados en las cinco bases

Las cinco bases del `compose.yaml` están cargadas con el volcado `bbdd.sql` (DDL + INSERT por
cada fuente):

| Fuente (puerto) | `casa` de la API | Origen en la app | Contenido |
|---|---|---|---|
| MariaDB `r01_central` (3306) | Hogwarts | `MARIADB` | Central: semilla inicial **y** todos los alumnos nuevos creados desde la app |
| Derby (1527) | Gryffindor | `DERBY` | 303 alumnos del volcado |
| H2 (9092) | Hufflepuff | `H2` | 212 alumnos del volcado |
| HSQLDB (9001) | Slytherin | `HSQLDB` | 267 alumnos del volcado |
| Oracle `FREEPDB1` (1521) | Ravenclaw | `ORACLE` | 203 alumnos del volcado |

El volcado aporta **985 alumnos históricos**; con la semilla de MariaDB la API servía 990
alumnos al escribir estas líneas, y el total **sube y baja con cada alta y cada baja**. El listado
se pide sin límite (`ConsultaViewModel.LIMITE_POR_DEFECTO = 0`) y el filtro por origen, la búsqueda
y la paginación se resuelven en el cliente, porque `GET /alumnos` devuelve siempre el listado
completo.

---

## 5. Arquitectura

Patrón **MVVM** en tres capas; la vista solo traduce estado visible, sin lógica de negocio ni red.

```
src/main/java/com/omni3/gestorhogwartsjavafx/
├── App.java               Punto de entrada (ventana, escena, fábrica)
├── modelo/                Origen, TipoAlumno, EstadoConector, Alumno
├── util/                  Validaciones (DNI, nombre, patronus, casa) + resultado
├── api/                   ClienteAlumnos (interfaz) · Http · ConfigApi · ExcepcionApi · dto/
├── servicio/              ServicioAlumnos (hilos de fondo + publicación en el hilo FX)
│                          FabricaAplicacion (monta config + cliente + servicio + viewmodels)
├── viewmodel/             Consulta, Alta, Edición, Baja, Salud, Conectores, Sincronización, Formulario
└── controlador/           Controladores de cada pantalla (Alumnos, Conectores, Estado,
                           FormularioAlumno, Principal, Sincronización + ControladorVista)

src/main/resources/com/omni3/gestorhogwartsjavafx/vista/    (FXML y CSS: la vista, sin Java)
├── principal.fxml         Marco: cabecera, navegación lateral, contenedor, barra de estado
├── estilos.css            Sistema de diseño «azul tenue»
├── estado.fxml · alumnos.fxml · formulario-alumno.fxml
└── conectores.fxml · sincronizar.fxml
```

Los controladores y las vistas van en paquetes separados: `controlador/` solo contiene Java y
`vista/` solo contiene FXML/CSS, que se cargan con la ruta absoluta `ControladorVista.RUTA_VISTAS`.

Flujo de una petición:

```
controlador → viewmodel → ServicioAlumnos (executor de fondo)
                      → ClienteAlumnosHttp → API
                      → callback → Platform.runLater → viewmodel (properties) → vista
```

El **hilo de JavaFX nunca se bloquea**: toda llamada a la API se ejecuta en un `ExecutorService`
y la respuesta se publica con `Platform.runLater`.

---

## 6. Sistema de diseño

Azul discreto y sin fatiga visual: sin degradados, sin sombras marcadas, sin colores saturados.
Los valores (tokens) y las clases CSS están documentados en
[`docs/00-contrato-tecnico.md`](docs/00-contrato-tecnico.md) §5; en el CSS solo se usan
**clases semánticas** (`.boton-primario`, `.tarjeta`, `.texto-error`, …), nunca colores sueltos
en FXML o Java.

---

## 7. Pruebas

```bash
mvn test
```

Cobertura actual: doble del cliente, mapeo de DTOs, validaciones, servicio y fábrica de aplicación.
Las pruebas no arrancan el toolkit JavaFX (usan ejecutores síncronos) y son deterministas.

---

## 8. Estado y limitaciones conocidas

- **Verificado contra la API real** (rama `Api`, `uvicorn` + las cinco bases del `compose`):
  listado con mapeo de campos (los 985 alumnos históricos del volcado repartidos en las cinco
  fuentes), ficha por DNI, alta (`POST /alumnos`), edición (`PUT /alumnos/{dni}`), baja
  (`DELETE /alumnos/{dni}`), búsqueda y filtro por origen, `404` con el `detail` del backend y
  pantalla de estado.
- **Operaciones pendientes de la API**: conectores, sincronización y cola responden
  `501 NO_IMPLEMENTADO` con mensaje explicativo en la UI; al no haber versionado, tampoco hay
  control de concurrencia (`version = 0`).
- **La fuente (`casa`) no es editable**: la API identifica la fuente con la que responde cada
  alumno y no admite cambiarla.
- La URL base **no es configurable desde la interfaz** (§4.2).
- Existe material de trabajo no versionado en la carpeta local (`tests-pendientes/`), fuera del
  alcance de este repositorio.

---

## 9. Documentación

- [`docs/00-contrato-tecnico.md`](docs/00-contrato-tecnico.md) — contrato técnico completo
  (entorno, alcance, endpoints, modelo, diseño, reparto de archivos y verificación).
