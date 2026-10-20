# Gestor Hogwarts Móvil

Aplicación Android en Kotlin para consultar alumnos, buscar por nombre y abrir
su ficha. Admite inglés, castellano y euskera, teléfono y tablet, en vertical
y horizontal.

## Guía del código

El código está en `app/src/main/java/com/omni3/gestorhogwartsmovil/`.
Las clases y los métodos propios incluyen KDoc en castellano (`/** ... */`),
visible en la documentación rápida de Android Studio.

| Archivo | Responsabilidad |
| --- | --- |
| `Student.kt` | Campos REST, nombre completo y coincidencia de búsqueda. |
| `ApiClient.kt` | URL del servidor, GET /alumnos, JSON y errores HTTP. |
| `StudentDatabase.kt` | Entidades, mappers, contrato de caché, DAO y base Room. |
| `StudentResult.kt` | Datos, origen, fecha de sincronización y estados de error. |
| `StudentRepository.kt` | Consulta de la API, validación, persistencia y fallback. |
| `MainActivity.kt` | Lista, búsqueda, estado e idioma. |
| `StudentAdapter.kt` | Filas con View Binding y ListAdapter. |
| `StudentViewActivity.kt` | Contrato de navegación, ficha y descarga opcional de su foto. |

Se mantiene una estructura pequeña, con dependencias en
`gradle/libs.versions.toml`. El wrapper y los archivos generados por las
herramientas no se refactorizan como código de aplicación.

## API y persistencia

`GET /alumnos` devuelve la colección completa con `id`, `nombre`, `apellido`,
`sexo`, `patronus`, `casa` e `imagen`. La URL se configura en `ApiClient`.
No se cambia el contrato ni se presuponen endpoints de búsqueda o de detalle.

1. La Activity solicita la lista al repositorio.
2. El repositorio consulta la API en IO y valida campos obligatorios e IDs únicos.
3. Room realiza upsert, elimina los IDs ausentes con una consulta conjunta y
   guarda la fecha. Toda la actualización se ejecuta en una transacción.
4. Ante errores de red, timeout o HTTP 5xx, devuelve la última instantánea de
   `hogwarts.db`. HTTP 4xx y respuestas inválidas se presentan como errores.
5. Si falla el guardado, registra el fallo y devuelve los datos remotos. Si no
   puede leerse la caché, devuelve un error controlado.

Una lista vacía es válida y se persiste. La marca de sincronización la distingue
de una caché nunca inicializada. Un Mutex compartido serializa consulta y
escritura para evitar respuestas guardadas fuera de orden. Las cancelaciones se
propagan y no se convierten en estados de error u offline.

**`replaceComplete` solo admite colecciones completas y autoritativas.**
Las búsquedas filtran la lista cargada sin escribir. Los futuros endpoints
filtrados, individuales o paginados no deben llamar a ese método. Abrir una
ficha tampoco realiza otra consulta de alumnos.

Buscar con texto filtra; buscar con el campo vacío recarga la API. Cada nueva
carga vuelve a priorizar el servidor. La indicación offline permanece durante
las búsquedas. Sin conexión ni caché se muestra un mensaje informativo.

El esquema sigue en versión 1, exportado en `app/schemas`. Los cambios futuros
requieren migraciones; no hay recreación destructiva. No se guardan secretos
ni se sincronizan escrituras offline.

## Pantallas y recursos

- Ambas Activities usan `lifecycleScope`, red en IO y presentación en el hilo
  principal. No mantienen ejecutores manuales ni callbacks tras su destrucción.
- `StudentViewActivity.crearIntent` concentra las claves de navegación. La ficha
  reutiliza `Student.nombreCompleto` y traduce los campos ausentes.
- La foto conserva las cabeceras del proveedor y timeouts de cinco segundos.
  No se persisten imágenes: sin URL o si falla la descarga se mantiene el icono.
- Los layouts de teléfono y tablet se separan cuando cambia la estructura.
  `dimens.xml` y sus variantes ajustan tamaños sin copiar layouts enteros.
- `R01ImagenAlumno` centraliza la foto de la ficha. Las columnas de tablet apilan
  etiqueta y valor verticalmente. Día y noche comparten la paleta existente,
  ya que ambos archivos tenían exactamente los mismos colores.

## Comprobación manual

1. Con API disponible, comprueba lista, búsqueda y ficha.
2. Cierra la app, activa modo avión y vuelve a abrirla: deben aparecer los datos
   guardados y el aviso offline. Prueba búsquedas y fichas.
3. Busca «Harry», reinicia sin red y busca otro nombre: la caché debe conservarlo.
4. Recupera la conexión, modifica datos en el servidor y pulsa Buscar con el
   campo vacío. Reinicia offline para confirmar la persistencia.
5. En una instalación limpia sin conexión, comprueba el mensaje sin datos.
   Una respuesta `[]` debe mostrar cero resultados también offline.
6. HTTP 401/403 deben mostrar error; 503 debe recuperar la caché.
7. Comprueba idiomas y ambas orientaciones en teléfono y tablet.

## Pruebas

Desde la carpeta del proyecto:

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew.bat :app:connectedDebugAndroidTest
```

La segunda orden requiere dispositivo o emulador. Las pruebas no necesitan API:

- `StudentTest`: nombres, búsquedas y conservación de campos en los mappers.
- `StudentRepositoryTest`: sincronización, reconexión, filtros, listas vacías,
  HTTP, validación, fallos locales, cancelación y concurrencia.
- `StudentDatabaseTest`: reapertura de SQLite, upsert, borrado conjunto, orden
  de filas y rollback de datos y fecha.
- `StudentViewActivityTest`: navegación, recreación, campos ausentes y layouts
  de teléfono/tablet en vertical y horizontal.
- `StudentAdapterTest`: actualización y selección de una fila reciclada.

Validación de la revisión (16/10/2026): compilación debug correcta, 12 pruebas
unitarias y 7 instrumentadas aprobadas en Small_Phone (API 37). La prueba de
recursos carga las cuatro combinaciones teléfono/tablet y vertical/horizontal.
Lint final: cero errores y 9 advertencias (versiones de dependencias y detalles
de layouts). El esquema Room mantiene su versión y hash originales.
