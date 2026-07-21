package mx.ades.modules.disponibilidad.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.disponibilidad.DisponibilidadDocente;
import mx.ades.modules.disponibilidad.DisponibilidadDocenteRepository;
import mx.ades.modules.disponibilidad.domain.port.in.GuardarDisponibilidadUseCase;
import mx.ades.modules.disponibilidad.domain.port.out.DisponibilidadRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Adaptador JPA/JDBC que implementa {@link DisponibilidadRepositoryPort}.
 * <p>Persiste slots en {@code ades_disponibilidad_docente} y actualiza horas máximas
 * en {@code ades_profesores}. Soporta filtro por ciclo escolar y búsqueda por nombre de docente.</p>
 *
 * @author ADES
 * @since 2026
 */
@Component
@RequiredArgsConstructor
public class DisponibilidadPersistenceAdapter implements DisponibilidadRepositoryPort {

    private final DisponibilidadDocenteRepository jpa;
    private final JdbcTemplate jdbc;

    /**
     * BOLA fix (2026-07-16): {@code plantelId} filtra por {@code pr.plantel_id} —
     * null solo para nivelAcceso 0 (ver {@code AdesUserService#getEffectivePlantelId}).
     */
    @Override
    public List<Map<String, Object>> list(UUID profesorId, UUID cicloEscolarId, String q, UUID plantelId) {
        StringBuilder sq = new StringBuilder(
                "SELECT dd.*, " +
                "  CONCAT(pe.nombre, ' ', pe.apellido_paterno, " +
                "    CASE WHEN pe.apellido_materno IS NOT NULL THEN CONCAT(' ', pe.apellido_materno) ELSE '' END) AS nombre_docente, " +
                "  pe.nombre AS nombre_persona, pe.apellido_paterno, pe.apellido_materno, " +
                "  pr.numero_empleado " +
                "FROM ades_disponibilidad_docente dd " +
                "LEFT JOIN ades_profesores pr ON pr.id = dd.profesor_id " +
                "LEFT JOIN ades_personas pe ON pe.id = pr.persona_id " +
                "WHERE dd.is_active = TRUE ");

        List<Object> params = new ArrayList<>();
        if (profesorId != null) { sq.append("AND dd.profesor_id = ? "); params.add(profesorId); }
        if (q != null && !q.isBlank()) {
            sq.append("AND (pe.nombre ILIKE ? OR pe.apellido_paterno ILIKE ? OR CONCAT(pe.nombre,' ',pe.apellido_paterno) ILIKE ?) ");
            String like = "%" + q.trim() + "%";
            params.add(like); params.add(like); params.add(like);
        }
        if (cicloEscolarId != null) { sq.append("AND dd.ciclo_escolar_id = ? "); params.add(cicloEscolarId); }
        if (plantelId != null) { sq.append("AND pr.plantel_id = ? "); params.add(plantelId); }
        sq.append("ORDER BY dd.dia_semana, dd.hora_inicio");
        return jdbc.queryForList(sq.toString(), params.toArray());
    }

    @Override
    public UUID plantelDeProfesor(UUID profesorId) {
        List<UUID> rows = jdbc.queryForList(
                "SELECT plantel_id FROM ades_profesores WHERE id = ?", UUID.class, profesorId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public void softDeleteByProfesor(UUID profesorId, UUID cicloEscolarId) {
        if (cicloEscolarId != null) {
            jdbc.update("UPDATE ades_disponibilidad_docente SET is_active = FALSE WHERE profesor_id = ? AND ciclo_escolar_id = ?",
                    profesorId, cicloEscolarId);
        } else {
            jdbc.update("UPDATE ades_disponibilidad_docente SET is_active = FALSE WHERE profesor_id = ? AND ciclo_escolar_id IS NULL",
                    profesorId);
        }
    }

    @Override
    public void createSlots(UUID profesorId, UUID cicloEscolarId, List<GuardarDisponibilidadUseCase.Slot> slots, String usuario) {
        if (slots.isEmpty()) return;
        // Auditoría 2026-07-20 (principio "preferir operaciones Bulk"): guardar la
        // disponibilidad semanal completa de un docente (40-50 slots típicos) hacía un
        // INSERT por slot — batchUpdate en un solo viaje de red.
        List<Object[]> batchArgs = slots.stream()
                .map(slot -> new Object[]{
                        UUID.randomUUID(), profesorId, slot.diaSemana(), slot.horaInicio(), slot.horaFin(),
                        slot.disponible() != null ? slot.disponible() : Boolean.TRUE,
                        slot.motivoNoDisponible(), cicloEscolarId, usuario, usuario,
                })
                .toList();
        jdbc.batchUpdate("INSERT INTO ades_disponibilidad_docente " +
                "(id, profesor_id, dia_semana, hora_inicio, hora_fin, disponible, motivo_no_disponible, ciclo_escolar_id, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", batchArgs);
    }

    @Override
    public void updateProfesorHoras(UUID profesorId, Double horasSemanaMax, Double horasFrenteGrupo, String usuario) {
        StringBuilder sql = new StringBuilder("UPDATE ades_profesores SET usuario_modificacion = ?, row_version = row_version + 1 ");
        List<Object> params = new ArrayList<>();
        params.add(usuario);
        if (horasSemanaMax != null) { sql.append(", horas_semana_max = ? "); params.add(horasSemanaMax); }
        if (horasFrenteGrupo != null) { sql.append(", horas_frente_grupo = ? "); params.add(horasFrenteGrupo); }
        sql.append("WHERE id = ?");
        params.add(profesorId);
        jdbc.update(sql.toString(), params.toArray());
    }

    @Override
    public Optional<DisponibilidadDocente> findById(UUID id) {
        return jpa.findById(id).filter(DisponibilidadDocente::getIsActive);
    }

    @Override
    public DisponibilidadDocente save(DisponibilidadDocente slot) {
        return jpa.save(slot);
    }

    @Override
    public List<Map<String, Object>> resumen(UUID profesorId, UUID cicloEscolarId) {
        StringBuilder sq = new StringBuilder(
                "SELECT dia_semana, hora_inicio, hora_fin, disponible " +
                "FROM ades_disponibilidad_docente " +
                "WHERE profesor_id = ? AND is_active = TRUE ");
        List<Object> params = new ArrayList<>();
        params.add(profesorId);
        if (cicloEscolarId != null) {
            sq.append("AND ciclo_escolar_id = ? ");
            params.add(cicloEscolarId);
        } else {
            sq.append("AND ciclo_escolar_id IS NULL ");
        }
        sq.append("ORDER BY dia_semana, hora_inicio");
        return jdbc.queryForList(sq.toString(), params.toArray());
    }

    @Override
    public Map<String, Object> getProfesorHoras(UUID profesorId) {
        return jdbc.queryForMap(
                "SELECT horas_semana_max, horas_frente_grupo FROM ades_profesores WHERE id = ?",
                profesorId);
    }

    @Override
    public List<Map<String, Object>> cobertura(UUID cicloId, UUID plantelId) {
        StringBuilder sq = new StringBuilder(
                "SELECT p.id, per.nombre, per.apellido_paterno, COUNT(dd.id) AS slots_registrados " +
                "FROM ades_profesores p " +
                "JOIN ades_personas per ON per.id = p.persona_id " +
                "LEFT JOIN ades_disponibilidad_docente dd ON dd.profesor_id = p.id " +
                "AND dd.ciclo_escolar_id = ? AND dd.is_active = TRUE " +
                "WHERE p.is_active = TRUE ");
        List<Object> params = new ArrayList<>();
        params.add(cicloId);
        if (plantelId != null) { sq.append("AND p.plantel_id = ? "); params.add(plantelId); }
        sq.append("GROUP BY p.id, per.nombre, per.apellido_paterno " +
                "ORDER BY per.apellido_paterno, per.nombre");
        return jdbc.queryForList(sq.toString(), params.toArray());
    }
}
