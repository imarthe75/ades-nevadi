package mx.ades.modules.asistencias.query;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de consulta (lado lectura CQRS) para el módulo asistencias.
 *
 * <p>Provee listado y detalle de clases ({@code ades_clases}) con filtros dinámicos
 * por grupo, materia, profesor, rango de fechas y estatus.</p>
 *
 * @author ADES
 * @since 2026
 */
@Service
@RequiredArgsConstructor
public class ClaseQueryService {

    private static final String BASE_SELECT =
        "SELECT c.id, c.horario_id, c.grupo_id, c.materia_id, c.profesor_id, " +
        "c.fecha_clase, c.hora_inicio, c.hora_fin, c.tema_visto, c.observaciones, " +
        "c.estatus_clase, c.modalidad, c.is_active, c.row_version, c.fecha_creacion, c.fecha_modificacion, " +
        "c.usuario_creacion, c.usuario_modificacion, " +
        "g.nombre_grupo AS grupo_nombre, m.nombre_materia AS materia_nombre " +
        "FROM ades_clases c " +
        "LEFT JOIN ades_grupos g ON g.id = c.grupo_id " +
        "LEFT JOIN ades_materias m ON m.id = c.materia_id ";

    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> listar(UUID grupoId, UUID materiaId, UUID profesorId,
                                             LocalDate fechaDesde, LocalDate fechaHasta, String estatus) {
        StringBuilder sql = new StringBuilder(BASE_SELECT + "WHERE c.is_active = TRUE ");
        List<Object> params = new ArrayList<>();
        if (grupoId != null) { sql.append("AND c.grupo_id = ? "); params.add(grupoId); }
        if (materiaId != null) { sql.append("AND c.materia_id = ? "); params.add(materiaId); }
        if (profesorId != null) { sql.append("AND c.profesor_id = ? "); params.add(profesorId); }
        if (fechaDesde != null) { sql.append("AND c.fecha_clase >= ? "); params.add(fechaDesde); }
        if (fechaHasta != null) { sql.append("AND c.fecha_clase <= ? "); params.add(fechaHasta); }
        if (estatus != null && !estatus.isBlank()) { sql.append("AND c.estatus_clase = ? "); params.add(estatus.toUpperCase()); }
        sql.append("ORDER BY c.fecha_clase DESC, c.hora_inicio ASC");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public Map<String, Object> obtener(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(BASE_SELECT + "WHERE c.id = ?", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Clase no encontrada");
        return rows.get(0);
    }
}
