package mx.ades.modules.entregas.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.entregas.domain.port.in.CalificarEntregaUseCase;
import mx.ades.modules.entregas.domain.port.in.SubirEntregaUseCase;
import mx.ades.modules.entregas.domain.port.out.EntregaRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Adaptador JDBC que implementa {@link EntregaRepositoryPort}.
 * <p>Opera sobre {@code ades_tareas_entregas} con upsert en conflicto por (tarea_id, estudiante_id).</p>
 *
 * @author ADES
 * @since 2026
 */
@Component
@RequiredArgsConstructor
public class EntregaPersistenceAdapter implements EntregaRepositoryPort {

    private final JdbcTemplate jdbc;

    @Override
    public List<Map<String, Object>> listByAlumno(UUID alumnoId, UUID periodoId, UUID materiaId, boolean soloPendientes) {
        StringBuilder sql = new StringBuilder(
            "SELECT te.id, te.tarea_id, te.estatus_entrega, te.fecha_entrega, te.es_tarde, " +
            "te.calificacion_obtenida, te.comentario_profesor, te.archivo_url, te.fecha_calificacion_docente, " +
            "t.titulo, t.tipo_item, t.fecha_entrega AS fecha_limite, t.puntaje_maximo, " +
            "m.nombre_materia, pe.nombre_periodo, " +
            "(t.fecha_entrega < CURRENT_DATE AND te.estatus_entrega = 'PENDIENTE') AS vencida " +
            "FROM ades_tareas_entregas te " +
            "JOIN ades_tareas t ON t.id = te.tarea_id " +
            "JOIN ades_materias m ON m.id = t.materia_id " +
            "LEFT JOIN ades_periodos_evaluacion pe ON pe.id = t.periodo_evaluacion_id " +
            "WHERE te.estudiante_id = ? AND te.is_active = TRUE ");
        List<Object> params = new ArrayList<>();
        params.add(alumnoId);

        if (periodoId != null) { sql.append("AND t.periodo_evaluacion_id = ? "); params.add(periodoId); }
        if (materiaId != null) { sql.append("AND t.materia_id = ? "); params.add(materiaId); }
        if (soloPendientes) { sql.append("AND te.estatus_entrega = 'PENDIENTE' "); }
        sql.append("ORDER BY t.fecha_entrega DESC");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    @Override
    public List<Map<String, Object>> pendientesByGrupo(UUID grupoId, UUID materiaId) {
        StringBuilder sql = new StringBuilder(
            "SELECT te.id, te.estudiante_id, te.estatus_entrega, te.fecha_entrega, te.archivo_url, te.comentario_alumno, " +
            "t.titulo, t.tipo_item, t.fecha_entrega AS fecha_limite, t.id AS actividad_id, m.nombre_materia, " +
            "COALESCE(p.nombre_social, p.nombre) || ' ' || p.apellido_paterno AS alumno_nombre, est.matricula " +
            "FROM ades_tareas_entregas te " +
            "JOIN ades_tareas t ON t.id = te.tarea_id " +
            "JOIN ades_materias m ON m.id = t.materia_id " +
            "JOIN ades_estudiantes est ON est.id = te.estudiante_id " +
            "JOIN ades_personas p ON p.id = est.persona_id " +
            "WHERE t.grupo_id = ? AND te.estatus_entrega = 'ENTREGADA' AND te.is_active = TRUE ");
        List<Object> params = new ArrayList<>();
        params.add(grupoId);

        if (materiaId != null) { sql.append("AND t.materia_id = ? "); params.add(materiaId); }
        sql.append("ORDER BY t.fecha_entrega, p.apellido_paterno");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    @Override
    public void upsertEntrega(SubirEntregaUseCase.Command cmd) {
        jdbc.update(
            "INSERT INTO ades_tareas_entregas " +
            "(id, tarea_id, estudiante_id, fecha_entrega, comentario_alumno, archivo_url, estatus_entrega, usuario_creacion, usuario_modificacion) " +
            "VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, ?, 'ENTREGADA', ?, ?) " +
            "ON CONFLICT (tarea_id, estudiante_id) DO UPDATE " +
            "SET fecha_entrega = CURRENT_TIMESTAMP, " +
            "    comentario_alumno = EXCLUDED.comentario_alumno, " +
            "    archivo_url = COALESCE(EXCLUDED.archivo_url, ades_tareas_entregas.archivo_url), " +
            "    estatus_entrega = 'ENTREGADA', " +
            "    fecha_modificacion = CURRENT_TIMESTAMP, " +
            "    row_version = ades_tareas_entregas.row_version + 1, " +
            "    usuario_modificacion = EXCLUDED.usuario_modificacion",
            UUID.randomUUID(), cmd.tareaId(), cmd.alumnoId(), cmd.comentario(), cmd.archivoUrl(),
            cmd.usuario(), cmd.usuario());
    }

    @Override
    public int calificar(CalificarEntregaUseCase.Command cmd) {
        return jdbc.update(
            "UPDATE ades_tareas_entregas " +
            "SET calificacion_obtenida = ?, comentario_profesor = ?, calificado_por = ?, " +
            "    fecha_calificacion_docente = CURRENT_TIMESTAMP, estatus_entrega = 'CALIFICADA', " +
            "    fecha_modificacion = CURRENT_TIMESTAMP, row_version = row_version + 1, " +
            "    usuario_modificacion = ? " +
            "WHERE id = ? AND is_active = TRUE",
            cmd.calificacion(), cmd.comentario(), cmd.calificadoPor(), cmd.usuario(), cmd.entregaId());
    }

    @Override
    public int registrarExcusa(UUID entregaId, String motivo, String usuario) {
        return jdbc.update(
            "UPDATE ades_tareas_entregas " +
            "SET estatus_entrega = 'EXCUSA', comentario_profesor = ?, " +
            "    fecha_modificacion = CURRENT_TIMESTAMP, usuario_modificacion = ? " +
            "WHERE id = ?",
            motivo, usuario, entregaId);
    }

    @Override
    public int reabrir(UUID entregaId, String motivo, String usuario) {
        return jdbc.update(
            "UPDATE ades_tareas_entregas " +
            "SET estatus_entrega = 'PENDIENTE', calificacion_obtenida = NULL, " +
            "    comentario_profesor = ?, fecha_calificacion_docente = NULL, " +
            "    fecha_modificacion = CURRENT_TIMESTAMP, usuario_modificacion = ? " +
            "WHERE id = ?",
            motivo, usuario, entregaId);
    }
}
