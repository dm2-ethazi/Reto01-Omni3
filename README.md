# Reto01 · Omni3 — Gestor Hogwarts

API de alumnado distribuido y tres clientes. La rama `integration` incorpora las correcciones del feedback ADAT y de la propuesta v6 aceptada. La integración original conectó las puntas de `Api`, `androidapp`, `javafxapp`, `unityClient`, `development`, `prototipo` y `main` mediante commits de fusión; el [informe histórico](docs/integration-audit.md) recoge sus referencias de aquel momento. El prototipo es una implementación histórica diferente de la API principal.

## Arquitectura y componentes

| Carpeta | Función | Tecnología y estado comprobado |
|---|---|---|
| `api/` | API REST, servicios de integración y repositorios. | Python 3.11+, FastAPI; MariaDB principal, SQLite de respaldo y cuatro fuentes reales verificadas. |
| `database/` | Esquemas centrales y semillas compatibles de las cuatro fuentes. | MariaDB 11.8, Oracle, H2, HSQLDB y Derby; 985 históricos de los CSV suministrados. |
| `gestorhogwartsjavafx/` | Consulta, altas, edición con versión y baja lógica de nuevos por API. | JavaFX 21/Maven; 102 pruebas correctas, JAR Windows y apertura local comprobados. |
| `gestorhogwartsmovil/` | Consulta y caché offline de alumnado con URL de API editable. | Kotlin/Android/Room; APK, 17 pruebas unitarias, 9 instrumentadas y lint correctos. |
| `Omni3_Hogwarts_Unity/` | Búsqueda y ficha de alumno/patronus. | Unity 6000.3.25f1; proyecto integrado. |
| `prototipo/` | Solución experimental anterior de sincronización. | API, clientes e infraestructura propios; no comparte esquema con `api/`. |
| `scripts/` | Arranque local, preparación de fuentes, comprobación de la API y demo ADAT. | PowerShell y Python. |

Los clientes hablan por HTTP/JSON con `api/`. Android conserva una copia Room. La API principal usa MariaDB como fuente de verdad de nuevos y vista central de históricos; Oracle, H2, HSQLDB y Derby mantienen sus propios registros. SQLite central es el respaldo de lectura y conserva una cola de refrescos con cinco intentos, FAILED y control de saturación. La copia Room pertenece al cliente y es independiente de SQLite central.

```mermaid
flowchart LR
    javafx["Cliente JavaFX"] -->|HTTP y JSON| api["API FastAPI"]
    android["Cliente Android"] -->|HTTP y JSON| api
    unity["Cliente Unity"] -->|HTTP y JSON| api
    android <--> room["Caché Room y SQLite"]
    api --> servicios["Servicios: importación, versiones y reintentos"]
    servicios --> sqlite["SQLite central y cola persistente"]
    servicios --> mariadb["MariaDB: alumnado central y patronus"]
    servicios --> otros["Oracle, H2, HSQLDB y Derby"]
```

## Arranque rápido comprobable

Requiere Docker Compose, Python 3.11+, Java 21+ y una terminal PowerShell. Los ejemplos usan Python 3.14. Desde la raíz:

```powershell
Copy-Item .env.example .env
# Cambia las contraseñas de ejemplo en .env.
Copy-Item api/.env.example api/.env
# Igualar credenciales en ambos archivos; ORACLE_PASSWORD de api/.env
# corresponde a ORACLE_USER_PASSWORD de .env raíz.
docker compose up -d
py -3.14 -m venv api/.venv
& api/.venv/Scripts/python.exe -m pip install --upgrade pip
& api/.venv/Scripts/python.exe -m pip install -e ./api --group ./api/pyproject.toml:dev
& api/.venv/Scripts/python.exe scripts/seed-origins.py
& api/.venv/Scripts/python.exe -m uvicorn proyecto_reto1.main:app --host 127.0.0.1 --port 8000
```

En otra terminal, `./scripts/check-api.ps1` comprueba la identificación R01. Ejecuta `POST /sync` para importar los datos de ejemplo antes de abrir los clientes. `scripts/verify-adat.py` demuestra el recorrido HTTP y comprueba ambas centrales; consulta el [README de la API](api/README.md) para importación selectiva, caída de MariaDB, concurrencia y pruebas. Los volúmenes Docker y `api/data/` deben conservarse al reiniciar. En Linux usa `cp`, `python3.14` y `api/.venv/bin/python`.

JavaFX: entra en `gestorhogwartsjavafx/`, ejecuta `mvn verify`, configura `GESTOR_API_URL` o el `config.properties` de esa carpeta y lanza `mvn javafx:run`. Requiere JDK 17+ y una instalación externa de Maven 3.9+; consulta el [README del cliente](gestorhogwartsjavafx/README.md). Android: abre `gestorhogwartsmovil/` en Android Studio con SDK 37; para usar la API local desde el emulador, establece `http://10.0.2.2:8000` en el campo **URL de la API** y pulsa **Aplicar**. La rama actual trae un túnel HTTPS como valor inicial de compilación, que no es necesario para esa prueba local. Unity: abre `Omni3_Hogwarts_Unity/` en Unity 6000.3.25f1, pulsa Play y cambia la dirección desde el campo **API URL**.

### Instalación local compartida entre clones

El [bloque completo al final](#copiar-y-pegar-montar-y-ejecutar-desde-la-raíz)
prepara el backend, compila JavaFX y arranca el sistema. Usa una instalación por
usuario de Windows, compartida por todos los clones de este repositorio:

- Configuración privada: `%LOCALAPPDATA%/Omni3/R01/local.env`.
- SQLite central y cola persistente: `%LOCALAPPDATA%/Omni3/R01/central.sqlite3`.
- Proyecto y volúmenes Docker: `r01-adat-validation`.
- API: `http://127.0.0.1:8000`; Swagger: `/docs`.

`scripts/local-environment.py` genera las claves una sola vez. En instalaciones
anteriores busca y verifica la configuración ligada a los contenedores existentes,
conserva sus contraseñas y copia SQLite con su cola mediante un backup consistente.
El archivo original se conserva. Si no encuentra credenciales válidas, se detiene
con instrucciones; nunca borra volúmenes ni cambia las claves de bases existentes.
La primera migración debe hacerse con la API anterior detenida.

Los arranques posteriores y otros clones reutilizan ese mismo archivo. No copies
otra plantilla encima ni generes contraseñas distintas para esos volúmenes.
`api/data/local-environment.json` indica la ruta de la configuración y de SQLite
sin contener contraseñas. Los logs y PID quedan en `api/data/local-*` de cada clon.
El script admite `-NoJava`, `-Port 8001` y `-EnvFile ruta/al/env-anterior` como ayuda
para la primera migración. Cuando ya existe la configuración compartida se reutiliza.
`R01_LOCAL_HOME` permite elegir otro directorio compartido antes de prepararlo.

La plantilla publica MariaDB en **33306**, Oracle en **31521**, H2 en **39092**
(web **38082**), HSQLDB en **39001** y Derby en **31527**. Una instalación migrada
conserva sus puertos. `/health` identifica la instalación para evitar conectar un
clon a una API que esté usando otra SQLite. Los clientes siguen accediendo por HTTP.
En JavaFX, **SQLite** consulta la copia mediante `/alumnos?lectura=sqlite`,
conservando en **Origen** la procedencia real del alumno.

### Datos históricos suministrados

Los archivos originales se versionan en `database/origins/data/`:

| CSV | Base histórica | Registros |
|---|---|---:|
| `gryffindor.csv` | Derby | 303 |
| `hufflepuff.csv` | H2 | 212 |
| `ravenclaw.csv` | Oracle | 203 |
| `slytherin.csv` | HSQLDB | 267 |
| **Total** | | **985** |

El seeder usa los **985 alumnos de `BBDD_con_Imagenes.rar`**, normalizados en
`database/origins/data/alumnos.json`: DNI de ocho cifras y letra, nombre y
apellido separados, sexo, Patronus e imagen. Se conservan los DNI del archivo;
no hace falta inventarlos. Los CSV originales siguen aportando casa, alias,
especie, género, nacimiento, nacionalidad y wiki. Los UUID solo sirven para
relacionar ambos conjuntos y migrar instalaciones anteriores.

Se respetan los Patronus indicados en el RAR y se completan los vacíos con los
143 tipos del ZIP de Patronus, mediante una asignación estable basada en el UUID
original. Esa asignación de los vacíos es de semilla, no una relación canónica.
El catálogo existente `PATRONUS_IMG` recibe también los nombres adicionales del
RAR: 152 entradas en total. Se conservan las URL personalizadas y se refresca su
copia SQLite durante el arranque.

La carga es repetible. Las fichas anteriores con UUID pasan a su DNI sin perder
ediciones particulares. El arranque conserva inactivas sus antiguas copias en
MariaDB y SQLite e importa las fichas con DNI, de modo que no aparecen duplicados.
También retira los cuatro ejemplos antiguos solo si siguen intactos. No se cambia
la estructura de las tablas para actualizar estos datos. Antes de abrir JavaFX,
el arranque espera a que termine la importación y ambas centrales coincidan.

El equipo confirma pruebas manuales en dispositivos. El [registro de validación manual](docs/manual-validation.md) recoge la evidencia disponible, incluidas las pruebas físicas Android en Redmi Note 11 y Redmi Pad SE, y los datos que faltan para identificar las versiones y la cobertura de cada componente.

## Documentación y equipo

La [Wiki de GitHub](https://github.com/dm2-ethazi/Reto01-Omni3/wiki) reúne la instalación, el contrato, los datos, la arquitectura y el recorrido de demostración. Se mantiene en el repositorio independiente `https://github.com/dm2-ethazi/Reto01-Omni3.wiki.git`. El [informe de integración](docs/integration-audit.md) conserva el registro de las comprobaciones realizadas.

La [validación ADAT y propuesta v6 del 09/11/2026](docs/adat-validation.md) recoge las correcciones de la API, las condiciones de fallo contratadas y la demostración real. La [revisión de JavaFX del 21/10/2026](docs/javafx-review.md) conserva el estado histórico anterior; el [contrato técnico de referencia](gestorhogwartsjavafx/docs/00-contrato-tecnico.md) sigue documentando también requisitos de interfaz ajenos a esta corrección.

Grupo **Omni3**: koldo ([@koldoaso](https://github.com/koldoaso)), henri ([@henritejada2006](https://github.com/henritejada2006)) y jairo ([@jairoben](https://github.com/jairoben)), identificados en el README de `javafxapp`.

## Copiar y pegar: montar y ejecutar desde la raíz

**Windows:** instala **Python 3.11+ de 64 bits** con el lanzador `py`, **JDK 21+
de 64 bits** y **Docker Desktop**. Abre Docker Desktop y espera a que esté listo.
Los comandos `py`, `java`, `javaw` y `docker` deben estar en el `PATH`. La compilación
usa Maven dentro de Docker. Hace falta Internet para descargar las dependencias
e imágenes la primera vez.

Abre **PowerShell en la carpeta raíz `Reto01-Omni3`**, donde está `compose.yaml`,
y pega **este bloque entero de una vez**. Si ya tienes JavaFX abierto, ciérralo
antes de repetirlo para permitir recompilar su JAR.

El bloque instala el backend, compila JavaFX para Windows y arranca **las cinco
bases, SQLite central, la API y JavaFX** con los 985 históricos suministrados.
La configuración se crea una sola vez fuera del clon y conserva los datos de la
instalación existente. En la primera migración, detén la API anterior antes de pegarlo.

```powershell
& {
    $ErrorActionPreference = 'Stop'

    # 1. Comprobar carpeta y herramientas antes de preparar nada.
    if (-not (Test-Path './compose.yaml') -or -not (Test-Path './api/pyproject.toml')) {
        throw 'Abre PowerShell en la raiz Reto01-Omni3 y vuelve a pegar el bloque.'
    }
    foreach ($tool in @('py', 'java', 'javaw', 'docker', 'powershell')) {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
            throw "Falta $tool en el PATH. Instala los requisitos y abre otra terminal."
        }
    }
    docker info --format '{{.ServerVersion}}'
    if ($LASTEXITCODE -ne 0) { throw 'Abre Docker Desktop y espera a que este listo.' }
    docker compose version
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose no esta disponible.' }
    java --version
    if ($LASTEXITCODE -ne 0) { throw 'Comprueba la instalacion de JDK 21 o superior.' }
    py -3 -c "import sys, struct; assert sys.version_info >= (3, 11) and struct.calcsize('P') == 8, 'Se requiere Python 3.11+ de 64 bits'"
    if ($LASTEXITCODE -ne 0) { throw 'Comprueba la instalacion de Python 3.11+ de 64 bits.' }

    # 2. Crear el entorno Python si falta e instalar las dependencias.
    if (-not (Test-Path './api/.venv/Scripts/python.exe')) {
        py -3 -m venv api/.venv
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear api/.venv.' }
    }
    & ./api/.venv/Scripts/python.exe -m pip install --upgrade pip
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo actualizar pip.' }
    & ./api/.venv/Scripts/python.exe -m pip install -e ./api --group ./api/pyproject.toml:dev
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron instalar las dependencias Python.' }

    # 3. La configuracion compartida la prepara start-local.ps1 al arrancar.
    New-Item -ItemType Directory -Force './api/data' | Out-Null

    # 4. Compilar JavaFX para Windows usando Maven de Docker.
    $repo = (Get-Location).Path
    docker run --rm --mount "type=bind,source=$repo,target=/workspace" --mount 'type=volume,source=r01-adat-validation_maven_cache,target=/root/.m2' -w /workspace/gestorhogwartsjavafx maven:3.9-eclipse-temurin-21 mvn -B -q clean package '-Djavafx.platform=win' '-DskipTests'
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar JavaFX. Cierra la aplicacion si estaba abierta.' }

    # 5. Preparar las fuentes, arrancar la API, importar alumnos y abrir JavaFX.
    powershell -NoProfile -ExecutionPolicy Bypass -File './scripts/start-local.ps1'
    if ($LASTEXITCODE -ne 0) { throw 'Fallo el arranque. Revisa el mensaje anterior y api/data/local-*.log.' }

    Write-Host 'LISTO: JavaFX conectado a http://127.0.0.1:8000'
    Write-Host 'Swagger: http://127.0.0.1:8000/docs'
    Write-Host 'Estado:  http://127.0.0.1:8000/health'
    Write-Host 'Configuracion y SQLite: consulta api/data/local-environment.json'
}
```

La primera ejecución puede tardar varios minutos, especialmente al descargar e
iniciar Oracle e importar los CSV. Se muestra el progreso de preparación y los
refrescos pendientes. JavaFX se abre al terminar con ambas centrales coherentes.
Las altas hechas desde él se guardan en MariaDB y se refrescan en SQLite; el
selector **SQLite** permite consultar esa copia. El puerto **8000** debe estar
libre o tener esta misma instalación de la API ejecutándose. Si otra versión
ocupa el puerto, el script se detiene con una explicación.

Para los siguientes arranques, con Docker Desktop encendido, basta con pegar esto
desde la misma carpeta:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File ./scripts/start-local.ps1
```

Android y Unity usan la misma API: en el emulador Android configura
`http://10.0.2.2:8000`; en Unity, `http://127.0.0.1:8000`. Su compilación y ejecución
requieren Android Studio y Unity Editor, respectivamente, según sus README.
