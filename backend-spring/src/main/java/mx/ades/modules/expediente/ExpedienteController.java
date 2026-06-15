package mx.ades.modules.expediente;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ExpedienteController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;
    private final PaperlessService paperlessSvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.base-url:https://integrate.api.nvidia.com/v1}")
    private String openaiBaseUrl;

    @Value("${openai.api-key:}")
    private String openaiApiKey;

    @Value("${openai.model:meta/llama-3-71b-instruct}")
    private String openaiModel;

    private static final List<String> TIPOS_REQUERIDOS = Arrays.asList(
            "CURP", "ACTA_NACIMIENTO", "CERTIFICADO_PREV", "COMPROBANTE_DOMICILIO", "FOTOGRAFIA"
    );

    private static final Map<String, String> LABEL_TIPO;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("CURP", "CURP");
        m.put("ACTA_NACIMIENTO", "Acta de Nacimiento");
        m.put("CERTIFICADO_PREV", "Certificado de Nivel Previo");
        m.put("COMPROBANTE_DOMICILIO", "Comprobante de Domicilio");
        m.put("FOTOGRAFIA", "Fotografía");
        m.put("NSS", "Número de Seguro Social");
        m.put("CREDENCIAL_ESCOLAR", "Credencial Escolar");
        m.put("CONSTANCIA_INSCRIPCION", "Constancia de Inscripción");
        m.put("OTRO", "Otro");
        LABEL_TIPO = Collections.unmodifiableMap(m);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SCHEMAS FOR BAJAS
    // ──────────────────────────────────────────────────────────────────────────
    @Data
    public static class BajaCreate {
        private String tipoBaja; // TEMPORAL | DEFINITIVA | TRASLADO | DESERCION
        private String motivo;
        private LocalDate fechaEfectiva;
        private LocalDate fechaReingreso;
        private String plantelDestino;
        private String claveCtDestino;
        private String observaciones;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SCHEMAS FOR EXTRAORDINARIOS
    // ──────────────────────────────────────────────────────────────────────────
    @Data
    public static class ExtraordinarioCreate {
        private UUID materiaId;
        private UUID cicloEscolarId;
        private UUID grupoId;
        private String tipoExamen = "EXTRAORDINARIO";
        private Double calificacionPrevia;
        private LocalDate fechaExamen;
        private Double calificacion;
        private Boolean acredita;
        private String observaciones;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SCHEMAS FOR CONSTANCIAS
    // ──────────────────────────────────────────────────────────────────────────
    @Data
    public static class ConstanciaCreate {
        private String tipoConstancia;
        private UUID cicloEscolarId;
        private String solicitadaPor;
        private String proposito;
        private LocalDate fechaVencimiento;
        private String observaciones;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // BAJAS ENDPOINTS
    // ──────────────────────────────────────────────────────────────────────────
    @GetMapping("/bajas")
    public ResponseEntity<List<Map<String, Object>>> listarBajas(
            @RequestParam("estudiante_id") UUID estudianteId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        String sql = "SELECT * FROM ades_bajas WHERE estudiante_id = ? AND is_active = TRUE ORDER BY fecha_efectiva DESC";
        return ResponseEntity.ok(jdbc.queryForList(sql, estudianteId));
    }

    @PostMapping("/bajas")
    public ResponseEntity<Map<String, Object>> registrarBaja(
            @RequestParam("estudiante_id") UUID estudianteId,
            @RequestBody BajaCreate body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        UUID newId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_bajas " +
                "(id, estudiante_id, tipo_baja, motivo, fecha_efectiva, fecha_reingreso, " +
                " plantel_destino, clave_ct_destino, observaciones, autorizado_por_id, " +
                " usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                newId, estudianteId, body.getTipoBaja(), body.getMotivo(), body.getFechaEfectiva(),
                body.getFechaReingreso(), body.getPlantelDestino(), body.getClaveCtDestino(),
                body.getObservaciones(), user.getId(), user.getId().toString(), user.getId().toString()
        );

        if ("DEFINITIVA".equalsIgnoreCase(body.getTipoBaja()) || "DESERCION".equalsIgnoreCase(body.getTipoBaja())) {
            jdbc.update("UPDATE ades_estudiantes SET is_active = FALSE WHERE id = ?", estudianteId);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", newId);
        resp.put("estudiante_id", estudianteId);
        resp.put("tipo_baja", body.getTipoBaja());
        resp.put("motivo", body.getMotivo());
        resp.put("fecha_efectiva", body.getFechaEfectiva());
        resp.put("fecha_reingreso", body.getFechaReingreso());
        resp.put("plantel_destino", body.getPlantelDestino());
        resp.put("clave_ct_destino", body.getClaveCtDestino());
        resp.put("observaciones", body.getObservaciones());
        resp.put("autorizado_por_id", user.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // EXTRAORDINARIOS ENDPOINTS
    // ──────────────────────────────────────────────────────────────────────────
    @GetMapping("/extraordinarias")
    public ResponseEntity<List<Map<String, Object>>> listarExtraordinarias(
            @RequestParam("estudiante_id") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder("SELECT * FROM ades_extraordinarias WHERE estudiante_id = ? AND is_active = TRUE ");
        List<Object> params = new ArrayList<>();
        params.add(estudianteId);

        if (cicloId != null) {
            sql.append("AND ciclo_escolar_id = ? ");
            params.add(cicloId);
        }

        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }

    @PostMapping("/extraordinarias")
    public ResponseEntity<Map<String, Object>> registrarExtraordinario(
            @RequestParam("estudiante_id") UUID estudianteId,
            @RequestBody ExtraordinarioCreate body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        UUID newId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_extraordinarias " +
                "(id, estudiante_id, materia_id, ciclo_escolar_id, grupo_id, tipo_examen, " +
                " calificacion_previa, fecha_examen, calificacion, acredita, observaciones, aplicado_por_id, " +
                " usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                newId, estudianteId, body.getMateriaId(), body.getCicloEscolarId(), body.getGrupoId(),
                body.getTipoExamen(), body.getCalificacionPrevia(), body.getFechaExamen(), body.getCalificacion(),
                body.getAcredita(), body.getObservaciones(), user.getId(), user.getId().toString(), user.getId().toString()
        );

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", newId);
        resp.put("estudiante_id", estudianteId);
        resp.put("materia_id", body.getMateriaId());
        resp.put("ciclo_escolar_id", body.getCicloEscolarId());
        resp.put("tipo_examen", body.getTipoExamen());
        resp.put("calificacion_previa", body.getCalificacionPrevia());
        resp.put("fecha_examen", body.getFechaExamen());
        resp.put("calificacion", body.getCalificacion());
        resp.put("acredita", body.getAcredita());
        resp.put("observaciones", body.getObservaciones());

        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PatchMapping("/extraordinarias/{extra_id}")
    public ResponseEntity<Map<String, Object>> actualizarExtraordinario(
            @PathVariable("extra_id") UUID extraId,
            @RequestParam("calificacion") Double calificacion,
            @RequestParam("acredita") Boolean acredita,
            @RequestParam(value = "fecha_examen", required = false) LocalDate fechaExamen,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        if (calificacion < 0 || calificacion > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Calificación debe estar entre 0 y 10");
        }

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id FROM ades_extraordinarias WHERE id = ? AND is_active = TRUE", extraId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Extraordinario no encontrado");
        }

        StringBuilder sql = new StringBuilder("UPDATE ades_extraordinarias SET calificacion = ?, acredita = ?, usuario_modificacion = ? ");
        List<Object> params = new ArrayList<>();
        params.add(calificacion);
        params.add(acredita);
        params.add(user.getId().toString());

        if (fechaExamen != null) {
            sql.append(", fecha_examen = ? ");
            params.add(fechaExamen);
        }

        sql.append("WHERE id = ? RETURNING *");
        params.add(extraId);

        List<Map<String, Object>> updated = jdbc.queryForList(sql.toString(), params.toArray());
        return ResponseEntity.ok(updated.get(0));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // CONSTANCIAS ENDPOINTS
    // ──────────────────────────────────────────────────────────────────────────
    @GetMapping("/constancias")
    public ResponseEntity<List<Map<String, Object>>> listarConstancias(
            @RequestParam("estudiante_id") UUID estudianteId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        String sql = "SELECT * FROM ades_constancias WHERE estudiante_id = ? AND is_active = TRUE ORDER BY fecha_emision DESC";
        return ResponseEntity.ok(jdbc.queryForList(sql, estudianteId));
    }

    @PostMapping("/constancias")
    public ResponseEntity<Map<String, Object>> emitirConstancia(
            @RequestParam("estudiante_id") UUID estudianteId,
            @RequestBody ConstanciaCreate body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        int anio = LocalDate.now().getYear();
        String prefix = body.getTipoConstancia().length() >= 3 ? body.getTipoConstancia().substring(0, 3).toUpperCase() : body.getTipoConstancia().toUpperCase();

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_constancias WHERE tipo_constancia = ? AND EXTRACT(YEAR FROM fecha_emision) = ?",
                Integer.class, body.getTipoConstancia(), anio
        );
        int seq = (count != null ? count : 0) + 1;
        String folio = String.format("%s-%d-%04d", prefix, anio, seq);

        UUID newId = UUID.randomUUID();
        LocalDate now = LocalDate.now();
        jdbc.update(
                "INSERT INTO ades_constancias " +
                "(id, estudiante_id, tipo_constancia, folio, ciclo_escolar_id, fecha_emision, fecha_vencimiento, " +
                " solicitada_por, proposito, emitida_por_id, entregada, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, ?, ?)",
                newId, estudianteId, body.getTipoConstancia(), folio, body.getCicloEscolarId(), now,
                body.getFechaVencimiento(), body.getSolicitadaPor(), body.getProposito(), user.getId(),
                user.getId().toString(), user.getId().toString()
        );

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", newId);
        resp.put("estudiante_id", estudianteId);
        resp.put("tipo_constancia", body.getTipoConstancia());
        resp.put("folio", folio);
        resp.put("ciclo_escolar_id", body.getCicloEscolarId());
        resp.put("fecha_emision", now);
        resp.put("fecha_vencimiento", body.getFechaVencimiento());
        resp.put("solicitada_por", body.getSolicitadaPor());
        resp.put("proposito", body.getProposito());
        resp.put("emitida_por_id", user.getId());
        resp.put("entregada", false);

        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PatchMapping("/constancias/{constancia_id}/entregar")
    public ResponseEntity<Map<String, Object>> marcarEntregada(
            @PathVariable("constancia_id") UUID constanciaId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id FROM ades_constancias WHERE id = ? AND is_active = TRUE", constanciaId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Constancia no encontrada");
        }

        jdbc.update(
                "UPDATE ades_constancias SET entregada = TRUE, fecha_entrega = ?, usuario_modificacion = ? WHERE id = ?",
                LocalDate.now(), user.getId().toString(), constanciaId
        );

        List<Map<String, Object>> updated = jdbc.queryForList("SELECT * FROM ades_constancias WHERE id = ?", constanciaId);
        return ResponseEntity.ok(updated.get(0));
    }

    private UUID getCicloActivoId() {
        List<UUID> ids = jdbc.queryForList(
                "SELECT id FROM public.ades_ciclos_escolares WHERE es_vigente = TRUE ORDER BY fecha_inicio DESC LIMIT 1",
                UUID.class
        );
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay ciclo escolar activo configurado.");
        }
        return ids.get(0);
    }

    private Map<String, Object> obtenerOCrearExpediente(UUID estudianteId, UUID cicloId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, estado, completitud_pct, revisado_por, fecha_revision, observaciones, ciclo_escolar_id " +
                "FROM public.ades_expedientes_alumno WHERE estudiante_id = ? AND ciclo_escolar_id = ?",
                estudianteId, cicloId
        );
        if (!rows.isEmpty()) {
            return rows.get(0);
        }

        UUID newId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO public.ades_expedientes_alumno (id, estudiante_id, ciclo_escolar_id, estado, completitud_pct, is_active) " +
                "VALUES (?, ?, ?, 'PENDIENTE', 0.00, TRUE)",
                newId, estudianteId, cicloId
        );

        return jdbc.queryForMap(
                "SELECT id, estado, completitud_pct, revisado_por, fecha_revision, observaciones, ciclo_escolar_id " +
                "FROM public.ades_expedientes_alumno WHERE id = ?",
                newId
        );
    }

    @GetMapping("/expediente/alumno/{estudiante_id}")
    public ResponseEntity<Map<String, Object>> obtenerExpediente(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        UUID cicloRef = (cicloId != null) ? cicloId : getCicloActivoId();
        Map<String, Object> exp = obtenerOCrearExpediente(estudianteId, cicloRef);
        UUID expId = (UUID) exp.get("id");

        List<Map<String, Object>> docsRaw = jdbc.queryForList(
                "SELECT id, expediente_id, paperless_doc_id, tipo_documento, nombre_archivo, estado_ocr, fecha_carga, metadatos_ia " +
                "FROM public.ades_expediente_documentos WHERE expediente_id = ? AND is_active = TRUE ORDER BY fecha_carga DESC",
                expId
        );

        List<Map<String, Object>> documentos = new ArrayList<>();
        Set<String> tiposPresentes = new HashSet<>();
        for (Map<String, Object> d : docsRaw) {
            String tipo = (String) d.get("tipo_documento");
            tiposPresentes.add(tipo);

            Map<String, Object> docOut = new HashMap<>(d);
            docOut.put("tipo_label", LABEL_TIPO.getOrDefault(tipo, tipo));
            docOut.put("fecha_carga", d.get("fecha_carga") != null ? d.get("fecha_carga").toString() : null);
            documentos.add(docOut);
        }

        List<Map<String, Object>> docsRequeridos = new ArrayList<>();
        for (String t : TIPOS_REQUERIDOS) {
            Map<String, Object> req = new HashMap<>();
            req.put("tipo", t);
            req.put("label", LABEL_TIPO.getOrDefault(t, t));
            req.put("presente", tiposPresentes.contains(t));
            docsRequeridos.add(req);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", exp.get("id"));
        response.put("estudiante_id", estudianteId);
        response.put("ciclo_escolar_id", exp.get("ciclo_escolar_id"));
        response.put("estado", exp.get("estado"));
        response.put("completitud_pct", exp.get("completitud_pct"));
        response.put("revisado_por", exp.get("revisado_por"));
        response.put("fecha_revision", exp.get("fecha_revision") != null ? exp.get("fecha_revision").toString() : null);
        response.put("observaciones", exp.get("observaciones"));
        response.put("documentos", documentos);
        response.put("documentos_requeridos", docsRequeridos);

        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/expediente/alumno/{estudiante_id}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> subirDocumento(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(value = "tipo_documento", defaultValue = "OTRO") String tipoDocumento,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        if (!LABEL_TIPO.containsKey(tipoDocumento)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tipo_documento inválido: " + tipoDocumento);
        }

        if (archivo.getSize() > 20 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Archivo demasiado grande (max 20 MB).");
        }

        String mime = archivo.getContentType();
        Set<String> mimePermitidos = Set.of("application/pdf", "image/jpeg", "image/png", "image/tiff", "image/webp");
        if (mime == null || !mimePermitidos.contains(mime)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "MIME no permitido: " + mime);
        }

        UUID cicloRef = (cicloId != null) ? cicloId : getCicloActivoId();
        Map<String, Object> exp = obtenerOCrearExpediente(estudianteId, cicloRef);
        UUID expId = (UUID) exp.get("id");

        String paperlessTaskId = null;
        try {
            if (paperlessSvc.hasToken()) {
                String label = LABEL_TIPO.getOrDefault(tipoDocumento, tipoDocumento);
                String titulo = label + " — " + estudianteId;
                paperlessTaskId = paperlessSvc.subirDocumento(
                        archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : "documento.pdf",
                        archivo.getBytes(),
                        mime,
                        titulo
                );
            }
        } catch (Exception e) {
            log.error("Error al subir archivo a Paperless: {}", e.getMessage());
        }

        UUID nuevoId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO public.ades_expediente_documentos " +
                "(id, expediente_id, paperless_doc_id, tipo_documento, nombre_archivo, estado_ocr, cargado_por, fecha_carga, is_active) " +
                "VALUES (?, ?, NULL, ?, ?, 'PENDIENTE', ?, NOW(), TRUE)",
                nuevoId, expId, tipoDocumento, archivo.getOriginalFilename(), user.getId().toString()
        );

        String mensaje = "Documento registrado. ";
        if (paperlessTaskId != null) {
            mensaje += "OCR Paperless en cola (tarea: " + paperlessTaskId + ").";
        } else {
            mensaje += "Paperless no configurado; OCR pendiente.";
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", nuevoId);
        response.put("paperless_task_id", paperlessTaskId);
        response.put("mensaje", mensaje);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/expediente/alumno/{estudiante_id}/documentos/{doc_id}/preview")
    public ResponseEntity<byte[]> previewDocumento(
            @PathVariable("estudiante_id") UUID estudianteId,
            @PathVariable("doc_id") UUID docId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT paperless_doc_id, nombre_archivo FROM public.ades_expediente_documentos WHERE id = ? AND is_active = TRUE",
                docId
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento no encontrado.");
        }
        Map<String, Object> doc = rows.get(0);
        Integer paperlessDocId = (Integer) doc.get("paperless_doc_id");
        if (paperlessDocId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento aún no procesado por Paperless.");
        }

        byte[] contenido = paperlessSvc.descargarDocumento(paperlessDocId);
        if (contenido == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo obtener el documento de Paperless.");
        }

        String mediaType = "application/pdf";
        String filename = doc.get("nombre_archivo") != null ? doc.get("nombre_archivo").toString().toLowerCase() : "";
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            mediaType = "image/jpeg";
        } else if (filename.endsWith(".png")) {
            mediaType = "image/png";
        } else if (filename.endsWith(".webp")) {
            mediaType = "image/webp";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mediaType))
                .body(contenido);
    }

    @DeleteMapping("/expediente/{expediente_id}/documentos/{doc_id}")
    public ResponseEntity<Void> eliminarDocumento(
            @PathVariable("expediente_id") UUID expedienteId,
            @PathVariable("doc_id") UUID docId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT paperless_doc_id FROM public.ades_expediente_documentos WHERE id = ? AND expediente_id = ? AND is_active = TRUE",
                docId, expedienteId
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento no encontrado.");
        }

        Integer paperlessDocId = (Integer) rows.get(0).get("paperless_doc_id");
        if (paperlessDocId != null) {
            paperlessSvc.eliminarDocumento(paperlessDocId);
        }

        jdbc.update("UPDATE public.ades_expediente_documentos SET is_active = FALSE WHERE id = ?", docId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/expediente/buscar")
    public ResponseEntity<Map<String, Object>> buscarDocumentos(
            @RequestParam(value = "q", defaultValue = "") String q,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "25") int pageSize,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        if (!paperlessSvc.hasToken()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Paperless-ngx no configurado.");
        }

        Map<String, Object> result = paperlessSvc.buscarDocumentos(q, page, pageSize);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/expediente/{expediente_id}/verificar")
    public ResponseEntity<Map<String, Object>> verificarExpediente(
            @PathVariable("expediente_id") UUID expedienteId,
            @RequestParam(value = "observaciones", required = false) String observaciones,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        if (user.getNivelAcceso() > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el Director puede verificar expedientes.");
        }

        List<Map<String, Object>> rows = jdbc.queryForList(
                "UPDATE public.ades_expedientes_alumno " +
                "SET estado = 'VERIFICADO', revisado_por = ?, fecha_revision = NOW(), " +
                "    observaciones = COALESCE(?, observaciones) " +
                "WHERE id = ? RETURNING id, estado",
                user.getId(), observaciones, expedienteId
        );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expediente no encontrado.");
        }

        return ResponseEntity.ok(Map.of(
                "expediente_id", expedienteId,
                "estado", "VERIFICADO",
                "mensaje", "Expediente verificado correctamente."
        ));
    }

    @PostMapping("/expediente/alumno/{estudiante_id}/analizar-ia")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> analizarExpedienteIa(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        UUID cicloRef = (cicloId != null) ? cicloId : getCicloActivoId();

        List<Map<String, Object>> alumnoRows = jdbc.queryForList(
                "SELECT p.nombre, p.apellido_paterno, p.apellido_materno, p.curp, " +
                "       e.matricula, n.nombre_nivel as nivel " +
                "FROM public.ades_estudiantes e " +
                "JOIN public.ades_personas p ON p.id = e.persona_id " +
                "LEFT JOIN public.ades_grupos g ON g.id = e.grupo_id " +
                "LEFT JOIN public.ades_grados gr ON gr.id = g.grado_id " +
                "LEFT JOIN public.ades_niveles_educativos n ON n.id = gr.nivel_educativo_id " +
                "WHERE e.id = ? LIMIT 1",
                estudianteId
        );

        if (alumnoRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado.");
        }
        Map<String, Object> alumno = alumnoRows.get(0);

        Map<String, Object> exp = obtenerOCrearExpediente(estudianteId, cicloRef);
        UUID expId = (UUID) exp.get("id");

        List<Map<String, Object>> docs = jdbc.queryForList(
                "SELECT tipo_documento, ocr_texto FROM public.ades_expediente_documentos WHERE expediente_id = ? AND is_active = TRUE",
                expId
        );

        List<String> tiposPresentes = new ArrayList<>();
        List<String> ocrResumen = new ArrayList<>();
        for (Map<String, Object> d : docs) {
            String tipo = (String) d.get("tipo_documento");
            tiposPresentes.add(tipo);
            String ocrText = (String) d.get("ocr_texto");
            if (ocrText != null && !ocrText.isBlank()) {
                int len = Math.min(ocrText.length(), 300);
                ocrResumen.add("[" + tipo + "]: " + ocrText.substring(0, len));
            }
        }

        List<String> tiposFaltantes = new ArrayList<>();
        for (String t : TIPOS_REQUERIDOS) {
            if (!tiposPresentes.contains(t)) {
                tiposFaltantes.add(t);
            }
        }

        String nombreCompleto = String.format("%s %s %s",
                alumno.get("nombre"),
                alumno.get("apellido_paterno"),
                alumno.get("apellido_materno") != null ? alumno.get("apellido_materno") : ""
        ).trim();

        String prompt = String.format(
                "Eres asistente administrativo escolar del Instituto Nevadi, Mexico.\n\n" +
                "ALUMNO: %s | CURP: %s | Matricula: %s | Nivel: %s\n" +
                "DOCUMENTOS PRESENTES: %s\n" +
                "DOCUMENTOS FALTANTES: %s\n" +
                "EXTRACTOS OCR: %s\n\n" +
                "Responde SOLO con JSON valido:\n" +
                "{\"analisis\": \"...\", \"recomendaciones\": [\"...\", \"...\"], \"alertas\": [\"...\"]}",
                nombreCompleto,
                alumno.get("curp") != null ? alumno.get("curp") : "N/A",
                alumno.get("matricula") != null ? alumno.get("matricula") : "N/A",
                alumno.get("nivel") != null ? alumno.get("nivel") : "N/A",
                String.join(", ", tiposPresentes),
                String.join(", ", tiposFaltantes),
                String.join("\n", ocrResumen)
        );

        String analisisTexto = "NVIDIA NIM no configurado. Configure OPENAI_API_KEY en Vault.";
        List<String> recomendaciones = new ArrayList<>();
        List<String> alertas = new ArrayList<>();

        if (openaiApiKey != null && !openaiApiKey.isBlank()) {
            try {
                RestClient client = RestClient.builder().build();
                Map<String, Object> reqBody = new HashMap<>();
                reqBody.put("model", openaiModel);
                reqBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));
                reqBody.put("temperature", 0.3);
                reqBody.put("max_tokens", 600);

                ResponseEntity<String> response = client.post()
                        .uri(openaiBaseUrl + "/chat/completions")
                        .header("Authorization", "Bearer " + openaiApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(reqBody)
                        .retrieve()
                        .toEntity(String.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Map<String, Object> outerMap = objectMapper.readValue(response.getBody(), Map.class);
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) outerMap.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        String content = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{.*\\}", java.util.regex.Pattern.DOTALL).matcher(content);
                        if (m.find()) {
                            Map<String, Object> iaData = objectMapper.readValue(m.group(), Map.class);
                            analisisTexto = (String) iaData.getOrDefault("analisis", content);
                            recomendaciones = (List<String>) iaData.getOrDefault("recomendaciones", new ArrayList<>());
                            alertas = (List<String>) iaData.getOrDefault("alertas", new ArrayList<>());
                        } else {
                            analisisTexto = content;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error al conectar con NVIDIA NIM: {}", e.getMessage());
                analisisTexto = "Error al conectar con NVIDIA NIM: " + e.getMessage();
            }
        }

        jdbc.update(
                "UPDATE public.ades_expedientes_alumno SET observaciones = ? WHERE id = ?",
                analisisTexto.substring(0, Math.min(analisisTexto.length(), 500)), expId
        );

        Map<String, Object> resp = new HashMap<>();
        resp.put("expediente_id", expId);
        resp.put("completitud_pct", exp.get("completitud_pct"));
        resp.put("documentos_presentes", tiposPresentes);
        resp.put("documentos_faltantes", tiposFaltantes);
        resp.put("analisis", analisisTexto);
        resp.put("recomendaciones", recomendaciones);
        resp.put("alertas", alertas);

        return ResponseEntity.ok(resp);
    }
}
