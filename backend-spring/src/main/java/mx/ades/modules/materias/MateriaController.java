package mx.ades.modules.materias;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.materias.domain.port.in.ActualizarMateriaUseCase;
import mx.ades.modules.materias.domain.port.in.CrearMateriaUseCase;
import mx.ades.modules.materias.domain.port.out.MateriaRepositoryPort;
import mx.ades.modules.materias.query.MateriaQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST para el catálogo de materias del Instituto Nevadi.
 * Expone endpoints bajo /api/v1/materias para listar (con filtro por nivel educativo,
 * grupo y tipo), obtener, crear y actualizar materias. Soporta el campo
 * {@code campo_formativo} NEM (cuatro campos: Lenguajes, Saberes y Pensamiento Científico,
 * Ética y Naturaleza Humana, De lo Humano y lo Comunitario) para primaria/secundaria,
 * y materias CBU para preparatoria UAEMEX.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/materias")
@RequiredArgsConstructor
public class MateriaController {

    private final CrearMateriaUseCase crearUseCase;
    private final ActualizarMateriaUseCase actualizarUseCase;
    private final MateriaRepositoryPort repositoryPort;
    private final MateriaQueryService queryService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(name = "nivel_educativo_id", required = false) UUID nivelEducativoId,
            @RequestParam(name = "grupo_id", required = false) UUID grupoId,
            @RequestParam(name = "tipo", required = false) String tipo,
            @RequestParam(name = "incluir_inactivas", required = false, defaultValue = "false") boolean incluirInactivas) {
        return ResponseEntity.ok(queryService.listar(nivelEducativoId, grupoId, tipo, incluirInactivas));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Materia> get(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(repositoryPort.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Materia no encontrada")));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        CrearMateriaUseCase.Command cmd = new CrearMateriaUseCase.Command(
                (String) body.get("nombre_materia"),
                (String) body.get("clave_materia"),
                body.get("nivel_educativo_id") != null ? UUID.fromString(body.get("nivel_educativo_id").toString()) : null,
                body.get("horas_semana") != null ? new java.math.BigDecimal(body.get("horas_semana").toString()) : null,
                body.get("es_ingles") != null ? Boolean.valueOf(body.get("es_ingles").toString()) : false
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(crearUseCase.crear(cmd));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
        ActualizarMateriaUseCase.Command cmd = new ActualizarMateriaUseCase.Command(
                id,
                (String) body.get("nombre_materia"),
                (String) body.get("clave_materia"),
                body.get("nivel_educativo_id") != null ? UUID.fromString(body.get("nivel_educativo_id").toString()) : null,
                body.get("horas_semana") != null ? new java.math.BigDecimal(body.get("horas_semana").toString()) : null,
                body.get("es_ingles") != null ? Boolean.valueOf(body.get("es_ingles").toString()) : null,
                body.get("is_active") != null ? Boolean.valueOf(body.get("is_active").toString()) : null
        );
        return ResponseEntity.ok(actualizarUseCase.actualizar(cmd));
    }
}
