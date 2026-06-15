package mx.ades.modules.encuestas;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/encuestas")
@RequiredArgsConstructor
@Slf4j
public class EncuestaController {

    private final EncuestaRepository repository;
    private final EncuestaPreguntaRepository preguntaRepository;
    private final EncuestaRespuestaRepository respuestaRepository;
    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    @Value("${openai.base-url:https://integrate.api.nvidia.com/v1}")
    private String openaiBaseUrl;

    @Value("${openai.api-key:}")
    private String openaiApiKey;

    @Value("${openai.model:meta/llama-3-71b-instruct}")
    private String openaiModel;

    @Data
    public static class EncuestaCreateRequest {
        private String titulo;
        private String descripcion;
        private String tipo = "SATISFACCION";
        private String audiencia = "ALUMNO";
        private UUID plantelId;
        private UUID nivelEducativoId;
        private UUID grupoId;
        private LocalDate fechaInicio;
        private LocalDate fechaFin;
        private Boolean anonima = false;
    }

    @Data
    public static class PreguntaCreateRequest {
        private String texto;
        private String tipoPregunta = "ESCALA_5";
        private List<String> opciones;
        private Integer orden = 1;
        private Boolean obligatoria = true;
    }

    @Data
    public static class RespuestaItem {
        private UUID preguntaId;
        private String textoRespuesta;
        private Double valorNumerico;
        private String opcionSeleccionada;
    }

    @Data
    public static class SesionRespuestas {
        private String sesionId;
        private List<RespuestaItem> respuestas;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(value = "tipo", required = false) String tipo,
            @RequestParam(value = "activa", required = false) Boolean activa,
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {

        StringBuilder query = new StringBuilder(
                "SELECT e.id, e.titulo, e.descripcion, e.tipo, e.audiencia, " +
                "e.plantel_id, pl.nombre_plantel, e.fecha_inicio, e.fecha_fin, " +
                "e.anonima, e.activa, e.fecha_creacion, " +
                "COUNT(DISTINCT ep.id) FILTER (WHERE ep.is_active = TRUE) AS total_preguntas, " +
                "COUNT(DISTINCT er.sesion_id) AS total_respuestas " +
                "FROM ades_encuestas e " +
                "LEFT JOIN ades_planteles pl ON pl.id = e.plantel_id " +
                "LEFT JOIN ades_encuesta_preguntas ep ON ep.encuesta_id = e.id " +
                "LEFT JOIN ades_encuesta_respuestas er ON er.encuesta_id = e.id " +
                "WHERE e.is_active = TRUE ");

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

        return ResponseEntity.ok(jdbc.queryForList(query.toString(), params.toArray()));
    }

    @PostMapping
    public ResponseEntity<Encuesta> crear(
            @RequestBody EncuestaCreateRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);

        Encuesta e = new Encuesta();
        e.setTitulo(body.getTitulo());
        e.setDescripcion(body.getDescripcion());
        e.setTipo(body.getTipo());
        e.setAudiencia(body.getAudiencia());
        e.setPlantelId(body.getPlantelId());
        e.setNivelEducativoId(body.getNivelEducativoId());
        e.setGrupoId(body.getGrupoId());
        e.setFechaInicio(body.getFechaInicio());
        e.setFechaFin(body.getFechaFin());
        e.setAnonima(body.getAnonima());
        e.setCreadoPorId(user.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(e));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detalle(@PathVariable("id") UUID id) {
        Encuesta enc = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Encuesta no encontrada"));

        String totalRespuestasSql = "SELECT COUNT(DISTINCT sesion_id) FROM ades_encuesta_respuestas WHERE encuesta_id = ?";
        Long totalRespuestas = jdbc.queryForObject(totalRespuestasSql, Long.class, id);

        List<EncuestaPregunta> preguntas = preguntaRepository.findByEncuestaIdAndIsActiveTrueOrderByOrden(id);

        Map<String, Object> out = new HashMap<>();
        out.put("id", enc.getId());
        out.put("titulo", enc.getTitulo());
        out.put("descripcion", enc.getDescripcion());
        out.put("tipo", enc.getTipo());
        out.put("audiencia", enc.getAudiencia());
        out.put("plantel_id", enc.getPlantelId());
        out.put("fecha_inicio", enc.getFechaInicio());
        out.put("fecha_fin", enc.getFechaFin());
        out.put("anonima", enc.getAnonima());
        out.put("activa", enc.getActiva());
        out.put("total_respuestas", totalRespuestas);
        out.put("preguntas", preguntas);

        return ResponseEntity.ok(out);
    }

    @PatchMapping("/{id}/toggle-activa")
    public ResponseEntity<Encuesta> toggleActiva(@PathVariable("id") UUID id) {
        Encuesta enc = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Encuesta no encontrada"));
        enc.setActiva(!enc.getActiva());
        return ResponseEntity.ok(repository.save(enc));
    }

    @PostMapping("/{id}/preguntas")
    public ResponseEntity<EncuestaPregunta> agregarPregunta(
            @PathVariable("id") UUID id,
            @RequestBody PreguntaCreateRequest body) {

        EncuestaPregunta p = new EncuestaPregunta();
        p.setEncuestaId(id);
        p.setTexto(body.getTexto());
        p.setTipoPregunta(body.getTipoPregunta());
        p.setOpciones(body.getOpciones());
        p.setOrden(body.getOrden());
        p.setObligatoria(body.getObligatoria());

        return ResponseEntity.status(HttpStatus.CREATED).body(preguntaRepository.save(p));
    }

    @PutMapping("/{id}/preguntas/{pregunta_id}")
    public ResponseEntity<EncuestaPregunta> actualizarPregunta(
            @PathVariable("id") UUID id,
            @PathVariable("pregunta_id") UUID preguntaId,
            @RequestBody PreguntaCreateRequest body) {

        EncuestaPregunta p = preguntaRepository.findById(preguntaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pregunta no encontrada"));

        p.setTexto(body.getTexto());
        p.setTipoPregunta(body.getTipoPregunta());
        p.setOpciones(body.getOpciones());
        p.setOrden(body.getOrden());
        p.setObligatoria(body.getObligatoria());

        return ResponseEntity.ok(preguntaRepository.save(p));
    }

    @DeleteMapping("/{id}/preguntas/{pregunta_id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarPregunta(
            @PathVariable("id") UUID id,
            @PathVariable("pregunta_id") UUID preguntaId) {
        EncuestaPregunta p = preguntaRepository.findById(preguntaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pregunta no encontrada"));
        p.setIsActive(false);
        preguntaRepository.save(p);
    }

    @PostMapping("/{id}/responder")
    public ResponseEntity<Map<String, Object>> responder(
            @PathVariable("id") UUID id,
            @RequestBody SesionRespuestas body,
            @AuthenticationPrincipal Jwt jwt) {

        Encuesta enc = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Encuesta no encontrada"));

        if (!Boolean.TRUE.equals(enc.getActiva())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La encuesta no está activa");
        }

        AdesUser user = userService.resolveUser(jwt);
        UUID uid = Boolean.TRUE.equals(enc.getAnonima()) ? null : user.getId();

        String sesionId = body.getSesionId() != null ? body.getSesionId() : UUID.randomUUID().toString();
        int saved = 0;

        for (RespuestaItem r : body.getRespuestas()) {
            // Save response
            EncuestaRespuesta er = new EncuestaRespuesta();
            er.setEncuestaId(id);
            er.setPreguntaId(r.getPreguntaId());
            er.setRespondidoPorId(uid);
            er.setSesionId(sesionId);
            er.setTextoRespuesta(r.getTextoRespuesta());
            er.setValorNumerico(r.getValorNumerico() != null ? java.math.BigDecimal.valueOf(r.getValorNumerico()) : null);
            er.setOpcionSeleccionada(r.getOpcionSeleccionada());

            try {
                // Ensure idempotency using JDBC check/insert or try-catch for uniqueness
                int count = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ades_encuesta_respuestas WHERE pregunta_id = ? AND sesion_id = ?",
                        Integer.class, r.getPreguntaId(), sesionId);

                if (count == 0) {
                    respuestaRepository.save(er);
                    saved++;
                }
            } catch (Exception ex) {
                // Skip unique constraint failures
            }

            // Semantic Analysis of harassment/bullying
            if (r.getTextoRespuesta() != null && !r.getTextoRespuesta().isBlank()) {
                String textLower = r.getTextoRespuesta().toLowerCase();
                List<String> keywords = List.of(
                        "acoso", "bullying", "cyberbullying", "golpe", "insult", "burl", "amenaz",
                        "maltrat", "pegan", "miedo", "lloro", "suicid", "cortar", "dañar", "morir", "agred"
                );
                boolean isFlagged = keywords.stream().anyMatch(textLower::contains);

                // Optional LLM Scan using OpenAI if API key available
                if (!isFlagged && openaiApiKey != null && !openaiApiKey.isBlank()) {
                    isFlagged = callOpenAiLlmCheck(r.getTextoRespuesta());
                }

                if (isFlagged) {
                    UUID studentId = null;
                    UUID studentPlantelId = null;

                    if (uid != null) {
                        try {
                            Map<String, Object> studentRow = jdbc.queryForMap(
                                    "SELECT id, plantel_id FROM ades_estudiantes " +
                                            "WHERE persona_id = (SELECT persona_id FROM ades_usuarios WHERE id = ?)", uid);
                            studentId = (UUID) studentRow.get("id");
                            studentPlantelId = (UUID) studentRow.get("plantel_id");
                        } catch (Exception ex) {
                            // Student not found for this user
                        }
                    }

                    if (studentPlantelId == null) {
                        studentPlantelId = enc.getPlantelId();
                    }

                    String descAlerta = String.format("Alerta semántica de Acoso/Bullying detectada en Encuesta '%s' (Sesión: %s): '%s'",
                            enc.getTitulo(), sesionId, r.getTextoRespuesta());

                    jdbc.update("INSERT INTO ades_alertas_cumplimiento " +
                            "(tipo_alerta, descripcion, alumno_id, plantel_id, severidad, requiere_accion, estado, " +
                            "usuario_creacion, usuario_modificacion) " +
                            "VALUES ('ACOSO_BULLYING', ?, ?, ?, 'CRITICA', TRUE, 'PENDIENTE', 'system_ia', 'system_ia')",
                            descAlerta, studentId, studentPlantelId);
                }
            }
        }

        return ResponseEntity.ok(Map.of("ok", true, "sesion_id", sesionId, "guardadas", saved));
    }

    private boolean callOpenAiLlmCheck(String text) {
        try {
            String url = openaiBaseUrl + "/chat/completions";
            RestClient client = RestClient.builder().build();

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", openaiModel);
            requestBody.put("max_tokens", 5);

            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", String.format(
                    "Analiza si el siguiente texto escolar contiene indicios o reportes de acoso (bullying), " +
                    "cyberbullying, violencia física/verbal o pensamientos de autolesión/suicidio: '%s'. " +
                    "Responde únicamente con la palabra SI o NO.", text));

            requestBody.put("messages", List.of(userMessage));

            String response = client.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (response != null) {
                String responseUpper = response.toUpperCase();
                return responseUpper.contains("\"SI\"") || responseUpper.contains(" SI ") || responseUpper.endsWith("SI");
            }
        } catch (Exception e) {
            log.warn("OpenAI LLM semantic check failed: {}", e.getMessage());
        }
        return false;
    }

    @GetMapping("/{id}/resultados")
    public ResponseEntity<Map<String, Object>> resultados(@PathVariable("id") UUID id) {
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
                case "ESCALA_5":
                    Map<String, Object> escalaStats = jdbc.queryForMap(
                            "SELECT COUNT(*) AS total, " +
                                    "ROUND(AVG(valor_numerico)::numeric, 2) AS promedio, " +
                                    "MODE() WITHIN GROUP (ORDER BY valor_numerico) AS moda, " +
                                    "COUNT(*) FILTER (WHERE ROUND(valor_numerico) = 1) AS n1, " +
                                    "COUNT(*) FILTER (WHERE ROUND(valor_numerico) = 2) AS n2, " +
                                    "COUNT(*) FILTER (WHERE ROUND(valor_numerico) = 3) AS n3, " +
                                    "COUNT(*) FILTER (WHERE ROUND(valor_numerico) = 4) AS n4, " +
                                    "COUNT(*) FILTER (WHERE ROUND(valor_numerico) = 5) AS n5 " +
                                    "FROM ades_encuesta_respuestas WHERE pregunta_id = ? AND valor_numerico IS NOT NULL",
                            p.getId());
                    stats.putAll(escalaStats);
                    break;

                case "OPCION_MULTIPLE":
                    List<Map<String, Object>> dist = jdbc.queryForList(
                            "SELECT opcion_seleccionada AS opcion, COUNT(*) AS cantidad, " +
                                    "ROUND((COUNT(*)::numeric / NULLIF(SUM(COUNT(*)) OVER(), 0) * 100)::numeric, 1) AS porcentaje " +
                                    "FROM ades_encuesta_respuestas WHERE pregunta_id = ? AND opcion_seleccionada IS NOT NULL " +
                                    "GROUP BY opcion_seleccionada ORDER BY cantidad DESC",
                            p.getId());
                    stats.put("distribucion", dist);
                    stats.put("total", dist.stream().mapToLong(row -> ((Number) row.get("cantidad")).longValue()).sum());
                    break;

                case "BOOLEANO":
                    Map<String, Object> boolStats = jdbc.queryForMap(
                            "SELECT " +
                                    "COUNT(*) FILTER (WHERE LOWER(opcion_seleccionada) IN ('sí','si','true','1')) AS si, " +
                                    "COUNT(*) FILTER (WHERE LOWER(opcion_seleccionada) IN ('no','false','0')) AS no, " +
                                    "COUNT(*) AS total " +
                                    "FROM ades_encuesta_respuestas WHERE pregunta_id = ? AND opcion_seleccionada IS NOT NULL",
                            p.getId());
                    long totalBool = ((Number) boolStats.get("total")).longValue();
                    long si = ((Number) boolStats.get("si")).longValue();
                    long no = ((Number) boolStats.get("no")).longValue();

                    stats.put("si", si);
                    stats.put("no", no);
                    stats.put("total", totalBool);
                    stats.put("pct_si", totalBool > 0 ? Math.round(((double) si / totalBool * 100.0) * 10.0) / 10.0 : 0.0);
                    stats.put("pct_no", totalBool > 0 ? Math.round(((double) no / totalBool * 100.0) * 10.0) / 10.0 : 0.0);
                    break;

                case "TEXTO_LIBRE":
                    List<String> respuestas = jdbc.queryForList(
                            "SELECT texto_respuesta FROM ades_encuesta_respuestas " +
                                    "WHERE pregunta_id = ? AND texto_respuesta IS NOT NULL " +
                                    "AND LENGTH(TRIM(texto_respuesta)) > 0 ORDER BY fecha_creacion DESC LIMIT 20",
                            String.class, p.getId());
                    stats.put("respuestas", respuestas);
                    stats.put("total", respuestas.size());
                    break;
            }
            resultados.add(stats);
        }

        return ResponseEntity.ok(Map.of(
                "encuesta_id", id,
                "total_sesiones", totalSesiones,
                "preguntas", resultados
        ));
    }

    @GetMapping("/{id}/respuestas-raw")
    public ResponseEntity<List<Map<String, Object>>> respuestasRaw(
            @PathVariable("id") UUID id,
            @RequestParam(value = "limit", defaultValue = "200") int limit) {

        String sql = "SELECT er.sesion_id, ep.texto AS pregunta, ep.tipo_pregunta, " +
                "er.valor_numerico, er.opcion_seleccionada, er.texto_respuesta, er.fecha_creacion " +
                "FROM ades_encuesta_respuestas er JOIN ades_encuesta_preguntas ep ON ep.id = er.pregunta_id " +
                "WHERE er.encuesta_id = ? ORDER BY er.sesion_id, ep.orden LIMIT ?";

        return ResponseEntity.ok(jdbc.queryForList(sql, id, limit));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable("id") UUID id) {
        Encuesta e = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Encuesta no encontrada"));
        e.setIsActive(false);
        repository.save(e);
    }
}
