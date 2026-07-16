package mx.ades.modules.asistencias;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.asistencias.domain.port.in.ConsultarAsistenciasPorClaseUseCase;
import mx.ades.modules.asistencias.domain.port.in.RegistrarAsistenciaMasivaUseCase;
import mx.ades.modules.asistencias.infrastructure.inbound.rest.dto.AsistenciaResponseDto;
import mx.ades.modules.asistencias.infrastructure.inbound.rest.dto.RegistrarAsistenciaItemDto;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/asistencias")
@RequiredArgsConstructor
public class AsistenciaController {

    private final RegistrarAsistenciaMasivaUseCase registrarAsistenciaMasiva;
    private final ConsultarAsistenciasPorClaseUseCase consultarAsistenciasPorClase;
    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    @PostMapping("/registrar-lote")
    public ResponseEntity<Void> registrarLote(
            @RequestBody List<RegistrarAsistenciaItemDto> items,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = requireStaff(jwt);
        // Un docente (nivelAcceso 4) solo puede registrar asistencia de clases que él mismo
        // imparte (ades_clases.profesor_id) — sin esto, cualquier docente podía pasar lista
        // por cualquier claseId ajeno (BOLA/BFLA, OWASP API1/API5).
        new LinkedHashSet<>(items.stream().map(RegistrarAsistenciaItemDto::claseId).toList())
                .forEach(claseId -> requireAccesoClase(user, claseId));
        registrarAsistenciaMasiva.ejecutar(
                items.stream().map(RegistrarAsistenciaItemDto::toCommand).toList(),
                user.getUsername());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/clase/{claseId}")
    public ResponseEntity<List<AsistenciaResponseDto>> listarPorClase(
            @PathVariable("claseId") UUID claseId,
            @AuthenticationPrincipal Jwt jwt) {
        // Antes solo se llamaba resolveUser (autenticación) sin verificar rol ni
        // asignación: cualquier cuenta autenticada (incluidos alumnos/padres) podía leer
        // la lista de asistencia de CUALQUIER clase (BOLA, OWASP API1).
        AdesUser user = requireStaff(jwt);
        requireAccesoClase(user, claseId);
        return ResponseEntity.ok(
                consultarAsistenciasPorClase.ejecutar(claseId).stream()
                        .map(AsistenciaResponseDto::from)
                        .toList());
    }

    private static final Set<String> ESTATUS_VALIDOS =
        Set.of("PRESENTE", "AUSENTE", "TARDE", "JUSTIFICADO");

    /** POST /api/v1/asistencias/clase/{claseId} — frontend sends { asistencias: [{estudiante_id, estatus_asistencia}] } */
    @PostMapping("/clase/{claseId}")
    public ResponseEntity<Void> registrarPorClase(
            @PathVariable("claseId") UUID claseId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = requireStaff(jwt);
        requireAccesoClase(user, claseId);
        String usuario = user.getUsername();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lista = (List<Map<String, Object>>) body.get("asistencias");
        if (lista == null || lista.isEmpty()) return ResponseEntity.ok().build();

        List<RegistrarAsistenciaItemDto> items = new ArrayList<>();
        for (int i = 0; i < lista.size(); i++) {
            Map<String, Object> a = lista.get(i);
            Object estudianteIdRaw = a.get("estudiante_id");
            if (estudianteIdRaw == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El campo 'estudiante_id' es obligatorio en el registro #" + (i + 1));
            }
            UUID estudianteId;
            try {
                estudianteId = UUID.fromString(estudianteIdRaw.toString());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "UUID inválido en 'estudiante_id' del registro #" + (i + 1));
            }
            String estatusRaw = a.get("estatus_asistencia") != null
                ? a.get("estatus_asistencia").toString().toUpperCase()
                : "AUSENTE";
            if (!ESTATUS_VALIDOS.contains(estatusRaw)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Estatus inválido '" + estatusRaw + "'. Permitidos: " + ESTATUS_VALIDOS);
            }
            items.add(new RegistrarAsistenciaItemDto(claseId, estudianteId, estatusRaw, null));
        }
        registrarAsistenciaMasiva.ejecutar(
            items.stream().map(RegistrarAsistenciaItemDto::toCommand).toList(),
            usuario);
        return ResponseEntity.ok().build();
    }

    /**
     * Registrar asistencia es operación de personal escolar (nivelAcceso &le;4).
     * Antes de este fix, cualquier JWT válido (incluidos alumnos/padres, nivelAcceso
     * &ge;5) podía invocar estos endpoints — la firma del token se validaba pero
     * nunca se resolvía el usuario ADES ni se comprobaba su rol (BFLA/OWASP API5).
     */
    private AdesUser requireStaff(Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
        return user;
    }

    /**
     * Admin/Director/Coordinador (nivelAcceso &le;3) tienen alcance institucional dentro
     * de su propio plantel; un Docente (nivelAcceso 4) solo puede leer/escribir asistencia
     * de clases donde él mismo es el profesor de registro ({@code ades_clases.profesor_id})
     * — mismo criterio ya aplicado en ActividadesController (Fase 5) vía
     * {@code ades_asignaciones_docentes}.
     * <p>
     * (Corregido 2026-07-16 — BOLA: el {@code return} temprano para nivelAcceso&le;3
     * nunca verificaba el plantel de la clase — Admin_Plantel/Director/Coordinador de
     * un plantel podían leer/escribir asistencia de clases de CUALQUIER plantel. Solo
     * nivelAcceso 0 mantiene alcance libre, ver {@code AdesUserService#verificarPlantel}.)
     */
    private void requireAccesoClase(AdesUser user, UUID claseId) {
        if (user.getNivelAcceso() != null && user.getNivelAcceso() <= 3) {
            List<UUID> plantelRows = jdbc.queryForList(
                    "SELECT gr.plantel_id FROM ades_clases c " +
                    "JOIN ades_grupos g ON g.id = c.grupo_id " +
                    "JOIN ades_grados gr ON gr.id = g.grado_id " +
                    "WHERE c.id = ?", UUID.class, claseId);
            if (plantelRows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Clase no encontrada");
            userService.verificarPlantel(user, plantelRows.get(0), "La clase no pertenece a su plantel");
            return;
        }
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_clases c " +
                "JOIN ades_profesores p ON p.id = c.profesor_id " +
                "WHERE c.id = ? AND p.persona_id = ?",
                Long.class, claseId, user.getPersonaId());
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No está asignado a esta clase");
        }
    }
}
