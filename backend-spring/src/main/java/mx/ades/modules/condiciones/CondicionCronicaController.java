package mx.ades.modules.condiciones;

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
@RequestMapping("/api/v1/condiciones-cronicas")
@RequiredArgsConstructor
public class CondicionCronicaController {

    private final CondicionCronicaRepository repository;
    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    private static final Set<String> TIPOS = Set.of(
            "EPILEPSIA", "DIABETES", "ASMA", "ALERGIA", "CARDIACA",
            "HIPERTENSION", "DISCAPACIDAD_VISUAL", "DISCAPACIDAD_AUDITIVA", "OTRA"
    );

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(value = "alumno_id", required = false) UUID alumnoId,
            @RequestParam(value = "tipo_condicion", required = false) String tipoCondicion,
            @RequestParam(value = "solo_activas", defaultValue = "true") boolean soloActivas) {

        StringBuilder query = new StringBuilder(
                "SELECT c.id, c.alumno_id, e.numero_control, " +
                "p.nombre || ' ' || p.apellido_paterno AS alumno_nombre, " +
                "c.tipo_condicion, c.descripcion, c.medicacion_nombre, c.dosis, c.frecuencia, c.alergias, " +
                "c.medico_responsable, c.telefono_medico, c.activa, c.fecha_creacion " +
                "FROM ades_condiciones_cronicas c " +
                "JOIN ades_estudiantes e ON e.id = c.alumno_id " +
                "JOIN ades_personas p ON p.id = e.persona_id " +
                "WHERE c.is_active = TRUE ");

        List<Object> params = new ArrayList<>();

        if (alumnoId != null) {
            query.append("AND c.alumno_id = ? ");
            params.add(alumnoId);
        }
        if (tipoCondicion != null && !tipoCondicion.isBlank()) {
            query.append("AND c.tipo_condicion = ? ");
            params.add(tipoCondicion.toUpperCase());
        }
        if (soloActivas) {
            query.append("AND c.activa = TRUE ");
        }

        query.append("ORDER BY p.apellido_paterno, p.nombre, c.tipo_condicion");

        return ResponseEntity.ok(jdbc.queryForList(query.toString(), params.toArray()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(
            @RequestBody CondicionCronica body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permisos");
        }

        if (body.getTipoCondicion() == null || !TIPOS.contains(body.getTipoCondicion().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tipo_condicion inválido. Opciones: " + TIPOS);
        }

        body.setTipoCondicion(body.getTipoCondicion().toUpperCase());
        CondicionCronica saved = repository.save(body);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.getId()));
    }

    @GetMapping("/alumno/{alumnoId}/alerta")
    public ResponseEntity<List<Map<String, Object>>> alertaEmergencia(@PathVariable("alumnoId") UUID alumnoId) {
        String sql = "SELECT c.tipo_condicion, c.descripcion, c.medicacion_nombre, " +
                "c.dosis, c.frecuencia, c.alergias, c.medico_responsable, c.telefono_medico, " +
                "p.nombre || ' ' || p.apellido_paterno AS alumno_nombre, " +
                "e.numero_control, " +
                "cf.nombre_completo AS contacto_emergencia, " +
                "cf.telefono AS tel_emergencia " +
                "FROM ades_condiciones_cronicas c " +
                "JOIN ades_estudiantes e ON e.id = c.alumno_id " +
                "JOIN ades_personas p ON p.id = e.persona_id " +
                "LEFT JOIN ades_contactos_familiares cf ON cf.estudiante_id = c.alumno_id " +
                "AND cf.es_contacto_emergencia = TRUE AND cf.is_active = TRUE " +
                "WHERE c.alumno_id = ? AND c.activa = TRUE AND c.is_active = TRUE " +
                "ORDER BY c.tipo_condicion";

        return ResponseEntity.ok(jdbc.queryForList(sql, alumnoId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CondicionCronica> obtener(@PathVariable("id") UUID id) {
        CondicionCronica cc = repository.findById(id)
                .filter(CondicionCronica::getIsActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condición no encontrada"));
        return ResponseEntity.ok(cc);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizar(
            @PathVariable("id") UUID id,
            @RequestBody CondicionCronica body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permisos");
        }

        CondicionCronica cc = repository.findById(id)
                .filter(CondicionCronica::getIsActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condición no encontrada"));

        if (body.getDescripcion() != null) cc.setDescripcion(body.getDescripcion());
        if (body.getMedicacionNombre() != null) cc.setMedicacionNombre(body.getMedicacionNombre());
        if (body.getDosis() != null) cc.setDosis(body.getDosis());
        if (body.getFrecuencia() != null) cc.setFrecuencia(body.getFrecuencia());
        if (body.getAlergias() != null) cc.setAlergias(body.getAlergias());
        if (body.getMedicoResponsable() != null) cc.setMedicoResponsable(body.getMedicoResponsable());
        if (body.getTelefonoMedico() != null) cc.setTelefonoMedico(body.getTelefonoMedico());
        if (body.getActiva() != null) cc.setActiva(body.getActiva());

        repository.save(cc);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permisos");
        }

        CondicionCronica cc = repository.findById(id)
                .filter(CondicionCronica::getIsActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condición no encontrada"));

        cc.setIsActive(false);
        repository.save(cc);
    }
}
