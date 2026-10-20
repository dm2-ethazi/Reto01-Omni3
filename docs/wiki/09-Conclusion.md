# Conclusión técnica

La rama `integration` reúne los cuatro componentes pedidos —`unityClient`, `androidapp`, `javafxapp` y `Api`— y conecta además `main`, `development` y `prototipo` como ascendencia Git. Los hashes originales permanecen intactos. Los proyectos están aislados en carpetas propias, y el prototipo se identifica como solución histórica distinta.

Hay una ruta funcional verificada para **MariaDB + FastAPI**: instalación de dependencias, tablas de inicio y CRUD contra una base real. JavaFX pasó 100 pruebas tras corregir la compilación; Android pasó 17 pruebas unitarias, empaquetado y lint. Unity completó la importación batch con el Editor indicado. La suite instrumentada Android quedó incompleta por falta de memoria del AVD. Esto valida una parte sustancial del trabajo, pero no certifica una ejecución gráfica de todos los clientes ni el despliegue de cinco bases con datos.

La arquitectura de contrato HTTP compartido simplifica los clientes y la caché SQLite aporta lectura móvil sin red. El coste principal aparece en la persistencia distribuida: esquemas heterogéneos, configuración por entorno y escrituras sin transacción global. El siguiente paso técnico debe ser hacer reproducibles los otros cuatro motores y automatizar una prueba de integración conjunta antes de presentar el sistema como completamente operativo.

El estado de publicación de la Wiki se comprueba en el [informe de entrega del repositorio](https://github.com/koldoaso/Reto01-Omni3/tree/integration) cuando la rama esté publicada.
