package mx.ades.modules.foros.query;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de lectura CQRS para el módulo foros.
 * Provee consultas de foros, mensajes, respuestas y anuncios con filtros dinámicos.
 *
 * @author ADES
 * @since 2026
 */
@Service
@RequiredArgsConstructor
public class ForoQueryService {

    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> listar(UUID grupoId, UUID plantelId, UUID materiaId, String tipo, Integer nivelAcceso) {
        StringBuilder sql = new StringBuilder(
                "SELECT f.id, f.nombre, f.descripcion, f.tipo, f.es_moderado, f.materia_id, f.fecha_creacion, " +
                "g.nombre_grupo, pl.nombre_plantel, mat.nombre_materia, " +
                "(SELECT COUNT(*) FROM ades_mensajes_foro WHERE foro_id = f.id AND is_active = TRUE) as total_mensajes " +
                "FROM ades_foros f " +
                "LEFT JOIN ades_grupos g ON g.id = f.grupo_id " +
                "LEFT JOIN ades_planteles pl ON pl.id = f.plantel_id " +
                "LEFT JOIN ades_materias mat ON mat.id = f.materia_id " +
                "WHERE f.is_active = TRUE ");
        List<Object> params = new ArrayList<>();

        if (grupoId != null) { sql.append("AND f.grupo_id = ? "); params.add(grupoId); }
        if (plantelId != null) { sql.append("AND f.plantel_id = ? "); params.add(plantelId); }
        if (materiaId != null) { sql.append("AND f.materia_id = ? "); params.add(materiaId); }
        if (tipo != null && !tipo.isBlank()) { sql.append("AND f.tipo = ? "); params.add(tipo); }
        if (nivelAcceso != null && nivelAcceso == 5) {
            sql.append("AND f.tipo IN ('GENERAL', 'PLANTEL', 'GRUPO', 'TUTORES', 'MATERIA') ");
        }
        sql.append("ORDER BY f.tipo, f.nombre");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> listarMensajes(UUID foroId, int skip, int limit) {
        return jdbc.queryForList(
            "SELECT m.id, m.asunto, m.contenido, m.adjunto_url, m.fecha_creacion, m.usuario_creacion, " +
            "(SELECT COUNT(*) FROM ades_respuestas_foro WHERE mensaje_id = m.id AND is_active = TRUE) as respuestas " +
            "FROM ades_mensajes_foro m " +
            "WHERE m.foro_id = ? AND m.is_active = TRUE AND m.mensaje_padre_id IS NULL " +
            "ORDER BY m.fecha_creacion DESC LIMIT ? OFFSET ?",
            foroId, limit, skip);
    }

    public List<Map<String, Object>> listarRespuestas(UUID mensajeId) {
        return jdbc.queryForList(
            "SELECT id, contenido, adjunto_url, fecha_creacion, usuario_creacion " +
            "FROM ades_respuestas_foro WHERE mensaje_id = ? AND is_active = TRUE ORDER BY fecha_creacion",
            mensajeId);
    }

    public List<Map<String, Object>> listarAnuncios(UUID plantelId, String nivelEducativo, boolean soloVigentes) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, titulo, contenido, es_urgente, nivel_educativo, " +
                "fecha_inicio, fecha_fin, fecha_creacion, usuario_creacion " +
                "FROM ades_anuncios WHERE is_active = TRUE ");
        List<Object> params = new ArrayList<>();

        if (soloVigentes) sql.append("AND fecha_inicio <= CURRENT_DATE AND (fecha_fin IS NULL OR fecha_fin >= CURRENT_DATE) ");
        if (plantelId != null) { sql.append("AND (plantel_id = ? OR plantel_id IS NULL) "); params.add(plantelId); }
        if (nivelEducativo != null && !nivelEducativo.isBlank()) {
            sql.append("AND (nivel_educativo = ? OR nivel_educativo IS NULL) ");
            params.add(nivelEducativo);
        }
        sql.append("ORDER BY es_urgente DESC, fecha_creacion DESC");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }
}
