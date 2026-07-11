package mx.ades.modules.movilidad.application.service;

import mx.ades.modules.movilidad.domain.port.in.RegistrarBajaUseCase;
import mx.ades.modules.movilidad.domain.port.in.RegistrarCambioGrupoUseCase;
import mx.ades.modules.movilidad.domain.port.out.MovilidadRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

/**
 * Caso de uso: cambios de grupo y bajas/traslados de alumnos entre planteles.
 * Implementa {@link RegistrarCambioGrupoUseCase} y {@link RegistrarBajaUseCase}
 * coordinando el dominio de movilidad con el puerto de repositorio, validando
 * capacidad de grupos destino, gestionando inscripciones y activando o
 * desactivando alumnos según el tipo de baja registrado.
 *
 * @author ADES
 * @since 2026
 */
public class MovilidadApplicationService
        implements RegistrarCambioGrupoUseCase, RegistrarBajaUseCase {

    private final MovilidadRepositoryPort repo;

    public MovilidadApplicationService(MovilidadRepositoryPort repo) {
        this.repo = repo;
    }

    // ── CAMBIO DE GRUPO ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public RegistrarCambioGrupoUseCase.Result ejecutar(RegistrarCambioGrupoUseCase.Command cmd) {
        var insc = repo.findInscripcionActiva(cmd.estudianteId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "El alumno no tiene inscripción activa"));

        if (insc.grupoId().equals(cmd.grupoDestinoId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El alumno ya está inscrito en ese grupo");

        var dest = repo.findGrupo(cmd.grupoDestinoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Grupo destino no encontrado o inactivo"));

        if (dest.estaLleno())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Grupo destino lleno (" + dest.inscritos() + "/" + dest.capacidadMaxima() + ")");

        var cambioId = repo.guardarCambioGrupo(
                cmd.estudianteId(), insc.id(),
                insc.grupoId(), cmd.grupoDestinoId(),
                cmd.motivo(), cmd.autorizadoPorId());

        repo.actualizarGrupoInscripcion(insc.id(), cmd.grupoDestinoId(), cmd.usuarioModificacion());

        return new RegistrarCambioGrupoUseCase.Result(insc.nombreGrupo(), dest.nombreGrupo(), cambioId);
    }

    // ── BAJA / TRASLADO ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public RegistrarBajaUseCase.Result ejecutar(RegistrarBajaUseCase.Command cmd) {
        var insc = repo.findInscripcionActiva(cmd.estudianteId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "El alumno no tiene inscripción activa"));

        repo.desactivarInscripcion(insc.id(), cmd.usuarioModificacion());

        LocalDate efectiva = cmd.fechaEfectiva() != null ? cmd.fechaEfectiva() : LocalDate.now();

        repo.guardarBaja(
                cmd.estudianteId(), insc.id(), cmd.tipo().tipoBajaDb(),
                cmd.motivo(), efectiva, cmd.fechaReingreso(),
                cmd.plantelDestino(), cmd.claveCtDestino(),
                cmd.observaciones(), cmd.autorizadoPorId());

        if (cmd.tipo().desactivaEstudiante()) {
            repo.desactivarEstudiante(cmd.estudianteId(), cmd.usuarioModificacion());
        }

        // Traslado interno: crear nueva inscripción en plantel destino
        if (cmd.grupoDestinoId() != null) {
            var ciclo = repo.findCicloVigente()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "No hay ciclo escolar vigente activo"));
            repo.crearInscripcion(cmd.estudianteId(), cmd.grupoDestinoId(), ciclo, cmd.usuarioModificacion());
        }

        return new RegistrarBajaUseCase.Result(
                cmd.tipo().name().replace('_', ' ') + " registrado",
                cmd.tipo(), efectiva);
    }
}
