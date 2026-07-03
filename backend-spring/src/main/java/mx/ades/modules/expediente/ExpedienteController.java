package mx.ades.modules.expediente;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.ades.modules.expediente.domain.model.CalificacionExtra;
import mx.ades.modules.expediente.domain.model.TipoBaja;
import mx.ades.modules.expediente.domain.port.in.*;
import mx.ades.modules.expediente.query.ExpedienteQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ExpedienteController {

    private final AdesUserService userService;
    private final PaperlessService paperlessSvc;
    private final ExpedienteWriteService writeService;
    private final JdbcTemplate jdbc;
    private final RegistrarBajaUseCase registrarBaja;
    private final CalificarExtraordinarioUseCase calificarExtraordinario;
    private final EmitirConstanciaUseCase emitirConstancia;
    private final VerificarExpedienteUseCase verificarExpediente;
    private final ExpedienteQueryService queryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.base-url:https://integrate.api.nvidia.com/v1}")
    private String openaiBaseUrl;

    @Value("${openai.api-key:}")
    private String openaiApiKey;

    @Value("${openai.model:meta/llama-3-71b-instruct}")
    private String openaiModel;

    @Data
    public static class BajaCreate {
        private String tipoBaja;
        private String motivo;
        private LocalDate fechaEfectiva;
        private LocalDate fechaReingreso;
        private String plantelDestino;
        private String claveCtDestino;
        private String observaciones;
    }

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

    @Data
    public static class ConstanciaCreate {
        private String tipoConstancia;
        private UUID cicloEscolarId;
        private String solicitadaPor;
        private String proposito;
        private LocalDate fechaVencimiento;
        private String observaciones;
    }

    // ── Bajas ────────────────────────────────────────────────────────────────

    @GetMapping("/bajas")
    public ResponseEntity<List<Map<String, Object>>> listarBajas(
            @RequestParam("estudiante_id") UUID estudianteId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.listarBajas(estudianteId));
    }

    @PostMapping("/bajas")
    public ResponseEntity<Map<String, Object>> registrarBaja(
            @RequestParam("estudiante_id") UUID estudianteId,
            @RequestBody BajaCreate body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        TipoBaja tipo = TipoBaja.of(body.getTipoBaja());
        RegistrarBajaUseCase.Result result = registrarBaja.ejecutar(
                new RegistrarBajaUseCase.Command(
                        estudianteId, tipo, body.getMotivo(),
                        body.getFechaEfectiva(), body.getFechaReingreso(),
                        body.getPlantelDestino(), body.getClaveCtDestino(),
                        body.getObservaciones(), user.getId(), user.getId().toString()));

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", result.bajaId(),
                "estudiante_id", estudianteId,
                "tipo_baja", tipo.name(),
                "motivo", body.getMotivo() != null ? body.getMotivo() : "",
                "fecha_efectiva", body.getFechaEfectiva(),
                "estudiante_desactivado", result.estudianteDesactivado()));
    }

    // ── Extraordinarios ───────────────────────────────────────────────────────

    @GetMapping("/extraordinarias")
    public ResponseEntity<List<Map<String, Object>>> listarExtraordinarias(
            @RequestParam("estudiante_id") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.listarExtraordinarios(estudianteId, cicloId));
    }

    @PostMapping("/extraordinarias")
    public ResponseEntity<Map<String, Object>> registrarExtraordinario(
            @RequestParam("estudiante_id") UUID estudianteId,
            @RequestBody ExtraordinarioCreate body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        UUID newId = writeService.insertExtraordinario(
            estudianteId, body.getMateriaId(), body.getCicloEscolarId(), body.getGrupoId(),
            body.getTipoExamen(), body.getCalificacionPrevia(), body.getFechaExamen(),
            body.getCalificacion(), body.getAcredita(), body.getObservaciones(),
            user.getId(), user.getId().toString());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", newId,
                "estudiante_id", estudianteId,
                "materia_id", body.getMateriaId() != null ? body.getMateriaId() : "",
                "tipo_examen", body.getTipoExamen()));
    }

    @PatchMapping("/extraordinarias/{extra_id}")
    public ResponseEntity<Map<String, Object>> calificarExtraordinario(
            @PathVariable("extra_id") UUID extraId,
            @RequestParam("calificacion") Double calificacion,
            @RequestParam("acredita") Boolean acredita,
            @RequestParam(value = "fecha_examen", required = false) LocalDate fechaExamen,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        calificarExtraordinario.ejecutar(new CalificarExtraordinarioUseCase.Command(
                extraId, CalificacionExtra.of(calificacion), acredita, fechaExamen,
                user.getId().toString()));

        List<Map<String, Object>> updated = queryService.fetchExtraordinarioById(extraId);
        return ResponseEntity.ok(updated.get(0));
    }

    // ── Constancias ───────────────────────────────────────────────────────────

    @GetMapping("/constancias")
    public ResponseEntity<List<Map<String, Object>>> listarConstancias(
            @RequestParam("estudiante_id") UUID estudianteId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.listarConstancias(estudianteId));
    }

    @PostMapping("/constancias")
    public ResponseEntity<Map<String, Object>> emitirConstancia(
            @RequestParam("estudiante_id") UUID estudianteId,
            @RequestBody ConstanciaCreate body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        EmitirConstanciaUseCase.Result result = emitirConstancia.ejecutar(
                new EmitirConstanciaUseCase.Command(
                        estudianteId, body.getTipoConstancia(), body.getCicloEscolarId(),
                        body.getSolicitadaPor(), body.getProposito(), body.getFechaVencimiento(),
                        body.getObservaciones(), user.getId(), user.getId().toString()));

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", result.constanciaId(),
                "estudiante_id", estudianteId,
                "tipo_constancia", body.getTipoConstancia(),
                "folio", result.folio(),
                "fecha_emision", LocalDate.now()));
    }

    @PatchMapping("/constancias/{constancia_id}/entregar")
    public ResponseEntity<Map<String, Object>> marcarEntregada(
            @PathVariable("constancia_id") UUID constanciaId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        writeService.marcarConstanciaEntregada(constanciaId);
        List<Map<String, Object>> updated = queryService.fetchConstanciaById(constanciaId);
        if (updated.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Constancia no encontrada");
        return ResponseEntity.ok(updated.get(0));
    }

    // ── Expediente documentos ─────────────────────────────────────────────────

    @GetMapping("/expediente/alumno/{estudiante_id}")
    public ResponseEntity<Map<String, Object>> obtenerExpediente(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @RequestParam(value = "lite", required = false, defaultValue = "false") boolean lite,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        UUID cicloRef = cicloId != null ? cicloId : queryService.cicloActivoId();
        return ResponseEntity.ok(lite
                ? queryService.detalleExpedienteLite(estudianteId, cicloRef)
                : queryService.detalleExpediente(estudianteId, cicloRef));
    }

    @PostMapping(value = "/expediente/alumno/{estudiante_id}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> subirDocumento(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(value = "tipo_documento", defaultValue = "OTRO") String tipoDocumento,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) throws Exception {
        AdesUser user = userService.resolveUser(jwt);

        mx.ades.modules.expediente.domain.model.TipoDocumentoExpediente.validarArchivo(
                archivo.getContentType(), archivo.getSize());

        UUID cicloRef = cicloId != null ? cicloId : queryService.cicloActivoId();
        Map<String, Object> exp = queryService.obtenerOCrearExpediente(estudianteId, cicloRef);
        UUID expId = (UUID) exp.get("id");

        String paperlessTaskId = null;
        try {
            if (paperlessSvc.hasToken()) {
                String titulo = queryService.labelTipo(tipoDocumento) + " — " + estudianteId;
                paperlessTaskId = paperlessSvc.subirDocumento(
                        archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : "documento.pdf",
                        archivo.getBytes(), archivo.getContentType(), titulo);
            }
        } catch (Exception e) {
            log.error("Error al subir archivo a Paperless: {}", e.getMessage());
        }

        UUID nuevoId = writeService.insertDocumentoExpediente(expId, tipoDocumento,
                archivo.getOriginalFilename(), user.getId().toString());

        String mensaje = paperlessTaskId != null
                ? "Documento registrado. OCR Paperless en cola (tarea: " + paperlessTaskId + ")."
                : "Documento registrado. Paperless no configurado; OCR pendiente.";

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", nuevoId,
                "paperless_task_id", paperlessTaskId != null ? paperlessTaskId : "",
                "mensaje", mensaje));
    }

    @GetMapping("/expediente/alumno/{estudiante_id}/documentos/{doc_id}/preview")
    public ResponseEntity<byte[]> previewDocumento(
            @PathVariable("estudiante_id") UUID estudianteId,
            @PathVariable("doc_id") UUID docId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        Map<String, Object> doc = queryService.documentoById(docId);
        Integer paperlessDocId = (Integer) doc.get("paperless_doc_id");
        if (paperlessDocId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento aún no procesado por Paperless.");
        }

        byte[] contenido = paperlessSvc.descargarDocumento(paperlessDocId);
        if (contenido == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo obtener el documento de Paperless.");
        }

        String filename = doc.get("nombre_archivo") != null ? doc.get("nombre_archivo").toString().toLowerCase() : "";
        String mediaType = filename.endsWith(".jpg") || filename.endsWith(".jpeg") ? "image/jpeg"
                : filename.endsWith(".png") ? "image/png"
                : filename.endsWith(".webp") ? "image/webp"
                : "application/pdf";

        return ResponseEntity.ok().contentType(MediaType.parseMediaType(mediaType)).body(contenido);
    }

    @DeleteMapping("/expediente/{expediente_id}/documentos/{doc_id}")
    public ResponseEntity<Void> eliminarDocumento(
            @PathVariable("expediente_id") UUID expedienteId,
            @PathVariable("doc_id") UUID docId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        List<Map<String, Object>> rows = queryService.fetchDocForDelete(docId, expedienteId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento no encontrado.");

        Integer paperlessDocId = (Integer) rows.get(0).get("paperless_doc_id");
        if (paperlessDocId != null) paperlessSvc.eliminarDocumento(paperlessDocId);

        writeService.softDeleteDocumento(docId);
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
        return ResponseEntity.ok(paperlessSvc.buscarDocumentos(q, page, pageSize));
    }

    /** GET /expediente/alumno/{estudiante_id}/buscar — busca OCR en los docs del alumno */
    @GetMapping("/expediente/alumno/{estudiante_id}/buscar")
    public ResponseEntity<Map<String, Object>> buscarDocumentosAlumno(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestParam(value = "q", defaultValue = "") String q,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "25") int pageSize,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // Verificar que el alumno pertenece al plantel del usuario (para no-admins globales)
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 1 && user.getPlantelId() != null) {
            boolean tieneAcceso = !jdbc.queryForList(
                "SELECT id FROM ades_estudiantes WHERE id = ?::uuid AND plantel_id = ?::uuid AND is_active = true",
                estudianteId, user.getPlantelId()).isEmpty();
            if (!tieneAcceso) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Sin acceso al expediente del alumno solicitado");
            }
        }
        if (!paperlessSvc.hasToken()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Paperless-ngx no configurado.");
        }
        // Incluir estudianteId como término de búsqueda adicional para acotar resultados al alumno
        String scopedQuery = q.isBlank() ? estudianteId.toString() : q + " " + estudianteId;
        return ResponseEntity.ok(paperlessSvc.buscarDocumentos(scopedQuery, page, pageSize));
    }

    @PostMapping("/expediente/{expediente_id}/verificar")
    public ResponseEntity<Map<String, Object>> verificarExpediente(
            @PathVariable("expediente_id") UUID expedienteId,
            @RequestParam(value = "observaciones", required = false) String observaciones,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        verificarExpediente.ejecutar(new VerificarExpedienteUseCase.Command(
                expedienteId, observaciones, user.getNivelAcceso(), user.getId()));

        return ResponseEntity.ok(Map.of(
                "expediente_id", expedienteId,
                "estado", "VERIFICADO",
                "mensaje", "Expediente verificado correctamente."));
    }

    @PostMapping("/expediente/alumno/{estudiante_id}/analizar-ia")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> analizarExpedienteIa(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        UUID cicloRef = cicloId != null ? cicloId : queryService.cicloActivoId();
        Map<String, Object> alumno = queryService.alumnoParaAnalisis(estudianteId);
        Map<String, Object> exp = queryService.obtenerOCrearExpediente(estudianteId, cicloRef);
        UUID expId = (UUID) exp.get("id");
        List<Map<String, Object>> docs = queryService.documentosExpediente(expId);

        List<String> tiposPresentes = new ArrayList<>();
        List<String> ocrResumen = new ArrayList<>();
        for (Map<String, Object> d : docs) {
            String tipo = (String) d.get("tipo_documento");
            tiposPresentes.add(tipo);
            String ocrText = (String) d.get("ocr_texto");
            if (ocrText != null && !ocrText.isBlank()) {
                ocrResumen.add("[" + tipo + "]: " + ocrText.substring(0, Math.min(ocrText.length(), 300)));
            }
        }

        List<String> tiposFaltantes = new ArrayList<>(queryService.tiposRequeridos());
        tiposFaltantes.removeAll(tiposPresentes);

        String nombreCompleto = String.format("%s %s %s",
                alumno.get("nombre"), alumno.get("apellido_paterno"),
                alumno.get("apellido_materno") != null ? alumno.get("apellido_materno") : "").trim();

        String prompt = String.format(
            "Eres asistente administrativo escolar del Instituto Nevadi, Mexico.\n\n" +
            "ALUMNO: %s | CURP: %s | Matricula: %s | Nivel: %s\n" +
            "DOCUMENTOS PRESENTES: %s\nDOCUMENTOS FALTANTES: %s\nEXTRACTOS OCR: %s\n\n" +
            "Responde SOLO con JSON valido:\n{\"analisis\": \"...\", \"recomendaciones\": [\"...\"], \"alertas\": [\"...\"]}",
            nombreCompleto,
            alumno.getOrDefault("curp", "N/A"),
            alumno.getOrDefault("matricula", "N/A"),
            alumno.getOrDefault("nivel", "N/A"),
            String.join(", ", tiposPresentes),
            String.join(", ", tiposFaltantes),
            String.join("\n", ocrResumen));

        String analisisTexto = "NVIDIA NIM no configurado. Configure OPENAI_API_KEY en Vault.";
        List<String> recomendaciones = new ArrayList<>();
        List<String> alertas = new ArrayList<>();

        if (openaiApiKey != null && !openaiApiKey.isBlank()) {
            try {
                RestClient client = RestClient.builder().build();
                ResponseEntity<String> response = client.post()
                        .uri(openaiBaseUrl + "/chat/completions")
                        .header("Authorization", "Bearer " + openaiApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("model", openaiModel,
                                "messages", List.of(Map.of("role", "user", "content", prompt)),
                                "temperature", 0.3, "max_tokens", 600))
                        .retrieve().toEntity(String.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Map<String, Object> outer = objectMapper.readValue(response.getBody(), Map.class);
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) outer.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        String content = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{.*\\}", java.util.regex.Pattern.DOTALL).matcher(content);
                        if (m.find()) {
                            Map<String, Object> ia = objectMapper.readValue(m.group(), Map.class);
                            analisisTexto = (String) ia.getOrDefault("analisis", content);
                            recomendaciones = (List<String>) ia.getOrDefault("recomendaciones", new ArrayList<>());
                            alertas = (List<String>) ia.getOrDefault("alertas", new ArrayList<>());
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

        writeService.actualizarObservacionesExpediente(expId,
            analisisTexto.substring(0, Math.min(analisisTexto.length(), 500)));

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
