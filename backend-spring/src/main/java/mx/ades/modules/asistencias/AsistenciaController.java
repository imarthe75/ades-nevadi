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
}
