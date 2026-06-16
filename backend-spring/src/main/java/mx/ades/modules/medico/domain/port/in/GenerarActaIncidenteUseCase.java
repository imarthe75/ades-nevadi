package mx.ades.modules.medico.domain.port.in;

import java.util.UUID;

public interface GenerarActaIncidenteUseCase {

    record Command(
            UUID incidenteId, String descripcionDetallada, String testigos,
            String medidasTomadas, Boolean requirioTraslado, String hospitalDestino,
            Boolean notificadoFamilia, String firmaResponsable,
            Integer nivelAcceso, String usuario) {
        public Command {
            if (incidenteId == null) throw new IllegalArgumentException("incidente_id es requerido");
            if (nivelAcceso == null || nivelAcceso > 3)
                throw new IllegalArgumentException("Se requiere nivel Médico/Coordinador o superior");
        }
    }

    UUID generar(Command cmd);
}
