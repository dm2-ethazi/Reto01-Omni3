# Directorio de Patronus Esta escena de Unity 6 busca estudiantes en el servicio **API Alumnos** y muestra el Patronus y la casa del estudiante seleccionado. 
## Cómo ejecutar 
1. Inicia la API. 
2. En Unity, abre `Assets/Scenes/PatronusDirectory.unity` y presiona Play. 
3. Escribe el nombre de un estudiante y luego selecciona un resultado para ver sus detalles. 

El objeto **Directorio de Patronus** de la escena tiene un campo `apiBaseUrl`. 

Por defecto es `http://127.0.0.1:8000`. Cámbialo si la API está alojada en otro lugar. 
Una compilación que se ejecute en otro dispositivo necesita una dirección accesible desde ese dispositivo. 

## Campos de la API usados por esta pantalla

- `GET /alumnos?nombre=...` busca por nombre. La búsqueda se envía después de una breve pausa al escribir. La entrada en blanco no solicita toda la base de datos.
- `GET /alumnos/{dni}` carga al estudiante seleccionado usando su `id` como valor en la ruta.
- El panel de detalle muestra `nombre`, `apellido`, `casa` y `patronus`.
- Cuando `patronus_url` está presente, su imagen aparece en el panel de detalle. Cuando está ausente, el nombre del Patronus sigue visible con un marcador de imagen. El campo separado `imagen` es un retrato del estudiante y no se usa como imagen del Patronus. El panel actual muestra hasta ocho coincidencias a la vez. Si una búsqueda encuentra más, escribe un nombre más específico. Las operaciones de crear, actualizar, eliminar y listar Patronus de la API están fuera de esta pantalla de búsqueda.

## Estructura del código
- `PatronusApiClient.cs` hace las solicitudes HTTP y lee el JSON de la API.
- `Alumno.cs` describe a un estudiante y envuelve la lista principal para el parser JSON de Unity.
- `PatronusDirectoryController.cs` coordina los resultados de búsqueda y el estudiante seleccionado. 
- `PatronusDirectoryView.cs` actualiza las etiquetas de detalle y estado. 
- `PatronusResultRow.cs` llena una fila de resultados. 
- `GoldenTypingLightEffect.cs` controla el efecto de escritura dorada existente.