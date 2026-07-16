package mx.ades.modules.expediente_laboral;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.common.ValidationUtils;
import mx.ades.modules.expediente_laboral.application.service.ExpedienteLaboralApplicationService;
import mx.ades.modules.expediente_laboral.domain.model.TipoContrato;
import mx.ades.modules.expediente_laboral.domain.model.NivelEstudios;
import mx.ades.modules.expediente_laboral.domain.port.in.ActualizarExpedienteLaboralUseCase;
import mx.ades.modules.expediente_laboral.domain.port.in.AgregarDocumentoLaboralUseCase;
import mx.ades.modules.expediente_laboral.domain.port.in.CrearExpedienteLaboralUseCase;
import mx.ades.modules.expediente_laboral.query.ExpedienteLaboralQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

/**
 * Adaptador REST para la gestión del expediente laboral del personal.
 * Expone endpoints bajo /api/v1/expediente-laboral para crear, consultar, actualizar
 * y eliminar expedientes laborales de docentes y personal administrativo. Incluye
 * datos contractuales (TipoContrato), datos previsionales (IMSS, INFONAVIT, ISSSTE),
 * formación académica (NivelEstudios, cédula profesional) y documentos digitales
 * adjuntos. Valida CURP y RFC con {@code ValidationUtils}. Las operaciones de
 * creación y eliminación restringen acceso a RH (verificado en use cases).
 * Aplica scoping por nivelAcceso del usuario autenticado.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/expediente-laboral")
@RequiredArgsConstructor
public class ExpedienteLaboralController {

    private final AdesUserService userService;
    private final CrearExpedienteLaboralUseCase crearExpedienteLaboralUseCase;
    private final ActualizarExpedienteLaboralUseCase actualizarExpedienteLaboralUseCase;
    private final AgregarDocumentoLaboralUseCase agregarDocumentoLaboralUseCase;
    private final ExpedienteLaboralApplicationService expedienteService;
    private final ExpedienteLaboralQueryService queryService;

    @Data
    public static class ExpedienteCreate {
        private UUID personaId;
        private String tipoContrato = "INDEFINIDO";
        private LocalDate fechaContratacion;
        private LocalDate fechaFinContrato;
        private Double salarioMensual = 0.0;
        private String imssNumero;
        private String infonavitNumero;
        private String curp;
        private String rfc;
        private String cedulaProfesional;
        private String nivelEstudios;
        private String especialidad;
        private String institucionFormacion;
        private String claveCt;
        private String claveIssste;
    }

    @Data
    public static class ExpedientePatch {
        private String tipoContrato;
        private LocalDate fechaFinContrato;
        private Double salarioMensual;
        private String imssNumero;
        private String infonavitNumero;
        private String curp;
        private String rfc;
        private String cedulaProfesional;
        private String nivelEstudios;
        private String especialidad;
        private String institucionFormacion;
        private String claveCt;
        private String claveIssste;
    }

    @Data
    public static class DocumentoIn {
        private String tipoDocumento;
        private String url;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listarExpedientes(
            @RequestParam(value = "persona_id", required = false) UUID personaId,
            @RequestParam(value = "tipo_contrato", required = false) String tipoContrato,
            @RequestParam(value = "q", required = false) String q,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // BFLA fix (asimetría): crear/editar/eliminar expedientes laborales ya exigen
        // nivelAcceso<=2 (RH/Dirección) en el use case ("Solo RH..."); esta lectura exponía
        // datos de personal altamente sensibles (CURP, RFC, salario, IMSS, INFONAVIT) de
        // CUALQUIER empleado a cualquier cuenta autenticada, sin restricción alguna.
        requireRH(user);
        return ResponseEntity.ok(queryService.listar(personaId, tipoContrato, q));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crearExpediente(
            @RequestBody ExpedienteCreate body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        if (body.getCurp() != null) { try { ValidationUtils.validarCURP(body.getCurp()); } catch (Exception e) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()); } }
        if (body.getRfc() != null) { try { ValidationUtils.validarRFC(body.getRfc()); } catch (Exception e) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()); } }
        if (body.getNivelEstudios() != null) { try { NivelEstudios.of(body.getNivelEstudios()); } catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()); } }
        try { TipoContrato.of(body.getTipoContrato()); } catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()); }

        CrearExpedienteLaboralUseCase.Command cmd;
        try {
            cmd = new CrearExpedienteLaboralUseCase.Command(
                    body.getPersonaId(), body.getTipoContrato(), body.getFechaContratacion(),
                    body.getFechaFinContrato(), body.getSalarioMensual(), body.getImssNumero(),
                    body.getInfonavitNumero(), body.getCurp(), body.getRfc(), body.getCedulaProfesional(),
                    body.getNivelEstudios(), body.getEspecialidad(), body.getInstitucionFormacion(),
                    body.getClaveCt(), body.getClaveIssste(), user.getId().toString(), user.getNivelAcceso());
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Solo RH")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(crearExpedienteLaboralUseCase.crear(cmd));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detalleExpediente(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // BFLA fix (asimetría): mismo hallazgo que listarExpedientes() — sin restricción,
        // exponía CURP/RFC/salario/IMSS/INFONAVIT de un empleado por solo conocer el id.
        requireRH(user);
        try {
            return ResponseEntity.ok(queryService.detalle(id));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizarExpediente(
            @PathVariable("id") UUID id,
            @RequestBody ExpedientePatch body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        if (body.getCurp() != null) { try { ValidationUtils.validarCURP(body.getCurp()); } catch (Exception e) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()); } }
        if (body.getRfc() != null) { try { ValidationUtils.validarRFC(body.getRfc()); } catch (Exception e) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()); } }
        if (body.getNivelEstudios() != null) { try { NivelEstudios.of(body.getNivelEstudios()); } catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()); } }
        if (body.getTipoContrato() != null) { try { TipoContrato.of(body.getTipoContrato()); } catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()); } }

        ActualizarExpedienteLaboralUseCase.Patch patch = new ActualizarExpedienteLaboralUseCase.Patch(
                body.getTipoContrato(), body.getFechaFinContrato(), body.getSalarioMensual(),
                body.getImssNumero(), body.getInfonavitNumero(), body.getCurp(), body.getRfc(),
                body.getCedulaProfesional(), body.getNivelEstudios(), body.getEspecialidad(),
                body.getInstitucionFormacion(), body.getClaveCt(), body.getClaveIssste());

        ActualizarExpedienteLaboralUseCase.Command cmd;
        try {
            cmd = new ActualizarExpedienteLaboralUseCase.Command(id, patch, user.getId().toString(), user.getNivelAcceso());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }

        try {
            return ResponseEntity.ok(actualizarExpedienteLaboralUseCase.actualizar(cmd));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/{id}/documento")
    public ResponseEntity<Map<String, Object>> agregarDocumento(
            @PathVariable("id") UUID id,
            @RequestBody DocumentoIn body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // BFLA fix (asimetría): mismo hallazgo que listarExpedientes() — adjuntar documentos
        // a un expediente laboral ajeno no tenía ninguna restricción de nivel de acceso.
        requireRH(user);

        AgregarDocumentoLaboralUseCase.Command cmd;
        try {
            cmd = new AgregarDocumentoLaboralUseCase.Command(id, body.getTipoDocumento(), body.getUrl(), user.getId().toString());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }

        try {
            return ResponseEntity.ok(agregarDocumentoLaboralUseCase.agregar(cmd));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarExpediente(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        try {
            expedienteService.eliminar(id, user.getNivelAcceso(), user.getId().toString());
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if (msg.contains("Solo RH")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, msg);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, msg);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Datos de expediente laboral (CURP, RFC, salario, IMSS, INFONAVIT) son de personal,
     * altamente sensibles. Mismo umbral (nivelAcceso &le; 2, RH/Dirección) que ya exigen
     * los use cases de creación/actualización ("Solo RH...").
     */
    private void requireRH(AdesUser user) {
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo RH o Dirección puede acceder a expedientes laborales");
        }
    }
}
