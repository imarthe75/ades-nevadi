package mx.ades.modules.personal_admin;

import lombok.RequiredArgsConstructor;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/v1/personal-admin")
@RequiredArgsConstructor
public class PersonalAdminController {

    private final JdbcTemplate jdbc;
    private final AdesUserService userService;

    // ── LIST ──────────────────────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @RequestParam(name = "tipo_rol",   required = false) String tipoRol,
            @RequestParam(name = "buscar",     required = false) String buscar,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder("""
            SELECT pa.id, pa.numero_empleado, pa.tipo_rol, pa.area,
                   pa.tipo_contrato, pa.nivel_estudios, pa.cedula_profesional,
                   pa.especialidad, pa.turno, pa.fecha_ingreso_inst,
                   pa.rfc, pa.nss, pa.is_active, pa.plantel_id, pa.persona_id,
                   p.nombre, p.apellido_paterno, p.apellido_materno,
                   p.curp, p.telefono, p.email_personal,
                   pl.nombre_plantel
            FROM ades_personal_administrativo pa
            JOIN ades_personas p  ON p.id  = pa.persona_id
            JOIN ades_planteles pl ON pl.id = pa.plantel_id
            WHERE pa.is_active = TRUE
            """);

        List<Object> params = new ArrayList<>();

        UUID effectivePlantel = userService.getEffectivePlantelId(user, plantelId);
        if (effectivePlantel != null) {
            sql.append(" AND pa.plantel_id = ?");
            params.add(effectivePlantel);
        }

        if (tipoRol != null && !tipoRol.isBlank()) {
            sql.append(" AND pa.tipo_rol = ?");
            params.add(tipoRol.trim().toUpperCase());
        }

        if (buscar != null && !buscar.isBlank()) {
            sql.append(" AND (p.nombre ILIKE ? OR p.apellido_paterno ILIKE ? OR p.apellido_materno ILIKE ?" +
                       " OR CONCAT(p.nombre,' ',p.apellido_paterno) ILIKE ?)");
            String like = "%" + buscar.trim() + "%";
            params.add(like); params.add(like); params.add(like); params.add(like);
        }

        sql.append(" ORDER BY pa.tipo_rol, p.apellido_paterno, p.nombre");

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        return ResponseEntity.ok(Map.of("data", rows, "total", rows.size()));
    }

    // ── GET ───────────────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        userService.resolveUser(jwt);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT pa.*,
                   p.nombre, p.apellido_paterno, p.apellido_materno, p.curp,
                   p.genero, p.fecha_nacimiento, p.telefono, p.email_personal,
                   p.estado_civil, p.municipio_nacimiento, p.estado_nacimiento,
                   p.pais_nacimiento, p.nacionalidad, p.foto_url,
                   pl.nombre_plantel
            FROM ades_personal_administrativo pa
            JOIN ades_personas p   ON p.id  = pa.persona_id
            JOIN ades_planteles pl ON pl.id = pa.plantel_id
            WHERE pa.id = ?
            """, id);

        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal no encontrado");
        return ResponseEntity.ok(rows.get(0));
    }

    // ── POST ──────────────────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);

        @SuppressWarnings("unchecked")
        Map<String, Object> per = (Map<String, Object>) body.get("persona");
        @SuppressWarnings("unchecked")
        Map<String, Object> lab = (Map<String, Object>) body.get("laborales");

        if (per == null || lab == null)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "persona y laborales son requeridos");

        Object plantelIdObj = body.get("plantel_id");
        if (plantelIdObj == null)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "plantel_id es requerido");

        Object tipoRolObj = lab.get("tipo_rol");
        if (tipoRolObj == null)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "tipo_rol es requerido");

        // 1. Crear persona
        UUID personaId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO ades_personas
              (id, nombre, apellido_paterno, apellido_materno, curp, genero,
               fecha_nacimiento, telefono, email_personal, estado_civil,
               pais_nacimiento, municipio_nacimiento, estado_nacimiento, nacionalidad,
               usuario_creacion, usuario_modificacion)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            personaId,
            per.get("nombre"), per.get("apellido_paterno"), per.get("apellido_materno"),
            per.get("curp"), per.get("genero"),
            per.get("fecha_nacimiento") != null
                ? java.sql.Date.valueOf(per.get("fecha_nacimiento").toString().substring(0, 10)) : null,
            per.get("telefono"), per.get("email_personal"), per.get("estado_civil"),
            per.getOrDefault("pais_nacimiento", "México"),
            per.get("municipio_nacimiento"), per.get("estado_nacimiento"),
            per.getOrDefault("nacionalidad", "Mexicana"),
            user.getUsername(), user.getUsername());

        // 2. Crear empleado administrativo
        UUID empleadoId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO ades_personal_administrativo
              (id, persona_id, plantel_id, numero_empleado, tipo_rol, area,
               tipo_contrato, nivel_estudios, cedula_profesional, especialidad, turno,
               rfc, nss, clabe, banco, fecha_ingreso_inst, fecha_fin_contrato,
               usuario_creacion, usuario_modificacion)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            empleadoId, personaId, UUID.fromString(plantelIdObj.toString()),
            lab.get("numero_empleado"),
            tipoRolObj.toString().toUpperCase(),
            lab.get("area"),
            lab.get("tipo_contrato"), lab.get("nivel_estudios"),
            lab.get("cedula_profesional"), lab.get("especialidad"), lab.get("turno"),
            lab.get("rfc"), lab.get("nss"), lab.get("clabe"), lab.get("banco"),
            lab.get("fecha_ingreso_inst") != null
                ? java.sql.Date.valueOf(lab.get("fecha_ingreso_inst").toString().substring(0, 10)) : null,
            lab.get("fecha_fin_contrato") != null
                ? java.sql.Date.valueOf(lab.get("fecha_fin_contrato").toString().substring(0, 10)) : null,
            user.getUsername(), user.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(getById(empleadoId));
    }

    // ── PATCH ─────────────────────────────────────────────────────────────────
    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> patch(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);

        List<Map<String, Object>> existing = jdbc.queryForList(
            "SELECT persona_id FROM ades_personal_administrativo WHERE id = ? AND is_active = TRUE", id);
        if (existing.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal no encontrado");
        UUID personaId = (UUID) existing.get(0).get("persona_id");

        @SuppressWarnings("unchecked")
        Map<String, Object> per = (Map<String, Object>) body.get("persona");
        if (per != null) {
            jdbc.update("""
                UPDATE ades_personas SET
                  nombre=COALESCE(?,nombre), apellido_paterno=COALESCE(?,apellido_paterno),
                  apellido_materno=?, curp=COALESCE(?,curp), genero=?,
                  fecha_nacimiento=?, telefono=?, email_personal=?, estado_civil=?,
                  pais_nacimiento=?, municipio_nacimiento=?, estado_nacimiento=?,
                  nacionalidad=COALESCE(?,nacionalidad),
                  usuario_modificacion=?
                WHERE id=?
                """,
                per.get("nombre"), per.get("apellido_paterno"), per.get("apellido_materno"),
                per.get("curp"), per.get("genero"),
                per.get("fecha_nacimiento") != null
                    ? java.sql.Date.valueOf(per.get("fecha_nacimiento").toString().substring(0, 10)) : null,
                per.get("telefono"), per.get("email_personal"), per.get("estado_civil"),
                per.get("pais_nacimiento"), per.get("municipio_nacimiento"), per.get("estado_nacimiento"),
                per.get("nacionalidad"), user.getUsername(), personaId);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> lab = (Map<String, Object>) body.get("laborales");
        if (lab != null) {
            jdbc.update("""
                UPDATE ades_personal_administrativo SET
                  tipo_rol=COALESCE(UPPER(?),tipo_rol), numero_empleado=?, area=?,
                  tipo_contrato=?, nivel_estudios=?, cedula_profesional=?,
                  especialidad=?, turno=?, rfc=?, nss=?, clabe=?, banco=?,
                  fecha_ingreso_inst=?, fecha_fin_contrato=?,
                  usuario_modificacion=?
                WHERE id=?
                """,
                lab.get("tipo_rol"), lab.get("numero_empleado"), lab.get("area"),
                lab.get("tipo_contrato"), lab.get("nivel_estudios"), lab.get("cedula_profesional"),
                lab.get("especialidad"), lab.get("turno"), lab.get("rfc"), lab.get("nss"),
                lab.get("clabe"), lab.get("banco"),
                lab.get("fecha_ingreso_inst") != null
                    ? java.sql.Date.valueOf(lab.get("fecha_ingreso_inst").toString().substring(0, 10)) : null,
                lab.get("fecha_fin_contrato") != null
                    ? java.sql.Date.valueOf(lab.get("fecha_fin_contrato").toString().substring(0, 10)) : null,
                user.getUsername(), id);
        }

        return ResponseEntity.ok(getById(id));
    }

    // ── DELETE (soft) ─────────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        userService.resolveUser(jwt);
        int n = jdbc.update(
            "UPDATE ades_personal_administrativo SET is_active = FALSE WHERE id = ? AND is_active = TRUE", id);
        if (n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal no encontrado");
    }

    // ── HELPER ────────────────────────────────────────────────────────────────
    private Map<String, Object> getById(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT pa.*,
                   p.nombre, p.apellido_paterno, p.apellido_materno, p.curp,
                   p.genero, p.fecha_nacimiento, p.telefono, p.email_personal,
                   p.estado_civil, p.municipio_nacimiento, p.estado_nacimiento,
                   p.pais_nacimiento, p.nacionalidad, p.foto_url,
                   pl.nombre_plantel
            FROM ades_personal_administrativo pa
            JOIN ades_personas p   ON p.id  = pa.persona_id
            JOIN ades_planteles pl ON pl.id = pa.plantel_id
            WHERE pa.id = ?
            """, id);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }
}
