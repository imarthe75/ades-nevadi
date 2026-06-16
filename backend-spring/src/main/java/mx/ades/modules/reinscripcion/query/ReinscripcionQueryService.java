package mx.ades.modules.reinscripcion.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ReinscripcionQueryService {

    private final JdbcTemplate jdbc;

    public ReinscripcionQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> getEstado(UUID cicloDestinoId, String estado,
                                          UUID plantelId, int page, int porPagina,
                                          mx.ades.security.AdesUser user) {
        StringBuilder sql = new StringBuilder(
            "SELECT rc.id, rc.estudiante_id, rc.estado, rc.ciclo_origen_id, rc.ciclo_destino_id, " +
            "       rc.aprobado_por, rc.razon_rechazo, rc.fecha_aprobacion, " +
            "       CONCAT(p.nombre,' ',p.apellido_paterno) AS nombre_estudiante, " +
            "       p.curp, e.matricula, pl.nombre_plantel " +
            "FROM ades_reinscripcion_ciclo rc " +
            "JOIN ades_inscripciones i ON i.estudiante_id = rc.estudiante_id AND i.ciclo_origen_id = rc.ciclo_origen_id AND i.is_active = TRUE " +
            "JOIN ades_estudiantes e ON e.id = rc.estudiante_id " +
            "JOIN ades_personas p ON p.id = e.persona_id " +
            "JOIN ades_grupos g ON g.id = i.grupo_id " +
            "JOIN ades_planteles pl ON pl.id = g.plantel_id " +
            "WHERE rc.ciclo_destino_id = ? AND rc.is_active = TRUE ");

        List<Object> params = new ArrayList<>();
        params.add(cicloDestinoId);

        if (estado != null && !estado.isBlank()) {
            sql.append("AND rc.estado = ? ");
            params.add(estado);
        }
        if (plantelId != null) {
            sql.append("AND pl.id = ? ");
            params.add(plantelId);
        }
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3 && user.getPlantelId() != null) {
            sql.append("AND pl.id = ? ");
            params.add(user.getPlantelId());
        }

        int offset = (page - 1) * porPagina;
        String countSql = "SELECT COUNT(*) FROM (" + sql + ") ct";
        Integer total = jdbc.queryForObject(countSql, Integer.class, params.toArray());

        sql.append("ORDER BY p.apellido_paterno LIMIT ? OFFSET ?");
        params.add(porPagina);
        params.add(offset);

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        return Map.of("data", rows, "total", total != null ? total : 0, "page", page, "por_pagina", porPagina);
    }

    public Map<String, Object> getReporte(UUID cicloDestinoId) {
        String sqlResumen =
            "SELECT estado, COUNT(*) AS total " +
            "FROM ades_reinscripcion_ciclo WHERE ciclo_destino_id = ? AND is_active = TRUE " +
            "GROUP BY estado ORDER BY estado";
        List<Map<String, Object>> resumen = jdbc.queryForList(sqlResumen, cicloDestinoId);

        String sqlPlantel =
            "SELECT pl.nombre_plantel, rc.estado, COUNT(*) AS total " +
            "FROM ades_reinscripcion_ciclo rc " +
            "JOIN ades_inscripciones i ON i.estudiante_id = rc.estudiante_id AND i.ciclo_origen_id = rc.ciclo_origen_id AND i.is_active = TRUE " +
            "JOIN ades_grupos g ON g.id = i.grupo_id " +
            "JOIN ades_planteles pl ON pl.id = g.plantel_id " +
            "WHERE rc.ciclo_destino_id = ? AND rc.is_active = TRUE " +
            "GROUP BY pl.nombre_plantel " +
            "ORDER BY pl.nombre_plantel";
        List<Map<String, Object>> porPlantel = jdbc.queryForList(sqlPlantel, cicloDestinoId);

        return Map.of("resumen", resumen, "por_plantel", porPlantel);
    }

    public Map<String, Object> verificarNoAdeudo(UUID estudianteId, UUID cicloEscolarId) {
        List<String> filters = new ArrayList<>(List.of(
            "cp.estudiante_id = ?", "cp.is_active = TRUE", "cp.saldo_pendiente > 0"));
        List<Object> params = new ArrayList<>(List.of(estudianteId));

        if (cicloEscolarId != null) {
            filters.add("cp.ciclo_escolar_id = ?");
            params.add(cicloEscolarId);
        }

        String sql = "SELECT cc.nombre AS concepto, cp.monto_cobrado, cp.monto_pagado, " +
                "cp.descuento, cp.saldo_pendiente, cp.fecha_vencimiento, cp.estatus " +
                "FROM ades_cuotas_pagos cp " +
                "JOIN ades_cuotas_concepto cc ON cc.id = cp.concepto_id " +
                "WHERE " + String.join(" AND ", filters) + " " +
                "ORDER BY cp.fecha_vencimiento";

        List<Map<String, Object>> rows = jdbc.queryForList(sql, params.toArray());
        double totalAdeudo = rows.stream()
                .mapToDouble(r -> r.get("saldo_pendiente") instanceof Number n ? n.doubleValue() : 0.0)
                .sum();

        return Map.of(
                "estudiante_id", estudianteId.toString(),
                "tiene_adeudo", !rows.isEmpty(),
                "total_adeudo", totalAdeudo,
                "adeudos", rows,
                "puede_reinscribirse", rows.isEmpty());
    }

    @Transactional
    public String validarMasiva(UUID cicloOrigenId, UUID cicloDestinoId) {
        return jdbc.queryForObject(
            "SELECT pg_validar_reinscripcion_masiva(?, ?)::text",
            String.class, cicloOrigenId, cicloDestinoId);
    }

    @Transactional
    public Map<String, Object> aprobarMasivo(UUID cicloOrigenId, UUID cicloDestinoId, UUID usuarioId) {
        int nAprobados = jdbc.update(
            "UPDATE ades_reinscripcion_ciclo " +
            "SET estado = 'APROBADO', aprobado_por = ?, fecha_aprobacion = now(), " +
            "fecha_modificacion = now(), row_version = row_version + 1 " +
            "WHERE ciclo_destino_id = ? AND estado = 'VALIDADO' AND is_active = TRUE",
            usuarioId, cicloDestinoId);

        String resultadoPromo = jdbc.queryForObject(
            "SELECT cerrar_ciclo_y_promover(?, ?, ?)::text",
            String.class, cicloOrigenId, cicloDestinoId, usuarioId.toString());

        return Map.of("ok", true, "aprobados", nAprobados,
                "resultado_promocion", resultadoPromo != null ? resultadoPromo : "");
    }
}
