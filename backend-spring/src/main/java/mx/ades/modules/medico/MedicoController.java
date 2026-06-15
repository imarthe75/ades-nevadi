package mx.ades.modules.medico;

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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MedicoController {

    private final ExpedienteMedicoRepository expedienteRepository;
    private final IncidenteMedicoRepository incidenteRepository;
    private final JdbcTemplate jdbc;
    private final AdesUserService userService;

    @GetMapping("/expedientes-medicos/alumno/{estudianteId}")
    public ResponseEntity<ExpedienteMedico> obtenerExpediente(@PathVariable("estudianteId") UUID estudianteId) {
        ExpedienteMedico exp = expedienteRepository.findByEstudianteId(estudianteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expediente médico no encontrado"));
        return ResponseEntity.ok(exp);
    }

    @PostMapping("/expedientes-medicos")
    public ResponseEntity<ExpedienteMedico> crearExpediente(@RequestBody ExpedienteMedico data) {
        expedienteRepository.findByEstudianteId(data.getEstudianteId())
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "El alumno ya tiene expediente médico");
                });
        return ResponseEntity.status(HttpStatus.CREATED).body(expedienteRepository.save(data));
    }

    @PutMapping("/expedientes-medicos/{id}")
    public ResponseEntity<ExpedienteMedico> actualizarExpediente(
            @PathVariable("id") UUID id,
            @RequestBody ExpedienteMedico data) {

        ExpedienteMedico exp = expedienteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expediente no encontrado"));

        if (data.getTipoSangre() != null) exp.setTipoSangre(data.getTipoSangre());
        if (data.getAlergias() != null) exp.setAlergias(data.getAlergias());
        if (data.getMedicamentosAutorizados() != null) exp.setMedicamentosAutorizados(data.getMedicamentosAutorizados());
        if (data.getCondicionesCronicas() != null) exp.setCondicionesCronicas(data.getCondicionesCronicas());
        if (data.getObservacionesGenerales() != null) exp.setObservacionesGenerales(data.getObservacionesGenerales());
        if (data.getNss() != null) exp.setNss(data.getNss());
        if (data.getDiscapacidad() != null) exp.setDiscapacidad(data.getDiscapacidad());
        if (data.getSeguroMedicoTipo() != null) exp.setSeguroMedicoTipo(data.getSeguroMedicoTipo());
        if (data.getSeguroMedicoNumero() != null) exp.setSeguroMedicoNumero(data.getSeguroMedicoNumero());
        if (data.getVacunasAlDia() != null) exp.setVacunasAlDia(data.getVacunasAlDia());
        if (data.getPadecimientoCronico() != null) exp.setPadecimientoCronico(data.getPadecimientoCronico());
        if (data.getRequiereMedicacion() != null) exp.setRequiereMedicacion(data.getRequiereMedicacion());

        return ResponseEntity.ok(expedienteRepository.save(exp));
    }

    @GetMapping("/incidentes-medicos/alumno/{estudianteId}")
    public ResponseEntity<List<IncidenteMedico>> incidentesAlumno(@PathVariable("estudianteId") UUID estudianteId) {
        return ResponseEntity.ok(incidenteRepository.findByEstudianteIdAndIsActiveTrueOrderByFechaIncidenteDesc(estudianteId));
    }

    @PostMapping("/incidentes-medicos")
    public ResponseEntity<IncidenteMedico> registrarIncidente(@RequestBody IncidenteMedico data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(incidenteRepository.save(data));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PERSONAL DE SALUD — perfil del médico/enfermero
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/personal-salud")
    public ResponseEntity<Map<String, Object>> listPersonalSalud(
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @RequestParam(name = "buscar",     required = false) String buscar,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        StringBuilder sql = new StringBuilder("""
            SELECT ps.id, ps.numero_empleado, ps.cedula_profesional, ps.especialidad,
                   ps.tipo_contrato, ps.nivel_estudios, ps.turno,
                   ps.fecha_ingreso_inst, ps.rfc, ps.nss, ps.is_active,
                   ps.plantel_id, ps.persona_id,
                   p.nombre, p.apellido_paterno, p.apellido_materno,
                   p.curp, p.telefono, p.email_personal,
                   pl.nombre_plantel
            FROM ades_personal_salud ps
            JOIN ades_personas p  ON p.id  = ps.persona_id
            JOIN ades_planteles pl ON pl.id = ps.plantel_id
            WHERE ps.is_active = TRUE
            """);

        List<Object> params = new ArrayList<>();
        UUID effectivePlantel = userService.getEffectivePlantelId(user, plantelId);
        if (effectivePlantel != null) {
            sql.append(" AND ps.plantel_id = ?");
            params.add(effectivePlantel);
        }
        if (buscar != null && !buscar.isBlank()) {
            sql.append(" AND (p.nombre ILIKE ? OR p.apellido_paterno ILIKE ? OR CONCAT(p.nombre,' ',p.apellido_paterno) ILIKE ?)");
            String like = "%" + buscar.trim() + "%";
            params.add(like); params.add(like); params.add(like);
        }
        sql.append(" ORDER BY p.apellido_paterno, p.nombre");
        return ResponseEntity.ok(Map.of("data", jdbc.queryForList(sql.toString(), params.toArray())));
    }

    @GetMapping("/personal-salud/{id}")
    public ResponseEntity<Map<String, Object>> getPersonalSalud(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        userService.resolveUser(jwt);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT ps.*,
                   p.nombre, p.apellido_paterno, p.apellido_materno, p.curp,
                   p.genero, p.fecha_nacimiento, p.telefono, p.email_personal,
                   p.estado_civil, p.municipio_nacimiento, p.estado_nacimiento,
                   p.pais_nacimiento, p.nacionalidad, p.foto_url,
                   pl.nombre_plantel
            FROM ades_personal_salud ps
            JOIN ades_personas p   ON p.id  = ps.persona_id
            JOIN ades_planteles pl ON pl.id = ps.plantel_id
            WHERE ps.id = ?
            """, id);

        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal de salud no encontrado");
        return ResponseEntity.ok(rows.get(0));
    }

    @PostMapping("/personal-salud")
    public ResponseEntity<Map<String, Object>> createPersonalSalud(
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

        UUID saludId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO ades_personal_salud
              (id, persona_id, plantel_id, numero_empleado, cedula_profesional,
               especialidad, tipo_contrato, nivel_estudios, turno,
               rfc, nss, clabe, banco, fecha_ingreso_inst, fecha_fin_contrato,
               usuario_creacion, usuario_modificacion)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            saludId, personaId, UUID.fromString(plantelIdObj.toString()),
            lab.get("numero_empleado"), lab.get("cedula_profesional"),
            lab.get("especialidad"), lab.get("tipo_contrato"), lab.get("nivel_estudios"),
            lab.get("turno"), lab.get("rfc"), lab.get("nss"), lab.get("clabe"), lab.get("banco"),
            lab.get("fecha_ingreso_inst") != null
                ? java.sql.Date.valueOf(lab.get("fecha_ingreso_inst").toString().substring(0, 10)) : null,
            lab.get("fecha_fin_contrato") != null
                ? java.sql.Date.valueOf(lab.get("fecha_fin_contrato").toString().substring(0, 10)) : null,
            user.getUsername(), user.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(getPersonalSaludById(saludId));
    }

    @PatchMapping("/personal-salud/{id}")
    public ResponseEntity<Map<String, Object>> patchPersonalSalud(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);

        List<Map<String, Object>> existing = jdbc.queryForList(
            "SELECT persona_id FROM ades_personal_salud WHERE id = ? AND is_active = TRUE", id);
        if (existing.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal de salud no encontrado");
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
                  nacionalidad=COALESCE(?,nacionalidad), usuario_modificacion=?
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
                UPDATE ades_personal_salud SET
                  numero_empleado=?, cedula_profesional=?, especialidad=?,
                  tipo_contrato=?, nivel_estudios=?, turno=?,
                  rfc=?, nss=?, clabe=?, banco=?,
                  fecha_ingreso_inst=?, fecha_fin_contrato=?,
                  usuario_modificacion=?
                WHERE id=?
                """,
                lab.get("numero_empleado"), lab.get("cedula_profesional"), lab.get("especialidad"),
                lab.get("tipo_contrato"), lab.get("nivel_estudios"), lab.get("turno"),
                lab.get("rfc"), lab.get("nss"), lab.get("clabe"), lab.get("banco"),
                lab.get("fecha_ingreso_inst") != null
                    ? java.sql.Date.valueOf(lab.get("fecha_ingreso_inst").toString().substring(0, 10)) : null,
                lab.get("fecha_fin_contrato") != null
                    ? java.sql.Date.valueOf(lab.get("fecha_fin_contrato").toString().substring(0, 10)) : null,
                user.getUsername(), id);
        }

        return ResponseEntity.ok(getPersonalSaludById(id));
    }

    @DeleteMapping("/personal-salud/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivatePersonalSalud(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        userService.resolveUser(jwt);
        int n = jdbc.update(
            "UPDATE ades_personal_salud SET is_active = FALSE WHERE id = ? AND is_active = TRUE", id);
        if (n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal de salud no encontrado");
    }

    private Map<String, Object> getPersonalSaludById(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT ps.*,
                   p.nombre, p.apellido_paterno, p.apellido_materno, p.curp,
                   p.genero, p.fecha_nacimiento, p.telefono, p.email_personal,
                   p.estado_civil, p.municipio_nacimiento, p.estado_nacimiento,
                   p.pais_nacimiento, p.nacionalidad, p.foto_url,
                   pl.nombre_plantel
            FROM ades_personal_salud ps
            JOIN ades_personas p   ON p.id  = ps.persona_id
            JOIN ades_planteles pl ON pl.id = ps.plantel_id
            WHERE ps.id = ?
            """, id);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }
}
