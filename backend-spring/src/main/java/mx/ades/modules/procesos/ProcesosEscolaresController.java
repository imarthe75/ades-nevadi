package mx.ades.modules.procesos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.procesos.domain.port.in.ProcesarPreinscripcionUseCase;
import mx.ades.modules.procesos.query.ProcesosQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import mx.ades.common.WebhookService;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/procesos")
@RequiredArgsConstructor
public class ProcesosEscolaresController {

    private final AdesUserService userService;
    private final WebhookService webhookService;
    private final mx.ades.common.ZipService zipService;
    private final ProcesarPreinscripcionUseCase procesarPreinscripcion;
    private final ProcesosQueryService queryService;
    private final ProcesosWriteService writeService;
    private final mx.ades.modules.imports.ImportsWriteService importsWriteService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final RestClient restClient = RestClient.builder().build();

    @Value("${carbone.url:http://ades-carbone:3000}")
    private String carboneUrl;

    private void requireSecretariaOrHigher(AdesUser user) {
        if (user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
    }

    private void requireAdminOrHigher(AdesUser user) {
        if (user.getNivelAcceso() > 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere Rol Admin o superior");
        }
    }

    // ── FASE 34: Importación SEP & Descarga ZIP Expediente ─────────────────────

    /**
     * @deprecated sin consumidores en el frontend (verificado 2026-07-03) — la
     * importación de preinscripciones SEP vive ahora en el módulo genérico
     * {@code mx.ades.modules.imports} vía {@code TipoEntidadImport.PREINSCRITOS_SEP}
     * (mismo flujo de plantilla/CSV que usan alumnos, profesores, etc.), consumido
     * desde {@code admision.component.ts} con {@code <app-import-button entidad="preinscritos-sep">}.
     * Se deja sin eliminar por ahora para no romper integraciones externas no documentadas.
     */
    @Deprecated
    @PostMapping(value = "/importar-sep", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importarSep(
            @RequestParam("archivo") org.springframework.web.multipart.MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireSecretariaOrHigher(user);

        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo está vacío");
        }

        try {
            mx.ades.modules.imports.ImportadorUtil.ParsedFile parsed =
                    mx.ades.modules.imports.ImportadorUtil.parseFile(file.getBytes(), file.getOriginalFilename());
            List<String> headers = parsed.getHeaders();
            List<List<String>> rows = parsed.getRows();

            int creados = 0;
            int omitidos = 0;

            for (List<String> row : rows) {
                String curp = mx.ades.modules.imports.ImportadorUtil.getCol(row, headers, "curp");
                if (curp.isBlank()) continue;

                String nombre = mx.ades.modules.imports.ImportadorUtil.getCol(row, headers, "nombre");
                String primerApellido = mx.ades.modules.imports.ImportadorUtil.getCol(row, headers, "primer_apellido", "apellido_paterno");
                String segundoApellido = mx.ades.modules.imports.ImportadorUtil.getCol(row, headers, "segundo_apellido", "apellido_materno");
                String fechaNac = mx.ades.modules.imports.ImportadorUtil.getCol(row, headers, "fecha_nacimiento", "fecha_nac");
                String nivel = mx.ades.modules.imports.ImportadorUtil.getCol(row, headers, "nivel_solicitado", "nivel");
                String gradoStr = mx.ades.modules.imports.ImportadorUtil.getCol(row, headers, "grado_solicitado", "grado");
                String tutor = mx.ades.modules.imports.ImportadorUtil.getCol(row, headers, "tutor", "nombre_tutor");
                String telTutor = mx.ades.modules.imports.ImportadorUtil.getCol(row, headers, "telefono_tutor", "telefono");
                String emailTutor = mx.ades.modules.imports.ImportadorUtil.getCol(row, headers, "email_tutor", "email");

                Integer grado = mx.ades.modules.imports.ImportadorUtil.parseInt(gradoStr);

                boolean creado = writeService.registrarSolicitudSEP(nombre.trim(), primerApellido.trim(),
                        segundoApellido, fechaNac, curp.trim(), nivel, grado,
                        tutor, telTutor, emailTutor, user.getUsername());
                if (creado) creados++; else omitidos++;
            }

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "creados", creados,
                    "omitidos_duplicados", omitidos
            ));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al importar preinscripciones: " + e.getMessage());
        }
    }

    @GetMapping("/alumnos/{id}/expediente-zip")
    public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> descargarZip(
            @PathVariable("id") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        UUID cicloRef = cicloId;
        if (cicloRef == null) {
            List<UUID> ids = queryService.fetchCicloPorVigente();
            if (ids.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay ciclo escolar activo configurado.");
            }
            cicloRef = ids.get(0);
        }

        List<UUID> expIds = queryService.fetchExpedienteId(estudianteId, cicloRef);
        if (expIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expediente no encontrado.");
        }
        UUID expedienteId = expIds.get(0);

        List<Map<String, Object>> documentos = queryService.fetchDocumentosExpediente(expedienteId);
        if (documentos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El expediente no tiene documentos cargados en Paperless.");
        }

        String filename = "expediente_" + estudianteId + ".zip";
        String bearerToken = jwt.getTokenValue();
        org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody responseBody =
                outputStream -> zipService.compressDocuments(documentos, outputStream, bearerToken);

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(responseBody);
    }

    // ── PE-003: Solicitudes de admisión ──────────────────────────────────────

    @Data
    public static class AdmisionRequest {
        @NotBlank(message = "nombre es obligatorio")
        private String nombre;
        @NotBlank(message = "apellidoPaterno es obligatorio")
        private String apellidoPaterno;
        private String apellidoMaterno;
        @NotBlank(message = "fechaNacimiento es obligatorio")
        private String fechaNacimiento;
        @NotBlank(message = "curp es obligatorio")
        private String curp;
        @NotBlank(message = "nivelSolicitado es obligatorio")
        private String nivelSolicitado;
        @NotNull(message = "gradoSolicitado es obligatorio")
        private Integer gradoSolicitado;
        @NotNull(message = "plantelId es obligatorio")
        private UUID plantelId;
        @NotBlank(message = "nombreTutor es obligatorio")
        private String nombreTutor;
        @NotBlank(message = "telefonoTutor es obligatorio")
        private String telefonoTutor;
        private String emailTutor;
        private String escuelaProcedencia;
        private Double promedioProcedencia;
    }

    @Data
    public static class ResolucionRequest {
        private String decision;
        private String motivo;
        private UUID grupoAsignadoId;
    }

    @GetMapping("/admision")
    public ResponseEntity<List<Map<String, Object>>> listarAdmisiones(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "estado", required = false) String estado,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @RequestParam(value = "skip", defaultValue = "0") int skip,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireSecretariaOrHigher(user);
        return ResponseEntity.ok(queryService.listarAdmisiones(plantelId, estado, cicloId, skip, limit));
    }

    @PostMapping("/admision")
    public ResponseEntity<Map<String, Object>> crearSolicitud(
            @RequestBody @Valid AdmisionRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        mx.ades.common.ValidationUtils.validarCURP(body.getCurp());
        mx.ades.common.ValidationUtils.validarEmail(body.getEmailTutor());
        mx.ades.common.ValidationUtils.validarTelefono(body.getTelefonoTutor());
        mx.ades.common.ValidationUtils.validarNombrePersona(body.getNombre(), "El nombre del alumno");
        mx.ades.common.ValidationUtils.validarNombrePersona(body.getApellidoPaterno(), "El apellido paterno del alumno");
        mx.ades.common.ValidationUtils.validarNombrePersona(body.getApellidoMaterno(), "El apellido materno del alumno");
        mx.ades.common.ValidationUtils.validarNombrePersona(body.getNombreTutor(), "El nombre del tutor");

        UUID id = writeService.registrarSolicitudManual(
                body.getNombre(), body.getApellidoPaterno(), body.getApellidoMaterno(),
                body.getFechaNacimiento(), body.getCurp(), body.getNivelSolicitado(),
                body.getGradoSolicitado(), body.getPlantelId(),
                body.getNombreTutor(), body.getTelefonoTutor(), body.getEmailTutor(),
                body.getEscuelaProcedencia(), body.getPromedioProcedencia(), user.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", id.toString(),
                "message", "Solicitud de admisión registrada",
                "estado", "PENDIENTE"
        ));
    }

    @PostMapping("/admision/{id}/aceptar")
    public ResponseEntity<Map<String, Object>> resolverAdmision(
            @PathVariable("id") UUID id,
            @RequestBody ResolucionRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdminOrHigher(user);

        List<Map<String, Object>> sol = queryService.fetchSolicitudEstado(id);
        if (sol.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada");
        }
        String estado = (String) sol.get(0).get("estado");
        if (!"PENDIENTE".equalsIgnoreCase(estado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La solicitud ya fue resuelta: " + estado);
        }

        writeService.actualizarResolucion(id, body.getDecision(), body.getMotivo(),
                body.getGrupoAsignadoId(), user.getPersonaId(), user.getUsername());

        return ResponseEntity.ok(Map.of("message", "Solicitud " + body.getDecision().toLowerCase(), "estado", body.getDecision()));
    }

    // ── PE-006: Documentos de admisión ───────────────────────────────────────

    @GetMapping("/documentos-admision")
    public ResponseEntity<List<Map<String, Object>>> listarDocumentos(
            @RequestParam(value = "admision_id", required = false) UUID admisionId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.listarDocumentosAdmision(admisionId));
    }

    // ── PE-007: Preinscripción ───────────────────────────────────────────────

    @Data
    public static class PreinscripcionRequest {
        private UUID admisionId;
        private UUID cicloEscolarId;
        private UUID grupoId;
    }

    @PostMapping("/preinscripcion")
    public ResponseEntity<Map<String, Object>> preinscribir(
            @RequestBody PreinscripcionRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireSecretariaOrHigher(user);

        var result = procesarPreinscripcion.ejecutar(
                new ProcesarPreinscripcionUseCase.Command(
                        body.getAdmisionId(), body.getCicloEscolarId(), body.getGrupoId(), user.getUsername()));

        Map<String, Object> webhookData = new HashMap<>();
        webhookData.put("estudiante_id", result.estudianteId().toString());
        webhookData.put("matricula", result.matricula());
        webhookData.put("nombre", result.nombre());
        webhookData.put("apellido_paterno", result.apellidoPaterno());
        webhookData.put("curp", result.curp());
        webhookData.put("grupo_id", body.getGrupoId().toString());
        webhookData.put("ciclo_escolar_id", body.getCicloEscolarId().toString());
        webhookData.put("fecha_inscripcion", LocalDate.now().toString());
        webhookService.dispatchWebhook("ALUMNO_INSCRITO", webhookData);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Preinscripción completada",
                "estudiante_id", result.estudianteId().toString()
        ));
    }

    // ── Aprobar + Inscribir + Crear Usuarios (flujo completo en 1 paso) ──────

    @Data
    public static class AprobarEInscribirRequest {
        private UUID cicloEscolarId;
        private UUID grupoId;
        private String motivoDecision;
    }

    /**
     * POST /api/v1/procesos/admision/{id}/aprobar-e-inscribir
     * Combina: aprobación de la solicitud + preinscripción + creación de cuentas ALUMNO y PADRE_FAMILIA.
     * El padre/tutor capturado en la solicitud recibe una cuenta automáticamente.
     */
    @PostMapping("/admision/{id}/aprobar-e-inscribir")
    @Transactional
    public ResponseEntity<Map<String, Object>> aprobarEInscribir(
            @PathVariable("id") UUID solicitudId,
            @RequestBody AprobarEInscribirRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        requireSecretariaOrHigher(user);

        if (body.getCicloEscolarId() == null || body.getGrupoId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cicloEscolarId y grupoId son requeridos");

        // 1. Verificar estado de solicitud
        List<Map<String, Object>> solRows = queryService.fetchSolicitudEstado(solicitudId);
        if (solRows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada");
        String estado = (String) solRows.get(0).get("estado");
        if (!"PENDIENTE".equalsIgnoreCase(estado))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La solicitud ya fue resuelta: " + estado);

        // Obtener datos completos de la solicitud
        List<Map<String, Object>> solData = jdbc.queryForList(
            "SELECT * FROM ades_solicitudes_admision WHERE id = ?", solicitudId);
        Map<String, Object> sol = solData.get(0);

        // 2. Aprobar la solicitud — el estado debe quedar en ACEPTADO: es el único valor
        // que ProcesosApplicationService/EstadoAdmision.permitePreinscripcion() acepta para
        // continuar con la preinscripción (no existe "APROBADO" en el enum de dominio).
        writeService.actualizarResolucion(solicitudId, "ACEPTADO",
            body.getMotivoDecision(), body.getGrupoId(), user.getPersonaId(), user.getUsername());

        // 3. Crear estudiante + inscripción via use case
        var result = procesarPreinscripcion.ejecutar(
            new ProcesarPreinscripcionUseCase.Command(
                solicitudId, body.getCicloEscolarId(), body.getGrupoId(), user.getUsername()));

        // 4. Crear usuario ALUMNO
        String alumnoUsername = null;
        try {
            UUID rolAlumnoId = (UUID) jdbc.queryForList(
                "SELECT id FROM ades_roles WHERE nombre_rol = 'ALUMNO' LIMIT 1").get(0).get("id");

            String base = (result.nombre().substring(0, 1)
                + result.apellidoPaterno().replace(" ", "")).toLowerCase();
            if (base.length() > 9) base = base.substring(0, 9);
            String username = base;
            int counter = 1;
            while (!jdbc.queryForList("SELECT id FROM ades_usuarios WHERE nombre_usuario = ?", username).isEmpty()) {
                username = base + counter++;
            }
            alumnoUsername = username;
            String emailAlumno = username + "@nevadi.edu.mx";

            // Buscar persona_id del estudiante
            List<Map<String, Object>> personaRows = jdbc.queryForList(
                "SELECT persona_id FROM ades_estudiantes WHERE id = ?", result.estudianteId());
            if (!personaRows.isEmpty()) {
                UUID personaId = (UUID) personaRows.get(0).get("persona_id");
                // Verificar que no tenga ya usuario ALUMNO
                List<Map<String, Object>> usrExist = jdbc.queryForList(
                    "SELECT id FROM ades_usuarios WHERE persona_id = ? AND rol_id = ?",
                    personaId, rolAlumnoId);
                if (usrExist.isEmpty()) {
                    jdbc.update(
                        "INSERT INTO ades_usuarios (id, persona_id, nombre_usuario, email_institucional, " +
                        "rol_id, is_active, usuario_creacion, usuario_modificacion) " +
                        "VALUES (gen_random_uuid(), ?, ?, ?, ?, true, ?, ?)",
                        personaId, alumnoUsername, emailAlumno, rolAlumnoId,
                        user.getUsername(), user.getUsername());
                }
            }
        } catch (Exception ex) {
            // No bloquear la inscripción si falla la creación de usuario
        }

        // 5. Crear usuario PADRE_FAMILIA desde datos del tutor en la solicitud
        String padreUsername = null;
        String nombreTutor = (String) sol.get("nombre_tutor");
        String emailTutor  = (String) sol.get("email_tutor");
        if (nombreTutor != null && !nombreTutor.isBlank()) {
            try {
                List<Map<String, Object>> rolPadreRows = importsWriteService.getRolPadreId();
                if (!rolPadreRows.isEmpty()) {
                    UUID rolPadreId = (UUID) rolPadreRows.get(0).get("id");
                    String[] partesTutor = nombreTutor.trim().split("\\s+", 3);
                    String nomPadre = partesTutor[0];
                    String apPatPadre = partesTutor.length > 1 ? partesTutor[1] : "N/A";
                    String apMatPadre = partesTutor.length > 2 ? partesTutor[2] : null;
                    String curpPadre  = "TEMP" + result.curp().substring(0, Math.min(14, result.curp().length()))
                        + (int)(Math.random()*9);

                    mx.ades.modules.imports.ImportsWriteService.PadreData padreData =
                        mx.ades.modules.imports.ImportsWriteService.PadreData.builder()
                            .nombre(nomPadre)
                            .apellidoPaterno(apPatPadre)
                            .apellidoMaterno(apMatPadre)
                            .curp(curpPadre)
                            .email(emailTutor)
                            .telefono((String) sol.get("telefono_tutor"))
                            .rolPadreId(rolPadreId)
                            .usuario(user.getUsername())
                            .build();
                    importsWriteService.insertarPadreYVincular(result.estudianteId(), padreData);
                    padreUsername = nomPadre.toLowerCase().charAt(0)
                        + apPatPadre.toLowerCase().replace(" ", "");
                }
            } catch (Exception ex) {
                // No bloquear si falla creación de padre
            }
        }

        Map<String, Object> webhookData = new HashMap<>();
        webhookData.put("estudiante_id", result.estudianteId().toString());
        webhookData.put("matricula", result.matricula());
        webhookData.put("nombre", result.nombre());
        webhookData.put("grupo_id", body.getGrupoId().toString());
        webhookService.dispatchWebhook("ALUMNO_INSCRITO", webhookData);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("message", "Solicitud aprobada, alumno inscrito y cuentas creadas");
        resp.put("estudiante_id", result.estudianteId().toString());
        resp.put("matricula", result.matricula());
        if (alumnoUsername != null) resp.put("usuario_alumno", alumnoUsername);
        if (padreUsername != null) resp.put("usuario_padre", padreUsername);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    // ── PE-011: Lista de espera y Notificación ───────────────────────────────

    @GetMapping("/lista-espera")
    public ResponseEntity<List<Map<String, Object>>> listaEspera(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "nivel_solicitado", required = false) String nivelSolicitado,
            @RequestParam(value = "skip", defaultValue = "0") int skip,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireSecretariaOrHigher(user);
        skip = Math.max(skip, 0);
        limit = Math.min(Math.max(limit, 1), 200);
        return ResponseEntity.ok(queryService.listaEspera(plantelId, nivelSolicitado, skip, limit));
    }

    @PostMapping("/lista-espera/{id}/notificar")
    public ResponseEntity<Map<String, Object>> notificarListaEspera(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireSecretariaOrHigher(user);

        List<Map<String, Object>> sol = queryService.fetchSolicitudListaEspera(id);
        if (sol.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud en lista de espera no encontrada");
        }
        Map<String, Object> sm = sol.get(0);

        writeService.enqueueNotificacion(id, (String) sm.get("email_tutor"), (String) sm.get("nombre"), user.getUsername());
        writeService.marcarNotificado(id, user.getUsername());

        return ResponseEntity.ok(Map.of("message", "Notificación encolada para envío por correo"));
    }

    // ── PE-014: Materias optativas ───────────────────────────────────────────

    @Data
    public static class OptativaRequest {
        @NotNull(message = "estudianteId es obligatorio")
        private UUID estudianteId;
        @NotNull(message = "materiaId es obligatorio")
        private UUID materiaId;
        @NotNull(message = "cicloEscolarId es obligatorio")
        private UUID cicloEscolarId;
    }

    @GetMapping("/optativas")
    public ResponseEntity<List<Map<String, Object>>> listarOptativas(
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @RequestParam(value = "estudiante_id", required = false) UUID estudianteId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.listarOptativas(cicloId, estudianteId));
    }

    @PostMapping("/optativas")
    public ResponseEntity<Map<String, Object>> inscribirOptativa(
            @RequestBody @Valid OptativaRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        if (!queryService.checkMateriaExists(body.getMateriaId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Materia no encontrada");
        }
        if (queryService.checkDupOptativa(body.getEstudianteId(), body.getMateriaId(), body.getCicloEscolarId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El alumno ya está inscrito en esta optativa");
        }

        UUID id = writeService.inscribirOptativa(body.getEstudianteId(), body.getMateriaId(),
                body.getCicloEscolarId(), user.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", id.toString(),
                "message", "Inscripción a optativa registrada"
        ));
    }

    @DeleteMapping("/optativas/{id}")
    public ResponseEntity<Void> bajaOptativa(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        int updated = writeService.darBajaOptativa(id, user.getUsername());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inscripción no encontrada o ya inactiva");
        }
        return ResponseEntity.noContent().build();
    }

    // ── PE-026: Acuerdo de convivencia ───────────────────────────────────────

    @Data
    public static class AcuerdoRequest {
        // alumno_id y tutor_nombre son NOT NULL en ades_acuerdos_convivencia (sin default);
        // antes de este fix no había ninguna validación y el INSERT fallaba con
        // DataIntegrityViolationException -> 409 genérico en vez de un 422 claro.
        @NotNull(message = "alumnoId es obligatorio")
        private UUID alumnoId;
        @NotBlank(message = "tutorNombre es obligatorio")
        private String tutorNombre;
        private String tutorFirmaHash;
        private String ipFirma;
    }

    @PostMapping("/acuerdo-convivencia")
    public ResponseEntity<Map<String, Object>> registrarAcuerdo(
            @RequestBody @Valid AcuerdoRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        UUID id = writeService.registrarAcuerdo(body.getAlumnoId(), body.getTutorNombre(),
                body.getTutorFirmaHash(), body.getIpFirma(), user.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", id.toString(),
                "message", "Acuerdo de convivencia firmado y registrado"
        ));
    }

    // ── AC-005: Calendarios académicos ───────────────────────────────────────

    @Data
    public static class CalendarioRequest {
        @NotBlank(message = "nombre es obligatorio")
        private String nombre;
        private UUID cicloEscolarId;
        @NotBlank(message = "nivelEducativo es obligatorio")
        private String nivelEducativo;
        @NotBlank(message = "tipo es obligatorio")
        private String tipo;
        @NotNull(message = "fechaInicio es obligatorio")
        private LocalDate fechaInicio;
        @NotNull(message = "fechaFin es obligatorio")
        private LocalDate fechaFin;
        private String descripcion;
        private Boolean esOficial = true;
    }

    @GetMapping("/calendarios-academicos")
    public ResponseEntity<List<Map<String, Object>>> listarCalendarios(
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @RequestParam(value = "nivel_educativo", required = false) String nivelEducativo,
            @RequestParam(value = "tipo", required = false) String tipo,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.listarCalendarios(cicloId, nivelEducativo, tipo));
    }

    @PostMapping("/calendarios-academicos")
    public ResponseEntity<Map<String, Object>> crearEventoCalendario(
            @RequestBody @Valid CalendarioRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdminOrHigher(user);

        if (body.getFechaFin().isBefore(body.getFechaInicio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "fechaFin no puede ser anterior a fechaInicio");
        }

        UUID id = writeService.crearEventoCalendario(body.getNombre(), body.getCicloEscolarId(),
                body.getNivelEducativo(), body.getTipo(), body.getFechaInicio(),
                body.getFechaFin(), body.getDescripcion(), body.getEsOficial(), user.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Evento de calendario registrado"));
    }

    // ── AC-014: Periodos de evaluación ───────────────────────────────────────

    @Data
    public static class PeriodoEvaluacionRequest {
        @NotNull(message = "cicloEscolarId es obligatorio")
        private UUID cicloEscolarId;
        @NotBlank(message = "nombrePeriodo es obligatorio")
        private String nombrePeriodo;
        @NotNull(message = "numeroPeriodo es obligatorio")
        private Integer numeroPeriodo;
        private String tipoEvaluacion;
        @NotNull(message = "fechaInicio es obligatorio")
        private LocalDate fechaInicio;
        @NotNull(message = "fechaFin es obligatorio")
        private LocalDate fechaFin;
        private Boolean abierto = true;
    }

    @PostMapping("/periodos-evaluacion")
    public ResponseEntity<Map<String, Object>> crearPeriodoEvaluacion(
            @RequestBody @Valid PeriodoEvaluacionRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdminOrHigher(user);

        if (body.getFechaFin().isBefore(body.getFechaInicio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "fechaFin no puede ser anterior a fechaInicio");
        }

        UUID id = writeService.crearPeriodoEvaluacion(body.getCicloEscolarId(), body.getNombrePeriodo(),
                body.getNumeroPeriodo(), body.getTipoEvaluacion(), body.getFechaInicio(),
                body.getFechaFin(), body.getAbierto(), user.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(),
                "message", "Período de evaluación " + body.getNombrePeriodo() + " creado"));
    }

    @PatchMapping("/periodos-evaluacion/{id}/cerrar")
    public ResponseEntity<Map<String, Object>> cerrarPeriodo(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdminOrHigher(user);
        writeService.cerrarPeriodo(id, user.getUsername());
        return ResponseEntity.ok(Map.of("message", "Período de evaluación cerrado"));
    }

    // ── AC-015: Calendario de actividades ────────────────────────────────────

    @GetMapping("/calendario-actividades")
    public ResponseEntity<List<Map<String, Object>>> calendarioActividades(
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "mes", required = false) Integer mes,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.calendarioActividades(cicloId, plantelId, user.getPlantelId(), mes));
    }

    // ── PE-003: Evaluación Diagnóstica ───────────────────────────────────────

    @Data
    public static class EvaluacionRequest {
        @NotNull(message = "puntuacionDiagnostico es obligatorio")
        @DecimalMin(value = "0", message = "puntuacionDiagnostico mínimo 0")
        @DecimalMax(value = "100", message = "puntuacionDiagnostico máximo 100")
        private Double puntuacionDiagnostico;
        private String observacionesDiagnostico;
    }

    @PatchMapping("/admision/{id}/evaluacion")
    public ResponseEntity<Map<String, Object>> registrarEvaluacionDiagnostica(
            @PathVariable("id") UUID id,
            @RequestBody @Valid EvaluacionRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireSecretariaOrHigher(user);
        writeService.registrarEvaluacionDiagnostica(id, body.getPuntuacionDiagnostico(),
                body.getObservacionesDiagnostico(), user.getUsername());
        return ResponseEntity.ok(Map.of("message", "Evaluación diagnóstica registrada", "estado", "DIAGNOSTICO"));
    }

    // ── PE-005: Generar Carta de Admisión ────────────────────────────────────

    @PostMapping("/admision/{id}/carta")
    public ResponseEntity<byte[]> generarCarta(
            @PathVariable("id") UUID id,
            @RequestParam("template_id") String templateId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        List<Map<String, Object>> sol = queryService.fetchSolicitudParaCarta(id);
        if (sol.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada");
        }
        Map<String, Object> s = sol.get(0);

        Map<String, Object> payload = new HashMap<>();
        payload.put("nombre_completo", (s.get("nombre") + " " + s.get("apellido_paterno") + " " + (s.get("apellido_materno") != null ? s.get("apellido_materno") : "")).trim());
        payload.put("curp", s.get("curp"));
        payload.put("estado", s.get("estado"));
        payload.put("nivel", s.get("nivel_solicitado"));
        payload.put("grado", s.get("grado_solicitado"));
        payload.put("tutor", s.get("nombre_tutor"));
        payload.put("fecha", s.get("fecha_solicitud").toString());
        payload.put("fecha_impresion", LocalDate.now().toString());

        try {
            Map<String, Object> reqBody = new HashMap<>();
            reqBody.put("data", payload);

            ResponseEntity<byte[]> response = restClient.post()
                    .uri(carboneUrl + "/render/" + templateId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(reqBody)
                    .retrieve()
                    .toEntity(byte[].class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String filename = ("Carta_Admision_" + s.get("nombre") + "_" + s.get("apellido_paterno") + ".pdf").replace(" ", "_");
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header("Content-Disposition", "attachment; filename=" + filename)
                        .body(response.getBody());
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al generar la carta");
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al renderizar con Carbone: " + e.getMessage());
        }
    }

    // ── PE-006: Timeline de expediente de admisión ────────────────────────────

    @GetMapping("/admision/{id}/historial")
    public ResponseEntity<List<Map<String, Object>>> historialAdmision(
            @PathVariable("id") UUID id,
            @RequestParam(value = "skip", defaultValue = "0") int skip,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        skip = Math.max(skip, 0);
        limit = Math.min(Math.max(limit, 1), 200);
        return ResponseEntity.ok(queryService.fetchHistorialAdmision(id, skip, limit));
    }

    // ── PE-020: Workflow de Bajas ────────────────────────────────────────────

    @Data
    public static class BajaRequest {
        @NotNull(message = "estudianteId es obligatorio")
        private UUID estudianteId;
        private UUID inscripcionId;
        @NotBlank(message = "tipoBaja es obligatorio")
        private String tipoBaja;
        private String motivo;
        @NotNull(message = "fechaEfectiva es obligatorio")
        private LocalDate fechaEfectiva;
        private String observaciones;
    }

    @PostMapping("/bajas")
    public ResponseEntity<Map<String, Object>> registrarBaja(
            @RequestBody @Valid BajaRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdminOrHigher(user);

        UUID id = writeService.registrarBaja(body.getEstudianteId(), body.getInscripcionId(),
                body.getTipoBaja(), body.getMotivo(), body.getFechaEfectiva(),
                body.getObservaciones(), user.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", id.toString(),
                "message", "Baja registrada con éxito. Cupo liberado."
        ));
    }

    @GetMapping("/bajas")
    public ResponseEntity<List<Map<String, Object>>> listarBajas(
            @RequestParam(value = "skip", defaultValue = "0") int skip,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireSecretariaOrHigher(user);
        skip = Math.max(skip, 0);
        limit = Math.min(Math.max(limit, 1), 200);
        return ResponseEntity.ok(queryService.listarBajas(skip, limit));
    }

    @PostMapping("/bajas/{baja_id}/reactivar")
    public ResponseEntity<Map<String, Object>> reactivarEstudiante(
            @PathVariable("baja_id") UUID bajaId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdminOrHigher(user);

        List<Map<String, Object>> baja = queryService.fetchBajaParaReactivar(bajaId);
        if (baja.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro de baja no encontrado");
        }
        Map<String, Object> bm = baja.get(0);
        UUID estudianteId = (UUID) bm.get("estudiante_id");
        UUID inscripcionId = (UUID) bm.get("inscripcion_id");

        writeService.reactivarEstudiante(bajaId, estudianteId, inscripcionId, user.getUsername());

        return ResponseEntity.ok(Map.of("ok", true, "message", "Estudiante reactivado con éxito"));
    }
}
