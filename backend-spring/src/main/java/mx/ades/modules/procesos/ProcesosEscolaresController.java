package mx.ades.modules.procesos;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
    private final JdbcTemplate jdbc;
    private final WebhookService webhookService;
    private final mx.ades.common.ZipService zipService;
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

                // Validar duplicados de CURP en solicitudes activas
                List<Map<String, Object>> dup = jdbc.queryForList(
                        "SELECT id FROM ades_solicitudes_admision WHERE curp = ? AND estado NOT IN ('RECHAZADO')",
                        curp.trim());
                if (!dup.isEmpty()) {
                    omitidos++;
                    continue;
                }

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

                UUID id = UUID.randomUUID();
                jdbc.update(
                        "INSERT INTO ades_solicitudes_admision " +
                        "(id, nombre, apellido_paterno, apellido_materno, fecha_nacimiento, curp, " +
                        "nivel_solicitado, grado_solicitado, estado, nombre_tutor, telefono_tutor, email_tutor, " +
                        "usuario_creacion, usuario_modificacion) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDIENTE', ?, ?, ?, ?, ?)",
                        id, nombre.trim(), primerApellido.trim(),
                        segundoApellido.isBlank() ? null : segundoApellido.trim(),
                        mx.ades.modules.imports.ImportadorUtil.parseDate(fechaNac), curp.trim(),
                        nivel.isBlank() ? "PRIMARIA" : nivel, grado == null ? 1 : grado,
                        tutor.isBlank() ? "Tutor SEP" : tutor, telTutor.isBlank() ? "5500000000" : telTutor,
                        emailTutor.isBlank() ? "sep@example.com" : emailTutor,
                        user.getUsername(), user.getUsername()
                );
                creados++;
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
            List<UUID> ids = jdbc.queryForList(
                    "SELECT id FROM public.ades_ciclos_escolares WHERE es_vigente = TRUE ORDER BY fecha_inicio DESC LIMIT 1",
                    UUID.class
            );
            if (ids.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay ciclo escolar activo configurado.");
            }
            cicloRef = ids.get(0);
        }

        // Buscar expediente activo
        List<Map<String, Object>> exps = jdbc.queryForList(
                "SELECT id FROM public.ades_expedientes_alumno WHERE estudiante_id = ? AND ciclo_escolar_id = ? AND is_active = TRUE",
                estudianteId, cicloRef
        );
        if (exps.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expediente no encontrado.");
        }
        UUID expedienteId = (UUID) exps.get(0).get("id");

        // Buscar documentos
        List<Map<String, Object>> documentos = jdbc.queryForList(
                "SELECT paperless_doc_id, nombre_archivo, tipo_documento FROM public.ades_expediente_documentos " +
                "WHERE expediente_id = ? AND is_active = TRUE AND paperless_doc_id IS NOT NULL",
                expedienteId
        );

        if (documentos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El expediente no tiene documentos cargados en Paperless.");
        }

        String filename = "expediente_" + estudianteId + ".zip";

        org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody responseBody = outputStream -> {
            zipService.compressDocuments(documentos, outputStream);
        };

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(responseBody);
    }

    // ── PE-003: Solicitudes de admisión ──────────────────────────────────────

    @Data
    public static class AdmisionRequest {
        private String nombre;
        private String apellidoPaterno;
        private String apellidoMaterno;
        private String fechaNacimiento;
        private String curp;
        private String nivelSolicitado;
        private Integer gradoSolicitado;
        private UUID plantelId;
        private String nombreTutor;
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

        StringBuilder sql = new StringBuilder(
                "SELECT id, nombre, apellido_paterno, apellido_materno, curp, " +
                "nivel_solicitado, grado_solicitado, estado, " +
                "nombre_tutor, email_tutor, fecha_solicitud, " +
                "promedio_procedencia, escuela_procedencia " +
                "FROM ades_solicitudes_admision " +
                "WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();
        if (plantelId != null) {
            sql.append("AND plantel_id = ? ");
            params.add(plantelId);
        }
        if (estado != null && !estado.isBlank()) {
            sql.append("AND estado = ? ");
            params.add(estado);
        }
        if (cicloId != null) {
            sql.append("AND ciclo_escolar_id = ? ");
            params.add(cicloId);
        }

        sql.append("ORDER BY fecha_solicitud DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(skip);

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/admision")
    public ResponseEntity<Map<String, Object>> crearSolicitud(
            @RequestBody AdmisionRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        // Validar CURP del alumno y datos de contacto del tutor
        mx.ades.common.ValidationUtils.validarCURP(body.getCurp());
        mx.ades.common.ValidationUtils.validarEmail(body.getEmailTutor());
        mx.ades.common.ValidationUtils.validarTelefono(body.getTelefonoTutor());

        List<Map<String, Object>> dup = jdbc.queryForList(
                "SELECT id FROM ades_solicitudes_admision WHERE curp = ? AND estado NOT IN ('RECHAZADO')",
                body.getCurp().trim());
        if (!dup.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una solicitud activa con este CURP");
        }

        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_solicitudes_admision " +
                "(id, nombre, apellido_paterno, apellido_materno, fecha_nacimiento, curp, " +
                "nivel_solicitado, grado_solicitado, plantel_id, nombre_tutor, telefono_tutor, email_tutor, " +
                "escuela_procedencia, promedio_procedencia, estado, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDIENTE', ?, ?)",
                id, body.getNombre().trim(), body.getApellidoPaterno().trim(),
                body.getApellidoMaterno() != null ? body.getApellidoMaterno().trim() : null,
                body.getFechaNacimiento(), body.getCurp().trim(), body.getNivelSolicitado(), body.getGradoSolicitado(),
                body.getPlantelId(), body.getNombreTutor(), body.getTelefonoTutor(), body.getEmailTutor(),
                body.getEscuelaProcedencia(), body.getPromedioProcedencia(), user.getUsername(), user.getUsername()
        );

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

        List<Map<String, Object>> sol = jdbc.queryForList(
                "SELECT estado FROM ades_solicitudes_admision WHERE id = ?", id);
        if (sol.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada");
        }
        String estado = (String) sol.get(0).get("estado");
        if (!"PENDIENTE".equalsIgnoreCase(estado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La solicitud ya fue resuelta: " + estado);
        }

        jdbc.update(
                "UPDATE ades_solicitudes_admision " +
                "SET estado = ?, motivo_decision = ?, grupo_asignado_id = ?, " +
                "fecha_resolucion = CURRENT_TIMESTAMP, resuelto_por = ?, usuario_modificacion = ? " +
                "WHERE id = ?",
                body.getDecision(), body.getMotivo(), body.getGrupoAsignadoId(),
                user.getId(), user.getUsername(), id
        );

        return ResponseEntity.ok(Map.of("message", "Solicitud " + body.getDecision().toLowerCase(), "estado", body.getDecision()));
    }

    // ── PE-006: Documentos de admisión ───────────────────────────────────────

    @GetMapping("/documentos-admision")
    public ResponseEntity<List<Map<String, Object>>> listarDocumentos(
            @RequestParam(value = "admision_id", required = false) UUID admisionId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder(
                "SELECT id, admision_id, tipo_documento, nombre_archivo, url_documento, " +
                "estado_validacion, observaciones, fecha_creacion " +
                "FROM ades_documentos_admision WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();
        if (admisionId != null) {
            sql.append("AND admision_id = ? ");
            params.add(admisionId);
        }

        sql.append("ORDER BY tipo_documento");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        return ResponseEntity.ok(rows);
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

        // Verificar solicitud aceptada
        List<Map<String, Object>> sol = jdbc.queryForList(
                "SELECT nombre, apellido_paterno, curp FROM ades_solicitudes_admision WHERE id = ? AND estado = 'ACEPTADO'",
                body.getAdmisionId());
        if (sol.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La solicitud debe estar en estado ACEPTADO");
        }
        Map<String, Object> s = sol.get(0);

        // Verificar capacidad del grupo
        List<Map<String, Object>> cap = jdbc.queryForList(
                "SELECT capacidad_maxima, " +
                "(SELECT COUNT(*) FROM ades_inscripciones WHERE grupo_id = ? AND is_active) AS inscritos " +
                "FROM ades_grupos WHERE id = ? AND is_active = TRUE", body.getGrupoId(), body.getGrupoId());
        if (cap.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo no encontrado");
        }
        int max = ((Number) cap.get(0).get("capacidad_maxima")).intValue();
        int cur = ((Number) cap.get(0).get("inscritos")).intValue();
        if (cur >= max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El grupo está lleno");
        }

        // Crear persona y estudiante
        UUID personaId = UUID.randomUUID();
        jdbc.update("INSERT INTO ades_personas (id, nombre, apellido_paterno, curp, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                personaId, s.get("nombre"), s.get("apellido_paterno"), s.get("curp"), user.getUsername(), user.getUsername());

        UUID estudianteId = UUID.randomUUID();
        String matricula = "MAT-" + (100000 + new Random().nextInt(900000));
        jdbc.update("INSERT INTO ades_estudiantes (id, persona_id, matricula, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?)",
                estudianteId, personaId, matricula, user.getUsername(), user.getUsername());

        // Inscribir
        jdbc.update("INSERT INTO ades_inscripciones (id, estudiante_id, grupo_id, ciclo_escolar_id, fecha_inscripcion, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, CURRENT_DATE, ?, ?)",
                UUID.randomUUID(), estudianteId, body.getGrupoId(), body.getCicloEscolarId(), user.getUsername(), user.getUsername());

        // Marcar solicitud como inscrito
        jdbc.update("UPDATE ades_solicitudes_admision SET estado = 'INSCRITO', usuario_modificacion = ? WHERE id = ?",
                user.getUsername(), body.getAdmisionId());

        // Disparar Webhook
        Map<String, Object> webhookData = new HashMap<>();
        webhookData.put("estudiante_id", estudianteId.toString());
        webhookData.put("matricula", matricula);
        webhookData.put("nombre", s.get("nombre"));
        webhookData.put("apellido_paterno", s.get("apellido_paterno"));
        webhookData.put("curp", s.get("curp"));
        webhookData.put("grupo_id", body.getGrupoId().toString());
        webhookData.put("ciclo_escolar_id", body.getCicloEscolarId().toString());
        webhookData.put("fecha_inscripcion", LocalDate.now().toString());

        webhookService.dispatchWebhook("ALUMNO_INSCRITO", webhookData);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Preinscripción completada",
                "estudiante_id", estudianteId.toString()
        ));
    }

    // ── PE-011: Lista de espera y Notificación ───────────────────────────────

    @GetMapping("/lista-espera")
    public ResponseEntity<List<Map<String, Object>>> listaEspera(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "nivel_solicitado", required = false) String nivelSolicitado,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireSecretariaOrHigher(user);

        StringBuilder sql = new StringBuilder(
                "SELECT id, nombre, apellido_paterno, nivel_solicitado, grado_solicitado, " +
                "nombre_tutor, telefono_tutor, email_tutor, fecha_solicitud " +
                "FROM ades_solicitudes_admision " +
                "WHERE estado = 'LISTA_ESPERA' "
        );

        List<Object> params = new ArrayList<>();
        if (plantelId != null) {
            sql.append("AND plantel_id = ? ");
            params.add(plantelId);
        }
        if (nivelSolicitado != null && !nivelSolicitado.isBlank()) {
            sql.append("AND nivel_solicitado = ? ");
            params.add(nivelSolicitado);
        }

        sql.append("ORDER BY fecha_solicitud ASC");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/lista-espera/{id}/notificar")
    public ResponseEntity<Map<String, Object>> notificarListaEspera(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireSecretariaOrHigher(user);

        List<Map<String, Object>> sol = jdbc.queryForList(
                "SELECT email_tutor, nombre FROM ades_solicitudes_admision WHERE id = ? AND estado='LISTA_ESPERA'", id);
        if (sol.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud en lista de espera no encontrada");
        }
        Map<String, Object> sm = sol.get(0);

        String payload = String.format("{\"admision_id\": \"%s\", \"email\": \"%s\", \"nombre\": \"%s\"}",
                id, sm.get("email_tutor"), sm.get("nombre"));

        jdbc.update("INSERT INTO ades_tareas_sistema (tipo_tarea, payload_json, estado, usuario_creacion, usuario_modificacion) " +
                "VALUES ('NOTIFICAR_LISTA_ESPERA', ?::jsonb, 'PENDIENTE', ?, ?)",
                payload, user.getUsername(), user.getUsername());

        jdbc.update("UPDATE ades_solicitudes_admision SET estado = 'NOTIFICADO', usuario_modificacion = ? WHERE id = ?",
                user.getUsername(), id);

        return ResponseEntity.ok(Map.of("message", "Notificación encolada para envío por correo"));
    }

    // ── PE-014: Materias optativas ───────────────────────────────────────────

    @Data
    public static class OptativaRequest {
        private UUID estudianteId;
        private UUID materiaId;
        private UUID cicloEscolarId;
    }

    @GetMapping("/optativas")
    public ResponseEntity<List<Map<String, Object>>> listarOptativas(
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @RequestParam(value = "estudiante_id", required = false) UUID estudianteId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder(
                "SELECT io.id, io.fecha_inscripcion, m.nombre_materia, m.clave_materia, " +
                "p.nombre || ' ' || p.apellido_paterno AS alumno, e.matricula " +
                "FROM ades_inscripciones_optativas io " +
                "JOIN ades_materias m ON m.id = io.materia_id " +
                "JOIN ades_estudiantes e ON e.id = io.estudiante_id " +
                "JOIN ades_personas p ON p.id = e.persona_id " +
                "WHERE io.is_active = TRUE "
        );

        List<Object> params = new ArrayList<>();
        if (cicloId != null) {
            sql.append("AND io.ciclo_escolar_id = ? ");
            params.add(cicloId);
        }
        if (estudianteId != null) {
            sql.append("AND io.estudiante_id = ? ");
            params.add(estudianteId);
        }

        sql.append("ORDER BY m.nombre_materia");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/optativas")
    public ResponseEntity<Map<String, Object>> inscribirOptativa(
            @RequestBody OptativaRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        List<Map<String, Object>> mat = jdbc.queryForList(
                "SELECT tipo_materia FROM ades_materias WHERE id = ? AND is_active = TRUE", body.getMateriaId());
        if (mat.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Materia no encontrada");
        }

        List<Map<String, Object>> dup = jdbc.queryForList(
                "SELECT id FROM ades_inscripciones_optativas " +
                "WHERE estudiante_id = ? AND materia_id = ? AND ciclo_escolar_id = ? AND is_active = TRUE",
                body.getEstudianteId(), body.getMateriaId(), body.getCicloEscolarId());
        if (!dup.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El alumno ya está inscrito en esta optativa");
        }

        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_inscripciones_optativas " +
                "(id, estudiante_id, materia_id, ciclo_escolar_id, fecha_inscripcion, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, CURRENT_DATE, ?, ?)",
                id, body.getEstudianteId(), body.getMateriaId(), body.getCicloEscolarId(), user.getUsername(), user.getUsername()
        );

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
        int updated = jdbc.update(
                "UPDATE ades_inscripciones_optativas SET is_active = FALSE, usuario_modificacion = ? WHERE id = ? AND is_active = TRUE",
                user.getUsername(), id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inscripción no encontrada o ya inactiva");
        }
        return ResponseEntity.noContent().build();
    }

    // ── PE-026: Acuerdo de convivencia ───────────────────────────────────────

    @Data
    public static class AcuerdoRequest {
        private UUID alumnoId;
        private String tutorNombre;
        private String tutorFirmaHash;
        private String ipFirma;
    }

    @PostMapping("/acuerdo-convivencia")
    public ResponseEntity<Map<String, Object>> registrarAcuerdo(
            @RequestBody AcuerdoRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_acuerdos_convivencia " +
                "(id, alumno_id, tutor_nombre, tutor_firma_hash, ip_firma, firmado_por_usuario, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, body.getAlumnoId(), body.getTutorNombre(), body.getTutorFirmaHash(), body.getIpFirma(),
                user.getUsername(), user.getUsername(), user.getUsername()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", id.toString(),
                "message", "Acuerdo de convivencia firmado y registrado"
        ));
    }

    // ── AC-005: Calendarios académicos ───────────────────────────────────────

    @Data
    public static class CalendarioRequest {
        private String nombre;
        private UUID cicloEscolarId;
        private String nivelEducativo;
        private String tipo;
        private LocalDate fechaInicio;
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
        AdesUser user = userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder(
                "SELECT id, nombre, tipo, nivel_educativo, fecha_inicio, fecha_fin, " +
                "descripcion, es_oficial, fecha_creacion " +
                "FROM ades_calendarios_academicos WHERE is_active = TRUE "
        );

        List<Object> params = new ArrayList<>();
        if (cicloId != null) {
            sql.append("AND ciclo_escolar_id = ? ");
            params.add(cicloId);
        }
        if (nivelEducativo != null && !nivelEducativo.isBlank()) {
            sql.append("AND nivel_educativo = ? ");
            params.add(nivelEducativo);
        }
        if (tipo != null && !tipo.isBlank()) {
            sql.append("AND tipo = ? ");
            params.add(tipo);
        }

        sql.append("ORDER BY fecha_inicio");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/calendarios-academicos")
    public ResponseEntity<Map<String, Object>> crearEventoCalendario(
            @RequestBody CalendarioRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdminOrHigher(user);

        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_calendarios_academicos " +
                "(id, nombre, ciclo_escolar_id, nivel_educativo, tipo, fecha_inicio, fecha_fin, descripcion, es_oficial, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, body.getNombre(), body.getCicloEscolarId(), body.getNivelEducativo(), body.getTipo(),
                body.getFechaInicio(), body.getFechaFin(), body.getDescripcion(), body.getEsOficial(),
                user.getUsername(), user.getUsername()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Evento de calendario registrado"));
    }

    // ── AC-014: Periodos de evaluación ───────────────────────────────────────

    @Data
    public static class PeriodoEvaluacionRequest {
        private UUID cicloEscolarId;
        private String nombrePeriodo;
        private String nivelEducativo;
        private String tipoEvaluacion;
        private LocalDate fechaInicio;
        private LocalDate fechaFin;
        private Boolean abierto = true;
    }

    @PostMapping("/periodos-evaluacion")
    public ResponseEntity<Map<String, Object>> crearPeriodoEvaluacion(
            @RequestBody PeriodoEvaluacionRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdminOrHigher(user);

        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_periodos_evaluacion " +
                "(id, ciclo_escolar_id, nombre_periodo, nivel_educativo, tipo_evaluacion, fecha_inicio, fecha_fin, abierto, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, body.getCicloEscolarId(), body.getNombrePeriodo(), body.getNivelEducativo(), body.getTipoEvaluacion(),
                body.getFechaInicio(), body.getFechaFin(), body.getAbierto(), user.getUsername(), user.getUsername()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Período de evaluación " + body.getNombrePeriodo() + " creado"));
    }

    @PatchMapping("/periodos-evaluacion/{id}/cerrar")
    public ResponseEntity<Map<String, Object>> cerrarPeriodo(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdminOrHigher(user);

        jdbc.update("UPDATE ades_periodos_evaluacion SET abierto = FALSE, usuario_modificacion = ? WHERE id = ?",
                user.getUsername(), id);
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

        StringBuilder sql = new StringBuilder(
                "SELECT ca.id, ca.nombre, ca.tipo, ca.nivel_educativo, " +
                "ca.fecha_inicio, ca.fecha_fin, ca.descripcion, ca.es_oficial, " +
                "pl.nombre_plantel " +
                "FROM ades_calendarios_academicos ca " +
                "LEFT JOIN ades_planteles pl ON pl.id = ca.plantel_id " +
                "WHERE ca.is_active = TRUE "
        );

        List<Object> params = new ArrayList<>();
        if (cicloId != null) {
            sql.append("AND ca.ciclo_escolar_id = ? ");
            params.add(cicloId);
        }
        if (plantelId != null) {
            sql.append("AND (ca.plantel_id = ? OR ca.plantel_id IS NULL) ");
            params.add(plantelId);
        } else if (user.getPlantelId() != null) {
            sql.append("AND (ca.plantel_id = ? OR ca.plantel_id IS NULL) ");
            params.add(user.getPlantelId());
        }
        if (mes != null) {
            sql.append("AND EXTRACT(MONTH FROM ca.fecha_inicio) = ? ");
            params.add(mes);
        }

        sql.append("ORDER BY ca.fecha_inicio");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        return ResponseEntity.ok(rows);
    }

    // ── PE-003: Evaluación Diagnóstica ───────────────────────────────────────

    @Data
    public static class EvaluacionRequest {
        private Double puntuacionDiagnostico;
        private String observacionesDiagnostico;
    }

    @PatchMapping("/admision/{id}/evaluacion")
    public ResponseEntity<Map<String, Object>> registrarEvaluacionDiagnostica(
            @PathVariable("id") UUID id,
            @RequestBody EvaluacionRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireSecretariaOrHigher(user);

        jdbc.update(
                "UPDATE ades_solicitudes_admision " +
                "SET puntuacion_diagnostico = ?, observaciones_diagnostico = ?, " +
                "estado = 'DIAGNOSTICO', usuario_modificacion = ? " +
                "WHERE id = ?",
                body.getPuntuacionDiagnostico(), body.getObservacionesDiagnostico(), user.getUsername(), id
        );

        return ResponseEntity.ok(Map.of("message", "Evaluación diagnóstica registrada", "estado", "DIAGNOSTICO"));
    }

    // ── PE-005: Generar Carta de Admisión ────────────────────────────────────

    @PostMapping("/admision/{id}/carta")
    public ResponseEntity<byte[]> generarCarta(
            @PathVariable("id") UUID id,
            @RequestParam("template_id") String templateId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        List<Map<String, Object>> sol = jdbc.queryForList(
                "SELECT nombre, apellido_paterno, apellido_materno, curp, estado, " +
                "nivel_solicitado, grado_solicitado, nombre_tutor, fecha_solicitud " +
                "FROM ades_solicitudes_admision WHERE id = ?", id);
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

    // ── PE-020: Workflow de Bajas ────────────────────────────────────────────

    @Data
    public static class BajaRequest {
        private UUID estudianteId;
        private UUID inscripcionId;
        private String tipoBaja;
        private String motivo;
        private LocalDate fechaEfectiva;
        private String observaciones;
    }

    @PostMapping("/bajas")
    public ResponseEntity<Map<String, Object>> registrarBaja(
            @RequestBody BajaRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdminOrHigher(user);

        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_bajas " +
                "(id, estudiante_id, inscripcion_id, tipo_baja, motivo, fecha_efectiva, observaciones, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, body.getEstudianteId(), body.getInscripcionId(), body.getTipoBaja(), body.getMotivo(),
                body.getFechaEfectiva(), body.getObservaciones(), user.getUsername(), user.getUsername()
        );

        jdbc.update("UPDATE ades_estudiantes SET is_active = FALSE, usuario_modificacion = ? WHERE id = ?",
                user.getUsername(), body.getEstudianteId());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", id.toString(),
                "message", "Baja registrada con éxito. Cupo liberado."
        ));
    }

    @GetMapping("/bajas")
    public ResponseEntity<List<Map<String, Object>>> listarBajas(
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireSecretariaOrHigher(user);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT b.id, b.estudiante_id, b.tipo_baja, b.motivo, b.fecha_efectiva, " +
                "p.nombre || ' ' || p.apellido_paterno AS alumno, g.nombre_grupo AS grupo " +
                "FROM ades_bajas b " +
                "JOIN ades_estudiantes e ON e.id = b.estudiante_id " +
                "JOIN ades_personas p ON p.id = e.persona_id " +
                "LEFT JOIN ades_inscripciones i ON i.id = b.inscripcion_id " +
                "LEFT JOIN ades_grupos g ON g.id = i.grupo_id " +
                "ORDER BY b.fecha_creacion DESC"
        );
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/bajas/{baja_id}/reactivar")
    public ResponseEntity<Map<String, Object>> reactivarEstudiante(
            @PathVariable("baja_id") UUID bajaId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdminOrHigher(user);

        List<Map<String, Object>> baja = jdbc.queryForList(
                "SELECT estudiante_id, inscripcion_id FROM ades_bajas WHERE id = ?", bajaId);
        if (baja.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro de baja no encontrado");
        }
        Map<String, Object> bm = baja.get(0);
        UUID estudianteId = (UUID) bm.get("estudiante_id");
        UUID inscripcionId = (UUID) bm.get("inscripcion_id");

        jdbc.update("UPDATE ades_estudiantes SET is_active = TRUE, usuario_modificacion = ? WHERE id = ?",
                user.getUsername(), estudianteId);

        if (inscripcionId != null) {
            List<Map<String, Object>> ins = jdbc.queryForList(
                    "SELECT grupo_id FROM ades_inscripciones WHERE id = ?", inscripcionId);
            if (!ins.isEmpty()) {
                UUID gid = (UUID) ins.get(0).get("grupo_id");
                List<Map<String, Object>> cap = jdbc.queryForList(
                        "SELECT capacidad_maxima, " +
                        "(SELECT COUNT(*) FROM ades_inscripciones WHERE grupo_id = ? AND is_active = TRUE) AS inscritos " +
                        "FROM ades_grupos WHERE id = ?", gid, gid);
                if (!cap.isEmpty()) {
                    int max = ((Number) cap.get(0).get("capacidad_maxima")).intValue();
                    int cur = ((Number) cap.get(0).get("inscritos")).intValue();
                    if (cur >= max) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El grupo asignado original está lleno. No se puede reactivar la inscripción.");
                    }

                    jdbc.update("UPDATE ades_inscripciones SET is_active = TRUE, usuario_modificacion = ? WHERE id = ?",
                            user.getUsername(), inscripcionId);
                }
            }
        }

        jdbc.update("UPDATE ades_bajas SET is_active = FALSE WHERE id = ?", bajaId);

        return ResponseEntity.ok(Map.of("ok", true, "message", "Estudiante reactivado con éxito"));
    }
}
