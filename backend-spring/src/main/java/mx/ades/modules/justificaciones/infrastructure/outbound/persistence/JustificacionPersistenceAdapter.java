package mx.ades.modules.justificaciones.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.justificaciones.domain.model.EstadoJustificacion;
import mx.ades.modules.justificaciones.domain.model.TipoJustificacion;
import mx.ades.modules.justificaciones.domain.port.out.JustificacionRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JustificacionPersistenceAdapter implements JustificacionRepositoryPort {

    private final JdbcTemplate jdbc;

    @Override
    public Optional<Map<String, Object>> findById(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, estado FROM ades_justificaciones_falta WHERE id = ? AND is_active = TRUE",
                id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public boolean existsByAsistenciaId(UUID asistenciaId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id FROM ades_justificaciones_falta WHERE asistencia_id = ? AND is_active = TRUE",
                asistenciaId);
        return !rows.isEmpty();
    }

    @Override
    public boolean asistenciaJustificable(UUID asistenciaId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id FROM ades_asistencias WHERE id = ? AND is_active = TRUE " +
                "AND estatus_asistencia <> 'PRESENTE'",
                asistenciaId);
        return !rows.isEmpty();
    }

    @Override
    public UUID create(UUID asistenciaId, TipoJustificacion tipo, String motivo,
                       String documentoUrl, UUID usuarioId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_justificaciones_falta " +
                "(id, asistencia_id, tipo_justificacion, motivo, documento_url, estado, " +
                "usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, 'PENDIENTE', ?, ?)",
                id, asistenciaId, tipo.name(), motivo, documentoUrl,
                usuarioId.toString(), usuarioId.toString());
        return id;
    }

    @Override
    public void linkToAsistencia(UUID asistenciaId, UUID justificacionId) {
        jdbc.update("UPDATE ades_asistencias SET justificacion_id = ? WHERE id = ?",
                justificacionId, asistenciaId);
    }

    @Override
    public String resolve(UUID id, EstadoJustificacion estado, UUID aprobadaPor,
                          String motivoRechazo, String usuarioMod) {
        jdbc.update(
                "UPDATE ades_justificaciones_falta " +
                "SET estado = ?, aprobada_por = ?, fecha_resolucion = CURRENT_TIMESTAMP, " +
                "motivo_rechazo = ?, usuario_modificacion = ? " +
                "WHERE id = ?",
                estado.name(), aprobadaPor, motivoRechazo, usuarioMod, id);
        return estado.name();
    }

    @Override
    public List<Map<String, Object>> list(UUID estudianteId, String estado, UUID grupoId) {
        StringBuilder q = new StringBuilder(
                "SELECT j.id, j.asistencia_id, j.tipo_justificacion, j.motivo, " +
                "j.documento_url, j.estado, j.motivo_rechazo, j.fecha_resolucion, " +
                "j.fecha_creacion, " +
                "a.estudiante_id, " +
                "p.nombre || ' ' || p.apellido_paterno AS alumno_nombre, " +
                "a.estatus_asistencia, " +
                "cl.fecha_clase " +
                "FROM ades_justificaciones_falta j " +
                "JOIN ades_asistencias a  ON a.id = j.asistencia_id " +
                "JOIN ades_estudiantes e  ON e.id = a.estudiante_id " +
                "JOIN ades_personas p     ON p.id = e.persona_id " +
                "LEFT JOIN ades_clases cl ON cl.id = a.clase_id " +
                "WHERE j.is_active = TRUE ");

        List<Object> params = new ArrayList<>();
        if (estudianteId != null) { q.append("AND a.estudiante_id = ? "); params.add(estudianteId); }
        if (estado != null && !estado.isBlank()) { q.append("AND j.estado = ? "); params.add(estado.toUpperCase()); }
        if (grupoId != null) { q.append("AND cl.grupo_id = ? "); params.add(grupoId); }
        q.append("ORDER BY j.fecha_creacion DESC");

        return jdbc.queryForList(q.toString(), params.toArray());
    }
}
