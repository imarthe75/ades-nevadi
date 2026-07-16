package mx.ades.modules.portal_familias;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.portal_familias.domain.port.in.AgregarTutorUseCase;
import mx.ades.modules.portal_familias.application.service.PortalFamiliasApplicationService;
import mx.ades.modules.portal_familias.query.PortalFamiliasQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.util.*;

/**
 * Adaptador REST para el portal de familias (tutores y padres de familia).
 * Expone endpoints bajo /api/v1/portal-familias para listar y agregar tutores de un alumno
 * (requiere nivelAcceso &le;3), desvincular tutores, crear cuentas de usuario para padres
 * (requiere nivelAcceso &le;2), establecer restricciones de acceso al portal por tutor
 * (requiere nivelAcceso &le;2), y que un padre autenticado consulte sus alumnos vinculados
 * y el resumen académico de cada uno. Requiere JWT válido en todos los endpoints.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/portal-familias")
@RequiredArgsConstructor
public class PortalFamiliasController {

    private final AdesUserService userService;
    private final AgregarTutorUseCase agregarTutorUseCase;
    private final PortalFamiliasApplicationService appService;
    private final PortalFamiliasQueryService queryService;

    @Data
    public static class TutorIn {
        @NotNull(message = "personaId es obligatorio")
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
        @NotNull(message = "tutorAlumnoId es obligatorio")
        private UUID tutorAlumnoId;
        @NotBlank(message = "email es obligatorio")
        @Email(message = "email debe tener un formato válido")
        private String email;
        @NotBlank(message = "nombreCompleto es obligatorio")
        @Size(max = 255, message = "nombreCompleto máximo 255 caracteres")
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
        AdesUser user = userService.resolveUser(jwt);
        verificarAccesoAlumno(user, alumnoId);
        return ResponseEntity.ok(queryService.listarTutores(alumnoId));
    }

    @PostMapping("/tutores/{alumno_id}")
    public ResponseEntity<Map<String, Object>> agregarTutor(
            @PathVariable("alumno_id") UUID alumnoId,
            @RequestBody @Valid TutorIn body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        if (user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere Coordinador o superior");
        }
        verificarPlantelAlumno(user, alumnoId);

        try {
            var cmd = new AgregarTutorUseCase.Command(
                alumnoId, body.getPersonaId(), body.getRelacion(), body.getPrioridad(),
                body.getPuedeRecoger(), body.getEsResponsableEconomico(), body.getEsContactoEmergencia(),
                body.getNivelAccesoPortal(), user.getId().toString()
            );
            Map<String, Object> result = agregarTutorUseCase.agregar(cmd);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @DeleteMapping("/tutores/{tutor_alumno_id}")
    public ResponseEntity<Map<String, Object>> desvincularTutor(
            @PathVariable("tutor_alumno_id") UUID tutorAlumnoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        if (user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere Coordinador o superior");
        }
        verificarPlantelTutorAlumno(user, tutorAlumnoId);

        appService.desvincular(tutorAlumnoId, user.getId().toString());
        return ResponseEntity.ok(Map.of("message", "Tutor desvinculado"));
    }

    @PostMapping("/crear-usuario")
    public ResponseEntity<Map<String, Object>> crearUsuarioPadre(
            @RequestBody @Valid CrearUsuarioPadreIn body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        if (user.getNivelAcceso() > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere Admin Plantel o superior");
        }
        verificarPlantelTutorAlumno(user, body.getTutorAlumnoId());

        try {
            appService.crearUsuario(body.getTutorAlumnoId(), body.getEmail(),
                body.getNombreCompleto(), user.getId().toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Solicitud de creación de usuario encolada. Se enviará invitación al correo.",
                "email", body.getEmail()
            ));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
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
        verificarPlantelTutorAlumno(user, tutorAlumnoId);

        Map<String, Object> restricciones = new HashMap<>();
        restricciones.put("puede_ver_calificaciones", body.getPuedeVerCalificaciones());
        restricciones.put("puede_ver_asistencias", body.getPuedeVerAsistencias());
        restricciones.put("puede_ver_conducta", body.getPuedeVerConducta());
        restricciones.put("puede_ver_tareas", body.getPuedeVerTareas());
        restricciones.put("puede_descargar_documentos", body.getPuedeDescargarDocumentos());
        restricciones.put("puede_comunicarse_docentes", body.getPuedeComunicarseDocentes());
        restricciones.put("razon_restriccion", body.getRazonRestriccion());

        appService.establecerRestriccion(tutorAlumnoId, restricciones, user.getId().toString());
        return ResponseEntity.ok(Map.of("message", "Restricciones de acceso actualizadas"));
    }

    @GetMapping("/mis-alumnos")
    public ResponseEntity<List<Map<String, Object>>> misAlumnos(
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        String email = user.getEmail() != null ? user.getEmail() : "";
        return ResponseEntity.ok(queryService.misAlumnos(email));
    }

    @GetMapping("/resumen/{alumno_id}")
    public ResponseEntity<Map<String, Object>> resumenAcademico(
            @PathVariable("alumno_id") UUID alumnoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        verificarAccesoAlumno(user, alumnoId);
        return ResponseEntity.ok(queryService.resumenAcademico(alumnoId));
    }

    /**
     * Personal escolar (nivelAcceso &le;4: admin/director/coordinador/docente) puede consultar
     * cualquier alumno de su plantel. Padres/alumnos (nivelAcceso &gt;=5) solo pueden consultar
     * alumnos donde son tutor activo — previene IDOR (OWASP API1 BOLA) sobre datos de menores.
     */
    private void verificarAccesoAlumno(AdesUser user, UUID alumnoId) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso != null && nivelAcceso <= 4) {
            verificarPlantelAlumno(user, alumnoId);
            return;
        }
        String email = user.getEmail();
        if (email == null || !queryService.esTutorDeAlumno(email, alumnoId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a este alumno");
        }
    }

    /**
     * BOLA cross-plantel: un Director/Coordinador (nivelAcceso 1-4) solo debe gestionar
     * tutores/alumnos de SU plantel, no de cualquier plantel del sistema. Solo ADMIN_GLOBAL
     * (nivelAcceso 0) queda exento del scoping.
     */
    private void verificarPlantelAlumno(AdesUser user, UUID alumnoId) {
        UUID plantelAlumno = queryService.plantelIdDeAlumno(alumnoId);
        if (plantelAlumno == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado");
        }
        userService.verificarPlantel(user, plantelAlumno, "El alumno no pertenece a su plantel");
    }

    /** Igual que {@link #verificarPlantelAlumno} pero resolviendo desde el id de la relación tutor-alumno. */
    private void verificarPlantelTutorAlumno(AdesUser user, UUID tutorAlumnoId) {
        UUID plantelAlumno = queryService.plantelIdPorTutorAlumno(tutorAlumnoId);
        if (plantelAlumno == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Relación tutor-alumno no encontrada");
        }
        userService.verificarPlantel(user, plantelAlumno, "El alumno no pertenece a su plantel");
    }
}
