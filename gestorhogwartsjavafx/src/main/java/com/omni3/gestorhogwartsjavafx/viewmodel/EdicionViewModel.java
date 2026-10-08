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

    // DNI de la ficha en edición; fijo tras {@link #cargar(String)}.
    private final StringProperty dni = new SimpleStringProperty("");

    // Nombre y apellidos.
    private final StringProperty nombre = new SimpleStringProperty("");

    // Casa (obligatoria en los históricos).
    private final StringProperty casa = new SimpleStringProperty("");

    // Patronus.
    private final StringProperty patronus = new SimpleStringProperty("");

    // Origen de la ficha, informativo y no editable.
    private final ReadOnlyStringWrapper origenTexto = new ReadOnlyStringWrapper("");

    // Tipo de alumno, informativo y no editable.
    private final ReadOnlyStringWrapper tipoTexto = new ReadOnlyStringWrapper("");

    // Versión previa enviada a la API (estado del registro al cargarlo).
    private final ObjectProperty<Long> versionPrevia = new SimpleObjectProperty<>(1L);

    // Errores de campo.
    private final StringProperty errorNombre = new SimpleStringProperty("");
    private final StringProperty errorCasa = new SimpleStringProperty("");
    private final StringProperty errorPatronus = new SimpleStringProperty("");

    // Habilita el botón de guardar.
    private final BooleanProperty valido = new SimpleBooleanProperty(false);

    // Ficha recién guardada; la vista la usa para refrescar la tabla.
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

    /**
     * DNI que identifica la ficha cargada.
     *
     * @return el DNI de la ficha en edición; cadena vacía hasta que
     *         {@link #cargar(String)} lo rellena.
     */
    public String getDni() {
        return dni.get();
    }

    /**
     * Propiedad observable del DNI; la vista la enlaza al campo y se
     * actualiza cuando se carga la ficha.
     *
     * @return la propiedad {@code StringProperty} que mantiene el DNI.
     */
    public StringProperty dniProperty() {
        return dni;
    }

    /**
     * Nombre y apellidos de la ficha.
     *
     * @return el nombre actual, o cadena vacía si la ficha todavía no se
     *         ha cargado.
     */
    public String getNombre() {
        return nombre.get();
    }

    /**
     * Propiedad observable del nombre; el campo se enlaza a ella y su
     * cambio dispara la revalidación del formulario.
     *
     * @return la propiedad {@code StringProperty} que mantiene el nombre.
     */
    public StringProperty nombreProperty() {
        return nombre;
    }

    /**
     * Casa de la ficha.
     *
     * @return la casa del alumno, o cadena vacía si la ficha no tiene casa
     *         o aún no se ha cargado.
     */
    public String getCasa() {
        return casa.get();
    }

    /**
     * Propiedad observable de la casa; el campo se enlaza a ella y su
     * cambio dispara la revalidación del formulario.
     *
     * @return la propiedad {@code StringProperty} que mantiene la casa.
     */
    public StringProperty casaProperty() {
        return casa;
    }

    /**
     * Patronus de la ficha.
     *
     * @return el Patronus del alumno, o cadena vacía si la ficha no tiene
     *         valor o aún no se ha cargado.
     */
    public String getPatronus() {
        return patronus.get();
    }

    /**
     * Propiedad observable del patronus; el campo se enlaza a ella y su
     * cambio dispara la revalidación del formulario.
     *
     * @return la propiedad {@code StringProperty} que mantiene el Patronus.
     */
    public StringProperty patronusProperty() {
        return patronus;
    }

    /**
     * Origen de la ficha, de solo lectura.
     *
     * @return el origen devuelto por la API al cargar la ficha, tal como
     *         debe reenviarse en la edición.
     */
    public String getOrigenTexto() {
        return origenTexto.get();
    }

    /**
     * Propiedad observable del origen, solo lectura; la vista la muestra
     * como dato informativo junto a la ficha.
     *
     * @return la propiedad {@code ReadOnlyStringProperty} con el origen de
     *         la ficha.
     */
    public ReadOnlyStringProperty origenTextoProperty() {
        return origenTexto;
    }

    /**
     * Tipo de alumno de la ficha, de solo lectura.
     *
     * @return el tipo devuelto por la API, que además determina qué reglas
     *         de validación se aplican al formulario.
     */
    public String getTipoTexto() {
        return tipoTexto.get();
    }

    /**
     * Propiedad observable del tipo, solo lectura; de su valor depende la
     * validación que se aplica en cada revalidado.
     *
     * @return la propiedad {@code ReadOnlyStringProperty} con el tipo de
     *         alumno.
     */
    public ReadOnlyStringProperty tipoTextoProperty() {
        return tipoTexto;
    }

    /**
     * Versión previa enviada a la API.
     *
     * @return la versión del registro tal como está en el servidor;
     *         {@code 1} si la propiedad no tiene valor.
     */
    public long getVersionPrevia() {
        Long valor = versionPrevia.get();
        return valor == null ? 1L : valor;
    }

    /**
     * Propiedad observable de la versión previa que se envía a la API para
     * el control de concurrencia optimista.
     *
     * @return la propiedad {@code ObjectProperty} con la versión del
     *         registro al cargarlo.
     */
    public ObjectProperty<Long> versionPreviaProperty() {
        return versionPrevia;
    }

    /**
     * Error visible del campo nombre.
     *
     * @return el mensaje que se muestra bajo el campo, o cadena vacía si
     *         el nombre supera la validación.
     */
    public String getErrorNombre() {
        return errorNombre.get();
    }

    /**
     * Propiedad observable del error de nombre; la vista la lee para
     * mostrar el mensaje bajo el campo correspondiente.
     *
     * @return la propiedad {@code StringProperty} que publica el error del
     *         nombre.
     */
    public StringProperty errorNombreProperty() {
        return errorNombre;
    }

    /**
     * Error visible del campo casa.
     *
     * @return el mensaje que se muestra bajo el campo, o cadena vacía si
     *         la casa supera la validación.
     */
    public String getErrorCasa() {
        return errorCasa.get();
    }

    /**
     * Propiedad observable del error de casa; la vista la lee para mostrar
     * el mensaje bajo el campo correspondiente.
     *
     * @return la propiedad {@code StringProperty} que publica el error de
     *         la casa.
     */
    public StringProperty errorCasaProperty() {
        return errorCasa;
    }

    /**
     * Error visible del campo patronus.
     *
     * @return el mensaje que se muestra bajo el campo, o cadena vacía si
     *         el Patronus supera la validación.
     */
    public String getErrorPatronus() {
        return errorPatronus.get();
    }

    /**
     * Propiedad observable del error de patronus; la vista la lee para
     * mostrar el mensaje bajo el campo correspondiente.
     *
     * @return la propiedad {@code StringProperty} que publica el error del
     *         Patronus.
     */
    public StringProperty errorPatronusProperty() {
        return errorPatronus;
    }

    /**
     * Indica si el formulario puede guardarse.
     *
     * @return {@code true} si nombre, casa, patronus y DNI cumplen las
     *         reglas del tipo de alumno; {@code false} en caso contrario.
     */
    public boolean isValido() {
        return valido.get();
    }

    /**
     * Propiedad observable de validez; la vista la observa para habilitar
     * o deshabilitar el botón de guardar.
     *
     * @return la propiedad {@code BooleanProperty} que refleja el
     *         resultado de la última validación.
     */
    public BooleanProperty validoProperty() {
        return valido;
    }

    /**
     * Ficha guardada, si la operación terminó bien.
     *
     * @return la ficha devuelta por la API tras el {@code PUT}, o
     *         {@code null} mientras el guardado no termine con éxito.
     */
    public AlumnoDto getGuardado() {
        return guardado.get();
    }

    /**
     * Propiedad observable de la ficha guardada; la vista la observa para
     * refrescar la tabla con los cambios.
     *
     * @return la propiedad {@code ObjectProperty} que entrega la ficha
     *         actualizada.
     */
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
