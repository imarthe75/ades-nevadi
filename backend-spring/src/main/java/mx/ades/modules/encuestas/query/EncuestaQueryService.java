package mx.ades.modules.encuestas.query;

import mx.ades.modules.encuestas.EncuestaPregunta;
import mx.ades.modules.encuestas.EncuestaPreguntaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de lectura CQRS para el módulo encuestas.
 * <p>Expone listado de encuestas, detalle con preguntas, resultados estadísticos por tipo
 * (ESCALA_5, OPCION_MULTIPLE, BOOLEANO, TEXTO_LIBRE) y respuestas en bruto.</p>
 *
 * @author ADES
 * @since 2026
 */
@Service
public class EncuestaQueryService {

    private final JdbcTemplate jdbc;
    private final EncuestaPreguntaRepository preguntaRepository;

    public EncuestaQueryService(JdbcTemplate jdbc, EncuestaPreguntaRepository preguntaRepository) {
        this.jdbc = jdbc;
        this.preguntaRepository = preguntaRepository;
    }

    public List<Map<String, Object>> listar(String tipo, Boolean activa, UUID plantelId, int limit) {
        StringBuilder query = new StringBuilder("""
                SELECT e.id, e.titulo, e.descripcion, e.tipo, e.audiencia,
                       e.plantel_id, pl.nombre_plantel, e.fecha_inicio, e.fecha_fin,
                       e.anonima, e.activa, e.fecha_creacion,
                       COUNT(DISTINCT ep.id) FILTER (WHERE ep.is_active = TRUE) AS total_preguntas,
                       COUNT(DISTINCT er.sesion_id) AS total_respuestas
                FROM ades_encuestas e
                LEFT JOIN ades_planteles pl ON pl.id = e.plantel_id
                LEFT JOIN ades_encuesta_preguntas ep ON ep.encuesta_id = e.id
                LEFT JOIN ades_encuesta_respuestas er ON er.encuesta_id = e.id
                WHERE e.is_active = TRUE
                """);

        List<Object> params = new ArrayList<>();
        if (tipo != null && !tipo.isBlank()) {
            query.append("AND e.tipo = ? ");
            params.add(tipo);
        }
        if (activa != null) {
            query.append("AND e.activa = ? ");
            params.add(activa);
        }
        if (plantelId != null) {
            query.append("AND (e.plantel_id IS NULL OR e.plantel_id = ?) ");
            params.add(plantelId);
        }
        query.append("GROUP BY e.id, pl.nombre_plantel ORDER BY e.fecha_creacion DESC LIMIT ?");
        params.add(limit);

        return jdbc.queryForList(query.toString(), params.toArray());
    }

    public Map<String, Object> detalle(UUID id) {
        Map<String, Object> enc = jdbc.queryForMap("""
                SELECT id, titulo, descripcion, tipo, audiencia, plantel_id,
                       fecha_inicio, fecha_fin, anonima, activa
                FROM ades_encuestas WHERE id = ? AND is_active = TRUE
                """, id);

        Long totalRespuestas = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT sesion_id) FROM ades_encuesta_respuestas WHERE encuesta_id = ?",
                Long.class, id);

        List<EncuestaPregunta> preguntas = preguntaRepository.findByEncuestaIdAndIsActiveTrueOrderByOrden(id);

        Map<String, Object> out = new HashMap<>(enc);
        out.put("total_respuestas", totalRespuestas);
        out.put("preguntas", preguntas);
        return out;
    }

    public Map<String, Object> resultados(UUID id) {
        Long totalSesiones = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT sesion_id) FROM ades_encuesta_respuestas WHERE encuesta_id = ?",
                Long.class, id);

        List<EncuestaPregunta> preguntas = preguntaRepository.findByEncuestaIdAndIsActiveTrueOrderByOrden(id);
        List<Map<String, Object>> resultados = new ArrayList<>();

        for (EncuestaPregunta p : preguntas) {
            Map<String, Object> stats = new HashMap<>();
            stats.put("pregunta_id", p.getId());
            stats.put("texto", p.getTexto());
            stats.put("tipo_pregunta", p.getTipoPregunta());
            stats.put("orden", p.getOrden());

            switch (p.getTipoPregunta()) {
                case "ESCALA_5" -> {
                    Map<String, Object> s = jdbc.queryForMap("""
                            SELECT COUNT(*) AS total,
                                   ROUND(AVG(valor_numerico)::numeric, 2) AS promedio,
                                   MODE() WITHIN GROUP (ORDER BY valor_numerico) AS moda,
                                   COUNT(*) FILTER (WHERE ROUND(valor_numerico) = 1) AS n1,
                                   COUNT(*) FILTER (WHERE ROUND(valor_numerico) = 2) AS n2,
                                   COUNT(*) FILTER (WHERE ROUND(valor_numerico) = 3) AS n3,
                                   COUNT(*) FILTER (WHERE ROUND(valor_numerico) = 4) AS n4,
                                   COUNT(*) FILTER (WHERE ROUND(valor_numerico) = 5) AS n5
                            FROM ades_encuesta_respuestas WHERE pregunta_id = ? AND valor_numerico IS NOT NULL
                            """, p.getId());
                    stats.putAll(s);
                }
                case "OPCION_MULTIPLE" -> {
                    List<Map<String, Object>> dist = jdbc.queryForList("""
                            SELECT opcion_seleccionada AS opcion, COUNT(*) AS cantidad,
                                   ROUND((COUNT(*)::numeric / NULLIF(SUM(COUNT(*)) OVER(), 0) * 100)::numeric, 1) AS porcentaje
                            FROM ades_encuesta_respuestas
                            WHERE pregunta_id = ? AND opcion_seleccionada IS NOT NULL
                            GROUP BY opcion_seleccionada ORDER BY cantidad DESC
                            """, p.getId());
                    stats.put("distribucion", dist);
                    stats.put("total", dist.stream().mapToLong(r -> ((Number) r.get("cantidad")).longValue()).sum());
                }
                case "BOOLEANO" -> {
                    Map<String, Object> b = jdbc.queryForMap("""
                            SELECT COUNT(*) FILTER (WHERE LOWER(opcion_seleccionada) IN ('sí','si','true','1')) AS si,
                                   COUNT(*) FILTER (WHERE LOWER(opcion_seleccionada) IN ('no','false','0')) AS no,
                                   COUNT(*) AS total
                            FROM ades_encuesta_respuestas WHERE pregunta_id = ? AND opcion_seleccionada IS NOT NULL
                            """, p.getId());
                    long total = ((Number) b.get("total")).longValue();
                    long si = ((Number) b.get("si")).longValue();
                    long no = ((Number) b.get("no")).longValue();
                    stats.put("si", si);
                    stats.put("no", no);
                    stats.put("total", total);
                    stats.put("pct_si", total > 0 ? Math.round(si * 1000.0 / total) / 10.0 : 0.0);
                    stats.put("pct_no", total > 0 ? Math.round(no * 1000.0 / total) / 10.0 : 0.0);
                }
                case "TEXTO_LIBRE" -> {
                    List<String> resp = jdbc.queryForList("""
                            SELECT texto_respuesta FROM ades_encuesta_respuestas
                            WHERE pregunta_id = ? AND texto_respuesta IS NOT NULL
                              AND LENGTH(TRIM(texto_respuesta)) > 0
                            ORDER BY fecha_creacion DESC LIMIT 20
                            """, String.class, p.getId());
                    stats.put("respuestas", resp);
                    stats.put("total", resp.size());
                }
            }
            resultados.add(stats);
        }

        return Map.of("encuesta_id", id, "total_sesiones", totalSesiones, "preguntas", resultados);
    }

    public List<Map<String, Object>> respuestasRaw(UUID id, int limit) {
        return jdbc.queryForList("""
                SELECT er.sesion_id, ep.texto AS pregunta, ep.tipo_pregunta,
                       er.valor_numerico, er.opcion_seleccionada, er.texto_respuesta, er.fecha_creacion
                FROM ades_encuesta_respuestas er
                JOIN ades_encuesta_preguntas ep ON ep.id = er.pregunta_id
                WHERE er.encuesta_id = ?
                ORDER BY er.sesion_id, ep.orden LIMIT ?
                """, id, limit);
    }
}
