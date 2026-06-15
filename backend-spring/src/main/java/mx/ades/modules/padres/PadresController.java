package mx.ades.modules.padres;

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
@RequestMapping("/api/v1/padres")
@RequiredArgsConstructor
public class PadresController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    @GetMapping("/mis-alumnos")
    public ResponseEntity<List<Map<String, Object>>> misAlumnos(
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getPersonaId() == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        String sql = "SELECT cf.estudiante_id, " +
                "p.nombre || ' ' || p.apellido_paterno || COALESCE(' ' || p.apellido_materno, '') AS nombre_completo, " +
                "e.matricula, " +
                "COALESCE(ne.nombre_nivel, '—') AS nivel, " +
                "COALESCE(gr.nombre_grado, '—') AS grado, " +
                "COALESCE(g.nombre_grupo, '—') AS grupo, " +
                "COALESCE(pl.nombre_plantel, '—') AS plantel, " +
                "cf.parentesco, " +
                "cf.es_tutor_legal " +
                "FROM ades_contactos_familiares cf " +
                "JOIN ades_estudiantes e ON e.id = cf.estudiante_id " +
                "JOIN ades_personas p ON p.id = e.persona_id " +
                "LEFT JOIN ades_inscripciones ins ON ins.estudiante_id = e.id AND ins.is_active = TRUE " +
                "LEFT JOIN ades_grupos g ON g.id = ins.grupo_id " +
                "LEFT JOIN ades_grados gr ON gr.id = g.grado_id " +
                "LEFT JOIN ades_planteles pl ON pl.id = gr.plantel_id " +
                "LEFT JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id " +
                "WHERE cf.persona_id = ? AND cf.is_active = TRUE AND e.is_active = TRUE";

        return ResponseEntity.ok(jdbc.queryForList(sql, user.getPersonaId()));
    }

    @GetMapping("/calificaciones/{estudiante_id}")
    public ResponseEntity<List<Map<String, Object>>> calificacionesAlumno(
            @PathVariable("estudiante_id") UUID estudianteId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        // Verificar que el tutor está vinculado a este alumno (o nivel_acceso <= 3)
        if (user.getPersonaId() != null && user.getNivelAcceso() > 3) {
            List<Map<String, Object>> cf = jdbc.queryForList(
                    "SELECT id FROM ades_contactos_familiares " +
                    "WHERE persona_id = ? AND estudiante_id = ? AND is_active = TRUE",
                    user.getPersonaId(), estudianteId
            );
            if (cf.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin acceso a este alumno");
            }
        }

        String sql = "SELECT m.nombre_materia AS materia, " +
                "pe.nombre_periodo AS periodo, " +
                "cp.calificacion_final AS calificacion, " +
                "cp.es_acreditado " +
                "FROM ades_calificaciones_periodo cp " +
                "JOIN ades_materias m ON m.id = cp.materia_id " +
                "JOIN ades_periodos_evaluacion pe ON pe.id = cp.periodo_evaluacion_id " +
                "WHERE cp.estudiante_id = ? AND cp.is_active = TRUE " +
                "ORDER BY pe.fecha_inicio, m.nombre_materia";

        return ResponseEntity.ok(jdbc.queryForList(sql, estudianteId));
    }
}
