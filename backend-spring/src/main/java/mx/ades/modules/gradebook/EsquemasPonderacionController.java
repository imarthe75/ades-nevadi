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

@RestController
@RequestMapping("/api/v1/esquemas-ponderacion")
@RequiredArgsConstructor
public class EsquemasPonderacionController {

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
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.listar(nivelEducativoId, materiaId));
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

        List<ItemPonderacion> items;
        try {
            items = toItems(body.getItems());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        CrearEsquemaUseCase.Command cmd;
        try {
            cmd = new CrearEsquemaUseCase.Command(
                    body.getNombre(), body.getNivelEducativoId(), body.getMateriaId(),
                    body.getVigenteDesde(), body.getVigenteHasta(), items,
                    user.getId(), user.getUsername(), body.getEsNee());
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

        List<ItemPonderacion> items;
        try {
            items = toItems(body.getItems());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        ActualizarEsquemaUseCase.Command cmd;
        try {
            cmd = new ActualizarEsquemaUseCase.Command(
                    esquemaId, body.getNombre(), body.getNivelEducativoId(), body.getMateriaId(),
                    body.getVigenteDesde(), body.getVigenteHasta(), items, user.getUsername(), body.getEsNee());
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
        try {
            return ResponseEntity.ok(esquemaService.desactivar(esquemaId, user.getUsername()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
