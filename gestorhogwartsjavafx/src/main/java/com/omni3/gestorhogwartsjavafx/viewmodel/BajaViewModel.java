package com.omni3.gestorhogwartsjavafx.viewmodel;

import com.omni3.gestorhogwartsjavafx.api.dto.AlumnoDto;
import com.omni3.gestorhogwartsjavafx.servicio.ServicioAlumnos;
import com.omni3.gestorhogwartsjavafx.util.Idiomas;
import com.omni3.gestorhogwartsjavafx.util.Validaciones;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Viewmodel de la baja de un alumno ({@code DELETE /students/{dni}}).
 *
 * <p>La baja lógica se aplica en los alumnos nuevos; en los históricos,
 * el comportamiento es el de su base de origen (§2.4). La vista sólo
 * muestra el resultado: la confirmación es responsabilidad del
 * controlador, que invoca {@link #bajar(String, Runnable)} una vez el
 * usuario ha confirmado.</p>
 * @author jairoben
 */
public class BajaViewModel extends ViewModelBase {

    // Alumno que se acaba de dar de baja, si la operación terminó bien.
    private final ObjectProperty<AlumnoDto> bajaRealizada = new SimpleObjectProperty<>();

    /**
     * Crea el viewmodel.
     *
     * @param servicio servicio de fondo; no nulo
     */
    public BajaViewModel(ServicioAlumnos servicio) {
        super(servicio);
        reiniciar(Idiomas.texto("comun.listo"));
    }

    /**
     * Alumno dado de baja.
     *
     * @return la ficha del alumno tras la baja; {@code null} si la operación sigue en curso o ha fallado
     */
    public AlumnoDto getBajaRealizada() {
        return bajaRealizada.get();
    }

    /**
     * Propiedad observable de la baja; la vista se suscribe a sus
     * cambios para reaccionar al resultado de la operación.
     *
     * @return la propiedad enlazada con la ficha del alumno, vaciada al empezar y rellena si la baja termina bien
     */
    public ObjectProperty<AlumnoDto> bajaRealizadaProperty() {
        return bajaRealizada;
    }

    /**
     * Ejecuta la baja del alumno indicado.
     *
     * @param dni       DNI del alumno a dar de baja
     * @param trasExito acción a realizar cuando la baja termina bien
     *                   (p. ej. cerrar el diálogo o refrescar la tabla);
     *                   puede ser {@code null}
     */
    public void bajar(String dni, Runnable trasExito) {
        String normalizado = Validaciones.normalizarDNI(dni);
        if (normalizado == null || normalizado.isEmpty()) {
            marcarFallo(new com.omni3.gestorhogwartsjavafx.api.ExcepcionApi(
                    400, "VALIDACION", Idiomas.texto("baja.sin.dni"), null));
            return;
        }
        bajaRealizada.set(null);
        marcarCargando(Idiomas.texto("baja.cargando"));
        servicio.bajarAlumno(normalizado,
                () -> {
                    bajaRealizada.set(new com.omni3.gestorhogwartsjavafx.api.dto.AlumnoDto(
                            normalizado, "", null, null, "", "", 0L, null, false));
                    marcarOk(Idiomas.texto("baja.ok"));
                    if (trasExito != null) {
                        trasExito.run();
                    }
                },
                this::marcarFallo);
    }
}
