package mx.ades.modules.justificaciones.application.service;

import mx.ades.modules.justificaciones.domain.model.EstadoJustificacion;
import mx.ades.modules.justificaciones.domain.port.in.RegistrarJustificacionUseCase;
import mx.ades.modules.justificaciones.domain.port.in.ResolverJustificacionUseCase;
import mx.ades.modules.justificaciones.domain.port.out.JustificacionRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class JustificacionApplicationService
        implements RegistrarJustificacionUseCase, ResolverJustificacionUseCase {

    private final JustificacionRepositoryPort repo;

    public JustificacionApplicationService(JustificacionRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public UUID registrar(RegistrarJustificacionUseCase.Command cmd) {
        if (!repo.asistenciaJustificable(cmd.asistenciaId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Asistencia no encontrada o está marcada como PRESENTE");
        }
        if (repo.existsByAsistenciaId(cmd.asistenciaId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una justificación para esta asistencia");
        }
        UUID id = repo.create(cmd.asistenciaId(), cmd.tipo(), cmd.motivo(),
                cmd.documentoUrl(), cmd.usuarioId());
        repo.linkToAsistencia(cmd.asistenciaId(), id);
        return id;
    }

    @Override
    public String resolver(ResolverJustificacionUseCase.Command cmd) {
        if (cmd.nivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Sin permisos para resolver justificaciones (requiere COORDINADOR+)");
        }
        Optional<Map<String, Object>> just = repo.findById(cmd.justificacionId());
        if (just.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Justificación no encontrada");
        }
        EstadoJustificacion actual = EstadoJustificacion.valueOf(
                (String) just.get().get("estado"));
        if (!actual.permiteResolucion()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La justificación ya fue resuelta: " + actual.name());
        }
        EstadoJustificacion nuevo = cmd.accion().estadoResultante();
        return repo.resolve(cmd.justificacionId(), nuevo, cmd.usuarioId(),
                cmd.motivoRechazo(), cmd.usuarioId().toString());
    }
}
