package mx.ades.modules.gradebook;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Component
public class ActividadesWriteService {

    private final JdbcTemplate jdbc;

    public ActividadesWriteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record CrearActividadResult(UUID tareaId, int slotsCreados) {}

    @Transactional
    public CrearActividadResult crearActividad(
            String titulo, String descripcion, UUID grupoId, UUID materiaId,
            UUID periodoEvaluacionId, String tipoItem, UUID temaId, UUID planTrabajoId,
            UUID rubricaId, LocalDate fechaAsignacion, LocalDate fechaEntrega,
            LocalDate fechaExamen, Double puntajeMaximo, String instruccionesUrl,
            Boolean permiteEntregaTarde, String usuario) {

        UUID tareaId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_tareas " +
                "(id, titulo, descripcion, grupo_id, materia_id, periodo_evaluacion_id, tipo_item, tema_id, " +
                "plan_trabajo_id, rubrica_id, fecha_asignacion, fecha_entrega, fecha_examen, puntaje_maximo, " +
                "instrucciones_url, permite_entrega_tarde, origen, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'MANUAL', ?, ?)",
                tareaId, titulo, descripcion, grupoId, materiaId,
                periodoEvaluacionId, tipoItem, temaId, planTrabajoId,
                rubricaId, fechaAsignacion, fechaEntrega, fechaExamen,
                puntajeMaximo, instruccionesUrl, permiteEntregaTarde,
                usuario, usuario
        );

        List<Map<String, Object>> alumnos = jdbc.queryForList(
                "SELECT estudiante_id FROM ades_inscripciones WHERE grupo_id = ? AND is_active = TRUE",
                grupoId
        );

        // Auditoría 2026-07-20 (principio "preferir operaciones Bulk"): un INSERT por
        // alumno al crear una actividad — un grupo típico son 20-40 alumnos, eso eran
        // 20-40 viajes de red secuenciales. batchUpdate() lo hace en uno solo.
        if (!alumnos.isEmpty()) {
            List<Object[]> batchArgs = alumnos.stream()
                    .map(a -> new Object[]{UUID.randomUUID(), tareaId, a.get("estudiante_id"), usuario, usuario})
                    .toList();
            jdbc.batchUpdate(
                    "INSERT INTO ades_tareas_entregas " +
                    "(id, tarea_id, estudiante_id, estatus_entrega, usuario_creacion, usuario_modificacion) " +
                    "VALUES (?, ?, ?, 'PENDIENTE', ?, ?) " +
                    "ON CONFLICT (tarea_id, estudiante_id) DO NOTHING",
                    batchArgs
            );
        }

        return new CrearActividadResult(tareaId, alumnos.size());
    }

    @Transactional
    public int calificarMasivo(UUID actividadId, List<Map<String, Object>> items, UUID calificadoPor, String usuario) {
        if (items.isEmpty()) return 0;
        // Auditoría 2026-07-20 (principio "preferir operaciones Bulk"): mismo patrón que
        // crearActividad() — un UPDATE por alumno calificado en un loop secuencial.
        List<Object[]> batchArgs = items.stream()
                .map(item -> new Object[]{
                        item.get("calificacion"), item.get("comentario"), calificadoPor, usuario,
                        actividadId, item.get("alumnoId"),
                })
                .toList();
        int[] rows = jdbc.batchUpdate(
                "UPDATE ades_tareas_entregas " +
                "SET calificacion_obtenida = ?, " +
                "    comentario_profesor = ?, " +
                "    calificado_por = ?, " +
                "    fecha_calificacion_docente = CURRENT_TIMESTAMP, " +
                "    estatus_entrega = 'CALIFICADA', " +
                "    fecha_modificacion = CURRENT_TIMESTAMP, " +
                "    row_version = row_version + 1, " +
                "    usuario_modificacion = ? " +
                "WHERE tarea_id = ? AND estudiante_id = ?",
                batchArgs
        );
        int actualizados = 0;
        for (int r : rows) actualizados += Math.max(r, 0);
        return actualizados;
    }
}
