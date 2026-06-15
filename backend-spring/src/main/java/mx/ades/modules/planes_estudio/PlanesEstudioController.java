package mx.ades.modules.planes_estudio;

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
@RequestMapping("/api/v1/planes-estudio")
@RequiredArgsConstructor
public class PlanesEstudioController {

    private final JdbcTemplate jdbc;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(name = "ciclo_id", required = false) UUID cicloId,
            @RequestParam(name = "grado_id", required = false) UUID gradoId,
            @RequestParam(name = "nivel_id", required = false) UUID nivelId) {

        StringBuilder sql = new StringBuilder("""
                SELECT mp.id, mp.materia_id, mp.grado_id, mp.ciclo_escolar_id,
                       mp.horas_semana, mp.es_obligatoria, mp.orden, mp.is_active,
                       m.nombre_materia, m.clave_materia,
                       g.nombre_grado, g.numero_grado,
                       ce.nombre_ciclo,
                       ne.id AS nivel_educativo_id, ne.nombre_nivel
                FROM ades_materias_plan mp
                JOIN ades_materias m ON m.id = mp.materia_id
                JOIN ades_grados g ON g.id = mp.grado_id
                JOIN ades_ciclos_escolares ce ON ce.id = mp.ciclo_escolar_id
                JOIN ades_nivel_educativo ne ON ne.id = g.nivel_educativo_id
                WHERE mp.is_active = TRUE
                """);

        List<Object> params = new ArrayList<>();
        if (cicloId != null) {
            sql.append(" AND mp.ciclo_escolar_id = ?");
            params.add(cicloId);
        }
        if (gradoId != null) {
            sql.append(" AND mp.grado_id = ?");
            params.add(gradoId);
        }
        if (nivelId != null) {
            sql.append(" AND g.nivel_educativo_id = ?");
            params.add(nivelId);
        }
        sql.append(" ORDER BY g.numero_grado, mp.orden, m.nombre_materia");

        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        UUID materiaId = UUID.fromString((String) body.get("materia_id"));
        UUID gradoId = UUID.fromString((String) body.get("grado_id"));
        UUID cicloId = UUID.fromString((String) body.get("ciclo_escolar_id"));
        Number horasSemana = body.get("horas_semana") instanceof Number ? (Number) body.get("horas_semana") : 0;
        Boolean esObligatoria = body.get("es_obligatoria") instanceof Boolean ? (Boolean) body.get("es_obligatoria") : true;

        try {
            jdbc.update(
                "INSERT INTO ades_materias_plan (id, materia_id, grado_id, ciclo_escolar_id, " +
                "horas_semana, es_obligatoria, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'sistema', 'sistema')",
                id, materiaId, gradoId, cicloId, horasSemana.doubleValue(), esObligatoria
            );
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "La materia ya está asignada a este grado y ciclo: " + e.getMessage());
        }

        List<Map<String, Object>> result = jdbc.queryForList(
            "SELECT mp.id, mp.materia_id, mp.grado_id, mp.ciclo_escolar_id, mp.horas_semana, " +
            "mp.es_obligatoria, m.nombre_materia, g.nombre_grado " +
            "FROM ades_materias_plan mp JOIN ades_materias m ON m.id = mp.materia_id " +
            "JOIN ades_grados g ON g.id = mp.grado_id WHERE mp.id = ?", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(result.isEmpty() ? Map.of("id", id) : result.get(0));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> patch(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, Object> body) {

        if (body.containsKey("horas_semana")) {
            Number hs = (Number) body.get("horas_semana");
            jdbc.update("UPDATE ades_materias_plan SET horas_semana = ? WHERE id = ?", hs.doubleValue(), id);
        }
        if (body.containsKey("es_obligatoria")) {
            jdbc.update("UPDATE ades_materias_plan SET es_obligatoria = ? WHERE id = ?",
                body.get("es_obligatoria"), id);
        }
        if (body.containsKey("orden")) {
            jdbc.update("UPDATE ades_materias_plan SET orden = ? WHERE id = ?",
                body.get("orden"), id);
        }
        return ResponseEntity.ok(Map.of("id", id, "updated", true));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        int rows = jdbc.update("UPDATE ades_materias_plan SET is_active = FALSE WHERE id = ?", id);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan de estudio no encontrado");
        }
        return ResponseEntity.noContent().build();
    }
}
