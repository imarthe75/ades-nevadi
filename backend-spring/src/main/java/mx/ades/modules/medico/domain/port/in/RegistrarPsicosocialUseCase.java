package mx.ades.modules.medico.domain.port.in;

import java.time.LocalDate;
import java.util.UUID;

public interface RegistrarPsicosocialUseCase {

    record Command(
            UUID alumnoId, String tipoAtencion, String motivo, String observaciones,
            String estrategiasSugeridas, Boolean requiereDerivacion, String derivadoA,
            LocalDate proximaSesion, Integer nivelAcceso, String usuario) {
        public Command {
            if (alumnoId == null) throw new IllegalArgumentException("alumno_id es requerido");
            // tipo_atencion, motivo y observaciones son NOT NULL en ades_seguimiento_psicosocial
            // (sin default); antes de este fix faltaban aquí y el INSERT fallaba con
            // DataIntegrityViolationException (409 genérico en vez de un mensaje claro).
            if (tipoAtencion == null || tipoAtencion.isBlank())
                throw new IllegalArgumentException("tipo_atencion es requerido");
            if (motivo == null || motivo.isBlank())
                throw new IllegalArgumentException("motivo es requerido");
            if (observaciones == null || observaciones.isBlank())
                throw new IllegalArgumentException("observaciones es requerido");
            if (nivelAcceso == null || nivelAcceso > 3)
                throw new IllegalArgumentException("Se requiere nivel Médico/Coordinador o superior");
        }
    }

    UUID registrar(Command cmd);
}
