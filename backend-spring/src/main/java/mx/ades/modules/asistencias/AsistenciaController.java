package mx.ades.modules.asistencias;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.asistencias.domain.port.in.ConsultarAsistenciasPorClaseUseCase;
import mx.ades.modules.asistencias.domain.port.in.RegistrarAsistenciaMasivaUseCase;
import mx.ades.modules.asistencias.infrastructure.inbound.rest.dto.AsistenciaResponseDto;
import mx.ades.modules.asistencias.infrastructure.inbound.rest.dto.RegistrarAsistenciaItemDto;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/asistencias")
@RequiredArgsConstructor
public class AsistenciaController {

    private final RegistrarAsistenciaMasivaUseCase registrarAsistenciaMasiva;
    private final ConsultarAsistenciasPorClaseUseCase consultarAsistenciasPorClase;
    private final AdesUserService userService;

    @PostMapping("/registrar-lote")
    public ResponseEntity<Void> registrarLote(
            @RequestBody List<RegistrarAsistenciaItemDto> items,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = requireStaff(jwt);
        registrarAsistenciaMasiva.ejecutar(
                items.stream().map(RegistrarAsistenciaItemDto::toCommand).toList(),
                user.getUsername());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/clase/{claseId}")
    public ResponseEntity<List<AsistenciaResponseDto>> listarPorClase(
            @PathVariable("claseId") UUID claseId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(
                consultarAsistenciasPorClase.ejecutar(claseId).stream()
                        .map(AsistenciaResponseDto::from)
                        .toList());
    }

    private static final Set<String> ESTATUS_VALIDOS =
        Set.of("PRESENTE", "AUSENTE", "TARDANZA", "JUSTIFICADO");

    /** POST /api/v1/asistencias/clase/{claseId} — frontend sends { asistencias: [{estudiante_id, estatus_asistencia}] } */
    @PostMapping("/clase/{claseId}")
    public ResponseEntity<Void> registrarPorClase(
            @PathVariable("claseId") UUID claseId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = requireStaff(jwt);
        String usuario = user.getUsername();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lista = (List<Map<String, Object>>) body.get("asistencias");
        if (lista == null || lista.isEmpty()) return ResponseEntity.ok().build();

        List<RegistrarAsistenciaItemDto> items = new ArrayList<>();
        for (int i = 0; i < lista.size(); i++) {
            Map<String, Object> a = lista.get(i);
            Object estudianteIdRaw = a.get("estudiante_id");
            if (estudianteIdRaw == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El campo 'estudiante_id' es obligatorio en el registro #" + (i + 1));
            }
            UUID estudianteId;
            try {
                estudianteId = UUID.fromString(estudianteIdRaw.toString());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "UUID inválido en 'estudiante_id' del registro #" + (i + 1));
            }
            String estatusRaw = a.get("estatus_asistencia") != null
                ? a.get("estatus_asistencia").toString().toUpperCase()
                : "AUSENTE";
            if (!ESTATUS_VALIDOS.contains(estatusRaw)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Estatus inválido '" + estatusRaw + "'. Permitidos: " + ESTATUS_VALIDOS);
            }
            items.add(new RegistrarAsistenciaItemDto(claseId, estudianteId, estatusRaw, null));
        }
        registrarAsistenciaMasiva.ejecutar(
            items.stream().map(RegistrarAsistenciaItemDto::toCommand).toList(),
            usuario);
        return ResponseEntity.ok().build();
    }

    /**
     * Registrar asistencia es operación de personal escolar (nivelAcceso &le;4).
     * Antes de este fix, cualquier JWT válido (incluidos alumnos/padres, nivelAcceso
     * &ge;5) podía invocar estos endpoints — la firma del token se validaba pero
     * nunca se resolvía el usuario ADES ni se comprobaba su rol (BFLA/OWASP API5).
     */
    private AdesUser requireStaff(Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
        return user;
    }
}
