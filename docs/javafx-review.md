# Revisión del cliente JavaFX · 21/10/2026

Revisión de la entrega integrada en `main`, sobre la base de código
[`d8c26cb`](https://github.com/dm2-ethazi/Reto01-Omni3/commit/d8c26cbf5f430ad76754037d9aa492d28fac2f6a).
Esta revisión distingue comprobaciones automatizadas, inspección de código y pruebas
manuales comunicadas por el equipo. No certifica el cumplimiento del documento firmado con el cliente.

## Pruebas manuales comunicadas por el equipo

El equipo confirma pruebas manuales en dispositivos. El [registro de validación manual](manual-validation.md) recoge la evidencia disponible, incluidas las pruebas físicas Android en Redmi Note 11 y Redmi Pad SE, y los datos que faltan para identificar las versiones y la cobertura de cada componente.
La ausencia de una sesión gráfica en la revisión automatizada no significa que el equipo no haya probado la aplicación manualmente. Para precisar su cobertura puede completarse el registro con dispositivo y sistema, versión de la aplicación, forma de lanzamiento, casos recorridos y resultado.

## Entorno y comprobaciones realizadas

Windows 11 x86-64, Oracle JDK 25.0.2 y Maven 3.9.16 disponible en la instalación del
IDE. JavaFX 21.0.5 y Jackson 2.18.11 se resolvieron mediante el POM del módulo.
Maven y su wrapper no se distribuyen en este módulo.

| Comprobación | Resultado observado |
|---|---|
| `mvn verify`, desde `gestorhogwartsjavafx/` | `BUILD SUCCESS`: 100 pruebas, 0 fallos, 0 errores y 0 omitidas. No se ejecutó `clean` en esta comprobación. |
| Empaquetado | Generado `target/gestorhogwartsjavafx-1.0.0.jar`, con dependencias y `Main-Class: com.omni3.gestorhogwartsjavafx.Launcher`. |
| Contenido del JAR | No contiene `config.properties` ni `ClienteAlumnosFalso`. Incluye recursos de las vistas y traducciones. |
| `mvn javadoc:javadoc site` | Termina con `BUILD SUCCESS`; genera Javadoc y sitio, con la incidencia de informes descrita abajo. |
| Carga de recursos JavaFX | Una comprobación local externa al repositorio cargó los FXML `principal`, `estado`, `alumnos` y `formulario-alumno` desde el JAR, con bundles ES/EU/EN: 12 cargas correctas y cálculo de layout a 640 × 480. No se mostró una ventana ni se conectaron los controladores a una API. |
| Bundles de idiomas | 150 claves en cada uno de los cuatro archivos `messages*.properties`; mismos nombres, sin claves faltantes respecto al bundle base. No constituye una revisión lingüística. |
| Botones del listado | Buscar, Limpiar, Nuevo alumno, Editar y Dar de baja tienen tooltip, pero sus textos no incluyen marcador de mnemónico. |

Las 100 pruebas JUnit no arrancan el toolkit JavaFX. Usan dobles del cliente y,
en las pruebas de fallo HTTP, sockets locales. No sustituyen una prueba CRUD del
cliente gráfico contra FastAPI y una base de datos real.

Los archivos generados quedan bajo `target/`, ignorado por Git. Los resultados del
20/10/2026 para API, bases, Android y Unity se conservan en el
[informe de integración](integration-audit.md); no se han repetido en esta revisión.

## Incidencia en Maven Site

La generación del sitio omite el informe `dependency-info` de
`maven-project-info-reports-plugin:3.9.0` por un
`LinkageError: void org.apache.maven.doxia.sink.Sink.verbatim()`.
También avisa de una versión de plugin de informes no fijada y de anclas XHTML
normalizadas. El sitio generado no equivale a tener todos los informes correctos.
La solución requiere ajustar la configuración del POM y volver a ejecutar el objetivo;
queda pendiente en esta revisión exclusivamente documental.

## Estado frente a la rúbrica

| Criterio | Evidencia y límites |
|---|---|
| Funciona según requisitos | El contrato versionado exige concurrencia, baja lógica, salud, conectores y sincronización que la entrega actual no completa. Véase su [matriz de correspondencia](../gestorhogwartsjavafx/docs/00-contrato-tecnico.md#9-correspondencia-con-la-entrega-actual--21102026). No se han revisado los PDF firmados ni una aprobación posterior de cambios de alcance. |
| Documentación | README de uso, contrato anotado, wiki, diagrama de flujo y Javadoc. Las correcciones documentales no implementan las funciones pendientes. |
| Maven | Compilación, pruebas y JAR correctos; informe de Site parcialmente fallido. No se ha vuelto a ejecutar `javafx:run` ni se han verificado otras plataformas. |
| Control de versiones | 56 commits que afectan al módulo hasta la base revisada, con nomenclatura estándar y cambios frecuentes. El inicial (`03cb9a0`) incorpora 54 archivos y 6.811 líneas. Se conserva ese historial. |
| Entrega ejecutable | JAR generado e inspeccionado; el equipo confirma pruebas manuales en dispositivos, sin especificar la forma de lanzamiento usada. Dockerfile presente, sin `docker build` ni `docker run` en esta revisión. |
| Logger | `Registro` escribe INFO, AVISO y ERROR en `gestor-hogwarts.log` y consola. No registra sistemáticamente CRUD correcto ni todos los errores HTTP; no permite reconstruir toda la actividad. |
| Organización y modelos | Controladores, vistas, API, servicios, viewmodels y modelos separados; `Alumno` es inmutable y valida sus datos. Los metadatos del modelo no acreditan capacidades del backend. |
| Responsividad y accesibilidad | Hay contenedores flexibles, mínimos de ventana, ayudas y atajos; faltan mnemónicos en los cinco botones revisados. El equipo confirma pruebas manuales; no se ha detallado su cobertura de redimensionado, DPI, contraste y teclado. |
| Multiidioma | Bundles ES/EU/EN, selección inicial por locale admitido y carga correcta de vistas. La conservación de formularios sin guardar al cambiar de idioma no está acreditada. |
| Credenciales y errores | No se detectaron credenciales reales en el módulo inspeccionado y la configuración privada no está en el JAR. No se ha auditado todo el historial; URL de conexión, detalles externos y trazas carecen de saneamiento general. |

## Guion para documentar o repetir las pruebas manuales

El equipo ya ha realizado pruebas manuales. Este guion sirve para registrar su cobertura o repetir los casos necesarios; no indica que todos estén sin probar. Docker sigue siendo una comprobación independiente sin resultado aportado.

1. Configurar una API de prueba y una base con datos prescindibles según la
   [guía de ejecución](https://github.com/dm2-ethazi/Reto01-Omni3/wiki/06-Ejecucion-del-Proyecto).
2. Ejecutar el JAR desde la carpeta del módulo y recorrer alta, búsqueda, ficha,
   edición y baja, comprobando la persistencia. Para JavaFX se necesita un DNI con
   ocho cifras y una letra; la API admite identificadores más generales.
3. Probar tamaños mínimos, maximizado, escalado del sistema, teclado y los tres
   idiomas, incluidos los formularios y los mensajes de error.
4. Comprobar el arranque sin API, la configuración inválida y los permisos de escritura
   del log. Verificar que los mensajes no exponen datos sensibles del entorno usado.
5. Construir y ejecutar Docker por separado. Xvfb no proporciona por sí mismo una
   ventana visible en el anfitrión.

Los ajustes de código, POM, FXML, traducciones de la aplicación y Docker quedan fuera
de esta corrección documental. La puntuación final depende de la evaluación y de los
requisitos efectivamente aprobados por el cliente.

## Correcciones documentales locales

- Instalación de Maven mediante una instalación externa o el IDE; retirada de las
  rutas personales y de la supuesta distribución de Maven en `tools/`.
- Instrucciones de uso desde la carpeta del módulo para que se encuentre su
  `config.properties`, y requisitos de plataforma del JAR y de la pantalla virtual Docker.
- Recuento actualizado de traducciones, créditos y enlaces a la entrega integrada
  en `main`; la wiki conserva enlaces al repositorio de la organización.
- Matriz de requisitos e implementación en el contrato de referencia, manteniendo
  los requisitos originales y señalando las diferencias pendientes.
- Descripción precisa del registro de eventos, los mnemónicos disponibles, el
  tratamiento de errores y la incidencia de Maven Site.
- [Registro de pruebas manuales](manual-validation.md) con las evidencias ya
  comunicadas por el equipo y documentadas en el README Android.
- Guion de la wiki con identificador admitido por JavaFX y baja al final del
  recorrido, para que el mismo alumno pueda consultarse desde los tres clientes.

Esta actualización solo modifica archivos Markdown. No se han cambiado fuentes,
recursos de la aplicación, dependencias, configuración de compilación ni historial Git.
