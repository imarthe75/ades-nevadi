package mx.ades.modules.planes_estudio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Escritura de planes de estudio alternativos/reducidos (NEE) — AC-014. */
@Component
public class PlanAltWriteService {

    private final JdbcTemplate jdbc;

    public PlanAltWriteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID crear(UUID estudianteId, UUID grupoId, String motivo, List<Map<String, Object>> materias) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO ades_planes_estudio_alt (id, estudiante_id, grupo_id, motivo)
            VALUES (?, ?, ?, ?)
            """, id, estudianteId, grupoId, motivo);

        for (Map<String, Object> m : materias) {
            UUID materiaId = UUID.fromString((String) m.get("materia_id"));
            Double horas = m.get("horas_semana") != null ? ((Number) m.get("horas_semana")).doubleValue() : null;
            jdbc.update("""
                INSERT INTO ades_planes_estudio_alt_materias (plan_alt_id, materia_id, horas_semana)
                VALUES (?, ?, ?)
                """, id, materiaId, horas);
        }
        return id;
    }

    public void eliminar(UUID id) {
        jdbc.update("UPDATE ades_planes_estudio_alt SET is_active = FALSE WHERE id = ?", id);
    }
}
