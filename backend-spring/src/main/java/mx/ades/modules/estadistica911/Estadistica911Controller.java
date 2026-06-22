package mx.ades.modules.estadistica911;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.estadistica911.query.Estadistica911QueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reporte 911 de inicio de cursos (educación básica SEP). Solo pre-cálculo:
 * la captura oficial se realiza en la plataforma f911 de la SEP.
 */
@RestController
@RequestMapping("/api/v1/reportes/911")
@RequiredArgsConstructor
public class Estadistica911Controller {

    private final AdesUserService            userService;
    private final Estadistica911QueryService query;

    @GetMapping
    public Map<String, Object> reporte(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "ciclo_id",   required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (nivel(user) > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo coordinación/dirección puede consultar el reporte 911");
        }
        UUID plantel = scopePlantel(user, plantelId);

        List<Map<String, Object>> matriz  = query.matriz(plantel, cicloId);
        List<Map<String, Object>> grupos  = query.gruposPorGrado(plantel, cicloId);

        List<Map<String, Object>> discapacidad = query.discapacidadPorGrado(plantel, cicloId);

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("nota", "Cifras de inicio de cursos para transcribir a la plataforma oficial f911 (SEP). " +
                "Edad cumplida al 31 de diciembre del año de inicio del ciclo.");
        result.put("matricula_por_grado_sexo_ingreso_edad", matriz);  // IV.1
        result.put("grupos_por_grado", grupos);                        // IV.2
        result.put("discapacidad_por_grado_sexo", discapacidad);       // IX
        return result;
    }

    private int nivel(AdesUser u) {
        return u.getNivelAcceso() != null ? u.getNivelAcceso() : 5;
    }

    /** No-admins (nivel > 2) quedan acotados a su propio plantel. */
    private UUID scopePlantel(AdesUser u, UUID solicitado) {
        if (nivel(u) > 2 && u.getPlantelId() != null) {
            return u.getPlantelId();
        }
        return solicitado;
    }
}
