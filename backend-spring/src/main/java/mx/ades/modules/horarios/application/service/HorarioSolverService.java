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
import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.score.analysis.ScoreAnalysis;
import com.fasterxml.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class HorarioSolverService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HorarioSolverService.class);

    private final SolverManager<HorarioPlan> solverManager;
    private final SolutionManager<HorarioPlan, HardSoftScore> solutionManager;
    private final HorarioCorridaRepository corridaRepository;
    private final HorarioRepository horarioRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

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
                null,
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
                    null,
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

    private final mx.ades.modules.horarios.config.HorarioReglaRepository reglaRepository;
    private final mx.ades.modules.horarios.config.HorarioFranjaRepository franjaRepository;
    private final mx.ades.modules.horarios.AsignacionDocenteRepository asignacionDocenteRepository;

    public UUID iniciarCorrida(UUID plantelId, UUID cicloEscolarId, String generadoPor,
            List<HorarioTimeslot> providedTimeslots, List<HorarioLeccion> lecciones) {
        HorarioCorrida corrida = new HorarioCorrida();
        corrida.setPlantelId(plantelId);
        corrida.setCicloEscolarId(cicloEscolarId);
        corrida.setGeneradoPor(generadoPor);
        corrida.setEstado("PENDIENTE");
        Integer maxVersion = corridaRepository.findMaxVersionByPlantelIdAndCicloEscolarId(plantelId, cicloEscolarId);
        corrida.setVersion(maxVersion == null ? 1 : maxVersion + 1);
        corrida = corridaRepository.save(corrida);

        for (HorarioLeccion leccion : lecciones) {
            leccion.setCorridaId(corrida.getId());
            if (leccion.getCicloEscolarId() == null) {
                leccion.setCicloEscolarId(cicloEscolarId);
            }
            if (leccion.getMateriaNombre() == null && leccion.getMateriaId() != null) {
                String matNombre = jdbc.queryForObject("SELECT nombre_materia FROM ades_materias WHERE id = ?", String.class, leccion.getMateriaId());
                leccion.setMateriaNombre(matNombre);
            }
            if (leccion.getGradoNumero() == null && leccion.getGrupoId() != null) {
                Integer gradoNum = jdbc.queryForObject("SELECT gr.numero_grado FROM ades_grados gr JOIN ades_grupos g ON g.grado_id = gr.id WHERE g.id = ?", Integer.class, leccion.getGrupoId());
                leccion.setGradoNumero(gradoNum);
            }
        }

        // Extraer Nivel Educativo del primer grupo
        UUID nivelId = null;
        if (!lecciones.isEmpty() && lecciones.get(0).getGrupoId() != null) {
            try {
                nivelId = jdbc.queryForObject(
                    "SELECT gr.nivel_educativo_id FROM ades_grados gr JOIN ades_grupos g ON g.grado_id = gr.id WHERE g.id = ?",
                    UUID.class, lecciones.get(0).getGrupoId());
            } catch (Exception ignored) {}
        }

        // Obtener Franjas de la BD
        List<mx.ades.modules.horarios.config.HorarioFranja> franjasDb = franjaRepository.findFranjasAplicables(plantelId, cicloEscolarId, nivelId);
        List<HorarioTimeslot> timeslots = franjasDb.stream()
            .map(f -> new HorarioTimeslot(f.getId(), f.getDiaSemana().intValue(), f.getHoraInicio(), f.getHoraFin(), null))
            .collect(Collectors.toList());

        // Si no hay franjas en BD, usar las proveídas (fallback)
        if (timeslots.isEmpty() && providedTimeslots != null) {
            timeslots = providedTimeslots;
        }

        HorarioPlan plan = new HorarioPlan();
        plan.setTimeslots(timeslots);
        plan.setLecciones(lecciones);

        List<mx.ades.modules.horarios.config.HorarioIndisponibilidad> indisponibilidades = jdbc.query(
            "SELECT profesor_id, franja_id, tipo FROM ades_horario_indisponibilidad WHERE ciclo_escolar_id = ?",
            (rs, rowNum) -> {
                var ind = new mx.ades.modules.horarios.config.HorarioIndisponibilidad();
                ind.setProfesorId(rs.getObject("profesor_id", UUID.class));
                ind.setFranjaId(rs.getObject("franja_id", UUID.class));
                ind.setTipo(rs.getString("tipo"));
                return ind;
            },
            cicloEscolarId
        );
        plan.setIndisponibilidades(indisponibilidades);

        // Filtrar reglas por nivel
        List<mx.ades.modules.horarios.config.HorarioRegla> reglas = reglaRepository.findByPlantelIdAndCicloEscolarIdAndActivaTrueAndIsActiveTrue(plantelId, cicloEscolarId);
        final UUID nId = nivelId;
        reglas = reglas.stream().filter(r -> r.getNivelEducativoId() == null || r.getNivelEducativoId().equals(nId)).toList();
        plan.setReglas(reglas);

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

    /**
     * Calcula las lecciones que el generador de horarios debe programar para un plantel/ciclo:
     * une las ya existentes en {@code ades_horarios} (con su horario ya asignado) con las
     * pendientes derivadas de {@code ades_asignaciones_docentes} × {@code ades_materias_plan.horas_semana}
     * que aún no tienen suficientes sesiones agendadas. Sin esto, el solver no tiene forma de
     * generar un horario desde cero — solo puede reordenar lo que ya existe.
     */
    public List<HorarioLeccion> generarLeccionesSugeridas(UUID plantelId, UUID cicloEscolarId) {
        List<mx.ades.modules.horarios.AsignacionDocente> asignaciones =
                asignacionDocenteRepository.findByPlantelIdAndCicloEscolarId(plantelId, cicloEscolarId);

        List<HorarioLeccion> resultado = new java.util.ArrayList<>();

        for (mx.ades.modules.horarios.AsignacionDocente asignacion : asignaciones) {
            List<Horario> existentes = horarioRepository.findByGrupoId(asignacion.getGrupoId()).stream()
                    .filter(h -> h.getMateriaId().equals(asignacion.getMateriaId())
                            && h.getCicloEscolarId().equals(cicloEscolarId)
                            && Boolean.TRUE.equals(h.getIsActive()))
                    .toList();

            for (Horario h : existentes) {
                HorarioLeccion leccion = new HorarioLeccion();
                leccion.setId(h.getId());
                leccion.setCorridaId(null);
                leccion.setCicloEscolarId(cicloEscolarId);
                leccion.setGrupoId(h.getGrupoId());
                leccion.setMateriaId(h.getMateriaId());
                leccion.setProfesorId(h.getProfesorId());
                leccion.setAulaId(h.getAulaId());
                // El timeslot debe traer el id REAL de ades_horario_franjas — Timefold rechaza
                // ("outside of the related value range") cualquier valor asignado que no exista,
                // por identidad completa, en la lista de valores del solver. Antes se creaba con
                // id=null, lo cual siempre rompía cualquier re-corrida sobre un horario ya generado
                // (bug real encontrado al reoptimizar). Si la franja original ya no existe, se deja
                // el timeslot sin asignar (no fijado) para que el solver le busque un lugar válido.
                UUID franjaId = resolverFranjaId(cicloEscolarId, h.getGrupoId(), h.getDiaSemana(), h.getHoraInicio(), h.getHoraFin());
                if (franjaId != null) {
                    leccion.setFijado(h.getFijado());
                    leccion.setTimeslot(new HorarioTimeslot(franjaId, h.getDiaSemana().intValue(), h.getHoraInicio(), h.getHoraFin(), null));
                } else {
                    leccion.setFijado(false);
                    leccion.setTimeslot(null);
                }
                resultado.add(leccion);
            }

            double horasSemana = horasSemanaParaAsignacion(asignacion.getMateriaId(), asignacion.getGrupoId(), cicloEscolarId);
            int leccionesRequeridas = Math.max(1, (int) Math.round(horasSemana));
            int faltantes = leccionesRequeridas - existentes.size();
            for (int i = 0; i < faltantes; i++) {
                HorarioLeccion pendiente = new HorarioLeccion();
                pendiente.setId(UUID.randomUUID());
                pendiente.setCorridaId(null);
                pendiente.setCicloEscolarId(cicloEscolarId);
                pendiente.setGrupoId(asignacion.getGrupoId());
                pendiente.setMateriaId(asignacion.getMateriaId());
                pendiente.setProfesorId(asignacion.getProfesorId());
                pendiente.setAulaId(null);
                pendiente.setFijado(false);
                pendiente.setTimeslot(null);
                resultado.add(pendiente);
            }
        }
        return resultado;
    }

    /** Resuelve el id real de ades_horario_franjas para el día/hora de un Horario ya existente,
     * scoping por el nivel/plantel del grupo (una franja puede ser global o específica de plantel). */
    private UUID resolverFranjaId(UUID cicloEscolarId, UUID grupoId, short diaSemana,
            java.time.LocalTime horaInicio, java.time.LocalTime horaFin) {
        try {
            return jdbc.queryForObject("""
                SELECT hf.id FROM ades_horario_franjas hf
                JOIN ades_grupos g ON g.id = ?
                JOIN ades_grados gr ON gr.id = g.grado_id
                WHERE hf.ciclo_escolar_id = ? AND hf.dia_semana = ? AND hf.hora_inicio = ? AND hf.hora_fin = ?
                  AND hf.nivel_educativo_id = gr.nivel_educativo_id
                  AND (hf.plantel_id IS NULL OR hf.plantel_id = gr.plantel_id)
                LIMIT 1
                """, UUID.class, grupoId, cicloEscolarId, diaSemana, horaInicio, horaFin);
        } catch (Exception e) {
            log.warn("resolverFranjaId sin match (grupo={}, ciclo={}, dia={}, {}-{}): {}",
                    grupoId, cicloEscolarId, diaSemana, horaInicio, horaFin, e.toString());
            return null;
        }
    }

    /** Horas/semana desde el plan curricular del grado (ades_materias_plan); si no hay plan publicado, cae a ades_materias.horas_semana. */
    private double horasSemanaParaAsignacion(UUID materiaId, UUID grupoId, UUID cicloEscolarId) {
        try {
            Double horasPlan = jdbc.queryForObject(
                    """
                    SELECT mp.horas_semana FROM ades_materias_plan mp
                    JOIN ades_grupos g ON g.grado_id = mp.grado_id
                    WHERE mp.materia_id = ? AND g.id = ? AND mp.ciclo_escolar_id = ?
                      AND mp.is_active = true AND mp.estado_publicacion = 'PUBLICADO'
                    """,
                    Double.class, materiaId, grupoId, cicloEscolarId);
            if (horasPlan != null && horasPlan > 0) {
                return horasPlan;
            }
        } catch (Exception ignored) {
            // sin fila en el plan curricular para este grado/ciclo — cae al fallback
        }
        try {
            Double horasMateria = jdbc.queryForObject(
                    "SELECT horas_semana FROM ades_materias WHERE id = ?", Double.class, materiaId);
            return horasMateria != null ? horasMateria : 1.0;
        } catch (Exception ignored) {
            return 1.0;
        }
    }

    public java.util.Map<String, Object> verificarHorario(UUID plantelId, UUID cicloEscolarId,
            List<HorarioTimeslot> providedTimeslots, List<HorarioLeccion> lecciones) {
        
        // Extraer Nivel Educativo del primer grupo
        UUID nivelId = null;
        if (!lecciones.isEmpty() && lecciones.get(0).getGrupoId() != null) {
            try {
                nivelId = jdbc.queryForObject(
                    "SELECT gr.nivel_educativo_id FROM ades_grados gr JOIN ades_grupos g ON g.grado_id = gr.id WHERE g.id = ?",
                    UUID.class, lecciones.get(0).getGrupoId());
            } catch (Exception ignored) {}
        }

        // Obtener Franjas de la BD
        List<mx.ades.modules.horarios.config.HorarioFranja> franjasDb = franjaRepository.findFranjasAplicables(plantelId, cicloEscolarId, nivelId);
        List<HorarioTimeslot> timeslots = franjasDb.stream()
            .map(f -> new HorarioTimeslot(f.getId(), f.getDiaSemana().intValue(), f.getHoraInicio(), f.getHoraFin(), null))
            .collect(Collectors.toList());

        if (timeslots.isEmpty() && providedTimeslots != null) {
            timeslots = providedTimeslots;
        }

        HorarioPlan plan = new HorarioPlan();
        plan.setTimeslots(timeslots);
        plan.setLecciones(lecciones);

        List<mx.ades.modules.horarios.config.HorarioIndisponibilidad> indisponibilidades = jdbc.query(
            "SELECT profesor_id, franja_id, tipo FROM ades_horario_indisponibilidad WHERE ciclo_escolar_id = ?",
            (rs, rowNum) -> {
                var ind = new mx.ades.modules.horarios.config.HorarioIndisponibilidad();
                ind.setProfesorId(rs.getObject("profesor_id", UUID.class));
                ind.setFranjaId(rs.getObject("franja_id", UUID.class));
                ind.setTipo(rs.getString("tipo"));
                return ind;
            },
            cicloEscolarId
        );
        plan.setIndisponibilidades(indisponibilidades);

        List<mx.ades.modules.horarios.config.HorarioRegla> reglas = reglaRepository.findByPlantelIdAndCicloEscolarIdAndActivaTrueAndIsActiveTrue(plantelId, cicloEscolarId);
        final UUID nId = nivelId;
        reglas = reglas.stream().filter(r -> r.getNivelEducativoId() == null || r.getNivelEducativoId().equals(nId)).toList();
        plan.setReglas(reglas);

        solutionManager.update(plan);
        ScoreAnalysis<HardSoftScore> analysis = solutionManager.analyze(plan);
        
        try {
            return objectMapper.readValue(objectMapper.writeValueAsString(analysis), new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Error parsing ScoreAnalysis", e);
        }
    }

    private void persistirResultado(UUID corridaId, HorarioPlan solution) {
        List<Horario> horarios = solution.getLecciones().stream()
                .filter(leccion -> leccion.getTimeslot() != null)
                .map(leccion -> toHorario(corridaId, leccion))
                .toList();
        horarioRepository.saveAll(horarios);

        String analysisJson = null;
        try {
            // SolutionManager.analyze() requiere Timefold Enterprise (no licenciado en este
            // despliegue) — siempre lanza excepción aquí. Se captura y se guarda un JSON de
            // error válido en vez del breakdown detallado (antes se concatenaba el mensaje
            // crudo, con saltos de línea, produciendo JSON inválido que abortaba el UPDATE
            // completo y dejaba la corrida atascada en SOLVING para siempre).
            ScoreAnalysis<HardSoftScore> scoreAnalysis = solutionManager.analyze(solution);
            analysisJson = objectMapper.writeValueAsString(scoreAnalysis);
        } catch (Exception e) {
            analysisJson = toErrorJson(e.getMessage());
        }
        
        final String finalAnalysisJson = analysisJson;

        corridaRepository.findById(corridaId).ifPresent(corrida -> {
            corrida.setEstado("SOLUCIONADA");
            corrida.setScoreText(solution.getScore() == null ? null : solution.getScore().toString());
            corrida.setScoreAnalysisJson(finalAnalysisJson);
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
        log.error("Corrida de solver {} falló", corridaId, throwable);
        corridaRepository.findById(corridaId).ifPresent(corrida -> {
            corrida.setEstado("ERROR");
            corrida.setScoreAnalysisJson(toErrorJson(throwable.getMessage()));
            corridaRepository.save(corrida);
        });
    }

    private Horario toHorario(UUID corridaId, HorarioLeccion leccion) {
        Horario horario = new Horario();
        // Id nuevo SIEMPRE, nunca leccion.getId(): cada corrida crea su propio set de filas
        // ades_horarios ligadas a su corridaId. Cuando la lección viene de "existentes"
        // (generarLeccionesSugeridas), leccion.getId() es el id de la fila de una corrida
        // ANTERIOR — reusarlo aquí violaba la PK de ades_horarios en cuanto se reoptimizaba
        // un horario ya generado (bug real encontrado esta sesión).
        horario.setId(UUID.randomUUID());
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

    /** Serializa un mensaje de error como JSON válido vía Jackson (evita saltos de línea/comillas sin escapar). */
    private String toErrorJson(String message) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of("error", message == null ? "" : message));
        } catch (Exception e) {
            return "{\"error\":\"unknown\"}";
        }
    }
}