package com.omni3.gestorhogwartsmovil.data

import com.omni3.gestorhogwartsmovil.Student

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert

/**
 * Fila persistida con el ID remoto; [position] conserva el orden de la colección recibida.
 *
 * @author koldo
 * @description Fila persistida con el ID remoto; [position] conserva el orden de la colección recibida.
 */
@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey val id: String,
    val nombre: String,
    val apellido: String?,
    val sexo: String?,
    val patronus: String?,
    val casa: String?,
    val imagen: String?,
    val position: Int
)

/**
 * Copia los campos remotos y añade su posición en la lista completa.
 *
 * @author koldo
 * @description Copia los campos remotos y añade su posición en la lista completa.
 */
fun Student.toEntity(position: Int) = StudentEntity(
    id = id,
    nombre = nombre,
    apellido = apellido,
    sexo = sexo,
    patronus = patronus,
    casa = casa,
    imagen = imagen,
    position = position
)

/**
 * Recupera el modelo de presentación sin exponer los metadatos de SQLite.
 *
 * @author koldo
 * @description Recupera el modelo de presentación sin exponer los metadatos de SQLite.
 */
fun StudentEntity.toStudent() = Student(
    id = id,
    nombre = nombre,
    apellido = apellido,
    sexo = sexo,
    patronus = patronus,
    casa = casa,
    imagen = imagen
)

/**
 * Marca una sincronización completada, incluso cuando la API devuelve cero alumnos.
 *
 * @author koldo
 * @description Marca una sincronización completada, incluso cuando la API devuelve cero alumnos.
 */
@Entity(tableName = "sync_state")
data class SyncState(@PrimaryKey val key: String = "alumnos", val syncedAt: Long)

/**
 * Lista y fecha de persistencia, leídas en la misma transacción.
 *
 * @author koldo
 * @description Lista y fecha de persistencia, leídas en la misma transacción.
 */
data class StudentSnapshot(val students: List<Student>, val syncedAt: Long)

/**
 * Contrato mínimo de persistencia usado por el repositorio y sus dobles de prueba.
 *
 * @author koldo
 * @description Contrato mínimo de persistencia usado por el repositorio y sus dobles de prueba.
 */
interface StudentCache {
    /**
     * Devuelve null si nunca se ha sincronizado; una instantánea con lista vacía sí es válida.
     *
     * @author koldo
     * @description Devuelve null si nunca se ha sincronizado; una instantánea con lista vacía sí es válida.
     */
    suspend fun read(): StudentSnapshot?

    /**
     * Actualiza la colección completa y su fecha de forma atómica.
     *
     * No se debe usar con respuestas filtradas o paginadas: elimina los registros ausentes.
     *
     * @author koldo
     * @description Actualiza la colección completa y su fecha de forma atómica.
     */
    suspend fun replaceComplete(students: List<Student>, syncedAt: Long)
}

/**
 * Acceso a SQLite. Room ejecuta las operaciones suspendidas fuera del hilo principal.
 *
 * @author koldo
 * @description Acceso a SQLite. Room ejecuta las operaciones suspendidas fuera del hilo principal.
 */
@Dao
abstract class StudentDao : StudentCache {
    /**
     * Lista los registros en el mismo orden que la última respuesta completa de la API.
     *
     * @author koldo
     * @description Lista los registros en el mismo orden que la última respuesta completa de la API.
     */
    @Query("SELECT * FROM students ORDER BY position, id")
    abstract suspend fun listEntities(): List<StudentEntity>

    /**
     * Lee la marca que distingue una colección vacía de una caché todavía sin inicializar.
     *
     * @author koldo
     * @description Lee la marca que distingue una colección vacía de una caché todavía sin inicializar.
     */
    @Query("SELECT * FROM sync_state WHERE `key` = 'alumnos'")
    abstract suspend fun syncState(): SyncState?

    /**
     * Inserta o actualiza por ID, sin crear duplicados. No elimina registros ausentes.
     *
     * @author koldo
     * @description Inserta o actualiza por ID, sin crear duplicados. No elimina registros ausentes.
     */
    @Upsert
    abstract suspend fun upsert(students: List<StudentEntity>)

    /**
     * Registra la fecha una vez guardada la colección.
     *
     * @author koldo
     * @description Registra la fecha una vez guardada la colección.
     */
    @Upsert
    abstract suspend fun saveSync(state: SyncState)

    /**
     * Elimina en una sola consulta los IDs ajenos a una colección completa no vacía.
     *
     * @author koldo
     * @description Elimina en una sola consulta los IDs ajenos a una colección completa no vacía.
     */
    @Query("DELETE FROM students WHERE id NOT IN (:ids)")
    protected abstract suspend fun deleteAbsent(ids: List<String>)

    /**
     * Procesa explícitamente una colección completa vacía, sin depender de NOT IN vacío.
     *
     * @author koldo
     * @description Procesa explícitamente una colección completa vacía, sin depender de NOT IN vacío.
     */
    @Query("DELETE FROM students")
    protected abstract suspend fun deleteAll()

    /**
     * Lee datos y fecha de forma coherente, aunque haya otra sincronización en curso.
     *
     * @author koldo
     * @description Lee datos y fecha de forma coherente, aunque haya otra sincronización en curso.
     */
    @Transaction
    override suspend fun read(): StudentSnapshot? {
        val state = syncState() ?: return null
        return StudentSnapshot(listEntities().map { it.toStudent() }, state.syncedAt)
    }

    /**
     * Sincroniza exclusivamente la respuesta completa de GET /alumnos.
     *
     * Room revierte datos y fecha juntos si falla cualquier operación de la transacción.
     *
     * @author koldo
     * @description Sincroniza exclusivamente la respuesta completa de GET /alumnos.
     */
    @Transaction
    override suspend fun replaceComplete(students: List<Student>, syncedAt: Long) {
        if (students.isEmpty()) deleteAll() else deleteAbsent(students.map { it.id })
        upsert(students.mapIndexed { index, student -> student.toEntity(index) })
        saveSync(SyncState(syncedAt = syncedAt))
        // Verifica dentro de la transacción: una discrepancia revierte filas y fecha juntas.
        check(read() == StudentSnapshot(students, syncedAt)) { "R01-PERSISTENCE: SQLite mismatch" }
    }
}

/**
 * Base local inicial. Los cambios de esquema deben incrementar la versión y aportar migración;
 * no se permite recreación destructiva de la caché.
 *
 * @author koldo
 * @description Base local inicial. Los cambios de esquema deben incrementar la versión y aportar migración; no se permite recreación destructiva de la caché.
 */
@Database(entities = [StudentEntity::class, SyncState::class], version = 1, exportSchema = true)
abstract class StudentDatabase : RoomDatabase() {
    /**
     * DAO de alumnos y de su estado de sincronización.
     *
     * @author koldo
     * @description DAO de alumnos y de su estado de sincronización.
     */
    abstract fun students(): StudentDao

    companion object {
        /**
         * Crea la base con el contexto de aplicación para no retener una Activity.
         *
         * @author koldo
         * @description Crea la base con el contexto de aplicación para no retener una Activity.
         */
        fun create(context: Context): StudentDatabase = Room.databaseBuilder(
            context.applicationContext, StudentDatabase::class.java, "hogwarts.db"
        ).build()
    }
}
