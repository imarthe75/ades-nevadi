package mx.ades.modules.calificaciones;

import lombok.Data;
import lombok.RequiredArgsConstructor;
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

    private final CalificacionesService service;
    private final JdbcTemplate jdbc;

    @PostMapping("/calcular")
    public ResponseEntity<Void> calcular(@RequestBody CalcularCalificacionRequest request) {
        service.calcularCalificacionPeriodo(
                request.getEstudianteId(),
                request.getInscripcionId(),
                request.getMateriaId(),
                request.getPeriodoId()
        );
        return ResponseEntity.ok().build();
    }

    @PostMapping("/manual")
    public ResponseEntity<CalificacionesPeriodo> guardarManual(@RequestBody CalificacionesPeriodo calificacion) {
        CalificacionesPeriodo guardada = service.guardarCalificacionManual(calificacion);
        return ResponseEntity.ok(guardada);
    }

    @GetMapping("/boleta/{estudianteId}")
    public ResponseEntity<List<CalificacionesPeriodo>> obtenerBoleta(@PathVariable("estudianteId") UUID estudianteId) {
        return ResponseEntity.ok(service.obtenerBoleta(estudianteId));
    }

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

    @Data
    public static class CalcularCalificacionRequest {
        private UUID estudianteId;
        private UUID inscripcionId;
        private UUID materiaId;
        private UUID periodoId;
    }
}
