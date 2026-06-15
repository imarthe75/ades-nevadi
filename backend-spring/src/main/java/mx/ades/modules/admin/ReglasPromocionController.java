package mx.ades.modules.admin;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.catalogos.NivelEducativo;
import mx.ades.modules.catalogos.NivelEducativoRepository;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/reglas-promocion")
@RequiredArgsConstructor
public class ReglasPromocionController {

    private final NivelEducativoRepository nivelRepo;
    private final JdbcTemplate jdbc;
    private final AdesUserService userService;

    /** Lista todos los niveles con sus reglas de promoción */
    @GetMapping
    public ResponseEntity<List<NivelEducativo>> listar(
            @AuthenticationPrincipal Jwt jwt) {

        userService.resolveUser(jwt);
        return ResponseEntity.ok(nivelRepo.findAllByIsActiveTrueOrderByNombreNivel());
    }

    /** Actualiza las reglas de promoción de un nivel educativo */
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

        if (body.containsKey("minimo_aprobatorio")) {
            nivel.setMinimoAprobatorio(new BigDecimal(body.get("minimo_aprobatorio").toString()));
        }
        if (body.containsKey("escala_maxima")) {
            nivel.setEscalaMaxima(new BigDecimal(body.get("escala_maxima").toString()));
        }
        if (body.containsKey("max_materias_reprobadas")) {
            nivel.setMaxMateriasReprobadas(Short.parseShort(body.get("max_materias_reprobadas").toString()));
        }
        if (body.containsKey("min_asistencia_pct")) {
            nivel.setMinAsistenciaPct(new BigDecimal(body.get("min_asistencia_pct").toString()));
        }
        if (body.containsKey("permite_recursamiento")) {
            nivel.setPermiteRecursamiento(Boolean.parseBoolean(body.get("permite_recursamiento").toString()));
        }
        if (body.containsKey("max_anios_reprobados")) {
            nivel.setMaxAniosReprobados(Short.parseShort(body.get("max_anios_reprobados").toString()));
        }
        if (body.containsKey("tiene_examen_extra")) {
            nivel.setTieneExamenExtra(Boolean.parseBoolean(body.get("tiene_examen_extra").toString()));
        }

        nivel.setUsuarioModificacion(user.getUsername());
        return ResponseEntity.ok(nivelRepo.save(nivel));
    }

    /**
     * Ejecuta fn_evaluar_estatus_promocion() para un ciclo dado.
     * Marca cada alumno ACTIVO como PROMOVIDO o REPROBADO según las reglas del nivel.
     * Requiere nivel_acceso = 1 (superadmin).
     */
    @PostMapping("/evaluar")
    public ResponseEntity<Map<String, Object>> evaluarPromocion(
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

        String sql = plantelId != null
                ? "SELECT fn_evaluar_estatus_promocion(?::uuid, ?::uuid, ?)"
                : "SELECT fn_evaluar_estatus_promocion(?::uuid, NULL::uuid, ?)";

        Object result;
        if (plantelId != null) {
            result = jdbc.queryForObject(sql, Object.class,
                    cicloIdObj.toString(), plantelId.toString(), user.getUsername());
        } else {
            result = jdbc.queryForObject(sql, Object.class,
                    cicloIdObj.toString(), user.getUsername());
        }

        return ResponseEntity.ok(Map.of(
                "resultado", result,
                "ciclo_id",  cicloIdObj,
                "plantel_id", plantelIdObj != null ? plantelIdObj : "todos",
                "ejecutado_por", user.getUsername()
        ));
    }
}
