package com.omni3.gestorhogwartsjavafx.viewmodel;

import com.omni3.gestorhogwartsjavafx.api.dto.AlumnoDto;
import com.omni3.gestorhogwartsjavafx.modelo.TipoAlumno;
import com.omni3.gestorhogwartsjavafx.servicio.ServicioAlumnos;
import com.omni3.gestorhogwartsjavafx.util.ResultadoValidacion;
import com.omni3.gestorhogwartsjavafx.util.Validaciones;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Viewmodel de la edición de una ficha de alumno.
 *
 * <p>Carga la ficha desde la API y, al guardar, <strong>envía siempre la
 * versión previa</strong> (control de concurrencia optimista, §2.3). Si la
 * API responde 409 la pantalla pasa a {@link EstadoCarga#CONFLICTO} con el
 * mensaje correspondiente y <strong>no se sobrescribe nada</strong>: la
 * vista ofrece recargar la ficha con {@link #recargar()}.</p>
 */
public class EdicionViewModel extends ViewModelBase implements FormularioAlumno {

    /** DNI de la ficha en edición; fijo tras {@link #cargar(String)}. */
    private final StringProperty dni = new SimpleStringProperty("");

    /** Nombre y apellidos. */
    private final StringProperty nombre = new SimpleStringProperty("");

    /** Casa (obligatoria en los históricos). */
    private final StringProperty casa = new SimpleStringProperty("");

    /** Patronus. */
    private final StringProperty patronus = new SimpleStringProperty("");

    /** Origen de la ficha, informativo y no editable. */
    private final ReadOnlyStringWrapper origenTexto = new ReadOnlyStringWrapper("");

    /** Tipo de alumno, informativo y no editable. */
    private final ReadOnlyStringWrapper tipoTexto = new ReadOnlyStringWrapper("");

    /** Versión previa enviada a la API (estado del registro al cargarlo). */
    private final ObjectProperty<Long> versionPrevia = new SimpleObjectProperty<>(1L);

    /** Errores de campo. */
    private final StringProperty errorNombre = new SimpleStringProperty("");
    private final StringProperty errorCasa = new SimpleStringProperty("");
    private final StringProperty errorPatronus = new SimpleStringProperty("");

    /** Habilita el botón de guardar. */
    private final BooleanProperty valido = new SimpleBooleanProperty(false);

    /** Ficha recién guardada; la vista la usa para refrescar la tabla. */
    private final ObjectProperty<AlumnoDto> guardado = new SimpleObjectProperty<>();

    /**
     * Crea el viewmodel.
     *
     * @param servicio servicio de fondo; no nulo
     */
    public EdicionViewModel(ServicioAlumnos servicio) {
        super(servicio);
        reiniciar("Seleccione una ficha para editarla.");
        Runnable recalcular = this::validar;
        nombre.addListener((o, a, b) -> recalcular.run());
        casa.addListener((o, a, b) -> recalcular.run());
        patronus.addListener((o, a, b) -> recalcular.run());
        validar();
    }

    /** DNI de la ficha. */
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

    /** Casa. */
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

    /** Origen de la ficha. */
    public String getOrigenTexto() {
        return origenTexto.get();
    }

    /** Propiedad observable del origen. */
    public ReadOnlyStringProperty origenTextoProperty() {
        return origenTexto;
    }

    /** Tipo de alumno. */
    public String getTipoTexto() {
        return tipoTexto.get();
    }

    /** Propiedad observable del tipo. */
    public ReadOnlyStringProperty tipoTextoProperty() {
        return tipoTexto;
    }

    /** Versión previa enviada a la API. */
    public long getVersionPrevia() {
        Long valor = versionPrevia.get();
        return valor == null ? 1L : valor;
    }

    /** Propiedad observable de la versión previa. */
    public ObjectProperty<Long> versionPreviaProperty() {
        return versionPrevia;
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

    /** Ficha guardada, si la operación terminó bien. */
    public AlumnoDto getGuardado() {
        return guardado.get();
    }

    /** Propiedad observable de la ficha guardada. */
    public ObjectProperty<AlumnoDto> guardadoProperty() {
        return guardado;
    }

    /** {@inheritDoc} */
    @Override
    public ObjectProperty<AlumnoDto> resultadoProperty() {
        return guardado;
    }

    /**
     * Carga la ficha desde la API ({@code GET /students/{dni}}) y rellena
     * el formulario con la versión vigente.
     *
     * @param dniAlumno DNI de la ficha a editar
     */
    public void cargar(String dniAlumno) {
        guardado.set(null);
        marcarCargando("Cargando ficha…");
        servicio.obtenerAlumno(dniAlumno,
                ficha -> {
                    dni.set(ficha.dni());
                    nombre.set(ficha.nombre() == null ? "" : ficha.nombre());
                    casa.set(ficha.casa() == null ? "" : ficha.casa());
                    patronus.set(ficha.patronus() == null ? "" : ficha.patronus());
                    origenTexto.set(ficha.origen());
                    tipoTexto.set(ficha.tipo());
                    versionPrevia.set(ficha.version());
                    validar();
                    marcarOk("Ficha cargada (versión " + ficha.version() + ").");
                },
                this::marcarFallo);
    }

    /**
     * Recarga la ficha tras un conflicto de concurrencia, descartando los
     * cambios locales y recuperando el estado vigente del servidor.
     */
    public void recargar() {
        cargar(dni.get());
    }

    /**
     * Valida el formulario según el tipo de alumno.
     *
     * @return {@code true} si todos los campos válidan
     */
    public boolean validar() {
        TipoAlumno tipo = TipoAlumno.desdeTexto(tipoTexto.get()).orElse(TipoAlumno.HISTORICO);
        ResultadoValidacion rNombre = Validaciones.validarNombre(nombre.get());
        ResultadoValidacion rCasa = Validaciones.validarCasa(casa.get(), tipo);
        ResultadoValidacion rPatronus = Validaciones.validarPatronus(patronus.get(), tipo);

        errorNombre.set(rNombre.esValido() ? "" : rNombre.mensajeError().orElse(""));
        errorCasa.set(rCasa.esValido() ? "" : rCasa.mensajeError().orElse(""));
        errorPatronus.set(rPatronus.esValido() ? "" : rPatronus.mensajeError().orElse(""));
        boolean ok = rNombre.esValido() && rCasa.esValido() && rPatronus.esValido()
                && !dni.get().isEmpty();
        valido.set(ok);
        return ok;
    }

    /**
     * Envía los cambios a la API ({@code PUT /students/{dni}}) con la
     * versión previa como estado del registro.
     *
     * <p>Ante un 409 la pantalla queda en
     * {@link EstadoCarga#CONFLICTO}, con los cambios locales intactos y sin
     * sobrescribir lo que hay en la fuente (§2.3).</p>
     */
    public void guardar() {
        if (!validar()) {
            marcarFallo(new com.omni3.gestorhogwartsjavafx.api.ExcepcionApi(
                    400, "VALIDACION", "Revise los campos marcados antes de guardar.", null));
            return;
        }
        guardado.set(null);
        marcarCargando("Guardando cambios…");
        TipoAlumno tipo = TipoAlumno.desdeTexto(tipoTexto.get()).orElse(TipoAlumno.HISTORICO);
        String casaLimpia = casa.get() == null || casa.get().isBlank() ? null : casa.get().trim();
        String patronusLimpio = patronus.get() == null || patronus.get().isBlank()
                ? null : patronus.get().trim();
        AlumnoDto aEnviar = new AlumnoDto(
                dni.get(),
                nombre.get().trim(),
                casaLimpia,
                patronusLimpio,
                origenTexto.get(),
                tipo.name(),
                getVersionPrevia(),
                null,
                true);
        servicio.editarAlumno(dni.get(), aEnviar,
                actualizado -> {
                    guardado.set(actualizado);
                    versionPrevia.set(actualizado.version());
                    marcarOk("Cambios guardados (versión " + actualizado.version() + ").");
                },
                this::marcarFallo);
    }
}
