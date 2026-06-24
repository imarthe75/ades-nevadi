package mx.ades.modules.learning_paths.infrastructure.outbound.persistence;

import mx.ades.modules.learning_paths.domain.port.out.LearningPathRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Adaptador JDBC que implementa {@link LearningPathRepositoryPort}.
 * Gestiona paths ({@code ades_learning_paths}), recursos ({@code ades_lp_recursos}),
 * asignaciones ({@code ades_lp_asignaciones}) y progreso ({@code ades_lp_progreso}).
 *
 * @author ADES
 * @since 2026
 */
@Component
public class LearningPathPersistenceAdapter implements LearningPathRepositoryPort {

    private final JdbcTemplate jdbc;

    public LearningPathPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsertProgreso(UUID asignacionId, UUID recursoId, Integer tiempoMin, Double calificacion) {
        jdbc.update("""
                INSERT INTO ades_lp_progreso
                    (asignacion_id, recurso_id, completado, tiempo_min, calificacion, fccompletado)
                VALUES (?, ?, TRUE, ?, ?, NOW())
                ON CONFLICT (asignacion_id, recurso_id) DO UPDATE
                SET completado = TRUE,
                    tiempo_min = COALESCE(EXCLUDED.tiempo_min, ades_lp_progreso.tiempo_min),
                    calificacion = COALESCE(EXCLUDED.calificacion, ades_lp_progreso.calificacion),
                    fccompletado = NOW()
                """,
                asignacionId, recursoId, tiempoMin,
                calificacion != null ? BigDecimal.valueOf(calificacion) : null);
    }

    @Override
    public ProgresoStats calcularProgreso(UUID asignacionId) {
        Integer completados = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_lp_progreso WHERE asignacion_id = ? AND completado = TRUE",
                Integer.class, asignacionId);
        Integer obligatorios = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ades_lp_recursos r
                WHERE r.path_id = (SELECT path_id FROM ades_lp_asignaciones WHERE id = ?)
                  AND r.is_active = TRUE AND r.obligatorio = TRUE
                """, Integer.class, asignacionId);
        Integer totalRecursos = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ades_lp_recursos r
                WHERE r.path_id = (SELECT path_id FROM ades_lp_asignaciones WHERE id = ?)
                  AND r.is_active = TRUE
                """, Integer.class, asignacionId);
        int comp = completados != null ? completados : 0;
        int oblig = obligatorios != null ? obligatorios : 0;
        int total = totalRecursos != null ? totalRecursos : 0;
        double pct = total > 0 ? Math.round(comp * 1000.0 / total) / 10.0 : 0.0;
        return new ProgresoStats(comp, oblig, pct);
    }

    @Override
    public void actualizarEstatus(UUID asignacionId, String estatus, double pctCompletado) {
        jdbc.update("""
                UPDATE ades_lp_asignaciones
                SET pct_completado = ?, estatus = ?,
                    fccompletado = CASE WHEN ? = 'COMPLETADO' THEN NOW() ELSE fccompletado END,
                    fecha_modificacion = NOW()
                WHERE id = ?
                """, BigDecimal.valueOf(pctCompletado), estatus, estatus, asignacionId);
    }

    @Override
    public Map<String, Object> insertPath(UUID id, String nombre, String descripcion, UUID nivelEducativoId,
                                          UUID materiaId, String criterioActivacion, Double umbralActivacion) {
        jdbc.update("""
                INSERT INTO ades_learning_paths
                    (id, nombre, descripcion, nivel_educativo_id, materia_id, criterio_activacion, umbral_activacion, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)
                """, id, nombre, descripcion, nivelEducativoId, materiaId, criterioActivacion, umbralActivacion);
        return Map.of("id", id, "nombre", nombre, "descripcion", descripcion,
                "criterio_activacion", criterioActivacion != null ? criterioActivacion : "",
                "umbral_activacion", umbralActivacion != null ? umbralActivacion : 0.0,
                "is_active", true, "total_recursos", 0);
    }

    @Override
    public boolean existsPath(UUID pathId) {
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_learning_paths WHERE id = ?", Integer.class, pathId);
        return cnt != null && cnt > 0;
    }

    @Override
    public Optional<String> fetchPathNombre(UUID pathId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT nombre FROM ades_learning_paths WHERE id = ?", pathId);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable((String) rows.get(0).get("nombre"));
    }

    @Override
    public Map<String, Object> insertRecurso(UUID id, UUID pathId, Integer orden, String tipo,
                                              String titulo, String descripcion, String urlRecurso,
                                              Integer duracionMin, Boolean obligatorio) {
        jdbc.update("""
                INSERT INTO ades_lp_recursos
                    (id, path_id, orden, tipo, titulo, descripcion, url_recurso, duracion_min, obligatorio)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, pathId, orden, tipo, titulo, descripcion, urlRecurso, duracionMin, obligatorio);
        return Map.of("id", id, "orden", orden != null ? orden : 0, "tipo", tipo != null ? tipo : "",
                "titulo", titulo != null ? titulo : "", "url_recurso", urlRecurso != null ? urlRecurso : "",
                "is_active", true);
    }

    @Override
    public Map<String, Object> upsertAsignacion(UUID id, UUID pathId, UUID estudianteId,
                                                 UUID asignadoPor, String motivo) {
        jdbc.update("""
                INSERT INTO ades_lp_asignaciones
                    (id, path_id, estudiante_id, asignado_por, motivo, estatus, pct_completado, fcinicio)
                VALUES (?, ?, ?, ?, ?, 'PENDIENTE', 0, NOW())
                ON CONFLICT (path_id, estudiante_id) DO UPDATE
                SET estatus = EXCLUDED.estatus, fecha_modificacion = NOW()
                """, id, pathId, estudianteId, asignadoPor, motivo);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, path_id, estudiante_id, motivo, estatus, pct_completado, fecha_creacion
                FROM ades_lp_asignaciones WHERE path_id = ? AND estudiante_id = ? LIMIT 1
                """, pathId, estudianteId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    @Override
    public List<Map<String, Object>> fetchAlertasByGrupo(UUID grupoId) {
        return jdbc.queryForList("""
                SELECT a.estudiante_id, a.tipo_alerta FROM ades_alertas_academicas a
                WHERE a.grupo_id = ? AND a.atendida = FALSE
                """, grupoId);
    }

    @Override
    public List<Map<String, Object>> fetchPathsByCriterios(List<String> criterios) {
        if (criterios.isEmpty()) return List.of();
        String placeholders = String.join(",", criterios.stream().map(ignored -> "?").toList());
        return jdbc.queryForList(
                "SELECT id, criterio_activacion FROM ades_learning_paths " +
                "WHERE criterio_activacion IN (" + placeholders + ") AND is_active = TRUE",
                criterios.toArray());
    }

    @Override
    public void insertAsignacionAuto(UUID pathId, UUID estudianteId, UUID asignadoPor, String motivo) {
        jdbc.update("""
                INSERT INTO ades_lp_asignaciones
                    (id, path_id, estudiante_id, asignado_por, motivo, estatus, pct_completado, fcinicio)
                VALUES (?, ?, ?, ?, ?, 'PENDIENTE', 0, NOW())
                ON CONFLICT (path_id, estudiante_id) DO NOTHING
                """, UUID.randomUUID(), pathId, estudianteId, asignadoPor, motivo);
    }
}
