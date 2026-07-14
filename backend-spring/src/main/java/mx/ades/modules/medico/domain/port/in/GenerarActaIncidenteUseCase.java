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
            // descripcion_detallada y medidas_tomadas son NOT NULL en ades_actas_incidente_medico
            // (sin default); antes de este fix faltaban aquí y el INSERT fallaba con
            // DataIntegrityViolationException (409 genérico en vez de un mensaje claro).
            if (descripcionDetallada == null || descripcionDetallada.isBlank())
                throw new IllegalArgumentException("descripcion_detallada es requerida");
            if (medidasTomadas == null || medidasTomadas.isBlank())
                throw new IllegalArgumentException("medidas_tomadas es requerida");
            if (nivelAcceso == null || nivelAcceso > 3)
                throw new IllegalArgumentException("Se requiere nivel Médico/Coordinador o superior");
        }
    }

    UUID generar(Command cmd);
}
