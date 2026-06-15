package mx.ades.modules.compliance;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
@Slf4j
public class ComplianceController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    private static final int NIVEL_ADMIN = 2;
    private static final int NIVEL_DIRECTOR = 3;

    @Data
    public static class NormatividadPayload {
        private String nombre;
        private String tipo;
        private String descripcion;
        private String fechaVigenciaInicio;
        private String fechaVigenciaFin;
        private String urlDocumento;
        private Boolean aplicaPrimaria = true;
        private Boolean aplicaSecundaria = true;
        private Boolean aplicaPreparatoria = true;
    }

    @Data
    public static class RetencionPayload {
        private UUID alumnoId;
        private String tipoRetencion;
        private String motivo;
        private String fechaInicio;
        private String fechaFin;
        private String accionesRequeridas;
    }

    @Data
    public static class AlertaCumplimientoPayload {
        private String tipoAlerta;
        private String descripcion;
        private UUID alumnoId;
        private UUID plantelId;
        private String severidad = "MEDIA";
        private Boolean requiereAccion = true;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AD-007: Historial de logins
    // ──────────────────────────────────────────────────────────────────────────
    @GetMapping("/login-auditoria")
    public ResponseEntity<List<Map<String, Object>>> historialLogins(
            @RequestParam(value = "usuario", required = false) String usuario,
            @RequestParam(value = "desde", required = false) String desde,
            @RequestParam(value = "hasta", required = false) String hasta,
            @RequestParam(value = "skip", defaultValue = "0") int skip,
            @RequestParam(value = "limit", defaultValue = "100") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere Admin Plantel o superior");
        }

        StringBuilder sql = new StringBuilder("SELECT id, usuario_id, ip_origen, user_agent, exitoso, motivo_fallo, fecha_login FROM ades_log_autenticacion WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (usuario != null && !usuario.isBlank()) {
            sql.append(" AND usuario_id ILIKE ?");
            params.add("%" + usuario + "%");
        }
        if (desde != null && !desde.isBlank()) {
            sql.append(" AND fecha_login >= ?::timestamptz");
            params.add(desde);
        }
        if (hasta != null && !hasta.isBlank()) {
            sql.append(" AND fecha_login <= ?::timestamptz");
            params.add(hasta);
        }

        sql.append(" ORDER BY fecha_login DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(skip);

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        return ResponseEntity.ok(rows);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AD-013: Estadísticas de sistema
    // ──────────────────────────────────────────────────────────────────────────
    @GetMapping("/estadisticas-sistema")
    public ResponseEntity<Map<String, Object>> estadisticasSistema(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_DIRECTOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }

        List<Object> paramsList = new ArrayList<>();
        String plantelFilter = "";
        if (plantelId != null) {
            plantelFilter = " AND g.plantel_id = ?";
            paramsList.add(plantelId);
        }

        String cicloFilter = "";
        if (cicloId != null) {
            cicloFilter = " AND i.ciclo_escolar_id = ?";
            paramsList.add(cicloId);
        }

        Object[] params = paramsList.toArray();

        // 1. Alumnos y planteles
        Map<String, Object> alumnos = jdbc.queryForMap(
                "SELECT COUNT(DISTINCT i.estudiante_id) as total_alumnos, COUNT(DISTINCT g.plantel_id) as planteles " +
                        "FROM ades_inscripciones i " +
                        "JOIN ades_grupos g ON g.id = i.grupo_id " +
                        "WHERE i.is_active = TRUE" + plantelFilter + cicloFilter,
                params
        );

        // 2. Asistencia promedio
        Map<String, Object> asistencia = jdbc.queryForMap(
                "SELECT ROUND(100.0 * SUM(CASE WHEN a.estatus = 'PRESENTE' THEN 1 ELSE 0 END) / NULLIF(COUNT(a.id), 0), 2) as porcentaje_asistencia " +
                        "FROM ades_asistencias a " +
                        "JOIN ades_inscripciones i ON i.id = a.inscripcion_id " +
                        "JOIN ades_grupos g ON g.id = i.grupo_id " +
                        "WHERE i.is_active = TRUE" + plantelFilter + cicloFilter,
                params
        );

        // 3. Calificación promedio
        Map<String, Object> calificaciones = jdbc.queryForMap(
                "SELECT ROUND(AVG(c.calificacion_final)::numeric, 2) as promedio_general " +
                        "FROM ades_calificaciones c " +
                        "JOIN ades_inscripciones i ON i.id = c.inscripcion_id " +
                        "JOIN ades_grupos g ON g.id = i.grupo_id " +
                        "WHERE i.is_active = TRUE AND c.calificacion_final IS NOT NULL" + plantelFilter + cicloFilter,
                params
        );

        // 4. Incidentes de conducta
        List<Object> paramsConductaList = new ArrayList<>();
        String plantelFilterConducta = "";
        if (plantelId != null) {
            plantelFilterConducta = " AND g.plantel_id = ?";
            paramsConductaList.add(plantelId);
        }
        Map<String, Object> conducta = jdbc.queryForMap(
                "SELECT COUNT(*) as total_incidentes, SUM(CASE WHEN tipo_incidente IN ('GRAVE','MUY_GRAVE') THEN 1 ELSE 0 END) as graves " +
                        "FROM ades_incidentes_conducta ic " +
                        "JOIN ades_inscripciones i ON i.estudiante_id = ic.alumno_id AND i.is_active = TRUE " +
                        "JOIN ades_grupos g ON g.id = i.grupo_id " +
                        "WHERE ic.is_active = TRUE" + plantelFilterConducta,
                paramsConductaList.toArray()
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("alumnos", alumnos);
        result.put("asistencia", asistencia);
        result.put("calificaciones", calificaciones);
        result.put("conducta", conducta);

        return ResponseEntity.ok(result);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AD-014: Normatividad
    // ──────────────────────────────────────────────────────────────────────────
    @GetMapping("/normatividad")
    public ResponseEntity<List<Map<String, Object>>> listarNormatividad(
            @RequestParam(value = "tipo", required = false) String tipo,
            @RequestParam(value = "vigente", defaultValue = "true") boolean vigente,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder("SELECT id, nombre, tipo, descripcion, fecha_vigencia_inicio, fecha_vigencia_fin, url_documento, aplica_primaria, aplica_secundaria, aplica_preparatoria FROM ades_normatividad WHERE is_active = TRUE");
        List<Object> params = new ArrayList<>();

        if (tipo != null && !tipo.isBlank()) {
            sql.append(" AND tipo = ?");
            params.add(tipo);
        }
        if (vigente) {
            sql.append(" AND (fecha_vigencia_fin IS NULL OR fecha_vigencia_fin >= CURRENT_DATE)");
        }

        sql.append(" ORDER BY tipo, nombre");

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/normatividad")
    public ResponseEntity<Map<String, Object>> registrarNormativa(
            @RequestBody NormatividadPayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere Admin o superior");
        }

        UUID id = UUID.randomUUID();
        LocalDate fi = data.getFechaVigenciaInicio() != null ? LocalDate.parse(data.getFechaVigenciaInicio()) : LocalDate.now();
        LocalDate ff = data.getFechaVigenciaFin() != null ? LocalDate.parse(data.getFechaVigenciaFin()) : null;

        jdbc.update(
                "INSERT INTO ades_normatividad " +
                        "(id, nombre, tipo, descripcion, fecha_vigencia_inicio, fecha_vigencia_fin, " +
                        "url_documento, aplica_primaria, aplica_secundaria, aplica_preparatoria, " +
                        "usuario_creacion, usuario_modificacion) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, data.getNombre(), data.getTipo(), data.getDescripcion(), fi, ff,
                data.getUrlDocumento(), data.getAplicaPrimaria(), data.getAplicaSecundaria(), data.getAplicaPreparatoria(),
                user.getUsername(), user.getUsername()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Normativa registrada"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AD-017: Retenciones
    // ──────────────────────────────────────────────────────────────────────────
    @GetMapping("/retenciones")
    public ResponseEntity<List<Map<String, Object>>> listarRetenciones(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "tipo", required = false) String tipo,
            @RequestParam(value = "activas", defaultValue = "true") boolean activas,
            @RequestParam(value = "skip", defaultValue = "0") int skip,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_DIRECTOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }

        StringBuilder sql = new StringBuilder(
                "SELECT r.id, r.tipo_retencion, r.motivo, r.fecha_inicio, r.fecha_fin, r.acciones_requeridas, r.is_active, " +
                        "p.nombre || ' ' || p.apellido_paterno as alumno, e.matricula as numero_control, g.nombre_grupo " +
                        "FROM ades_retenciones r " +
                        "JOIN ades_estudiantes e ON e.id = r.alumno_id " +
                        "JOIN ades_personas p ON p.id = e.persona_id " +
                        "LEFT JOIN ades_inscripciones i ON i.estudiante_id = e.id AND i.is_active = TRUE " +
                        "LEFT JOIN ades_grupos g ON g.id = i.grupo_id " +
                        "WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();

        if (activas) {
            sql.append(" AND r.is_active = TRUE AND (r.fecha_fin IS NULL OR r.fecha_fin >= CURRENT_DATE)");
        }
        if (tipo != null && !tipo.isBlank()) {
            sql.append(" AND r.tipo_retencion = ?");
            params.add(tipo);
        }
        if (plantelId != null) {
            sql.append(" AND g.plantel_id = ?");
            params.add(plantelId);
        }

        sql.append(" ORDER BY r.fecha_inicio DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(skip);

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/retenciones")
    public ResponseEntity<Map<String, Object>> crearRetencion(
            @RequestBody RetencionPayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_DIRECTOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }

        UUID id = UUID.randomUUID();
        LocalDate fi = data.getFechaInicio() != null ? LocalDate.parse(data.getFechaInicio()) : LocalDate.now();
        LocalDate ff = data.getFechaFin() != null ? LocalDate.parse(data.getFechaFin()) : null;

        jdbc.update(
                "INSERT INTO ades_retenciones " +
                        "(id, alumno_id, tipo_retencion, motivo, fecha_inicio, fecha_fin, " +
                        "acciones_requeridas, autorizado_por, usuario_creacion, usuario_modificacion) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, data.getAlumnoId(), data.getTipoRetencion(), data.getMotivo(), fi, ff,
                data.getAccionesRequeridas(), user.getPersonaId(), user.getUsername(), user.getUsername()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Retención registrada"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AD-030: Alertas de cumplimiento
    // ──────────────────────────────────────────────────────────────────────────
    @PostMapping("/alerta-cumplimiento")
    public ResponseEntity<Map<String, Object>> crearAlerta(
            @RequestBody AlertaCumplimientoPayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_DIRECTOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }

        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_alertas_cumplimiento " +
                        "(id, tipo_alerta, descripcion, alumno_id, plantel_id, severidad, " +
                        "requiere_accion, estado, usuario_creacion, usuario_modificacion) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDIENTE', ?, ?)",
                id, data.getTipoAlerta(), data.getDescripcion(), data.getAlumnoId(), data.getPlantelId(),
                data.getSeveridad(), data.getRequiereAccion(), user.getUsername(), user.getUsername()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Alerta de cumplimiento registrada"));
    }

    @GetMapping("/alerta-cumplimiento")
    public ResponseEntity<List<Map<String, Object>>> listarAlertas(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "severidad", required = false) String severidad,
            @RequestParam(value = "estado", defaultValue = "PENDIENTE") String estado,
            @RequestParam(value = "skip", defaultValue = "0") int skip,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_DIRECTOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }

        StringBuilder sql = new StringBuilder("SELECT id, tipo_alerta, descripcion, severidad, estado, requiere_accion, fecha_creacion, usuario_creacion FROM ades_alertas_cumplimiento WHERE estado = ?");
        List<Object> params = new ArrayList<>();
        params.add(estado);

        if (plantelId != null) {
            sql.append(" AND plantel_id = ?");
            params.add(plantelId);
        }
        if (severidad != null && !severidad.isBlank()) {
            sql.append(" AND severidad = ?");
            params.add(severidad);
        }

        sql.append(" ORDER BY CASE severidad WHEN 'CRITICA' THEN 1 WHEN 'ALTA' THEN 2 WHEN 'MEDIA' THEN 3 ELSE 4 END, fecha_creacion DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(skip);

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        return ResponseEntity.ok(rows);
    }
}
