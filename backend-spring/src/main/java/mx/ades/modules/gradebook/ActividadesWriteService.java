package mx.ades.modules.gradebook;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@Component
public class ActividadesWriteService {

    private final JdbcTemplate jdbc;

    public ActividadesWriteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record CrearActividadResult(UUID tareaId, int slotsCreados) {}

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

        for (Map<String, Object> a : alumnos) {
            UUID estudianteId = (UUID) a.get("estudiante_id");
            jdbc.update(
                    "INSERT INTO ades_tareas_entregas " +
                    "(id, tarea_id, estudiante_id, estatus_entrega, usuario_creacion, usuario_modificacion) " +
                    "VALUES (?, ?, ?, 'PENDIENTE', ?, ?) " +
                    "ON CONFLICT (tarea_id, estudiante_id) DO NOTHING",
                    UUID.randomUUID(), tareaId, estudianteId, usuario, usuario
            );
        }

        return new CrearActividadResult(tareaId, alumnos.size());
    }

    public int calificarMasivo(UUID actividadId, List<Map<String, Object>> items, UUID calificadoPor, String usuario) {
        int actualizados = 0;
        for (Map<String, Object> item : items) {
            int r = jdbc.update(
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
                    item.get("calificacion"), item.get("comentario"), calificadoPor, usuario,
                    actividadId, item.get("alumnoId")
            );
            actualizados += r;
        }
        return actualizados;
    }
}
