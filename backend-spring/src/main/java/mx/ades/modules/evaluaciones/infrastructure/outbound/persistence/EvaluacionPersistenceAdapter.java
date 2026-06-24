package mx.ades.modules.evaluaciones.infrastructure.outbound.persistence;

import mx.ades.modules.evaluaciones.domain.model.SlotHorario;
import mx.ades.modules.evaluaciones.domain.port.out.AsignacionAulaRepositoryPort;
import mx.ades.modules.evaluaciones.domain.port.out.CalificacionEvaluacionRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JDBC que implementa {@link AsignacionAulaRepositoryPort}
 * y {@link CalificacionEvaluacionRepositoryPort}.
 * <p>Persiste asignaciones de aula en {@code ades_asignaciones_aula}
 * y calificaciones de evaluaciones en {@code ades_calificaciones_evaluaciones}.</p>
 *
 * @author ADES
 * @since 2026
 */
@Component
public class EvaluacionPersistenceAdapter
        implements AsignacionAulaRepositoryPort, CalificacionEvaluacionRepositoryPort {

    private final JdbcTemplate jdbc;

    public EvaluacionPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── AsignacionAulaRepositoryPort ──────────────────────────────────────────

    @Override
    public boolean existeConflicto(UUID aulaId, LocalDate fecha, SlotHorario slot) {
        List<Map<String, Object>> conflict = jdbc.queryForList(
            "SELECT id FROM ades_asignaciones_aula " +
            "WHERE aula_id = ? AND fecha = ? " +
            "AND NOT (hora_fin <= ? OR hora_inicio >= ?) " +
            "AND is_active = TRUE",
            aulaId, fecha, slot.inicio(), slot.fin());
        return !conflict.isEmpty();
    }

    @Override
    public UUID guardar(UUID claseId, UUID aulaId, LocalDate fecha, SlotHorario slot,
                        String observaciones, String username) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ades_asignaciones_aula " +
            "(id, clase_id, aula_id, fecha, hora_inicio, hora_fin, observaciones, " +
            " usuario_creacion, usuario_modificacion) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            id, claseId, aulaId, fecha, slot.inicio(), slot.fin(),
            observaciones, username, username);
        return id;
    }

    // ── CalificacionEvaluacionRepositoryPort ──────────────────────────────────

    @Override
    public Optional<UUID> findIdActiva(UUID evaluacionId, UUID estudianteId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id FROM ades_calificaciones_evaluaciones " +
            "WHERE evaluacion_id = ? AND estudiante_id = ? AND is_active = TRUE",
            evaluacionId, estudianteId);
        return rows.isEmpty() ? Optional.empty()
                : Optional.of((UUID) rows.get(0).get("id"));
    }

    @Override
    public void insertar(UUID evaluacionId, UUID estudianteId, double calificacion,
                         String comentarios, String username) {
        jdbc.update(
            "INSERT INTO ades_calificaciones_evaluaciones " +
            "(evaluacion_id, estudiante_id, calificacion, comentarios, usuario_creacion, usuario_modificacion) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            evaluacionId, estudianteId, calificacion, comentarios, username, username);
    }

    @Override
    public void actualizar(UUID calificacionId, double calificacion,
                           String comentarios, String username) {
        jdbc.update(
            "UPDATE ades_calificaciones_evaluaciones " +
            "SET calificacion = ?, comentarios = ?, usuario_modificacion = ? WHERE id = ?",
            calificacion, comentarios, username, calificacionId);
    }
}
