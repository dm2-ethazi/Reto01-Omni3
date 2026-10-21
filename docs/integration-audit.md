# Auditoría de integración · 20/10/2026

> Actualización documental del 21/10/2026: la [Wiki ya está publicada](https://github.com/koldoaso/Reto01-Omni3/wiki) en `https://github.com/koldoaso/Reto01-Omni3.wiki.git`, tras hacerse público el repositorio e inicializar su página Home. La limitación de publicación descrita al final recoge el estado histórico del 20/10 y ya no está vigente. El README Android documenta además 9 pruebas instrumentadas correctas el 21/10, posteriores al intento incompleto registrado aquí. No se han repetido las pruebas de componentes al actualizar la documentación.

## Método Git

La rama `integration` se creó en un worktree separado con `git switch --orphan` y un commit raíz vacío (`d1220b2`). Se conectó cada rama fuente mediante un merge real de dos padres, sin squash, rebase ni cherry-pick. Se usó `-s ours --no-commit` para impedir que los README, `.gitignore`, `compose.yaml` y configuraciones raíz de distintas ramas se sobrescribieran entre sí; antes de confirmar cada merge se restauró el directorio propio del componente desde la punta fuente. Para `javafxapp` se usó `--allow-unrelated-histories`. Las carpetas de los proyectos ya existían en los árboles originales, así que no se movieron rutas internas.

`development` se incorporó como historial; `androidapp` contiene su versión móvil más reciente. `prototipo` se mantuvo íntegro bajo `prototipo/`, separado de la API principal porque utiliza el esquema `ESTUDIANTE` y otro backend. En `javafxapp`, los JAR raíz tienen los mismos hashes de blob que `api/jars/`; se conservó la copia de `api/`. El `pyproject.toml`, `uv.lock` y `.python-version` raíz de `javafxapp` correspondían a una variante anterior de la API y siguen disponibles en su historial, mientras que la versión vigente se toma de `Api/api/`. Los README raíz de cada rama siguen igualmente disponibles en Git; el README integrado describe la entrega conjunta.

## Referencias de origen

Todas las referencias tenían el mismo hash antes y después de integrar; `git merge-base --is-ancestor <ref> integration` devolvió 0 para las siete. El repositorio no tiene submódulos ni archivos LFS listados. `git fsck --full --no-reflogs` terminó con código 0 después de materializar el objeto canónico del árbol vacío correspondiente al commit huérfano.

| Referencia remota | Hash antes = después | Ancestro |
|---|---|---|
| `origin/main` | `b9d3878ee4fb11d8a1800ed7b8c2d3b0137250fe` | Sí |
| `origin/development` | `964261530a75a0eddaf701044cd934076a46ae2c` | Sí |
| `origin/prototipo` | `f06e05eaa9227a59f47d0c53170c0b689fb5d414` | Sí |
| `origin/unityClient` | `c5afc4ddc05e6acd6892fecd7ff93cf826308830` | Sí |
| `origin/androidapp` | `6c57095a992740319b0e90e4b71fc6e34d7044e4` | Sí |
| `origin/javafxapp` | `f391a33ac91853023ca78dc472e396061a66c91b` | Sí |
| `origin/Api` | `72d63c54f6126c719fb117a16f8971fa50eefb7d` | Sí |

Los directorios `prototipo/` y `Omni3_Hogwarts_Unity/` se compararon con sus puntas fuente sin diferencias de contenido. Las únicas diferencias en carpetas de componente son las correcciones documentadas de JavaFX, Android y el README de la API. Los 34 archivos `.meta` de Unity permanecen versionados. El Editor modificó dos settings al importar; tras identificar que procedían de la importación se restauraron desde `origin/unityClient`.

## Pruebas realizadas en esta integración

| Componente | Prueba | Resultado |
|---|---|---|
| API | Python 3.14.7: instalación editable, importación y `compileall`. | Correcto. |
| API + MariaDB | MariaDB 11.8 aislada con DDL nuevo; TestClient: OpenAPI, listado, catálogo, POST, GET, PUT, DELETE y 404. | Códigos `200, 200, 201, 200, 200, 204, 404` para CRUD; datos creados y editados reales. |
| JavaFX | Maven 3.9.16 / JDK 21: `mvn test`. | Correcto, 100 pruebas tras corregir el tipo `Rectangle2D`. Interfaz gráfica no abierta. |
| Android | SDK 37: `assembleDebug`, `testDebugUnitTest`, `lintDebug`. | Correcto, 17 pruebas unitarias. Lint con advertencias de versiones/layout, sin error. |
| Android instrumentado | `connectedDebugAndroidTest` en AVD Medium_Phone API 37. | Falló: primer intento no instaló APK por reinicio del servicio package; segundo ejecutó 5 casos correctos de 6, luego el proceso de prueba cayó. La repetición aislada del caso también cayó. Logcat muestra `lowmemorykiller` matando procesos, incluido el de la app; no hay aserción que señale un defecto funcional concreto. |
| Unity | Editor 6000.3.25f1, importación batch `-batchmode -nographics -quit`. | Salida 0; sin errores C# encontrados en el registro. Escena Play no ejecutada manualmente. |

La base MariaDB de `compose.yaml` de este checkout tenía un volumen preexistente con otras credenciales. La prueba de API utilizó otro contenedor y puerto 33306, sin eliminar el volumen previo. El SQL nuevo se confirmó por `SHOW TABLES` y catálogo `Otter`. Para un clon limpio, `compose.yaml` monta el SQL en `/docker-entrypoint-initdb.d/`.

## Limitaciones de la entrega

- No se verificó el sistema con Oracle, H2, HSQLDB y Derby simultáneamente: faltan DDL y semillas `ALUMNOS` para esas fuentes en la rama principal. Los scripts del prototipo no son equivalentes.
- La API no implementa autenticación, autorización, transacción distribuida ni `/health`.
- La interfaz JavaFX, la escena Unity y una sesión Android con usuario aún requieren prueba visual de extremo a extremo. La API sí se probó contra MariaDB real. Las pruebas instrumentadas Android no terminaron por falta de memoria del AVD usado.
- **Publicación de la Wiki, incidencia histórica del 20/10/2026.** La Wiki se preparó en un repositorio Git hermano `Reto01-Omni3.wiki` con commits propios. El primer `git push` a `<repo>.wiki.git` devolvió `Repository not found`. La API autenticada de GitHub confirmó entonces `private=true`, permisos `admin=true` y `has_wiki=false`; una petición para activar `has_wiki=true` devolvió de nuevo `false`. Se conservó temporalmente una copia de lectura en el código. El 21/10/2026, tras hacerse público el repositorio e inicializar Home, se publicó la [Wiki del proyecto](https://github.com/koldoaso/Reto01-Omni3/wiki). La documentación se mantiene ahora únicamente en `https://github.com/koldoaso/Reto01-Omni3.wiki.git`; la copia duplicada se retiró de `integration` y este informe se conserva como registro de la integración.

La plantilla `template-reto-base` se inspeccionó desde el clon local existente: se tomó su estructura de README, documentación y scripts como referencia, sin copiar su código ni sus ejemplos. La URL web directa no fue accesible sin autenticación desde este entorno.
