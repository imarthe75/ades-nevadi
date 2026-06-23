package mx.ades.controller;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.catalogos.CicloEscolar;
import mx.ades.modules.catalogos.Grado;
import mx.ades.modules.catalogos.NivelEducativo;
import mx.ades.modules.catalogos.domain.port.out.CatalogReadPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalogs")
@RequiredArgsConstructor
public class CatalogsController {

    private final CatalogReadPort catalogPort;

    @GetMapping("/niveles")
    public ResponseEntity<List<NivelEducativo>> niveles() {
        return ResponseEntity.ok(catalogPort.findAllNiveles());
    }

    @GetMapping("/grados")
    public ResponseEntity<List<Grado>> grados(
            @RequestParam(name = "nivel_id", required = false) UUID nivelId,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId) {
        return ResponseEntity.ok((nivelId != null || plantelId != null)
                ? catalogPort.findGrados(nivelId, plantelId)
                : catalogPort.findAllGrados());
    }

    @GetMapping("/ciclos")
    public ResponseEntity<List<CicloEscolar>> ciclos(
            @RequestParam(name = "solo_vigentes", required = false, defaultValue = "false") boolean soloVigentes) {
        return ResponseEntity.ok(soloVigentes
                ? catalogPort.findCiclosVigentes()
                : catalogPort.findAllCiclos());
    }

    @GetMapping("/roles")
    public ResponseEntity<List<Map<String, Object>>> roles() {
        return ResponseEntity.ok(catalogPort.roles());
    }

    @GetMapping("/periodos")
    public ResponseEntity<List<Map<String, Object>>> periodos(
            @RequestParam(name = "ciclo_id", required = false) UUID cicloId,
            @RequestParam(name = "grupo_id", required = false) UUID grupoId) {
        return ResponseEntity.ok(catalogPort.periodos(cicloId, grupoId));
    }

    @GetMapping("/niveles/{nivelId}/grados")
    public ResponseEntity<List<Grado>> gradosPorNivel(@PathVariable UUID nivelId) {
        return ResponseEntity.ok(catalogPort.findGrados(nivelId, null));
    }

    @GetMapping("/paises")
    public ResponseEntity<List<Map<String, Object>>> paises() {
        return ResponseEntity.ok(catalogPort.paises());
    }

    @GetMapping("/nacionalidades")
    public ResponseEntity<List<Map<String, Object>>> nacionalidades() {
        return ResponseEntity.ok(catalogPort.nacionalidades());
    }

    @GetMapping("/lenguas-indigenas")
    public ResponseEntity<List<Map<String, Object>>> lenguasIndigenas(
            @RequestParam(name = "familia", required = false) String familia) {
        return ResponseEntity.ok(catalogPort.lenguasIndigenas(familia));
    }

    @GetMapping("/familias-linguisticas")
    public ResponseEntity<List<Map<String, Object>>> familiasLinguisticas() {
        return ResponseEntity.ok(catalogPort.familiasLinguisticas());
    }

    @GetMapping("/niveles-ingles")
    public ResponseEntity<List<Map<String, Object>>> nivelesIngles() {
        return ResponseEntity.ok(catalogPort.nivelesIngles());
    }
}
