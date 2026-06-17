package mx.ades.modules.movilidad.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MovilidadQueryService {

    private final JdbcTemplate jdbc;

    public MovilidadQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> historial(UUID estudianteId) {
        List<Map<String, Object>> cambios = jdbc.queryForList(
                "SELECT cg.fecha_cambio, cg.motivo, go.nombre_grupo AS grupo_origen, " +
                "gd.nombre_grupo AS grupo_destino, 'CAMBIO_GRUPO' AS tipo " +
                "FROM ades_cambios_grupo cg " +
                "JOIN ades_grupos go ON go.id = cg.grupo_origen_id " +
                "JOIN ades_grupos gd ON gd.id = cg.grupo_destino_id " +
                "WHERE cg.estudiante_id = ? ORDER BY cg.fecha_cambio DESC",
                estudianteId);

        List<Map<String, Object>> bajas = jdbc.queryForList(
                "SELECT tipo_baja, motivo, fecha_efectiva, fecha_reingreso, " +
                "plantel_destino, observaciones, is_active AS activa " +
                "FROM ades_bajas WHERE estudiante_id = ? ORDER BY fecha_efectiva DESC",
                estudianteId);

        return Map.of("cambios_grupo", cambios, "bajas", bajas);
    }

    public List<Map<String, Object>> listarBajas(UUID plantelId, String tipoBaja,
                                                   boolean soloActivas, int skip, int limit) {
        StringBuilder q = new StringBuilder(
                "SELECT b.id, b.estudiante_id, b.tipo_baja, b.motivo, b.fecha_efectiva, b.fecha_reingreso, " +
                "b.plantel_destino, b.is_active, e.matricula AS numero_control, " +
                "COALESCE(p.nombre_social, p.nombre) || ' ' || p.apellido_paterno AS nombre_alumno, " +
                "g.nombre_grupo, pl.nombre_plantel " +
                "FROM ades_bajas b " +
                "JOIN ades_estudiantes e ON e.id = b.estudiante_id " +
                "JOIN ades_personas p ON p.id = e.persona_id " +
                "LEFT JOIN ades_inscripciones i ON i.id = b.inscripcion_id " +
                "LEFT JOIN ades_grupos g ON g.id = i.grupo_id " +
                "LEFT JOIN ades_planteles pl ON pl.id = e.plantel_id " +
                "WHERE 1=1 ");

        List<Object> params = new ArrayList<>();
        if (soloActivas) q.append("AND b.is_active = TRUE ");
        if (tipoBaja != null && !tipoBaja.isBlank()) { q.append("AND b.tipo_baja = ? "); params.add(tipoBaja.toUpperCase()); }
        if (plantelId != null) { q.append("AND e.plantel_id = ? "); params.add(plantelId); }

        q.append("ORDER BY b.fecha_efectiva DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(skip);

        return jdbc.queryForList(q.toString(), params.toArray());
    }

    public List<Map<String, Object>> listarCambiosGrupo(UUID estudianteId, UUID plantelId,
                                                          UUID plantelIdFiltroUser, int skip, int limit) {
        StringBuilder q = new StringBuilder(
                "SELECT cg.id, cg.fecha_cambio, cg.motivo, " +
                "COALESCE(p.nombre_social, p.nombre) || ' ' || p.apellido_paterno AS nombre_alumno, " +
                "e.matricula AS numero_control, cg.estudiante_id, " +
                "go.nombre_grupo AS grupo_origen, gd.nombre_grupo AS grupo_destino, " +
                "e.plantel_id " +
                "FROM ades_cambios_grupo cg " +
                "JOIN ades_estudiantes e ON e.id = cg.estudiante_id " +
                "JOIN ades_personas p ON p.id = e.persona_id " +
                "JOIN ades_grupos go ON go.id = cg.grupo_origen_id " +
                "JOIN ades_grupos gd ON gd.id = cg.grupo_destino_id " +
                "WHERE 1=1 ");

        List<Object> params = new ArrayList<>();
        if (estudianteId != null) { q.append("AND cg.estudiante_id = ? "); params.add(estudianteId); }
        if (plantelId != null) { q.append("AND e.plantel_id = ? "); params.add(plantelId); }
        if (plantelIdFiltroUser != null) { q.append("AND e.plantel_id = ? "); params.add(plantelIdFiltroUser); }

        q.append("ORDER BY cg.fecha_cambio DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(skip);

        return jdbc.queryForList(q.toString(), params.toArray());
    }
}
