package mx.ades.modules.esquemas_ponderacion.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.esquemas_ponderacion.domain.model.ItemPonderacion;
import mx.ades.modules.esquemas_ponderacion.domain.port.in.ActualizarEsquemaUseCase;
import mx.ades.modules.esquemas_ponderacion.domain.port.in.CrearEsquemaUseCase;
import mx.ades.modules.esquemas_ponderacion.domain.port.out.EsquemaRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

/**
 * Adaptador JDBC que implementa {@link EsquemaRepositoryPort}.
 * <p>Persiste en {@code ades_esquemas_ponderacion} e {@code ades_items_ponderacion}.
 * La consulta {@code efectivo} prioriza el esquema específico de materia sobre el genérico de nivel.</p>
 *
 * @author ADES
 * @since 2026
 */
@Component
@RequiredArgsConstructor
public class EsquemaPersistenceAdapter implements EsquemaRepositoryPort {

    private final JdbcTemplate jdbc;

    @Override
    public List<Map<String, Object>> list(UUID nivelEducativoId, UUID materiaId, UUID profesorScopeId, UUID plantelScopeId) {
        StringBuilder sql = new StringBuilder(
            "SELECT ep.id, ep.nombre, ep.vigente_desde, ep.vigente_hasta, ep.es_nee, " +
            "ep.activo, ep.materia_id, ep.profesor_id, ep.plantel_id, ne.nombre_nivel, m.nombre_materia, " +
            "(SELECT json_agg(json_build_object(" +
            "  'id', ip.id, 'tipo_item', ip.tipo_item, 'nombre_personalizado', ip.nombre_personalizado, " +
            "  'peso_porcentaje', ip.peso_porcentaje, 'orden_display', ip.orden_display) " +
            " ORDER BY ip.orden_display) FROM ades_items_ponderacion ip WHERE ip.esquema_id = ep.id AND ip.is_active = TRUE) AS items " +
            "FROM ades_esquemas_ponderacion ep " +
            "JOIN ades_niveles_educativos ne ON ne.id = ep.nivel_educativo_id " +
            "LEFT JOIN ades_materias m ON m.id = ep.materia_id " +
            "WHERE ep.is_active = TRUE ");
        List<Object> params = new ArrayList<>();

        if (nivelEducativoId != null) { sql.append("AND ep.nivel_educativo_id = ? "); params.add(nivelEducativoId); }
        if (materiaId != null) { sql.append("AND (ep.materia_id = ? OR ep.materia_id IS NULL) "); params.add(materiaId); }
        // Visibilidad: institucional (profesor/plantel NULL) + lo propio del profesor/plantel del solicitante
        if (profesorScopeId != null) { sql.append("AND (ep.profesor_id IS NULL OR ep.profesor_id = ?) "); params.add(profesorScopeId); }
        if (plantelScopeId != null) { sql.append("AND (ep.plantel_id IS NULL OR ep.plantel_id = ?) "); params.add(plantelScopeId); }
        sql.append("ORDER BY ne.nombre_nivel, ep.vigente_desde DESC");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    @Override
    public Map<String, Object> efectivo(UUID materiaId) {
        String sql =
            "SELECT ep.id, ep.nombre, ep.vigente_desde, ep.vigente_hasta, ep.es_nee, ep.materia_id, " +
            "ne.nombre_nivel, ne.escala_maxima, ne.minimo_aprobatorio, " +
            "(SELECT json_agg(json_build_object(" +
            "  'id', ip.id, 'tipo_item', ip.tipo_item, 'nombre_personalizado', ip.nombre_personalizado, " +
            "  'peso_porcentaje', ip.peso_porcentaje, 'orden_display', ip.orden_display) " +
            " ORDER BY ip.orden_display) FROM ades_items_ponderacion ip WHERE ip.esquema_id = ep.id AND ip.is_active = TRUE) AS items " +
            "FROM ades_esquemas_ponderacion ep " +
            "JOIN ades_niveles_educativos ne ON ne.id = ep.nivel_educativo_id " +
            "JOIN ades_materias m ON m.nivel_educativo_id = ne.id " +
            "WHERE m.id = ? AND ep.activo = TRUE " +
            "AND (ep.vigente_hasta IS NULL OR ep.vigente_hasta >= CURRENT_DATE) " +
            "AND ep.vigente_desde <= CURRENT_DATE " +
            "ORDER BY (ep.materia_id = ?) DESC NULLS LAST, ep.vigente_desde DESC LIMIT 1";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, materiaId, materiaId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay esquema de ponderación para esta materia");
        return rows.get(0);
    }

    @Override
    public UUID insertEsquema(CrearEsquemaUseCase.Command cmd) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ades_esquemas_ponderacion " +
            "(id, nombre, nivel_educativo_id, materia_id, vigente_desde, vigente_hasta, creado_por, activo, usuario_creacion, usuario_modificacion, es_nee, profesor_id, plantel_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?, ?, ?)",
            id, cmd.nombre(), cmd.nivelEducativoId(), cmd.materiaId(),
            cmd.vigenteDesde(), cmd.vigenteHasta(), cmd.creadoPorId(), cmd.usuario(), cmd.usuario(), cmd.esNee(),
            cmd.profesorId(), cmd.plantelId());
        return id;
    }

    @Override
    public void insertItems(UUID esquemaId, CrearEsquemaUseCase.Command cmd) {
        if (cmd.items().isEmpty()) return;
        // Auditoría 2026-07-20 (principio "preferir operaciones Bulk"): un INSERT por
        // item de ponderación — batchUpdate en vez de un loop de escrituras individuales.
        List<Object[]> batchArgs = cmd.items().stream()
                .map(i -> new Object[]{
                        UUID.randomUUID(), esquemaId, i.tipoItem(), i.nombrePersonalizado(),
                        BigDecimal.valueOf(i.pesoPorcentaje()), i.ordenDisplay(), cmd.usuario(), cmd.usuario(),
                })
                .toList();
        jdbc.batchUpdate(
                "INSERT INTO ades_items_ponderacion " +
                "(id, esquema_id, tipo_item, nombre_personalizado, peso_porcentaje, orden_display, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                batchArgs);
    }

    @Override
    public int updateEsquema(ActualizarEsquemaUseCase.Command cmd) {
        return jdbc.update(
            "UPDATE ades_esquemas_ponderacion " +
            "SET nombre = ?, nivel_educativo_id = ?, materia_id = ?, vigente_desde = ?, vigente_hasta = ?, es_nee = ?, " +
            "profesor_id = ?, plantel_id = ?, " +
            "usuario_modificacion = ?, row_version = row_version + 1, fecha_modificacion = CURRENT_TIMESTAMP " +
            "WHERE id = ? AND is_active = TRUE",
            cmd.nombre(), cmd.nivelEducativoId(), cmd.materiaId(),
            cmd.vigenteDesde(), cmd.vigenteHasta(), cmd.esNee(), cmd.profesorId(), cmd.plantelId(),
            cmd.usuario(), cmd.esquemaId());
    }

    @Override
    public void softDeleteItems(UUID esquemaId) {
        jdbc.update("UPDATE ades_items_ponderacion SET is_active = FALSE WHERE esquema_id = ?", esquemaId);
    }

    @Override
    public void insertItems(UUID esquemaId, ActualizarEsquemaUseCase.Command cmd) {
        if (cmd.items().isEmpty()) return;
        List<Object[]> batchArgs = cmd.items().stream()
                .map(i -> new Object[]{
                        UUID.randomUUID(), esquemaId, i.tipoItem(), i.nombrePersonalizado(),
                        BigDecimal.valueOf(i.pesoPorcentaje()), i.ordenDisplay(), cmd.usuario(), cmd.usuario(),
                })
                .toList();
        jdbc.batchUpdate(
                "INSERT INTO ades_items_ponderacion " +
                "(id, esquema_id, tipo_item, nombre_personalizado, peso_porcentaje, orden_display, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                batchArgs);
    }

    @Override
    public int desactivar(UUID esquemaId, String usuario) {
        return jdbc.update(
            "UPDATE ades_esquemas_ponderacion " +
            "SET activo = FALSE, is_active = FALSE, usuario_modificacion = ?, fecha_modificacion = CURRENT_TIMESTAMP " +
            "WHERE id = ?",
            usuario, esquemaId);
    }

    @Override
    public UUID resolverProfesorIdPorPersona(UUID personaId) {
        List<UUID> ids = jdbc.query(
                "SELECT id FROM ades_profesores WHERE persona_id = ? AND is_active = TRUE",
                (rs, i) -> (UUID) rs.getObject("id"), personaId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    @Override
    public Map<String, Object> scopeDe(UUID esquemaId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT profesor_id, plantel_id FROM ades_esquemas_ponderacion WHERE id = ? AND is_active = TRUE",
                esquemaId);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
