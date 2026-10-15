# Gestor Hogwarts — Cliente de escritorio (JavaFX)

Cliente de escritorio para **consultar, dar de alta, editar y dar de baja** alumnado contra la
API central de R01 · Omni3.

**La aplicación habla siempre por HTTP con la API remota** desplegada en ngrok
(`https://tu-api-aqui.com`) y consulta las cinco fuentes del backend
—MariaDB, Derby, H2, HSQLDB y Oracle—, devolviendo a cada alumno su fuente de origen.
**La aplicación nunca accede directamente a las bases de datos**: todo pasa por la API HTTP
(requisito del contrato). Consulta, alta, **edición** (`PUT /alumnos/{dni}`) y **baja**
(`DELETE /alumnos/{dni}`) están conectadas con la API. No existe ningún modo alternativo ni
datos simulados en el cliente: el único cliente es `ClienteAlumnosHttp`.

> Fuente de verdad del proyecto: [`docs/00-contrato-tecnico.md`](docs/00-contrato-tecnico.md).

---

## 1. Características

| Pantalla | Qué hace |
|---|---|
| **Estado del sistema** | Comprueba la API real con `GET /alumnos` (estado, mensaje, momento y `request-id`). |
| **Alumnos** | Consulta unificada con **fuente de origen visible**, filtro por origen, búsqueda por DNI/nombre, alta, edición, baja y consulta de ficha. |

Reglas de negocio implementadas:

- **Alta** solo en la central MariaDB: **DNI de 9 caracteres no duplicado**, **patronus obligatorio** y **sin casa**.
- **Edición**: se lee la ficha vigente y se envía `PUT /alumnos/{dni}` con el nombre completo y
  el patronus. La API real aún no versiona, así que no hay control de concurrencia (`version = 0`
  y nunca hay `409`).
- **Baja**: se elimina el registro con `DELETE /alumnos/{dni}`; en los alumnos históricos la
  base de origen aplica su propio comportamiento.
- Los DNI **nunca se muestran completos** en mensajes de error (se enmascaran: `12***78A`).
- Toda petición envía una cabecera **`request-id`** que se muestra en la UI cuando hay error.
- **Tres idiomas** (castellano, euskera e inglés): el desplegable de la barra de estado
  (abajo a la izquierda, con el código actual `ES`/`EU`/`EN`) cambia el idioma en caliente y
  conserva la pantalla visible. El texto vive en `src/main/resources/i18n/mensajes*.properties`
  (base inglesa de respaldo); los FXML usan `%clave` y el código Java `Idiomas.texto(...)`.
  Arranca en el idioma del sistema si es `es`, `eu` o `en`; si no, en inglés.

---

## 2. Requisitos

| Componente | Versión |
|---|---|
| Sistema | Windows 10/11 x86-64 (también funciona en Linux/macOS) |
| Java | **JDK 17 o superior** |
| Maven | **3.9 o superior** |
| JavaFX | **21** (se descarga sola desde Maven Central: `21.0.5`) |

No hay que instalar JavaFX a mano: `pom.xml` ya declara las dependencias.

> **Nota de compilación.** El proyecto fija `maven.compiler.release=17`, así que compila con
> cualquier JDK 17 o posterior; la verificación completa de este encargo (compilación, 75 pruebas
> y Javadoc sin avisos) se ha hecho con un **JDK 25** instalado, sin necesidad de cambiar nada.

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

> **Idioma.** El idioma inicial sigue al del sistema cuando es español, euskera o inglés y cae
> a inglés en caso contrario; se puede forzar con la propiedad `-Dgestor.idioma=es|eu|en`
> (la usan las pruebas). Después se cambia con el desplegable de la barra de estado.

---

## 4. Cómo conectar con la API

### 4.1. Sin conmutador de datos

No hay nada que activar: la aplicación **siempre** usa `ClienteAlumnosHttp` contra la API real.
No existe `OMNI3_MODO`, `-Domni3.modo` ni datos simulados; si la API no está levantada, las
pantallas muestran el error de conexión con su `request-id`.

### 4.2. URL base

La URL de la API **ya no se edita desde la ventana**: se define en un único sitio de
configuración, con esta prioridad (`ConfigApi.desdeEntorno()`):

1. Propiedad de sistema **`-Dgestor.api.url`**;
2. variable de entorno **`GESTOR_API_URL`**;
3. valor por defecto **`https://tu-api-aqui.com`** (`ConfigApi.URL_POR_DEFECTO`).

En todos los casos se normaliza el texto (sin espacios, sin `/` finales y con esquema
`http://` si falta):

```bash
# propiedad de sistema (también sirve con el fat jar)
java -Dgestor.api.url=https://tu-api-aqui.com -jar gestor-hogwarts.jar

# variable de entorno (Windows)
set GESTOR_API_URL=https://tu-api-aqui.com && java -jar gestor-hogwarts.jar

# variable de entorno (Linux/macOS)
GESTOR_API_URL=https://tu-api-aqui.com java -jar gestor-hogwarts.jar
```

Si prefieres fijarlo en código, inyecta la configuración al crear la fábrica:

```java
ConfigApi config = new ConfigApi("https://tu-api-aqui.com", ConfigApi.TIMEOUT_POR_DEFECTO);
FabricaAplicacion fabrica = new FabricaAplicacion(config);
```

### 4.3. Endpoints de la API y cómo los usa el cliente

La API desplegada (ngrok, FastAPI de `proyecto_reto1`) publica cinco rutas:

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
- `ngrok-skip-browser-warning: 1`: necesaria para que ngrok-free no interponga su página de
  aviso; es inocua si la API no está detrás de ngrok.

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
curl -H "ngrok-skip-browser-warning: 1" https://tu-api-aqui.com/alumnos

# edición y baja (sustituye <dni> por un DNI real o de prueba)
curl -X PUT    -H "ngrok-skip-browser-warning: 1" -H "Content-Type: application/json" \
     https://tu-api-aqui.com/alumnos/<dni> -d '{"patronus":"Cervo"}'
curl -X DELETE -H "ngrok-skip-browser-warning: 1" \
     https://tu-api-aqui.com/alumnos/<dni>
```

Si contesta con la lista de alumnos, arranca la app y usa la pantalla **Estado del sistema**
(botón «Comprobar ahora») para verificar la conexión desde el cliente.

### 4.5. Datos cargados en las cinco bases

El backend remoto consulta las cinco bases (MariaDB central + Derby, H2, HSQLDB y Oracle), con
la misma asignación de `casa` a fuente que describe el mapeo de campos de §4.3.

---

## 5. Arquitectura

Patrón **MVVM** en tres capas; la vista solo traduce estado visible, sin lógica de negocio ni red.

```
src/main/java/com/omni3/gestorhogwartsjavafx/
├── App.java               Punto de entrada (ventana, escena, fábrica)
├── modelo/                Origen, TipoAlumno, Alumno
├── util/                  Validaciones (DNI, nombre, patronus, casa) + resultado · Idiomas
│                          (selector de idioma y bundles i18n)
├── api/                   ClienteAlumnos (interfaz) · Http · ConfigApi · ExcepcionApi · dto/
├── servicio/              ServicioAlumnos (hilos de fondo + publicación en el hilo FX)
│                          FabricaAplicacion (monta config + cliente + servicio + viewmodels)
├── viewmodel/             Consulta, Alta, Edición, Baja, Salud, Formulario
└── controlador/           Controladores de cada pantalla (Alumnos, Estado,
                           FormularioAlumno, Principal + ControladorVista)

src/main/resources/com/omni3/gestorhogwartsjavafx/vista/    (FXML y CSS: la vista, sin Java)
├── principal.fxml         Marco: cabecera, navegación lateral, contenedor, barra de estado
├── tema.css               Sistema de diseño «dark academia»
└── estado.fxml · alumnos.fxml · formulario-alumno.fxml

src/main/resources/i18n/  (mensajes por idioma; base en inglés como red de seguridad)
└── mensajes.properties · mensajes_es.properties · mensajes_eu.properties
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

Cobertura actual: doble del cliente, mapeo de DTOs, validaciones, servicio, fábrica de
aplicación y selector de idioma. Las pruebas no arrancan el toolkit JavaFX (usan ejecutores
síncronos) y son deterministas: fijan `gestor.idioma=es` para no depender del idioma del
equipo que las ejecute.

---

## 8. Estado y limitaciones conocidas

- **Verificado contra la API remota** (ngrok): listado con mapeo de campos, ficha por DNI, alta
  (`POST /alumnos`), edición (`PUT /alumnos/{dni}`), baja (`DELETE /alumnos/{dni}`), búsqueda,
  `404` con el `detail` del backend y pantalla de estado.
- **La fuente (`casa`) no es editable**: la API identifica la fuente con la que responde cada
  alumno y no admite cambiarla.
- La URL base **no es configurable desde la interfaz** (§4.2).
- El **catálogo de patronus** (`GET /patronus`) se ofrece en el formulario como desplegable
  editable con autocompletado: se muestran sugerencias solo cuando hay entre 1 y 14
  coincidencias, en el idioma de la interfaz, con vista previa de la imagen.
- ngrok-free puede mostrar una pantalla de aviso a clientes HTTP que no mandan la cabecera
  `ngrok-skip-browser-warning`; el cliente la envía siempre.
- Existe material de trabajo no versionado en la carpeta local (`tests-pendientes/`), fuera del
  alcance de este repositorio.

---

## 9. Documentación

- [`docs/00-contrato-tecnico.md`](docs/00-contrato-tecnico.md) — contrato técnico completo
  (entorno, alcance, endpoints, modelo, diseño, reparto de archivos y verificación).
