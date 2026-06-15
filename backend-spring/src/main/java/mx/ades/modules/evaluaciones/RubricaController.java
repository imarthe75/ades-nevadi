package mx.ades.modules.evaluaciones;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rubricas")
@RequiredArgsConstructor
public class RubricaController {

    private final RubricaRepository repository;
    private final RubricaCriterioRepository criterioRepository;
    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    @Data
    public static class CriterioPayload {
        private String nombreCriterio;
        private String descripcion;
        private BigDecimal ponderacion = BigDecimal.ZERO;
        private Integer orden = 1;
        private String nivelesLogro;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(name = "materia_id", required = false) UUID materiaId,
            @RequestParam(name = "nivel_educativo_id", required = false) UUID nivelEducativoId,
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder(
                "SELECT r.id, r.nombre_rubrica, r.descripcion, " +
                "r.materia_id, m.nombre_materia, " +
                "r.nivel_educativo_id, ne.nombre_nivel, " +
                "COUNT(rc.id) AS total_criterios, " +
                "ROUND(COALESCE(SUM(rc.ponderacion), 0), 1) AS ponderacion_total " +
                "FROM ades_rubricas r " +
                "LEFT JOIN ades_materias m ON m.id = r.materia_id " +
                "LEFT JOIN ades_niveles_educativos ne ON ne.id = r.nivel_educativo_id " +
                "LEFT JOIN ades_rubrica_criterios rc ON rc.rubrica_id = r.id AND rc.is_active = TRUE " +
                "WHERE r.is_active = TRUE"
        );
        List<Object> params = new ArrayList<>();

        if (materiaId != null) {
            sql.append(" AND r.materia_id = ?");
            params.add(materiaId);
        }
        if (nivelEducativoId != null) {
            sql.append(" AND r.nivel_educativo_id = ?");
            params.add(nivelEducativoId);
        }

        sql.append(" GROUP BY r.id, m.nombre_materia, ne.nombre_nivel ");
        sql.append(" ORDER BY r.fecha_creacion DESC LIMIT ?");
        params.add(limit);

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        List<Map<String, Object>> rList = jdbc.queryForList(
                "SELECT r.*, m.nombre_materia, ne.nombre_nivel " +
                "FROM ades_rubricas r " +
                "LEFT JOIN ades_materias m ON m.id = r.materia_id " +
                "LEFT JOIN ades_niveles_educativos ne ON ne.id = r.nivel_educativo_id " +
                "WHERE r.id = ? AND r.is_active = TRUE", id);

        if (rList.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rúbrica no encontrada");
        }

        Map<String, Object> rubrica = rList.get(0);
        List<RubricaCriterio> criterios = criterioRepository.findByRubricaIdAndIsActiveTrueOrderByOrdenAsc(id);
        rubrica.put("criterios", criterios);

        return ResponseEntity.ok(rubrica);
    }

    @PostMapping
    public ResponseEntity<Rubrica> create(
            @RequestBody Rubrica rubrica,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        rubrica.setUsuarioCreacion(user.getUsername());
        rubrica.setUsuarioModificacion(user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(rubrica));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Rubrica> update(
            @PathVariable("id") UUID id,
            @RequestBody Rubrica update,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        Rubrica rubrica = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rúbrica no encontrada"));

        rubrica.setNombreRubrica(update.getNombreRubrica());
        rubrica.setDescripcion(update.getDescripcion());
        rubrica.setMateriaId(update.getMateriaId());
        rubrica.setNivelEducativoId(update.getNivelEducativoId());
        rubrica.setIsActive(update.getIsActive());
        rubrica.setUsuarioModificacion(user.getUsername());

        return ResponseEntity.ok(repository.save(rubrica));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        Rubrica rubrica = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rúbrica no encontrada"));

        rubrica.setIsActive(false);
        rubrica.setUsuarioModificacion(user.getUsername());
        repository.save(rubrica);

        return ResponseEntity.noContent().build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Criterios
    // ──────────────────────────────────────────────────────────────────────────
    @PostMapping("/{rubrica_id}/criterios")
    public ResponseEntity<RubricaCriterio> addCriterio(
            @PathVariable("rubrica_id") UUID rubricaId,
            @RequestBody CriterioPayload payload,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (!repository.existsById(rubricaId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rúbrica no encontrada");
        }

        RubricaCriterio rc = new RubricaCriterio();
        rc.setRubricaId(rubricaId);
        rc.setNombreCriterio(payload.getNombreCriterio());
        rc.setDescripcion(payload.getDescripcion());
        rc.setPonderacion(payload.getPonderacion());
        rc.setOrden(payload.getOrden());
        rc.setNivelesLogro(payload.getNivelesLogro());
        rc.setUsuarioCreacion(user.getUsername());
        rc.setUsuarioModificacion(user.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(criterioRepository.save(rc));
    }

    @PutMapping("/{rubrica_id}/criterios/{criterio_id}")
    public ResponseEntity<Map<String, Object>> updateCriterio(
            @PathVariable("rubrica_id") UUID rubricaId,
            @PathVariable("criterio_id") UUID criterioId,
            @RequestBody CriterioPayload payload,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        RubricaCriterio rc = criterioRepository.findById(criterioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Criterio no encontrado"));

        if (!rc.getRubricaId().equals(rubricaId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El criterio no pertenece a la rúbrica indicada");
        }

        rc.setNombreCriterio(payload.getNombreCriterio());
        rc.setDescripcion(payload.getDescripcion());
        rc.setPonderacion(payload.getPonderacion());
        rc.setOrden(payload.getOrden());
        rc.setNivelesLogro(payload.getNivelesLogro());
        rc.setUsuarioModificacion(user.getUsername());

        criterioRepository.save(rc);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/{rubrica_id}/criterios/{criterio_id}")
    public ResponseEntity<Void> deleteCriterio(
            @PathVariable("rubrica_id") UUID rubricaId,
            @PathVariable("criterio_id") UUID criterioId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        RubricaCriterio rc = criterioRepository.findById(criterioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Criterio no encontrado"));

        if (!rc.getRubricaId().equals(rubricaId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El criterio no pertenece a la rúbrica indicada");
        }

        rc.setIsActive(false);
        rc.setUsuarioModificacion(user.getUsername());
        criterioRepository.save(rc);

        return ResponseEntity.noContent().build();
    }
}
