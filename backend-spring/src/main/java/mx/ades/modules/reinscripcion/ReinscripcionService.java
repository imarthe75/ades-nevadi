package mx.ades.modules.reinscripcion;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReinscripcionService {

    private final ReinscripcionCicloRepository repository;
    private final JdbcTemplate jdbc;

    @Transactional
    public String validarReinscripcionMasiva(UUID cicloOrigenId, UUID cicloDestinoId) {
        return jdbc.queryForObject(
                "SELECT pg_validar_reinscripcion_masiva(?, ?)::text",
                String.class, cicloOrigenId, cicloDestinoId);
    }

    @Transactional
    public ReinscripcionCiclo aprobarReinscripcion(UUID id, UUID aprobadoPor) {
        ReinscripcionCiclo r = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro de reinscripción no encontrado"));
        r.setEstado("APROBADO");
        r.setAprobadoPor(aprobadoPor);
        r.setFechaAprobacion(OffsetDateTime.now());
        return repository.save(r);
    }

    @Transactional
    public ReinscripcionCiclo rechazarReinscripcion(UUID id, String razonRechazo) {
        ReinscripcionCiclo r = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro de reinscripción no encontrado"));
        r.setEstado("RECHAZADO");
        r.setRazonRechazo(razonRechazo);
        return repository.save(r);
    }

    public List<ReinscripcionCiclo> listarPorCicloDestino(UUID cicloDestinoId) {
        return repository.findByCicloDestinoId(cicloDestinoId);
    }

    public Map<String, Object> getEstado(UUID cicloDestinoId, String estado, UUID plantelId, int page, int porPagina, AdesUser user) {
        List<String> filters = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        filters.add("rc.ciclo_destino_id = ?");
        params.add(cicloDestinoId);

        filters.add("rc.is_active = TRUE");

        if (estado != null && !estado.isBlank()) {
            filters.add("rc.estado = ?");
            params.add(estado);
        }
        if (plantelId != null) {
            filters.add("g.plantel_id = ?");
            params.add(plantelId);
        }
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 1 && user.getPlantelId() != null) {
            filters.add("g.plantel_id = ?");
            params.add(user.getPlantelId());
        }

        String where = String.join(" AND ", filters);

        String sql = "SELECT rc.id, rc.estudiante_id, p.nombre || ' ' || p.apellido_paterno AS alumno, " +
                "est.matricula, gr.nombre_grado || ' ' || g.nombre_grupo AS grado_grupo, pl.nombre_plantel, " +
                "rc.estado, rc.tiene_adeudos, rc.monto_adeudado, rc.bloqueantes, rc.razon_rechazo, " +
                "rc.fecha_validacion, rc.fecha_aprobacion " +
                "FROM ades_reinscripcion_ciclo rc " +
                "JOIN ades_estudiantes est ON est.id = rc.estudiante_id " +
                "JOIN ades_personas p ON p.id = est.persona_id " +
                "JOIN ades_inscripciones i ON i.estudiante_id = rc.estudiante_id AND i.ciclo_escolar_id = rc.ciclo_origen_id AND i.is_active = TRUE " +
                "JOIN ades_grupos g ON g.id = i.grupo_id " +
                "JOIN ades_grados gr ON gr.id = g.grado_id " +
                "JOIN ades_planteles pl ON pl.id = g.plantel_id " +
                "WHERE " + where + " " +
                "ORDER BY pl.nombre_plantel, gr.nombre_grado, p.apellido_paterno " +
                "LIMIT ? OFFSET ?";

        List<Object> sqlParams = new ArrayList<>(params);
        sqlParams.add(porPagina);
        sqlParams.add((page - 1) * porPagina);

        List<Map<String, Object>> data = jdbc.queryForList(sql, sqlParams.toArray());

        String countSql = "SELECT COUNT(*) " +
                "FROM ades_reinscripcion_ciclo rc " +
                "JOIN ades_inscripciones i ON i.estudiante_id = rc.estudiante_id AND i.ciclo_escolar_id = rc.ciclo_origen_id AND i.is_active = TRUE " +
                "JOIN ades_grupos g ON g.id = i.grupo_id " +
                "WHERE " + where;

        Long total = jdbc.queryForObject(countSql, Long.class, params.toArray());

        return Map.of(
                "data", data,
                "total", total != null ? total : 0L,
                "page", page,
                "por_pagina", porPagina
        );
    }

    public Map<String, Object> getReporte(UUID cicloDestinoId) {
        String sqlResumen = "SELECT COUNT(*) AS total, " +
                "COUNT(*) FILTER (WHERE estado = 'PENDIENTE') AS pendientes, " +
                "COUNT(*) FILTER (WHERE estado = 'VALIDADO') AS validados, " +
                "COUNT(*) FILTER (WHERE estado = 'APROBADO') AS aprobados, " +
                "COUNT(*) FILTER (WHERE estado = 'RECHAZADO') AS rechazados, " +
                "COUNT(*) FILTER (WHERE tiene_adeudos = TRUE) AS con_adeudos, " +
                "COALESCE(SUM(monto_adeudado), 0) AS monto_total_adeudado, " +
                "ROUND(COUNT(*) FILTER (WHERE estado = 'APROBADO') * 100.0 / NULLIF(COUNT(*), 0), 1) AS pct_completado " +
                "FROM ades_reinscripcion_ciclo " +
                "WHERE ciclo_destino_id = ? AND is_active = TRUE";

        Map<String, Object> resumen = jdbc.queryForMap(sqlResumen, cicloDestinoId);

        String sqlPlantel = "SELECT pl.nombre_plantel, COUNT(*) AS total, " +
                "COUNT(*) FILTER (WHERE rc.estado = 'APROBADO') AS aprobados, " +
                "COUNT(*) FILTER (WHERE rc.estado = 'PENDIENTE') AS pendientes, " +
                "COUNT(*) FILTER (WHERE rc.tiene_adeudos = TRUE) AS con_adeudos " +
                "FROM ades_reinscripcion_ciclo rc " +
                "JOIN ades_inscripciones i ON i.estudiante_id = rc.estudiante_id AND i.ciclo_origen_id = rc.ciclo_origen_id AND i.is_active = TRUE " +
                "JOIN ades_grupos g ON g.id = i.grupo_id " +
                "JOIN ades_planteles pl ON pl.id = g.plantel_id " +
                "WHERE rc.ciclo_destino_id = ? AND rc.is_active = TRUE " +
                "GROUP BY pl.nombre_plantel " +
                "ORDER BY pl.nombre_plantel";

        List<Map<String, Object>> porPlantel = jdbc.queryForList(sqlPlantel, cicloDestinoId);

        return Map.of(
                "resumen", resumen,
                "por_plantel", porPlantel
        );
    }

    @Transactional
    public Map<String, Object> patchIndividual(UUID registroId, String accion, String razonRechazo, UUID usuarioId) {
        String nuevoEstado = "APROBAR".equals(accion) ? "APROBADO" : "RECHAZADO";

        int count = jdbc.update(
                "UPDATE ades_reinscripcion_ciclo " +
                        "SET estado = ?, aprobado_por = ?, razon_rechazo = ?, " +
                        "fecha_aprobacion = CASE WHEN ? = 'APROBADO' THEN now() ELSE NULL END, " +
                        "fecha_modificacion = now(), row_version = row_version + 1 " +
                        "WHERE id = ? AND is_active = TRUE",
                nuevoEstado, usuarioId, razonRechazo, nuevoEstado, registroId
        );

        if (count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro de reinscripción no encontrado");
        }

        return Map.of("id", registroId.toString(), "estado", nuevoEstado);
    }

    @Transactional
    public Map<String, Object> aprobarMasivo(UUID cicloOrigenId, UUID cicloDestinoId, UUID usuarioId) {
        int nAprobados = jdbc.update(
                "UPDATE ades_reinscripcion_ciclo " +
                        "SET estado = 'APROBADO', aprobado_por = ?, fecha_aprobacion = now(), " +
                        "fecha_modificacion = now(), row_version = row_version + 1 " +
                        "WHERE ciclo_destino_id = ? AND estado = 'VALIDADO' AND is_active = TRUE",
                usuarioId, cicloDestinoId
        );

        String resultadoPromo = jdbc.queryForObject(
                "SELECT cerrar_ciclo_y_promover(?, ?, ?)::text",
                String.class, cicloOrigenId, cicloDestinoId, usuarioId.toString()
        );

        return Map.of(
                "ok", true,
                "aprobados", nAprobados,
                "resultado_promocion", resultadoPromo != null ? resultadoPromo : ""
        );
    }

    public Map<String, Object> verificarNoAdeudo(UUID estudianteId, UUID cicloEscolarId) {
        List<String> filters = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        filters.add("cp.estudiante_id = ?");
        params.add(estudianteId);
        filters.add("cp.is_active = TRUE");
        filters.add("cp.saldo_pendiente > 0");

        if (cicloEscolarId != null) {
            filters.add("cp.ciclo_escolar_id = ?");
            params.add(cicloEscolarId);
        }

        String where = String.join(" AND ", filters);

        String sql = "SELECT cc.nombre AS concepto, cp.monto_cobrado, cp.monto_pagado, " +
                "cp.descuento, cp.saldo_pendiente, cp.fecha_vencimiento, cp.estatus " +
                "FROM ades_cuotas_pagos cp " +
                "JOIN ades_cuotas_concepto cc ON cc.id = cp.concepto_id " +
                "WHERE " + where + " " +
                "ORDER BY cp.fecha_vencimiento";

        List<Map<String, Object>> rows = jdbc.queryForList(sql, params.toArray());

        double totalAdeudo = 0;
        for (Map<String, Object> r : rows) {
            Number saldo = (Number) r.get("saldo_pendiente");
            if (saldo != null) {
                totalAdeudo += saldo.doubleValue();
            }
        }

        return Map.of(
                "estudiante_id", estudianteId.toString(),
                "tiene_adeudo", !rows.isEmpty(),
                "total_adeudo", totalAdeudo,
                "adeudos", rows,
                "puede_reinscribirse", rows.isEmpty()
        );
    }
}
