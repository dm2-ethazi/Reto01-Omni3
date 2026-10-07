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
    @FXML private Label campoModo;
    @FXML private Label etiquetaError;
    @FXML private Label etiquetaRequestId;
    @FXML private Button botonComprobar;
    @FXML private ProgressIndicator indicadorCarga;

    private SaludViewModel viewmodel;

    @Override
    public void conectar(FabricaAplicacion fabrica) {
        this.viewmodel = fabrica.crearSaludViewModel();
        campoModo.setText(fabrica.getConfig().getModo().name()
                + " · " + fabrica.getConfig().getBaseUrl());

        viewmodel.saludProperty().addListener((observable, antes, salud) -> pintarSalud(salud));
        viewmodel.mensajeProperty().addListener((observable, antes, mensaje) -> {
            boolean fallo = viewmodel.getEstado() == EstadoCarga.ERROR
                    || viewmodel.getEstado() == EstadoCarga.CONFLICTO;
            etiquetaError.setText(fallo ? mensaje : "");
            if (!fallo) {
                etiquetaError.getStyleClass().remove("texto-error");
            }
        });
        viewmodel.estadoProperty().addListener((observable, antes, estado) -> {
            boolean cargando = estado == EstadoCarga.CARGANDO;
            indicadorCarga.setVisible(cargando);
            botonComprobar.setDisable(cargando);
            if (estado == EstadoCarga.ERROR || estado == EstadoCarga.CONFLICTO) {
                if (!etiquetaError.getStyleClass().contains("texto-error")) {
                    etiquetaError.getStyleClass().add("texto-error");
                }
                etiquetaRequestId.setText(viewmodel.getRequestId().isEmpty()
                        ? "" : "request-id: " + viewmodel.getRequestId());
            } else {
                etiquetaRequestId.setText("");
            }
        });

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
