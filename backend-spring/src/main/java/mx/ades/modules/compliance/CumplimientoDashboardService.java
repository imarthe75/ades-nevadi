package mx.ades.modules.compliance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * AD-014: agrega el estado de las piezas de cumplimiento SEP/UAEMEX ya
 * existentes (calificaciones capturadas, alertas de compliance pendientes,
 * normatividad vigente) en una sola vista de solo lectura — no genera datos
 * nuevos, solo consolida lo que ya se produce en otros módulos.
 */
@Service
public class CumplimientoDashboardService {

    private final JdbcTemplate jdbc;

    public CumplimientoDashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> resumen() {
        Map<String, Object> calificaciones = jdbc.queryForMap("""
            SELECT
                COUNT(DISTINCT i.estudiante_id) AS total_alumnos_activos,
                COUNT(DISTINCT i.id) FILTER (WHERE cp.calificacion_final IS NOT NULL) AS con_calificacion_capturada
            FROM ades_inscripciones i
            LEFT JOIN ades_calificaciones_periodo cp
                ON cp.estudiante_id = i.estudiante_id AND cp.grupo_id = i.grupo_id AND cp.is_active = TRUE
            WHERE i.is_active = TRUE
            """);

        List<Map<String, Object>> alertasPendientes = jdbc.queryForList("""
            SELECT tipo_alerta, severidad, descripcion
            FROM ades_alertas_cumplimiento
            WHERE estado = 'PENDIENTE'
            ORDER BY severidad, fecha_creacion DESC
            LIMIT 20
            """);

        List<Map<String, Object>> normativasVigentes = jdbc.queryForList("""
            SELECT nombre, tipo, fecha_vigencia_inicio, fecha_vigencia_fin
            FROM ades_normatividad
            WHERE is_active = TRUE AND (fecha_vigencia_fin IS NULL OR fecha_vigencia_fin >= CURRENT_DATE)
            ORDER BY tipo, nombre
            """);

        Long claves911Pendientes = jdbc.queryForObject("""
            SELECT COUNT(*) FROM ades_plantel_nivel_clave
            WHERE tipo_clave = 'INCORPORACION_UAEMEX' AND clave IS NULL
            """, Long.class);

        return Map.of(
                "calificaciones", calificaciones,
                "alertas_pendientes", alertasPendientes,
                "total_alertas_pendientes", alertasPendientes.size(),
                "normativas_vigentes", normativasVigentes,
                "claves_uaemex_pendientes", claves911Pendientes != null ? claves911Pendientes : 0L
        );
    }
}
