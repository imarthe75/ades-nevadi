package mx.ades.modules.profesores;

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

@RestController
@RequestMapping("/api/v1/profesores")
@RequiredArgsConstructor
public class ProfesorController {

    private final ProfesorRepository repository;
    private final JdbcTemplate jdbc;
    private final AdesUserService userService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @RequestParam(name = "buscar", required = false) String buscar,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        UUID effectivePlantel = userService.getEffectivePlantelId(user, plantelId);

        StringBuilder sql = new StringBuilder("""
                SELECT pr.id, pr.numero_empleado, pr.rfc, pr.nss, pr.cedula_profesional,
                       pr.especialidad, pr.turno, pr.tipo_contrato, pr.nivel_estudios,
                       pr.fecha_ingreso_inst, pr.is_active, pr.plantel_id, pr.persona_id,
                       p.nombre, p.apellido_paterno, p.apellido_materno, p.curp
                FROM ades_profesores pr
                JOIN ades_personas p ON p.id = pr.persona_id
                WHERE pr.is_active = true
                """);

        List<Object> paramList = new java.util.ArrayList<>();
        if (effectivePlantel != null) {
            sql.append(" AND pr.plantel_id = ?");
            paramList.add(effectivePlantel);
        }
        if (buscar != null && !buscar.isBlank()) {
            sql.append(" AND (p.nombre ILIKE ? OR p.apellido_paterno ILIKE ? OR p.apellido_materno ILIKE ? OR CONCAT(p.nombre,' ',p.apellido_paterno) ILIKE ?)");
            String like = "%" + buscar.trim() + "%";
            paramList.add(like); paramList.add(like); paramList.add(like); paramList.add(like);
        }
        sql.append(" ORDER BY p.apellido_paterno, p.nombre");
        Object[] params = paramList.toArray();

        List<Map<String, Object>> data = jdbc.query(sql.toString(), (rs, rowNum) -> {
            Map<String, Object> persona = new LinkedHashMap<>();
            persona.put("nombre", rs.getString("nombre"));
            persona.put("apellido_paterno", rs.getString("apellido_paterno"));
            persona.put("apellido_materno", rs.getString("apellido_materno"));
            persona.put("curp", rs.getString("curp"));

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getObject("id", UUID.class));
            row.put("numero_empleado", rs.getString("numero_empleado"));
            row.put("rfc", rs.getString("rfc"));
            row.put("nss", rs.getString("nss"));
            row.put("cedula_profesional", rs.getString("cedula_profesional"));
            row.put("especialidad", rs.getString("especialidad"));
            row.put("turno", rs.getString("turno"));
            row.put("tipo_contrato", rs.getString("tipo_contrato"));
            row.put("nivel_estudios", rs.getString("nivel_estudios"));
            row.put("fecha_ingreso_inst", rs.getObject("fecha_ingreso_inst"));
            row.put("is_active", rs.getBoolean("is_active"));
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
            "SELECT pr.*, " +
            "  p.nombre, p.apellido_paterno, p.apellido_materno, p.curp, p.rfc AS rfc_persona, " +
            "  p.genero, p.fecha_nacimiento, p.telefono, p.email_personal, p.estado_civil, " +
            "  p.municipio_nacimiento, p.estado_nacimiento, p.pais_nacimiento, " +
            "  p.nacionalidad, p.foto_url " +
            "FROM ades_profesores pr " +
            "JOIN ades_personas p ON p.id = pr.persona_id " +
            "WHERE pr.id = ?", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profesor no encontrado");
        return ResponseEntity.ok(rows.get(0));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> patch(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, Object> body) {

        List<Map<String, Object>> existing = jdbc.queryForList(
            "SELECT persona_id FROM ades_profesores WHERE id = ?", id);
        if (existing.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profesor no encontrado");
        UUID personaId = (UUID) existing.get(0).get("persona_id");

        @SuppressWarnings("unchecked")
        Map<String, Object> per = (Map<String, Object>) body.get("persona");
        if (per != null) {
            jdbc.update(
                "UPDATE ades_personas SET nombre=COALESCE(?,nombre), apellido_paterno=COALESCE(?,apellido_paterno), " +
                "apellido_materno=?, curp=COALESCE(?,curp), genero=?, fecha_nacimiento=?, " +
                "telefono=?, email_personal=?, estado_civil=?, " +
                "pais_nacimiento=?, municipio_nacimiento=?, estado_nacimiento=?, " +
                "nacionalidad=COALESCE(?,nacionalidad) " +
                "WHERE id=?",
                per.get("nombre"), per.get("apellido_paterno"), per.get("apellido_materno"),
                per.get("curp"), per.get("genero"),
                per.get("fecha_nacimiento") != null ? java.sql.Date.valueOf(per.get("fecha_nacimiento").toString().substring(0, 10)) : null,
                per.get("telefono"), per.get("email_personal"), per.get("estado_civil"),
                per.get("pais_nacimiento"), per.get("municipio_nacimiento"), per.get("estado_nacimiento"),
                per.get("nacionalidad"), personaId);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> lab = (Map<String, Object>) body.get("laborales");
        if (lab != null) {
            jdbc.update(
                "UPDATE ades_profesores SET tipo_contrato=?, rfc=?, nss=?, cedula_profesional=?, " +
                "especialidad=?, nivel_estudios=?, " +
                "fecha_ingreso_inst=?, clabe=?, banco=?, turno=? " +
                "WHERE id=?",
                lab.get("tipo_contrato"), lab.get("rfc"), lab.get("nss"), lab.get("cedula_profesional"),
                lab.get("especialidad"), lab.get("nivel_estudios"),
                lab.get("fecha_ingreso_inst") != null ? java.sql.Date.valueOf(lab.get("fecha_ingreso_inst").toString().substring(0, 10)) : null,
                lab.get("clabe"), lab.get("banco"), lab.get("turno"),
                id);
        }

        return ResponseEntity.ok(Map.of("updated", true));
    }

    @PostMapping
    public ResponseEntity<Profesor> create(@RequestBody Profesor prof) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(prof));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Profesor> update(@PathVariable("id") UUID id, @RequestBody Profesor update) {
        Profesor prof = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profesor no encontrado"));

        prof.setNumeroEmpleado(update.getNumeroEmpleado());
        prof.setPersonaId(update.getPersonaId());
        prof.setPlantelId(update.getPlantelId());
        prof.setEstatusId(update.getEstatusId());
        prof.setTipoContrato(update.getTipoContrato());
        prof.setIsActive(update.getIsActive());

        return ResponseEntity.ok(repository.save(prof));
    }
}
