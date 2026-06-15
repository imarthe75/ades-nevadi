package mx.ades.modules.calificaciones;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.calificaciones.domain.port.in.CalcularCalificacionPeriodoUseCase;
import mx.ades.modules.calificaciones.domain.port.in.GuardarCalificacionManualUseCase;
import mx.ades.modules.calificaciones.domain.port.in.ObtenerBoletaUseCase;
import mx.ades.modules.calificaciones.infrastructure.inbound.rest.dto.CalcularCalificacionDto;
import mx.ades.modules.calificaciones.infrastructure.inbound.rest.dto.CalificacionResponseDto;
import mx.ades.modules.calificaciones.infrastructure.inbound.rest.dto.GuardarCalificacionManualDto;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/calificaciones")
@RequiredArgsConstructor
public class CalificacionesController {

    private final CalcularCalificacionPeriodoUseCase calcular;
    private final GuardarCalificacionManualUseCase   guardarManual;
    private final ObtenerBoletaUseCase               boleta;
    private final JdbcTemplate jdbc;

    @PostMapping("/calcular")
    public ResponseEntity<Void> calcular(@RequestBody CalcularCalificacionDto req) {
        calcular.ejecutar(req.estudianteId(), req.inscripcionId(), req.materiaId(), req.periodoId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/manual")
    public ResponseEntity<CalificacionResponseDto> guardarManual(
            @RequestBody GuardarCalificacionManualDto req) {
        return ResponseEntity.ok(
                CalificacionResponseDto.from(guardarManual.ejecutar(req.toDomain())));
    }

    @GetMapping("/boleta/{estudianteId}")
    public ResponseEntity<List<CalificacionResponseDto>> obtenerBoleta(
            @PathVariable("estudianteId") UUID estudianteId) {
        return ResponseEntity.ok(
                boleta.ejecutar(estudianteId).stream()
                        .map(CalificacionResponseDto::from)
                        .toList());
    }

    // Catálogo de períodos — lectura simple, queda en controller (TIER 4 intent)
    @GetMapping("/periodos")
    public ResponseEntity<List<Map<String, Object>>> periodos(
            @RequestParam(name = "ciclo_id", required = false) UUID cicloId) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, nombre_periodo, numero_periodo, tipo_periodo, ciclo_escolar_id, " +
                "fecha_inicio, fecha_fin, fecha_entrega_boletas " +
                "FROM ades_periodos_evaluacion WHERE is_active = TRUE");
        Object[] params;
        if (cicloId != null) {
            sql.append(" AND ciclo_escolar_id = ?");
            params = new Object[]{cicloId};
        } else {
            params = new Object[0];
        }
        sql.append(" ORDER BY numero_periodo");
        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params));
    }
}
