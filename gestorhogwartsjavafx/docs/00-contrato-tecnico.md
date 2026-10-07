# Contrato técnico — Cliente de escritorio JavaFX (Reto R01 · Omni3)

> **Fuente de verdad obligatoria** para todo el desarrollo de esta carpeta.
> Basado en: *Propuesta técnica R01 v5* + *Convenio de colaboración Omni3 · Hogwarts*.
> Si este documento y una implementación discrepan, **gana este documento**.

---

## 1. Entorno de referencia (extraído de los PDFs)

| Componente | Versión obligatoria |
|---|---|
| SO | Windows 10/11 **x86-64** |
| Java | **JDK 17 o superior** (`maven.compiler.release=17`) |
| JavaFX | **21** (usamos `21.0.5`, rama 21) |
| Maven | **3.9 o superior** (solo compilación) |
| API | FastAPI en **`http://127.0.0.1:8000`** |
| Mínimos | 8 GB RAM (16 GB recomendados) |

Puertos del entorno: `1527` Derby · `9001` HSQLDB · `9092` H2 · `1521` Oracle · `3306` MariaDB · `8000` API.

**Prohibido** añadir dependencias que exijan otra versión de Java/JavaFX.

---

## 2. Alcance del cliente JavaFX

> PDF: *"ESCRITORIO · JAVAFX — Consulta, alta, edición y baja"*.
> PDF: *"Todas las aplicaciones consumen la misma API; ninguna accede directamente a las bases de datos."*

1. **Consulta unificada** de alumnado mostrando **siempre la fuente de origen**.
2. **Alta de alumno nuevo** (solo MariaDB): **Patronus obligatorio**, **DNI no duplicado**, **sin casa**.
3. **Edición de alumno histórico**: se edita en su base de origen enviando el **estado previo**;
   si hay conflicto de concurrencia → **error 409 y recarga**, nunca sobrescribir en silencio.
4. **Baja**: borrado **lógico** en alumnos nuevos; en históricos según el comportamiento de la base.
5. **Estado del sistema** (`/health`, identificador `R01`), **conectores** y **sincronización**.
6. Toda petición lleva **`request-id`**; los logs **nunca** muestran DNI completos (enmascarar: `12***45A`).

### Fuera de alcance
Acceso directo a bases de datos, autenticación, nube, nuevas fuentes (son *cambios de alcance* según el contrato).

---

## 3. Capa de acceso a la API (conmutable)

La API **aún no existe**, así que la app arranca en **modo demo**. La UI no cambia entre modos.

```
api/
├── Modo.java                 DEMO | API   (por defecto DEMO)
├── ClienteAlumnos.java       interfaz única usada por servicios/viewmodels
├── ClienteAlumnosDemo.java   implementación mock (datos en memoria)
├── ClienteAlumnosHttp.java   java.net.http.HttpClient + Jackson
├── ConfigApi.java            baseUrl, modo, timeout; lee -Domni3.modo / OMNI3_MODO
├── dto/                      respuestas JSON (AlumnoDto, ConectorDto, SaludDto, ErrorDto)
└── ExcepcionApi.java         codigo + mensaje + request-id + status HTTP
```

**Contrato de endpoints esperado (según PDF):**

| Método | Ruta | Uso |
|---|---|---|
| GET | `/health` | estado + identificador `R01` |
| GET | `/students?origen=&q=&limit=&offset=` | consulta unificada |
| GET | `/students/{dni}` | ficha |
| POST | `/students` | alta de nuevo (Patronus obligatorio) |
| PUT | `/students/{dni}` | edición (cabecera/estado previo → 409 en conflicto) |
| DELETE | `/students/{dni}` | baja |
| GET | `/connectors` | fuentes y su estado |
| POST | `/sync` | sincronización |
| GET | `/admin/refresh-queue` | cola de refrescos |

Errores con envelope `{"error":{"codigo","mensaje","detalle"}}`.
`ClienteAlumnosDemo` debe **simular** latencia, duplicidad de DNI (409/400) y conflicto de edición (409)
para poder ejercitar la UI sin backend.

---

## 4. Modelo de dominio

```java
enum Origen   { DERBY, HSQLDB, H2, ORACLE, MARIADB, SQLITE }   // + CENTRAL para la vista unificada
enum TipoAlumno { HISTORICO, NUEVO }
enum EstadoConector { OK, DEGRADADO, CAIDO, DESCONOCIDO }
```

```java
Alumno {
  String dni;            // exactamente 9, mayúsculas, sin espacios
  String nombre;
  String casa;           // null en NUEVOS
  String patronus;       // obligatorio en NUEVOS
  Origen origen;
  TipoAlumno tipo;
  long version;          // estado previo para control de concurrencia
  Instant actualizadoEn;
  boolean activo;        // borrado lógico (nuevos)
}
```

**Validaciones (en `util/Validaciones`, testeadas):**
- DNI: 9 caracteres, `^[0-9]{8}[A-Z]$`, normalizado a mayúsculas y sin espacios.
- Nombre: 2–80, no vacío.
- Patronus: obligatorio en `NUEVO`, 2–60; opcional en `HISTORICO`.
- Casa: obligatoria en `HISTORICO`, prohibida en `NUEVO`.

---

## 5. Sistema de diseño — "azul tenue"

Restricción del cliente: **azul, discreto, sin fatiga visual**. Prohibido saturado, degradados fuertes,
sombras marcadas. JavaFX **no tiene variables CSS** → se usan *tokens documentados* + clases semánticas.
**No** escribir colores hex sueltos en FXML/Java.

| Token | Valor | Uso |
|---|---|---|
| `primario` | `#4A6FA5` | botones primarios, enlaces |
| `primario-hover` | `#5B82B5` | hover |
| `primario-activo` | `#3D5C87` | pressed |
| `cabecera` | `#3D5A80` | barra superior **plana** |
| `surface` | `#FFFFFF` | tarjetas, campos, tabla |
| `fondo` | `#F5F7FA` | fondo general |
| `borde` | `#D7DEE8` | bordes y separadores |
| `foco` | `#8FB0D9` | anillo de foco 2px |
| `seleccion` | `#E8EEF6` | fila seleccionada / secundario hover |
| `fila-alterna` | `#F8FAFC` | fila impar |
| `texto` | `#1F2937` | texto principal |
| `texto-secundario` | `#6B7A8F` | etiquetas y ayuda |
| `exito` | `#5F8D6E` | OK |
| `error` | `#A96B6B` | error / peligro |
| `aviso` | `#B08D57` | aviso |

- Tipografía: `"Segoe UI", "Inter", "Roboto", sans-serif` → 22 título · 16 sección · 14 cuerpo · 13 botón · 12 cabecera tabla · 11 etiquetas.
- Rejilla de 8 px (8/16/24/32). Radios: tarjeta 16, campo y botón 12.
- Única sombra admitida: `rgba(31,41,55,0.06)`.
- Contraste mínimo AA.

---

## 6. Contrato FXML (`principal.fxml` ↔ `PrincipalController`)

`fx:id` **inmutables** (no renombrar sin actualizar ambos lados):

`campoUrl`, `etiquetaModo`, `botonSalud`, `navEstado`, `navAlumnos`, `navConectores`,
`navSincronizar`, `contenedorContenido`, `estado`, `estadoDerecha`.

Clases CSS raíz: `.raiz`, `.cabecera`, `.lateral`, `.contenido`, `.barra-estado`, `.tarjeta`, `.boton-primario`, `.boton-secundario`, `.campo`, `.tabla`.

---

## 7. Estructura y reparto de archivos (no invadir)

```
gestorhogwartsjavafx/
├── pom.xml                     [BASE]   — 1 solo editor a la vez
├── README.md                   [W5]
├── docs/                       [W5]
└── src/
    ├── main/java/com/omni3/gestorhogwartsjavafx/
    │   ├── App.java            [BASE]
    │   ├── modelo/             [W2]
    │   ├── util/               [W2]
    │   ├── api/                [W2]
    │   ├── servicio/           [W3]
    │   ├── viewmodel/          [W3]
    │   └── vista/              [W4]
    └── main/resources/com/omni3/gestorhogwartsjavafx/vista/
        ├── principal.fxml      [W1]
        ├── estilos.css         [W1]
        ├── estado.fxml         [W4]
        ├── alumnos.fxml        [W4]
        ├── alumno-editar.fxml  [W4]
        ├── conectores.fxml     [W4]
        └── sincronizar.fxml    [W4]
```

**W1** define el esqueleto visual y el CSS. **W2** (modelo+util+api) y **W1** pueden ir en paralelo.
**W3** depende de W2. **W4** depende de W1 y W3. **W5** el último.

---

## 8. Verificación obligatoria (todos los workers)

```powershell
& 'C:\Users\dm2\Documents\Reto01-Omni3\tools\apache-maven-3.9.11\bin\mvn.cmd' `
  -f 'C:\Users\dm2\Documents\Reto01-Omni3\gestorhogwartsjavafx\pom.xml' test -q
# EXIT=0 es obligatorio. Sin él, la tarea NO está terminada.
```

Reglas:
- **Nada de `System.out`** para estado de UI → usar `javafx.application.Platform.runLater` desde hilos de red.
- **Nunca** bloquear el hilo FX: toda llamada a `api/` va en `Thread`/`ExecutorService` y vuelve con callback o `CompletableFuture`.
- Código, Javadoc y mensajes **en español**.
- Sin comentarios sobredimensionados; Javadoc en clases y métodos públicos.
