package mx.ades.controller;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.catalogos.CicloEscolar;
import mx.ades.modules.catalogos.Grado;
import mx.ades.modules.catalogos.NivelEducativo;
import mx.ades.modules.catalogos.domain.port.out.CatalogReadPort;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adaptador de entrada REST que expone los catálogos de referencia de ADES
 * bajo {@code /api/v1/catalogs}.
 * <p>
 * Sirve datos de solo lectura que el frontend Angular consume para poblar
 * selectores en cascada (plantel → nivel → grado → grupo) y otros LOVs:
 * ciclos escolares, periodos, roles, países, nacionalidades y lenguas indígenas
 * (requeridas por el Reporte 911 SEP). Delega toda la recuperación al puerto
 * {@link mx.ades.modules.catalogos.domain.port.out.CatalogReadPort}, manteniendo
 * la separación hexagonal.
 * </p>
 * <p>
 * Los endpoints de este controlador son accesibles para cualquier usuario autenticado;
 * no filtran por plantel porque los catálogos son globales al sistema ADES.
 * </p>
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/catalogs")
@RequiredArgsConstructor
public class CatalogsController {

    private final CatalogReadPort catalogPort;
    private final JdbcTemplate jdbc;

    @GetMapping("/niveles")
    public ResponseEntity<List<NivelEducativo>> niveles() {
        return ResponseEntity.ok(catalogPort.findAllNiveles());
    }

    /**
     * DTO plano para Grado — evita serializar la entidad JPA directamente
     * (que expone {@code nivel_educativo}/{@code plantel} como objetos anidados
     * con metadata de proxy de Hibernate en vez de los ids/nombres planos que
     * espera el frontend, p. ej. {@code nivel_educativo_id}, {@code plantel_id}).
     */
    public record GradoDto(
            UUID id, Integer numeroGrado, String nombreGrado,
            UUID nivelEducativoId, String nombreNivel,
            UUID plantelId, String plantelNombre,
            Boolean isActive) {
    }

    private GradoDto toDto(Grado g) {
        return new GradoDto(
                g.getId(), g.getNumeroGrado(), g.getNombreGrado(),
                g.getNivelEducativo() != null ? g.getNivelEducativo().getId() : null,
                g.getNivelEducativo() != null ? g.getNivelEducativo().getNombreNivel() : null,
                g.getPlantel() != null ? g.getPlantel().getId() : null,
                g.getPlantel() != null ? g.getPlantel().getNombrePlantel() : null,
                g.getIsActive());
    }

    @GetMapping("/grados")
    public ResponseEntity<List<GradoDto>> grados(
            @RequestParam(name = "nivel_id", required = false) UUID nivelId,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @RequestParam(name = "todos_planteles", required = false, defaultValue = "false") boolean todosPlanteles) {
        // todos_planteles=true evita la deduplicación por (numero_grado, nivel) que
        // aplica CatalogReadAdapter.findGrados() cuando plantel_id es null — esa
        // deduplicación sirve al topbar (selector simplificado), pero Planes de
        // Estudio necesita ver la asignación real de cada plantel por separado.
        List<Grado> grados = (nivelId != null || plantelId != null)
                ? catalogPort.findGrados(nivelId, plantelId)
                : catalogPort.findAllGrados();
        if (todosPlanteles && plantelId == null) {
            grados = (nivelId != null)
                    ? catalogPort.findGradosSinDeduplicar(nivelId)
                    : catalogPort.findAllGrados();
        }
        return ResponseEntity.ok(grados.stream().map(this::toDto).collect(Collectors.toList()));
    }

    @GetMapping("/ciclos")
    public ResponseEntity<List<CicloEscolar>> ciclos(
            @RequestParam(name = "solo_vigentes", required = false, defaultValue = "false") boolean soloVigentes,
            @RequestParam(name = "nivel_id", required = false) UUID nivelId) {
        if (soloVigentes && nivelId != null) {
            return ResponseEntity.ok(catalogPort.findCiclosVigentesByNivel(nivelId));
        }
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
    public ResponseEntity<List<GradoDto>> gradosPorNivel(@PathVariable UUID nivelId) {
        return ResponseEntity.ok(catalogPort.findGrados(nivelId, null).stream().map(this::toDto).collect(Collectors.toList()));
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

    @GetMapping("/planteles")
    public ResponseEntity<List<Map<String, Object>>> planteles() {
        return ResponseEntity.ok(jdbc.queryForList(
            "SELECT id, nombre_plantel FROM ades_planteles WHERE is_active = true ORDER BY nombre_plantel"));
    }
}
