package mx.ades.modules.calificaciones;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.calificaciones.domain.port.in.CalcularCalificacionPeriodoUseCase;
import mx.ades.modules.calificaciones.domain.port.in.GuardarCalificacionManualUseCase;
import mx.ades.modules.calificaciones.domain.port.in.ObtenerBoletaUseCase;
import mx.ades.modules.calificaciones.infrastructure.inbound.rest.dto.CalcularCalificacionDto;
import mx.ades.modules.calificaciones.infrastructure.inbound.rest.dto.CalificacionResponseDto;
import mx.ades.modules.calificaciones.infrastructure.inbound.rest.dto.GuardarCalificacionManualDto;
import mx.ades.modules.calificaciones.query.CalificacionesQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/calificaciones")
@RequiredArgsConstructor
public class CalificacionesController {

    private final CalcularCalificacionPeriodoUseCase calcularCalificacionPeriodo;
    private final GuardarCalificacionManualUseCase   guardarCalificacionManual;
    private final ObtenerBoletaUseCase               obtenerBoleta;
    private final CalificacionesQueryService         queryService;

    @PostMapping("/calcular")
    public ResponseEntity<Void> calcular(@RequestBody CalcularCalificacionDto req) {
        calcularCalificacionPeriodo.ejecutar(req.estudianteId(), req.inscripcionId(), req.materiaId(), req.periodoId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/manual")
    public ResponseEntity<CalificacionResponseDto> guardarManual(
            @RequestBody GuardarCalificacionManualDto req) {
        return ResponseEntity.ok(
                CalificacionResponseDto.from(guardarCalificacionManual.ejecutar(req.toDomain())));
    }

    @GetMapping("/boleta/{estudianteId}")
    public ResponseEntity<List<CalificacionResponseDto>> boleta(
            @PathVariable("estudianteId") UUID estudianteId) {
        return ResponseEntity.ok(
                obtenerBoleta.ejecutar(estudianteId).stream()
                        .map(CalificacionResponseDto::from)
                        .toList());
    }

    @GetMapping("/periodos")
    public ResponseEntity<List<Map<String, Object>>> periodos(
            @RequestParam(name = "ciclo_id", required = false) UUID cicloId) {
        return ResponseEntity.ok(queryService.periodos(cicloId));
    }
}
