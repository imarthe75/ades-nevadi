package mx.ades.modules.evaluaciones;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TareaService {

    private final TareaRepository repository;
    private final TareaEntregaRepository entregaRepository;
    private final JdbcTemplate jdbcTemplate;

    public JdbcTemplate getJdbcTemplate() {
        return this.jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getActividadesDeGrupo(UUID grupoId, UUID materiaId, UUID periodoId, String tipoItem) {
        String query = """
            SELECT t.id, t.titulo, t.descripcion, t.tipo_item,
                   t.fecha_asignacion, t.fecha_entrega, t.fecha_examen,
                   t.puntaje_maximo, t.permite_entrega_tarde,
                   t.instrucciones_url,
                   m.nombre_materia,
                   te_stats.total_alumnos,
                   te_stats.entregadas,
                   te_stats.calificadas,
                   pe.nombre_periodo,
                   tm.nombre_tema
              FROM ades_tareas t
              JOIN ades_materias m ON m.id = t.materia_id
              LEFT JOIN ades_periodos_evaluacion pe ON pe.id = t.periodo_evaluacion_id
              LEFT JOIN ades_temas tm ON tm.id = t.tema_id
              LEFT JOIN LATERAL (
                  SELECT COUNT(*) AS total_alumnos,
                         COUNT(*) FILTER (WHERE te.estatus_entrega IN ('ENTREGADA','CALIFICADA')) AS entregadas,
                         COUNT(*) FILTER (WHERE te.estatus_entrega = 'CALIFICADA') AS calificadas
                    FROM ades_tareas_entregas te
                   WHERE te.tarea_id = t.id
              ) te_stats ON TRUE
             WHERE t.grupo_id = ?::uuid AND t.is_active = TRUE
        """;

        List<Object> params = new ArrayList<>();
        params.add(grupoId.toString());

        if (materiaId != null) {
            query += " AND t.materia_id = ?::uuid";
            params.add(materiaId.toString());
        }
        if (periodoId != null) {
            query += " AND t.periodo_evaluacion_id = ?::uuid";
            params.add(periodoId.toString());
        }
        if (tipoItem != null && !tipoItem.isBlank()) {
            query += " AND t.tipo_item = ?";
            params.add(tipoItem);
        }

        query += " ORDER BY t.fecha_entrega, t.tipo_item";

        return jdbcTemplate.queryForList(query, params.toArray());
    }

    @Transactional
    public Map<String, Object> crearActividad(Tarea tarea) {
        Tarea saved = repository.save(tarea);

        // Fetch active students in the group
        List<UUID> estudianteIds = jdbcTemplate.queryForList(
                "SELECT estudiante_id FROM ades_inscripciones WHERE grupo_id = ?::uuid AND is_active = TRUE",
                UUID.class, tarea.getGrupoId().toString());

        int slotsCreados = 0;
        for (UUID estId : estudianteIds) {
            // We use native sql for ON CONFLICT DO NOTHING just in case
            jdbcTemplate.update(
                    "INSERT INTO ades_tareas_entregas (tarea_id, estudiante_id, estatus_entrega) " +
                            "VALUES (?::uuid, ?::uuid, 'PENDIENTE') ON CONFLICT (tarea_id, estudiante_id) DO NOTHING",
                    saved.getId().toString(), estId.toString());
            slotsCreados++;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", saved.getId().toString());
        response.put("slots_creados", slotsCreados);
        response.put("message", "Actividad creada y slots generados");
        return response;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getEntregasDeActividad(UUID actividadId) {
        String query = """
            SELECT te.id, te.estudiante_id, te.estatus_entrega,
                   te.fecha_entrega, te.es_tarde,
                   te.calificacion_obtenida, te.comentario_profesor,
                   te.archivo_url,
                   p.nombre || ' ' || p.apellido_paterno AS alumno_nombre,
                   est.numero_matricula
              FROM ades_tareas_entregas te
              JOIN ades_estudiantes est ON est.id = te.estudiante_id
              JOIN ades_personas p ON p.id = est.persona_id
             WHERE te.tarea_id = ?::uuid
             ORDER BY p.apellido_paterno, p.nombre
        """;
        return jdbcTemplate.queryForList(query, actividadId.toString());
    }

    @Transactional
    public int calificarMasivo(UUID actividadId, List<CalificarMasivoItem> items, UUID userId) {
        int actualizados = 0;
        for (CalificarMasivoItem item : items) {
            int rows = jdbcTemplate.update("""
                UPDATE ades_tareas_entregas
                   SET calificacion_obtenida = ?,
                       comentario_profesor = ?,
                       calificado_por = ?::uuid,
                       fecha_calificacion_docente = now(),
                       estatus_entrega = 'CALIFICADA',
                       fecha_modificacion = now(),
                       row_version = row_version + 1
                 WHERE tarea_id = ?::uuid AND estudiante_id = ?::uuid
            """,
                    item.calificacion(),
                    item.comentario(),
                    userId.toString(),
                    actividadId.toString(),
                    item.alumnoId().toString());
            actualizados += rows;
        }
        return actualizados;
    }

    public record CalificarMasivoItem(
            UUID alumnoId,
            BigDecimal calificacion,
            String comentario
    ) {}
}
