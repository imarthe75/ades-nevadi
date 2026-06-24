package mx.ades.modules.conducta.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Servicio de lectura CQRS para el módulo conducta.
 * <p>Expone listados de reportes con filtros en cascada plantel/nivel/grado/grupo/estudiante,
 * historial conductual completo del alumno y detalle con sanción, plan de mejora y seguimientos.</p>
 *
 * @author ADES
 * @since 2026
 */
@Service
public class ConductaQueryService {

    private final JdbcTemplate jdbc;

    public ConductaQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> listar(UUID estudianteId, UUID grupoId, String tipoFalta,
                                             Boolean requiereSeguimiento, int pagina, int porPagina) {
        return listar(null, null, null, grupoId, estudianteId, tipoFalta, requiereSeguimiento, pagina, porPagina);
    }

    public List<Map<String, Object>> listar(UUID plantelId, UUID nivelId, UUID gradoId, UUID grupoId,
                                             UUID estudianteId, String tipoFalta, Boolean requiereSeguimiento,
                                             int pagina, int porPagina) {
        StringBuilder q = new StringBuilder(
                "SELECT rc.id, rc.estudiante_id, rc.grupo_id, rc.reportado_por_id, rc.fecha_reporte, " +
                "rc.tipo_falta, rc.descripcion, rc.medida_aplicada, rc.requiere_seguimiento, " +
                "p.nombre || ' ' || p.apellido_paterno AS nombre_alumno, " +
                "u.nombre_usuario AS reportado_por_nombre " +
                "FROM ades_reportes_conducta rc " +
                "JOIN ades_estudiantes e ON e.id = rc.estudiante_id " +
                "JOIN ades_personas p ON p.id = e.persona_id " +
                "JOIN ades_usuarios u ON u.id = rc.reportado_por_id " +
                "LEFT JOIN ades_grupos g ON g.id = rc.grupo_id " +
                "LEFT JOIN ades_grados gr ON gr.id = g.grado_id " +
                "WHERE rc.is_active = TRUE ");

        List<Object> params = new ArrayList<>();
        if (estudianteId != null) { q.append("AND rc.estudiante_id = ? "); params.add(estudianteId); }

        if (grupoId != null) {
            q.append("AND rc.grupo_id = ? ");
            params.add(grupoId);
        } else if (gradoId != null) {
            q.append("AND g.grado_id IN (SELECT id FROM ades_grados WHERE (numero_grado, nivel_educativo_id) = (SELECT numero_grado, nivel_educativo_id FROM ades_grados WHERE id = ?)) ");
            params.add(gradoId);
        } else if (nivelId != null) {
            q.append("AND gr.nivel_educativo_id = ? ");
            params.add(nivelId);
        }

        if (plantelId != null) {
            q.append("AND (gr.plantel_id = ? OR e.plantel_id = ?) ");
            params.add(plantelId);
            params.add(plantelId);
        }

        if (tipoFalta != null && !tipoFalta.isBlank()) { q.append("AND rc.tipo_falta = ? "); params.add(tipoFalta.toUpperCase()); }
        if (requiereSeguimiento != null) { q.append("AND rc.requiere_seguimiento = ? "); params.add(requiereSeguimiento); }

        q.append("ORDER BY rc.fecha_reporte DESC LIMIT ? OFFSET ?");
        params.add(porPagina);
        params.add((pagina - 1) * porPagina);

        return jdbc.queryForList(q.toString(), params.toArray());
    }

    public List<Map<String, Object>> historial(UUID estudianteId) {
        String sql = "SELECT rc.id, rc.fecha_reporte, rc.tipo_falta, rc.descripcion, " +
                "rc.medida_aplicada, rc.requiere_seguimiento, " +
                "sd.id AS sancion_id, sd.tipo_sancion, sd.estado AS estado_sancion, " +
                "sd.fecha_sancion, sd.notificado_padres, " +
                "pm.id AS plan_id, pm.estado AS estado_plan, pm.fecha_elaboracion, pm.objetivo_general " +
                "FROM ades_reportes_conducta rc " +
                "LEFT JOIN ades_sanciones_disciplinarias sd ON sd.reporte_conducta_id = rc.id AND sd.is_active = TRUE " +
                "LEFT JOIN ades_planes_mejora pm ON pm.reporte_conducta_id = rc.id AND pm.is_active = TRUE " +
                "WHERE rc.estudiante_id = ? AND rc.is_active = TRUE " +
                "ORDER BY rc.fecha_reporte DESC";

        return jdbc.queryForList(sql, estudianteId);
    }

    public Map<String, Object> detalleCompleto(UUID id) {
        List<Map<String, Object>> reportes = jdbc.queryForList(
                "SELECT rc.*, p.nombre || ' ' || p.apellido_paterno AS nombre_alumno, est.matricula " +
                "FROM ades_reportes_conducta rc " +
                "JOIN ades_estudiantes est ON est.id = rc.estudiante_id " +
                "JOIN ades_personas p ON p.id = est.persona_id " +
                "WHERE rc.id = ? AND rc.is_active = TRUE", id);

        if (reportes.isEmpty()) return null;
        Map<String, Object> rc = reportes.get(0);

        List<Map<String, Object>> sanciones = jdbc.queryForList(
                "SELECT sd.*, u.nombre_usuario AS autorizado_por_nombre " +
                "FROM ades_sanciones_disciplinarias sd " +
                "JOIN ades_usuarios u ON u.id = sd.autorizado_por_id " +
                "WHERE sd.reporte_conducta_id = ? AND sd.is_active = TRUE LIMIT 1", id);

        Map<String, Object> sancion = sanciones.isEmpty() ? null : sanciones.get(0);

        List<Map<String, Object>> planes = jdbc.queryForList(
                "SELECT pm.*, u.nombre_usuario AS elaborado_por_nombre " +
                "FROM ades_planes_mejora pm " +
                "JOIN ades_usuarios u ON u.id = pm.elaborado_por_id " +
                "WHERE pm.reporte_conducta_id = ? AND pm.is_active = TRUE LIMIT 1", id);

        Map<String, Object> plan = planes.isEmpty() ? null : planes.get(0);

        List<Map<String, Object>> seguimientos = Collections.emptyList();
        if (plan != null) {
            UUID planId = (UUID) plan.get("id");
            seguimientos = jdbc.queryForList(
                    "SELECT sp.*, u.nombre_usuario AS registrado_por_nombre " +
                    "FROM ades_seguimiento_plan sp " +
                    "JOIN ades_usuarios u ON u.id = sp.registrado_por_id " +
                    "WHERE sp.plan_mejora_id = ? AND sp.is_active = TRUE " +
                    "ORDER BY sp.fecha_seguimiento DESC", planId);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("reporte", rc);
        result.put("sancion", sancion);
        result.put("plan_mejora", plan);
        result.put("seguimientos", seguimientos);
        return result;
    }
}
