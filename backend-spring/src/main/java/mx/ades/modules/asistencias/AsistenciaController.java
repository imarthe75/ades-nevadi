package mx.ades.modules.asistencias;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.asistencias.domain.port.in.ConsultarAsistenciasPorClaseUseCase;
import mx.ades.modules.asistencias.domain.port.in.RegistrarAsistenciaMasivaUseCase;
import mx.ades.modules.asistencias.infrastructure.inbound.rest.dto.AsistenciaResponseDto;
import mx.ades.modules.asistencias.infrastructure.inbound.rest.dto.RegistrarAsistenciaItemDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/asistencias")
@RequiredArgsConstructor
public class AsistenciaController {

    private final RegistrarAsistenciaMasivaUseCase registrarAsistenciaMasiva;
    private final ConsultarAsistenciasPorClaseUseCase consultarAsistenciasPorClase;

    @PostMapping("/registrar-lote")
    public ResponseEntity<Void> registrarLote(
            @RequestBody List<RegistrarAsistenciaItemDto> items,
            @AuthenticationPrincipal Jwt jwt) {
        String usuario = jwt != null ? jwt.getClaimAsString("email") : "sistema";
        registrarAsistenciaMasiva.ejecutar(
                items.stream().map(RegistrarAsistenciaItemDto::toCommand).toList(),
                usuario);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/clase/{claseId}")
    public ResponseEntity<List<AsistenciaResponseDto>> listarPorClase(
            @PathVariable("claseId") UUID claseId) {
        return ResponseEntity.ok(
                consultarAsistenciasPorClase.ejecutar(claseId).stream()
                        .map(AsistenciaResponseDto::from)
                        .toList());
    }

    /** POST /api/v1/asistencias/clase/{claseId} — frontend sends { asistencias: [{estudiante_id, estatus_asistencia}] } */
    @PostMapping("/clase/{claseId}")
    public ResponseEntity<Void> registrarPorClase(
            @PathVariable("claseId") UUID claseId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        String usuario = jwt != null ? jwt.getClaimAsString("email") : "sistema";
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lista = (List<Map<String, Object>>) body.get("asistencias");
        if (lista == null) return ResponseEntity.ok().build();
        List<RegistrarAsistenciaItemDto> items = lista.stream()
            .map(a -> new RegistrarAsistenciaItemDto(
                claseId,
                UUID.fromString(a.get("estudiante_id").toString()),
                a.get("estatus_asistencia") != null ? a.get("estatus_asistencia").toString() : "AUSENTE",
                null))
            .toList();
        registrarAsistenciaMasiva.ejecutar(
            items.stream().map(RegistrarAsistenciaItemDto::toCommand).toList(),
            usuario);
        return ResponseEntity.ok().build();
    }
}
