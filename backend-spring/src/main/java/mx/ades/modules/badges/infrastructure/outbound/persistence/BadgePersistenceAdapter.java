package mx.ades.modules.badges.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.badges.Badge;
import mx.ades.modules.badges.BadgeRepository;
import mx.ades.modules.badges.domain.port.out.BadgeRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Adaptador de persistencia que implementa {@link BadgeRepositoryPort} accediendo
 * a las tablas {@code ades_badges} y {@code ades_badge_otorgados} vía JPA y JDBC.
 *
 * <p>Las consultas de elegibilidad usan SQL analítico con {@code HAVING} sobre
 * asistencias y calificaciones para calcular métricas en el ciclo escolar.</p>
 *
 * @author ADES
 * @since 2026
 */
@Component
@RequiredArgsConstructor
public class BadgePersistenceAdapter implements BadgeRepositoryPort {

    private final BadgeRepository jpa;
    private final JdbcTemplate jdbc;

    @Override
    public Badge save(Badge badge) { return jpa.save(badge); }

    @Override
    public Optional<Badge> findById(UUID id) { return jpa.findById(id); }

    @Override
    public List<Badge> findAllAutomatic() {
        return jpa.findAll().stream()
                .filter(b -> "AUTOMATICO".equalsIgnoreCase(b.getCriterioTipo()) && Boolean.TRUE.equals(b.getIsActive()))
                .toList();
    }

    @Override
    public List<Map<String, Object>> list(String tipo, UUID plantelId) {
        StringBuilder sq = new StringBuilder(
                "SELECT b.id, b.nombre, b.descripcion, b.icono, b.color, b.tipo, " +
                "b.criterio_tipo, b.criterio_metrica, b.criterio_valor, b.plantel_id, " +
                "COUNT(DISTINCT o.estudiante_id) AS total_otorgados " +
                "FROM ades_badges b " +
                "LEFT JOIN ades_badge_otorgados o ON o.badge_id = b.id " +
                "WHERE b.is_active = TRUE ");
        List<Object> params = new ArrayList<>();
        if (tipo != null && !tipo.isBlank()) { sq.append("AND b.tipo = ? "); params.add(tipo); }
        if (plantelId != null) { sq.append("AND (b.plantel_id = ? OR b.plantel_id IS NULL) "); params.add(plantelId); }
        sq.append("GROUP BY b.id ORDER BY b.tipo, b.nombre");
        return jdbc.queryForList(sq.toString(), params.toArray());
    }

    @Override
    public Map<String, Object> detalle(UUID badgeId) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT estudiante_id) FROM ades_badge_otorgados WHERE badge_id = ?",
                Long.class, badgeId);
        List<Map<String, Object>> alumnos = jdbc.queryForList(
                "SELECT o.id AS otorgado_id, o.fecha_otorgado, o.motivo, " +
                "p.nombre || ' ' || p.apellido_paterno || ' ' || COALESCE(p.apellido_materno,'') AS nombre_alumno, " +
                "e.matricula, g.nombre_grupo AS grupo, ce.nombre_ciclo AS ciclo " +
                "FROM ades_badge_otorgados o " +
                "JOIN ades_estudiantes e ON e.id = o.estudiante_id " +
                "JOIN ades_personas p ON p.id = e.persona_id " +
                "JOIN ades_inscripciones ins ON ins.estudiante_id = e.id " +
                "JOIN ades_grupos g ON g.id = ins.grupo_id " +
                "JOIN ades_ciclos_escolares ce ON ce.id = ins.ciclo_escolar_id " +
                "WHERE o.badge_id = ? AND ins.ciclo_escolar_id = o.ciclo_id " +
                "ORDER BY o.fecha_otorgado DESC", badgeId);
        Map<String, Object> result = new HashMap<>();
        result.put("total_otorgados", total);
        result.put("alumnos", alumnos);
        return result;
    }

    @Override
    public List<Map<String, Object>> badgesAlumno(UUID estudianteId, UUID cicloId) {
        StringBuilder sq = new StringBuilder(
                "SELECT b.id, b.nombre, b.descripcion, b.icono, b.color, b.tipo, " +
                "b.criterio_tipo, b.criterio_metrica, b.criterio_valor, " +
                "o.id AS otorgado_id, o.fecha_otorgado, o.motivo " +
                "FROM ades_badges b " +
                "LEFT JOIN ades_badge_otorgados o ON o.badge_id = b.id AND o.estudiante_id = ? ");
        List<Object> params = new ArrayList<>();
        params.add(estudianteId);
        if (cicloId != null) { sq.append("AND o.ciclo_id = ? "); params.add(cicloId); }
        sq.append("WHERE b.is_active = TRUE ORDER BY o.fecha_otorgado DESC NULLS LAST, b.tipo, b.nombre");
        return jdbc.queryForList(sq.toString(), params.toArray());
    }

    @Override
    public boolean otorgar(UUID badgeId, UUID estudianteId, UUID cicloId, String motivo, UUID otorgadoPor) {
        try {
            int rows = jdbc.update(
                    "INSERT INTO ades_badge_otorgados (badge_id, estudiante_id, ciclo_id, motivo, otorgado_por) " +
                    "VALUES (?, ?, ?, ?, ?) ON CONFLICT (badge_id, estudiante_id, ciclo_id) DO NOTHING",
                    badgeId, estudianteId, cicloId, motivo, otorgadoPor);
            return rows > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public void revocar(UUID badgeId, UUID estudianteId, UUID cicloId) {
        if (cicloId != null) {
            jdbc.update("DELETE FROM ades_badge_otorgados WHERE badge_id = ? AND estudiante_id = ? AND ciclo_id = ?",
                    badgeId, estudianteId, cicloId);
        } else {
            jdbc.update("DELETE FROM ades_badge_otorgados WHERE badge_id = ? AND estudiante_id = ?",
                    badgeId, estudianteId);
        }
    }

    @Override
    public List<UUID> eligiblesByPctAsistencia(UUID cicloId, double umbral) {
        return jdbc.queryForList(
                "SELECT ins.estudiante_id " +
                "FROM ades_inscripciones ins " +
                "JOIN ades_asistencias a ON a.estudiante_id = ins.estudiante_id " +
                "JOIN ades_clases cl ON cl.id = a.clase_id " +
                "WHERE ins.ciclo_escolar_id = ? AND cl.ciclo_escolar_id = ? " +
                "GROUP BY ins.estudiante_id " +
                "HAVING ROUND(100.0 * COUNT(CASE WHEN a.estatus_asistencia='PRESENTE' THEN 1 END) / NULLIF(COUNT(a.id),0), 2) >= ?",
                UUID.class, cicloId, cicloId, umbral);
    }

    @Override
    public List<UUID> eligiblesByPromedioGeneral(UUID cicloId, double umbral) {
        return jdbc.queryForList(
                "SELECT ins.estudiante_id " +
                "FROM ades_inscripciones ins " +
                "JOIN ades_calificaciones_periodo cp ON cp.estudiante_id = ins.estudiante_id " +
                "JOIN ades_periodos_evaluacion pe ON pe.id = cp.periodo_id " +
                "WHERE ins.ciclo_escolar_id = ? AND pe.ciclo_escolar_id = ? " +
                "GROUP BY ins.estudiante_id " +
                "HAVING AVG(cp.calificacion) >= ?",
                UUID.class, cicloId, cicloId, umbral);
    }

    @Override
    public List<UUID> eligiblesBySinReportes(UUID cicloId) {
        return jdbc.queryForList(
                "SELECT ins.estudiante_id " +
                "FROM ades_inscripciones ins " +
                "WHERE ins.ciclo_escolar_id = ? " +
                "AND NOT EXISTS (" +
                "SELECT 1 FROM ades_reportes_conducta rc " +
                "WHERE rc.estudiante_id = ins.estudiante_id " +
                "AND rc.fecha_creacion >= (SELECT fecha_inicio FROM ades_ciclos_escolares WHERE id = ?))",
                UUID.class, cicloId, cicloId);
    }

    @Override
    public int otorgarBulk(UUID badgeId, UUID cicloId, List<UUID> estudianteIds) {
        int total = 0;
        for (UUID eid : estudianteIds) {
            try {
                int rows = jdbc.update(
                        "INSERT INTO ades_badge_otorgados (badge_id, estudiante_id, ciclo_id, motivo) " +
                        "VALUES (?, ?, ?, 'Otorgado automáticamente por evaluación de criterios') " +
                        "ON CONFLICT (badge_id, estudiante_id, ciclo_id) DO NOTHING",
                        badgeId, eid, cicloId);
                total += rows;
            } catch (Exception ignored) {}
        }
        return total;
    }
}
