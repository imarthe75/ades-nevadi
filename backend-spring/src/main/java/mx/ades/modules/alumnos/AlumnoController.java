package mx.ades.modules.alumnos;

import lombok.RequiredArgsConstructor;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.sql.Date;

@RestController
@RequestMapping("/api/v1/alumnos")
@RequiredArgsConstructor
public class AlumnoController {

    private final EstudianteRepository repository;
    private final JdbcTemplate jdbc;
    private final AdesUserService userService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        UUID effectivePlantel = userService.getEffectivePlantelId(user, plantelId);

        StringBuilder sql = new StringBuilder("""
                SELECT e.id, e.matricula, e.nss, e.fecha_ingreso, e.is_active, e.tipo_alumno,
                       e.plantel_id, e.persona_id,
                       COALESCE(p.nombre_social, p.nombre) AS nombre,
                       p.apellido_paterno, p.apellido_materno, p.curp
                FROM ades_estudiantes e
                JOIN ades_personas p ON p.id = e.persona_id
                WHERE e.is_active = true
                """);

        Object[] params;
        if (effectivePlantel != null) {
            sql.append(" AND e.plantel_id = ?");
            params = new Object[]{effectivePlantel};
        } else {
            params = new Object[0];
        }
        sql.append(" ORDER BY p.apellido_paterno, p.nombre");

        List<Map<String, Object>> data = jdbc.query(sql.toString(), (rs, rowNum) -> {
            Map<String, Object> persona = new LinkedHashMap<>();
            persona.put("nombre", rs.getString("nombre"));
            persona.put("apellido_paterno", rs.getString("apellido_paterno"));
            persona.put("apellido_materno", rs.getString("apellido_materno"));
            persona.put("curp", rs.getString("curp"));

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getObject("id", UUID.class));
            row.put("matricula", rs.getString("matricula"));
            row.put("nss", rs.getString("nss"));
            row.put("fecha_ingreso", rs.getObject("fecha_ingreso"));
            row.put("is_active", rs.getBoolean("is_active"));
            row.put("tipo_alumno", rs.getString("tipo_alumno"));
            row.put("plantel_id", rs.getObject("plantel_id", UUID.class));
            row.put("persona_id", rs.getObject("persona_id", UUID.class));
            row.put("persona", persona);
            return row;
        }, params);

        return ResponseEntity.ok(Map.of("data", data, "total", data.size()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable("id") UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT e.*, " +
            "  p.nombre, p.apellido_paterno, p.apellido_materno, p.curp, p.rfc, p.genero, " +
            "  p.nombre_social, p.genero_autopercibido, p.pronombres, p.datos_sensibles_restringidos, " +
            "  p.fecha_nacimiento, p.telefono, p.email_personal, p.estado_civil, " +
            "  p.pais_nacimiento, p.municipio_nacimiento, p.estado_nacimiento, p.nacionalidad, p.foto_url " +
            "FROM ades_estudiantes e " +
            "JOIN ades_personas p ON p.id = e.persona_id " +
            "WHERE e.id = ?", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado");
        return ResponseEntity.ok(rows.get(0));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> patch(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, Object> body) {

        // 1. Verify alumno exists and get persona_id
        List<Map<String, Object>> existing = jdbc.queryForList(
            "SELECT persona_id FROM ades_estudiantes WHERE id = ?", id);
        if (existing.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado");
        UUID personaId = (UUID) existing.get(0).get("persona_id");

        // 2. Update ades_personas
        @SuppressWarnings("unchecked")
        Map<String, Object> per = (Map<String, Object>) body.get("persona");
        if (per != null) {
            jdbc.update(
                "UPDATE ades_personas SET nombre=COALESCE(?,nombre), apellido_paterno=COALESCE(?,apellido_paterno), " +
                "apellido_materno=?, curp=COALESCE(?,curp), genero=?, fecha_nacimiento=?, " +
                "telefono=?, email_personal=?, estado_civil=?, " +
                "pais_nacimiento=?, municipio_nacimiento=?, estado_nacimiento=?, " +
                "nacionalidad=COALESCE(?,nacionalidad), " +
                "nombre_social=?, genero_autopercibido=?, pronombres=? " +
                "WHERE id=?",
                per.get("nombre"), per.get("apellido_paterno"), per.get("apellido_materno"),
                per.get("curp"), per.get("genero"),
                per.get("fecha_nacimiento") != null ? java.sql.Date.valueOf(per.get("fecha_nacimiento").toString().substring(0, 10)) : null,
                per.get("telefono"), per.get("email_personal"), per.get("estado_civil"),
                per.get("pais_nacimiento"), per.get("municipio_nacimiento"), per.get("estado_nacimiento"),
                per.get("nacionalidad"),
                per.get("nombre_social"), per.get("genero_autopercibido"), per.get("pronombres"),
                personaId);
        }

        // 3. Update ades_estudiantes complementarios
        @SuppressWarnings("unchecked")
        Map<String, Object> comp = (Map<String, Object>) body.get("complementarios");
        if (comp != null) {
            Object lengInd = comp.get("lengua_indigena_id");
            Object nivIng  = comp.get("nivel_ingles_id");
            jdbc.update(
                "UPDATE ades_estudiantes SET nss=?, discapacidad=?, escuela_procedencia=?, " +
                "clave_ct_procedencia=?, promedio_procedencia=?, beca_tipo=?, beca_monto=?, " +
                "nivel_socioeconomico=?, etnia=?, " +
                "lengua_indigena_id=?::uuid, nivel_ingles_id=?::uuid " +
                "WHERE id=?",
                comp.get("nss"), comp.get("discapacidad"), comp.get("escuela_procedencia"),
                comp.get("clave_ct_procedencia"),
                comp.get("promedio_procedencia") != null ? Double.parseDouble(comp.get("promedio_procedencia").toString()) : null,
                comp.get("beca_tipo"),
                comp.get("beca_monto") != null ? Double.parseDouble(comp.get("beca_monto").toString()) : null,
                comp.get("nivel_socioeconomico"), comp.get("etnia"),
                lengInd != null ? lengInd.toString() : null,
                nivIng  != null ? nivIng.toString()  : null,
                id);
        }

        return ResponseEntity.ok(Map.of("updated", true));
    }

    @PostMapping
    public ResponseEntity<Estudiante> create(@RequestBody Estudiante est) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(est));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Estudiante> update(@PathVariable("id") UUID id, @RequestBody Estudiante update) {
        Estudiante est = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado"));

        est.setMatricula(update.getMatricula());
        est.setPersonaId(update.getPersonaId());
        est.setPlantelId(update.getPlantelId());
        est.setEstatusId(update.getEstatusId());
        est.setFechaIngreso(update.getFechaIngreso());
        est.setIsActive(update.getIsActive());

        return ResponseEntity.ok(repository.save(est));
    }
}
