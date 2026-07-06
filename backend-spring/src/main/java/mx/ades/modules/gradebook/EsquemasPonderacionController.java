package mx.ades.modules.gradebook;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.esquemas_ponderacion.application.service.EsquemaApplicationService;
import mx.ades.modules.esquemas_ponderacion.domain.model.ItemPonderacion;
import mx.ades.modules.esquemas_ponderacion.domain.port.in.ActualizarEsquemaUseCase;
import mx.ades.modules.esquemas_ponderacion.domain.port.in.CrearEsquemaUseCase;
import mx.ades.modules.esquemas_ponderacion.query.EsquemaQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Adaptador REST para la gestión de esquemas de ponderación del gradebook.
 * Expone endpoints bajo /api/v1/esquemas-ponderacion para listar, consultar el esquema
 * efectivo por materia, crear, actualizar y desactivar esquemas con sus ítems ponderados
 * (tarea, examen, participación, etc.) por nivel educativo y vigencia temporal.
 * Requiere JWT válido; operaciones de escritura registran el usuario en el audit trail.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/esquemas-ponderacion")
@RequiredArgsConstructor
public class EsquemasPonderacionController {

    // Jerarquía real (db/seeds/001_datos_base.sql): 0=ADMIN_GLOBAL, 1=ADMIN_PLANTEL,
    // 2=Director/Subdirector/Coord.Administrativo, 3=Coord.Académico/Orientador,
    // 4=DOCENTE/MEDICO/PREFECTO, 5=ALUMNO/PADRE_FAMILIA.
    private static final int NIVEL_ADMIN_GLOBAL = 0;
    private static final int NIVEL_DOCENTE = 4;
    private static final int NIVEL_STAFF_MAX = 4;

    private final AdesUserService userService;
    private final CrearEsquemaUseCase crearEsquemaUseCase;
    private final ActualizarEsquemaUseCase actualizarEsquemaUseCase;
    private final EsquemaApplicationService esquemaService;
    private final EsquemaQueryService queryService;

    @Data
    public static class ItemIn {
        private String tipoItem;
        private String nombrePersonalizado;
        private Double pesoPorcentaje;
        private Integer ordenDisplay = 1;
    }

    @Data
    public static class EsquemaIn {
        private String nombre;
        private UUID nivelEducativoId;
        private UUID materiaId;
        private LocalDate vigenteDesde;
        private LocalDate vigenteHasta;
        private List<ItemIn> items;
        private Boolean esNee = false;
        private UUID plantelId;
    }

    /**
     * Resuelve el alcance (profesor_id, plantel_id) que se debe grabar en el esquema
     * según el rol del usuario que escribe. Docente: siempre su propio profesor_id/plantel_id
     * (nunca institucional). Director/Coordinador: nunca a nombre de un profesor, acotado a
     * su propio plantel. Admin global: sin profesor_id; plantel_id libre (puede ser NULL = institucional).
     */
    private UUID[] resolverScopeEscritura(AdesUser user, UUID plantelIdSolicitado) {
        Integer nivel = user.getNivelAcceso();
        if (nivel == null || nivel > NIVEL_DOCENTE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo Admin, Director, Coordinador o Docente pueden gestionar esquemas de ponderación");
        }
        if (nivel == NIVEL_DOCENTE) {
            UUID profesorId = queryService.resolverProfesorIdPorPersona(user.getPersonaId());
            if (profesorId == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "El usuario no está vinculado a un registro de profesor");
            }
            return new UUID[]{profesorId, user.getPlantelId()};
        }
        UUID plantelId = (nivel == NIVEL_ADMIN_GLOBAL) ? plantelIdSolicitado : user.getPlantelId();
        return new UUID[]{null, plantelId};
    }

    /** Verifica que el usuario pueda modificar/desactivar el esquema existente dado su alcance actual. */
    private void verificarPropiedad(AdesUser user, UUID esquemaId) {
        Map<String, Object> actual = queryService.scopeDe(esquemaId);
        if (actual == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Esquema no encontrado: " + esquemaId);
        UUID profesorActual = (UUID) actual.get("profesor_id");
        UUID plantelActual = (UUID) actual.get("plantel_id");
        int nivel = user.getNivelAcceso();
        if (nivel == NIVEL_DOCENTE) {
            UUID miProfesorId = queryService.resolverProfesorIdPorPersona(user.getPersonaId());
            if (miProfesorId == null || !miProfesorId.equals(profesorActual)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puede modificar su propio esquema de ponderación");
            }
        } else if (nivel > NIVEL_ADMIN_GLOBAL) {
            if (profesorActual != null || plantelActual == null || !plantelActual.equals(user.getPlantelId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado para modificar este esquema");
            }
        }
    }

    private List<ItemPonderacion> toItems(List<ItemIn> in) {
        return in == null ? List.of() : in.stream()
                .map(i -> new ItemPonderacion(i.getTipoItem(), i.getNombrePersonalizado(),
                        i.getPesoPorcentaje(), i.getOrdenDisplay()))
                .collect(Collectors.toList());
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listarEsquemas(
            @RequestParam(value = "nivel_educativo_id", required = false) UUID nivelEducativoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_STAFF_MAX) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado para consultar esquemas de ponderación");
        }
        UUID profesorScopeId = null;
        UUID plantelScopeId = null;
        if (user.getNivelAcceso() > NIVEL_ADMIN_GLOBAL) {
            plantelScopeId = user.getPlantelId();
        }
        if (user.getNivelAcceso() >= NIVEL_DOCENTE) {
            profesorScopeId = queryService.resolverProfesorIdPorPersona(user.getPersonaId());
        }
        return ResponseEntity.ok(queryService.listar(nivelEducativoId, materiaId, profesorScopeId, plantelScopeId));
    }

    @GetMapping("/efectivo/{materiaId}")
    public ResponseEntity<Map<String, Object>> esquemaEfectivo(
            @PathVariable("materiaId") UUID materiaId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.efectivo(materiaId));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crearEsquema(
            @RequestBody EsquemaIn body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        UUID[] scope = resolverScopeEscritura(user, body.getPlantelId());

        List<ItemPonderacion> items;
        try {
            items = toItems(body.getItems());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        double suma = items.stream()
                .mapToDouble(i -> i.pesoPorcentaje() != null ? i.pesoPorcentaje() : 0.0)
                .sum();
        if (Math.abs(suma - 100.0) > 0.01) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La suma de ponderaciones debe ser exactamente 100%. Suma actual: " + String.format("%.2f", suma) + "%");
        }

        CrearEsquemaUseCase.Command cmd;
        try {
            cmd = new CrearEsquemaUseCase.Command(
                    body.getNombre(), body.getNivelEducativoId(), body.getMateriaId(),
                    body.getVigenteDesde(), body.getVigenteHasta(), items,
                    user.getId(), user.getUsername(), body.getEsNee(), scope[0], scope[1]);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(crearEsquemaUseCase.crear(cmd));
    }

    @PutMapping("/{esquemaId}")
    public ResponseEntity<Map<String, Object>> actualizarEsquema(
            @PathVariable("esquemaId") UUID esquemaId,
            @RequestBody EsquemaIn body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        UUID[] scope = resolverScopeEscritura(user, body.getPlantelId());
        verificarPropiedad(user, esquemaId);

        List<ItemPonderacion> items;
        try {
            items = toItems(body.getItems());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        double suma = items.stream()
                .mapToDouble(i -> i.pesoPorcentaje() != null ? i.pesoPorcentaje() : 0.0)
                .sum();
        if (Math.abs(suma - 100.0) > 0.01) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La suma de ponderaciones debe ser exactamente 100%. Suma actual: " + String.format("%.2f", suma) + "%");
        }

        ActualizarEsquemaUseCase.Command cmd;
        try {
            cmd = new ActualizarEsquemaUseCase.Command(
                    esquemaId, body.getNombre(), body.getNivelEducativoId(), body.getMateriaId(),
                    body.getVigenteDesde(), body.getVigenteHasta(), items, user.getUsername(), body.getEsNee(),
                    scope[0], scope[1]);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        try {
            return ResponseEntity.ok(actualizarEsquemaUseCase.actualizar(cmd));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @DeleteMapping("/{esquemaId}")
    public ResponseEntity<Map<String, Object>> desactivarEsquema(
            @PathVariable("esquemaId") UUID esquemaId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        resolverScopeEscritura(user, null);
        verificarPropiedad(user, esquemaId);
        try {
            return ResponseEntity.ok(esquemaService.desactivar(esquemaId, user.getUsername()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
