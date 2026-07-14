package mx.ades.modules.horarios.domain.port.in;

import java.util.UUID;

/**
 * Puerto de entrada: contrato para crear una entrada de horario para un grupo.
 * El origen puede ser MANUAL o ASC (importado desde aSc TimeTables via XML).
 *
 * @author ADES
 * @since 2026
 */
public interface CrearHorarioUseCase {

    record Command(
            UUID grupoId, UUID materiaId, UUID profesorId, UUID aulaId,
            UUID cicloEscolarId, Integer diaSemana,
            String horaInicio, String horaFin, String origen, String usuario) {
        /**
         * ades_horarios tiene NOT NULL sin default en: ciclo_escolar_id, dia_semana,
         * grupo_id, hora_fin, hora_inicio, materia_id, profesor_id. Antes solo se
         * validaban grupoId y el rango de diaSemana (y encima con un bug: si diaSemana
         * llegaba null el chequeo de rango se saltaba silenciosamente) — el resto caía
         * directo en el INSERT y producía DataIntegrityViolationException -> 409 engañoso.
         */
        public Command {
            if (grupoId == null) throw new IllegalArgumentException("grupo_id es requerido");
            if (materiaId == null) throw new IllegalArgumentException("materia_id es requerido");
            if (profesorId == null) throw new IllegalArgumentException("profesor_id es requerido");
            if (cicloEscolarId == null) throw new IllegalArgumentException("ciclo_escolar_id es requerido");
            if (diaSemana == null) throw new IllegalArgumentException("dia_semana es requerido");
            if (diaSemana < 1 || diaSemana > 5)
                throw new IllegalArgumentException("dia_semana debe estar entre 1 (Lunes) y 5 (Viernes)");
            if (horaInicio == null || horaInicio.isBlank()) throw new IllegalArgumentException("hora_inicio es requerido");
            if (horaFin == null || horaFin.isBlank()) throw new IllegalArgumentException("hora_fin es requerido");
            if (origen == null || origen.isBlank()) origen = "MANUAL";
        }
    }

    UUID crear(Command cmd);
}
