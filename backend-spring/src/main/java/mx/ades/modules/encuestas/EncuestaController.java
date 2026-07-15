package mx.ades.modules.encuestas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.encuestas.domain.port.in.ResponderEncuestaUseCase;
import mx.ades.modules.encuestas.query.EncuestaQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST para la gestión de encuestas de satisfacción y diagnóstico.
 * Expone endpoints bajo /api/v1/encuestas para crear, listar, activar/desactivar,
 * agregar preguntas (tipos ESCALA_5, texto libre, opciones múltiples) y recopilar
 * respuestas por sesión. El endpoint /responder registra las respuestas de un usuario
 * autenticado en una sesión identificada por sesionId. Los resultados agregados se
 * obtienen en /{id}/resultados. Soporta encuestas anónimas con audiencia configurable
 * (ALUMNO, DOCENTE, PADRE). Segmentación por plantel, nivel y grupo.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/encuestas")
@RequiredArgsConstructor
public class EncuestaController {

    private final EncuestaRepository repository;
    private final EncuestaPreguntaRepository preguntaRepository;
    private final EncuestaRespuestaRepository respuestaRepository;
    private final AdesUserService userService;
    private final ResponderEncuestaUseCase responderEncuesta;
    private final EncuestaQueryService queryService;

    @Data
    public static class EncuestaCreateRequest {
        @NotBlank(message = "titulo es obligatorio")
        @Size(max = 255, message = "titulo máximo 255 caracteres")
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
        @NotBlank(message = "texto es obligatorio")
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

    // ── READS (delegadas a QueryService) ─────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(value = "tipo", required = false) String tipo,
            @RequestParam(value = "activa", required = false) Boolean activa,
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return ResponseEntity.ok(queryService.listar(tipo, activa, plantelId, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detalle(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(queryService.detalle(id));
    }

    @GetMapping("/{id}/resultados")
    public ResponseEntity<Map<String, Object>> resultados(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        // BOLA/exposición de datos fix: al igual que /respuestas-raw, este endpoint expone
        // hasta 20 respuestas de texto libre sin agregar por pregunta (mismo nivel de
        // sensibilidad, incluye detección de acoso/bullying) pero no exigía ni resolver el
        // JWT — cualquier usuario autenticado podía leer comentarios crudos de cualquier
        // encuesta por id. Se alinea con requireStaff() ya aplicado en respuestasRaw().
        requireStaff(userService.resolveUser(jwt));
        return ResponseEntity.ok(queryService.resultados(id));
    }

    @GetMapping("/{id}/respuestas-raw")
    public ResponseEntity<List<Map<String, Object>>> respuestasRaw(
            @PathVariable("id") UUID id,
            @RequestParam(value = "limit", defaultValue = "200") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        // BOLA/exposición de datos fix: las respuestas individuales sin agregar (texto libre
        // incluido) son material de revisión interna del personal — antes no exigían ni
        // siquiera JWT resuelto, exponiendo respuestas crudas de encuestas (algunas no
        // anónimas) a cualquier usuario autenticado, incl. padres/alumnos nivelAcceso=5.
        requireStaff(userService.resolveUser(jwt));
        return ResponseEntity.ok(queryService.respuestasRaw(id, limit));
    }

    // ── WRITES ────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<Encuesta> crear(
            @RequestBody @Valid EncuestaCreateRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // BFLA fix: crear encuestas es una operación de personal escolar como el resto de
        // endpoints de administración de este controller (toggle-activa, preguntas, eliminar);
        // faltaba aquí el mismo requireStaff() que ya protege a sus pares.
        requireStaff(user);
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

    @PatchMapping("/{id}/toggle-activa")
    public ResponseEntity<Encuesta> toggleActiva(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
        Encuesta enc = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Encuesta no encontrada"));
        enc.setActiva(!enc.getActiva());
        return ResponseEntity.ok(repository.save(enc));
    }

    @PostMapping("/{id}/preguntas")
    public ResponseEntity<EncuestaPregunta> agregarPregunta(
            @PathVariable("id") UUID id,
            @RequestBody @Valid PreguntaCreateRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
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
            @RequestBody @Valid PreguntaCreateRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
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
            @PathVariable("pregunta_id") UUID preguntaId,
            @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
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
        AdesUser user = userService.resolveUser(jwt);

        String sesionId = body.getSesionId() != null ? body.getSesionId() : UUID.randomUUID().toString();

        List<ResponderEncuestaUseCase.RespuestaItem> items = body.getRespuestas().stream()
                .filter(r -> r.getPreguntaId() != null)
                .map(r -> new ResponderEncuestaUseCase.RespuestaItem(
                        r.getPreguntaId(), r.getTextoRespuesta(),
                        r.getValorNumerico(), r.getOpcionSeleccionada()))
                .toList();

        if (items.isEmpty()) return ResponseEntity.ok(Map.of("ok", true, "sesion_id", sesionId, "guardadas", 0));

        ResponderEncuestaUseCase.Result result = responderEncuesta.ejecutar(
                new ResponderEncuestaUseCase.Command(id, sesionId, items, user.getId()));

        return ResponseEntity.ok(Map.of("ok", true, "sesion_id", result.sesionId(), "guardadas", result.guardadas()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable("id") UUID id, @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
        Encuesta e = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Encuesta no encontrada"));
        e.setIsActive(false);
        repository.save(e);
    }

    /**
     * Administrar la estructura de una encuesta (activar/desactivar, preguntas,
     * eliminación) es operación de personal escolar (nivelAcceso &le;4). Padres/
     * alumnos (nivelAcceso &gt;=5) solo pueden responder — previene BFLA (OWASP API5).
     */
    private void requireStaff(AdesUser user) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso == null || nivelAcceso > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
    }
}
