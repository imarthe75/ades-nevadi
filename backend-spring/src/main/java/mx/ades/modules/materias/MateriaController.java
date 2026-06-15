package mx.ades.modules.materias;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/materias")
@RequiredArgsConstructor
public class MateriaController {

    private final MateriaRepository repository;
    private final JdbcTemplate jdbc;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(name = "nivel_educativo_id", required = false) UUID nivelEducativoId,
            @RequestParam(name = "grupo_id", required = false) UUID grupoId,
            @RequestParam(name = "tipo", required = false) String tipo,
            @RequestParam(name = "incluir_inactivas", required = false, defaultValue = "false") boolean incluirInactivas) {

        StringBuilder sql = new StringBuilder(
            "SELECT m.id, m.nombre_materia, m.clave_materia, m.nivel_educativo_id, " +
            "  m.horas_semana, m.tipo_materia, m.es_inglés AS es_ingles, m.is_active, " +
            "  ne.nombre_nivel " +
            "FROM ades_materias m " +
            "LEFT JOIN ades_niveles_educativos ne ON ne.id = m.nivel_educativo_id " +
            "WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (!incluirInactivas) {
            sql.append("AND m.is_active = TRUE ");
        }
        if (tipo != null && !tipo.isBlank()) {
            sql.append("AND m.tipo_materia LIKE ? ");
            params.add(tipo.toUpperCase() + "%");
        }
        if (grupoId != null) {
            sql.append("AND m.nivel_educativo_id = (" +
                "SELECT gr.nivel_educativo_id FROM ades_grados gr " +
                "JOIN ades_grupos g ON g.grado_id = gr.id WHERE g.id = ?) ");
            params.add(grupoId);
        } else if (nivelEducativoId != null) {
            sql.append("AND m.nivel_educativo_id = ? ");
            params.add(nivelEducativoId);
        }

        sql.append("ORDER BY m.tipo_materia, m.nombre_materia");
        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Materia> get(@PathVariable("id") UUID id) {
        Materia mat = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Materia no encontrada"));
        return ResponseEntity.ok(mat);
    }

    @PostMapping
    public ResponseEntity<Materia> create(@RequestBody Materia mat) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(mat));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Materia> update(@PathVariable("id") UUID id, @RequestBody Materia update) {
        Materia mat = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Materia no encontrada"));

        mat.setNombreMateria(update.getNombreMateria());
        mat.setClaveMateria(update.getClaveMateria());
        mat.setNivelEducativoId(update.getNivelEducativoId());
        mat.setHorasSemana(update.getHorasSemana());
        mat.setEsIngles(update.getEsIngles());
        mat.setIsActive(update.getIsActive());

        return ResponseEntity.ok(repository.save(mat));
    }
}
