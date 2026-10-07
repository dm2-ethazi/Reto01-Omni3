# Gestor Hogwarts — Cliente de escritorio (JavaFX)

Cliente de escritorio para **consultar, dar de alta, editar y dar de baja** alumnado, gestionar
conectores y lanzar sincronizaciones contra la API central de R01 · Omni3.

**La aplicación ya está conectada con la API.** Arrancando con `OMNI3_MODO=api` (§4) habla por
HTTP con la API real de FastAPI (`src/proyecto_reto1/`, rama `Api`, `http://127.0.0.1:8000`) y
consulta las cinco fuentes del `compose.yaml` —MariaDB, Derby, H2, HSQLDB y Oracle—, devolviendo
a cada alumno su fuente de origen. **La aplicación nunca accede directamente a las bases de
datos**: todo pasa por la API HTTP (requisito del contrato). Las operaciones que la API actual
todavía no publica (edición, baja, conectores y sincronización) se responden como
`501 NO_IMPLEMENTADO` con un mensaje legible (§4.3).

Sin esa variable, la app arranca en **modo demo** con los mismos datos en memoria y la misma
interfaz, para poder probar la UI sin levantar la API (§2 de la propuesta).

> Fuente de verdad del proyecto: [`docs/00-contrato-tecnico.md`](docs/00-contrato-tecnico.md).

---

## 1. Características

| Pantalla | Qué hace |
|---|---|
| **Estado del sistema** | Comprueba la API real con `GET /alumnos` (estado, mensaje, momento, modo y `request-id`). |
| **Alumnos** | Consulta unificada con **fuente de origen visible**, filtro por origen, búsqueda por DNI/nombre, alta y consulta de ficha. |
| **Conectores** | Pantalla de fuentes de datos; requiere `GET /connectors` (ver §4.3). |
| **Sincronización** | «Sincronizar ahora» y cola de refrescos; requiere `POST /sync` y `GET /admin/refresh-queue` (ver §4.3). |

Reglas de negocio implementadas:

- **Alta** solo en la central MariaDB: **DNI de 9 caracteres no duplicado**, **patronus obligatorio** y **sin casa**.
- **Edición con control de concurrencia**: se envía el estado previo (`If-Match` / `X-Version-Previa`).
  Si otra sesión ha modificado la ficha la API responde **409** y la app **recarga sin sobrescribir nunca** en silencio.
- **Baja lógica** en alumnos nuevos; en históricos según el comportamiento de la base de origen.
- Los DNI **nunca se muestran completos** en mensajes de error (se enmascaran: `12***78A`).
- Toda petición envía una cabecera **`request-id`** que se muestra en la UI cuando hay error.

Las reglas marcadas con «API» dependen de endpoints que la API actual todavía no publica
(edición y baja): en modo API se muestran como error `501 NO_IMPLEMENTADO` y en modo demo se
comportan como está descrito (§4.3).

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

Al abrir la ventana se ve en la cabecera el **modo** activo (`MODO DEMO` o `MODO API`) y la
URL base de la API.

---

## 4. Cómo conectar con la API

### 4.1. Activar el modo API

La app arranca en **modo demo**. Para hablar con la API real basta con indicarlo al arrancar,
con una **variable de entorno** o con una **propiedad de la JVM**:

| Fuente | Clave | Valores admitidos |
|---|---|---|
| Propiedad de JVM | `-Domni3.modo` | `demo` · `api` |
| Variable de entorno | `OMNI3_MODO` | `demo` · `api` |

Prioridad: `-Domni3.modo` > `OMNI3_MODO` > `demo` (por defecto).

**PowerShell:**

```powershell
$env:OMNI3_MODO = 'api'
mvn javafx:run
```

**bash / Linux / macOS:**

```bash
OMNI3_MODO=api mvn javafx:run
```

**Propiedad de JVM** (útil para IDEs como IntelliJ o VS Code):

```text
-Domni3.modo=api
```

La UI **no cambia** entre modos: solo cambia el cliente que se usa por debajo
(`ClienteAlumnosDemo` → `ClienteAlumnosHttp`).

### 4.2. URL base

La URL base por defecto es **`http://127.0.0.1:8000`** (constante
`ConfigApi.URL_POR_DEFECTO`, en `api/ConfigApi.java`). El campo de la cabecera de la ventana
muestra esa URL a informativamente; **para apuntar a otro host o puerto** hay dos opciones:

1. Cambiar `ConfigApi.URL_POR_DEFECTO`, o
2. Inyectar la configuración al crear la fábrica:

```java
ConfigApi config = new ConfigApi("http://127.0.0.1:9000", Modo.API, ConfigApi.TIMEOUT_POR_DEFECTO);
FabricaAplicacion fabrica = new FabricaAplicacion(config);
```

### 4.3. Endpoints de la API y cómo los usa el cliente

La API desplegada (rama `Api`, FastAPI de `src/proyecto_reto1/`) publica solo tres rutas:

| Método | Ruta | Uso en la app |
|---|---|---|
| `GET` | `/alumnos` | Consulta unificada y pantalla de estado. |
| `GET` | `/alumnos/{dni}` | Ficha de un alumno. |
| `POST` | `/alumnos` | Alta (cuerpo `{id, nombre, apellido?, sexo?, patronus?}`). |

**Operaciones sin endpoint publicado.** Cuando la API no publica la ruta, el cliente lanza
`ExcepcionApi` con estado **501** y código `NO_IMPLEMENTADO`, y la pantalla muestra ese mensaje en
su estado de error:

| Método | Ruta | Pantalla afectada |
|---|---|---|
| `PUT` | `/alumnos/{dni}` | Edición (formulario de alumno). |
| `DELETE` | `/alumnos/{dni}` | Baja (menú de fila). |
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
```

Si contesta con la lista de alumnos, arranca la app con `OMNI3_MODO=api` y usa la pantalla
**Estado del sistema** (botón «Comprobar ahora») para verificar la conexión desde el cliente.

---

## 5. Arquitectura

Patrón **MVVM** en tres capas; la vista solo traduce estado visible, sin lógica de negocio ni red.

```
src/main/java/com/omni3/gestorhogwartsjavafx/
├── App.java               Punto de entrada (ventana, escena, fábrica)
├── modelo/                Origen, TipoAlumno, EstadoConector, Alumno
├── util/                  Validaciones (DNI, nombre, patronus, casa) + resultado
├── api/                   ClienteAlumnos (interfaz) · Demo · Http · ConfigApi · ExcepcionApi · dto/
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
                      → ClienteAlumnos (Demo o Http) → API
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

Cobertura actual: cliente demo, mapeo de DTOs, validaciones, servicio y fábrica de aplicación.
Las pruebas no arrancan el toolkit JavaFX (usan ejecutores síncronos) y son deterministas.

---

## 8. Estado y limitaciones conocidas

- **Modo demo verificado de extremo a extremo**: consulta, filtros, alta, edición con conflicto 409,
  conectores, cola y salud.
- **Modo API verificado contra la API real** (rama `Api`, `uvicorn` + MariaDB): listado con
  mapeo de campos, ficha por DNI, alta (`POST /alumnos`), búsqueda y filtro por origen,
  `404` con el `detail` del backend y pantalla de estado.
- **Operaciones pendientes de la API**: edición (`PUT`), baja (`DELETE`), conectores,
  sincronización y cola responden `501 NO_IMPLEMENTADO` con mensaje explicativo en la UI;
  al no haber versionado, tampoco hay control de concurrencia (`version = 0`).
- La URL base **no es configurable desde la interfaz** (§4.2).
- Existe material de trabajo no versionado en la carpeta local (`tests-pendientes/`), fuera del
  alcance de este repositorio.

---

## 9. Documentación

- [`docs/00-contrato-tecnico.md`](docs/00-contrato-tecnico.md) — contrato técnico completo
  (entorno, alcance, endpoints, modelo, diseño, reparto de archivos y verificación).
