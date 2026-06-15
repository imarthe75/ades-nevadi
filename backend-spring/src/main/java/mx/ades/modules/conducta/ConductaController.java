package mx.ades.modules.conducta;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.conducta.domain.model.TipoSancion;
import mx.ades.modules.conducta.domain.port.in.AplicarSancionCommand;
import mx.ades.modules.conducta.domain.port.in.AplicarSancionUseCase;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/conducta")
@RequiredArgsConstructor
public class ConductaController {

    private final ReporteConductaRepository repository;
    private final SancionDisciplinariaRepository sancionRepository;
    private final PlanMejoraRepository planRepository;
    private final SeguimientoPlanRepository seguimientoRepository;
    private final AdesUserService userService;
    private final JdbcTemplate jdbc;
    private final AplicarSancionUseCase aplicarSancion;

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
        private String tipoSancion;
        private String justificacion;
        private UUID autorizadoPorId;
        private LocalDate fechaSancion;
        private LocalDate fechaFinSancion;
        private Boolean notificadoPadres = false;
        private LocalDate fechaNotificacion;
        private String medioNotificacion;
        private String notasAdicionales;
    }

    @Data
    public static class PlanMejoraCreateRequest {
        private UUID elaboradoPorId;
        private String objetivoGeneral;
        private List<Map<String, Object>> compromisosAlumno;
        private List<Map<String, Object>> compromisosPadre;
        private List<Map<String, Object>> compromisosEscuela;
        private LocalDate fechaPrimerSeguimiento;
        private UUID cicloEscolarId;
    }

    @Data
    public static class SeguimientoRequest {
        private UUID registradoPorId;
        private LocalDate fechaSeguimiento;
        private String avance = "PARCIAL";
        private String descripcion;
        private List<Map<String, Object>> compromisosCumplidos;
        private String accionesAdicionales;
        private String nuevoEstadoPlan;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(value = "estudiante_id", required = false) UUID estudianteId,
            @RequestParam(value = "grupo_id", required = false) UUID grupoId,
            @RequestParam(value = "tipo_falta", required = false) String tipoFalta,
            @RequestParam(value = "requiere_seguimiento", required = false) Boolean requiereSeguimiento,
            @RequestParam(value = "pagina", defaultValue = "1") int pagina,
            @RequestParam(value = "por_pagina", defaultValue = "20") int porPagina) {

        StringBuilder query = new StringBuilder(
                "SELECT rc.id, rc.estudiante_id, rc.grupo_id, rc.reportado_por_id, rc.fecha_reporte, " +
                "rc.tipo_falta, rc.descripcion, rc.medida_aplicada, rc.requiere_seguimiento, " +
                "p.nombre || ' ' || p.apellido_paterno AS nombre_alumno, " +
                "u.nombre_usuario AS reportado_por_nombre " +
                "FROM ades_reportes_conducta rc " +
                "JOIN ades_estudiantes e ON e.id = rc.estudiante_id " +
                "JOIN ades_personas p ON p.id = e.persona_id " +
                "JOIN ades_usuarios u ON u.id = rc.reportado_por_id " +
                "WHERE rc.is_active = TRUE ");

        List<Object> params = new ArrayList<>();

        if (estudianteId != null) {
            query.append("AND rc.estudiante_id = ? ");
            params.add(estudianteId);
        }
        if (grupoId != null) {
            query.append("AND rc.grupo_id = ? ");
            params.add(grupoId);
        }
        if (tipoFalta != null && !tipoFalta.isBlank()) {
            query.append("AND rc.tipo_falta = ? ");
            params.add(tipoFalta.toUpperCase());
        }
        if (requiereSeguimiento != null) {
            query.append("AND rc.requiere_seguimiento = ? ");
            params.add(requiereSeguimiento);
        }

        int offset = (pagina - 1) * porPagina;
        query.append("ORDER BY rc.fecha_reporte DESC LIMIT ? OFFSET ?");
        params.add(porPagina);
        params.add(offset);

        return ResponseEntity.ok(jdbc.queryForList(query.toString(), params.toArray()));
    }

    @GetMapping("/alumno/{estudianteId}/historial")
    public ResponseEntity<List<Map<String, Object>>> historial(@PathVariable("estudianteId") UUID estudianteId) {
        String sql = "SELECT rc.id, rc.fecha_reporte, rc.tipo_falta, rc.descripcion, rc.medida_aplicada, rc.requiere_seguimiento, " +
                "sd.id AS sancion_id, sd.tipo_sancion, sd.estado AS estado_sancion, sd.fecha_sancion, sd.notificado_padres, " +
                "pm.id AS plan_id, pm.estado AS estado_plan, pm.fecha_elaboracion, pm.objetivo_general " +
                "FROM ades_reportes_conducta rc " +
                "LEFT JOIN ades_sanciones_disciplinarias sd ON sd.reporte_conducta_id = rc.id AND sd.is_active = TRUE " +
                "LEFT JOIN ades_planes_mejora pm ON pm.reporte_conducta_id = rc.id AND pm.is_active = TRUE " +
                "WHERE rc.estudiante_id = ? AND rc.is_active = TRUE " +
                "ORDER BY rc.fecha_reporte DESC";

        return ResponseEntity.ok(jdbc.queryForList(sql, estudianteId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReporteConducta> obtener(@PathVariable("id") UUID id) {
        ReporteConducta rc = repository.findById(id)
                .filter(ReporteConducta::getIsActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reporte no encontrado"));
        return ResponseEntity.ok(rc);
    }

    @GetMapping("/{id}/detalle-completo")
    public ResponseEntity<Map<String, Object>> detalleCompleto(@PathVariable("id") UUID id) {
        String reporteSql = "SELECT rc.*, p.nombre || ' ' || p.apellido_paterno AS nombre_alumno, est.matricula " +
                "FROM ades_reportes_conducta rc " +
                "JOIN ades_estudiantes est ON est.id = rc.estudiante_id " +
                "JOIN ades_personas p ON p.id = est.persona_id " +
                "WHERE rc.id = ? AND rc.is_active = TRUE";

        List<Map<String, Object>> reportes = jdbc.queryForList(reporteSql, id);
        if (reportes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reporte no encontrado");
        }
        Map<String, Object> rc = reportes.get(0);

        Map<String, Object> sancion = null;
        String sancionSql = "SELECT sd.*, u.nombre_usuario AS autorizado_por_nombre " +
                "FROM ades_sanciones_disciplinarias sd " +
                "JOIN ades_usuarios u ON u.id = sd.autorizado_por_id " +
                "WHERE sd.reporte_conducta_id = ? AND sd.is_active = TRUE LIMIT 1";
        List<Map<String, Object>> sanciones = jdbc.queryForList(sancionSql, id);
        if (!sanciones.isEmpty()) {
            sancion = sanciones.get(0);
        }

        Map<String, Object> plan = null;
        String planSql = "SELECT pm.*, u.nombre_usuario AS elaborado_por_nombre " +
                "FROM ades_planes_mejora pm " +
                "JOIN ades_usuarios u ON u.id = pm.elaborado_por_id " +
                "WHERE pm.reporte_conducta_id = ? AND pm.is_active = TRUE LIMIT 1";
        List<Map<String, Object>> planes = jdbc.queryForList(planSql, id);
        if (!planes.isEmpty()) {
            plan = planes.get(0);
        }

        List<Map<String, Object>> seguimientos = new ArrayList<>();
        if (plan != null) {
            UUID planId = (UUID) plan.get("id");
            String segSql = "SELECT sp.*, u.nombre_usuario AS registrado_por_nombre " +
                    "FROM ades_seguimiento_plan sp " +
                    "JOIN ades_usuarios u ON u.id = sp.registrado_por_id " +
                    "WHERE sp.plan_mejora_id = ? AND sp.is_active = TRUE " +
                    "ORDER BY sp.fecha_seguimiento DESC";
            seguimientos = jdbc.queryForList(segSql, planId);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("reporte", rc);
        response.put("sancion", sancion);
        response.put("plan_mejora", plan);
        response.put("seguimientos", seguimientos);

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ReporteConducta> crear(@RequestBody ReporteCreateRequest body) {
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
            @RequestBody ReporteCreateRequest body) {

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
            @RequestBody SancionCreateRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        int nivelAcceso = user.getNivelAcceso() != null ? user.getNivelAcceso() : 99;

        UUID sancionId = aplicarSancion.ejecutar(new AplicarSancionCommand(
                reporteId,
                TipoSancion.valueOf(body.getTipoSancion()),
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

        sancionRepository.save(sd);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/{reporteId}/plan-mejora")
    public ResponseEntity<PlanMejora> crearPlanMejora(
            @PathVariable("reporteId") UUID reporteId,
            @RequestBody PlanMejoraCreateRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo COORDINADOR/DIRECTOR/ADMIN puede crear planes de mejora");
        }

        ReporteConducta rc = repository.findById(reporteId)
                .filter(ReporteConducta::getIsActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reporte no encontrado"));

        Optional<PlanMejora> existing = planRepository.findByReporteConductaIdAndIsActiveTrue(reporteId);
        if (existing.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este reporte ya tiene un plan de mejora activo");
        }

        PlanMejora pm = new PlanMejora();
        pm.setReporteConductaId(reporteId);
        pm.setEstudianteId(rc.getEstudianteId());
        pm.setCicloEscolarId(body.getCicloEscolarId());
        pm.setElaboradoPorId(body.getElaboradoPorId());
        pm.setObjetivoGeneral(body.getObjetivoGeneral());
        pm.setCompromisosAlumno(body.getCompromisosAlumno());
        pm.setCompromisosPadre(body.getCompromisosPadre());
        pm.setCompromisosEscuela(body.getCompromisosEscuela());
        pm.setFechaPrimerSeguimiento(body.getFechaPrimerSeguimiento());

        return ResponseEntity.status(HttpStatus.CREATED).body(planRepository.save(pm));
    }

    @PatchMapping("/{reporteId}/plan-mejora/{planId}")
    public ResponseEntity<Map<String, Object>> actualizarPlanMejora(
            @PathVariable("reporteId") UUID reporteId,
            @PathVariable("planId") UUID planId,
            @RequestBody PlanMejora pmUpdate,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permiso para actualizar planes de mejora");
        }

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
        if (pmUpdate.getEstado() != null) pm.setEstado(pmUpdate.getEstado());
        if (pmUpdate.getObservacionesCierre() != null) pm.setObservacionesCierre(pmUpdate.getObservacionesCierre());

        planRepository.save(pm);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/{reporteId}/plan-mejora/{planId}/seguimiento")
    public ResponseEntity<SeguimientoPlan> agregarSeguimiento(
            @PathVariable("reporteId") UUID reporteId,
            @PathVariable("planId") UUID planId,
            @RequestBody SeguimientoRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permiso para registrar seguimientos");
        }

        PlanMejora pm = planRepository.findById(planId)
                .filter(p -> p.getReporteConductaId().equals(reporteId) && p.getIsActive())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan no encontrado"));

        SeguimientoPlan sp = new SeguimientoPlan();
        sp.setPlanMejoraId(planId);
        sp.setEstudianteId(pm.getEstudianteId());
        sp.setRegistradoPorId(body.getRegistradoPorId());
        sp.setFechaSeguimiento(body.getFechaSeguimiento() != null ? body.getFechaSeguimiento() : LocalDate.now());
        sp.setAvance(body.getAvance());
        sp.setDescripcion(body.getDescripcion());
        sp.setCompromisosCumplidos(body.getCompromisosCumplidos());
        sp.setAccionesAdicionales(body.getAccionesAdicionales());
        sp.setNuevoEstadoPlan(body.getNuevoEstadoPlan());

        SeguimientoPlan saved = seguimientoRepository.save(sp);

        if (body.getNuevoEstadoPlan() != null && !body.getNuevoEstadoPlan().isBlank()) {
            pm.setEstado(body.getNuevoEstadoPlan());
            planRepository.save(pm);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
