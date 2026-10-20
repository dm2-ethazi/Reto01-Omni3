# Demo funcional reproducible

No consta un vídeo demostrativo verificable. Este recorrido usa la instalación MariaDB de [Ejecución](06-Ejecucion-del-Proyecto.md), `DB_ENGINES=mariadb` y el catálogo `Otter` que crea el SQL de inicialización. No toca registros existentes si se usa el DNI de ejemplo solo en una base de prueba.

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
6. Ejecuta DELETE. Espera 204; un GET posterior debe devolver 404; la consulta SQL ya no devuelve la fila.

Durante la integración se comprobaron con TestClient y MariaDB real los códigos **200, 200, 201, 200, 200, 204, 404** para OpenAPI, listado, creación, ficha, actualización, borrado y ficha ausente. Esta prueba usó un contenedor aislado porque un volumen local anterior tenía otras credenciales. No certifica los cuatro motores restantes.

## Comprobación desde clientes

- **JavaFX:** con `api.url` local y API activa, abre Estado y Alumnos. Repite alta o búsqueda de `DEMO0001` antes de borrarlo; la ficha debe mostrar origen Hogwarts. Edición y baja usan PUT/DELETE. En esta integración solo se ejecutaron sus 100 pruebas automatizadas; el recorrido de interfaz queda para una sesión gráfica.
- **Android:** compila con la URL del emulador, abre con red y busca el alumno. La primera carga llena Room; al reiniciar sin red debe mostrar la copia SQLite con indicación de verificación pendiente. Tras modificar el registro en la API y reconectar, una recarga debe actualizarla. En esta integración pasaron 17 pruebas unitarias; la suite instrumentada quedó incompleta porque el AVD API 37 agotó memoria y cerró el proceso de prueba tras cinco casos correctos.
- **Unity:** en Play, busca `Ada` y abre la ficha. La API devuelve datos del alumno; la foto del patronus no se verá con el catálogo de ejemplo porque su URL está vacía. La escena no se ejecutó manualmente durante esta integración.

Para un recorrido entre clientes, crea el alumno con API o JavaFX, consulta desde Android y Unity, edítalo desde JavaFX y actualiza ambos clientes. La consistencia visible depende de que todos apunten a la misma URL; Android puede mostrar temporalmente su caché hasta completar la verificación.
