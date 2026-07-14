package mx.ades.modules.aulas.domain.port.in;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Puerto de entrada: define el contrato para crear un aula nueva en el dominio de aulas.
 *
 * @author ADES
 * @since 2026
 */
public interface CrearAulaUseCase {

    /** Valores permitidos por el CHECK ades_aulas_tipo_aula_check en ades_aulas. */
    Set<String> TIPOS_AULA_VALIDOS = Set.of(
            "AULA", "SALON", "LABORATORIO", "COMPUTO", "TALLER", "AUDITORIO",
            "BIBLIOTECA", "GIMNASIO", "CANCHA", "AREA_DEPORTIVA", "SALA_MAESTROS",
            "DIRECCION", "OTRO");

    /** Valores permitidos por el CHECK chk_aula_estado en ades_aulas. */
    Set<String> ESTADOS_AULA_VALIDOS = Set.of(
            "ACTIVA", "EN_MANTENIMIENTO", "INHABILITADA", "FUERA_DE_SERVICIO");

    record Command(
            String nombreAula,
            UUID plantelId,
            String tipoAula,
            Integer capacidadAlumnos
    ) {
        public Command {
            if (nombreAula == null || nombreAula.isBlank())
                throw new IllegalArgumentException("El nombre del aula es requerido");
            if (plantelId == null)
                throw new IllegalArgumentException("El plantel es requerido");
            if (tipoAula != null && !tipoAula.isBlank() && !TIPOS_AULA_VALIDOS.contains(tipoAula))
                throw new IllegalArgumentException("tipo_aula inválido: " + tipoAula);
        }
    }

    Map<String, Object> crear(Command cmd);
}
