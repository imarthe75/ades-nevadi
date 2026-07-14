package mx.ades.modules.conducta.infrastructure.outbound.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.ades.modules.conducta.domain.port.in.CrearPlanMejoraUseCase;
import mx.ades.modules.conducta.domain.port.out.PlanMejoraRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador JDBC que implementa {@link PlanMejoraRepositoryPort}.
 * <p>Persiste planes de mejora en {@code ades_planes_mejora} serializando los compromisos
 * como JSONB. El estado inicial es siempre ACTIVO (mismo valor que el DEFAULT de la
 * columna en BD — ver {@code 034_sanciones_planes_mejora.sql}).</p>
 *
 * @author ADES
 * @since 2026
 */
@Component
public class ConductaPlanPersistenceAdapter implements PlanMejoraRepositoryPort {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ConductaPlanPersistenceAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public boolean existeActivo(UUID reporteId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_planes_mejora WHERE reporte_conducta_id = ? AND is_active = TRUE",
                Integer.class, reporteId);
        return count != null && count > 0;
    }

    @Override
    public UUID guardar(CrearPlanMejoraUseCase.Command cmd) {
        UUID id = UUID.randomUUID();
        // CRITICO (hallazgo de auditoría): el estado inicial estaba hardcodeado como
        // 'BORRADOR', valor que NUNCA existió en el CHECK real de
        // ades_planes_mejora.estado (solo ACTIVO/EN_PROCESO/CUMPLIDO/INCUMPLIDO/
        // CANCELADO — ver 034_sanciones_planes_mejora.sql). Esto rompía TODO INSERT
        // en este endpoint con una violación de CHECK a nivel BD.
        jdbc.update(
                "INSERT INTO ades_planes_mejora " +
                "(id, reporte_conducta_id, estudiante_id, ciclo_escolar_id, elaborado_por_id, " +
                " objetivo_general, compromisos_alumno, compromisos_padre, compromisos_escuela, " +
                " fecha_primer_seguimiento, estado, is_active, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, 'ACTIVO', TRUE, ?, ?)",
                id,
                cmd.reporteId(),
                cmd.estudianteId(),
                cmd.cicloEscolarId(),
                cmd.elaboradoPorId(),
                cmd.objetivoGeneral(),
                toJson(cmd.compromisosAlumno()),
                toJson(cmd.compromisosPadre()),
                toJson(cmd.compromisosEscuela()),
                cmd.fechaPrimerSeguimiento() != null ? cmd.fechaPrimerSeguimiento() : LocalDate.now().plusWeeks(2),
                cmd.username(),
                cmd.username()
        );
        return id;
    }

    private String toJson(List<Map<String, Object>> list) {
        if (list == null) return "[]";
        try {
            return mapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
