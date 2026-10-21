# Conclusión técnica

La rama `integration` reúne los cuatro componentes pedidos —`unityClient`, `androidapp`, `javafxapp` y `Api`— y conecta además `main`, `development` y `prototipo` como ascendencia Git. Los hashes originales permanecen intactos. Los proyectos están aislados en carpetas propias, y el prototipo se identifica como solución histórica distinta.

## Aprendizajes técnicos

El proyecto permite extraer los siguientes aprendizajes de su implementación y de las incidencias documentadas:

- **Separar contrato y almacenamiento:** un modelo JSON común permite desarrollar tres interfaces aunque los datos procedan de motores y dialectos SQL distintos.
- **Distinguir caché y fuente de verdad:** una copia local útil sin red necesita registrar su fecha y procedencia y verificar la escritura; mostrar datos no garantiza que estén actualizados.
- **Entender los límites de las transacciones:** confirmar una escritura MariaDB o una transacción Room no garantiza atomicidad entre varias bases independientes.
- **Preparar una entrega reproducible:** reunir ramas no basta; también hay que alinear esquemas, credenciales, puertos, URLs y versiones de herramientas.
- **Relacionar cada afirmación con una prueba:** compilar, ejecutar pruebas, importar Unity y recorrer una interfaz son comprobaciones diferentes. Un fallo de memoria del emulador necesita diagnóstico y repetición, no permite concluir por sí solo que la aplicación funciona o falla.

Estos aprendizajes se apoyan en el código y el informe de integración; no atribuyen experiencias personales no documentadas a miembros concretos.

## Valoración técnica

Hay una ruta funcional verificada para **MariaDB + FastAPI**: instalación de dependencias, tablas de inicio y CRUD contra una base real. JavaFX pasó 100 pruebas tras corregir la compilación. El README Android documenta 17 pruebas unitarias, empaquetado, lint y 9 pruebas instrumentadas correctas el 21/10/2026, posteriores al intento incompleto de integración que agotó la memoria del AVD. Unity completó la importación batch con el Editor indicado. Esto valida una parte sustancial del trabajo, pero no certifica una ejecución gráfica conjunta de todos los clientes ni el despliegue de cinco bases con datos.

La arquitectura de contrato HTTP compartido simplifica los clientes y la caché SQLite aporta lectura móvil sin red. El coste principal aparece en la persistencia distribuida: esquemas heterogéneos, configuración por entorno y escrituras sin transacción global. El siguiente paso técnico debe ser hacer reproducibles los otros cuatro motores y automatizar una prueba de integración conjunta antes de presentar el sistema como completamente operativo.

La valoración es favorable como integración académica con un recorrido MariaDB reproducible. Para un servicio de producción siguen pendientes autenticación, autorización, observabilidad y garantías operativas. La defensa debe mostrar el recorrido implementado, explicar esas decisiones y reconocer los límites que todavía quedan abiertos.

La documentación está publicada en la [Wiki del proyecto](https://github.com/koldoaso/Reto01-Omni3/wiki), cuyo repositorio Git independiente es `https://github.com/koldoaso/Reto01-Omni3.wiki.git`.
