package mx.ades.controller;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.catalogos.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalogs")
@RequiredArgsConstructor
public class CatalogsController {

    private final NivelEducativoRepository nivelRepo;
    private final GradoRepository gradoRepo;
    private final CicloEscolarRepository cicloRepo;
    private final CatalogsQueryService catalogsQuery;

    @GetMapping("/niveles")
    public ResponseEntity<List<NivelEducativo>> niveles() {
        return ResponseEntity.ok(nivelRepo.findAll());
    }

    @GetMapping("/grados")
    public ResponseEntity<List<Grado>> grados(
            @RequestParam(name = "nivel_id", required = false) UUID nivelId) {
        if (nivelId != null) {
            return ResponseEntity.ok(gradoRepo.findByNivelEducativoId(nivelId));
        }
        return ResponseEntity.ok(gradoRepo.findAll());
    }

    @GetMapping("/ciclos")
    public ResponseEntity<List<CicloEscolar>> ciclos(
            @RequestParam(name = "solo_vigentes", required = false, defaultValue = "false") boolean soloVigentes) {
        if (soloVigentes) {
            return ResponseEntity.ok(cicloRepo.findByEsVigenteTrue());
        }
        return ResponseEntity.ok(cicloRepo.findAll());
    }

    @GetMapping("/roles")
    public ResponseEntity<List<Map<String, Object>>> roles() {
        return ResponseEntity.ok(catalogsQuery.roles());
    }

    @GetMapping("/periodos")
    public ResponseEntity<List<Map<String, Object>>> periodos(
            @RequestParam(name = "ciclo_id", required = false) UUID cicloId,
            @RequestParam(name = "grupo_id", required = false) UUID grupoId) {
        return ResponseEntity.ok(catalogsQuery.periodos(cicloId, grupoId));
    }

    @GetMapping("/niveles/{nivelId}/grados")
    public ResponseEntity<List<Grado>> gradosPorNivel(@PathVariable UUID nivelId) {
        return ResponseEntity.ok(gradoRepo.findByNivelEducativoId(nivelId));
    }

    @GetMapping("/paises")
    public ResponseEntity<List<Map<String, Object>>> paises() {
        return ResponseEntity.ok(catalogsQuery.paises());
    }

    @GetMapping("/nacionalidades")
    public ResponseEntity<List<Map<String, Object>>> nacionalidades() {
        return ResponseEntity.ok(catalogsQuery.nacionalidades());
    }

    @GetMapping("/lenguas-indigenas")
    public ResponseEntity<List<Map<String, Object>>> lenguasIndigenas(
            @RequestParam(name = "familia", required = false) String familia) {
        return ResponseEntity.ok(catalogsQuery.lenguasIndigenas(familia));
    }

    @GetMapping("/familias-linguisticas")
    public ResponseEntity<List<Map<String, Object>>> familiasLinguisticas() {
        return ResponseEntity.ok(catalogsQuery.familiasLinguisticas());
    }

    @GetMapping("/niveles-ingles")
    public ResponseEntity<List<Map<String, Object>>> nivelesIngles() {
        return ResponseEntity.ok(catalogsQuery.nivelesIngles());
    }
}
