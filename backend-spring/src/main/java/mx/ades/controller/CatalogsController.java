package mx.ades.controller;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.catalogos.*;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;

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
        return ResponseEntity.ok(jdbc.queryForList(
                "SELECT id, nombre_rol, descripcion, nivel_acceso FROM ades_roles " +
                "WHERE is_active = TRUE ORDER BY nivel_acceso, nombre_rol"
        ));
    }

    @GetMapping("/periodos")
    public ResponseEntity<List<Map<String, Object>>> periodos(
            @RequestParam(name = "ciclo_id", required = false) UUID cicloId,
            @RequestParam(name = "grupo_id", required = false) UUID grupoId) {
        StringBuilder sql = new StringBuilder(
                "SELECT pe.id, pe.nombre_periodo, pe.numero_periodo, pe.tipo_periodo, " +
                "pe.ciclo_escolar_id, pe.fecha_inicio, pe.fecha_fin, pe.fecha_entrega_boletas " +
                "FROM ades_periodos_evaluacion pe WHERE pe.is_active = TRUE");
        List<Object> params = new java.util.ArrayList<>();
        if (cicloId != null) {
            sql.append(" AND pe.ciclo_escolar_id = ?");
            params.add(cicloId);
        } else if (grupoId != null) {
            // Resolve ciclo from grupo
            sql.append(" AND pe.ciclo_escolar_id = (SELECT ciclo_escolar_id FROM ades_grupos WHERE id = ?)");
            params.add(grupoId);
        }
        sql.append(" ORDER BY pe.numero_periodo");
        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }

    @GetMapping("/niveles/{nivelId}/grados")
    public ResponseEntity<List<Grado>> gradosPorNivel(@PathVariable UUID nivelId) {
        return ResponseEntity.ok(gradoRepo.findByNivelEducativoId(nivelId));
    }

    @GetMapping("/paises")
    public ResponseEntity<List<Map<String, Object>>> paises() {
        return ResponseEntity.ok(jdbc.queryForList(
                "SELECT id, clave_pais, nombre_pais, nacionalidad FROM ades_paises " +
                "WHERE is_active = TRUE ORDER BY nombre_pais"
        ));
    }

    @GetMapping("/nacionalidades")
    public ResponseEntity<List<Map<String, Object>>> nacionalidades() {
        return ResponseEntity.ok(jdbc.queryForList(
                "SELECT DISTINCT nacionalidad FROM ades_paises " +
                "WHERE is_active = TRUE AND nacionalidad IS NOT NULL " +
                "ORDER BY nacionalidad"
        ));
    }

    /** Catálogo INALI — 68 agrupaciones lingüísticas indígenas de México */
    @GetMapping("/lenguas-indigenas")
    public ResponseEntity<List<Map<String, Object>>> lenguasIndigenas(
            @RequestParam(name = "familia", required = false) String familia) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, familia_linguistica, agrupacion, autonym " +
                "FROM ades_lenguas_indigenas WHERE activa = TRUE ");
        List<Object> params = new java.util.ArrayList<>();
        if (familia != null && !familia.isBlank()) {
            sql.append("AND familia_linguistica = ? ");
            params.add(familia);
        }
        sql.append("ORDER BY agrupacion");
        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }

    /** Familias lingüísticas — para agrupar el LOV de lenguas */
    @GetMapping("/familias-linguisticas")
    public ResponseEntity<List<Map<String, Object>>> familiasLinguisticas() {
        return ResponseEntity.ok(jdbc.queryForList(
                "SELECT DISTINCT familia_linguistica, COUNT(*) AS total " +
                "FROM ades_lenguas_indigenas WHERE activa = TRUE " +
                "GROUP BY familia_linguistica ORDER BY familia_linguistica"
        ));
    }

    /** Catálogo CEFR — niveles de inglés A1-C2 */
    @GetMapping("/niveles-ingles")
    public ResponseEntity<List<Map<String, Object>>> nivelesIngles() {
        return ResponseEntity.ok(jdbc.queryForList(
                "SELECT id, nivel, nombre, descripcion, equivalencia_cambridge, " +
                "rango_toefl_ibt, rango_ielts " +
                "FROM ades_niveles_ingles WHERE activo = TRUE ORDER BY orden"
        ));
    }
}
