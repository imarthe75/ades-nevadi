package mx.ades.modules.evaluaciones;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TareaEntregaService {

    private final TareaEntregaRepository repository;
    private final MinioService minioService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getEntregasDelAlumno(UUID alumnoId, UUID periodoId, UUID materiaId, Boolean soloPendientes) {
        String query = """
             SELECT te.id, te.tarea_id, te.estatus_entrega,
                    te.fecha_entrega, te.es_tarde,
                    te.calificacion_obtenida, te.comentario_profesor,
                    te.archivo_url,
                    te.fecha_calificacion_docente,
                    te.plagio_porcentaje, te.plagio_reporte_url,
                    te.feedback_audio_url, te.feedback_video_url,
                    t.titulo, t.tipo_item, t.fecha_entrega AS fecha_limite,
                    t.puntaje_maximo,
                    m.nombre_materia,
                    pe.nombre_periodo,
                    (t.fecha_entrega < CURRENT_DATE AND te.estatus_entrega = 'PENDIENTE') AS vencida
               FROM ades_tareas_entregas te
               JOIN ades_tareas t ON t.id = te.tarea_id
               JOIN ades_materias m ON m.id = t.materia_id
               LEFT JOIN ades_periodos_evaluacion pe ON pe.id = t.periodo_evaluacion_id
              WHERE te.estudiante_id = ?::uuid AND te.is_active = TRUE
        """;

        List<Object> params = new ArrayList<>();
        params.add(alumnoId.toString());

        if (periodoId != null) {
            query += " AND t.periodo_evaluacion_id = ?::uuid";
            params.add(periodoId.toString());
        }
        if (materiaId != null) {
            query += " AND t.materia_id = ?::uuid";
            params.add(materiaId.toString());
        }
        if (soloPendientes != null && soloPendientes) {
            query += " AND te.estatus_entrega = 'PENDIENTE'";
        }

        query += " ORDER BY t.fecha_entrega DESC";

        return jdbcTemplate.queryForList(query, params.toArray());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPendientesDelGrupo(UUID grupoId, UUID materiaId) {
        String query = """
             SELECT te.id, te.estudiante_id, te.estatus_entrega,
                    te.fecha_entrega, te.archivo_url, te.comentario_alumno,
                    te.plagio_porcentaje, te.plagio_reporte_url,
                    te.feedback_audio_url, te.feedback_video_url,
                    t.titulo, t.tipo_item, t.fecha_entrega AS fecha_limite,
                    t.id AS actividad_id,
                    m.nombre_materia,
                    p.nombre || ' ' || p.apellido_paterno AS alumno_nombre,
                    est.numero_matricula
               FROM ades_tareas_entregas te
               JOIN ades_tareas t ON t.id = te.tarea_id
               JOIN ades_materias m ON m.id = t.materia_id
               JOIN ades_estudiantes est ON est.id = te.estudiante_id
               JOIN ades_personas p ON p.id = est.persona_id
              WHERE t.grupo_id = ?::uuid AND te.estatus_entrega = 'ENTREGADA' AND te.is_active = TRUE
        """;

        List<Object> params = new ArrayList<>();
        params.add(grupoId.toString());

        if (materiaId != null) {
            query += " AND t.materia_id = ?::uuid";
            params.add(materiaId.toString());
        }

        query += " ORDER BY t.fecha_entrega, p.apellido_paterno";

        return jdbcTemplate.queryForList(query, params.toArray());
    }

    @Transactional
    public Map<String, Object> subirEntrega(UUID tareaId, UUID alumnoId, String comentario, MultipartFile file) {
        String archivoUrl = minioService.uploadFile(tareaId, alumnoId, file);

        // Native SQL to perform UPSERT (INSERT ON CONFLICT UPDATE)
        String sql = """
            INSERT INTO ades_tareas_entregas
                   (tarea_id, estudiante_id, fecha_entrega, comentario_alumno,
                    archivo_url, estatus_entrega)
            VALUES (?::uuid, ?::uuid, now(), ?, ?, 'ENTREGADA')
            ON CONFLICT (tarea_id, estudiante_id) DO UPDATE
               SET fecha_entrega    = now(),
                   comentario_alumno = EXCLUDED.comentario_alumno,
                   archivo_url      = COALESCE(EXCLUDED.archivo_url, ades_tareas_entregas.archivo_url),
                   estatus_entrega  = 'ENTREGADA',
                   fecha_modificacion   = now(),
                   row_version      = ades_tareas_entregas.row_version + 1
        """;
        jdbcTemplate.update(sql, 
                tareaId.toString(), 
                alumnoId.toString(), 
                comentario, 
                archivoUrl);

        Map<String, Object> res = new HashMap<>();
        res.put("message", "Entrega registrada");
        res.put("archivo_url", archivoUrl);
        return res;
    }

    @Transactional
    public void calificarEntrega(UUID entregaId, BigDecimal calificacion, String comentario, UUID userId) {
        String sql = """
            UPDATE ades_tareas_entregas
               SET calificacion_obtenida = ?,
                   comentario_profesor = ?,
                   calificado_por = ?::uuid,
                   fecha_calificacion_docente = now(),
                   estatus_entrega = 'CALIFICADA',
                   fecha_modificacion = now(),
                   row_version = row_version + 1
             WHERE id = ?::uuid
        """;
        int rows = jdbcTemplate.update(sql, calificacion, comentario, userId.toString(), entregaId.toString());
        if (rows == 0) {
            throw new NoSuchElementException("Entrega no encontrada");
        }
    }

    @Transactional
    public void registrarExcusa(UUID entregaId, String motivo) {
        String sql = """
            UPDATE ades_tareas_entregas
               SET estatus_entrega = 'EXCUSA',
                   comentario_profesor = ?,
                   fecha_modificacion = now()
             WHERE id = ?::uuid
        """;
        jdbcTemplate.update(sql, motivo, entregaId.toString());
    }

    @Transactional
    public Map<String, Object> checkPlagio(UUID entregaId) {
        String fetchSql = "SELECT archivo_url FROM ades_tareas_entregas WHERE id = ?::uuid AND is_active = TRUE";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(fetchSql, entregaId.toString());
        if (rows.isEmpty()) {
            throw new NoSuchElementException("Entrega no encontrada");
        }
        Map<String, Object> row = rows.get(0);
        String archivoUrl = (String) row.get("archivo_url");
        if (archivoUrl == null || archivoUrl.isBlank()) {
            throw new IllegalArgumentException("No hay archivo entregado para escanear");
        }

        double pct = 5.0 + Math.random() * 35.0; // 5% - 40% similarity
        BigDecimal plagioPct = BigDecimal.valueOf(Math.round(pct * 100.0) / 100.0);
        String reportUrl = "https://turnitin.mock/reports/" + UUID.randomUUID();

        String updateSql = """
            UPDATE ades_tareas_entregas
               SET plagio_porcentaje = ?,
                   plagio_reporte_url = ?,
                   fecha_modificacion = now(),
                   row_version = row_version + 1
             WHERE id = ?::uuid
        """;
        jdbcTemplate.update(updateSql, plagioPct, reportUrl, entregaId.toString());

        Map<String, Object> res = new HashMap<>();
        res.put("plagio_porcentaje", plagioPct);
        res.put("plagio_reporte_url", reportUrl);
        return res;
    }

    @Transactional
    public Map<String, Object> subirFeedbackMultimedia(UUID entregaId, MultipartFile audioFile, MultipartFile videoFile) {
        String fetchSql = "SELECT tarea_id, estudiante_id FROM ades_tareas_entregas WHERE id = ?::uuid AND is_active = TRUE";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(fetchSql, entregaId.toString());
        if (rows.isEmpty()) {
            throw new NoSuchElementException("Entrega no encontrada");
        }
        Map<String, Object> row = rows.get(0);
        UUID tareaId = UUID.fromString(row.get("tarea_id").toString());
        UUID estudianteId = UUID.fromString(row.get("estudiante_id").toString());

        String audioUrl = null;
        if (audioFile != null && !audioFile.isEmpty()) {
            audioUrl = minioService.uploadFile(tareaId, estudianteId, audioFile);
        }

        String videoUrl = null;
        if (videoFile != null && !videoFile.isEmpty()) {
            videoUrl = minioService.uploadFile(tareaId, estudianteId, videoFile);
        }

        StringBuilder sql = new StringBuilder("UPDATE ades_tareas_entregas SET ");
        List<Object> params = new ArrayList<>();
        if (audioUrl != null) {
            sql.append("feedback_audio_url = ?, ");
            params.add(audioUrl);
        }
        if (videoUrl != null) {
            sql.append("feedback_video_url = ?, ");
            params.add(videoUrl);
        }
        sql.delete(sql.length() - 2, sql.length()); // remove trailing comma and space
        sql.append(" , fecha_modificacion = now(), row_version = row_version + 1 WHERE id = ?::uuid");
        params.add(entregaId.toString());

        jdbcTemplate.update(sql.toString(), params.toArray());

        Map<String, Object> res = new HashMap<>();
        res.put("feedback_audio_url", audioUrl);
        res.put("feedback_video_url", videoUrl);
        return res;
    }

    public byte[] descargarArchivo(String minioUrl) {
        return minioService.downloadFile(minioUrl);
    }
}
