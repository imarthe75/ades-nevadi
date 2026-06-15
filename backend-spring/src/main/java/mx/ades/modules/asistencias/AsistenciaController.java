package mx.ades.modules.asistencias;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/asistencias")
@RequiredArgsConstructor
public class AsistenciaController {

    private final AsistenciaService service;

    @PostMapping("/registrar-lote")
    public ResponseEntity<Void> registrarLote(@RequestBody List<Asistencia> asistencias) {
        service.registrarAsistenciaMasiva(asistencias);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/clase/{claseId}")
    public ResponseEntity<List<Asistencia>> listarPorClase(@PathVariable("claseId") UUID claseId) {
        return ResponseEntity.ok(service.listarPorClase(claseId));
    }
}
