# Gestor Hogwarts — Cliente de escritorio (JavaFX)

Cliente de escritorio para **consultar, dar de alta, editar y dar de baja** alumnado
contra la API REST central del Reto R01 · Omni3, con pantalla de estado, catálogo de
patronus y tres idiomas.

> Fuente de verdad del proyecto: [`docs/00-contrato-tecnico.md`](docs/00-contrato-tecnico.md).

---

## 1. Título y descripción

**Título:** Gestor Hogwarts — Cliente de escritorio (JavaFX) · `com.omni3:gestorhogwartsjavafx:1.0.0`

La aplicación habla **siempre por HTTP con la API remota**: no existe modo demo, ni
conmutador de datos, ni acceso directo a bases de datos (requisito del contrato §2–§3).
El único cliente es `api/ClienteAlumnosHttp` (`java.net.http.HttpClient` + Jackson).

| Pantalla | Qué hace |
|---|---|
| **Estado del sistema** | Comprueba la API real con `GET /alumnos` (estado, mensaje, momento y `request-id`). La API no publica `/health`. |
| **Alumnos** | Consulta unificada con **fuente de origen visible**, filtro por origen, búsqueda por DNI/nombre, alta, edición, baja y ficha. |

Reglas de negocio implementadas:

- **Alta** solo en la central MariaDB: DNI de 9 caracteres no duplicado, **patronus
  obligatorio** y **sin casa**.
- **Edición**: se lee la ficha vigente y se envía `PUT /alumnos/{dni}` con el nombre
  completo y el patronus; la fuente (`casa`) es de solo lectura porque la API no admite
  cambiarla.
- **Baja**: `DELETE /alumnos/{dni}`; en los alumnos históricos la base de origen aplica
  su propio comportamiento.
- Los DNI **nunca se muestran completos** en mensajes (se enmascaran: `12***78A`) y toda
  petición lleva cabecera **`request-id`** visible cuando hay error.
- **Tres idiomas** (castellano, euskera, inglés) con cambio en caliente desde la barra
  de estado (§9).

Endpoints que consume la API:

| Método | Ruta | Uso |
|---|---|---|
| `GET` | `/alumnos` | Listado unificado (búsqueda, filtro y paginación se aplican en el cliente) |
| `GET` | `/alumnos/{dni}` | Ficha de un alumno |
| `POST` | `/alumnos` | Alta (cuerpo `{id, nombre, apellido?, sexo?, patronus?}`) |
| `PUT` | `/alumnos/{dni}` | Edición (cuerpo `{nombre, apellido?, patronus?}`) |
| `DELETE` | `/alumnos/{dni}` | Baja (`204`; `404` si no existe) |
| `GET` | `/patronus` | Catálogo de patronus para el formulario |

Los errores se aceptan en los dos formatos del backend
(`{"error":{codigo,mensaje,detalle}}` o `{"detail":"…"}`); cualquier respuesta `>= 400`
se convierte en `ExcepcionApi` (código, mensaje, `request-id`, estado HTTP) y la
pantalla pasa a estado **ERROR**; el `409` se trata aparte como **CONFLICTO**.

---

## 2. Requisitos previos

| Componente | Versión | Notas |
|---|---|---|
| Sistema | Windows 10/11 x86-64 (también Linux y macOS) | Los perfiles de `pom.xml` (`win`, `linux`, `mac`, `mac-aarch64`) eligen el clasificador JavaFX según el SO. |
| Java | **JDK 17 o superior** (`maven.compiler.release=17`) | Verificado con Temurin 25.0.4.1 sin cambiar nada. |
| Maven | **3.9 o superior** | En este repositorio ya va incluido en `../tools/apache-maven-3.9.11/`. |
| JavaFX | **21** (usa `21.0.5`) | **No hay que instalarlo a mano**: lo descarga Maven Central. |
| Memoria | 8 GB (16 GB recomendados) | §1 del contrato. |
| Red | Internet en el primer build | Para bajar dependencias de Maven Central. |
| API | Accesible por HTTP | Por defecto `http://127.0.0.1:8000`; se configura en §4. |
| Docker (opcional) | Cualquier versión con daemon activo | Solo para el contenedor de §5. |

---

## 3. Instalación de todo lo necesario

1. **JDK 17 o superior.** Instala [Eclipse Temurin](https://adoptium.net/) (u otra
   distribución) y comprueba:

   ```bash
   java -version    # debe informar de 17 o superior
   ```

2. **Maven.** Usa el que trae el repositorio (no requiere instalación):

   ```powershell
   & 'C:\Users\dm2\Documents\Reto01-Omni3\tools\apache-maven-3.9.11\bin\mvn.cmd' -v
   ```

   O instala Maven 3.9+ en el `PATH` y sustituye `mvn.cmd` por `mvn` en los ejemplos.

3. **Clonar el repositorio:**

   ```bash
   git clone https://github.com/dm2-ethazi/Reto01-Omni3.git
   cd Reto01-Omni3/gestorhogwartsjavafx
   ```

4. **Primer build** (descarga dependencias y empaqueta; `EXIT=0` es obligatorio):

   ```bash
   mvn clean package
   ```

5. **Ejecutar las pruebas** (§6): `mvn test` → **100 pruebas, 0 fallos**.

6. **Configurar la URL de la API** (§4): copia la plantilla:

   ```bash
   cp config.properties.example config.properties   # Windows: copy config.properties.example config.properties
   ```

   y rellena `api.url`. Si no lo haces, la aplicación muestra un aviso traducido y
   continúa con la API local (§4 y §11).

7. **Opcional — IDE:** abre la carpeta del módulo como proyecto Maven (IntelliJ IDEA,
   VS Code + Extension Pack for Java, Eclipse). Compila y lanza desde la terminal o el
   panel Maven con `javafx:run` (§5): es la forma que Maven resuelve JavaFX por ti.

8. **Opcional — contenedor:** Docker solo para el build de §5e; la ventana corre en un
   X virtual (Xvfb).

---

## 4. Configuración de la API

La URL de la API **no está en el código ni se edita desde la ventana**: la resuelve
`util/ConfigLoader` siguiendo este orden (**primero que encuentre, gana**):

| # | Fuente | Detalle |
|---|---|---|
| 1 | Propiedad de sistema `-Dgestor.api.url` | La que usan las pruebas y los lanzamientos puntuales. |
| 2 | Variable de entorno `GESTOR_API_URL` | Cómoda para despliegues y contenedores. |
| 3 | `config.properties` en el **directorio de trabajo** | Clave `api.url`; es donde estés al lanzar. |
| 4 | `config.properties` **junto al JAR** | Clave `api.url`; válida al hacer `java -jar` desde otro directorio. |
| 5 | `config.properties` en el **classpath** | Uso puntual desde el IDE o pruebas. |

Plantilla versionada (`config.properties.example`):

```properties
# URL base de la API. NO subir este fichero a git: copia este ejemplo
# a config.properties (está en .gitignore) y escribe aquí tu URL real.
api.url=https://tu-api-aqui.com
```

Para desarrollo local con la API del propio equipo:

```properties
api.url=http://127.0.0.1:8000
```

Normalización aplicada al valor (en `ConfigApi`): se recortan espacios, se quitan las
`/` finales, se antepone `http://` si no traes esquema (puedes escribir solo
`127.0.0.1:8000`) y, si la URI queda inválida, se rechaza con un mensaje de error.

**Si ninguna fuente aporta la URL** se lanza `ExcepcionConfiguracion`; la aplicación
**no se cierra**: muestra el aviso «No se pudo cargar la configuración de la API» con
instrucciones traducidas, registra el error en `gestor-hogwarts.log` y continúa con la
API local `http://127.0.0.1:8000`. El timeout de peticiones es de 10 segundos.

Comprobación rápida de que la API responde (antes de arrancar la app):

```bash
curl http://127.0.0.1:8000/alumnos
# si está tras ngrok, añade la cabecera que el cliente envía siempre:
curl -H "ngrok-skip-browser-warning: 1" https://tu-api-aqui.com/alumnos
```

---

## 5. Todas las formas de ejecutar el programa

### a) En desarrollo, directo con Maven (lo más habitual)

```bash
mvn clean javafx:run
```

Compila y abre la ventana; no necesita build previo.

### b) Fat JAR (recomendado para distribuir)

```bash
mvn clean package
java -jar target/gestorhogwartsjavafx-1.0.0.jar
```

El JAR final lleva dentro las dependencias JavaFX, los FXML/CSS, las fuentes, los
iconos y los bundles i18n; su `Main-Class` es `com.omni3.gestorhogwartsjavafx.Launcher`
(una lanzadera que no extiende `Application`, requisito para poder hacer `java -jar`).

### c) Scripts de lanzamiento

Tras hacer `mvn clean package` (§b):

- **Windows:** `run.bat`
- **Linux/macOS:** `sh run.sh`

Se ejecutan desde la carpeta del módulo (usan la ruta relativa `target/…`).

### d) Con configuración o idioma forzados

```bash
# URL de la API por propiedad de sistema (gana sobre todo lo demás)
java -Dgestor.api.url=https://tu-api-aqui.com -jar target/gestorhogwartsjavafx-1.0.0.jar

# por variable de entorno (PowerShell)
$env:GESTOR_API_URL="https://tu-api-aqui.com"; java -jar target/gestorhogwartsjavafx-1.0.0.jar

# por variable de entorno (Linux/macOS)
GESTOR_API_URL=https://tu-api-aqui.com java -jar target/gestorhogwartsjavafx-1.0.0.jar

# idioma inicial forzado (si no, sigue al del sistema: es, eu, en o inglés por defecto)
java -Dgestor.idioma=eu -jar target/gestorhogwartsjavafx-1.0.0.jar
```

### e) En contenedor Docker

```bash
# desde la raíz del repositorio
docker build -f gestorhogwartsjavafx/Dockerfile -t gestorhogwarts-javafx gestorhogwartsjavafx
docker run --rm gestorhogwarts-javafx
```

El build usa `maven:3.9-eclipse-temurin-17` y la imagen final
`eclipse-temurin:17-jre-jammy` con Xvfb: la ventana se pinta en un X virtual, así que
el contenedor **arranca sin servidor gráfico** (si la API no está, muestra el aviso de
conexión y sigue vivo). Para ver la interfaz en el host hay que montar un X11
(X410/VcXsrv en Windows); el objetivo del contenedor es el despliegue reproducible,
no sustituir al JAR nativo. Para inyectar la URL:
`docker run --rm -e GESTOR_API_URL=https://tu-api-aqui.com gestorhogwarts-javafx`
(o monta un `config.properties` con `-v`).

> **Nota de verificación:** el Dockerfile está revisado y su `CMD` probado en local
> con los mismos Java y JAR, pero el `docker build` **no se ha ejecutado en este
> entorno** (el demonio de Docker estaba apagado); ejéctutalo tú en el paso 1.

### f) Sin API levantada

La aplicación **arranca igual**: la pantalla de estado muestra el error de conexión con
su `request-id` y no se cierra (comportamiento verificado).

---

## 6. Comandos Maven útiles

Todos estos comandos se han verificado en este entorno con `EXIT=0`:

| Comando | Qué hace |
|---|---|
| `mvn clean package` | Limpia, compila, ejecuta las **100 pruebas** y genera el fat JAR en `target/`. |
| `mvn test` | Solo pruebas (Surefire). Fija `gestor.idioma=es` para que sean independientes del equipo. |
| `mvn clean javafx:run` | Compila y abre la ventana en desarrollo. |
| `mvn dependency:resolve` | Descarga/comprueba todas las dependencias (útil para el primer build o para CI). |
| `mvn javadoc:javadoc` | Genera la Javadoc en `target/reports/apidocs/index.html`. |
| `mvn site` | Genera el informe del sitio en `target/site/index.html` (aviso inofensivo de anclas XHTML). |
| `mvn clean` | Borra `target/` (build completo limpio antes de empaquetar). |

- **Perfiles de plataforma** (se activan solos según el SO; se pueden forzar con
  `-P`): `win`, `linux`, `mac`, `mac-aarch64`. Sirven para empaquetar los binarios
  JavaFX de otro sistema desde esta máquina.
- Las pruebas **no arrancan el toolkit JavaFX** (usan ejecutores síncronos y un doble
  del cliente) y son deterministas: doble de cliente HTTP, mapeo de DTOs, validaciones,
  servicio, fábrica, `ConfigLoader`, idiomas, catálogo de patronus y viewmodels —
  12 clases, 100 pruebas.
- Ruta larga si Maven no está en el `PATH`:

  ```powershell
  & 'C:\Users\dm2\Documents\Reto01-Omni3\tools\apache-maven-3.9.11\bin\mvn.cmd' -f '.\pom.xml' clean package
  ```

---

## 7. Estructura del proyecto justificada

```
gestorhogwartsjavafx/
├── pom.xml                      build: perfiles multiSO, shade (fat JAR), surefire
├── README.md                    este documento
├── docs/00-contrato-tecnico.md  fuente de verdad del desarrollo
├── config.properties.example    plantilla versionada (la real va en .gitignore)
├── Dockerfile · run.bat · run.sh despliegue y lanzamiento rápido
└── src/
    ├── main/java/com/omni3/gestorhogwartsjavafx/
    │   ├── App.java             ventana, escena y arranque
    │   ├── Launcher.java        lanzadera para `java -jar` (no extiende Application)
    │   ├── modelo/              Alumno, Origen, TipoAlumno, CatalogoPatronus
    │   ├── util/                Validaciones, ResultadoValidacion, Idiomas,
    │   │                        ConfigLoader, ExcepcionConfiguracion, Registro
    │   ├── api/                 ClienteAlumnos (interfaz), ClienteAlumnosHttp,
    │   │                        ConfigApi, ExcepcionApi, dto/
    │   ├── servicio/            ServicioAlumnos (hilos de fondo), FabricaAplicacion
    │   ├── viewmodel/           Consulta, Alta, Edición, Baja, Salud, Formulario…
    │   └── controlador/         controladores de cada pantalla + ControladorVista
    ├── main/resources/
    │   ├── com/omni3/gestorhogwartsjavafx/
    │   │   ├── vista/fxml/      principal, estado, alumnos, formulario-alumno (sin Java)
    │   │   ├── vista/css/tema.css   sistema de diseño «dark academia»
    │   │   ├── fonts/ icons/    Cinzel y Cormorant Garamond; icon-16…512
    │   └── i18n/                messages{_es,_eu,_en}.properties (§9)
    └── test/java/…              espejo de los paquetes + ClienteAlumnosFalso (solo tests)
```

**Por qué así:**

- **Reparto del contrato (§7):** las vistas son solo FXML/CSS (`vista/`), los
  controladores solo Java (`controlador/`) y cada paquete tiene su zona; nadie invade
  la carpeta de otro.
- **MVVM en tres capas:** `controlador` → `viewmodel` (propiedades observables) →
  `servicio` → `api` → la respuesta vuelve con
  `Platform.runLater` al viewmodel y de ahí a la vista. **El hilo de JavaFX nunca se
  bloquea**: toda llamada de red va en un `ExecutorService`.
- **Una sola puerta a la API:** la interfaz `ClienteAlumnos` con una única
  implementación HTTP (`ClienteAlumnosHttp`); el doble `ClienteAlumnosFalso` vive en
  `src/test` y **no se empaqueta**.
- **`util/`** agrupa lo transversal y testeable: reglas de validación, resolución de
  idioma, carga de configuración, excepción de configuración y registro de eventos.
- **`i18n/` en `resources`** para que los bundles viajen dentro del JAR (§10).
- **`config.properties` fuera de `src/`**: así Maven no lo copia a `target/` ni al
  JAR (§10).

Flujo de una petición:

```mermaid
flowchart LR
    controlador["Controlador JavaFX"] --> vistaModelo["ViewModel"]
    vistaModelo --> servicio["ServicioAlumnos"]
    servicio -->|ExecutorService| cliente["ClienteAlumnosHttp"]
    cliente -->|HTTP y JSON| api["API de alumnos"]
    api --> cliente
    cliente -->|Callback| hilo["Platform.runLater"]
    hilo --> vistaModelo
    vistaModelo --> vista["Vista FXML"]
```

---

## 8. Decisiones de diseño y tecnologías

| Tecnología | Versión | Por qué se eligió |
|---|---|---|
| JavaFX | 21.0.5 (`controls`, `fxml`, `graphics`, `base`) | Requisito del contrato §1; FXML declara la vista sin lógica. |
| Java | release 17 (verificado con JDK 25) | Compatibilidad mínima exigida; ninguna dependencia pide otra versión. |
| Maven | 3.9+ con perfiles por SO | Sola compilación (contrato); perfiles para el clasificador JavaFX de cada plataforma. |
| Jackson | 2.18.11 | Serializa los DTO JSON del backend sin traer un servidor ni javax. |
| `java.net.http.HttpClient` | incluido en el JDK | Cliente HTTP moderno y suficiente (timeouts, HTTP/2); cero dependencias extra. |
| `maven-shade-plugin` | 3.5.1 | Fat JAR con `Main-Class=Launcher`; Launcher evita el error «JavaFX runtime components are missing» de hacer `java -jar` sobre `Application`. |
| `javafx-maven-plugin` | 0.0.8 | `mvn javafx:run` en desarrollo. |
| JUnit Jupiter | 5.10.3 | Pruebas unitarias sin arrancar JavaFX ni la red (doble falso). |
| Sin module-info | classpath | Empaquetado simple y compatible con shade; el aviso «unnamed module» al lanzar es cosmético (§11). |
| `util/Registro` propio | fichero `gestor-hogwarts.log` | Registro UTF-8 en apéndice sin añadir log4j/slf4j; cero dependencias. |
| Bundles `ResourceBundle` | 4 ficheros | i18n estándar del JDK, sin frameworks; `config.properties` aparte (§10). |

- **Patrón MVVM:** los viewmodels exponen `Property` de JavaFX; la vista solo traduce
  estado visible. Nunca hay lógica de negocio ni red en los FXML.
- **Sistema de diseño «dark academia»:** tokens documentados y clases semánticas en
  `tema.css` (§5 del contrato); sin colores hex sueltos en FXML/Java, contraste AA,
  tipografías Cinzel/Cormorant incluidas en el JAR.
- **Configuración por entorno (12-factor)** en vez de campo editable en la UI: la URL
  vive fuera del código (§4) y no cambia entre compilaciones.
- **Logger condicional propio:** los puntos de log se mantienen (`aviso`, `info`,
  `error` con traza) y todo `System.out/err` de depuración fue sustituido por
  `Registro` (§9).

**Limitaciones heredadas de la API (no son fallos del cliente):**

- La fuente (`casa`) no es editable: la API identifica la base con la que responde.
- El backend no versiona: `version = 0`, no hay `409` por concurrencia real.
- `GET /alumnos` devuelve el listado completo: filtro, búsqueda y paginación se aplican
  en el cliente.
- No hay autenticación ni nube: añadirlas serían cambios de alcance (§2 del contrato).

---

## 9. Internacionalización, logs y accesibilidad

### Internacionalización

- **Cuatro bundles** en `src/main/resources/i18n/`:
  `messages.properties` (base **inglesa**, red de seguridad), `messages_es.properties`,
  `messages_eu.properties` y `messages_en.properties`; **135 claves idénticas** en los
  cuatro ficheros (0 faltantes; 133 usadas por la interfaz).
- En **FXML** los textos son `%clave` (`text="%boton.aceptar"`); en **Java**,
  `Idiomas.texto("clave", …)` (con `MessageFormat` para los parámetros).
- **Idioma inicial:** sigue al del sistema cuando es `es`, `eu` o `en`; cualquier otro
  locale cae a inglés. Se fuerza con `-Dgestor.idioma=es|eu|en` (lo usan las pruebas).
- **Cambio en caliente** con el desplegable de la barra de estado (`ES`/`EU`/`EN`):
  cambia los textos de la pantalla visible sin reiniciar ni perder datos.
- Si apareciera una clave no traducida se vería `!clave!` o un
  `MissingResourceException`: la solución está en §11.

### Logs

- Fichero **`gestor-hogwarts.log`** (UTF-8, apéndice) en el directorio de trabajo;
  también se replica en consola. Si el disco no permite escribir, el aviso va a
  `System.err` y la app continúa.
- Puntos de registro (`util/Registro`): `aviso`, `info`, `error` (con traza de la
  excepción). Ejemplos reales:

  ```text
  …INFO  URL de la API resuelta desde fichero config.properties
  …INFO  Aplicación iniciada
  …ERROR Configuración de la API ausente o inválida (ExcepcionConfiguracion); se continúa con la API local
  …ERROR Fallo de red al consultar la API (petición 22f5d0b0-…)
  ```

- El registro escribe el **origen** de la configuración, **nunca el valor**; los
  mensajes no incluyen credenciales (§10), los DNI van enmascarados y cada petición
  arrastra su `request-id`.

### Accesibilidad

- **Tooltips** en todos los botones: 24 en los FXML (`%tooltip.*`) más los de los
  diálogos y del icono de la aplicación.
- **Mnemónicos** (`mnemonicParsing="true"`, 5 nodos) y **enlaces etiqueta↔campo**
  (`labelFor`, 5 en el formulario) resueltos en `FormularioAlumnoController`.
- **Campos con texto de ayuda** (`promptText`, 5: búsquedas y guías de cada campo).
- **Atajos de teclado:** `Ctrl`/`⌘` + `N` (alumno nuevo), `Ctrl`/`⌘` + `F` (buscar) y
  `F1` (ayuda).
- **Dimensiones mínimas** (`minWidth`/`minHeight`) y ventana redimensionable: el
  contenido no se corta al reducirla.
- Contraste AA y foco visible definidos en `tema.css`; la interfaz se puede recorrir
  con teclado.

---

## 10. Seguridad

- **`config.properties` nunca viaja versionado ni empaquetado:**

  | Garantía | Dónde está |
  |---|---|
  | Fuera de git | `.gitignore` del módulo y de la raíz (`config.properties`) |
  | Fuera de `target/` | `maven-resources-plugin` → `<excludes>` en `pom.xml` |
  | Fuera del fat JAR | filtro de `maven-shade-plugin` en `pom.xml` |
  | Fuera de la imagen | `.dockerignore` |
  | Plantilla versionada | `config.properties.example` (valor de ejemplo, sin secreto) |

  Se puede comprobar en cualquier momento (desde la carpeta del módulo):

  ```bash
  git check-ignore config.properties          # debe imprimir la ruta
  jar tf target/gestorhogwartsjavafx-1.0.0.jar | findstr config.properties   # Windows: sin salida
  # Linux/macOS: jar tf target/gestorhogwartsjavafx-1.0.0.jar | grep config.properties
  ```

- **La URL real no aparece en el código:** la única URL escrita en Java es la local de
  desarrollo `http://127.0.0.1:8000` (`ConfigApi.URL_POR_DEFECTO`), usada por
  `porDefecto()` y por el aviso de respaldo.
- **Mensajes sin secretos:** el aviso de configuración solo explica cómo crear el
  fichero (sin imprimir ninguna URL ajena); el registro anota el *origen* resuelto y
  jamás el valor. Los DNI se enmascaran (`12***78A`) y toda respuesta queda ligada a su
  `request-id`.
- **Cabeceras:** el cliente envía `ngrok-skip-browser-warning` (cabecera pública de
  ngrok, sin credenciales) para que el túnel no interponga su página de aviso.
- **Sin autenticación:** fuera de alcance según el contrato; no hay usuarios, tokens ni
  claves en el cliente.
- **Recomendaciones operativas:** nunca subas tu `config.properties` (por eso está en
  `.gitignore`); si una URL con credenciales llega a filtrarse en el historial de git,
  rota el túnel/credenciales y limpia el historial con `git filter-repo` o BFG.
  La API documenta su propia configuración secreta (`.env`) en el
  [README de la raíz](../README.md).

---

## 11. Solución de problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| Aviso **«No se pudo cargar la configuración de la API»** al arrancar | No hay `config.properties` ni variables | Copia `config.properties.example` a `config.properties` junto al JAR o en el directorio de trabajo y rellena `api.url` (§4). La app sigue abierta con la API local. |
| **«No se pudo contactar con la API en http://127.0.0.1:8000 (conexión rechazada)»** | La API no está levantada o la URL es otra | Levanta la API y comprueba con `curl /alumnos`; si vive en otro sitio, cambia `api.url` (§4) y pulsa «Comprobar ahora». |
| Textos con **`!clave!`** o `MissingResourceException` | Falta una clave en algún bundle | Añade la clave a **los cuatro** ficheros de `src/main/resources/i18n/` (base, `_es`, `_eu`, `_en`) con el mismo nombre y vuelve a compilar. |
| Error al cargar un FXML («Controller … not found», campo nulo) | Ruta o `fx:id` desalineados | Las vistas se cargan desde `ControladorVista.RUTA_VISTAS` = `/com/omni3/gestorhogwartsjavafx/vista/fxml/`; `fx:id` y `fx:controller` deben coincidir con el controlador. |
| **«JavaFX runtime components are missing»** | Se lanzó `App` con un classpath incompleto | Usa `mvn javafx:run` o el fat JAR (su `Main-Class` es `Launcher`, §5). |
| `Unsupported JavaFX configuration: classes were loaded from 'unnamed module'` | Aviso de JavaFX con classpath (sin module-info) | Cosmético e inofensivo; documentado como decisión en §8. |
| `WARNING: … System::load … / Restricted methods` | Java moderno pide `--enable-native-access` al cargar librerías nativas | Solo avisos de la JDK; no afectan al funcionamiento. |
| **`mvn` no se reconoce** | Maven no está en el `PATH` | Usa la ruta completa de `../tools/apache-maven-3.9.11/bin/mvn.cmd` (§6). |
| `java -version` informa de algo menor que 17 | JDK antiguo o `JAVA_HOME` mal apuntando | Instala Temurin 17+ (§2–§3) y reabre la terminal. |
| Fallan pruebas tras un cambio grande | Artefactos de `target/` desactualizados | `mvn clean test`. |
| El log `gestor-hogwarts.log` no aparece | El directorio de trabajo es de solo lectura | Lanza desde una carpeta con permisos de escritura; el error también va a consola. |
| Docker: build no probado o ventana invisible | Demonio apagado / Xvfb es virtual | Activa Docker y ejecuta los comandos de §5e; para ver la ventana monta un X11. |
| curl devuelve HTML en vez de JSON (túnel ngrok) | Página interstitial de ngrok-free | Añade `-H "ngrok-skip-browser-warning: 1"` (el cliente ya la envía siempre). |
| Aparece una carpeta `tests-pendientes/` local | Material de trabajo no versionado | Fuera del alcance de este repositorio; no forma parte del build. |

---

## 12. Autores y licencia

- **Desarrollo principal:** [jairoben](https://github.com/koldoaso) — Jairo Ben
  (`jairoben` en `pom.xml`, autor de la práctica totalidad de los commits).
- **Colaboraciones:** henritejada2006 y koldoaso (proprietario del repositorio).
- **Repositorio:** <https://github.com/dm2-ethazi/Reto01-Omni3> — rama `javafxapp`.
- **Documentación relacionada:**
  [contrato técnico del cliente](docs/00-contrato-tecnico.md) ·
  [README de la API](../README.md).

**Licencia:** el repositorio **no incluye fichero `LICENSE`**, así que no se declara
licencia formal: el código forma parte del Reto R01 · Omni3 y sus autores conservan
sus derechos. Si quieres reutilizarlo o distribuirlo, añade un fichero `LICENSE` a la
raíz o pide permiso a los autores.
