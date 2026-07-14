package mx.ades.modules.planes_estudio;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    @Transactional
    public UUID crear(UUID estudianteId, UUID grupoId, String motivo, List<Map<String, Object>> materias) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO ades_planes_estudio_alt (id, estudiante_id, grupo_id, motivo)
            VALUES (?, ?, ?, ?)
            """, id, estudianteId, grupoId, motivo);

        for (Map<String, Object> m : materias) {
            // materia_id es NOT NULL en ades_planes_estudio_alt_materias; sin esta validación,
            // UUID.fromString(null) lanza NullPointerException y cae en el 500 genérico
            // en vez de un 400 claro (mismo patrón detectado en RegistrarRetencionUseCase).
            Object materiaIdRaw = m.get("materia_id");
            if (materiaIdRaw == null || materiaIdRaw.toString().isBlank())
                throw new IllegalArgumentException("materia_id es obligatorio para cada materia del plan alternativo");
            UUID materiaId = UUID.fromString(materiaIdRaw.toString());
            Double horas = m.get("horas_semana") != null ? ((Number) m.get("horas_semana")).doubleValue() : null;
            jdbc.update("""
                INSERT INTO ades_planes_estudio_alt_materias (plan_alt_id, materia_id, horas_semana)
                VALUES (?, ?, ?)
                """, id, materiaId, horas);
        }
        return id;
    }

    @Transactional
    public void eliminar(UUID id) {
        int rows = jdbc.update("UPDATE ades_planes_estudio_alt SET is_active = FALSE WHERE id = ? AND is_active = TRUE", id);
        if (rows == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan alternativo no encontrado");
    }
}
