package com.omni3.gestorhogwartsjavafx.viewmodel;

import com.omni3.gestorhogwartsjavafx.api.dto.AlumnoDto;
import com.omni3.gestorhogwartsjavafx.modelo.Origen;
import com.omni3.gestorhogwartsjavafx.modelo.TipoAlumno;
import com.omni3.gestorhogwartsjavafx.servicio.ServicioAlumnos;
import com.omni3.gestorhogwartsjavafx.util.Idiomas;
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

    // DNI del alumno a dar de alta.
    private final StringProperty dni = new SimpleStringProperty("");

    // Nombre y apellidos.
    private final StringProperty nombre = new SimpleStringProperty("");

    // Casa; debe quedar vacía en los alumnos nuevos.
    private final StringProperty casa = new SimpleStringProperty("");

    // Patronus; obligatorio.
    private final StringProperty patronus = new SimpleStringProperty("");

    // Error visible de cada campo; vacío si el campo es válido.
    private final StringProperty errorDni = new SimpleStringProperty("");
    private final StringProperty errorNombre = new SimpleStringProperty("");
    private final StringProperty errorCasa = new SimpleStringProperty("");
    private final StringProperty errorPatronus = new SimpleStringProperty("");

    // Habilita el botón de guardar cuando todos los campos válidan.
    private final BooleanProperty valido = new SimpleBooleanProperty(false);

    // Alumno devuelto por la API tras el alta; cierra el formulario.
    private final ObjectProperty<AlumnoDto> altaRealizada = new SimpleObjectProperty<>();

    /**
     * Crea el viewmodel.
     *
     * @param servicio servicio de fondo; no nulo
     */
    public AltaViewModel(ServicioAlumnos servicio) {
        super(servicio);
        reiniciar(Idiomas.texto("alta.inicial"));
        Runnable recalcular = this::validar;
        dni.addListener((o, a, b) -> recalcular.run());
        nombre.addListener((o, a, b) -> recalcular.run());
        casa.addListener((o, a, b) -> recalcular.run());
        patronus.addListener((o, a, b) -> recalcular.run());
        validar();
    }

    /**
     * DNI introducido en el formulario de alta.
     *
     * @return el valor actual del campo, sin normalizar todavía; cadena
     *         vacía si el usuario no ha escrito nada.
     */
    public String getDni() {
        return dni.get();
    }

    /**
     * Propiedad observable del DNI; el campo de texto se enlaza a ella y
     * su cambio dispara la revalidación del formulario.
     *
     * @return la propiedad {@code StringProperty} que mantiene el DNI.
     */
    public StringProperty dniProperty() {
        return dni;
    }

    /**
     * Nombres y apellidos capturados en el formulario.
     *
     * @return la cadena escrita por el usuario, o vacío si todavía no se
     *         ha rellenado el campo.
     */
    public String getNombre() {
        return nombre.get();
    }

    /**
     * Propiedad observable del nombre; el campo de texto se enlaza a ella
     * y su cambio dispara la revalidación del formulario.
     *
     * @return la propiedad {@code StringProperty} que mantiene el nombre.
     */
    public StringProperty nombreProperty() {
        return nombre;
    }

    /**
     * Casa del alumno nuevo.
     *
     * @return la casa actual, vacía por defecto porque el alta no llega a
     *         asignar ninguna.
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
     * Patronus escrito en el formulario.
     *
     * @return el valor capturado para el Patronus; vacío hasta que el
     *         usuario lo rellena.
     */
    public String getPatronus() {
        return patronus.get();
    }

    /**
     * Propiedad observable del patronus; el campo de texto se enlaza a
     * ella y su cambio dispara la revalidación del formulario.
     *
     * @return la propiedad {@code StringProperty} que mantiene el Patronus.
     */
    public StringProperty patronusProperty() {
        return patronus;
    }

    /**
     * Error visible del campo DNI.
     *
     * @return el mensaje que se muestra bajo el campo, o cadena vacía si
     *         el DNI supera la validación.
     */
    public String getErrorDni() {
        return errorDni.get();
    }

    /**
     * Propiedad observable del error de DNI; la vista la lee para mostrar
     * el mensaje bajo el campo correspondiente.
     *
     * @return la propiedad {@code StringProperty} que publica el error del
     *         DNI.
     */
    public StringProperty errorDniProperty() {
        return errorDni;
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
     * @return {@code true} si DNI, nombre y Patronus superan la validación
     *         local; {@code false} en caso contrario.
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
     * Alumno dado de alta, si la operación terminó bien.
     *
     * @return el alumno creado devuelto por la API, o {@code null} mientras
     *         el alta no se complete con éxito.
     */
    public AlumnoDto getAltaRealizada() {
        return altaRealizada.get();
    }

    /**
     * Propiedad observable del alta realizada; la vista la observa para
     * cerrar el formulario cuando la API devuelve el alumno.
     *
     * @return la propiedad {@code ObjectProperty} que entrega el alumno
     *         creado.
     */
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
     * Envía el alta a la API ({@code POST /alumnos}).
     *
     * <p>Si la validación local falla, no se realiza ninguna petición y la
     * pantalla queda en {@link EstadoCarga#ERROR} con el motivo. Los errores
     * de la API se publican también como {@link EstadoCarga#ERROR} y nunca
     * como {@link EstadoCarga#CONFLICTO}: en un alta no hay versión previa,
     * de modo que un 409 (DNI duplicado) no debe abrir el diálogo de
     * conflicto de concurrencia reservado a la edición.</p>
     */
    public void guardar() {
        if (!validar()) {
            marcarError(new com.omni3.gestorhogwartsjavafx.api.ExcepcionApi(
                    400, "VALIDACION", Idiomas.texto("form.revise"), null));
            return;
        }
        altaRealizada.set(null);
        marcarCargando(Idiomas.texto("alta.cargando"));
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
                    marcarOk(Idiomas.texto("alta.ok"));
                },
                this::marcarError);
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
        reiniciar(Idiomas.texto("alta.inicial"));
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
