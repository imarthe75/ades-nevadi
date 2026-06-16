package mx.ades.modules.asistencias;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.asistencias.query.ClaseQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clases")
@RequiredArgsConstructor
public class ClaseController {

    private final ClaseService service;
    private final ClaseQueryService queryService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(value = "grupo_id", required = false) UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @RequestParam(value = "profesor_id", required = false) UUID profesorId,
            @RequestParam(value = "fecha_desde", required = false) LocalDate fechaDesde,
            @RequestParam(value = "fecha_hasta", required = false) LocalDate fechaHasta,
            @RequestParam(value = "estatus", required = false) String estatus) {
        return ResponseEntity.ok(queryService.listar(grupoId, materiaId, profesorId, fechaDesde, fechaHasta, estatus));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> obtener(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(queryService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<Clase> crear(@RequestBody Clase clase) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(clase));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Clase> actualizar(@PathVariable("id") UUID id, @RequestBody Clase clase) {
        return ResponseEntity.ok(service.actualizar(id, clase));
    }

    @GetMapping("/{id}/alumnos-esperados")
    public ResponseEntity<List<Map<String, Object>>> alumnosEsperados(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(service.alumnosEsperados(id));
    }
}
