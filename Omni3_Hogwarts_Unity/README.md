# Reto01 — Omni3 Hogwarts

Aplicación de Unity ambientada en Hogwarts que permite buscar estudiantes y consultar su casa y su Patronus. El cliente obtiene los datos de los estudiantes y las imágenes de sus Patronus desde una **API Alumnos** externa. Este repositorio también incluye un entorno de Docker Compose para las bases de datos del proyecto.

## Funcionalidades

- Búsqueda de estudiantes por nombre, con una espera de 250 ms después de escribir.
- Visualización de hasta ocho coincidencias, con el nombre y la casa de cada estudiante.
- Selección de un resultado para cargar el nombre completo, la casa, el Patronus y su imagen, si está disponible.
- Indicadores de carga, búsquedas sin resultados, imágenes no disponibles y errores de conexión.
- Animación de aparición y ampliación de las imágenes del Patronus y destellos dorados al escribir.

Las búsquedas vacías no solicitan la lista de estudiantes. Si hay más de ocho coincidencias, escribe un nombre más específico para reducir los resultados.

## Requisitos

- **Unity Hub** y **Unity Editor 6000.3.25f1**, la versión registrada en el proyecto.
- Una API Alumnos en ejecución que implemente los endpoints descritos más abajo.
- **Docker con Docker Compose** para ejecutar el entorno de bases de datos incluido.

Las dependencias de Unity se declaran en [`Packages/manifest.json`](Omni3_Hogwarts_Unity/Packages/manifest.json), e incluyen Universal Render Pipeline, Unity UI y el Input System.

## Ejecutar la aplicación de Unity

1. En Unity Hub, añade la carpeta **`Omni3_Hogwarts_Unity`** como proyecto y ábrela con la versión correspondiente del editor.
2. Espera a que Unity importe los recursos y resuelva los paquetes.
3. Abre `Assets/Scenes/PatronusDirectory.unity`.
4. Localiza el objeto de la escena que contiene el componente **PatronusApiClient** y configura **Api Base Url** en el Inspector con la dirección de tu API en ejecución.
5. Activa el modo Play, escribe el nombre de un estudiante y pulsa un resultado para consultar sus detalles.

El script utiliza `http://127.0.0.1:8000` por defecto, pero la escena actualmente sobrescribe ese valor con `https://curator-caliber-magma.ngrok-free.dev`. Sustituye el valor de la escena si el túnel no está disponible o utilizas otro servidor. Si ejecutas la aplicación en otro dispositivo, usa una dirección de la API accesible desde él; `127.0.0.1` hace referencia al propio dispositivo.

El servidor de la API y sus instrucciones de arranque no están incluidos en este repositorio. Iniciar los contenedores de bases de datos no inicia la API.

## Contrato de la API

| Petición | Respuesta esperada | Finalidad |
| --- | --- | --- |
| `GET /alumnos?nombre=<nombre>` | Un array JSON de objetos de estudiantes | Buscar por nombre |
| `GET /alumnos/{dni}` | Un objeto de estudiante | Cargar los detalles del estudiante seleccionado |

El cliente codifica el término de búsqueda para incluirlo en la URL y utiliza el **`id`** del estudiante seleccionado como valor de `{dni}` en la ruta. Se eliminan los espacios al principio y al final del término de búsqueda, que está limitado a 100 caracteres.

Ejemplo de un objeto de estudiante con datos ficticios:

```json
{
  "id": "student-001",
  "nombre": "Ejemplo",
  "apellido": "Estudiante",
  "sexo": "",
  "casa": "Gryffindor",
  "patronus": "Ciervo",
  "patronus_url": "/images/patronus/stag.png",
  "imagen": "/images/students/student-001.png"
}
```

`patronus_url` puede ser una URL absoluta o una ruta relativa a la URL base de la API. Si falta o la imagen no se puede descargar, la aplicación mantiene visibles los detalles del estudiante y muestra un mensaje en lugar de la imagen. El campo independiente `imagen` representa el retrato del estudiante y no se utiliza como imagen del Patronus.

La interfaz permite buscar y consultar detalles de estudiantes. La creación, modificación y eliminación de estudiantes, así como el listado independiente de Patronus, quedan fuera de su alcance actual.


## Estructura del repositorio

```text
└── Omni3_Hogwarts_Unity/
    ├── Assets/
    │   ├── Scenes/
    │   │   └── PatronusDirectory.unity
    │   └── Scripts/
    │       ├── Alumno.cs
    │       ├── PatronusApiClient.cs
    │       ├── PatronusDirectoryController.cs
    │       ├── PatronusDirectoryView.cs
    │       ├── PatronusResultRow.cs
    │       └── GoldenTypingLightEffect.cs
    ├── Packages/
    └── ProjectSettings/
```

El cliente de la API gestiona las peticiones HTTP y la lectura del JSON. El controlador coordina las búsquedas con espera y la selección; la vista actualiza el panel de detalles, y cada fila de resultados vincula un estudiante con su botón de selección. `Alumno` define el modelo de respuesta y `GoldenTypingLightEffect` gestiona los destellos del campo de búsqueda.

## Compilación y comprobación

Abre **File → Build Profiles** en Unity, elige la plataforma de destino y comprueba que `Assets/Scenes/PatronusDirectory.unity` esté incluida y activada. Ya está activada en la configuración de compilación del repositorio. Configura una URL de la API accesible antes de compilar.

Para realizar una comprobación manual básica con la API en ejecución:

1. Busca un estudiante conocido y selecciona un resultado; comprueba que aparecen el nombre, la casa y el Patronus.
2. Prueba con un estudiante que tenga una imagen válida del Patronus y con otro que no tenga imagen.
3. Busca un nombre sin coincidencias y después vacía el campo de búsqueda.
4. Escribe rápidamente o cambia de selección mientras se cargan las peticiones; comprueba que se muestran los resultados y los detalles de la selección más reciente.

## Solución de problemas

- **Falla la búsqueda de estudiantes:** verifica la URL base de la API en la escena, comprueba que el servidor esté en ejecución y revisa las advertencias de las peticiones en la consola de Unity.
- **Formato de respuesta inesperado:** la búsqueda debe devolver un array JSON en el nivel principal; el endpoint de detalles debe devolver un objeto con un `id` no vacío.
- **La imagen del Patronus no está disponible:** comprueba `patronus_url` y que la dirección resultante permita descargar una imagen.
- **Las peticiones HTTP están bloqueadas:** el proyecto actualmente no permite HTTP sin cifrar. Utiliza preferentemente una API con HTTPS o configura **Allow downloads over HTTP** en Player Settings para la plataforma de desarrollo que utilices.
