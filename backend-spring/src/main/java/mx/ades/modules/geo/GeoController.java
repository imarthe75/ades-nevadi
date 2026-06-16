package mx.ades.modules.geo;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.geo.query.GeoQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/geo")
@RequiredArgsConstructor
public class GeoController {

    private final GeoQueryService queryService;

    @GetMapping("/estados")
    public ResponseEntity<List<Map<String, Object>>> listarEstados() {
        return ResponseEntity.ok(queryService.estados());
    }

    @GetMapping("/municipios")
    public ResponseEntity<List<Map<String, Object>>> listarMunicipios(
            @RequestParam("estado_id") int estadoId) {
        return ResponseEntity.ok(queryService.municipios(estadoId));
    }

    @GetMapping("/colonias")
    public ResponseEntity<List<Map<String, Object>>> buscarColonias(
            @RequestParam(value = "cp", required = false) String cp,
            @RequestParam(value = "municipio_id", required = false) Integer municipioId) {
        if (cp != null && !cp.isBlank()) {
            return ResponseEntity.ok(queryService.coloniasPorCp(cp));
        } else if (municipioId != null) {
            return ResponseEntity.ok(queryService.coloniasPorMunicipio(municipioId));
        }
        return ResponseEntity.ok(Collections.emptyList());
    }

    @GetMapping("/buscar-cp/{cp}")
    public ResponseEntity<Map<String, Object>> buscarPorCp(
            @PathVariable("cp") String cp) {
        return ResponseEntity.ok(queryService.buscarPorCp(cp));
    }
}
