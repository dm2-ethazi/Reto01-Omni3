# Instalación y ejecución

Esta guía ofrece primero el recorrido que se verificó en Windows 10: **MariaDB + API + clientes**. Requisitos probados el 20/10/2026: Docker 29.7.2, MariaDB 11.8, Python 3.14.7, JDK 21.0.12, Maven 3.9.16 y SDK Android 37. El proyecto Unity declara Editor **6000.3.25f1**. Otros sistemas pueden funcionar, pero no se comprobaron en esta integración.

## 1. Clonar y preparar MariaDB

```bash
git clone -b integration https://github.com/koldoaso/Reto01-Omni3.git
cd Reto01-Omni3
```

La rama tiene que estar publicada antes de ejecutar el comando anterior; si aún no lo está, usa el checkout local de `integration`.

En PowerShell:

```powershell
Copy-Item .env.example .env
# Edita .env: fija MARIADB_ROOT_PASSWORD y MARIADB_PASSWORD propios.
docker compose up -d mariadb
docker compose ps mariadb
```

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

En Bash: `cp api/.env.local.example api/.env`, `python3.14 -m venv api/.venv`, `api/.venv/bin/python -m pip install -e ./api` y `api/.venv/bin/python -m uvicorn ...`. Se puede usar `uv sync` desde `api/` si se instala uv, pero no es obligatorio.

En otra terminal: `curl http://127.0.0.1:8000/openapi.json` comprueba el proceso; `curl http://127.0.0.1:8000/alumnos` comprueba la conexión y el esquema. `/docs` ofrece Swagger. Si `/alumnos` devuelve 503, revisa `DB_ENGINES`, credenciales y tablas.

## 3. Cliente JavaFX

```powershell
Copy-Item gestorhogwartsjavafx/config.properties.example gestorhogwartsjavafx/config.properties
# Cambia api.url a http://127.0.0.1:8000
mvn -f gestorhogwartsjavafx/pom.xml test
mvn -f gestorhogwartsjavafx/pom.xml javafx:run
```

En Bash usa `cp` y `mvn` con las mismas rutas. Requiere JDK 17+ y Maven; JavaFX se descarga con Maven. Alternativamente define `GESTOR_API_URL=http://127.0.0.1:8000` sin archivo. La interfaz gráfica requiere sesión de escritorio. El README específico contiene detalles de controles y traducciones, pero algunas rutas históricas a `tools/apache-maven...` ya no están en esta rama.

## 4. Cliente Android

Abre `gestorhogwartsmovil/` en Android Studio con SDK 37 y un emulador API 33+. En un emulador estándar, el valor por defecto `http://10.0.2.2:8000` llega al localhost del host. Para dispositivo físico o servidor remoto, recompila con URL accesible desde ese dispositivo:

```powershell
$env:ANDROID_HOME = 'RUTA_A_TU_ANDROID_SDK'
cd gestorhogwartsmovil
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug -Pomni3ApiUrl=https://tu-servidor
```

En Bash usa `./gradlew` y `export ANDROID_HOME=...`. También se admite `GESTOR_API_URL` durante la compilación. La URL se inserta en `BuildConfig`, así que una URL interna no se debe incluir en una distribución pública. Para dispositivo físico con API local, usa una IP LAN accesible y configura red y cortafuegos; `10.0.2.2` solo corresponde al emulador. El APK queda en `app/build/outputs/apk/debug/`.

## 5. Cliente Unity

Abre `Omni3_Hogwarts_Unity/` con Unity Editor 6000.3.25f1. Carga `Assets/Scenes/PatronusDirectory.unity`, selecciona el objeto que contiene `PatronusApiClient` y establece `apiBaseUrl` a una URL alcanzable desde el Editor o build. La URL serializada es local; Unity no lee el `.env` de la API. Ejecuta Play, escribe un prefijo de nombre existente y abre la ficha. Se conservan todos los `.meta` para mantener referencias de recursos.

## Problemas habituales

- `SDK location not found`: configura `ANDROID_HOME` o `local.properties` local, nunca versiones una ruta personal.
- `503` en la API: el motor listado en `DB_ENGINES` no responde o le falta `ALUMNOS`; prueba primero `mariadb` exclusivamente.
- La tabla no aparece en un volumen MariaDB anterior: el SQL de inicio solo se ejecuta en la creación inicial. Revisa los datos y aplica el SQL explícitamente.
- JavaFX indica falta de conexión: comprueba `api.url`, `GESTOR_API_URL`, `/alumnos` y cortafuegos.
- Android/Unity no muestran imagen: el ejemplo `Otter` tiene URL vacía; proporciona una URL real y autorizada para comprobar imágenes.
- El prototipo usa otra API y otras tablas. Sus instrucciones están en `prototipo/README.md` y no son pasos de esta instalación.

Continuar: [demo funcional](07-Demo-Funcional.md).
