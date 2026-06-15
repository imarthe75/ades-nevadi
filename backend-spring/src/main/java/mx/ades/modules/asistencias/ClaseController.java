package mx.ades.modules.asistencias;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clases")
@RequiredArgsConstructor
public class ClaseController {

    private final ClaseService service;
    private final JdbcTemplate jdbc;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(value = "grupo_id", required = false) UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @RequestParam(value = "profesor_id", required = false) UUID profesorId,
            @RequestParam(value = "fecha_desde", required = false) LocalDate fechaDesde,
            @RequestParam(value = "fecha_hasta", required = false) LocalDate fechaHasta,
            @RequestParam(value = "estatus", required = false) String estatus) {

        StringBuilder sql = new StringBuilder(
                "SELECT c.id, c.horario_id, c.grupo_id, c.materia_id, c.profesor_id, " +
                "c.fecha_clase, c.hora_inicio, c.hora_fin, c.tema_visto, c.observaciones, " +
                "c.estatus_clase, c.is_active, c.row_version, c.fecha_creacion, c.fecha_modificacion, " +
                "c.usuario_creacion, c.usuario_modificacion, " +
                "g.nombre_grupo AS grupo_nombre, " +
                "m.nombre_materia AS materia_nombre " +
                "FROM ades_clases c " +
                "LEFT JOIN ades_grupos g ON g.id = c.grupo_id " +
                "LEFT JOIN ades_materias m ON m.id = c.materia_id " +
                "WHERE c.is_active = TRUE "
        );

        List<Object> params = new ArrayList<>();
        if (grupoId != null) {
            sql.append("AND c.grupo_id = ? ");
            params.add(grupoId);
        }
        if (materiaId != null) {
            sql.append("AND c.materia_id = ? ");
            params.add(materiaId);
        }
        if (profesorId != null) {
            sql.append("AND c.profesor_id = ? ");
            params.add(profesorId);
        }
        if (fechaDesde != null) {
            sql.append("AND c.fecha_clase >= ? ");
            params.add(fechaDesde);
        }
        if (fechaHasta != null) {
            sql.append("AND c.fecha_clase <= ? ");
            params.add(fechaHasta);
        }
        if (estatus != null && !estatus.isBlank()) {
            sql.append("AND c.estatus_clase = ? ");
            params.add(estatus.toUpperCase());
        }

        sql.append("ORDER BY c.fecha_clase DESC, c.hora_inicio ASC");

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> obtener(@PathVariable("id") UUID id) {
        String sql = "SELECT c.id, c.horario_id, c.grupo_id, c.materia_id, c.profesor_id, " +
                "c.fecha_clase, c.hora_inicio, c.hora_fin, c.tema_visto, c.observaciones, " +
                "c.estatus_clase, c.is_active, c.row_version, c.fecha_creacion, c.fecha_modificacion, " +
                "c.usuario_creacion, c.usuario_modificacion, " +
                "g.nombre_grupo AS grupo_nombre, " +
                "m.nombre_materia AS materia_nombre " +
                "FROM ades_clases c " +
                "LEFT JOIN ades_grupos g ON g.id = c.grupo_id " +
                "LEFT JOIN ades_materias m ON m.id = c.materia_id " +
                "WHERE c.id = ?";

        List<Map<String, Object>> rows = jdbc.queryForList(sql, id);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Clase no encontrada");
        }
        return ResponseEntity.ok(rows.get(0));
    }

    @PostMapping
    public ResponseEntity<Clase> crear(@RequestBody Clase clase) {
        Clase saved = service.crear(clase);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Clase> actualizar(@PathVariable("id") UUID id, @RequestBody Clase clase) {
        Clase updated = service.actualizar(id, clase);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{id}/alumnos-esperados")
    public ResponseEntity<List<Map<String, Object>>> alumnosEsperados(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(service.alumnosEsperados(id));
    }
}
