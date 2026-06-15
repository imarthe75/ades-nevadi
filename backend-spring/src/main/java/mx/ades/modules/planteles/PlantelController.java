package mx.ades.modules.planteles;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/planteles")
@RequiredArgsConstructor
public class PlantelController {

    private final PlantelRepository repository;
    private final JdbcTemplate jdbc;

    @GetMapping("/stats")
    public ResponseEntity<List<Map<String, Object>>> stats() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Plantel> planteles = repository.findAll();

        for (Plantel p : planteles) {
            if (!Boolean.TRUE.equals(p.getIsActive())) {
                continue;
            }

            UUID pid = p.getId();
            
            // 1. Alumnos
            Long totalAlumnos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_estudiantes WHERE plantel_id = ? AND is_active = true",
                Long.class, pid
            );

            // 2. Profesores
            Long totalProfesores = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_profesores WHERE plantel_id = ? AND is_active = true",
                Long.class, pid
            );

            // 3. Grupos activos
            Long totalGrupos = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT g.id) FROM ades_grupos g JOIN ades_grados gr ON gr.id = g.grado_id JOIN ades_ciclos_escolares c ON c.id = g.ciclo_escolar_id WHERE gr.plantel_id = ? AND g.is_active = true AND c.es_vigente = true",
                Long.class, pid
            );

            // 4. Niveles
            List<Map<String, Object>> niveles = jdbc.queryForList(
                "SELECT n.id AS nivel_educativo_id, n.nombre_nivel, COUNT(DISTINCT gr.id) AS grados, COUNT(DISTINCT g.id) AS grupos_activos FROM ades_niveles_educativos n JOIN ades_grados gr ON gr.nivel_educativo_id = n.id LEFT JOIN ades_grupos g ON g.grado_id = gr.id AND g.is_active = true LEFT JOIN ades_ciclos_escolares c ON c.id = g.ciclo_escolar_id AND c.es_vigente = true WHERE gr.plantel_id = ? AND n.is_active = true GROUP BY n.id, n.nombre_nivel ORDER BY n.nombre_nivel",
                pid
            );

            result.add(Map.of(
                "id", pid,
                "nombre_plantel", p.getNombrePlantel(),
                "clave_ct", p.getClaveCt() != null ? p.getClaveCt() : "",
                "total_alumnos", totalAlumnos != null ? totalAlumnos : 0L,
                "total_profesores", totalProfesores != null ? totalProfesores : 0L,
                "total_grupos", totalGrupos != null ? totalGrupos : 0L,
                "niveles", niveles
            ));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/niveles")
    public ResponseEntity<List<Map<String, Object>>> nivelesPorPlantel(@PathVariable UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT DISTINCT ne.id, ne.nombre_nivel, ne.clave_nivel, ne.max_grados " +
                "FROM ades_niveles_educativos ne " +
                "JOIN ades_grados gr ON gr.nivel_educativo_id = ne.id " +
                "WHERE gr.plantel_id = ? AND gr.is_active = TRUE AND ne.is_active = TRUE " +
                "ORDER BY ne.nombre_nivel", id);
        return ResponseEntity.ok(rows);
    }

    @GetMapping
    public ResponseEntity<List<Plantel>> list() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Plantel> get(@PathVariable("id") UUID id) {
        Plantel plantel = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plantel no encontrado"));
        return ResponseEntity.ok(plantel);
    }

    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Plantel> create(@RequestBody Plantel plantel) {
        // ID is auto-assigned to UUID v7 in entity constructor
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(plantel));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Plantel> update(@PathVariable("id") UUID id, @RequestBody Plantel update) {
        Plantel plantel = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plantel no encontrado"));

        plantel.setNombrePlantel(update.getNombrePlantel());
        plantel.setEscuelaId(update.getEscuelaId());
        plantel.setClaveCt(update.getClaveCt());
        plantel.setEstatusId(update.getEstatusId());
        plantel.setIsActive(update.getIsActive());

        return ResponseEntity.ok(repository.save(plantel));
    }
}
