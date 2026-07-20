package mx.ades.modules.evaluaciones.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.evaluaciones.domain.model.ItemCalificacion;
import mx.ades.modules.evaluaciones.domain.port.out.TareaRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JPA/JDBC que implementa {@link TareaRepositoryPort}.
 * <p>Usa JPA para persistir la entidad {@code TareaEntity} y JDBC para operaciones
 * masivas sobre {@code ades_tareas_entregas} (slots con upsert y calificación masiva).</p>
 *
 * @author ADES
 * @since 2026
 */
@Component
@RequiredArgsConstructor
public class TareaPersistenceAdapter implements TareaRepositoryPort {

    private final TareaJpaRepository jpaRepository;
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public UUID guardar(TareaDatos datos) {
        TareaEntity entity = new TareaEntity();
        entity.setTitulo(datos.titulo());
        entity.setDescripcion(datos.descripcion());
        entity.setGrupoId(datos.grupoId());
        entity.setMateriaId(datos.materiaId());
        entity.setTemaId(datos.temaId());
        entity.setPeriodoEvaluacionId(datos.periodoEvaluacionId());
        entity.setFechaAsignacion(datos.fechaAsignacion());
        entity.setFechaEntrega(datos.fechaEntrega());
        entity.setPuntajeMaximo(datos.puntajeMaximo());
        entity.setTipoItem(datos.tipoItem().name().toLowerCase());
        entity.setPermiteEntregaTarde(datos.permiteEntregaTarde());
        entity.setInstruccionesUrl(datos.instruccionesUrl());
        entity.setOrigen("MANUAL");
        entity.setIsActive(true);
        entity.setUsuarioCreacion(datos.creadorUsername());
        entity.setUsuarioModificacion(datos.creadorUsername());

        return jpaRepository.save(entity).getId();
    }

    @Override
    @Transactional
    public int crearSlots(UUID tareaId, List<UUID> estudianteIds) {
        if (estudianteIds.isEmpty()) return 0;
        // Auditoría 2026-07-20 (principio "preferir operaciones Bulk"): un INSERT por
        // alumno en un loop secuencial son N round-trips reales a la BD por cada tarea
        // creada (un grupo típico son 20-40 alumnos). jdbc.batchUpdate() envía todos los
        // parámetros en un solo batch — mismo resultado (ON CONFLICT DO NOTHING se
        // respeta por fila), una sola ida y vuelta de red.
        List<Object[]> batchArgs = estudianteIds.stream()
                .map(id -> new Object[]{tareaId.toString(), id.toString()})
                .toList();
        int[] rows = jdbc.batchUpdate("""
            INSERT INTO ades_tareas_entregas (tarea_id, estudiante_id, estatus_entrega)
            VALUES (?::uuid, ?::uuid, 'PENDIENTE')
            ON CONFLICT (tarea_id, estudiante_id) DO NOTHING
            """, batchArgs);
        int creados = 0;
        for (int r : rows) creados += Math.max(r, 0);
        return creados;
    }

    @Override
    public List<UUID> findEstudiantesEnGrupo(UUID grupoId) {
        return jdbc.queryForList(
                "SELECT estudiante_id FROM ades_inscripciones WHERE grupo_id = ?::uuid AND is_active = TRUE",
                UUID.class, grupoId.toString());
    }

    @Override
    public Optional<BigDecimal> findPuntajeMaximo(UUID tareaId) {
        List<BigDecimal> rows = jdbc.queryForList(
                "SELECT puntaje_maximo FROM ades_tareas WHERE id = ?::uuid AND is_active = TRUE",
                BigDecimal.class, tareaId.toString());
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }

    @Override
    @Transactional
    public int calificarItems(UUID tareaId, List<ItemCalificacion> items, UUID calificadorId) {
        if (items.isEmpty()) return 0;
        // Auditoría 2026-07-20 (principio "preferir operaciones Bulk"): calificar un
        // grupo completo (20-40 alumnos) hacía un UPDATE individual por alumno —
        // convertido a batchUpdate, un solo viaje de red para toda la calificación
        // masiva en vez de N.
        List<Object[]> batchArgs = items.stream()
                .map(item -> new Object[]{
                        item.calificacion(),
                        item.comentario(),
                        calificadorId.toString(),
                        tareaId.toString(),
                        item.estudianteId().toString(),
                })
                .toList();
        int[] rows = jdbc.batchUpdate("""
            UPDATE ades_tareas_entregas
               SET calificacion_obtenida     = ?,
                   comentario_profesor        = ?,
                   calificado_por             = ?::uuid,
                   fecha_calificacion_docente = now(),
                   estatus_entrega            = 'CALIFICADA',
                   fecha_modificacion         = now(),
                   row_version                = row_version + 1
             WHERE tarea_id = ?::uuid AND estudiante_id = ?::uuid
            """, batchArgs);
        int actualizados = 0;
        for (int r : rows) actualizados += Math.max(r, 0);
        return actualizados;
    }
}
