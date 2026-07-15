package mx.ades.modules.conducta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.conducta.domain.model.AvanceSeguimiento;
import mx.ades.modules.conducta.domain.model.EstadoPlan;
import mx.ades.modules.conducta.domain.model.TipoSancion;
import mx.ades.modules.conducta.domain.port.in.AplicarSancionCommand;
import mx.ades.modules.conducta.domain.port.in.AplicarSancionUseCase;
import mx.ades.modules.conducta.domain.port.in.CrearPlanMejoraUseCase;
import mx.ades.modules.conducta.query.ConductaQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

/**
 * Adaptador REST para el módulo de conducta y disciplina escolar.
 * Expone endpoints bajo /api/v1/conducta para crear y gestionar reportes de conducta,
 * aplicar sanciones disciplinarias (LEVE/GRAVE/MUY_GRAVE via {@code TipoSancion}),
 * elaborar planes de mejora y registrar seguimientos de dichos planes.
 * Las operaciones de sanción verifican nivelAcceso; la creación/modificación de
 * planes de mejora requiere Coordinador o superior (nivelAcceso {@literal <=} 3).
 * Incluye endpoints de historial por alumno y detalle completo del reporte.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/conducta")
@RequiredArgsConstructor
public class ConductaController {

    private final ReporteConductaRepository repository;
    private final SancionDisciplinariaRepository sancionRepository;
    private final PlanMejoraRepository planRepository;
    private final SeguimientoPlanRepository seguimientoRepository;
    private final AdesUserService userService;
    private final AplicarSancionUseCase aplicarSancion;
    private final CrearPlanMejoraUseCase crearPlanMejora;
    private final ConductaQueryService queryService;
    private final RiesgoConductualService riesgoConductualService;
    private final RestClient restClient = RestClient.builder().build();

    private static final String API_BASE_URL = "http://ades-api:8000/api/v1/conducta";

    /**
     * Reportar/editar conducta es operación de personal escolar (nivelAcceso &le;4:
     * admin/director/coordinador/docente). Padres/alumnos (nivelAcceso &gt;=5) no
     * pueden crear ni modificar reportes de conducta — previene BFLA (OWASP API5).
     */
    private void requireStaff(AdesUser user) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso == null || nivelAcceso > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
    }

    @Data
    public static class ReporteCreateRequest {
        private UUID estudianteId;
        private UUID grupoId;
        private UUID reportadoPorId;
        private LocalDate fechaReporte;
        private String tipoFalta;
        private String descripcion;
        private String medidaAplicada;
        private Boolean requiereSeguimiento = false;
    }

    @Data
    public static class SancionCreateRequest {
        @NotBlank(message = "tipoSancion es obligatorio")
        private String tipoSancion;

        @NotBlank(message = "justificacion es obligatoria")
        @Size(min = 20, message = "justificacion debe tener al menos 20 caracteres")
        private String justificacion;

        @NotNull(message = "autorizadoPorId es obligatorio")
        private UUID autorizadoPorId;

        private LocalDate fechaSancion;
        private LocalDate fechaFinSancion;
        private Boolean notificadoPadres = false;
        private LocalDate fechaNotificacion;
        private String medioNotificacion;
        private String notasAdicionales;
        // Hallazgo 2026-07-15: el frontend (conducta.component.ts#actualizarSancion) ya
        // enviaba este campo desde antes, pero el DTO no lo tenía — se descartaba
        // silenciosamente y el estado de la sanción nunca se actualizaba pese al toast
        // de éxito. Ver ades_sanciones_disciplinarias_estado_check para los valores reales.
        private String estado;
    }

    private static final Set<String> ESTADOS_SANCION_VALIDOS =
            Set.of("APLICADA", "EN_PROCESO", "CUMPLIDA", "APELADA", "REVOCADA");

    @Data
    public static class PlanMejoraCreateRequest {
        @NotNull(message = "elaboradoPorId es obligatorio")
        private UUID elaboradoPorId;

        @NotBlank(message = "objetivoGeneral es obligatorio")
        @Size(min = 20, message = "objetivoGeneral debe tener al menos 20 caracteres")
        private String objetivoGeneral;

        private List<Map<String, Object>> compromisosAlumno;
        private List<Map<String, Object>> compromisosPadre;
        private List<Map<String, Object>> compromisosEscuela;
        private LocalDate fechaPrimerSeguimiento;
        private UUID cicloEscolarId;
    }

    @Data
    public static class SeguimientoRequest {
        @NotNull(message = "registradoPorId es obligatorio")
        private UUID registradoPorId;

        private LocalDate fechaSeguimiento;
        private String avance = "PARCIAL";

        @NotBlank(message = "descripcion es obligatoria")
        @Size(min = 20, message = "descripcion debe tener al menos 20 caracteres")
        private String descripcion;

        private List<Map<String, Object>> compromisosCumplidos;
        private String accionesAdicionales;
        private String nuevoEstadoPlan;
    }

    // ── Reads (delegados a ConductaQueryService) ─────────────────────────────

    // NOTA: los 4 GET siguientes no llamaban a resolveUser(jwt) — quedaban accesibles
    // a CUALQUIER cuenta autenticada del sistema (incluyendo padres/alumnos, nivelAcceso
    // >=5) sin ninguna verificación de rol ni scoping por plantel, exponiendo reportes
    // de conducta, sanciones disciplinarias y planes de mejora de cualquier alumno.
    // Se restringe a personal escolar (mismo criterio que requireStaff() para escritura)
    // para prevenir BOLA/BFLA (OWASP API1/API5) sobre datos conductuales sensibles.

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "nivel_id", required = false) UUID nivelId,
            @RequestParam(value = "grado_id", required = false) UUID gradoId,
            @RequestParam(value = "estudiante_id", required = false) UUID estudianteId,
            @RequestParam(value = "grupo_id", required = false) UUID grupoId,
            @RequestParam(value = "tipo_falta", required = false) String tipoFalta,
            @RequestParam(value = "requiere_seguimiento", required = false) Boolean requiereSeguimiento,
            @RequestParam(value = "pagina", defaultValue = "1") int pagina,
            @RequestParam(value = "por_pagina", defaultValue = "20") int porPagina,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        UUID plantelFiltro = (user.getNivelAcceso() != null && user.getNivelAcceso() > 1 && user.getPlantelId() != null)
                ? user.getPlantelId() : plantelId;
        return ResponseEntity.ok(
                queryService.listar(plantelFiltro, nivelId, gradoId, grupoId, estudianteId, tipoFalta, requiereSeguimiento, pagina, porPagina));
    }

    @GetMapping("/alumno/{estudianteId}/historial")
    public ResponseEntity<List<Map<String, Object>>> historial(
            @PathVariable("estudianteId") UUID estudianteId,
            @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
        return ResponseEntity.ok(queryService.historial(estudianteId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReporteConducta> obtener(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
        ReporteConducta rc = repository.findById(id)
                .filter(ReporteConducta::getIsActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reporte no encontrado"));
        return ResponseEntity.ok(rc);
    }

    @GetMapping("/{id}/detalle-completo")
    public ResponseEntity<Map<String, Object>> detalleCompleto(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
        Map<String, Object> result = queryService.detalleCompleto(id);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reporte no encontrado");
        return ResponseEntity.ok(result);
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ReporteConducta> crear(@RequestBody ReporteCreateRequest body, @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
        // estudiante_id, grupo_id, reportado_por_id, tipo_falta y descripcion son NOT NULL
        // en ades_reportes_conducta (sin default). Antes de este fix el repository.save()
        // llegaba con campos faltantes hasta el flush de Hibernate y salía como
        // DataIntegrityViolationException -> 409 genérico en vez de un 422 claro.
        if (body.getEstudianteId() == null)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "estudianteId es obligatorio");
        if (body.getGrupoId() == null)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "grupoId es obligatorio");
        if (body.getReportadoPorId() == null)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "reportadoPorId es obligatorio");
        if (body.getTipoFalta() == null || body.getTipoFalta().isBlank())
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "tipoFalta es obligatorio");
        if (body.getDescripcion() == null || body.getDescripcion().isBlank())
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "descripcion es obligatoria");
        ReporteConducta rc = new ReporteConducta();
        rc.setEstudianteId(body.getEstudianteId());
        rc.setGrupoId(body.getGrupoId());
        rc.setReportadoPorId(body.getReportadoPorId());
        if (body.getFechaReporte() != null) rc.setFechaReporte(body.getFechaReporte());
        rc.setTipoFalta(body.getTipoFalta());
        rc.setDescripcion(body.getDescripcion());
        rc.setMedidaAplicada(body.getMedidaAplicada());
        rc.setRequiereSeguimiento(body.getRequiereSeguimiento());
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(rc));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ReporteConducta> actualizar(
            @PathVariable("id") UUID id,
            @RequestBody ReporteCreateRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        requireStaff(userService.resolveUser(jwt));
        ReporteConducta rc = repository.findById(id)
                .filter(ReporteConducta::getIsActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reporte no encontrado"));

        if (body.getDescripcion() != null) rc.setDescripcion(body.getDescripcion());
        if (body.getMedidaAplicada() != null) rc.setMedidaAplicada(body.getMedidaAplicada());
        if (body.getRequiereSeguimiento() != null) rc.setRequiereSeguimiento(body.getRequiereSeguimiento());
        if (body.getTipoFalta() != null) rc.setTipoFalta(body.getTipoFalta());
        return ResponseEntity.ok(repository.save(rc));
    }

    @PostMapping("/{reporteId}/sancion")
    public ResponseEntity<Map<String, Object>> aplicarSancion(
            @PathVariable("reporteId") UUID reporteId,
            @RequestBody @Valid SancionCreateRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        int nivelAcceso = user.getNivelAcceso() != null ? user.getNivelAcceso() : 99;

        UUID sancionId = aplicarSancion.ejecutar(new AplicarSancionCommand(
                reporteId,
                parseTipoSancion(body.getTipoSancion()),
                body.getJustificacion(),
                body.getAutorizadoPorId(),
                body.getFechaSancion(),
                body.getFechaFinSancion(),
                Boolean.TRUE.equals(body.getNotificadoPadres()),
                body.getFechaNotificacion(),
                body.getMedioNotificacion(),
                body.getNotasAdicionales(),
                nivelAcceso
        ));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("sancion_id", sancionId, "ok", true));
    }

    @PatchMapping("/{reporteId}/sancion/{sancionId}")
    public ResponseEntity<Map<String, Object>> actualizarSancion(
            @PathVariable("reporteId") UUID reporteId,
            @PathVariable("sancionId") UUID sancionId,
            @RequestBody SancionCreateRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permiso para actualizar sanciones");
        }

        SancionDisciplinaria sd = sancionRepository.findById(sancionId)
                .filter(s -> s.getReporteConductaId().equals(reporteId) && s.getIsActive())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sanción no encontrada"));

        if (body.getTipoSancion() != null) sd.setTipoSancion(body.getTipoSancion());
        if (body.getJustificacion() != null) sd.setJustificacion(body.getJustificacion());
        if (body.getNotificadoPadres() != null) sd.setNotificadoPadres(body.getNotificadoPadres());
        if (body.getFechaNotificacion() != null) sd.setFechaNotificacion(body.getFechaNotificacion());
        if (body.getMedioNotificacion() != null) sd.setMedioNotificacion(body.getMedioNotificacion());
        if (body.getNotasAdicionales() != null) sd.setNotasAdicionales(body.getNotasAdicionales());
        if (body.getEstado() != null) {
            if (!ESTADOS_SANCION_VALIDOS.contains(body.getEstado()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estado inválido: " + body.getEstado());
            sd.setEstado(body.getEstado());
        }

        sancionRepository.save(sd);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/{reporteId}/plan-mejora")
    public ResponseEntity<Map<String, Object>> crearPlanMejora(
            @PathVariable("reporteId") UUID reporteId,
            @RequestBody @Valid PlanMejoraCreateRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        requireNivel(user, 3);

        UUID planId = crearPlanMejora.ejecutar(new CrearPlanMejoraUseCase.Command(
                reporteId,
                resolveEstudianteId(reporteId),
                body.getCicloEscolarId(),
                body.getElaboradoPorId(),
                body.getObjetivoGeneral(),
                body.getCompromisosAlumno(),
                body.getCompromisosPadre(),
                body.getCompromisosEscuela(),
                body.getFechaPrimerSeguimiento(),
                user.getUsername()
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", planId, "ok", true));
    }

    @PatchMapping("/{reporteId}/plan-mejora/{planId}")
    public ResponseEntity<Map<String, Object>> actualizarPlanMejora(
            @PathVariable("reporteId") UUID reporteId,
            @PathVariable("planId") UUID planId,
            @RequestBody PlanMejora pmUpdate,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        requireNivel(user, 3);

        PlanMejora pm = planRepository.findById(planId)
                .filter(p -> p.getReporteConductaId().equals(reporteId) && p.getIsActive())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan no encontrado"));

        if (pmUpdate.getObjetivoGeneral() != null) pm.setObjetivoGeneral(pmUpdate.getObjetivoGeneral());
        if (pmUpdate.getCompromisosAlumno() != null) pm.setCompromisosAlumno(pmUpdate.getCompromisosAlumno());
        if (pmUpdate.getCompromisosPadre() != null) pm.setCompromisosPadre(pmUpdate.getCompromisosPadre());
        if (pmUpdate.getCompromisosEscuela() != null) pm.setCompromisosEscuela(pmUpdate.getCompromisosEscuela());
        if (pmUpdate.getFirmadoAlumno() != null) pm.setFirmadoAlumno(pmUpdate.getFirmadoAlumno());
        if (pmUpdate.getFirmadoPadre() != null) pm.setFirmadoPadre(pmUpdate.getFirmadoPadre());
        if (pmUpdate.getFirmadoDirector() != null) pm.setFirmadoDirector(pmUpdate.getFirmadoDirector());
        if (pmUpdate.getFechaFirmaAlumno() != null) pm.setFechaFirmaAlumno(pmUpdate.getFechaFirmaAlumno());
        if (pmUpdate.getFechaFirmaPadre() != null) pm.setFechaFirmaPadre(pmUpdate.getFechaFirmaPadre());
        if (pmUpdate.getFechaPrimerSeguimiento() != null) pm.setFechaPrimerSeguimiento(pmUpdate.getFechaPrimerSeguimiento());
        // CHECK ades_planes_mejora.estado: solo ACTIVO/EN_PROCESO/CUMPLIDO/INCUMPLIDO/
        // CANCELADO. Antes no se validaba aquí — un valor arbitrario llegaba hasta el
        // UPDATE y disparaba la violación del CHECK a nivel BD (409 engañoso) en vez
        // de un 400 claro (hallazgo de auditoría de consistencia BD↔backend).
        if (pmUpdate.getEstado() != null) pm.setEstado(EstadoPlan.of(pmUpdate.getEstado()).name());
        if (pmUpdate.getObservacionesCierre() != null) pm.setObservacionesCierre(pmUpdate.getObservacionesCierre());

        planRepository.save(pm);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/{reporteId}/plan-mejora/{planId}/seguimiento")
    public ResponseEntity<SeguimientoPlan> agregarSeguimiento(
            @PathVariable("reporteId") UUID reporteId,
            @PathVariable("planId") UUID planId,
            @RequestBody @Valid SeguimientoRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        requireNivel(user, 3);

        PlanMejora pm = planRepository.findById(planId)
                .filter(p -> p.getReporteConductaId().equals(reporteId) && p.getIsActive())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan no encontrado"));

        // CHECK ades_seguimiento_plan.avance: solo SIN_AVANCE/PARCIAL/SATISFACTORIO/
        // EXCELENTE. CHECK ades_seguimiento_plan.nuevo_estado_plan: NULL o uno de los
        // 5 estados de EstadoPlan. Ninguno se validaba antes de este fix — un valor
        // arbitrario llegaba hasta el INSERT/UPDATE y disparaba la violación del CHECK
        // a nivel BD (409 engañoso) en vez de un 400 claro (hallazgo de auditoría).
        String avanceValidado = AvanceSeguimiento.of(body.getAvance()).name();
        String nuevoEstadoValidado = (body.getNuevoEstadoPlan() != null && !body.getNuevoEstadoPlan().isBlank())
                ? EstadoPlan.of(body.getNuevoEstadoPlan()).name()
                : null;

        SeguimientoPlan sp = new SeguimientoPlan();
        sp.setPlanMejoraId(planId);
        sp.setEstudianteId(pm.getEstudianteId());
        sp.setRegistradoPorId(body.getRegistradoPorId());
        sp.setFechaSeguimiento(body.getFechaSeguimiento() != null ? body.getFechaSeguimiento() : LocalDate.now());
        sp.setAvance(avanceValidado);
        sp.setDescripcion(body.getDescripcion());
        sp.setCompromisosCumplidos(body.getCompromisosCumplidos());
        sp.setAccionesAdicionales(body.getAccionesAdicionales());
        sp.setNuevoEstadoPlan(nuevoEstadoValidado);

        SeguimientoPlan saved = seguimientoRepository.save(sp);

        if (nuevoEstadoValidado != null) {
            pm.setEstado(nuevoEstadoValidado);
            planRepository.save(pm);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void requireNivel(AdesUser user, int maxNivel) {
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > maxNivel) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo COORDINADOR/DIRECTOR/ADMIN puede realizar esta acción");
        }
    }

    /**
     * Convierte el {@code tipoSancion} recibido en el enum {@link TipoSancion}.
     * Antes del fix de Jackson, este valor siempre llegaba {@code null} y
     * {@code TipoSancion.valueOf(null)} lanzaba una {@link NullPointerException} cruda
     * (500 genérico). Ahora que el dato llega real, un valor no reconocido debe
     * responder 400 con un mensaje claro en vez de propagar la excepción del enum.
     */
    private TipoSancion parseTipoSancion(String tipoSancion) {
        try {
            return TipoSancion.valueOf(tipoSancion);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "tipoSancion inválido: '" + tipoSancion + "'. Valores permitidos: "
                            + Arrays.toString(TipoSancion.values()));
        }
    }

    private UUID resolveEstudianteId(UUID reporteId) {
        return repository.findById(reporteId)
                .filter(ReporteConducta::getIsActive)
                .map(ReporteConducta::getEstudianteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reporte no encontrado"));
    }

    // ── SB-016: Análisis de patrones de conducta (riesgo) ─────────────────────

    // BOLA fix: a diferencia de calcularRiesgo()/recalcularRiesgoGrupo() (ambos con
    // requireNivel), este GET solo llamaba a resolveUser() — cualquier cuenta autenticada
    // (incluido un alumno/padre) podía consultar el riesgo conductual de CUALQUIER alumno.
    // Se alinea con requireStaff(), mismo criterio que el resto del archivo.
    @GetMapping("/riesgo/{estudianteId}")
    public ResponseEntity<Map<String, Object>> riesgoConductual(
            @PathVariable UUID estudianteId,
            @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
        return ResponseEntity.ok(riesgoConductualService.obtenerUltimo(estudianteId));
    }

    @PostMapping("/riesgo/{estudianteId}/calcular")
    public ResponseEntity<Map<String, Object>> calcularRiesgo(
            @PathVariable UUID estudianteId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireNivel(user, 4);
        return ResponseEntity.ok(riesgoConductualService.calcular(estudianteId));
    }

    @PostMapping("/riesgo/grupo/{grupoId}/recalcular")
    public ResponseEntity<List<Map<String, Object>>> recalcularRiesgoGrupo(
            @PathVariable UUID grupoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireNivel(user, 3);
        return ResponseEntity.ok(riesgoConductualService.recalcularGrupo(grupoId));
    }

    // ── SB-017: Acta de evaluación de conducta en PDF ─────────────────────────

    @GetMapping("/{reporteId}/acta-pdf")
    public ResponseEntity<byte[]> descargarActaConducta(
            @PathVariable UUID reporteId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireNivel(user, 3);
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get().uri(API_BASE_URL + "/" + reporteId + "/acta-pdf");
            if (authHeader != null) request.header(HttpHeaders.AUTHORIZATION, authHeader);
            ResponseEntity<byte[]> response = request.retrieve().toEntity(byte[].class);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header("Content-Disposition", "attachment; filename=acta_conducta_" + reporteId + ".pdf")
                    .body(response.getBody());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al generar el acta: " + e.getMessage());
        }
    }
}
