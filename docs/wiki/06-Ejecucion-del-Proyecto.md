# Instalación y ejecución

Esta guía ofrece primero el recorrido que se verificó en Windows 10: **MariaDB + API + clientes**. Requisitos probados el 20/10/2026: Docker 29.7.2, MariaDB 11.8, Python 3.14.7, JDK 21.0.12, Maven 3.9.16 y SDK Android 37. El proyecto Unity declara Editor **6000.3.25f1**. Otros sistemas pueden funcionar, pero no se comprobaron en esta integración.

## Requisitos por componente

| Componente | Requisitos |
|---|---|
| Descarga | Git y acceso a Internet para clonar y descargar dependencias. |
| MariaDB | Docker con Docker Compose y motor en ejecución; puerto 3306 libre. |
| API | Python 3.14 o superior, `pip` y puerto 8000 libre. No requiere JVM si solo se activa MariaDB. |
| JavaFX | JDK 17 o superior, Maven y sesión gráfica; la integración documenta pruebas con JDK 21. |
| Android | Android Studio, SDK 37 y wrapper Gradle incluido; el archivo `gradle-daemon-jvm.properties` solicita JDK 25 para el daemon. Dispositivo Android 7.0/API 24 o superior. |
| Unity | Unity Hub y Editor 6000.3.25f1 con soporte para la plataforma de destino. |
| Scripts PowerShell | PowerShell 7 para `scripts/check-api.ps1`; en Bash se pueden usar las peticiones `curl` equivalentes. |

Para comprobar API y persistencia basta con Git, Docker y Python. Los clientes se abren por separado; no es necesario instalar los tres para validar el CRUD HTTP.

## 1. Clonar y preparar MariaDB

```bash
git clone -b integration https://github.com/koldoaso/Reto01-Omni3.git
cd Reto01-Omni3
```

La entrega documentada está en la rama [`integration`](https://github.com/koldoaso/Reto01-Omni3/tree/integration), no en el contenido de la rama predeterminada `main`. Ejecuta los comandos siguientes desde la raíz de ese clon, salvo donde se indique un cambio de carpeta.

En PowerShell:

```powershell
Copy-Item .env.example .env
# Edita .env: fija MARIADB_ROOT_PASSWORD y MARIADB_PASSWORD propios.
docker compose up -d mariadb
docker compose ps mariadb
```

Espera a que MariaDB figure como `healthy`. Si no arranca, consulta `docker compose logs mariadb`. En un clon nuevo, `ALUMNOS` empieza vacía y `PATRONUS_IMG` contiene `Otter`.

En Bash: `cp .env.example .env` y los mismos comandos Docker. `compose.yaml` exige también las variables Oracle al interpolar, aunque solo se arranque MariaDB; los valores de ejemplo las proporcionan. El montaje `database/mariadb/01_schema.sql` crea tablas en un **volumen nuevo**. En uno existente, comprueba `SHOW TABLES` y aplica ese SQL de forma manual sin borrar datos.

## 2. Configurar y arrancar API

En PowerShell:

```powershell
Copy-Item api/.env.local.example api/.env
# Edita api/.env: usuario, contraseña, base y puerto deben coincidir con Compose.
py -3.14 -m venv api/.venv
& api/.venv/Scripts/python.exe -m pip install -e ./api
& api/.venv/Scripts/python.exe -m uvicorn proyecto_reto1.main:app --host 127.0.0.1 --port 8000
```

La correspondencia de configuración para una API ejecutada en el equipo anfitrión es:

| `api/.env` | Valor o correspondencia |
|---|---|
| `DB_ENGINES` | `mariadb` |
| `MARIADB_HOST` | `127.0.0.1` |
| `MARIADB_PORT` | `3306`, puerto publicado por `compose.yaml`; el valor por defecto del código es distinto, por lo que debe indicarse. |
| `MARIADB_DATABASE` | Mismo nombre que en `.env` raíz; el ejemplo usa `r01_central`. |
| `MARIADB_USER` | Mismo usuario que en `.env` raíz. |
| `MARIADB_PASSWORD` | Misma contraseña que en `.env` raíz. |

La configuración se debe guardar antes de arrancar Uvicorn; si la cambias después, detén y vuelve a iniciar el proceso. La API lee específicamente `api/.env`; Compose lee el `.env` raíz.

En Bash, usa esta secuencia y edita `api/.env` antes de crear el entorno:

```bash
cp api/.env.local.example api/.env
# Editar api/.env con los valores anteriores antes de continuar.
python3.14 -m venv api/.venv
api/.venv/bin/python -m pip install -e ./api
api/.venv/bin/python -m uvicorn proyecto_reto1.main:app --host 127.0.0.1 --port 8000
```

Se puede usar `uv sync` desde `api/` si se instala uv, pero no es obligatorio.

En otra terminal: `curl http://127.0.0.1:8000/openapi.json` comprueba el proceso; `curl http://127.0.0.1:8000/alumnos` comprueba la conexión y el esquema. `/docs` ofrece Swagger. Si `/alumnos` devuelve 503, revisa `DB_ENGINES`, credenciales y tablas.

En PowerShell 7, `./scripts/check-api.ps1` valida OpenAPI y muestra la cantidad de alumnos. Abre [Swagger local](http://127.0.0.1:8000/docs) para probar las operaciones con **Try it out** y **Execute**. Un listado `[]` con HTTP 200 es el resultado esperado antes de crear el primer alumno.

## 3. Cliente JavaFX

```powershell
Copy-Item gestorhogwartsjavafx/config.properties.example gestorhogwartsjavafx/config.properties
# Cambia api.url a http://127.0.0.1:8000
mvn -f gestorhogwartsjavafx/pom.xml test
mvn -f gestorhogwartsjavafx/pom.xml javafx:run
```

En Bash usa `cp` y `mvn` con las mismas rutas. Requiere JDK 17+ y Maven; JavaFX se descarga con Maven. Alternativamente define `GESTOR_API_URL=http://127.0.0.1:8000` sin archivo. La interfaz gráfica requiere sesión de escritorio. El README específico contiene detalles de controles y traducciones, pero algunas rutas históricas a `tools/apache-maven...` ya no están en esta rama.

## 4. Cliente Android

Abre `gestorhogwartsmovil/` en Android Studio con SDK 37 y un dispositivo o emulador API 24+. La rama actual trae una URL inicial de túnel HTTPS en `app/build.gradle.kts`; su disponibilidad no es un requisito de esta demo. En la pantalla principal, escribe **`http://10.0.2.2:8000`** en **URL de la API** y pulsa **Aplicar** para conectar el emulador estándar con el equipo anfitrión. La elección se conserva al reiniciar.

También puedes definir esa dirección inicial al compilar:

```powershell
$env:ANDROID_HOME = 'RUTA_A_TU_ANDROID_SDK'
cd gestorhogwartsmovil
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug -Pomni3ApiUrl=http://10.0.2.2:8000
```

En Bash usa `./gradlew` y `export ANDROID_HOME=...`. También se admite `GESTOR_API_URL` durante la compilación. La propiedad Gradle tiene prioridad sobre esa variable; una dirección guardada desde la interfaz sustituye el valor inicial de `BuildConfig`. El APK queda en `app/build/outputs/apk/debug/`: instálalo en el dispositivo o usa **Run** desde Android Studio. Con un emulador conectado, `./gradlew.bat :app:connectedDebugAndroidTest` ejecuta las pruebas instrumentadas; no equivale a las pruebas unitarias.

Para un dispositivo físico, establece en el campo de URL una IP LAN del equipo, por ejemplo `http://192.168.1.20:8000`, ajustándola a tu red. Ejecuta la API con `--host 0.0.0.0` en una red local de prueba y permite el puerto 8000 en el cortafuegos. `127.0.0.1` en el móvil apunta al propio móvil y `10.0.2.2` solo corresponde al emulador.

## 5. Cliente Unity

Abre `Omni3_Hogwarts_Unity/` con Unity Editor 6000.3.25f1. Carga `Assets/Scenes/PatronusDirectory.unity` y ejecuta Play. En el campo **API URL**, introduce `http://127.0.0.1:8000` si el Editor y la API están en el mismo equipo; confirma la edición saliendo del campo. Después escribe un prefijo de nombre existente y abre la ficha.

`ApiUrlField` acepta direcciones HTTP/HTTPS y restaura la anterior si el texto es inválido. El cambio se aplica en esa sesión; para fijar el valor inicial edita `apiBaseUrl` en el componente `PatronusApiClient` del Inspector antes de Play. Unity no lee el `.env` de la API. Se conservan todos los `.meta` para mantener referencias de recursos.

## 6. Evaluación y parada

Realiza los casos de la [demo funcional](07-Demo-Funcional.md): alta, listado, filtro, ficha, edición, consulta en clientes y baja. Comprueba los resultados HTTP indicados y la fila SQL para diferenciar una respuesta de interfaz de una escritura persistida.

Al terminar, detén Uvicorn con `Ctrl+C`, cierra los clientes y ejecuta `docker compose stop mariadb` desde la raíz del clon. Los datos permanecen en el volumen; el siguiente `docker compose up -d mariadb` lo reutiliza. No hace falta eliminar volúmenes para repetir la demo.

## Problemas habituales

- `SDK location not found`: configura `ANDROID_HOME` o `local.properties` local, nunca versiones una ruta personal.
- `503` en la API: el motor listado en `DB_ENGINES` no responde o le falta `ALUMNOS`; prueba primero `mariadb` exclusivamente.
- La tabla no aparece en un volumen MariaDB anterior: el SQL de inicio solo se ejecuta en la creación inicial. Revisa los datos y aplica el SQL explícitamente.
- JavaFX indica falta de conexión: comprueba `api.url`, `GESTOR_API_URL`, `/alumnos` y cortafuegos.
- Android/Unity no muestran imagen: el ejemplo `Otter` tiene URL vacía; proporciona una URL real y autorizada para comprobar imágenes.
- El prototipo usa otra API y otras tablas. Sus instrucciones están en `prototipo/README.md` y no son pasos de esta instalación.

Continuar: [demo funcional](07-Demo-Funcional.md).
