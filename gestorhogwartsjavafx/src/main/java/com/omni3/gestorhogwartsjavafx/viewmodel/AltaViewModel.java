package com.omni3.gestorhogwartsjavafx.viewmodel;

import com.omni3.gestorhogwartsjavafx.api.dto.AlumnoDto;
import com.omni3.gestorhogwartsjavafx.modelo.Origen;
import com.omni3.gestorhogwartsjavafx.modelo.TipoAlumno;
import com.omni3.gestorhogwartsjavafx.servicio.ServicioAlumnos;
import com.omni3.gestorhogwartsjavafx.util.ResultadoValidacion;
import com.omni3.gestorhogwartsjavafx.util.Validaciones;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Viewmodel del formulario de alta de alumno nuevo.
 *
 * <p>Según el §2.2 de la propuesta, el alta se hace siempre en la central
 * MariaDB: <strong>Patronus obligatorio</strong>, <strong>DNI no
 * duplicado</strong> y <strong>sin casa</strong>. La validación se hace
 * además en cliente para dar feedback inmediato; la decisión final es de
 * la API.</p>
 */
public class AltaViewModel extends ViewModelBase implements FormularioAlumno {

    /** DNI del alumno a dar de alta. */
    private final StringProperty dni = new SimpleStringProperty("");

    /** Nombre y apellidos. */
    private final StringProperty nombre = new SimpleStringProperty("");

    /** Casa; debe quedar vacía en los alumnos nuevos. */
    private final StringProperty casa = new SimpleStringProperty("");

    /** Patronus; obligatorio. */
    private final StringProperty patronus = new SimpleStringProperty("");

    /** Error visible de cada campo; vacío si el campo es válido. */
    private final StringProperty errorDni = new SimpleStringProperty("");
    private final StringProperty errorNombre = new SimpleStringProperty("");
    private final StringProperty errorCasa = new SimpleStringProperty("");
    private final StringProperty errorPatronus = new SimpleStringProperty("");

    /** Habilita el botón de guardar cuando todos los campos válidan. */
    private final BooleanProperty valido = new SimpleBooleanProperty(false);

    /** Alumno devuelto por la API tras el alta; cierra el formulario. */
    private final ObjectProperty<AlumnoDto> altaRealizada = new SimpleObjectProperty<>();

    /**
     * Crea el viewmodel.
     *
     * @param servicio servicio de fondo; no nulo
     */
    public AltaViewModel(ServicioAlumnos servicio) {
        super(servicio);
        reiniciar("Complete los datos del alumno nuevo.");
        Runnable recalcular = this::validar;
        dni.addListener((o, a, b) -> recalcular.run());
        nombre.addListener((o, a, b) -> recalcular.run());
        casa.addListener((o, a, b) -> recalcular.run());
        patronus.addListener((o, a, b) -> recalcular.run());
        validar();
    }

    /** DNI. */
    public String getDni() {
        return dni.get();
    }

    /** Propiedad observable del DNI. */
    public StringProperty dniProperty() {
        return dni;
    }

    /** Nombre. */
    public String getNombre() {
        return nombre.get();
    }

    /** Propiedad observable del nombre. */
    public StringProperty nombreProperty() {
        return nombre;
    }

    /** Casa (siempre vacía en alumnos nuevos). */
    public String getCasa() {
        return casa.get();
    }

    /** Propiedad observable de la casa. */
    public StringProperty casaProperty() {
        return casa;
    }

    /** Patronus. */
    public String getPatronus() {
        return patronus.get();
    }

    /** Propiedad observable del patronus. */
    public StringProperty patronusProperty() {
        return patronus;
    }

    /** Error del campo DNI. */
    public String getErrorDni() {
        return errorDni.get();
    }

    /** Propiedad observable del error de DNI. */
    public StringProperty errorDniProperty() {
        return errorDni;
    }

    /** Error del campo nombre. */
    public String getErrorNombre() {
        return errorNombre.get();
    }

    /** Propiedad observable del error de nombre. */
    public StringProperty errorNombreProperty() {
        return errorNombre;
    }

    /** Error del campo casa. */
    public String getErrorCasa() {
        return errorCasa.get();
    }

    /** Propiedad observable del error de casa. */
    public StringProperty errorCasaProperty() {
        return errorCasa;
    }

    /** Error del campo patronus. */
    public String getErrorPatronus() {
        return errorPatronus.get();
    }

    /** Propiedad observable del error de patronus. */
    public StringProperty errorPatronusProperty() {
        return errorPatronus;
    }

    /** Indica si el formulario puede guardarse. */
    public boolean isValido() {
        return valido.get();
    }

    /** Propiedad observable de validez. */
    public BooleanProperty validoProperty() {
        return valido;
    }

    /** Alumno dado de alta, si la operación terminó bien. */
    public AlumnoDto getAltaRealizada() {
        return altaRealizada.get();
    }

    /** Propiedad observable del alta realizada. */
    public ObjectProperty<AlumnoDto> altaRealizadaProperty() {
        return altaRealizada;
    }

    /** {@inheritDoc} */
    @Override
    public ObjectProperty<AlumnoDto> resultadoProperty() {
        return altaRealizada;
    }

    /**
     * Valida el formulario en cliente y publica los errores de cada campo.
     *
     * @return {@code true} si todos los campos son válidos
     */
    public boolean validar() {
        ResultadoValidacion rDni = Validaciones.validarDNI(dni.get());
        ResultadoValidacion rNombre = Validaciones.validarNombre(nombre.get());
        ResultadoValidacion rCasa = Validaciones.validarCasa(casa.get(), TipoAlumno.NUEVO);
        ResultadoValidacion rPatronus = Validaciones.validarPatronus(patronus.get(), TipoAlumno.NUEVO);

        errorDni.set(rDni.esValido() ? "" : rDni.mensajeError().orElse(""));
        errorNombre.set(rNombre.esValido() ? "" : rNombre.mensajeError().orElse(""));
        errorCasa.set(rCasa.esValido() ? "" : rCasa.mensajeError().orElse(""));
        errorPatronus.set(rPatronus.esValido() ? "" : rPatronus.mensajeError().orElse(""));
        // La casa no es editable en alumnos nuevos: no bloquea el guardado.
        boolean ok = rDni.esValido() && rNombre.esValido() && rPatronus.esValido();
        valido.set(ok);
        return ok;
    }

    /**
     * Envía el alta a la API ({@code POST /students}).
     *
     * <p>Si la validación local falla, no se realiza ninguna petición y la
     * pantalla queda en {@link EstadoCarga#ERROR} con el motivo.</p>
     */
    public void guardar() {
        if (!validar()) {
            marcarFallo(new com.omni3.gestorhogwartsjavafx.api.ExcepcionApi(
                    400, "VALIDACION", "Revise los campos marcados antes de guardar.", null));
            return;
        }
        altaRealizada.set(null);
        marcarCargando("Dando de alta al alumno…");
        AlumnoDto aEnviar = new AlumnoDto(
                Validaciones.normalizarDNI(dni.get()),
                nombre.get().trim(),
                null,
                patronus.get().trim(),
                Origen.MARIADB.name(),
                TipoAlumno.NUEVO.name(),
                1L,
                null,
                true);
        servicio.altaAlumno(aEnviar,
                creado -> {
                    altaRealizada.set(creado);
                    marcarOk("Alumno dado de alta correctamente.");
                },
                this::marcarFallo);
    }

    /**
     * Vacía el formulario y deja la pantalla lista para otro alta.
     */
    public void limpiar() {
        dni.set("");
        nombre.set("");
        casa.set("");
        patronus.set("");
        altaRealizada.set(null);
        reiniciar("Complete los datos del alumno nuevo.");
        validar();
    }

    /**
     * En el alta no hay ficha que recargar: vacía el formulario para
     * empezar de nuevo.
     */
    @Override
    public void recargar() {
        limpiar();
    }
}
