package mx.ades.modules.geo;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.geo.domain.port.out.GeoQueryPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Adaptador REST para el catálogo geográfico SEPOMEX.
 * Expone endpoints bajo /api/v1/geo para consultar estados, municipios y colonias
 * con IDs UUID (datos en public.ades_*, colonias deduplicadas por constraint UNIQUE).
 * No requiere autenticación para uso en formularios de dirección (selector-geo).
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/geo")
@RequiredArgsConstructor
public class GeoController {

    private final GeoQueryPort queryService;

    @GetMapping("/estados")
    public ResponseEntity<List<Map<String, Object>>> listarEstados() {
        return ResponseEntity.ok(queryService.estados());
    }

    @GetMapping("/municipios")
    public ResponseEntity<List<Map<String, Object>>> listarMunicipios(
            @RequestParam("estado_id") UUID estadoId) {
        return ResponseEntity.ok(queryService.municipios(estadoId));
    }

    @GetMapping("/colonias")
    public ResponseEntity<List<Map<String, Object>>> buscarColonias(
            @RequestParam(value = "cp", required = false) String cp,
            @RequestParam(value = "municipio_id", required = false) UUID municipioId) {
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
