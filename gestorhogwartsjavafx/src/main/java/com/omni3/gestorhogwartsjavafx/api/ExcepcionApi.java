package com.omni3.gestorhogwartsjavafx.api;

/**
 * Error de la capa de API: código de negocio, mensaje en español,
 * identificador de petición ({@code request-id}) y estado HTTP.
 *
 * <p>Es una excepción no obligada de capturar (hereda de
 * {@link RuntimeException}) para que las interfaces de la capa de acceso
 * sean limpias; el llamante decide el hilo y el manejo.</p>
 *
 * <p>Cuando el error no procede de una respuesta HTTP (por ejemplo, un
 * fallo de red o una validación local) el estado se codifica como
 * {@code 0}.</p>
 */
public class ExcepcionApi extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Estado HTTP de la respuesta; 0 cuando no hay respuesta. */
    private final int status;

    /** Código de negocio, p. ej. {@code CONFLICTO_VERSION}. */
    private final String codigo;

    /** Mensaje en español para el usuario. */
    private final String mensaje;

    /** Identificador de la petición (cabecera {@code request-id}); puede ser nulo. */
    private final String requestId;

    /**
     * Crea la excepción con todos los datos.
     *
     * @param status    estado HTTP (0 si no procede de HTTP)
     * @param codigo    código de negocio
     * @param mensaje   mensaje en español
     * @param requestId identificador de la petición; puede ser {@code null}
     * @throws IllegalArgumentException si el código o el mensaje están vacíos
     */
    public ExcepcionApi(int status, String codigo, String mensaje, String requestId) {
        super(mensaje);
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("El código de error no puede estar vacío");
        }
        if (mensaje == null || mensaje.isBlank()) {
            throw new IllegalArgumentException("El mensaje de error no puede estar vacío");
        }
        this.status = status;
        this.codigo = codigo;
        this.mensaje = mensaje;
        this.requestId = requestId;
    }

    /**
     * Crea la excepción asociada a una respuesta HTTP.
     *
     * @param status  estado HTTP
     * @param codigo  código de negocio
     * @param mensaje mensaje en español
     */
    public ExcepcionApi(int status, String codigo, String mensaje) {
        this(status, codigo, mensaje, null);
    }

    /**
     * Crea una excepción local sin respuesta HTTP.
     *
     * @param codigo  código de negocio
     * @param mensaje mensaje en español
     */
    public ExcepcionApi(String codigo, String mensaje) {
        this(0, codigo, mensaje, null);
    }

    /**
     * Estado HTTP asociado al error.
     *
     * @return estado HTTP, o 0 si el error es local
     */
    public int getStatus() {
        return status;
    }

    /**
     * Código de negocio del error.
     *
     * @return código, nunca vacío
     */
    public String getCodigo() {
        return codigo;
    }

    /**
     * Mensaje en español del error.
     *
     * @return mensaje, nunca vacío
     */
    public String getMensaje() {
        return mensaje;
    }

    /**
     * Identificador de la petición que falló.
     *
     * @return {@code request-id} o {@code null} si no se generó
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * Indica si el error es un conflicto de concurrencia (HTTP 409),
     * el caso en el que la UI debe recargar sin sobrescribir (§2.3).
     *
     * @return {@code true} si el estado es 409
     */
    public boolean esConflicto() {
        return status == 409;
    }

    /**
     * Indica si el error procede de una respuesta HTTP.
     *
     * @return {@code true} si hay estado HTTP (&gt; 0)
     */
    public boolean hayRespuestaHttp() {
        return status > 0;
    }

    @Override
    public String toString() {
        return "ExcepcionApi{status=" + status
                + ", codigo='" + codigo + '\''
                + ", mensaje='" + mensaje + '\''
                + ", requestId='" + requestId + '\''
                + '}';
    }
}
