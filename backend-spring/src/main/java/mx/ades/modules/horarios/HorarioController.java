package mx.ades.modules.horarios;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.horarios.application.service.HorarioApplicationService;
import mx.ades.modules.horarios.domain.port.in.ActualizarHorarioUseCase;
import mx.ades.modules.horarios.domain.port.in.CrearHorarioUseCase;
import mx.ades.modules.horarios.query.HorarioQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Adaptador REST para la gestión de horarios escolares con integración aSc TimeTables.
 * Expone endpoints bajo /api/v1/horarios para consultar horarios por grupo o profesor,
 * crear, actualizar y eliminar bloques horarios individuales, y realizar el round-trip
 * completo de export/import XML con aSc TimeTables (XXE-hardened, límite 10 MB).
 * La exportación acota el plantel para no-admins (anti cross-plantel); la importación
 * requiere nivel de acceso &le;3 (Coordinador o superior). Requiere JWT en todos los endpoints.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/horarios")
@RequiredArgsConstructor
public class HorarioController {

    private final AdesUserService userService;
    private final HorarioQueryService queryService;
    private final CrearHorarioUseCase crearHorarioUseCase;
    private final ActualizarHorarioUseCase actualizarHorarioUseCase;
    private final HorarioApplicationService horarioService;
    private final HorarioAscService ascService;

    private static final long MAX_XML_BYTES = 10 * 1024 * 1024; // 10 MB

    @GetMapping("/grupo/{grupo_id}")
    public ResponseEntity<List<Map<String, Object>>> porGrupo(
            @PathVariable("grupo_id") UUID grupoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.porGrupo(grupoId));
    }

    @GetMapping("/profesor/{profesor_id}")
    public ResponseEntity<List<Map<String, Object>>> porProfesor(
            @PathVariable("profesor_id") UUID profesorId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.porProfesor(profesorId));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(name = "grupo_id", required = false) UUID grupoId,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.listar(grupoId, plantelId));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(
            @RequestBody HorarioPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        var user = userService.resolveUser(jwt);
        try {
            UUID id = crearHorarioUseCase.crear(new CrearHorarioUseCase.Command(
                body.getGrupoId(), body.getMateriaId(), body.getProfesorId(), body.getAulaId(),
                body.getCicloEscolarId(), body.getDiaSemana(),
                body.getHoraInicio(), body.getHoraFin(), body.getOrigen(), user.getUsername()));
            return ResponseEntity.status(HttpStatus.CREATED).body(queryService.obtener(id));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizar(
            @PathVariable UUID id,
            @RequestBody HorarioPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        var user = userService.resolveUser(jwt);
        try {
            actualizarHorarioUseCase.actualizar(new ActualizarHorarioUseCase.Command(
                id, body.getMateriaId(), body.getProfesorId(), body.getAulaId(),
                body.getDiaSemana(), body.getHoraInicio(), body.getHoraFin(),
                body.getOrigen(), body.getMotivoCambio(), user.getUsername()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        return ResponseEntity.ok(queryService.obtener(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        horarioService.eliminar(id);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // aSc TimeTables — Export / Import
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/exportar-asc/{ciclo_id}")
    public ResponseEntity<byte[]> exportarAsc(
            @PathVariable("ciclo_id") UUID cicloId,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // No-admins quedan acotados a su propio plantel (evita fuga cross-plantel)
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 1 && user.getPlantelId() != null) {
            plantelId = user.getPlantelId();
        }
        String xml = ascService.exportarXml(cicloId, plantelId);
        byte[] content = xml.getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "xml", StandardCharsets.UTF_8));
        headers.setContentDispositionFormData("attachment", "horarios_asc.xml");
        return ResponseEntity.ok().headers(headers).body(content);
    }

    @PostMapping("/importar-asc/{ciclo_id}")
    public ResponseEntity<HorarioAscService.ImportResult> importarAsc(
            @PathVariable("ciclo_id") UUID cicloId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @RequestParam(name = "reemplazar", defaultValue = "false") boolean reemplazar,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // Solo coordinador (nivel 3) o superior puede reconstruir horarios
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permisos insuficientes para importar horarios");
        }
        // No-admins acotados a su plantel
        if (user.getNivelAcceso() > 1 && user.getPlantelId() != null) {
            plantelId = user.getPlantelId();
        }
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo no contiene datos");
        }
        if (file.getSize() > MAX_XML_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo supera el límite de 10 MB");
        }
        try {
            byte[] bytes = file.getBytes();
            HorarioAscService.ImportResult result =
                    ascService.importarXml(bytes, cicloId, plantelId, reemplazar, user.getUsername());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al importar: " + e.getMessage());
        }
    }

    @Data
    public static class HorarioPayload {
        private UUID grupoId;
        private UUID materiaId;
        private UUID profesorId;
        private UUID aulaId;
        private UUID cicloEscolarId;
        private Integer diaSemana;
        private String horaInicio;
        private String horaFin;
        private String origen;
        private String motivoCambio;
    }
}
