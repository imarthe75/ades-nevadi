package mx.ades.modules.evaluaciones;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.evaluaciones.query.RubricaQueryService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST para la gestión de rúbricas de evaluación.
 * Expone endpoints bajo /api/v1/rubricas para crear, listar, actualizar y eliminar
 * rúbricas con sus criterios de evaluación. Cada criterio tiene ponderación,
 * orden y niveles de logro configurables. Las rúbricas se asocian a materias y
 * niveles educativos. La eliminación lógica (is_active = false) preserva el historial.
 * Aplica scoping por usuario para auditoría (usuarioCreacion/usuarioModificacion).
 * Toda operación requiere JWT válido via {@code resolveUser}.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/rubricas")
@RequiredArgsConstructor
public class RubricaController {

    private final RubricaRepository repository;
    private final RubricaCriterioRepository criterioRepository;
    private final AdesUserService userService;
    private final RubricaQueryService queryService;

    @Data
    public static class CriterioPayload {
        @NotBlank(message = "nombreCriterio es obligatorio")
        private String nombreCriterio;

        private String descripcion;

        @NotNull(message = "ponderacion es obligatoria")
        @DecimalMin(value = "0", message = "ponderacion mínima 0")
        @DecimalMax(value = "100", message = "ponderacion máxima 100 (porcentaje)")
        private BigDecimal ponderacion = BigDecimal.ZERO;

        private Integer orden = 1;

        // Se persiste como String (columna jsonb sin @Convert, ver RubricaCriterio.java);
        // el frontend envía/recibe texto JSON, no un arreglo nativo.
        private String nivelesLogro;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(name = "materia_id", required = false) UUID materiaId,
            @RequestParam(name = "nivel_educativo_id", required = false) UUID nivelEducativoId,
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.listar(materiaId, nivelEducativoId, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        Map<String, Object> rubrica = queryService.detalle(id);
        if (rubrica == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rúbrica no encontrada");
        return ResponseEntity.ok(rubrica);
    }

    @PostMapping
    @CacheEvict(value = "rubricas", allEntries = true)
    public ResponseEntity<Rubrica> create(
            @RequestBody Rubrica rubrica,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // Rubrica es entidad JPA (no anotamos validación en la entidad — riesgo sobre
        // Hibernate); nombre_rubrica es NOT NULL en BD, validamos manualmente para dar
        // un 422 claro en vez de una excepción de integridad de datos.
        if (rubrica.getNombreRubrica() == null || rubrica.getNombreRubrica().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "nombre_rubrica es obligatorio");
        }
        rubrica.setUsuarioCreacion(user.getUsername());
        rubrica.setUsuarioModificacion(user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(rubrica));
    }

    @PutMapping("/{id}")
    @CacheEvict(value = "rubricas", allEntries = true)
    public ResponseEntity<Rubrica> update(
            @PathVariable("id") UUID id,
            @RequestBody Rubrica update,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (update.getNombreRubrica() == null || update.getNombreRubrica().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "nombre_rubrica es obligatorio");
        }
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
    @CacheEvict(value = "rubricas", allEntries = true)
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

    @PostMapping("/{rubrica_id}/criterios")
    @CacheEvict(value = "rubricas", allEntries = true)
    public ResponseEntity<RubricaCriterio> addCriterio(
            @PathVariable("rubrica_id") UUID rubricaId,
            @RequestBody @Valid CriterioPayload payload,
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
    @CacheEvict(value = "rubricas", allEntries = true)
    public ResponseEntity<Map<String, Object>> updateCriterio(
            @PathVariable("rubrica_id") UUID rubricaId,
            @PathVariable("criterio_id") UUID criterioId,
            @RequestBody @Valid CriterioPayload payload,
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
    @CacheEvict(value = "rubricas", allEntries = true)
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
