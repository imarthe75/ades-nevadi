package mx.ades.modules.portal_familias;

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
        return ResponseEntity.ok(queryService.listarTutores(alumnoId));
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

        appService.desvincular(tutorAlumnoId, user.getId().toString());
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
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.resumenAcademico(alumnoId));
    }
}
