# Omni3 Hogwarts — Directorio de Patronus

Cliente desarrollado en **Unity 6** para buscar estudiantes y consultar su casa y su Patronus desde una interfaz ambientada en Hogwarts. Combina un fondo de vídeo, un material creado con Shader Graph, destellos dorados al escribir y una animación de aparición de las imágenes.

## Funcionalidades

- Búsqueda por nombre con una espera de **250 ms** para evitar una petición por cada pulsación.
- Hasta **ocho resultados** visibles, con el nombre y la casa del estudiante.
- Panel de detalles con el nombre completo, la casa, el Patronus y su imagen disponible.
- Mensajes de carga, búsquedas sin coincidencias y errores de descarga.
- Control de respuestas antiguas para que prevalezcan la búsqueda y la selección más recientes.
- Campo de URL de la API editable durante Play; se aplica al pulsar Enter o al salir del campo.

El campo de búsqueda vacío no carga la lista completa. Si aparecen más de ocho coincidencias, escribe un nombre más específico.

## Abrir y ejecutar

1. Instala **Unity Editor 6000.3.25f1** desde Unity Hub.
2. Añade la carpeta `Omni3_Hogwarts_Unity` como proyecto.
3. Espera a que Unity importe los recursos y resuelva los paquetes.
4. Abre [PatronusDirectory.unity](Assets/Scenes/PatronusDirectory.unity).
5. Pulsa **Play**. **API URL** aparece relleno con `https://curator-caliber-magma.ngrok-free.dev`; puedes sustituirlo por otra dirección HTTP o HTTPS accesible.
6. Pulsa Enter o sal del campo para aplicarla; después escribe un nombre y selecciona un resultado.

Al vaciar **API URL**, el hint muestra `http://127.0.0.1:8000` como referencia
para una API local. El hint no se aplica como dirección: dejar el campo vacío
mantiene el servidor activo hasta introducir otra URL válida.

La escena utiliza Unity UI sobre Canvas. Las dependencias se encuentran en [Packages/manifest.json](Packages/manifest.json), incluyendo **Universal Render Pipeline 17.3.0**, **Unity UI 2.0.0** e **Input System 1.20.0**.

## Presentación visual

### Shader y material del Patronus

[BlueShader.shadergraph](Assets/Shaders/BlueShader.shadergraph) es un Shader Graph con destino **Canvas de URP**, utilizado por [PatronusMagicMaterial.mat](Assets/Shaders/PatronusMagicMaterial.mat). El material está asignado al `RawImage` que presenta el Patronus en la escena.

El grafo contiene:

- Una propiedad **Main Texture** con referencia **`_MainTex`**, marcada como textura principal y como dato por renderer, para recibir la textura de la interfaz.
- Un nodo **Sample Texture 2D** que muestrea esa textura y envía su salida al bloque **Base Color**.
- Un bloque **Emission** con un valor cian, aproximadamente `(0, 0.965, 1)`, que aporta el color luminoso del efecto.
- Un bloque **Alpha** con valor constante `1`.

Para modificar su aspecto, abre el grafo desde la ventana Project y ajusta sus bloques y conexiones. La emisión está definida dentro del grafo; no hay una propiedad de color expuesta en el material. La intensidad del brillo visible depende también de la configuración de renderizado de la escena.

### Fondo animado

El objeto **Background** utiliza un `VideoPlayer` para reproducir [patronus_magical_background.mp4](Assets/Textures/patronus_magical_background.mp4) automáticamente y en bucle, sin salida de audio.

El vídeo se escribe en [PatronusBackground.renderTexture](Assets/Textures/PatronusBackground.renderTexture), de **1536 × 720 píxeles**. Un `RawImage` muestra esa misma Render Texture como fondo de la interfaz. El componente `AspectRatioFitter` está configurado para cubrir su contenedor manteniendo la proporción del fondo.

```text
VideoPlayer → PatronusBackground.renderTexture → RawImage del fondo
```

Para cambiar el vídeo, sustituye **Video Clip** en el `VideoPlayer`. Mantén la misma Render Texture en **Target Texture** y en **Texture** del `RawImage`; si cambia la proporción del contenido, revisa también el `AspectRatioFitter`.

### Destellos al escribir

[GoldenTypingLightEffect.cs](Assets/Scripts/GoldenTypingLightEffect.cs) genera pequeñas imágenes de luz dorada junto al cursor del campo de búsqueda. Los recursos reutilizables se encuentran en `Assets/Effects/GoldenTypingLight/`.

Cada destello se desplaza, gira y pierde opacidad hasta desaparecer. El efecto limita las partículas activas a **32** y permite ajustar desde el Inspector la cantidad por cambio de texto, la duración, el tamaño y la velocidad de ascenso. Se actualiza con tiempo independiente de `Time.timeScale`.

### Aparición de la imagen

[PatronusDirectoryView.cs](Assets/Scripts/PatronusDirectoryView.cs) anima la imagen cuando termina su descarga. Su configuración por defecto combina una transición de opacidad y una ampliación desde el **85 %** de su escala hasta el tamaño original durante **0,4 segundos**, con interpolación suave.

Los campos **Animation Duration** e **Initial Scale** permiten ajustar la animación desde el Inspector. Al seleccionar otro estudiante se detiene la animación anterior y se muestra el estado de carga.

## Organización del proyecto

```text
Assets/
├── Effects/GoldenTypingLight/     # Prefabs y textura de los destellos
├── Scenes/
│   └── PatronusDirectory.unity    # Escena principal
├── Scripts/                      # Búsqueda, peticiones, interfaz y animaciones
├── Settings/                     # Configuración de URP y perfiles de compilación
├── Shaders/
│   ├── BlueShader.shadergraph
│   └── PatronusMagicMaterial.mat
└── Textures/
    ├── patronus_magical_background.mp4
    └── PatronusBackground.renderTexture
```

| Script | Responsabilidad |
| --- | --- |
| `Alumno.cs` | Modelo de estudiante y contenedor de la lista para `JsonUtility`. |
| `PatronusApiClient.cs` | Peticiones HTTP, lectura de JSON y descarga de imágenes. |
| `ApiUrlField.cs` | Edición y validación de la dirección de la API durante Play. |
| `PatronusDirectoryController.cs` | Espera de búsqueda, generación de resultados, selección y gestión de la textura descargada. |
| `PatronusDirectoryView.cs` | Textos, estados, imagen del Patronus y animación de aparición. |
| `PatronusResultRow.cs` | Nombre, inicial, casa y botón de cada resultado. |
| `GoldenTypingLightEffect.cs` | Creación, movimiento y eliminación de los destellos de escritura. |

Conserva los archivos `.meta` junto a sus recursos: Unity utiliza sus identificadores para mantener las referencias entre la escena, los materiales, los shaders y las texturas.

## Conexión del cliente

`PatronusApiClient` utiliza `UnityWebRequest` y corrutinas. La escena inicia con `http://127.0.0.1:8000`. Durante Play, el campo **API URL** muestra la dirección activa y permite cambiarla sin editar el Inspector. La dirección nueva se usa en las siguientes peticiones.

El cliente realiza estas peticiones:

| Petición | Uso dentro de Unity |
| --- | --- |
| `GET /alumnos?nombre=...` | Obtener un array JSON de coincidencias por nombre. |
| `GET /alumnos/{dni}` | Cargar los detalles utilizando el `id` del resultado seleccionado. |

La búsqueda elimina los espacios iniciales y finales, codifica el nombre para la URL y admite hasta **100 caracteres**. El panel utiliza `nombre`, `apellido`, `casa`, `patronus` y `patronus_url`. Este último admite direcciones absolutas o rutas relativas a la URL base. El campo `imagen` corresponde al retrato del estudiante y no se utiliza para el Patronus.

Si falta la imagen o falla su descarga, los detalles permanecen visibles con un mensaje en su lugar. Al cambiar de selección, el controlador libera la textura anterior y descarta las respuestas de selecciones antiguas.

Si ejecutas el cliente en otro dispositivo, introduce una dirección accesible desde ese dispositivo: `127.0.0.1` apunta al propio equipo donde se ejecuta la aplicación. Para conexiones HTTP en un entorno de desarrollo, configura **Allow downloads over HTTP** en Player Settings.

## Compilación y comprobación en Unity

Abre **File → Build Profiles** y selecciona la plataforma de destino. `Assets/Scenes/PatronusDirectory.unity` figura habilitada en la configuración guardada.

Comprobaciones recomendadas en modo Play:

1. Confirma que el fondo se reproduce en bucle y cubre la ventana al cambiar su tamaño.
2. Cambia **API URL** durante Play y comprueba que la búsqueda usa la nueva dirección.
3. Escribe en el buscador y comprueba la posición y desaparición de los destellos.
4. Selecciona un estudiante con imagen y revisa el material del Patronus y su animación de aparición.
5. Prueba búsquedas sin coincidencias y estudiantes sin imagen.
6. Cambia rápidamente de búsqueda o selección y comprueba que aparece la información más reciente.

## Solución de problemas

- **Material rosa o shader con errores:** revisa la consola, la importación de `BlueShader.shadergraph` y la configuración de URP. Comprueba que `PatronusMagicMaterial` siga asignado al `RawImage` del Patronus.
- **Fondo negro o estático:** revisa el clip del `VideoPlayer`, sus opciones de reproducción y que comparta la misma Render Texture con el `RawImage` del fondo.
- **Destellos ausentes:** comprueba las referencias **Search Input**, **Golden Light Particle Prefab** y **Effect Layer** del componente.
- **Búsqueda o imagen no disponible:** revisa **Api Base Url**, la conectividad y los avisos de `UnityWebRequest` en la consola.
