package com.omni3.gestorhogwartsmovil

/**
 * Alumno con los campos del contrato REST; se usa también en la lista y en la ficha.
 *
 * @author koldo
 * @description Alumno con los campos del contrato REST; se usa también en la lista y en la ficha.
 * @property id Identificador original de la API y clave de la caché local.
 * @property nombre Nombre obligatorio del alumno.
 * @property apellido Apellido opcional.
 * @property sexo Sexo informado por la API, si existe.
 * @property patronus Patronus del alumno, si existe.
 * @property casa Casa del alumno, si existe.
 * @property imagen URL opcional de la foto; la imagen no se guarda en SQLite.
 */
data class Student(
    val id: String,
    val nombre: String,
    val apellido: String?,
    val sexo: String?,
    val patronus: String?,
    val casa: String?,
    val imagen: String?
) {
    /**
     * Nombre y apellido unidos, omitiendo el apellido cuando es null.
     *
     * @author koldo
     * @description Nombre y apellido unidos, omitiendo el apellido cuando es null.
     */
    val nombreCompleto: String
        get() = listOfNotNull(nombre, apellido).joinToString(" ")

    /**
     * Coincidencia parcial sin distinguir mayúsculas; una búsqueda vacía incluye al alumno.
     *
     * @author koldo
     * @description Coincidencia parcial sin distinguir mayúsculas; una búsqueda vacía incluye al alumno.
     */
    fun coincideConNombre(busqueda: String): Boolean =
        nombreCompleto.contains(busqueda.trim(), ignoreCase = true) ||
            id.equals(busqueda.trim(), ignoreCase = true)
}
