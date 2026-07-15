package mx.ades.modules.foros;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.foros.domain.port.in.CrearForoUseCase;
import mx.ades.modules.foros.domain.port.in.ModerarMensajeUseCase;
import mx.ades.modules.foros.domain.port.in.PublicarAnuncioUseCase;
import mx.ades.modules.foros.domain.port.in.PublicarMensajeUseCase;
import mx.ades.modules.foros.query.ForoQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST para el módulo de foros de comunicación y anuncios.
 * Expone endpoints bajo /api/v1/foros para crear foros (GRUPO, PLANTEL, MATERIA),
 * publicar mensajes, responder en hilo, moderar mensajes y publicar anuncios
 * institucionales. La creación de foros y anuncios requiere Coordinador o superior
 * (nivelAcceso {@literal <=} 3). La moderación aplica validación de nivel. El listado
 * aplica filtro por nivelAcceso del usuario. Los anuncios soportan segmentación
 * por plantel, nivel educativo y vigencia temporal. Toda operación de escritura
 * requiere JWT válido via {@code resolveUser}.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/foros")
@RequiredArgsConstructor
public class ForoController {

    private final CrearForoUseCase crearForoUseCase;
    private final PublicarMensajeUseCase publicarMensajeUseCase;
    private final ModerarMensajeUseCase moderarMensajeUseCase;
    private final PublicarAnuncioUseCase publicarAnuncioUseCase;
    private final ForoQueryService queryService;
    private final AdesUserService userService;
    private final ForoRepository foroRepository;

    /**
     * Tipos de foro visibles/accesibles para nivelAcceso=5 (alumnos/padres) — mismo criterio
     * que {@link ForoQueryService#listar}. Notablemente excluye {@code DOCENTES} (foro
     * exclusivo de personal docente/directivo).
     */
    private static final Set<String> TIPOS_FORO_NIVEL5 = Set.of("GENERAL", "PLANTEL", "GRUPO", "TUTORES", "MATERIA");

    /**
     * BOLA fix: el filtro de tipo de foro en {@code listar()} solo controlaba qué aparecía
     * en el listado — los endpoints de mensajes/respuestas no verificaban nada y permitían
     * a cualquier usuario autenticado (incluido nivelAcceso=5) leer o publicar en un foro
     * {@code DOCENTES} con solo conocer/adivinar su UUID. Se valida aquí el mismo criterio
     * de tipo antes de cualquier lectura o escritura de mensajes.
     */
    private Foro verificarAccesoForo(AdesUser user, UUID foroId) {
        Foro foro = foroRepository.findById(foroId)
                .filter(Foro::getIsActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Foro no encontrado"));
        if (user.getNivelAcceso() != null && user.getNivelAcceso() == 5
                && !TIPOS_FORO_NIVEL5.contains(foro.getTipo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene acceso a este foro");
        }
        return foro;
    }

    @Data
    public static class ForoCreateRequest {
        @NotBlank(message = "nombre es obligatorio")
        private String nombre;
        private String descripcion;
        private String tipo = "GRUPO";
        private UUID grupoId;
        private UUID plantelId;
        private UUID materiaId;
        private Boolean esModerado = false;
    }

    @Data
    public static class MensajeForoRequest {
        @NotBlank(message = "asunto es obligatorio")
        private String asunto;
        @NotBlank(message = "contenido es obligatorio")
        private String contenido;
        private String adjuntoUrl;
    }

    @Data
    public static class RespuestaForoRequest {
        @NotBlank(message = "contenido es obligatorio")
        private String contenido;
        private String adjuntoUrl;
    }

    @Data
    public static class AnuncioRequest {
        @NotBlank(message = "titulo es obligatorio")
        private String titulo;
        @NotBlank(message = "contenido es obligatorio")
        private String contenido;
        private UUID plantelId;
        private String nivelEducativo;
        @NotBlank(message = "fechaInicio es obligatoria")
        private String fechaInicio;
        private String fechaFin;
        private Boolean esUrgente = false;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(value = "grupo_id", required = false) UUID grupoId,
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @RequestParam(value = "tipo", required = false) String tipo,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.listar(grupoId, plantelId, materiaId, tipo, user.getNivelAcceso()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(
            @RequestBody @Valid ForoCreateRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere Coordinador o superior");
        }
        CrearForoUseCase.Command cmd = new CrearForoUseCase.Command(
                body.getNombre(), body.getDescripcion(), body.getTipo(),
                body.getGrupoId(), body.getPlantelId(), body.getMateriaId(),
                body.getEsModerado(), user.getId(), user.getNivelAcceso());
        return ResponseEntity.status(HttpStatus.CREATED).body(crearForoUseCase.crear(cmd));
    }

    @GetMapping("/{id}/mensajes")
    public ResponseEntity<List<Map<String, Object>>> listarMensajes(
            @PathVariable("id") UUID id,
            @RequestParam(value = "skip", defaultValue = "0") int skip,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        verificarAccesoForo(user, id);
        return ResponseEntity.ok(queryService.listarMensajes(id, skip, limit));
    }

    @PostMapping("/{id}/mensajes")
    public ResponseEntity<Map<String, Object>> publicarMensaje(
            @PathVariable("id") UUID id,
            @RequestBody @Valid MensajeForoRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        verificarAccesoForo(user, id);
        PublicarMensajeUseCase.Command cmd = new PublicarMensajeUseCase.Command(
                id, body.getAsunto(), body.getContenido(), body.getAdjuntoUrl(), user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(publicarMensajeUseCase.publicar(cmd));
    }

    @PostMapping("/{foroId}/mensajes/{mensajeId}/responder")
    public ResponseEntity<Map<String, Object>> responderMensaje(
            @PathVariable("foroId") UUID foroId,
            @PathVariable("mensajeId") UUID mensajeId,
            @RequestBody @Valid RespuestaForoRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        verificarAccesoForo(user, foroId);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                publicarMensajeUseCase.responder(foroId, mensajeId, body.getContenido(), body.getAdjuntoUrl(), user.getId()));
    }

    @GetMapping("/{foroId}/mensajes/{mensajeId}/respuestas")
    public ResponseEntity<List<Map<String, Object>>> listarRespuestas(
            @PathVariable("foroId") UUID foroId,
            @PathVariable("mensajeId") UUID mensajeId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        verificarAccesoForo(user, foroId);
        return ResponseEntity.ok(queryService.listarRespuestas(mensajeId));
    }

    @GetMapping("/anuncios")
    public ResponseEntity<List<Map<String, Object>>> listarAnuncios(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "nivel_educativo", required = false) String nivelEducativo,
            @RequestParam(value = "solo_vigentes", defaultValue = "true") boolean soloVigentes) {
        return ResponseEntity.ok(queryService.listarAnuncios(plantelId, nivelEducativo, soloVigentes));
    }

    @PostMapping("/anuncios")
    public ResponseEntity<Map<String, Object>> publicarAnuncio(
            @RequestBody @Valid AnuncioRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere Coordinador o superior");
        }
        PublicarAnuncioUseCase.Command cmd = new PublicarAnuncioUseCase.Command(
                body.getTitulo(), body.getContenido(), body.getPlantelId(),
                body.getNivelEducativo(), body.getFechaInicio(), body.getFechaFin(),
                body.getEsUrgente(), user.getNivelAcceso());
        return ResponseEntity.status(HttpStatus.CREATED).body(publicarAnuncioUseCase.publicar(cmd));
    }

    @PatchMapping("/mensajes/{mensajeId}/moderar")
    public ResponseEntity<Map<String, Object>> moderar(
            @PathVariable("mensajeId") UUID mensajeId,
            @RequestParam("estado") String estado,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        return ResponseEntity.ok(moderarMensajeUseCase.moderar(mensajeId, estado, user.getNivelAcceso()));
    }
}
