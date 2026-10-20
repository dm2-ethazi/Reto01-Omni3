# Reto01 · Omni3 — Gestor Hogwarts

Entrega integrada de una API de alumnado distribuido y tres clientes. Las puntas originales de `Api`, `androidapp`, `javafxapp`, `unityClient`, `development`, `prototipo` y `main` son ancestros de esta rama mediante commits de fusión; sus referencias no se modificaron. El prototipo es una implementación histórica diferente de la API principal.

## Arquitectura y componentes

| Carpeta | Función | Tecnología y estado comprobado |
|---|---|---|
| `api/` | API REST de alumnado y adaptadores a cinco motores. | Python 3.14, FastAPI; CRUD probado con MariaDB real. |
| `database/mariadb/` | Esquema mínimo para el recorrido verificable. | MariaDB 11.8; tablas de inicio comprobadas. |
| `gestorhogwartsjavafx/` | Cliente de escritorio con consulta, altas, edición y baja. | JavaFX 21/Maven; 100 pruebas correctas. |
| `gestorhogwartsmovil/` | Consulta y caché offline de alumnado con URL de API editable. | Kotlin/Android/Room; APK, 17 pruebas unitarias, 9 instrumentadas y lint correctos. |
| `Omni3_Hogwarts_Unity/` | Búsqueda y ficha de alumno/patronus. | Unity 6000.3.25f1; proyecto integrado. |
| `prototipo/` | Solución experimental anterior de sincronización. | API, clientes e infraestructura propios; no comparte esquema con `api/`. |
| `scripts/` | Comprobación de conectividad de la API. | PowerShell. |

Los clientes hablan por HTTP/JSON con `api/`. Android conserva una copia Room. La API principal usa MariaDB para altas y patronus y contiene adaptadores para Oracle, H2, HSQLDB y Derby.

## Arranque rápido comprobable

Requiere Docker Compose, Python 3.14 y una terminal PowerShell. En un clon limpio de `integration`:

```powershell
Copy-Item .env.example .env
# Cambia las contraseñas de ejemplo en .env.
docker compose up -d mariadb
Copy-Item api/.env.local.example api/.env
# Igualar usuario y contraseña de api/.env con .env.
py -3.14 -m venv api/.venv
& api/.venv/Scripts/python.exe -m pip install -e ./api
& api/.venv/Scripts/python.exe -m uvicorn proyecto_reto1.main:app --host 127.0.0.1 --port 8000
```

En otra terminal: `./scripts/check-api.ps1` debe informar la cantidad de alumnos. El esquema `database/mariadb/01_schema.sql` se aplica al crear un volumen nuevo de MariaDB. Si el volumen existía, revisa su contenido y aplica el SQL manualmente; no borres datos para repetir el inicio. En Linux usa `cp`, `python3.14 -m venv`, `api/.venv/bin/python` y `curl` para `/openapi.json` y `/alumnos`.

JavaFX: `mvn -f gestorhogwartsjavafx/pom.xml test`; configura `GESTOR_API_URL` o `config.properties` y ejecuta `mvn -f gestorhogwartsjavafx/pom.xml javafx:run`. Android: abre `gestorhogwartsmovil/` en Android Studio con SDK 37; la URL inicial del emulador es `http://10.0.2.2:8000` y se puede cambiar desde el campo **URL de la API** mientras la app está abierta. Unity: abre `Omni3_Hogwarts_Unity/` en Unity 6000.3.25f1, pulsa Play y cambia la dirección desde el campo **API URL**.

## Documentación y equipo

La [documentación navegable del proyecto](docs/wiki/Home.md) reúne la instalación, el contrato, los datos, la arquitectura y el recorrido de demostración. También está preparada como Wiki en un repositorio Git local independiente.

Grupo **Omni3**: koldo ([@koldoaso](https://github.com/koldoaso)), henri ([@henritejada2006](https://github.com/henritejada2006)) y jairo ([@jairoben](https://github.com/jairoben)), identificados en el README de `javafxapp`.
