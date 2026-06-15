package mx.ades.modules.contactos;

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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ContactosController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

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

    // ──────────────────────────────────────────────────────────────────────────
    // CONTACTOS FAMILIARES / TUTORES
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/contactos")
    public ResponseEntity<List<Map<String, Object>>> listarContactos(
            @RequestParam("estudiante_id") UUID estudianteId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        String sql = "SELECT c.id, c.persona_id, c.estudiante_id, c.nombre_completo, c.parentesco, " +
                "c.telefono_principal, c.email, c.es_tutor_legal, c.es_contacto_emergencia, " +
                "c.puede_recoger, c.ocupacion, c.nivel_estudios, c.rfc, c.nacionalidad, " +
                "c.toma_decision_conjunta, c.grado_responsabilidad, c.is_active, c.row_version, " +
                "p.nombre || ' ' || p.apellido_paterno || COALESCE(' ' || p.apellido_materno, '') AS nombre_completo_persona " +
                "FROM ades_contactos_familiares c " +
                "LEFT JOIN ades_personas p ON p.id = c.persona_id " +
                "WHERE c.estudiante_id = ? AND c.is_active = TRUE " +
                "ORDER BY c.es_tutor_legal DESC, c.prioridad";

        List<Map<String, Object>> rows = jdbc.queryForList(sql, estudianteId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> c : rows) {
            Map<String, Object> map = new HashMap<>(c);
            String nombre = (String) c.get("nombre_completo");
            if ((nombre == null || nombre.isBlank()) && c.get("nombre_completo_persona") != null) {
                nombre = (String) c.get("nombre_completo_persona");
            }
            map.put("nombre_completo", nombre != null ? nombre : "");
            result.add(map);
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping("/contactos")
    public ResponseEntity<Map<String, Object>> crearContacto(
            @RequestBody ContactoPayload body,
            @RequestParam(value = "estudiante_id", required = false) UUID estudianteIdParam,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        UUID estId = estudianteIdParam != null ? estudianteIdParam : body.getEstudianteId();
        if (estId == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "estudiante_id es requerido");
        }

        UUID id = UUID.randomUUID();
        String nac = body.getNacionalidad() != null ? body.getNacionalidad() : "Mexicana";
        jdbc.update(
                "INSERT INTO ades_contactos_familiares " +
                "(id, estudiante_id, nombre_completo, parentesco, telefono_principal, email, " +
                "es_tutor_legal, es_contacto_emergencia, puede_recoger, ocupacion, nivel_estudios, rfc, nacionalidad, " +
                "usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, estId, body.getNombreCompleto(), body.getParentesco(), body.getTelefonoPrincipal(), body.getEmail(),
                body.getEsTutorLegal(), body.getEsContactoEmergencia(), body.getPuedeRecoger(), body.getOcupacion(),
                body.getNivelEstudios(), body.getRfc(), nac, user.getUsername(), user.getUsername()
        );

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, persona_id, estudiante_id, nombre_completo, parentesco, telefono_principal, " +
                "email, es_tutor_legal, es_contacto_emergencia, puede_recoger, ocupacion, nivel_estudios, rfc, nacionalidad, is_active, row_version " +
                "FROM ades_contactos_familiares WHERE id = ?", id);

        return ResponseEntity.status(HttpStatus.CREATED).body(rows.get(0));
    }

    @PatchMapping("/contactos/{contacto_id}")
    public ResponseEntity<Map<String, Object>> actualizarContacto(
            @PathVariable("contacto_id") UUID contactoId,
            @RequestBody ContactoPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT row_version, is_active FROM ades_contactos_familiares WHERE id = ?", contactoId);
        if (rows.isEmpty() || !Boolean.TRUE.equals(rows.get(0).get("is_active"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contacto no encontrado");
        }

        int currentVersion = ((Number) rows.get(0).get("row_version")).intValue();
        if (body.getRowVersion() != null && body.getRowVersion() != currentVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conflicto de concurrencia: el registro fue modificado por otro usuario.");
        }

        StringBuilder sql = new StringBuilder("UPDATE ades_contactos_familiares SET row_version = row_version + 1, usuario_modificacion = ?");
        List<Object> params = new ArrayList<>();
        params.add(user.getUsername());

        if (body.getNombreCompleto() != null) {
            sql.append(", nombre_completo = ?");
            params.add(body.getNombreCompleto());
        }
        if (body.getParentesco() != null) {
            sql.append(", parentesco = ?");
            params.add(body.getParentesco());
        }
        if (body.getTelefonoPrincipal() != null) {
            sql.append(", telefono_principal = ?");
            params.add(body.getTelefonoPrincipal());
        }
        if (body.getEmail() != null) {
            sql.append(", email = ?");
            params.add(body.getEmail());
        }
        if (body.getEsTutorLegal() != null) {
            sql.append(", es_tutor_legal = ?");
            params.add(body.getEsTutorLegal());
        }
        if (body.getEsContactoEmergencia() != null) {
            sql.append(", es_contacto_emergencia = ?");
            params.add(body.getEsContactoEmergencia());
        }
        if (body.getPuedeRecoger() != null) {
            sql.append(", puede_recoger = ?");
            params.add(body.getPuedeRecoger());
        }
        if (body.getOcupacion() != null) {
            sql.append(", ocupacion = ?");
            params.add(body.getOcupacion());
        }
        if (body.getNivelEstudios() != null) {
            sql.append(", nivel_estudios = ?");
            params.add(body.getNivelEstudios());
        }
        if (body.getRfc() != null) {
            sql.append(", rfc = ?");
            params.add(body.getRfc());
        }
        if (body.getNacionalidad() != null) {
            sql.append(", nacionalidad = ?");
            params.add(body.getNacionalidad());
        }

        sql.append(" WHERE id = ?");
        params.add(contactoId);

        jdbc.update(sql.toString(), params.toArray());

        List<Map<String, Object>> updated = jdbc.queryForList(
                "SELECT id, persona_id, estudiante_id, nombre_completo, parentesco, telefono_principal, " +
                "email, es_tutor_legal, es_contacto_emergencia, puede_recoger, ocupacion, nivel_estudios, rfc, nacionalidad, is_active, row_version " +
                "FROM ades_contactos_familiares WHERE id = ?", contactoId);

        return ResponseEntity.ok(updated.get(0));
    }

    @DeleteMapping("/contactos/{contacto_id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarContacto(
            @PathVariable("contacto_id") UUID contactoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id FROM ades_contactos_familiares WHERE id = ?", contactoId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contacto no encontrado");
        }

        jdbc.update("UPDATE ades_contactos_familiares SET is_active = FALSE WHERE id = ?", contactoId);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // EXPEDIENTE MÉDICO
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/expediente-medico/{estudiante_id}")
    public ResponseEntity<Map<String, Object>> obtenerExpedienteMedico(
            @PathVariable("estudiante_id") UUID estudianteId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT estudiante_id, tipo_sangre, alergias, medicamentos_autorizados, condiciones_cronicas, " +
                "observaciones_generales, nss, discapacidad, seguro_medico_tipo, seguro_medico_numero, " +
                "vacunas_al_dia, padecimiento_cronico, requiere_medicacion " +
                "FROM ades_expedientes_medicos WHERE estudiante_id = ?", estudianteId);

        if (rows.isEmpty()) {
            // Lazy init
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO ades_expedientes_medicos (id, estudiante_id, vacunas_al_dia, padecimiento_cronico, requiere_medicacion) " +
                    "VALUES (?, ?, TRUE, FALSE, FALSE)", id, estudianteId);
            rows = jdbc.queryForList(
                    "SELECT estudiante_id, tipo_sangre, alergias, medicamentos_autorizados, condiciones_cronicas, " +
                    "observaciones_generales, nss, discapacidad, seguro_medico_tipo, seguro_medico_numero, " +
                    "vacunas_al_dia, padecimiento_cronico, requiere_medicacion " +
                    "FROM ades_expedientes_medicos WHERE estudiante_id = ?", estudianteId);
        }

        return ResponseEntity.ok(rows.get(0));
    }

    @PutMapping("/expediente-medico/{estudiante_id}")
    public ResponseEntity<Map<String, Object>> actualizarExpedienteMedico(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestBody ExpedienteMedicoPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id FROM ades_expedientes_medicos WHERE estudiante_id = ?", estudianteId);
        if (rows.isEmpty()) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO ades_expedientes_medicos (id, estudiante_id, vacunas_al_dia, padecimiento_cronico, requiere_medicacion) " +
                    "VALUES (?, ?, TRUE, FALSE, FALSE)", id, estudianteId);
        }

        jdbc.update(
                "UPDATE ades_expedientes_medicos SET " +
                "tipo_sangre = ?, alergias = ?, medicamentos_autorizados = ?, condiciones_cronicas = ?, " +
                "observaciones_generales = ?, nss = ?, discapacidad = ?, seguro_medico_tipo = ?, " +
                "seguro_medico_numero = ?, vacunas_al_dia = ?, padecimiento_cronico = ?, requiere_medicacion = ?, " +
                "usuario_modificacion = ? WHERE estudiante_id = ?",
                body.getTipoSangre(), body.getAlergias(), body.getMedicamentosAutorizados(), body.getCondicionesCronicas(),
                body.getObservacionesGenerales(), body.getNss(), body.getDiscapacidad(), body.getSeguroMedicoTipo(),
                body.getSeguroMedicoNumero(), body.getVacunasAlDia(), body.getPadecimientoCronico(), body.getRequiereMedicacion(),
                user.getUsername(), estudianteId
        );

        List<Map<String, Object>> updated = jdbc.queryForList(
                "SELECT estudiante_id, tipo_sangre, alergias, medicamentos_autorizados, condiciones_cronicas, " +
                "observaciones_generales, nss, discapacidad, seguro_medico_tipo, seguro_medico_numero, " +
                "vacunas_al_dia, padecimiento_cronico, requiere_medicacion " +
                "FROM ades_expedientes_medicos WHERE estudiante_id = ?", estudianteId);

        return ResponseEntity.ok(updated.get(0));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // EXPEDIENTE DE DOCUMENTOS
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/expediente-docs/{estudiante_id}")
    public ResponseEntity<List<Map<String, Object>>> obtenerExpedienteDocs(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        List<Map<String, Object>> tipos = jdbc.queryForList(
                "SELECT id, nombre_documento, descripcion, obligatorio FROM ades_documentos_tipo WHERE is_active = TRUE ORDER BY orden");

        String docsSql = "SELECT documento_tipo_id, estatus, fecha_entrega, observaciones, id AS doc_id " +
                "FROM ades_expediente_docs WHERE estudiante_id = ? AND is_active = TRUE";
        List<Map<String, Object>> docsList;
        if (cicloId != null) {
            docsSql += " AND ciclo_escolar_id = ?";
            docsList = jdbc.queryForList(docsSql, estudianteId, cicloId);
        } else {
            docsList = jdbc.queryForList(docsSql, estudianteId);
        }

        Map<UUID, Map<String, Object>> docsMap = new HashMap<>();
        for (Map<String, Object> doc : docsList) {
            docsMap.put((UUID) doc.get("documento_tipo_id"), doc);
        }

        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Map<String, Object> tipo : tipos) {
            UUID tipoId = (UUID) tipo.get("id");
            Map<String, Object> doc = docsMap.get(tipoId);

            Map<String, Object> r = new HashMap<>();
            r.put("documento_tipo_id", tipoId);
            r.put("nombre_documento", tipo.get("nombre_documento"));
            r.put("descripcion", tipo.get("descripcion"));
            r.put("obligatorio", tipo.get("obligatorio"));
            r.put("estatus", doc != null ? doc.get("estatus") : "PENDIENTE");
            r.put("fecha_entrega", doc != null && doc.get("fecha_entrega") != null ? doc.get("fecha_entrega").toString() : null);
            r.put("observaciones", doc != null ? doc.get("observaciones") : null);
            r.put("doc_id", doc != null ? doc.get("doc_id") : null);
            resultado.add(r);
        }

        return ResponseEntity.ok(resultado);
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

        String checkSql = "SELECT id FROM ades_expediente_docs WHERE estudiante_id = ? AND documento_tipo_id = ?";
        List<Map<String, Object>> rows;
        if (cicloId != null) {
            checkSql += " AND ciclo_escolar_id = ?";
            rows = jdbc.queryForList(checkSql, estudianteId, docTipoId, cicloId);
        } else {
            rows = jdbc.queryForList(checkSql, estudianteId, docTipoId);
        }

        if (rows.isEmpty()) {
            UUID id = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO ades_expediente_docs " +
                    "(id, estudiante_id, documento_tipo_id, ciclo_escolar_id, estatus, verificado_por_id, observaciones, usuario_creacion, usuario_modificacion) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, estudianteId, docTipoId, cicloId, estatus, user.getId(), observaciones, user.getUsername(), user.getUsername()
            );
        } else {
            UUID id = (UUID) rows.get(0).get("id");
            StringBuilder sql = new StringBuilder("UPDATE ades_expediente_docs SET estatus = ?, observaciones = ?, usuario_modificacion = ?");
            List<Object> params = new ArrayList<>();
            params.add(estatus);
            params.add(observaciones);
            params.add(user.getUsername());

            if ("ENTREGADO".equals(estatus)) {
                sql.append(", fecha_entrega = CURRENT_DATE, verificado_por_id = ?");
                params.add(user.getId());
            }

            sql.append(" WHERE id = ?");
            params.add(id);

            jdbc.update(sql.toString(), params.toArray());
        }

        return ResponseEntity.ok(Map.of("ok", true, "estatus", estatus));
    }
}
