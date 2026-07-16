package mx.ades.modules.alumnos;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.alumnos.Estudiante;
import mx.ades.modules.alumnos.domain.port.in.ActualizarAlumnoUseCase;
import mx.ades.modules.alumnos.domain.port.in.CrearAlumnoUseCase;
import mx.ades.modules.alumnos.domain.port.out.AlumnoRepositoryPort;
import mx.ades.modules.alumnos.query.AlumnoQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST para la gestión de alumnos (estudiantes).
 * Expone endpoints bajo /api/v1/alumnos para listar, consultar, crear y
 * actualizar registros de alumnos. La consulta de lista aplica scoping
 * automático por plantel para usuarios no-administradores. El PATCH
 * implementa optimistic locking mediante rowVersion para evitar conflictos
 * de concurrencia. La creación valida CURP mediante {@code ValidationUtils}.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/alumnos")
@RequiredArgsConstructor
public class AlumnoController {

    private final AdesUserService         userService;
    private final AlumnoQueryService      query;
    private final CrearAlumnoUseCase      crear;
    private final ActualizarAlumnoUseCase actualizar;
    private final AlumnoRepositoryPort    repositoryPort;
    private final RestClient restClient = RestClient.builder().build();

    @Value("${carbone.url:http://ades-carbone:3000}")
    private String carboneUrl;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @RequestParam(name = "nivel_id",   required = false) UUID nivelId,
            @RequestParam(name = "grado_id",   required = false) UUID gradoId,
            @RequestParam(name = "grupo_id",   required = false) UUID grupoId,
            @PageableDefault(size = 20, page = 0) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        // Antes solo se llamaba resolveUser() + getEffectivePlantelId() (que únicamente
        // ACOTA el plantel, sin verificar rol) sin requireStaff: cualquier cuenta
        // autenticada (incluidos padres/alumnos, nivelAcceso >=5) podía listar el
        // expediente (CURP, nombre completo, etc.) de TODOS los alumnos de su plantel
        // (BFLA, OWASP API5 — asimetría con get()/patch()/update()/credencial() de este
        // mismo controlador, que sí exigen requireStaff()).
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        UUID effectivePlantel = userService.getEffectivePlantelId(user, plantelId);
        return ResponseEntity.ok(query.listar(effectivePlantel, nivelId, gradoId, grupoId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        // Antes de este fix no se llamaba a resolveUser(jwt) en absoluto: cualquier
        // cuenta autenticada (incluyendo padres/alumnos, nivelAcceso >=5) podía leer el
        // expediente completo de CUALQUIER alumno del sistema por id, sin scoping por
        // plantel (BOLA, OWASP API1). Se alinea con el mismo criterio ya aplicado en
        // patch()/update() de este controlador: solo personal escolar, acotado a su plantel.
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        Map<String, Object> result = query.obtener(id);
        verificarPlantelDelAlumno(user, (UUID) result.get("plantel_id"));
        return ResponseEntity.ok(result);
    }

    /**
     * No-admins quedan acotados a su propio plantel — evita leer/editar por id el
     * expediente de un alumno de otro plantel (BOLA, OWASP API1), el mismo criterio
     * ya usado en {@code list()} vía {@code getEffectivePlantelId}. (Corregido
     * 2026-07-16 — decisión explícita del usuario: solo ADMIN_GLOBAL exento, ver
     * AdesUserService#getEffectivePlantelId.)
     */
    private void verificarPlantelDelAlumno(AdesUser user, UUID plantelAlumnoId) {
        userService.verificarPlantel(user, plantelAlumnoId, "No puede acceder a un alumno de otro plantel");
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody CrearAlumnoRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        UUID plantelId = req.plantel_id() != null ? req.plantel_id() : user.getPlantelId();
        var cmd = new CrearAlumnoUseCase.Command(
                req.nombre(),
                req.apellido_paterno(),
                req.apellido_materno(),
                req.curp(),
                plantelId,
                user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(crear.crear(cmd));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> patch(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);

        // Scoping por plantel: se necesita el registro actual de todos modos para
        // verificar el plantel (antes solo se cargaba condicionalmente si rowVersion
        // venía en el body, dejando el PATCH sin scoping cuando no se enviaba versión).
        Estudiante current = repositoryPort.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado"));
        verificarPlantelDelAlumno(user, current.getPlantelId());

        // Optimistic locking: si el cliente envía rowVersion, verificar antes de modificar
        Object rv = body.get("rowVersion");
        if (rv != null) {
            Integer clientVersion = rv instanceof Number n ? n.intValue() : null;
            if (clientVersion != null && !clientVersion.equals(current.getRowVersion())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El registro fue modificado. Versión enviada: " + clientVersion +
                    ", actual: " + current.getRowVersion() + ". Recarga y vuelve a intentarlo.");
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> per  = (Map<String, Object>) body.get("persona");
        @SuppressWarnings("unchecked")
        Map<String, Object> comp = (Map<String, Object>) body.get("complementarios");

        // Actualizar en BD
        actualizar.actualizar(new ActualizarAlumnoUseCase.Command(id, per, comp));

        // RE-LEER desde BD para garantizar persistencia
        // Esto asegura que el frontend reciba los datos confirmados por la BD
        Map<String, Object> updated = query.obtener(id);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Estudiante> update(
            @PathVariable UUID id,
            @RequestBody Estudiante update,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        Estudiante est = repositoryPort.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado"));
        verificarPlantelDelAlumno(user, est.getPlantelId());
        // Un no-admin acotado a su plantel tampoco puede reasignar el alumno a OTRO
        // plantel vía este PUT (movería el expediente fuera de su alcance de control).
        if (update.getPlantelId() != null) {
            userService.verificarPlantel(user, update.getPlantelId(), "No puede reasignar el alumno a otro plantel");
        }
        est.setMatricula(update.getMatricula());
        est.setPersonaId(update.getPersonaId());
        est.setPlantelId(update.getPlantelId());
        est.setEstatusId(update.getEstatusId());
        est.setFechaIngreso(update.getFechaIngreso());
        est.setIsActive(update.getIsActive());
        return ResponseEntity.ok(repositoryPort.save(est));
    }

    /**
     * Modificar el expediente de un alumno (datos personales, estatus, plantel) es
     * operación de personal escolar (nivelAcceso &le;4). Padres/alumnos (nivelAcceso
     * &gt;=5) no pueden editar el expediente — previene BFLA (OWASP API5).
     */
    private void requireStaff(AdesUser user) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso == null || nivelAcceso > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
    }

    /**
     * PE-014: credencial de alumno en PDF vía Carbone. El template (diseño gráfico
     * institucional, con formatter de QR sobre la matrícula) se administra desde
     * el módulo de Reportes → Plantillas, mismo flujo ya usado por boletas/actas.
     */
    @GetMapping("/{id}/credencial")
    public ResponseEntity<byte[]> credencial(
            @PathVariable UUID id,
            @RequestParam("template_id") String templateId,
            @AuthenticationPrincipal Jwt jwt) {
        // Antes solo se llamaba resolveUser (autenticación) sin requireStaff ni scoping:
        // cualquier cuenta autenticada podía generar la credencial (con foto, CURP y
        // matrícula — PII sensible) de CUALQUIER alumno del sistema (BOLA, OWASP API1).
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);

        Map<String, Object> a = query.datosCredencial(id);
        verificarPlantelDelAlumno(user, (UUID) a.get("plantel_id"));
        Map<String, Object> payload = new HashMap<>();
        payload.put("matricula", a.get("matricula"));
        payload.put("nombre_completo", (a.get("nombre") + " " + a.get("apellido_paterno") + " " +
                (a.get("apellido_materno") != null ? a.get("apellido_materno") : "")).trim());
        payload.put("curp", a.get("curp"));
        payload.put("foto_url", a.get("foto_url"));
        payload.put("plantel", a.get("nombre_plantel"));
        payload.put("nivel", a.get("nombre_nivel"));
        payload.put("grado", a.get("nombre_grado"));
        payload.put("grupo", a.get("nombre_grupo"));
        payload.put("verificacion", a.get("matricula"));

        try {
            Map<String, Object> reqBody = Map.of("data", payload);
            ResponseEntity<byte[]> response = restClient.post()
                    .uri(carboneUrl + "/render/" + templateId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(reqBody)
                    .retrieve()
                    .toEntity(byte[].class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header("Content-Disposition", "attachment; filename=Credencial_" + a.get("matricula") + ".pdf")
                        .body(response.getBody());
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al generar la credencial");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            // Preserva el status real de Carbone (ej. 404 "Plantilla no encontrada") en
            // vez de colapsarlo siempre a 502 Bad Gateway.
            throw new ResponseStatusException(HttpStatus.valueOf(e.getStatusCode().value()), e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al renderizar con Carbone: " + e.getMessage());
        }
    }
}
