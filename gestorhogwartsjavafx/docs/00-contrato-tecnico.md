# Contrato técnico — Cliente de escritorio JavaFX (Reto R01 · Omni3)

> **Fuente de verdad obligatoria** para todo el desarrollo de esta carpeta.
> Basado en: *Propuesta técnica R01 v5* + *Convenio de colaboración Omni3 · Hogwarts*.
> Si este documento y una implementación discrepan, **gana este documento**.

> **Estado documental del 21/10/2026:** los apartados 1–7 conservan los requisitos
> de referencia, no una certificación de cumplimiento de la entrega. La implementación
> actual difiere en los puntos recogidos en el apartado 9. Esta anotación no cambia
> los requisitos ni acredita la aprobación del cliente. El
> [README del módulo](../README.md) describe el uso actual y la
> [revisión de JavaFX](../../docs/javafx-review.md) detalla las comprobaciones.

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

## 3. Capa de acceso a la API (única)

La aplicación **siempre** habla con la API real: no hay modo demo, ni conmutador de datos, ni
variables de entorno de configuración.

```
api/
├── ClienteAlumnos.java       interfaz única usada por servicios/viewmodels
├── ClienteAlumnosHttp.java   java.net.http.HttpClient + Jackson (única implementación)
├── ConfigApi.java            baseUrl + timeout
├── dto/                      respuestas JSON (AlumnoDto, ConectorDto, SaludDto, ErrorDto)
└── ExcepcionApi.java         codigo + mensaje + request-id + status HTTP
```

> El doble de pruebas `ClienteAlumnosFalso` vive en `src/test/java` (no se empaqueta) y solo
> lo usan las pruebas unitarias.

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
Las rutas no publicadas por la API se responden en el cliente con **501 `NO_IMPLEMENTADO`** y un
mensaje legible.

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

`campoUrl`, `botonSalud`, `navEstado`, `navAlumnos`, `navConectores`,
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
    │   └── controlador/        [W4]   — controladores Java (las vistas no llevan Java)
    └── main/resources/com/omni3/gestorhogwartsjavafx/vista/
        ├── principal.fxml      [W1]
        ├── estilos.css         [W1]
        ├── estado.fxml         [W4]
        ├── alumnos.fxml        [W4]
        ├── formulario-alumno.fxml [W4]
        ├── conectores.fxml     [W4]
        └── sincronizar.fxml    [W4]
```

**W1** define el esqueleto visual y el CSS. **W2** (modelo+util+api) y **W1** pueden ir en paralelo.
**W3** depende de W2. **W4** depende de W1 y W3. **W5** el último.

---

## 8. Verificación obligatoria (todos los workers)

Desde la raíz del repositorio, con JDK 17+ y una instalación externa de Maven 3.9+
disponible en `PATH`:

```bash
mvn -f gestorhogwartsjavafx/pom.xml test -q
```

`EXIT=0` es obligatorio para las pruebas. El repositorio actual no incluye la
instalación de Maven de las antiguas rutas personales. Para verificar también el
empaquetado, usa `mvn -f gestorhogwartsjavafx/pom.xml verify`.

Reglas:
- **Nada de `System.out`** para estado de UI → usar `javafx.application.Platform.runLater` desde hilos de red.
- **Nunca** bloquear el hilo FX: toda llamada a `api/` va en `Thread`/`ExecutorService` y vuelve con callback o `CompletableFuture`.
- Código, Javadoc y mensajes **en español**.
- Sin comentarios sobredimensionados; Javadoc en clases y métodos públicos.

## 9. Correspondencia con la entrega actual · 21/10/2026

| Requisito de referencia | Implementación observada | Estado |
|---|---|---|
| Consulta y CRUD por la API común (§2–§3) | HTTP contra `/alumnos` y `/patronus`; las rutas `/students` del contrato no están publicadas por la API principal. | Adaptación al backend actual; contrato HTTP distinto. |
| Edición con estado previo y conflicto 409 (§2.3) | PUT envía nombre, apellido y patronus, sin versión ni estado previo. La interfaz puede ofrecer recarga ante un 409, pero la API no detecta concurrencia. | Pendiente. |
| Baja lógica de nuevos alumnos (§2.4) | DELETE elimina físicamente el registro, también en MariaDB. El atributo `activo` del cliente no cambia esa operación. | Pendiente. |
| Salud `/health`, conectores, sincronización y cola (§2.5 y §3) | Estado comprueba `GET /alumnos`. La API principal no publica esas rutas y el cliente no ofrece las pantallas de conectores/sincronización. | Pendiente; las funciones del prototipo no las sustituyen. |
| `request-id` y DNI enmascarado en logs (§2.6) | El cliente genera la cabecera; hay enmascarado en mensajes de negocio. No hay saneamiento general de trazas ni correlación persistida por el backend. | Parcial. |
| Sin variables de entorno de configuración (§3) | Se admiten `GESTOR_API_URL`, propiedad Java y `config.properties`. | Diferencia con el requisito de referencia. |
| Modelo de dominio (§4) | Record inmutable con validaciones y normalización. Los metadatos de versión y activo no están respaldados por control de concurrencia o baja lógica en el servidor. | Implementación parcial del comportamiento previsto. |
| Paleta azul tenue y ausencia de colores literales en Java (§5) | Tema «dark academia» y un color literal para el fondo de diálogos en `ControladorVista`. | Diferencia de diseño sin aprobación acreditada en esta revisión. |
| Identificadores y vistas originales (§6–§7) | Hay cuatro FXML en `vista/fxml/`; se retiraron campo de URL, navegación y vistas de conectores/sincronización. | Estructura actual distinta; consultar el README para sus rutas. |
| Verificación (§8) | `mvn verify`: 100 pruebas correctas y JAR generado. Javadoc correcto; `mvn site` omite un informe por incompatibilidad de plugins. | Pruebas automáticas registradas y pruebas manuales en dispositivos confirmadas por el equipo; Docker pendiente de verificación. |

No se han revisado los PDF firmados originales en esta comprobación. Para cerrar
las diferencias funcionales se necesita implementar lo requerido o incorporar una
modificación de requisitos aprobada por el cliente; cambiar la documentación por sí
solo no acredita ese cumplimiento.
