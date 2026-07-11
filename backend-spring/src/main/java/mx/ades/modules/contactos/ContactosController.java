package mx.ades.modules.contactos;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.contactos.application.service.ContactosApplicationService;
import mx.ades.modules.contactos.domain.port.in.ActualizarContactoUseCase;
import mx.ades.modules.contactos.domain.port.in.RegistrarContactoUseCase;
import mx.ades.modules.contactos.query.ContactosQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import mx.ades.common.ValidationUtils;

import java.util.*;

/**
 * Adaptador REST para la gestión de contactos familiares y expediente médico de alumnos.
 * Expone endpoints bajo /api/v1/contactos para registrar, actualizar y eliminar
 * contactos (tutores legales, responsables de emergencia) con optimistic locking
 * (rowVersion) para evitar conflictos de concurrencia. También gestiona el
 * expediente médico en /api/v1/expediente-medico/{estudiante_id} (tipo de sangre,
 * alergias, medicamentos, discapacidad, seguro médico) y el seguimiento de
 * documentos del expediente escolar en /api/v1/expediente-docs. Los datos de
 * expediente médico son PII sensibles bajo LFPDPPP. Toda operación requiere JWT válido.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ContactosController {

    private final AdesUserService userService;
    private final RegistrarContactoUseCase registrarContactoUseCase;
    private final ActualizarContactoUseCase actualizarContactoUseCase;
    private final ContactosApplicationService contactosService;
    private final ContactosQueryService queryService;

    @Data
    public static class ContactoPayload {
        private UUID estudianteId;
        private String nombreCompleto;
        private String parentesco;
        private String telefonoPrincipal;
        private String email;
        private Boolean esTutorLegal = false;
        private Boolean esContactoEmergencia = false;
        private Boolean puedeRecoger = true;
        private String ocupacion;
        private String nivelEstudios;
        private String rfc;
        private String nacionalidad;
        private Integer rowVersion;
    }

    @Data
    public static class ExpedienteMedicoPayload {
        private String tipoSangre;
        private String alergias;
        private String medicamentosAutorizados;
        private String condicionesCronicas;
        private String observacionesGenerales;
        private String nss;
        private String discapacidad;
        private String seguroMedicoTipo;
        private String seguroMedicoNumero;
        private Boolean vacunasAlDia = true;
        private Boolean padecimientoCronico = false;
        private Boolean requiereMedicacion = false;
    }

    @GetMapping("/contactos")
    public ResponseEntity<List<Map<String, Object>>> listarContactos(
            @RequestParam("estudiante_id") UUID estudianteId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.listarContactos(estudianteId));
    }

    @PostMapping("/contactos")
    public ResponseEntity<Map<String, Object>> crearContacto(
            @RequestBody ContactoPayload body,
            @RequestParam(value = "estudiante_id", required = false) UUID estudianteIdParam,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        UUID estId = estudianteIdParam != null ? estudianteIdParam : body.getEstudianteId();

        ValidationUtils.validarNombrePersona(body.getNombreCompleto(), "El nombre del contacto");
        ValidationUtils.validarTelefono(body.getTelefonoPrincipal());
        ValidationUtils.validarRFCSiPresente(body.getRfc());
        if (body.getEmail() != null && !body.getEmail().isBlank()) {
            ValidationUtils.validarEmail(body.getEmail());
            if (queryService.existeEmailContacto(body.getEmail())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El email ya está registrado en otro contacto");
            }
        }

        try {
            var cmd = new RegistrarContactoUseCase.Command(
                estId, body.getNombreCompleto(), body.getParentesco(),
                body.getTelefonoPrincipal(), body.getEmail(),
                body.getEsTutorLegal(), body.getEsContactoEmergencia(), body.getPuedeRecoger(),
                body.getOcupacion(), body.getNivelEstudios(), body.getRfc(), body.getNacionalidad(),
                user.getUsername()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(registrarContactoUseCase.registrar(cmd));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    @PatchMapping("/contactos/{contacto_id}")
    public ResponseEntity<Map<String, Object>> actualizarContacto(
            @PathVariable("contacto_id") UUID contactoId,
            @RequestBody ContactoPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        ValidationUtils.validarNombrePersona(body.getNombreCompleto(), "El nombre del contacto");
        ValidationUtils.validarTelefono(body.getTelefonoPrincipal());
        ValidationUtils.validarRFCSiPresente(body.getRfc());
        if (body.getEmail() != null && !body.getEmail().isBlank()) {
            ValidationUtils.validarEmail(body.getEmail());
            if (queryService.existeEmailContactoExcepto(body.getEmail(), contactoId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El email ya está registrado en otro contacto");
            }
        }

        try {
            var cmd = new ActualizarContactoUseCase.Command(
                contactoId, body.getNombreCompleto(), body.getParentesco(),
                body.getTelefonoPrincipal(), body.getEmail(),
                body.getEsTutorLegal(), body.getEsContactoEmergencia(), body.getPuedeRecoger(),
                body.getOcupacion(), body.getNivelEstudios(), body.getRfc(), body.getNacionalidad(),
                body.getRowVersion(), user.getUsername()
            );
            return ResponseEntity.ok(actualizarContactoUseCase.actualizar(cmd));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        } catch (IllegalStateException e) {
            String msg = e.getMessage();
            if (msg != null && msg.startsWith("CONFLICT")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Conflicto de concurrencia: el registro fue modificado por otro usuario.");
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, msg);
        }
    }

    @DeleteMapping("/contactos/{contacto_id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarContacto(
            @PathVariable("contacto_id") UUID contactoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        try {
            contactosService.eliminar(contactoId);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @GetMapping("/expediente-medico/{estudiante_id}")
    public ResponseEntity<Map<String, Object>> obtenerExpedienteMedico(
            @PathVariable("estudiante_id") UUID estudianteId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(contactosService.expedienteMedico(estudianteId));
    }

    @PutMapping("/expediente-medico/{estudiante_id}")
    public ResponseEntity<Map<String, Object>> actualizarExpedienteMedico(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestBody ExpedienteMedicoPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        ValidationUtils.validarNSS(body.getNss());
        Map<String, Object> fields = new HashMap<>();
        fields.put("tipo_sangre", body.getTipoSangre());
        fields.put("alergias", body.getAlergias());
        fields.put("medicamentos_autorizados", body.getMedicamentosAutorizados());
        fields.put("condiciones_cronicas", body.getCondicionesCronicas());
        fields.put("observaciones_generales", body.getObservacionesGenerales());
        fields.put("nss", body.getNss());
        fields.put("discapacidad", body.getDiscapacidad());
        fields.put("seguro_medico_tipo", body.getSeguroMedicoTipo());
        fields.put("seguro_medico_numero", body.getSeguroMedicoNumero());
        fields.put("vacunas_al_dia", body.getVacunasAlDia());
        fields.put("padecimiento_cronico", body.getPadecimientoCronico());
        fields.put("requiere_medicacion", body.getRequiereMedicacion());
        return ResponseEntity.ok(contactosService.actualizarExpedienteMedico(estudianteId, fields, user.getUsername()));
    }

    @GetMapping("/expediente-docs/{estudiante_id}")
    public ResponseEntity<List<Map<String, Object>>> obtenerExpedienteDocs(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.listarExpedienteDocs(estudianteId, cicloId));
    }

    @PatchMapping("/expediente-docs/{estudiante_id}/{doc_tipo_id}")
    public ResponseEntity<Map<String, Object>> actualizarDocEstatus(
            @PathVariable("estudiante_id") UUID estudianteId,
            @PathVariable("doc_tipo_id") UUID docTipoId,
            @RequestParam("estatus") String estatus,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @RequestParam(value = "observaciones", required = false) String observaciones,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (!Arrays.asList("PENDIENTE", "ENTREGADO", "INCOMPLETO", "RECHAZADO", "EXENTO").contains(estatus)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Estatus inválido");
        }
        contactosService.upsertDocEstatus(estudianteId, docTipoId, cicloId, estatus, observaciones, user.getUsername(), user.getId());
        return ResponseEntity.ok(Map.of("ok", true, "estatus", estatus));
    }
}
