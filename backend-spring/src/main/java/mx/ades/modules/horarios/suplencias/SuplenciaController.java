package mx.ades.modules.horarios.suplencias;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.horarios.HorarioRepository;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/suplencias")
@RequiredArgsConstructor
public class SuplenciaController {

    private final SuplenciaRepository suplenciaRepository;
    private final AdesUserService userService;
    private final HorarioRepository horarioRepository;

    @PostMapping
    public ResponseEntity<Suplencia> crearSuplencia(
            @RequestBody SuplenciaPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);

        // profesor_ausente_id y fecha son NOT NULL en ades_suplencias; sin esta validación,
        // un valor faltante caía en NullPointerException (fecha) o en el 409 genérico de
        // GlobalExceptionHandler (profesor_ausente_id) en vez de un 400 claro.
        if (body.getProfesorAusenteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "profesor_ausente_id es requerido");
        }
        if (body.getFecha() == null || body.getFecha().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fecha es requerida");
        }
        LocalDate fecha;
        try {
            fecha = LocalDate.parse(body.getFecha());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fecha con formato inválido (esperado YYYY-MM-DD)");
        }

        Suplencia suplencia = new Suplencia();
        suplencia.setProfesorAusenteId(body.getProfesorAusenteId());
        suplencia.setFecha(fecha);
        suplencia.setTimeslotId(body.getTimeslotId());
        suplencia.setHorarioId(body.getHorarioId());
        suplencia.setMotivo(body.getMotivo());
        suplencia.setCreadoPor(user.getUsername());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(suplenciaRepository.save(suplencia));
    }

    @GetMapping
    public ResponseEntity<List<Suplencia>> listarSuplencias(
            @RequestParam("fecha") String fechaStr,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(suplenciaRepository.findByFechaAndIsActiveTrue(LocalDate.parse(fechaStr)));
    }

    @GetMapping("/{id}/sugerencias")
    public ResponseEntity<List<UUID>> sugerirSuplentes(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        Suplencia suplencia = suplenciaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Suplencia no encontrada"));
        
        // MVP: This would normally query AdesDisponibilidadDocente and cross-check with HorarioRepository 
        // for available teachers in that timeslot.
        // For now, returning an empty list as a stub for Phase B
        return ResponseEntity.ok(List.of());
    }

    @Data
    public static class SuplenciaPayload {
        private UUID profesorAusenteId;
        private String fecha;
        private UUID timeslotId;
        private UUID horarioId;
        private String motivo;
    }

    /**
     * Registrar una suplencia (reasignación de profesor ausente) es operación de
     * personal escolar (nivelAcceso &le;4: admin/director/coordinador/docente) —
     * previene BFLA (OWASP API5). Hallazgo de auditoría Fase 5: antes solo se llamaba
     * resolveUser sin verificar nivelAcceso, permitiendo a cualquier usuario
     * autenticado (incluido alumno/padre) crear registros de suplencia.
     */
    private void requireStaff(AdesUser user) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso == null || nivelAcceso > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
    }
}
