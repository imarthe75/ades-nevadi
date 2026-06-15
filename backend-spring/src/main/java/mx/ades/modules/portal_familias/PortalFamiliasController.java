package mx.ades.modules.portal_familias;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.util.*;

@RestController
@RequestMapping("/api/v1/portal-familias")
@RequiredArgsConstructor
public class PortalFamiliasController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    @Data
    public static class TutorIn {
        private UUID personaId;
        private String relacion = "TUTOR";
        private Boolean esResponsableEconomico = false;
        private Boolean esContactoEmergencia = false;
        private Integer prioridad = 1;
        private Boolean puedeRecoger = true;
        private String nivelAccesoPortal = "LECTURA";
    }

    @Data
    public static class CrearUsuarioPadreIn {
        private UUID tutorAlumnoId;
        private String email;
        private String nombreCompleto;
    }

    @Data
    public static class RestriccionTutorIn {
        private Boolean puedeVerCalificaciones = true;
        private Boolean puedeVerAsistencias = true;
        private Boolean puedeVerConducta = true;
        private Boolean puedeVerTareas = true;
        private Boolean puedeDescargarDocumentos = true;
        private Boolean puedeComunicarseDocentes = true;
        private String razonRestriccion;
    }

    @GetMapping("/tutores/{alumno_id}")
    public ResponseEntity<List<Map<String, Object>>> listarTutores(
            @PathVariable("alumno_id") UUID alumnoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        String sql = "SELECT ta.id, ta.relacion, ta.prioridad, ta.puede_recoger, " +
                "ta.es_responsable_economico, ta.es_contacto_emergencia, " +
                "ta.nivel_acceso_portal, ta.is_active, " +
                "p.nombre, p.apellido_paterno, p.apellido_materno, " +
                "p.telefono_principal, p.email " +
                "FROM ades_tutores_alumnos ta " +
                "JOIN ades_personas p ON p.id = ta.persona_id " +
                "WHERE ta.alumno_id = ? AND ta.is_active = TRUE " +
                "ORDER BY ta.prioridad";

        return ResponseEntity.ok(jdbc.queryForList(sql, alumnoId));
    }

    @PostMapping("/tutores/{alumno_id}")
    public ResponseEntity<Map<String, Object>> agregarTutor(
            @PathVariable("alumno_id") UUID alumnoId,
            @RequestBody TutorIn body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        if (user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere Coordinador o superior");
        }

        // Verificar alumno existe
        List<Map<String, Object>> students = jdbc.queryForList(
                "SELECT id FROM ades_estudiantes WHERE id = ? AND is_active = TRUE",
                alumnoId
        );
        if (students.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado");
        }

        UUID newId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_tutores_alumnos " +
                "(id, alumno_id, persona_id, relacion, prioridad, puede_recoger, " +
                " es_responsable_economico, es_contacto_emergencia, nivel_acceso_portal, " +
                " usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                newId, alumnoId, body.getPersonaId(), body.getRelacion(), body.getPrioridad(),
                body.getPuedeRecoger(), body.getEsResponsableEconomico(), body.getEsContactoEmergencia(),
                body.getNivelAccesoPortal(), user.getId().toString(), user.getId().toString()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", newId.toString(),
                "message", "Tutor vinculado al alumno"
        ));
    }

    @DeleteMapping("/tutores/{tutor_alumno_id}")
    public ResponseEntity<Map<String, Object>> desvincularTutor(
            @PathVariable("tutor_alumno_id") UUID tutorAlumnoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        if (user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere Coordinador o superior");
        }

        jdbc.update(
                "UPDATE ades_tutores_alumnos SET is_active = FALSE, usuario_modificacion = ? WHERE id = ?",
                user.getId().toString(), tutorAlumnoId
        );

        return ResponseEntity.ok(Map.of("message", "Tutor desvinculado"));
    }

    @PostMapping("/crear-usuario")
    public ResponseEntity<Map<String, Object>> crearUsuarioPadre(
            @RequestBody CrearUsuarioPadreIn body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        if (user.getNivelAcceso() > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere Admin Plantel o superior");
        }

        List<Map<String, Object>> ta = jdbc.queryForList(
                "SELECT id, alumno_id FROM ades_tutores_alumnos WHERE id = ? AND is_active = TRUE",
                body.getTutorAlumnoId()
        );
        if (ta.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vínculo tutor-alumno no encontrado");
        }

        String payload = String.format("{\"tutor_alumno_id\": \"%s\", \"email\": \"%s\", \"nombre\": \"%s\"}",
                body.getTutorAlumnoId(), body.getEmail(), body.getNombreCompleto()
        );

        UUID newTaskId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_tareas_sistema " +
                "(id, tipo_tarea, payload_json, estado, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, 'CREAR_USUARIO_PADRE', ?::jsonb, 'PENDIENTE', ?, ?)",
                newTaskId, payload, user.getId().toString(), user.getId().toString()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Solicitud de creación de usuario encolada. Se enviará invitación al correo.",
                "email", body.getEmail()
        ));
    }

    @PostMapping("/restriccion/{tutor_alumno_id}")
    public ResponseEntity<Map<String, Object>> establecerRestriccion(
            @PathVariable("tutor_alumno_id") UUID tutorAlumnoId,
            @RequestBody RestriccionTutorIn body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        if (user.getNivelAcceso() > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere Admin Plantel o superior");
        }

        jdbc.update(
                "INSERT INTO ades_restricciones_tutor " +
                "(tutor_alumno_id, puede_ver_calificaciones, puede_ver_asistencias, " +
                " puede_ver_conducta, puede_ver_tareas, puede_descargar_documentos, " +
                " puede_comunicarse_docentes, razon_restriccion, " +
                " usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (tutor_alumno_id) DO UPDATE SET " +
                " puede_ver_calificaciones = EXCLUDED.puede_ver_calificaciones, " +
                " puede_ver_asistencias = EXCLUDED.puede_ver_asistencias, " +
                " puede_ver_conducta = EXCLUDED.puede_ver_conducta, " +
                " puede_ver_tareas = EXCLUDED.puede_ver_tareas, " +
                " puede_descargar_documentos = EXCLUDED.puede_descargar_documentos, " +
                " puede_comunicarse_docentes = EXCLUDED.puede_comunicarse_docentes, " +
                " razon_restriccion = EXCLUDED.razon_restriccion, " +
                " usuario_modificacion = EXCLUDED.usuario_modificacion",
                tutorAlumnoId, body.getPuedeVerCalificaciones(), body.getPuedeVerAsistencias(),
                body.getPuedeVerConducta(), body.getPuedeVerTareas(), body.getPuedeDescargarDocumentos(),
                body.getPuedeComunicarseDocentes(), body.getRazonRestriccion(),
                user.getId().toString(), user.getId().toString()
        );

        return ResponseEntity.ok(Map.of("message", "Restricciones de acceso actualizadas"));
    }

    @GetMapping("/mis-alumnos")
    public ResponseEntity<List<Map<String, Object>>> misAlumnos(
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        String email = user.getEmail() != null ? user.getEmail() : "";

        String sql = "SELECT e.id AS alumno_id, e.matricula AS numero_control, " +
                "p.nombre, p.apellido_paterno, p.apellido_materno, " +
                "g.nombre_grupo, pl.nombre_plantel, " +
                "ta.relacion, ta.nivel_acceso_portal " +
                "FROM ades_tutores_alumnos ta " +
                "JOIN ades_personas per ON per.email = ? " +
                "JOIN ades_personas p ON p.id = (SELECT persona_id FROM ades_estudiantes WHERE id = ta.alumno_id) " +
                "JOIN ades_estudiantes e ON e.id = ta.alumno_id " +
                "LEFT JOIN ades_inscripciones i ON i.estudiante_id = e.id AND i.is_active = TRUE " +
                "LEFT JOIN ades_grupos g ON g.id = i.grupo_id " +
                "LEFT JOIN ades_planteles pl ON pl.id = g.plantel_id " +
                "WHERE ta.persona_id = per.id AND ta.is_active = TRUE AND e.is_active = TRUE";

        return ResponseEntity.ok(jdbc.queryForList(sql, email));
    }

    @GetMapping("/resumen/{alumno_id}")
    public ResponseEntity<Map<String, Object>> resumenAcademico(
            @PathVariable("alumno_id") UUID alumnoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        List<Map<String, Object>> calif = jdbc.queryForList(
                "SELECT m.nombre_materia, c.calificacion_final, pe.nombre_periodo AS periodo " +
                "FROM ades_calificaciones_periodo c " +
                "JOIN ades_materias m ON m.id = c.materia_id " +
                "JOIN ades_periodos_evaluacion pe ON pe.id = c.periodo_evaluacion_id " +
                "WHERE c.estudiante_id = ? AND c.is_active = TRUE " +
                "ORDER BY m.nombre_materia, pe.numero_periodo",
                alumnoId
        );

        List<Map<String, Object>> asist = jdbc.queryForList(
                "SELECT COUNT(*) AS total, " +
                "COALESCE(SUM(CASE WHEN estatus_asistencia = 'PRESENTE' THEN 1 ELSE 0 END), 0) AS presentes, " +
                "COALESCE(SUM(CASE WHEN estatus_asistencia = 'AUSENTE' THEN 1 ELSE 0 END), 0) AS faltas, " +
                "COALESCE(SUM(CASE WHEN estatus_asistencia = 'TARDE' THEN 1 ELSE 0 END), 0) AS tardanzas " +
                "FROM ades_asistencias " +
                "WHERE estudiante_id = ? AND is_active = TRUE",
                alumnoId
        );

        List<Map<String, Object>> cond = jdbc.queryForList(
                "SELECT COUNT(*) AS total_incidentes " +
                "FROM ades_incidentes_conducta " +
                "WHERE estudiante_id = ? AND is_active = TRUE",
                alumnoId
        );

        Map<String, Object> response = new HashMap<>();
        response.put("calificaciones", calif);
        response.put("asistencias", asist.isEmpty() ? Collections.emptyMap() : asist.get(0));
        response.put("conducta", cond.isEmpty() ? Map.of("total_incidentes", 0) : cond.get(0));

        return ResponseEntity.ok(response);
    }
}
