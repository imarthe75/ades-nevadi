package mx.ades.modules.licencias;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/licencias")
@RequiredArgsConstructor
public class LicenciaPersonalController {

    private final LicenciaPersonalRepository repository;
    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    private static final Set<String> TIPOS = Set.of(
            "MEDICA", "MATERNIDAD", "PATERNIDAD", "DUELO", "PERSONAL", "COMISION", "CAPACITACION", "OTRO"
    );

    @Data
    public static class LicenciaCreateRequest {
        private UUID personalId;
        private String tipoLicencia;
        private LocalDate fechaInicio;
        private LocalDate fechaFin;
        private String motivo;
        private UUID sustitutoId;
        private Boolean conGoceSueldo = true;
    }

    private int calcularDiasHabiles(LocalDate inicio, LocalDate fin) {
        int dias = 0;
        LocalDate current = inicio;
        while (!current.isAfter(fin)) {
            DayOfWeek dow = current.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                dias++;
            }
            current = current.plusDays(1);
        }
        return Math.max(dias, 1);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(value = "personal_id", required = false) UUID personalId,
            @RequestParam(value = "estado", required = false) String estado,
            @RequestParam(value = "tipo", required = false) String tipo,
            @RequestParam(value = "q", required = false) String q,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder query = new StringBuilder(
            "SELECT lp.*, " +
            "  CONCAT(pe.nombre, ' ', pe.apellido_paterno, CASE WHEN pe.apellido_materno IS NOT NULL THEN CONCAT(' ', pe.apellido_materno) ELSE '' END) AS nombre_completo, " +
            "  pe.nombre AS nombre_persona, pe.apellido_paterno, pe.apellido_materno, " +
            "  pr.numero_empleado " +
            "FROM public.ades_licencias_personal lp " +
            "LEFT JOIN ades_profesores pr ON pr.id = lp.personal_id " +
            "LEFT JOIN ades_personas pe ON pe.id = pr.persona_id " +
            "WHERE lp.is_active = TRUE ");
        List<Object> params = new ArrayList<>();

        if (personalId != null) {
            query.append("AND lp.personal_id = ? ");
            params.add(personalId);
        }
        if (q != null && !q.isBlank()) {
            query.append("AND (pe.nombre ILIKE ? OR pe.apellido_paterno ILIKE ? OR CONCAT(pe.nombre,' ',pe.apellido_paterno) ILIKE ?) ");
            String like = "%" + q.trim() + "%";
            params.add(like); params.add(like); params.add(like);
        }
        if (estado != null && !estado.isBlank()) {
            query.append("AND lp.estado = ? ");
            params.add(estado.toUpperCase());
        }
        if (tipo != null && !tipo.isBlank()) {
            query.append("AND lp.tipo_licencia = ? ");
            params.add(tipo.toUpperCase());
        }

        query.append("ORDER BY lp.fecha_creacion DESC");

        return ResponseEntity.ok(jdbc.queryForList(query.toString(), params.toArray()));
    }

    @PostMapping
    public ResponseEntity<LicenciaPersonal> crear(
            @RequestBody LicenciaCreateRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        if (body.getFechaFin().isBefore(body.getFechaInicio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fecha_fin debe ser >= fecha_inicio");
        }
        if (body.getTipoLicencia() == null || !TIPOS.contains(body.getTipoLicencia().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tipo_licencia inválido. Opciones: " + TIPOS);
        }

        int dias = calcularDiasHabiles(body.getFechaInicio(), body.getFechaFin());

        LicenciaPersonal lp = new LicenciaPersonal();
        lp.setPersonalId(body.getPersonalId());
        lp.setTipoLicencia(body.getTipoLicencia().toUpperCase());
        lp.setFechaInicio(body.getFechaInicio());
        lp.setFechaFin(body.getFechaFin());
        lp.setDiasHabiles(dias);
        lp.setMotivo(body.getMotivo());
        lp.setSustitutoId(body.getSustitutoId());
        lp.setConGoceSueldo(body.getConGoceSueldo());
        lp.setUsuarioCreacion(user.getUsername());
        lp.setUsuarioModificacion(user.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(lp));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LicenciaPersonal> detalle(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        LicenciaPersonal lp = repository.findById(id)
                .filter(LicenciaPersonal::getIsActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Licencia no encontrada"));
        return ResponseEntity.ok(lp);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<LicenciaPersonal> actualizar(
            @PathVariable("id") UUID id,
            @RequestBody LicenciaPersonal body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        LicenciaPersonal lp = repository.findById(id)
                .filter(LicenciaPersonal::getIsActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Licencia no encontrada"));

        if (!"PENDIENTE".equalsIgnoreCase(lp.getEstado())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se puede editar una licencia PENDIENTE");
        }

        if (body.getMotivo() != null) lp.setMotivo(body.getMotivo());
        if (body.getObservacionesRh() != null) lp.setObservacionesRh(body.getObservacionesRh());
        if (body.getSustitutoId() != null) lp.setSustitutoId(body.getSustitutoId());
        if (body.getConGoceSueldo() != null) lp.setConGoceSueldo(body.getConGoceSueldo());
        lp.setUsuarioModificacion(user.getUsername());

        return ResponseEntity.ok(repository.save(lp));
    }

    @PostMapping("/{id}/aprobar")
    public ResponseEntity<LicenciaPersonal> aprobar(
            @PathVariable("id") UUID id,
            @RequestParam(value = "observaciones", required = false) String observaciones,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo Director o RH puede aprobar licencias");
        }

        LicenciaPersonal lp = repository.findById(id)
                .filter(LicenciaPersonal::getIsActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Licencia no encontrada"));

        if (!"PENDIENTE".equalsIgnoreCase(lp.getEstado())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La licencia no está en estado PENDIENTE");
        }

        lp.setEstado("APROBADA");
        lp.setAprobadoPor(user.getId());
        lp.setFechaAprobacion(LocalDateTime.now());
        if (observaciones != null) {
            lp.setObservacionesRh(observaciones);
        }
        lp.setUsuarioModificacion(user.getUsername());

        return ResponseEntity.ok(repository.save(lp));
    }

    @PostMapping("/{id}/rechazar")
    public ResponseEntity<LicenciaPersonal> rechazar(
            @PathVariable("id") UUID id,
            @RequestParam("motivo_rechazo") String motivoRechazo,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo Director o RH puede rechazar licencias");
        }

        LicenciaPersonal lp = repository.findById(id)
                .filter(LicenciaPersonal::getIsActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Licencia no encontrada"));

        if (!"PENDIENTE".equalsIgnoreCase(lp.getEstado())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La licencia no está en estado PENDIENTE");
        }

        lp.setEstado("RECHAZADA");
        lp.setAprobadoPor(user.getId());
        lp.setObservacionesRh(motivoRechazo);
        lp.setUsuarioModificacion(user.getUsername());

        return ResponseEntity.ok(repository.save(lp));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        LicenciaPersonal lp = repository.findById(id)
                .filter(LicenciaPersonal::getIsActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Licencia no encontrada"));

        if (!"PENDIENTE".equalsIgnoreCase(lp.getEstado())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se puede cancelar una licencia PENDIENTE");
        }

        lp.setEstado("CANCELADA");
        lp.setIsActive(false);
        lp.setUsuarioModificacion(user.getUsername());
        repository.save(lp);
    }
}
