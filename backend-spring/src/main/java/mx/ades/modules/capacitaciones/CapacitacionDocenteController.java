package mx.ades.modules.capacitaciones;

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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/capacitaciones")
@RequiredArgsConstructor
public class CapacitacionDocenteController {

    private final CapacitacionDocenteRepository repository;
    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    private static final Set<String> TIPOS = Set.of(
            "CURSO", "TALLER", "DIPLOMADO", "POSGRADO", "CERTIFICACION", "CONGRESO", "OTRO"
    );
    private static final Set<String> MODALIDADES = Set.of("PRESENCIAL", "EN_LINEA", "HIBRIDA");
    private static final Set<String> AREAS = Set.of("PEDAGOGIA", "TIC", "DISCIPLINAR", "IDIOMAS", "LIDERAZGO", "OTRO");

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(value = "docente_id", required = false) UUID docenteId,
            @RequestParam(value = "tipo", required = false) String tipo,
            @RequestParam(value = "modalidad", required = false) String modalidad,
            @RequestParam(value = "validado", required = false) Boolean validado,
            @RequestParam(value = "q", required = false) String q,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder query = new StringBuilder(
            "SELECT cd.*, " +
            "  CONCAT(pe.nombre, ' ', pe.apellido_paterno, CASE WHEN pe.apellido_materno IS NOT NULL THEN CONCAT(' ', pe.apellido_materno) ELSE '' END) AS nombre_docente, " +
            "  pe.nombre AS nombre_persona, pe.apellido_paterno, pe.apellido_materno, " +
            "  pr.numero_empleado " +
            "FROM public.ades_capacitaciones_docente cd " +
            "LEFT JOIN ades_profesores pr ON pr.id = cd.docente_id " +
            "LEFT JOIN ades_personas pe ON pe.id = pr.persona_id " +
            "WHERE cd.is_active = TRUE ");
        List<Object> params = new ArrayList<>();

        if (docenteId != null) {
            query.append("AND cd.docente_id = ? ");
            params.add(docenteId);
        }
        if (q != null && !q.isBlank()) {
            query.append("AND (pe.nombre ILIKE ? OR pe.apellido_paterno ILIKE ? OR CONCAT(pe.nombre,' ',pe.apellido_paterno) ILIKE ?) ");
            String like = "%" + q.trim() + "%";
            params.add(like); params.add(like); params.add(like);
        }
        if (tipo != null && !tipo.isBlank()) {
            query.append("AND cd.tipo_certificacion = ? ");
            params.add(tipo.toUpperCase());
        }
        if (modalidad != null && !modalidad.isBlank()) {
            query.append("AND cd.modalidad = ? ");
            params.add(modalidad.toUpperCase());
        }
        if (validado != null) {
            query.append("AND cd.validado_rh = ? ");
            params.add(validado);
        }

        query.append("ORDER BY cd.fecha_inicio DESC");

        return ResponseEntity.ok(jdbc.queryForList(query.toString(), params.toArray()));
    }

    @PostMapping
    public ResponseEntity<CapacitacionDocente> crear(
            @RequestBody CapacitacionDocente body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        if (body.getFechaFin().isBefore(body.getFechaInicio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fecha_fin debe ser >= fecha_inicio");
        }
        if (body.getTipoCertificacion() == null || !TIPOS.contains(body.getTipoCertificacion().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tipo_certificacion inválido. Opciones: " + TIPOS);
        }
        if (body.getModalidad() == null || !MODALIDADES.contains(body.getModalidad().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modalidad inválida. Opciones: " + MODALIDADES);
        }
        if (body.getAreaFormacion() != null && !AREAS.contains(body.getAreaFormacion().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "area_formacion inválida. Opciones: " + AREAS);
        }
        if (body.getDuracionHrs() == null || body.getDuracionHrs().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "duracion_hrs debe ser positiva");
        }

        body.setTipoCertificacion(body.getTipoCertificacion().toUpperCase());
        body.setModalidad(body.getModalidad().toUpperCase());
        if (body.getAreaFormacion() != null) {
            body.setAreaFormacion(body.getAreaFormacion().toUpperCase());
        }
        body.setUsuarioCreacion(user.getUsername());
        body.setUsuarioModificacion(user.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(body));
    }

    @GetMapping("/resumen/{docenteId}")
    public ResponseEntity<Map<String, Object>> resumen(
            @PathVariable("docenteId") UUID docenteId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        String sql = "SELECT tipo_certificacion, modalidad, duracion_hrs, validado_rh " +
                "FROM public.ades_capacitaciones_docente WHERE docente_id = ? AND is_active = TRUE";

        List<Map<String, Object>> records = jdbc.queryForList(sql, docenteId);

        double totalHrs = 0.0;
        int validadas = 0;
        Map<String, Double> porTipo = new HashMap<>();
        Map<String, Double> porModalidad = new HashMap<>();

        for (Map<String, Object> r : records) {
            double hrs = ((Number) r.get("duracion_hrs")).doubleValue();
            totalHrs += hrs;

            String tipo = (String) r.get("tipo_certificacion");
            porTipo.put(tipo, porTipo.getOrDefault(tipo, 0.0) + hrs);

            String mod = (String) r.get("modalidad");
            porModalidad.put(mod, porModalidad.getOrDefault(mod, 0.0) + hrs);

            if (Boolean.TRUE.equals(r.get("validado_rh"))) {
                validadas++;
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("docente_id", docenteId);
        response.put("total_hrs", Math.round(totalHrs * 10.0) / 10.0);
        response.put("total_eventos", records.size());
        response.put("por_tipo", porTipo);
        response.put("por_modalidad", porModalidad);
        response.put("validadas", validadas);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CapacitacionDocente> detalle(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        CapacitacionDocente cd = repository.findById(id)
                .filter(CapacitacionDocente::getIsActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Capacitación no encontrada"));
        return ResponseEntity.ok(cd);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CapacitacionDocente> actualizar(
            @PathVariable("id") UUID id,
            @RequestBody CapacitacionDocente body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        CapacitacionDocente cd = repository.findById(id)
                .filter(CapacitacionDocente::getIsActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Capacitación no encontrada"));

        if (body.getNombre() != null) cd.setNombre(body.getNombre());
        if (body.getDescripcion() != null) cd.setDescripcion(body.getDescripcion());
        if (body.getInstitucion() != null) cd.setInstitucion(body.getInstitucion());
        if (body.getDuracionHrs() != null && body.getDuracionHrs().compareTo(java.math.BigDecimal.ZERO) > 0) cd.setDuracionHrs(body.getDuracionHrs());
        if (body.getFolioCertificado() != null) cd.setFolioCertificado(body.getFolioCertificado());
        if (body.getCertificadoUrl() != null) cd.setCertificadoUrl(body.getCertificadoUrl());
        if (body.getAreaFormacion() != null) cd.setAreaFormacion(body.getAreaFormacion().toUpperCase());
        cd.setUsuarioModificacion(user.getUsername());

        return ResponseEntity.ok(repository.save(cd));
    }

    @PostMapping("/{id}/validar")
    public ResponseEntity<CapacitacionDocente> validar(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo RH o Dirección puede validar capacitaciones");
        }

        CapacitacionDocente cd = repository.findById(id)
                .filter(CapacitacionDocente::getIsActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Capacitación no encontrada"));

        cd.setValidadoRh(true);
        cd.setFechaValidacion(LocalDateTime.now());
        cd.setUsuarioModificacion(user.getUsername());

        return ResponseEntity.ok(repository.save(cd));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        CapacitacionDocente cd = repository.findById(id)
                .filter(CapacitacionDocente::getIsActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Capacitación no encontrada"));

        cd.setIsActive(false);
        cd.setUsuarioModificacion(user.getUsername());
        repository.save(cd);
    }
}
