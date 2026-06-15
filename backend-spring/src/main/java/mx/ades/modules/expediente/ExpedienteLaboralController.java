package mx.ades.modules.expediente;

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

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/expediente-laboral")
@RequiredArgsConstructor
public class ExpedienteLaboralController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    private static final Set<String> CONTRATOS = new HashSet<>(Arrays.asList("INDEFINIDO", "DETERMINADO", "HONORARIOS", "COMISION"));
    private static final Set<String> ESTUDIOS = new HashSet<>(Arrays.asList("LICENCIATURA", "MAESTRIA", "DOCTORADO", "NORMAL_BASICA", "BACHILLERATO", "OTRO"));
    private static final Set<String> DOC_TIPOS = new HashSet<>(Arrays.asList("contrato", "titulo", "cedula", "nss", "identificacion", "acta_nacimiento", "curp_doc", "imss", "otro"));

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

    private void validateCreate(ExpedienteCreate body) {
        if (!CONTRATOS.contains(body.getTipoContrato())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "tipo_contrato inválido.");
        }
        if (body.getNivelEstudios() != null && !ESTUDIOS.contains(body.getNivelEstudios())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "nivel_estudios inválido.");
        }
        if (body.getFechaFinContrato() != null && body.getFechaFinContrato().isBefore(body.getFechaContratacion())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "fecha_fin_contrato debe ser >= fecha_contratacion");
        }
        if (body.getCurp() != null) {
            mx.ades.common.ValidationUtils.validarCURP(body.getCurp());
        }
        if (body.getRfc() != null) {
            mx.ades.common.ValidationUtils.validarRFC(body.getRfc());
        }
    }

    private Map<String, Object> getOr404(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM ades_expediente_laboral WHERE id = ? AND is_active = TRUE", id);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expediente laboral no encontrado");
        }
        return rows.get(0);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listarExpedientes(
            @RequestParam(value = "persona_id", required = false) UUID personaId,
            @RequestParam(value = "tipo_contrato", required = false) String tipoContrato,
            @RequestParam(value = "q", required = false) String q,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder(
            "SELECT el.*, " +
            "  CONCAT(pe.nombre, ' ', pe.apellido_paterno, CASE WHEN pe.apellido_materno IS NOT NULL THEN CONCAT(' ', pe.apellido_materno) ELSE '' END) AS nombre_completo, " +
            "  pe.nombre AS nombre_persona, pe.apellido_paterno, pe.apellido_materno " +
            "FROM ades_expediente_laboral el " +
            "LEFT JOIN ades_personas pe ON pe.id = el.persona_id " +
            "WHERE el.is_active = TRUE ");
        List<Object> params = new ArrayList<>();

        if (personaId != null) {
            sql.append("AND el.persona_id = ? ");
            params.add(personaId);
        }
        if (q != null && !q.isBlank()) {
            sql.append("AND (pe.nombre ILIKE ? OR pe.apellido_paterno ILIKE ? OR CONCAT(pe.nombre,' ',pe.apellido_paterno) ILIKE ?) ");
            String like = "%" + q.trim() + "%";
            params.add(like); params.add(like); params.add(like);
        }
        if (tipoContrato != null && !tipoContrato.isBlank()) {
            sql.append("AND el.tipo_contrato = ? ");
            params.add(tipoContrato);
        }

        sql.append("ORDER BY el.fecha_creacion DESC");

        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crearExpediente(
            @RequestBody ExpedienteCreate body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        if (user.getNivelAcceso() > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo RH o Dirección puede crear expedientes laborales");
        }

        validateCreate(body);

        UUID newId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_expediente_laboral " +
                "(id, persona_id, tipo_contrato, fecha_contratacion, fecha_fin_contrato, " +
                " salario_mensual, imss_numero, infonavit_numero, curp, rfc, " +
                " cedula_profesional, nivel_estudios, especialidad, institucion_formacion, " +
                " clave_ct, clave_issste, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                newId, body.getPersonaId(), body.getTipoContrato(), body.getFechaContratacion(),
                body.getFechaFinContrato(), body.getSalarioMensual(), body.getImssNumero(),
                body.getInfonavitNumero(), body.getCurp(), body.getRfc(), body.getCedulaProfesional(),
                body.getNivelEstudios(), body.getEspecialidad(), body.getInstitucionFormacion(),
                body.getClaveCt(), body.getClaveIssste(), user.getId().toString(), user.getId().toString()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(getOr404(newId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detalleExpediente(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(getOr404(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizarExpediente(
            @PathVariable("id") UUID id,
            @RequestBody ExpedientePatch body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        if (user.getNivelAcceso() > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo RH o Dirección puede editar expedientes");
        }

        getOr404(id);

        StringBuilder sql = new StringBuilder("UPDATE ades_expediente_laboral SET usuario_modificacion = ?, row_version = row_version + 1 ");
        List<Object> params = new ArrayList<>();
        params.add(user.getId().toString());

        if (body.getTipoContrato() != null) {
            if (!CONTRATOS.contains(body.getTipoContrato())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "tipo_contrato inválido.");
            }
            sql.append(", tipo_contrato = ? ");
            params.add(body.getTipoContrato());
        }
        if (body.getFechaFinContrato() != null) {
            sql.append(", fecha_fin_contrato = ? ");
            params.add(body.getFechaFinContrato());
        }
        if (body.getSalarioMensual() != null) {
            sql.append(", salario_mensual = ? ");
            params.add(body.getSalarioMensual());
        }
        if (body.getImssNumero() != null) {
            sql.append(", imss_numero = ? ");
            params.add(body.getImssNumero());
        }
        if (body.getInfonavitNumero() != null) {
            sql.append(", infonavit_numero = ? ");
            params.add(body.getInfonavitNumero());
        }
        if (body.getCurp() != null) {
            mx.ades.common.ValidationUtils.validarCURP(body.getCurp());
            sql.append(", curp = ? ");
            params.add(body.getCurp());
        }
        if (body.getRfc() != null) {
            mx.ades.common.ValidationUtils.validarRFC(body.getRfc());
            sql.append(", rfc = ? ");
            params.add(body.getRfc());
        }
        if (body.getCedulaProfesional() != null) {
            sql.append(", cedula_profesional = ? ");
            params.add(body.getCedulaProfesional());
        }
        if (body.getNivelEstudios() != null) {
            if (!ESTUDIOS.contains(body.getNivelEstudios())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "nivel_estudios inválido.");
            }
            sql.append(", nivel_estudios = ? ");
            params.add(body.getNivelEstudios());
        }
        if (body.getEspecialidad() != null) {
            sql.append(", especialidad = ? ");
            params.add(body.getEspecialidad());
        }
        if (body.getInstitucionFormacion() != null) {
            sql.append(", institucion_formacion = ? ");
            params.add(body.getInstitucionFormacion());
        }
        if (body.getClaveCt() != null) {
            sql.append(", clave_ct = ? ");
            params.add(body.getClaveCt());
        }
        if (body.getClaveIssste() != null) {
            sql.append(", clave_issste = ? ");
            params.add(body.getClaveIssste());
        }

        sql.append("WHERE id = ? RETURNING *");
        params.add(id);

        List<Map<String, Object>> updated = jdbc.queryForList(sql.toString(), params.toArray());
        return ResponseEntity.ok(updated.get(0));
    }

    @PostMapping("/{id}/documento")
    public ResponseEntity<Map<String, Object>> agregarDocumento(
            @PathVariable("id") UUID id,
            @RequestBody DocumentoIn body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        if (!DOC_TIPOS.contains(body.getTipoDocumento())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "tipo_documento inválido.");
        }

        getOr404(id);

        jdbc.update(
                "UPDATE ades_expediente_laboral " +
                "SET documentos_urls = documentos_urls || jsonb_build_object(?, ?), " +
                "usuario_modificacion = ?, row_version = row_version + 1 " +
                "WHERE id = ?",
                body.getTipoDocumento(), body.getUrl(), user.getId().toString(), id
        );

        return ResponseEntity.ok(getOr404(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarExpediente(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        if (user.getNivelAcceso() > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo RH o Dirección puede eliminar expedientes");
        }

        getOr404(id);

        jdbc.update(
                "UPDATE ades_expediente_laboral SET is_active = FALSE, usuario_modificacion = ?, row_version = row_version + 1 WHERE id = ?",
                user.getId().toString(), id
        );

        return ResponseEntity.noContent().build();
    }
}
