# Demo funcional reproducible

La entrega ofrece una explicación reproducible del funcionamiento como alternativa al vídeo. Este recorrido usa la instalación MariaDB de [Ejecución](06-Ejecucion-del-Proyecto.md), `DB_ENGINES=mariadb` y el catálogo `Otter` que crea el SQL de inicialización. Usa una base de prueba y comprueba que `DEMO0001` no corresponde a datos que quieras conservar.

## Casos de uso y criterios de evaluación

| Caso | Acción | Resultado esperado |
|---|---|---|
| Alta | Crear `DEMO0001` por API o JavaFX. | 201; registro con origen `Hogwarts`. |
| Consulta | Listar, filtrar por `Ada` y abrir la ficha. | 200; mismos identificador y nombre. |
| Edición | Cambiar el nombre a `Ada Editada`. | 200; GET posterior y consulta SQL muestran el cambio. |
| Consulta móvil sin red | Cargar Android con red, cerrar, desconectar y abrir. | La última copia sigue disponible y su estado indica que no se pudo verificar. |
| Consulta Unity | Buscar `Ada` y seleccionar el resultado. | Se abre la ficha; la imagen puede estar vacía con el catálogo de ejemplo. |
| Validación | Repetir el alta del mismo DNI; enviar `sexo=X`. | 409 para duplicado y 422 para un valor no válido. |
| Baja | Borrar el alumno al finalizar las comprobaciones. | 204; ficha posterior 404; no queda fila SQL. |

## API y persistencia

1. Arranca MariaDB y la API según la guía. Confirma `GET /openapi.json` (200) y `GET /alumnos` (200, posiblemente `[]`).
2. Ejecuta el POST de [Backend](04-Backend.md) con `id=DEMO0001`, `nombre=Ada`, `patronus=Otter`. Espera 201 y `casa=Hogwarts`.
3. Consulta `GET /alumnos/DEMO0001` y `GET /alumnos?nombre=Ada`; el alumno aparece en ambas. `GET /patronus` contiene `Otter`.
4. Edita el nombre con PUT; consulta de nuevo y comprueba `Ada Editada`.
5. Comprueba persistencia directamente en MariaDB, desde la raíz del clon:

   ```bash
   docker compose exec mariadb mariadb -u TU_USUARIO -p TU_BASE -e "SELECT ID,NOMBRE,PATRONUS FROM ALUMNOS WHERE ID='DEMO0001';"
   ```

   Introduce la contraseña cuando el cliente la pida. Espera una fila con `DEMO0001` y `Ada Editada`.
6. Mantén el alumno hasta completar el recorrido de clientes. La baja se realiza al final de esta página para que Android y Unity puedan consultarlo.

Las peticiones se pueden ejecutar desde [Swagger local](http://127.0.0.1:8000/docs), usando los cuerpos de [Backend](04-Backend.md). En PowerShell también puedes crear y editar el alumno sin depender del tratamiento de comillas de `curl`:

```powershell
$apiUrl = 'http://127.0.0.1:8000'
$alta = @{ id = 'DEMO0001'; nombre = 'Ada'; apellido = 'Demo'; sexo = 'M'; patronus = 'Otter' } | ConvertTo-Json
Invoke-RestMethod "$apiUrl/alumnos" -Method Post -ContentType 'application/json' -Body $alta
Invoke-RestMethod "$apiUrl/alumnos?nombre=Ada"
Invoke-RestMethod "$apiUrl/alumnos/DEMO0001"
$cambio = @{ nombre = 'Ada Editada' } | ConvertTo-Json
Invoke-RestMethod "$apiUrl/alumnos/DEMO0001" -Method Put -ContentType 'application/json' -Body $cambio
```

Ejecuta el alta una sola vez. Si el DNI ya existe, recibirás 409; selecciona un identificador de prueba libre de hasta diez caracteres y úsalo de forma coherente en todo el recorrido.

Durante la integración se comprobaron con TestClient y MariaDB real los códigos **200, 200, 201, 200, 200, 204, 404** para OpenAPI, listado, creación, ficha, actualización, borrado y ficha ausente. Esta prueba usó un contenedor aislado porque un volumen local anterior tenía otras credenciales. No certifica los cuatro motores restantes.

## Comprobación desde clientes

- **JavaFX:** con `api.url` local y API activa, abre Estado y Alumnos. Repite alta o búsqueda de `DEMO0001` antes de borrarlo; la ficha debe mostrar origen Hogwarts. Edición y baja usan PUT/DELETE. En esta integración solo se ejecutaron sus 100 pruebas automatizadas; el recorrido de interfaz queda para una sesión gráfica.
- **Android:** selecciona la URL de la API desde la pantalla principal, pulsa **Aplicar** y busca el alumno con red. Cierra, desconecta y vuelve a abrir: debe mostrar la copia SQLite con indicación de verificación pendiente. Tras modificar el registro en la API y reconectar, busca con el campo vacío para recargar y comprobar el nuevo nombre. El [README Android](https://github.com/koldoaso/Reto01-Omni3/blob/integration/gestorhogwartsmovil/README.md) documenta 17 pruebas unitarias y 9 instrumentadas correctas el 21/10/2026, revisión en ambas orientaciones y validaciones físicas del equipo. Es evidencia posterior al intento de integración del 20/10 que falló por memoria del AVD; no se han vuelto a ejecutar esas pruebas al editar esta Wiki.
- **Unity:** en Play, establece **API URL**, busca `Ada` y abre la ficha. La API devuelve datos del alumno; la foto del patronus no se verá con el catálogo de ejemplo porque su URL está vacía. La importación batch está comprobada en el informe de integración; el recorrido manual de esta escena no está acreditado por esa prueba.

Para un recorrido entre clientes, crea el alumno con API o JavaFX, consulta desde Android y Unity, edítalo desde JavaFX y actualiza ambos clientes. La consistencia visible depende de que todos apunten a la misma URL; Android puede mostrar temporalmente su caché hasta completar la verificación.

## Baja y comprobación final

Desde Swagger ejecuta `DELETE /alumnos/DEMO0001`: debe devolver 204 sin cuerpo. En PowerShell:

```powershell
Invoke-WebRequest 'http://127.0.0.1:8000/alumnos/DEMO0001' -Method Delete
```

Consulta otra vez la ficha: espera 404. Repite la consulta SQL anterior: espera cero filas. Recarga Android con red para comprobar que su colección se actualiza; una copia todavía sin red puede conservar el alumno hasta sincronizar.

## Guion para la exposición oral

| Tiempo orientativo | Qué mostrar y explicar |
|---|---|
| 1 minuto | Problema, objetivo y papel de cada cliente. |
| 1 minuto | Diagrama de arquitectura y separación HTTP/persistencia. |
| 1 minuto | Modelo: alumno, catálogo, datos calculados y caché. |
| 3 minutos | Alta, consulta, edición, lectura en clientes, consulta SQL y baja. Mostrar una validación y la copia Android sin red. |
| 1 minuto | Registros de errores, ausencia de métricas persistidas y límites de consistencia. |
| 1 minuto | Aprendizajes, evidencias de pruebas y mejoras pendientes. |

Antes de la exposición, prueba las tres interfaces contra la misma API y conserva el estado de la red para demostrar Android offline. Distingue lo que se ve en directo de las pruebas históricas y de las funciones pendientes de cinco motores.
