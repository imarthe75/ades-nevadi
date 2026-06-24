package mx.ades.modules.admin;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.admin.domain.port.in.EvaluarPromocionUseCase;
import mx.ades.modules.catalogos.NivelEducativo;
import mx.ades.modules.catalogos.NivelEducativoRepository;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST para la configuración de reglas de promoción por nivel educativo.
 * Expone endpoints bajo /api/v1/admin/reglas-promocion para consultar y actualizar
 * parámetros como mínimo aprobatorio, escala máxima, máximo de materias reprobadas
 * y porcentaje mínimo de asistencia. Incluye un endpoint de evaluación masiva
 * de promoción por ciclo escolar. Restringido a nivelAcceso de administrador (1).
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/admin/reglas-promocion")
@RequiredArgsConstructor
public class ReglasPromocionController {

    private final NivelEducativoRepository nivelRepo;
    private final AdesUserService userService;
    private final EvaluarPromocionUseCase evaluarPromocion;

    @GetMapping
    public ResponseEntity<List<NivelEducativo>> listar(@AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(nivelRepo.findAllByIsActiveTrueOrderByNombreNivel());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<NivelEducativo> actualizar(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere nivel de acceso administrador");
        }
        NivelEducativo nivel = nivelRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nivel educativo no encontrado"));
        if (body.containsKey("minimo_aprobatorio")) nivel.setMinimoAprobatorio(new BigDecimal(body.get("minimo_aprobatorio").toString()));
        if (body.containsKey("escala_maxima")) nivel.setEscalaMaxima(new BigDecimal(body.get("escala_maxima").toString()));
        if (body.containsKey("max_materias_reprobadas")) nivel.setMaxMateriasReprobadas(Short.parseShort(body.get("max_materias_reprobadas").toString()));
        if (body.containsKey("min_asistencia_pct")) nivel.setMinAsistenciaPct(new BigDecimal(body.get("min_asistencia_pct").toString()));
        if (body.containsKey("permite_recursamiento")) nivel.setPermiteRecursamiento(Boolean.parseBoolean(body.get("permite_recursamiento").toString()));
        if (body.containsKey("max_anios_reprobados")) nivel.setMaxAniosReprobados(Short.parseShort(body.get("max_anios_reprobados").toString()));
        if (body.containsKey("tiene_examen_extra")) nivel.setTieneExamenExtra(Boolean.parseBoolean(body.get("tiene_examen_extra").toString()));
        nivel.setUsuarioModificacion(user.getUsername());
        return ResponseEntity.ok(nivelRepo.save(nivel));
    }

    @PostMapping("/evaluar")
    public ResponseEntity<Map<String, Object>> evaluarPromocionEndpoint(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere nivel de acceso administrador");
        }
        Object cicloIdObj = body.get("ciclo_id");
        if (cicloIdObj == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "ciclo_id es requerido");
        }
        Object plantelIdObj = body.get("plantel_id");
        UUID plantelId = plantelIdObj != null ? UUID.fromString(plantelIdObj.toString()) : null;
        try {
            var cmd = new EvaluarPromocionUseCase.Command(
                UUID.fromString(cicloIdObj.toString()), plantelId, user.getUsername()
            );
            Object result = evaluarPromocion.ejecutar(cmd);
            return ResponseEntity.ok(Map.of(
                "resultado", result,
                "ciclo_id", cicloIdObj,
                "plantel_id", plantelIdObj != null ? plantelIdObj : "todos",
                "ejecutado_por", user.getUsername()
            ));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }
}
