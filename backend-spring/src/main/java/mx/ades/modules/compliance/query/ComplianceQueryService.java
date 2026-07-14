package mx.ades.modules.compliance.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Servicio de lectura CQRS para el módulo compliance.
 * <p>Expone consultas de historial de logins, estadísticas globales del sistema,
 * normatividad vigente, retenciones activas y alertas de cumplimiento.</p>
 *
 * @author ADES
 * @since 2026
 */
@Service
public class ComplianceQueryService {

    private final JdbcTemplate jdbc;

    public ComplianceQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> historialLogins(String usuario, String desde, String hasta,
                                                      int skip, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, usuario_id, ip_origen, user_agent, exitoso, motivo_fallo, fecha_login " +
                "FROM ades_log_autenticacion WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (usuario != null && !usuario.isBlank()) {
            sql.append(" AND usuario_id ILIKE ?");
            params.add("%" + usuario + "%");
        }
        if (desde != null && !desde.isBlank()) {
            sql.append(" AND fecha_login >= ?::timestamptz");
            params.add(desde);
        }
        if (hasta != null && !hasta.isBlank()) {
            sql.append(" AND fecha_login <= ?::timestamptz");
            params.add(hasta);
        }
        sql.append(" ORDER BY fecha_login DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(skip);

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public Map<String, Object> estadisticasSistema(UUID plantelId, UUID cicloId) {
        // ades_grupos NO tiene columna plantel_id — el plantel del alumno se
        // obtiene de ades_estudiantes.plantel_id directamente.
        String pf = plantelId != null ? " AND e2.plantel_id = ?" : "";
        String cf = cicloId != null ? " AND i.ciclo_escolar_id = ?" : "";

        List<Object> pList = new ArrayList<>();
        if (plantelId != null) pList.add(plantelId);
        if (cicloId != null) pList.add(cicloId);
        Object[] params = pList.toArray();

        Map<String, Object> alumnos = jdbc.queryForMap(
                "SELECT COUNT(DISTINCT i.estudiante_id) AS total_alumnos, COUNT(DISTINCT e2.plantel_id) AS planteles " +
                "FROM ades_inscripciones i JOIN ades_estudiantes e2 ON e2.id = i.estudiante_id " +
                "WHERE i.is_active = TRUE" + pf + cf, params);

        Map<String, Object> asistencia = jdbc.queryForMap(
                "SELECT ROUND(100.0 * SUM(CASE WHEN a.estatus_asistencia = 'PRESENTE' THEN 1 ELSE 0 END) / NULLIF(COUNT(a.id), 0), 2) AS porcentaje_asistencia " +
                "FROM ades_asistencias a JOIN ades_inscripciones i ON i.estudiante_id = a.estudiante_id " +
                "JOIN ades_estudiantes e2 ON e2.id = i.estudiante_id WHERE i.is_active = TRUE" + pf + cf, params);

        Map<String, Object> calificaciones = jdbc.queryForMap(
                "SELECT ROUND(AVG(cp.calificacion_final)::numeric, 2) AS promedio_general " +
                "FROM ades_calificaciones_periodo cp " +
                "JOIN ades_inscripciones i ON i.estudiante_id = cp.estudiante_id AND i.grupo_id = cp.grupo_id " +
                "JOIN ades_estudiantes e2 ON e2.id = i.estudiante_id " +
                "WHERE i.is_active = TRUE AND cp.is_active = TRUE AND cp.calificacion_final IS NOT NULL" + pf + cf, params);

        List<Object> conductaParams = plantelId != null ? List.of(plantelId) : List.of();
        String pfc = plantelId != null ? " AND e2.plantel_id = ?" : "";
        Map<String, Object> conducta = jdbc.queryForMap(
                "SELECT COUNT(*) AS total_incidentes, " +
                "SUM(CASE WHEN ic.tipo_falta IN ('GRAVE','MUY_GRAVE') THEN 1 ELSE 0 END) AS graves " +
                "FROM ades_reportes_conducta ic " +
                "JOIN ades_estudiantes e2 ON e2.id = ic.estudiante_id WHERE ic.is_active = TRUE" + pfc,
                conductaParams.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("alumnos", alumnos);
        result.put("asistencia", asistencia);
        result.put("calificaciones", calificaciones);
        result.put("conducta", conducta);
        return result;
    }

    public List<Map<String, Object>> normatividad(String tipo, boolean vigente) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, nombre, tipo, descripcion, fecha_vigencia_inicio, fecha_vigencia_fin, " +
                "url_documento, aplica_primaria, aplica_secundaria, aplica_preparatoria " +
                "FROM ades_normatividad WHERE is_active = TRUE");
        List<Object> params = new ArrayList<>();

        if (tipo != null && !tipo.isBlank()) { sql.append(" AND tipo = ?"); params.add(tipo); }
        if (vigente) sql.append(" AND (fecha_vigencia_fin IS NULL OR fecha_vigencia_fin >= CURRENT_DATE)");
        sql.append(" ORDER BY tipo, nombre");

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> retenciones(UUID plantelId, String tipo, boolean activas,
                                                   int skip, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT r.id, r.tipo_retencion, r.motivo, r.fecha_inicio, r.fecha_fin, " +
                "r.acciones_requeridas, r.is_active, " +
                "p.nombre || ' ' || p.apellido_paterno AS alumno, e.matricula AS numero_control, g.nombre_grupo " +
                "FROM ades_retenciones r " +
                "JOIN ades_estudiantes e ON e.id = r.alumno_id " +
                "JOIN ades_personas p ON p.id = e.persona_id " +
                "LEFT JOIN ades_inscripciones i ON i.estudiante_id = e.id AND i.is_active = TRUE " +
                "LEFT JOIN ades_grupos g ON g.id = i.grupo_id WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (activas) sql.append(" AND r.is_active = TRUE AND (r.fecha_fin IS NULL OR r.fecha_fin >= CURRENT_DATE)");
        if (tipo != null && !tipo.isBlank()) { sql.append(" AND r.tipo_retencion = ?"); params.add(tipo); }
        if (plantelId != null) { sql.append(" AND e.plantel_id = ?"); params.add(plantelId); }
        sql.append(" ORDER BY r.fecha_inicio DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(skip);

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> alertas(UUID plantelId, String severidad, String estado,
                                              int skip, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, tipo_alerta, descripcion, severidad, estado, requiere_accion, " +
                "fecha_creacion, usuario_creacion FROM ades_alertas_cumplimiento WHERE estado = ?");
        List<Object> params = new ArrayList<>();
        params.add(estado);

        if (plantelId != null) { sql.append(" AND plantel_id = ?"); params.add(plantelId); }
        if (severidad != null && !severidad.isBlank()) { sql.append(" AND severidad = ?"); params.add(severidad); }
        sql.append(" ORDER BY CASE severidad WHEN 'CRITICA' THEN 1 WHEN 'ALTA' THEN 2 WHEN 'MEDIA' THEN 3 ELSE 4 END, fecha_creacion DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(skip);

        return jdbc.queryForList(sql.toString(), params.toArray());
    }
}
