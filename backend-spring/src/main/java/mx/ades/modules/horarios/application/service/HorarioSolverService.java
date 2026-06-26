package mx.ades.modules.horarios.application.service;

import ai.timefold.solver.core.api.solver.SolverManager;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.horarios.Horario;
import mx.ades.modules.horarios.HorarioCorridaRepository;
import mx.ades.modules.horarios.HorarioRepository;
import mx.ades.modules.horarios.solver.HorarioCorrida;
import mx.ades.modules.horarios.solver.HorarioLeccion;
import mx.ades.modules.horarios.solver.HorarioPlan;
import mx.ades.modules.horarios.solver.HorarioTimeslot;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class HorarioSolverService {

    private final SolverManager<HorarioPlan> solverManager;
    private final HorarioCorridaRepository corridaRepository;
    private final HorarioRepository horarioRepository;

        public void fijarHorariosDeCorrida(UUID corridaId, List<UUID> horarioIds) {
        if (horarioIds == null || horarioIds.isEmpty()) {
            return;
        }
        Set<UUID> permitidos = Set.copyOf(horarioIds);
        List<Horario> horarios = horarioRepository.findByCorridaIdAndIsActiveTrue(corridaId).stream()
            .filter(horario -> permitidos.contains(horario.getId()))
            .peek(horario -> horario.setFijado(true))
            .toList();
        horarioRepository.saveAll(horarios);
        }

        public UUID regenerarDesdeCorrida(UUID corridaId, List<UUID> horarioIds, String generadoPor) {
        HorarioCorrida corridaBase = corridaRepository.findById(corridaId)
            .orElseThrow(() -> new IllegalArgumentException("Corrida no encontrada"));

        Set<UUID> fijados = horarioIds == null ? Set.of() : Set.copyOf(horarioIds);
        List<Horario> horariosBase = horarioRepository.findByCorridaIdAndIsActiveTrue(corridaId);
        if (horariosBase.isEmpty()) {
            throw new IllegalArgumentException("La corrida no contiene horarios para regenerar");
        }

        List<HorarioTimeslot> timeslots = horariosBase.stream()
            .map(horario -> new HorarioTimeslot(
                horario.getDiaSemana().intValue(),
                horario.getHoraInicio(),
                horario.getHoraFin(),
                null))
            .distinct()
            .toList();

        List<HorarioLeccion> lecciones = horariosBase.stream()
            .map(horario -> {
                HorarioLeccion leccion = new HorarioLeccion();
                leccion.setId(horario.getId());
                leccion.setCorridaId(null);
                leccion.setCicloEscolarId(horario.getCicloEscolarId());
                leccion.setGrupoId(horario.getGrupoId());
                leccion.setMateriaId(horario.getMateriaId());
                leccion.setProfesorId(horario.getProfesorId());
                leccion.setAulaId(horario.getAulaId());
                leccion.setFijado(fijados.contains(horario.getId()));
                leccion.setTimeslot(fijados.contains(horario.getId())
                    ? new HorarioTimeslot(
                    horario.getDiaSemana().intValue(),
                    horario.getHoraInicio(),
                    horario.getHoraFin(),
                    null)
                    : null);
                return leccion;
            })
            .collect(Collectors.toList());

        return iniciarCorrida(
            corridaBase.getPlantelId(),
            corridaBase.getCicloEscolarId(),
            generadoPor,
            timeslots,
            lecciones);
        }

    public UUID iniciarCorrida(UUID plantelId, UUID cicloEscolarId, String generadoPor,
            List<HorarioTimeslot> timeslots, List<HorarioLeccion> lecciones) {
        HorarioCorrida corrida = new HorarioCorrida();
        corrida.setPlantelId(plantelId);
        corrida.setCicloEscolarId(cicloEscolarId);
        corrida.setGeneradoPor(generadoPor);
        corrida.setEstado("PENDIENTE");
        corrida = corridaRepository.save(corrida);

        for (HorarioLeccion leccion : lecciones) {
            leccion.setCorridaId(corrida.getId());
            if (leccion.getCicloEscolarId() == null) {
                leccion.setCicloEscolarId(cicloEscolarId);
            }
        }

        HorarioPlan plan = new HorarioPlan();
        plan.setTimeslots(timeslots);
        plan.setLecciones(lecciones);

        UUID corridaId = corrida.getId();
        solverManager.solveBuilder()
                .withProblemId(corridaId)
                .withProblem(plan)
                .withSolverJobStartedEventConsumer(event -> marcarEstado(corridaId, "SOLVING"))
                .withFinalBestSolutionEventConsumer(event -> persistirResultado(corridaId, event.solution()))
                .withExceptionHandler((problemId, throwable) -> marcarFallo(corridaId, throwable))
                .run();

        return corridaId;
    }

    private void persistirResultado(UUID corridaId, HorarioPlan solution) {
        List<Horario> horarios = solution.getLecciones().stream()
                .filter(leccion -> leccion.getTimeslot() != null)
                .map(leccion -> toHorario(corridaId, leccion))
                .toList();
        horarioRepository.saveAll(horarios);

        corridaRepository.findById(corridaId).ifPresent(corrida -> {
            corrida.setEstado("SOLUCIONADA");
            corrida.setScoreText(solution.getScore() == null ? null : solution.getScore().toString());
            corridaRepository.save(corrida);
        });
    }

    private void marcarEstado(UUID corridaId, String estado) {
        corridaRepository.findById(corridaId).ifPresent(corrida -> {
            corrida.setEstado(estado);
            corridaRepository.save(corrida);
        });
    }

    private void marcarFallo(UUID corridaId, Throwable throwable) {
        corridaRepository.findById(corridaId).ifPresent(corrida -> {
            corrida.setEstado("ERROR");
            corrida.setScoreAnalysisJson("{\"error\":\"" + escapeJson(throwable.getMessage()) + "\"}");
            corridaRepository.save(corrida);
        });
    }

    private Horario toHorario(UUID corridaId, HorarioLeccion leccion) {
        Horario horario = new Horario();
        horario.setId(leccion.getId());
        horario.setGrupoId(leccion.getGrupoId());
        horario.setMateriaId(leccion.getMateriaId());
        horario.setProfesorId(leccion.getProfesorId());
        horario.setAulaId(leccion.getAulaId());
        horario.setCicloEscolarId(leccion.getCicloEscolarId());
        horario.setCorridaId(corridaId);
        horario.setDiaSemana(leccion.getTimeslot().diaSemana().shortValue());
        horario.setHoraInicio(leccion.getTimeslot().horaInicio());
        horario.setHoraFin(leccion.getTimeslot().horaFin());
        horario.setOrigen("TIMEFOLD");
        horario.setFijado(leccion.isFijado());
        horario.setIsActive(true);
        return horario;
    }

    private static String escapeJson(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}