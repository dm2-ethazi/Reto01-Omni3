package com.omni3.gestorhogwartsjavafx.controlador;

import com.omni3.gestorhogwartsjavafx.api.dto.SaludDto;
import com.omni3.gestorhogwartsjavafx.servicio.FabricaAplicacion;
import com.omni3.gestorhogwartsjavafx.viewmodel.EstadoCarga;
import com.omni3.gestorhogwartsjavafx.viewmodel.SaludViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;

/**
 * Controlador de la pantalla «Estado del sistema»
 * ({@code GET /health}, identificador {@code R01}).
 */
public class EstadoController implements ControladorVista {

    @FXML private Label campoIdentificador;
    @FXML private Label campoEstado;
    @FXML private Label campoMensaje;
    @FXML private Label campoMomento;
    @FXML private Label etiquetaError;
    @FXML private Label etiquetaRequestId;
    @FXML private Button botonComprobar;
    @FXML private ProgressIndicator indicadorCarga;

    private SaludViewModel viewmodel;

    /**
     * Crea el controlador de la pantalla «Estado del sistema».
     *
     * <p>El constructor no realiza ninguna inicialización: la configuración
     * de la vista se produce en {@link #conectar(FabricaAplicacion)}, que
     * FXMLLoader invoca al cargar el FXML.</p>
     */
    public EstadoController() {
    }

    @Override
    public void conectar(FabricaAplicacion fabrica) {
        this.viewmodel = fabrica.crearSaludViewModel();

        viewmodel.saludProperty().addListener((observable, antes, salud) -> pintarSalud(salud));
        viewmodel.mensajeProperty().addListener((observable, antes, mensaje) -> {
            boolean fallo = viewmodel.getEstado() == EstadoCarga.ERROR
                    || viewmodel.getEstado() == EstadoCarga.CONFLICTO;
            etiquetaError.setText(fallo ? mensaje : "");
            if (!fallo) {
                etiquetaError.getStyleClass().remove("texto-error");
            }
        });
        // Dos errores seguidos mantienen el estado en ERROR, así que el
        // identificador se repinta también cuando cambia el request-id.
        Runnable pintarRequestId = () -> {
            boolean fallo = viewmodel.getEstado() == EstadoCarga.ERROR
                    || viewmodel.getEstado() == EstadoCarga.CONFLICTO;
            String identificador = viewmodel.getRequestId();
            etiquetaRequestId.setText(fallo && !identificador.isEmpty()
                    ? "request-id: " + identificador : "");
        };
        viewmodel.estadoProperty().addListener((observable, antes, estado) -> {
            boolean cargando = estado == EstadoCarga.CARGANDO;
            indicadorCarga.setVisible(cargando);
            botonComprobar.setDisable(cargando);
            if (estado == EstadoCarga.ERROR || estado == EstadoCarga.CONFLICTO) {
                if (!etiquetaError.getStyleClass().contains("texto-error")) {
                    etiquetaError.getStyleClass().add("texto-error");
                }
            } else {
                etiquetaRequestId.setText("");
            }
            pintarRequestId.run();
        });
        viewmodel.requestIdProperty().addListener((observable, antes, identificador) ->
                pintarRequestId.run());

        comprobar();
    }

    /** Consulta el estado del sistema. */
    @FXML
    private void comprobar() {
        if (viewmodel != null) {
            viewmodel.comprobar();
        }
    }

    /** Pinta la ficha de salud; borra los datos si no hay respuesta. */
    private void pintarSalud(SaludDto salud) {
        if (salud == null) {
            campoIdentificador.setText("—");
            campoEstado.setText("—");
            campoMensaje.setText("—");
            campoMomento.setText("—");
            return;
        }
        campoIdentificador.setText(salud.identificador());
        campoEstado.setText(salud.estado());
        campoMensaje.setText(salud.mensaje());
        campoMomento.setText(salud.momento());
    }
}
