package mx.ades.modules.badges;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/v1/badges")
@RequiredArgsConstructor
public class BadgeController {

    private final BadgeRepository repository;
    private final BadgeOtorgadoRepository otorgadoRepository;
    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    @Data
    public static class BadgeCreateRequest {
        private String nombre;
        private String descripcion;
        private String icono = "pi-star";
        private String color = "#D02030";
        private String tipo;
        private String criterioTipo = "MANUAL";
        private String criterioMetrica;
        private String criterioValor;
        private UUID plantelId;
    }

    @Data
    public static class OtorgarRequest {
        private UUID estudianteId;
        private UUID cicloId;
        private String motivo;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(value = "tipo", required = false) String tipo,
            @RequestParam(value = "plantel_id", required = false) UUID plantelId) {

        StringBuilder query = new StringBuilder(
                "SELECT b.id, b.nombre, b.descripcion, b.icono, b.color, b.tipo, " +
                "b.criterio_tipo, b.criterio_metrica, b.criterio_valor, b.plantel_id, " +
                "COUNT(DISTINCT o.estudiante_id) AS total_otorgados " +
                "FROM ades_badges b " +
                "LEFT JOIN ades_badge_otorgados o ON o.badge_id = b.id " +
                "WHERE b.is_active = TRUE ");

        List<Object> params = new ArrayList<>();
        if (tipo != null && !tipo.isBlank()) {
            query.append("AND b.tipo = ? ");
            params.add(tipo);
        }
        if (plantelId != null) {
            query.append("AND (b.plantel_id = ? OR b.plantel_id IS NULL) ");
            params.add(plantelId);
        }

        query.append("GROUP BY b.id ORDER BY b.tipo, b.nombre");

        return ResponseEntity.ok(jdbc.queryForList(query.toString(), params.toArray()));
    }

    @PostMapping
    public ResponseEntity<Badge> crear(@RequestBody BadgeCreateRequest body) {
        Badge b = new Badge();
        b.setNombre(body.getNombre());
        b.setDescripcion(body.getDescripcion());
        if (body.getIcono() != null) b.setIcono(body.getIcono());
        if (body.getColor() != null) b.setColor(body.getColor());
        b.setTipo(body.getTipo());
        b.setCriterioTipo(body.getCriterioTipo());
        b.setCriterioMetrica(body.getCriterioMetrica());
        if (body.getCriterioValor() != null) b.setCriterioValor(new java.math.BigDecimal(body.getCriterioValor()));
        b.setPlantelId(body.getPlantelId());

        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(b));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detalle(@PathVariable("id") UUID id) {
        Badge b = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Badge no encontrado"));

        String totalOtorgadosSql = "SELECT COUNT(DISTINCT estudiante_id) FROM ades_badge_otorgados WHERE badge_id = ?";
        Long total = jdbc.queryForObject(totalOtorgadosSql, Long.class, id);

        String alumnosSql = "SELECT o.id AS otorgado_id, o.fecha_otorgado, o.motivo, " +
                "p.nombre || ' ' || p.apellido_paterno || ' ' || COALESCE(p.apellido_materno,'') AS nombre_alumno, " +
                "e.matricula, g.nombre_grupo AS grupo, ce.nombre_ciclo AS ciclo " +
                "FROM ades_badge_otorgados o " +
                "JOIN ades_estudiantes e ON e.id = o.estudiante_id " +
                "JOIN ades_personas p ON p.id = e.persona_id " +
                "JOIN ades_inscripciones ins ON ins.estudiante_id = e.id " +
                "JOIN ades_grupos g ON g.id = ins.grupo_id " +
                "JOIN ades_ciclos_escolares ce ON ce.id = ins.ciclo_escolar_id " +
                "WHERE o.badge_id = ? AND ins.ciclo_escolar_id = o.ciclo_id " +
                "ORDER BY o.fecha_otorgado DESC";

        List<Map<String, Object>> alumnos = jdbc.queryForList(alumnosSql, id);

        Map<String, Object> out = new HashMap<>();
        out.put("id", b.getId());
        out.put("nombre", b.getNombre());
        out.put("descripcion", b.getDescripcion());
        out.put("icono", b.getIcono());
        out.put("color", b.getColor());
        out.put("tipo", b.getTipo());
        out.put("criterio_tipo", b.getCriterioTipo());
        out.put("criterio_metrica", b.getCriterioMetrica());
        out.put("criterio_valor", b.getCriterioValor());
        out.put("plantel_id", b.getPlantelId());
        out.put("total_otorgados", total);
        out.put("alumnos", alumnos);

        return ResponseEntity.ok(out);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> eliminar(@PathVariable("id") UUID id) {
        Badge b = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Badge no encontrado"));
        b.setIsActive(false);
        repository.save(b);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/alumno/{estudianteId}")
    public ResponseEntity<List<Map<String, Object>>> badgesAlumno(
            @PathVariable("estudianteId") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId) {

        StringBuilder query = new StringBuilder(
                "SELECT b.id, b.nombre, b.descripcion, b.icono, b.color, b.tipo, " +
                "b.criterio_tipo, b.criterio_metrica, b.criterio_valor, " +
                "o.id AS otorgado_id, o.fecha_otorgado, o.motivo " +
                "FROM ades_badges b " +
                "LEFT JOIN ades_badge_otorgados o ON o.badge_id = b.id AND o.estudiante_id = ? ");

        List<Object> params = new ArrayList<>();
        params.add(estudianteId);

        if (cicloId != null) {
            query.append("AND o.ciclo_id = ? ");
            params.add(cicloId);
        }

        query.append("WHERE b.is_active = TRUE ORDER BY o.fecha_otorgado DESC NULLS LAST, b.tipo, b.nombre");

        return ResponseEntity.ok(jdbc.queryForList(query.toString(), params.toArray()));
    }

    @PostMapping("/{badgeId}/otorgar")
    public ResponseEntity<Map<String, Object>> otorgar(
            @PathVariable("badgeId") UUID badgeId,
            @RequestBody OtorgarRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);

        try {
            int updated = jdbc.update(
                    "INSERT INTO ades_badge_otorgados (badge_id, estudiante_id, ciclo_id, motivo, otorgado_por) " +
                    "VALUES (?, ?, ?, ?, ?) ON CONFLICT (badge_id, estudiante_id, ciclo_id) DO NOTHING",
                    badgeId, body.getEstudianteId(), body.getCicloId(), body.getMotivo(), user.getId());

            if (updated == 0) {
                return ResponseEntity.ok(Map.of("ok", true, "duplicado", true));
            }
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of("ok", true, "duplicado", true));
        }

        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/{badgeId}/otorgados/{estudianteId}")
    public ResponseEntity<Map<String, Object>> revocar(
            @PathVariable("badgeId") UUID badgeId,
            @PathVariable("estudianteId") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId) {

        if (cicloId != null) {
            jdbc.update("DELETE FROM ades_badge_otorgados WHERE badge_id = ? AND estudiante_id = ? AND ciclo_id = ?",
                    badgeId, estudianteId, cicloId);
        } else {
            jdbc.update("DELETE FROM ades_badge_otorgados WHERE badge_id = ? AND estudiante_id = ?",
                    badgeId, estudianteId);
        }

        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/auto-evaluar/{cicloId}")
    public ResponseEntity<Map<String, Object>> autoEvaluar(
            @PathVariable("cicloId") UUID cicloId) {

        List<Badge> badges = repository.findAll().stream()
                .filter(b -> "AUTOMATICO".equalsIgnoreCase(b.getCriterioTipo()) && Boolean.TRUE.equals(b.getIsActive()))
                .toList();

        int totalOtorgados = 0;
        for (Badge badge : badges) {
            String metrica = badge.getCriterioMetrica();
            double umbral = badge.getCriterioValor() != null ? badge.getCriterioValor().doubleValue() : 0.0;

            List<UUID> elegibles = new ArrayList<>();

            if ("pct_asistencia".equalsIgnoreCase(metrica)) {
                String sql = "SELECT ins.estudiante_id " +
                        "FROM ades_inscripciones ins " +
                        "JOIN ades_asistencias a ON a.estudiante_id = ins.estudiante_id " +
                        "JOIN ades_clases cl ON cl.id = a.clase_id " +
                        "WHERE ins.ciclo_escolar_id = ? AND cl.ciclo_escolar_id = ? " +
                        "GROUP BY ins.estudiante_id " +
                        "HAVING ROUND(100.0 * COUNT(CASE WHEN a.estatus_asistencia='PRESENTE' THEN 1 END) / NULLIF(COUNT(a.id),0), 2) >= ?";
                elegibles = jdbc.queryForList(sql, UUID.class, cicloId, cicloId, umbral);

            } else if ("promedio_general".equalsIgnoreCase(metrica)) {
                String sql = "SELECT ins.estudiante_id " +
                        "FROM ades_inscripciones ins " +
                        "JOIN ades_calificaciones_periodo cp ON cp.estudiante_id = ins.estudiante_id " +
                        "JOIN ades_periodos_evaluacion pe ON pe.id = cp.periodo_id " +
                        "WHERE ins.ciclo_escolar_id = ? AND pe.ciclo_escolar_id = ? " +
                        "GROUP BY ins.estudiante_id " +
                        "HAVING AVG(cp.calificacion) >= ?";
                elegibles = jdbc.queryForList(sql, UUID.class, cicloId, cicloId, umbral);

            } else if ("sin_reportes_conducta".equalsIgnoreCase(metrica)) {
                String sql = "SELECT ins.estudiante_id " +
                        "FROM ades_inscripciones ins " +
                        "WHERE ins.ciclo_escolar_id = ? " +
                        "AND NOT EXISTS (" +
                        "SELECT 1 FROM ades_reportes_conducta rc " +
                        "WHERE rc.estudiante_id = ins.estudiante_id " +
                        "AND rc.fecha_creacion >= (SELECT fecha_inicio FROM ades_ciclos_escolares WHERE id = ?))";
                elegibles = jdbc.queryForList(sql, UUID.class, cicloId, cicloId);
            }

            for (UUID eid : elegibles) {
                try {
                    int rows = jdbc.update(
                            "INSERT INTO ades_badge_otorgados (badge_id, estudiante_id, ciclo_id, motivo) " +
                            "VALUES (?, ?, ?, 'Otorgado automáticamente por evaluación de criterios') " +
                            "ON CONFLICT (badge_id, estudiante_id, ciclo_id) DO NOTHING",
                            badge.getId(), eid, cicloId);
                    totalOtorgados += rows;
                } catch (Exception ex) {
                    // Ignore insert errors
                }
            }
        }

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "total_otorgados", totalOtorgados,
                "badges_evaluados", badges.size()
        ));
    }
}
