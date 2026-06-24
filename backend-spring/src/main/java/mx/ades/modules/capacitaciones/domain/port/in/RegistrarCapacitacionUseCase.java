package mx.ades.modules.capacitaciones.domain.port.in;

import mx.ades.modules.capacitaciones.domain.model.AreaFormacion;
import mx.ades.modules.capacitaciones.domain.model.ModalidadCapacitacion;
import mx.ades.modules.capacitaciones.domain.model.TipoCertificacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Puerto de entrada: define el contrato para registrar una capacitación o certificación
 * docente en el dominio de capacitaciones.
 *
 * @author ADES
 * @since 2026
 */
public interface RegistrarCapacitacionUseCase {

    record Command(
            UUID docenteId,
            String nombre,
            TipoCertificacion tipo,
            ModalidadCapacitacion modalidad,
            AreaFormacion area,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            BigDecimal duracionHrs,
            String institucion,
            String folioCertificado,
            String certificadoUrl,
            String usuarioCreacion
    ) {
        public Command {
            if (docenteId == null)
                throw new IllegalArgumentException("docente_id es requerido");
            if (nombre == null || nombre.isBlank())
                throw new IllegalArgumentException("nombre es requerido");
            if (tipo == null)
                throw new IllegalArgumentException("tipo_certificacion es requerido");
            if (modalidad == null)
                throw new IllegalArgumentException("modalidad es requerida");
            if (fechaInicio == null)
                throw new IllegalArgumentException("fecha_inicio es requerida");
            if (fechaFin == null)
                throw new IllegalArgumentException("fecha_fin es requerida");
            if (fechaFin.isBefore(fechaInicio))
                throw new IllegalArgumentException("fecha_fin debe ser >= fecha_inicio");
            if (duracionHrs == null || duracionHrs.compareTo(BigDecimal.ZERO) <= 0)
                throw new IllegalArgumentException("duracion_hrs debe ser positiva");
        }
    }

    UUID registrar(Command cmd);
}
