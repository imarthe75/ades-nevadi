package mx.ades.modules.licencias.domain.port.in;

import mx.ades.modules.licencias.domain.model.TipoLicencia;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para solicitar una licencia para el personal.
 * Valida que fecha_fin sea mayor o igual a fecha_inicio.
 *
 * @author ADES
 * @since 2026
 */
public interface SolicitarLicenciaUseCase {

    record Command(
            UUID personalId,
            TipoLicencia tipoLicencia,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            String motivo,
            UUID sustitutoId,
            boolean conGoceSueldo,
            String usuarioCreacion
    ) {
        public Command {
            if (personalId == null)
                throw new IllegalArgumentException("personal_id es requerido");
            if (tipoLicencia == null)
                throw new IllegalArgumentException("tipo_licencia es requerido");
            if (fechaInicio == null)
                throw new IllegalArgumentException("fecha_inicio es requerida");
            if (fechaFin == null)
                throw new IllegalArgumentException("fecha_fin es requerida");
            if (fechaFin.isBefore(fechaInicio))
                throw new IllegalArgumentException("fecha_fin debe ser >= fecha_inicio");
        }
    }

    UUID solicitar(Command cmd);
}
