package mx.ades.modules.aulas;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.aulas.domain.port.in.ActualizarAulaUseCase;
import mx.ades.modules.aulas.domain.port.in.CrearAulaUseCase;
import mx.ades.modules.aulas.domain.port.out.AulaRepositoryPort;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Controlador REST para la gestión de aulas en los distintos planteles del Instituto Nevadi.
 * Permite listar, crear, actualizar y consultar información de las aulas, así como
 * administrar sus franjas de disponibilidad horaria y verificar conflictos de solapamiento.
 *
 * @author ADES
 * @version 2.0
 */
@RestController
@RequestMapping("/api/v1/aulas")
@RequiredArgsConstructor
public class AulaController {

    private final CrearAulaUseCase crearUseCase;
    private final ActualizarAulaUseCase actualizarUseCase;
    private final AulaRepositoryPort repositoryPort;
    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    /**
     * Recupera el listado de todas las aulas registradas.
     * Opcionalmente se puede filtrar por un plantel específico.
     *
     * @param plantelId Identificador del plantel para realizar el filtrado (opcional).
     * @return ResponseEntity con la lista de aulas obtenidas.
     */
    @GetMapping
    public ResponseEntity<List<Aula>> list(@RequestParam(name = "plantel_id", required = false) UUID plantelId) {
        return ResponseEntity.ok(plantelId != null
                ? repositoryPort.findByPlantelId(plantelId)
                : repositoryPort.findAll());
    }

    /**
     * Consulta el detalle completo de un aula por su identificador único.
     * Además, recupera todas las franjas de disponibilidad asociadas.
     *
     * @param id Identificador único del aula.
     * @return ResponseEntity con el mapa de atributos del aula y sus franjas activas.
     * @throws ResponseStatusException 404 si el aula no existe.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable("id") UUID id) {
        Aula aula = repositoryPort.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aula no encontrada"));

        List<Map<String, Object>> franjas = jdbc.queryForList(
            "SELECT id, dia_semana, hora_inicio::text, hora_fin::text, motivo_bloqueo, is_active " +
            "FROM ades_disponibilidad_aula WHERE aula_id = ? AND is_active = true ORDER BY dia_semana, hora_inicio",
            id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", aula.getId());
        result.put("nombre_aula", aula.getNombreAula());
        result.put("plantel_id", aula.getPlantelId());
        result.put("tipo_aula", aula.getTipoAula());
        result.put("capacidad_alumnos", aula.getCapacidadAlumnos());
        result.put("is_active", aula.getIsActive());
        result.put("clave_aula", aula.getClaveAula());
        result.put("piso", aula.getPiso());
        result.put("edificio", aula.getEdificio());
        result.put("capacidad_maxima", aula.getCapacidadMaxima());
        result.put("tiene_proyector", aula.isTieneProyector());
        result.put("tiene_pizarra_digital", aula.isTienePizarraDigital());
        result.put("tiene_pizarron", aula.isTienePizarron());
        result.put("tiene_aire_acondicionado", aula.isTieneAireAcondicionado());
        result.put("tiene_ventiladores", aula.isTieneVentiladores());
        result.put("tiene_internet", aula.isTieneInternet());
        result.put("num_computadoras", aula.getNumComputadoras());
        result.put("estado_aula", aula.getEstadoAula());
        result.put("observaciones", aula.getObservaciones());
        result.put("franjas", franjas);
        return ResponseEntity.ok(result);
    }

    /**
     * Registra una nueva aula en el sistema.
     *
     * @param body Datos básicos del aula a crear.
     * @param jwt Credenciales del usuario autenticado.
     * @return ResponseEntity con la estructura del aula recién creada.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        CrearAulaUseCase.Command cmd = buildCrearCmd(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(crearUseCase.crear(cmd));
    }

    /**
     * Modifica toda la información de un aula existente.
     *
     * @param id Identificador del aula a actualizar.
     * @param body Estructura con todos los datos actualizados del aula.
     * @param jwt Credenciales del usuario autenticado.
     * @return ResponseEntity con los datos actualizados de la operación.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(actualizarUseCase.actualizar(buildActualizarCmd(id, body)));
    }

    /**
     * Aplica cambios parciales (PATCH) a los atributos de un aula.
     *
     * @param id Identificador único del aula.
     * @param body Atributos parciales a modificar.
     * @param jwt Credenciales del usuario.
     * @return ResponseEntity con los datos del aula modificados.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> patch(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(actualizarUseCase.actualizar(buildActualizarCmd(id, body)));
    }

    /**
     * Agrega una franja horaria de disponibilidad o de bloqueo para un aula determinada.
     *
     * @param aulaId Identificador del aula.
     * @param body Parámetros de la franja (dia_semana, hora_inicio, hora_fin, motivo_bloqueo).
     * @param jwt Credenciales del usuario autenticado.
     * @return ResponseEntity con los detalles de la franja horaria creada.
     * @throws ResponseStatusException 404 si el aula no existe, o 400 si faltan parámetros requeridos.
     */
    @PostMapping("/{id}/disponibilidad")
    public ResponseEntity<Map<String, Object>> agregarFranja(
            @PathVariable("id") UUID aulaId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        if (!repositoryPort.findById(aulaId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aula no encontrada");
        }
        Integer dia = body.get("dia_semana") != null ? ((Number) body.get("dia_semana")).intValue() : null;
        String horaInicio = (String) body.get("hora_inicio");
        String horaFin    = (String) body.get("hora_fin");
        String motivo     = (String) body.get("motivo_bloqueo");
        if (dia == null || horaInicio == null || horaFin == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dia_semana, hora_inicio y hora_fin son obligatorios");
        }
        UUID newId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ades_disponibilidad_aula (id, aula_id, dia_semana, hora_inicio, hora_fin, motivo_bloqueo) " +
            "VALUES (?, ?, ?, ?::time, ?::time, ?)",
            newId, aulaId, dia, horaInicio, horaFin, motivo);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", newId);
        result.put("aula_id", aulaId);
        result.put("dia_semana", dia);
        result.put("hora_inicio", horaInicio);
        result.put("hora_fin", horaFin);
        result.put("motivo_bloqueo", motivo);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * Remueve una franja de disponibilidad existente cambiando su estado activo a inactivo.
     *
     * @param franjaId Identificador de la franja a eliminar.
     * @param jwt Credenciales del usuario autenticado.
     * @throws ResponseStatusException 404 si la franja no fue localizada.
     */
    @DeleteMapping("/disponibilidad/{franjaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarFranja(
            @PathVariable("franjaId") UUID franjaId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        int rows = jdbc.update(
            "UPDATE ades_disponibilidad_aula SET is_active = false WHERE id = ?", franjaId);
        if (rows == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Franja no encontrada");
    }

    /**
     * Verifica si existe solapamiento u otro conflicto de horario para un intervalo dado en un aula.
     *
     * @param aulaId Identificador del aula.
     * @param body Estructura con dia_semana, hora_inicio y hora_fin.
     * @param jwt Credenciales del usuario autenticado.
     * @return ResponseEntity indicando la presencia y conteo de conflictos detectados.
     * @throws ResponseStatusException 400 si faltan parámetros requeridos.
     */
    @PostMapping("/{id}/verificar-conflicto")
    public ResponseEntity<Map<String, Object>> verificarConflicto(
            @PathVariable("id") UUID aulaId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        Integer dia = body.get("dia_semana") != null ? ((Number) body.get("dia_semana")).intValue() : null;
        String horaInicio = (String) body.get("hora_inicio");
        String horaFin    = (String) body.get("hora_fin");
        if (dia == null || horaInicio == null || horaFin == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dia_semana, hora_inicio y hora_fin son obligatorios");
        }
        List<Map<String, Object>> conflictos = jdbc.queryForList(
            "SELECT id, dia_semana, hora_inicio::text, hora_fin::text, motivo_bloqueo " +
            "FROM ades_disponibilidad_aula " +
            "WHERE aula_id = ? AND dia_semana = ? AND is_active = true " +
            "  AND hora_inicio < ?::time AND hora_fin > ?::time",
            aulaId, dia, horaFin, horaInicio);
        boolean hayConflicto = !conflictos.isEmpty();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("conflicto", hayConflicto);
        resp.put("num_conflictos", conflictos.size());
        resp.put("conflictos", conflictos);
        resp.put("mensaje", hayConflicto ? "El aula ya tiene una franja en ese horario." : "Sin conflictos detectados.");
        return ResponseEntity.ok(resp);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private CrearAulaUseCase.Command buildCrearCmd(Map<String, Object> b) {
        return new CrearAulaUseCase.Command(
            (String) b.get("nombre_aula"),
            b.get("plantel_id") != null ? UUID.fromString(b.get("plantel_id").toString()) : null,
            (String) b.get("tipo_aula"),
            b.get("capacidad_alumnos") != null ? ((Number) b.get("capacidad_alumnos")).intValue() : null
        );
    }

    private ActualizarAulaUseCase.Command buildActualizarCmd(UUID id, Map<String, Object> b) {
        return new ActualizarAulaUseCase.Command(
            id,
            (String) b.get("nombre_aula"),
            b.get("plantel_id") != null ? UUID.fromString(b.get("plantel_id").toString()) : null,
            (String) b.get("tipo_aula"),
            b.get("capacidad_alumnos") != null ? ((Number) b.get("capacidad_alumnos")).intValue() : null,
            b.get("is_active") != null ? Boolean.valueOf(b.get("is_active").toString()) : null,
            (String) b.get("clave_aula"),
            b.get("piso") != null ? ((Number) b.get("piso")).shortValue() : null,
            (String) b.get("edificio"),
            b.get("capacidad_maxima") != null ? ((Number) b.get("capacidad_maxima")).shortValue() : null,
            b.get("tiene_proyector") != null ? Boolean.valueOf(b.get("tiene_proyector").toString()) : null,
            b.get("tiene_pizarra_digital") != null ? Boolean.valueOf(b.get("tiene_pizarra_digital").toString()) : null,
            b.get("tiene_pizarron") != null ? Boolean.valueOf(b.get("tiene_pizarron").toString()) : null,
            b.get("tiene_aire_acondicionado") != null ? Boolean.valueOf(b.get("tiene_aire_acondicionado").toString()) : null,
            b.get("tiene_ventiladores") != null ? Boolean.valueOf(b.get("tiene_ventiladores").toString()) : null,
            b.get("tiene_internet") != null ? Boolean.valueOf(b.get("tiene_internet").toString()) : null,
            b.get("num_computadoras") != null ? ((Number) b.get("num_computadoras")).shortValue() : null,
            (String) b.get("estado_aula"),
            (String) b.get("observaciones")
        );
    }
}
