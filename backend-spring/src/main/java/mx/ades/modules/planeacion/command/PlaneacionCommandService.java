package mx.ades.modules.planeacion.command;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.planeacion.domain.model.EstadoTema;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Escrituras de planeación — valida transiciones de EstadoTema antes de persistir.
 */
@Service
@RequiredArgsConstructor
public class PlaneacionCommandService {

    private final JdbcTemplate jdbc;

    @Transactional
    public Map<String, Object> crearPlaneacion(UUID grupoId, UUID temaId,
                                               LocalDate fecha, String descripcion, String recursos) {
        String sql = """
            INSERT INTO ades_planeacion_clases
                (grupo_id, tema_id, fecha_planeada, descripcion_actividades, recursos_didacticos)
            VALUES (?::uuid, ?::uuid, ?, ?, ?)
            ON CONFLICT (grupo_id, tema_id) WHERE is_active = TRUE
                DO UPDATE SET fecha_planeada           = EXCLUDED.fecha_planeada,
                              descripcion_actividades   = EXCLUDED.descripcion_actividades,
                              recursos_didacticos       = EXCLUDED.recursos_didacticos
            RETURNING id
            """;
        UUID id = jdbc.queryForObject(sql, UUID.class,
                grupoId.toString(), temaId.toString(), fecha, descripcion, recursos);
        return Map.of("id", id, "estado", EstadoTema.PLANEADO.name());
    }

    /**
     * Transición PLANEADO → IMPARTIDO.
     * Valida que la planeacion_clase exista antes de registrar el avance.
     */
    @Transactional
    public Map<String, Object> completarTema(UUID planeacionId, UUID claseId,
                                             LocalDate fecha, String comentarios) {
        boolean existe = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM ades_planeacion_clases WHERE id = ?::uuid AND is_active = TRUE)",
                Boolean.class, planeacionId.toString()));

        if (!existe) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Planeacion clase no encontrada: " + planeacionId);

        String sql = """
            INSERT INTO ades_avance_planificacion
                (planeacion_clase_id, clase_id, fecha_ejecucion, es_completado, comentarios_profesor)
            VALUES (?::uuid, ?::uuid, ?, TRUE, ?)
            ON CONFLICT (planeacion_clase_id)
                DO UPDATE SET es_completado        = TRUE,
                              fecha_ejecucion       = EXCLUDED.fecha_ejecucion,
                              comentarios_profesor  = EXCLUDED.comentarios_profesor
            RETURNING id
            """;
        UUID avanceId = jdbc.queryForObject(sql, UUID.class,
                planeacionId.toString(), claseId != null ? claseId.toString() : null,
                fecha, comentarios);
        return Map.of("avance_id", avanceId, "estado", EstadoTema.IMPARTIDO.name());
    }

    @Transactional
    public void eliminarPlaneacion(UUID planeacionId) {
        jdbc.update("UPDATE ades_planeacion_clases SET is_active = FALSE WHERE id = ?::uuid",
                planeacionId.toString());
    }

    /**
     * OA-012: cuando una clase se marca SUSPENDIDA, marca como pendiente_reprogramar
     * los temas planeados para esa fecha+grupo — en vez de perderse silenciosamente.
     * Llamado desde ClaseService al detectar el cambio de estatus_clase.
     */
    @Transactional
    public void marcarPendientesPorSuspension(UUID grupoId, LocalDate fecha) {
        jdbc.update("""
            UPDATE ades_planeacion_clases
               SET pendiente_reprogramar = TRUE
             WHERE grupo_id = ?::uuid AND fecha_planeada = ? AND is_active = TRUE
            """, grupoId.toString(), fecha);
    }

    /** OA-012: reprograma un tema pendiente a una nueva fecha, limpiando el flag. */
    @Transactional
    public void reprogramar(UUID planeacionId, LocalDate nuevaFecha) {
        int rows = jdbc.update("""
            UPDATE ades_planeacion_clases
               SET fecha_planeada = ?, pendiente_reprogramar = FALSE
             WHERE id = ?::uuid AND is_active = TRUE
            """, nuevaFecha, planeacionId.toString());
        if (rows == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Planeacion clase no encontrada: " + planeacionId);
    }
}
