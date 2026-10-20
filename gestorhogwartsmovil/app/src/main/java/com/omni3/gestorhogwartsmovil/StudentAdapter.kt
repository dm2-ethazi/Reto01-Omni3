package com.omni3.gestorhogwartsmovil

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.omni3.gestorhogwartsmovil.databinding.ItemStudentBinding

/**
 * Presenta alumnos de cualquier origen y comunica la selección a la Activity.
 * ListAdapter calcula los cambios por ID sin reconstruir toda la lista en cada búsqueda.
 *
 * @author koldo
 * @description Presenta alumnos de cualquier origen y comunica la selección a la Activity. ListAdapter calcula los cambios por ID sin reconstruir toda la lista en cada búsqueda.
 */
class StudentAdapter(
    private val onStudentClick: (Student) -> Unit
) : ListAdapter<Student, StudentAdapter.StudentViewHolder>(StudentDiff) {

    /**
     * Mantiene las vistas de una fila mediante View Binding, sin búsquedas manuales por ID.
     *
     * @author koldo
     * @description Mantiene las vistas de una fila mediante View Binding, sin búsquedas manuales por ID.
     */
    class StudentViewHolder(private val binding: ItemStudentBinding) : RecyclerView.ViewHolder(binding.root) {
        /**
         * Enlaza datos y selección al alumno actual, aunque la fila se haya reciclado.
         *
         * @author koldo
         * @description Enlaza datos y selección al alumno actual, aunque la fila se haya reciclado.
         */
        fun bind(student: Student, onStudentClick: (Student) -> Unit) {
            binding.textoDni.text = student.id
            binding.textoNombre.text = student.nombreCompleto
            binding.root.setOnClickListener { onStudentClick(student) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val binding = ItemStudentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StudentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        holder.bind(getItem(position), onStudentClick)
    }

    /**
     * Sustituye resultados; la copia evita modificaciones externas durante el cálculo de cambios.
     *
     * @author koldo
     * @description Sustituye resultados; la copia evita modificaciones externas durante el cálculo de cambios.
     */
    fun mostrarEstudiantes(students: List<Student>) {
        submitList(students.toList())
    }

    /**
     * El ID identifica la fila; la igualdad del modelo detecta cambios en cualquiera de sus campos.
     *
     * @author koldo
     * @description El ID identifica la fila; la igualdad del modelo detecta cambios en cualquiera de sus campos.
     */
    private object StudentDiff : DiffUtil.ItemCallback<Student>() {
        override fun areItemsTheSame(oldItem: Student, newItem: Student): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Student, newItem: Student): Boolean = oldItem == newItem
    }
}
