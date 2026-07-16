package mx.ades.modules.reinscripcion.query;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class ReinscripcionQueryService {

    private static final Logger logger = LoggerFactory.getLogger(ReinscripcionQueryService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper  om = new ObjectMapper();

    public ReinscripcionQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Lista el estado de reinscripción de alumnos para un ciclo destino, paginado.
     * CLAUDE.md Regla #20 — claves del {@code Map<String,Object>} devuelto:
     * <ul>
     *   <li>{@code data}: {@code List<Map<String,Object>>}, cada fila con las claves
     *       {@code id}, {@code estudiante_id}, {@code estado}, {@code ciclo_origen_id},
     *       {@code ciclo_destino_id}, {@code aprobado_por}, {@code razon_rechazo},
     *       {@code fecha_aprobacion}, {@code alumno} (nombre completo, alias SQL — el
     *       frontend consume esta clave, no {@code nombre_estudiante}), {@code curp},
     *       {@code matricula}, {@code nombre_plantel}</li>
     *   <li>{@code total}: {@code Integer}, total de filas sin paginar</li>
     *   <li>{@code page}: {@code Integer}, página solicitada (1-indexed)</li>
     *   <li>{@code por_pagina}: {@code Integer}, tamaño de página</li>
     * </ul>
     */
    public Map<String, Object> getEstado(UUID cicloDestinoId, String estado,
                                          UUID plantelId, int page, int porPagina,
                                          mx.ades.security.AdesUser user) {
        StringBuilder sql = new StringBuilder(
            "SELECT rc.id, rc.estudiante_id, rc.estado, rc.ciclo_origen_id, rc.ciclo_destino_id, " +
            "       rc.aprobado_por, rc.razon_rechazo, rc.fecha_aprobacion, " +
            "       CONCAT(p.nombre,' ',p.apellido_paterno) AS alumno, " +
            "       p.curp, e.matricula, pl.nombre_plantel " +
            "FROM ades_reinscripcion_ciclo rc " +
            "JOIN ades_inscripciones i ON i.estudiante_id = rc.estudiante_id AND i.ciclo_escolar_id = rc.ciclo_origen_id AND i.is_active = TRUE " +
            "JOIN ades_estudiantes e ON e.id = rc.estudiante_id " +
            "JOIN ades_personas p ON p.id = e.persona_id " +
            "JOIN ades_planteles pl ON pl.id = e.plantel_id " +
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
            "JOIN ades_inscripciones i ON i.estudiante_id = rc.estudiante_id AND i.ciclo_escolar_id = rc.ciclo_origen_id AND i.is_active = TRUE " +
            "JOIN ades_estudiantes e ON e.id = rc.estudiante_id " +
            "JOIN ades_planteles pl ON pl.id = e.plantel_id " +
            "WHERE rc.ciclo_destino_id = ? AND rc.is_active = TRUE " +
            "GROUP BY pl.nombre_plantel, rc.estado " +
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

        String sql = "SELECT cc.nombre_concepto AS concepto, cp.monto_cobrado, cp.monto_pagado, " +
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
    public Map<String, Object> validarMasiva(UUID cicloOrigenId, UUID cicloDestinoId) {
        String json = jdbc.queryForObject(
            "SELECT pg_validar_reinscripcion_masiva(?, ?)::text",
            String.class, cicloOrigenId, cicloDestinoId);
        try {
            return json != null
                ? om.readValue(json, new TypeReference<Map<String, Object>>() {})
                : Map.of();
        } catch (Exception e) {
            return Map.of("raw", json != null ? json : "");
        }
    }

    @Transactional
    public Map<String, Object> aprobarMasivo(UUID cicloOrigenId, UUID cicloDestinoId, UUID usuarioId) {
        validarCapacidadGrupos(cicloDestinoId);

        int nAprobados = jdbc.update(
            "UPDATE ades_reinscripcion_ciclo " +
            "SET estado = 'APROBADO', aprobado_por = ?, fecha_aprobacion = now(), " +
            "fecha_modificacion = now(), row_version = row_version + 1 " +
            "WHERE ciclo_destino_id = ? AND estado = 'VALIDADO' AND is_active = TRUE",
            usuarioId, cicloDestinoId);

        String promoJson = jdbc.queryForObject(
            "SELECT cerrar_ciclo_y_promover(?, ?, ?)::text",
            String.class, cicloOrigenId, cicloDestinoId, usuarioId.toString());

        Map<String, Object> promo;
        try {
            promo = promoJson != null
                ? om.readValue(promoJson, new TypeReference<Map<String, Object>>() {})
                : Map.of();
        } catch (Exception e) {
            promo = Map.of("raw", promoJson != null ? promoJson : "");
        }

        return Map.of("ok", true, "aprobados", nAprobados, "resultado_promocion", promo);
    }

    private void validarCapacidadGrupos(UUID cicloDestinoId) {
        try {
            String sql = "SELECT COUNT(*) as alumnos_validados FROM ades_reinscripcion_ciclo " +
                    "WHERE ciclo_destino_id = ? AND estado = 'VALIDADO' AND is_active = TRUE";
            Integer alumnosValidados = jdbc.queryForObject(sql, Integer.class, cicloDestinoId);

            if (alumnosValidados == null || alumnosValidados == 0) {
                return;
            }

            sql = "SELECT SUM(capacidad_maxima - (SELECT COUNT(*) FROM ades_alumnos e " +
                    "WHERE e.grupo_id = g.id AND e.ciclo_escolar_id = g.ciclo_escolar_id)) as total_capacidad " +
                    "FROM ades_grupos g WHERE g.ciclo_escolar_id = ? AND g.is_active = TRUE";
            Integer capacidadDisponible = jdbc.queryForObject(sql, Integer.class, cicloDestinoId);

            if (capacidadDisponible == null || capacidadDisponible < alumnosValidados) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "No hay suficiente capacidad en los grupos del ciclo destino. " +
                        "Se necesitan " + alumnosValidados + " espacios, pero solo hay " +
                        (capacidadDisponible != null ? capacidadDisponible : 0) + " disponibles.");
            }
        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Error validando capacidad de grupos", e);
        }
    }
}
