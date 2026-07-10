package mx.ades.modules.medico.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SaludQueryService {

    private final JdbcTemplate jdbc;

    public SaludQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> medicamentos(UUID alumnoId, boolean soloVigentes) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, nombre_medicamento, dosis, frecuencia, horario, via_administracion, " +
                "prescrito_por, fecha_inicio, fecha_fin, observaciones, is_active " +
                "FROM ades_medicamentos_alumno WHERE alumno_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(alumnoId);

        if (soloVigentes) sql.append(" AND is_active = TRUE AND (fecha_fin IS NULL OR fecha_fin >= CURRENT_DATE)");
        sql.append(" ORDER BY fecha_inicio DESC NULLS LAST");

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> psicosocial(UUID alumnoId, int skip, int limit) {
        return jdbc.queryForList(
                "SELECT id, tipo_atencion, motivo, observaciones, estrategias_sugeridas, " +
                "requiere_derivacion, derivado_a, proxima_sesion, " +
                "fecha_creacion, usuario_creacion AS especialista " +
                "FROM ades_seguimiento_psicosocial WHERE alumno_id = ? " +
                "ORDER BY fecha_creacion DESC LIMIT ? OFFSET ?",
                alumnoId, limit, skip);
    }

    public List<Map<String, Object>> tutorias(UUID alumnoId, String tipoTutoria, int skip, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT t.id, t.tipo_tutoria, t.tema, t.descripcion, t.duracion_minutos, " +
                "t.acuerdos, t.proxima_sesion, t.requiere_seguimiento, " +
                "t.fecha_creacion, t.usuario_creacion AS tutor, " +
                "p.nombre || ' ' || p.apellido_paterno AS alumno, e.matricula AS numero_control " +
                "FROM ades_tutorias t " +
                "JOIN ades_estudiantes e ON e.id = t.alumno_id " +
                "JOIN ades_personas p ON p.id = e.persona_id WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (alumnoId != null) { sql.append(" AND t.alumno_id = ?"); params.add(alumnoId); }
        if (tipoTutoria != null && !tipoTutoria.isBlank()) { sql.append(" AND t.tipo_tutoria = ?"); params.add(tipoTutoria); }
        sql.append(" ORDER BY t.fecha_creacion DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(skip);

        return jdbc.queryForList(sql.toString(), params.toArray());
    }
}
