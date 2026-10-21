# Persistencia de datos

## API y bases de datos

La configuración está en `api/.env`, leída por `pydantic-settings` en `config.py`. `DB_ENGINES` admite `oracle,mariadb,h2,hsqldb,derby`. MariaDB usa PyMySQL; Oracle, `oracledb`; H2, HSQLDB y Derby, `jaydebeapi`/JPype con los JAR de `api/jars/`. Cada repositorio implementa listado, búsqueda, lectura, edición y borrado; **solo MariaDB** implementa creación y catálogo de patronus.

El DDL `database/mariadb/01_schema.sql` crea `ALUMNOS` y `PATRONUS_IMG` para una instalación nueva de MariaDB con Docker Compose. La entrada `Otter` permite validar POST sin suponer que exista una imagen. Un volumen ya existente no vuelve a ejecutar `docker-entrypoint-initdb.d`: aplica el SQL de forma explícita tras revisar los datos existentes. No se borra el volumen para forzar la inicialización.

En la rama de la API no existen migraciones ni DDL para Oracle, H2, HSQLDB y Derby. El `compose.yaml` raíz levanta los motores, pero arrancar servidores **no** crea la tabla `ALUMNOS`. El prototipo trae SQL de `ESTUDIANTE`, incompatible con la consulta `SELECT ID, NOMBRE, APELLIDO, SEXO, PATRONUS, IMAGEN FROM ALUMNOS` de esta API; no debe aplicarse como si fuera su migración.

## Cliente Android

Room/SQLite guarda una instantánea completa de `GET /alumnos` en `students` y el tiempo de sincronización en `sync_state`. La escritura se hace en transacción y se vuelve a leer para comprobar igualdad. Al iniciar, una instantánea anterior puede mostrarse sin red; su estado indica que no se pudo verificar. Android no es la fuente de verdad y no sincroniza modificaciones locales hacia el servidor. El esquema versionado (`app/schemas/.../1.json`) permite comprobar cambios futuros.

La instantánea conserva cantidad, campos y orden de la colección completa. Al sustituirla se eliminan las filas que ya no devuelve el servidor; por eso esta operación no debe recibir una respuesta filtrada o paginada. Si la escritura o su comprobación falla, no se anuncia una nueva sincronización correcta. Las preferencias asocian la copia con la URL del servidor para evitar presentar datos de otra API como si pertenecieran a la seleccionada.

## Justificación del diseño

El modelo relacional permite identificar alumnos mediante una clave primaria y reutilizar un catálogo de patronus con nombre único. Los adaptadores convierten las filas de cada motor al mismo modelo HTTP. MariaDB concentra las altas y el catálogo para que esas operaciones tengan una fuente definida; no implica que exista una transacción distribuida con el resto de motores.

Las escrituras MariaDB usan `commit` explícito y las consultas emplean parámetros SQL. El volumen Docker conserva las tablas al detener o recrear el contenedor. En móvil, SQLite evita instalar un servidor local y las transacciones Room permiten guardar colección y fecha conjuntamente. Separar fuente remota y caché permite mostrar datos sin red, a cambio de distinguir su antigüedad y estado de verificación. El diseño actual no incluye copias de seguridad automáticas ni una política de retención de auditoría.

## Integridad y operaciones

`ALUMNOS.ID` y `PATRONUS_IMG.NOM_ENG` son únicos en el DDL MariaDB. No existe FK física entre `PATRONUS` y el catálogo; el POST valida el nombre, pero el PUT no lo valida. La actualización y eliminación en varios motores se realiza secuencialmente, sin confirmación distribuida ni compensación automática. Es un riesgo conocido para datos duplicados entre fuentes.

Para usar solo el recorrido verificado, copia `api/.env.local.example` a `api/.env` y mantén `DB_ENGINES=mariadb`; iguala usuario, contraseña, base y puerto con el `.env` de Compose. No se versionan credenciales reales. Continuar: [modelo](03-Modelo-de-Datos.md) · [ejecución](06-Ejecucion-del-Proyecto.md).
