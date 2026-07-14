package mx.ades.modules.horarios.solver;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;

public class HorarioConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                profesorEnDosClasesAlMismoTiempo(factory),
                grupoEnDosClasesAlMismoTiempo(factory),
                aulaEnDosClasesAlMismoTiempo(factory),
                reglaDiasPermitidos(factory),
                reglaDiasNoConsecutivos(factory),
                reglaVentanaHoraria(factory),
                bloqueContiguo(factory),
                maxHorasDia(factory),
                huecosDocente(factory),
                consecutivasMax(factory),
                sincronizarMateria(factory),
                ventanaHorariaDocente(factory),
                diasNoPermitidosDocente(factory),
                materiaFraccionada30Min(factory),
                indisponibilidadRojo(factory),
                indisponibilidadAmarillo(factory)
        };
    }

    private Constraint profesorEnDosClasesAlMismoTiempo(ConstraintFactory factory) {
        return factory.forEach(HorarioLeccion.class)
                .filter(lesson -> lesson.getTimeslot() != null && lesson.getProfesorId() != null)
                .join(HorarioLeccion.class,
                        Joiners.equal(HorarioLeccion::getProfesorId),
                        Joiners.equal(HorarioLeccion::getTimeslot),
                        Joiners.lessThan(HorarioLeccion::getId))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Profesor en dos clases al mismo tiempo");
    }

    private Constraint grupoEnDosClasesAlMismoTiempo(ConstraintFactory factory) {
        return factory.forEach(HorarioLeccion.class)
                .filter(lesson -> lesson.getTimeslot() != null && lesson.getGrupoId() != null)
                .join(HorarioLeccion.class,
                        Joiners.equal(HorarioLeccion::getGrupoId),
                        Joiners.equal(HorarioLeccion::getTimeslot),
                        Joiners.lessThan(HorarioLeccion::getId))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Grupo en dos clases al mismo tiempo");
    }

    private Constraint aulaEnDosClasesAlMismoTiempo(ConstraintFactory factory) {
        return factory.forEach(HorarioLeccion.class)
                .filter(lesson -> lesson.getTimeslot() != null && lesson.getAulaId() != null)
                .join(HorarioLeccion.class,
                        Joiners.equal(HorarioLeccion::getAulaId),
                        Joiners.equal(HorarioLeccion::getTimeslot),
                        Joiners.lessThan(HorarioLeccion::getId))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Aula en dos clases al mismo tiempo");
    }

    private Constraint reglaDiasPermitidos(ConstraintFactory factory) {
        return factory.forEach(HorarioLeccion.class)
                .filter(lesson -> lesson.getTimeslot() != null)
                .join(mx.ades.modules.horarios.config.HorarioRegla.class,
                        Joiners.filtering((lesson, regla) -> {
                            if (!"dias_permitidos".equals(regla.getTipo()) || !regla.getActiva()) return false;
                            Object mat = regla.getParams().get("materia");
                            if (mat == null || !mat.equals(lesson.getMateriaNombre())) return false;
                            
                            Object diasObj = regla.getParams().get("dias");
                            if (diasObj instanceof java.util.List diasList) {
                                // Convert to integer comparison
                                return !diasList.contains(lesson.getTimeslot().diaSemana()) 
                                    && !diasList.contains(String.valueOf(lesson.getTimeslot().diaSemana()));
                            }
                            return false;
                        }))
                .penalize(HardSoftScore.ONE_HARD, (lesson, regla) -> regla.getPeso())
                .asConstraint("Dinamica - Dias Permitidos");
    }

    private Constraint reglaDiasNoConsecutivos(ConstraintFactory factory) {
        return factory.forEach(HorarioLeccion.class)
                .filter(lesson -> lesson.getTimeslot() != null)
                .join(HorarioLeccion.class,
                        Joiners.equal(HorarioLeccion::getGrupoId),
                        Joiners.equal(HorarioLeccion::getMateriaNombre),
                        Joiners.lessThan(HorarioLeccion::getId))
                .filter((l1, l2) -> l1.getTimeslot() != null && l2.getTimeslot() != null && 
                                    Math.abs(l1.getTimeslot().diaSemana() - l2.getTimeslot().diaSemana()) == 1)
                .join(mx.ades.modules.horarios.config.HorarioRegla.class,
                        Joiners.filtering((l1, l2, regla) -> {
                            if (!"dias_no_consecutivos".equals(regla.getTipo()) || !regla.getActiva()) return false;
                            Object mat = regla.getParams().get("materia");
                            return mat != null && mat.equals(l1.getMateriaNombre());
                        }))
                .penalize(HardSoftScore.ONE_HARD, (l1, l2, regla) -> regla.getPeso())
                .asConstraint("Dinamica - Dias no consecutivos");
    }

    private Constraint reglaVentanaHoraria(ConstraintFactory factory) {
        return factory.forEach(HorarioLeccion.class)
                .filter(lesson -> lesson.getTimeslot() != null)
                .join(mx.ades.modules.horarios.config.HorarioRegla.class,
                        Joiners.filtering((lesson, regla) -> {
                            if (!"ventana_horaria".equals(regla.getTipo()) || !regla.getActiva()) return false;
                            Object mat = regla.getParams().get("materia");
                            if (mat == null || !mat.equals(lesson.getMateriaNombre())) return false;
                            
                            String modo = (String) regla.getParams().get("modo");
                            String horaStr = (String) regla.getParams().get("hora"); 
                            if (modo == null || horaStr == null) return false;
                            
                            java.time.LocalTime limit = java.time.LocalTime.parse(horaStr);
                            
                            if ("antes_de".equals(modo)) {
                                return !lesson.getTimeslot().horaInicio().isBefore(limit);
                            } else if ("despues_de".equals(modo)) {
                                return lesson.getTimeslot().horaInicio().isBefore(limit);
                            }
                            return false;
                        }))
                .penalize(HardSoftScore.ONE_HARD, (lesson, regla) -> regla.getPeso())
                .asConstraint("Dinamica - Ventana horaria");
    }

    private Constraint bloqueContiguo(ConstraintFactory factory) {
        return factory.forEach(HorarioLeccion.class)
                .filter(lesson -> lesson.getTimeslot() != null)
                .join(mx.ades.modules.horarios.config.HorarioRegla.class,
                        Joiners.filtering((lesson, regla) -> {
                            if (!"bloque_contiguo".equals(regla.getTipo()) || !regla.getActiva()) return false;
                            Object matObj = regla.getParams().get("materias");
                            if (matObj instanceof java.util.List) {
                                return ((java.util.List<?>) matObj).contains(lesson.getMateriaNombre());
                            }
                            return false;
                        }))
                .join(HorarioLeccion.class,
                        Joiners.equal((l1, r) -> l1.getGrupoId(), HorarioLeccion::getGrupoId),
                        Joiners.equal((l1, r) -> l1.getMateriaNombre(), HorarioLeccion::getMateriaNombre),
                        Joiners.lessThan((l1, r) -> l1.getId(), HorarioLeccion::getId))
                .filter((l1, r, l2) -> l1.getTimeslot() != null && l2.getTimeslot() != null 
                        && l1.getTimeslot().diaSemana() == l2.getTimeslot().diaSemana()
                        && Math.abs(l1.getTimeslot().horaInicio().getHour() - l2.getTimeslot().horaInicio().getHour()) == 1)
                .reward(HardSoftScore.ONE_SOFT, (l1, r, l2) -> r.getPeso())
                .asConstraint("Dinamica - Bloque Contiguo");
    }

    private Constraint maxHorasDia(ConstraintFactory factory) {
        return factory.forEach(HorarioLeccion.class)
                .filter(lesson -> lesson.getTimeslot() != null)
                .join(mx.ades.modules.horarios.config.HorarioRegla.class,
                        Joiners.filtering((lesson, regla) -> "max_horas_dia".equals(regla.getTipo()) && regla.getActiva()))
                .groupBy((lesson, regla) -> java.util.List.of(lesson.getGrupoId(), lesson.getMateriaNombre(), lesson.getTimeslot().diaSemana()),
                         (lesson, regla) -> regla,
                         ai.timefold.solver.core.api.score.stream.ConstraintCollectors.countBi())
                .filter((key, regla, count) -> {
                    Object maxObj = regla.getParams().get("default");
                    if (maxObj instanceof Integer) {
                        return count > (Integer) maxObj;
                    }
                    return false;
                })
                .penalize(HardSoftScore.ONE_HARD, (key, r, c) -> r.getPeso() * (c - (Integer) r.getParams().get("default")))
                .asConstraint("Dinamica - Max Horas Dia");
    }

    private Constraint huecosDocente(ConstraintFactory factory) {
        return factory.forEach(HorarioLeccion.class)
                .filter(lesson -> lesson.getTimeslot() != null && lesson.getProfesorId() != null)
                .groupBy(HorarioLeccion::getProfesorId,
                         l -> l.getTimeslot().diaSemana(),
                         ai.timefold.solver.core.api.score.stream.ConstraintCollectors.toList())
                .penalize(HardSoftScore.ONE_SOFT, (profId, dia, lecciones) -> {
                    // No confiar en que el timeslot siga no-nulo al momento de esta
                    // consecuencia: el filter/groupBy de más arriba se evalúa por evento,
                    // pero esta lista de entidades mutables puede llegar a re-evaluarse
                    // con una lección ya desasignada por otro movimiento del solver
                    // (causaba NullPointerException real durante corridas reales — ver
                    // sesión 2026-07-13).
                    java.util.List<HorarioLeccion> conTimeslot = lecciones.stream()
                            .filter(l -> l.getTimeslot() != null)
                            .sorted(java.util.Comparator.comparing(l -> l.getTimeslot().horaInicio()))
                            .toList();
                    if (conTimeslot.size() < 2) return 0;
                    int totalGaps = 0;
                    for (int i = 0; i < conTimeslot.size() - 1; i++) {
                        java.time.LocalTime finActual = conTimeslot.get(i).getTimeslot().horaFin();
                        java.time.LocalTime inicioSiguiente = conTimeslot.get(i + 1).getTimeslot().horaInicio();
                        long minutosHueco = java.time.Duration.between(finActual, inicioSiguiente).toMinutes();
                        // Un receso típico es de ~20 mins. Si es más de 30 mins, se considera hueco.
                        if (minutosHueco > 30) {
                            totalGaps += Math.max(1, (int)(minutosHueco / 50));
                        }
                    }
                    return totalGaps;
                })
                .asConstraint("Calidad - Minimizar huecos docente");
    }

    private Constraint consecutivasMax(ConstraintFactory factory) {
        return factory.forEach(HorarioLeccion.class)
                .filter(lesson -> lesson.getTimeslot() != null && lesson.getProfesorId() != null)
                .join(HorarioLeccion.class,
                        Joiners.equal(HorarioLeccion::getProfesorId),
                        Joiners.equal(l -> l.getTimeslot().diaSemana()))
                .filter((l1, l2) -> l2.getTimeslot().horaInicio().getHour() - l1.getTimeslot().horaInicio().getHour() == 1)
                .join(HorarioLeccion.class,
                        Joiners.equal((l1, l2) -> l1.getProfesorId(), HorarioLeccion::getProfesorId),
                        Joiners.equal((l1, l2) -> l1.getTimeslot().diaSemana(), l -> l.getTimeslot().diaSemana()))
                .filter((l1, l2, l3) -> l3.getTimeslot().horaInicio().getHour() - l2.getTimeslot().horaInicio().getHour() == 1)
                .penalize(HardSoftScore.ONE_SOFT)
                .asConstraint("Calidad - Demasiadas consecutivas");
    }

    private Constraint sincronizarMateria(ConstraintFactory factory) {
        return factory.forEach(HorarioLeccion.class)
                .filter(lesson -> lesson.getTimeslot() != null && lesson.getGradoNumero() != null)
                .join(HorarioLeccion.class,
                        Joiners.equal(HorarioLeccion::getMateriaNombre),
                        Joiners.equal(HorarioLeccion::getGradoNumero),
                        Joiners.lessThan(HorarioLeccion::getId))
                .filter((l1, l2) -> !l1.getGrupoId().equals(l2.getGrupoId()) && !l1.getTimeslot().equals(l2.getTimeslot()))
                .join(mx.ades.modules.horarios.config.HorarioRegla.class,
                        Joiners.filtering((l1, l2, regla) -> {
                            if (!"sincronizar_materia".equals(regla.getTipo()) || !regla.getActiva()) return false;
                            Object mat = regla.getParams().get("materia");
                            return mat != null && mat.equals(l1.getMateriaNombre());
                        }))
                .penalize(HardSoftScore.ONE_HARD, (l1, l2, regla) -> regla.getPeso())
                .asConstraint("Avanzado - Sincronizar materia");
    }

    private Constraint ventanaHorariaDocente(ConstraintFactory factory) {
        return factory.forEach(HorarioLeccion.class)
                .filter(lesson -> lesson.getTimeslot() != null && lesson.getProfesorId() != null)
                .join(mx.ades.modules.horarios.config.HorarioRegla.class,
                        Joiners.filtering((lesson, regla) -> {
                            if (!"ventana_horaria_docente".equals(regla.getTipo()) || !regla.getActiva()) return false;
                            Object prof = regla.getParams().get("profesor_id");
                            if (prof == null || !prof.toString().equals(lesson.getProfesorId().toString())) return false;
                            
                            // Si aplica solo a un dia especifico
                            Object diaRegla = regla.getParams().get("dia");
                            if (diaRegla != null && !diaRegla.toString().equals(String.valueOf(lesson.getTimeslot().diaSemana()))) return false;
                            
                            String modo = (String) regla.getParams().get("modo");
                            String horaStr = (String) regla.getParams().get("hora"); 
                            if (modo == null || horaStr == null) return false;
                            
                            java.time.LocalTime limit = java.time.LocalTime.parse(horaStr);
                            
                            if ("antes_de".equals(modo)) {
                                return !lesson.getTimeslot().horaInicio().isBefore(limit);
                            } else if ("despues_de".equals(modo)) {
                                return lesson.getTimeslot().horaInicio().isBefore(limit);
                            }
                            return false;
                        }))
                .penalize(HardSoftScore.ONE_HARD, (lesson, regla) -> regla.getPeso())
                .asConstraint("Dinamica - Ventana horaria docente");
    }

    private Constraint diasNoPermitidosDocente(ConstraintFactory factory) {
        return factory.forEach(HorarioLeccion.class)
                .filter(lesson -> lesson.getTimeslot() != null && lesson.getProfesorId() != null)
                .join(mx.ades.modules.horarios.config.HorarioRegla.class,
                        Joiners.filtering((lesson, regla) -> {
                            if (!"dias_no_permitidos_docente".equals(regla.getTipo()) || !regla.getActiva()) return false;
                            Object prof = regla.getParams().get("profesor_id");
                            if (prof == null || !prof.toString().equals(lesson.getProfesorId().toString())) return false;
                            
                            Object diasObj = regla.getParams().get("dias");
                            if (diasObj instanceof java.util.List diasList) {
                                return diasList.contains(lesson.getTimeslot().diaSemana()) 
                                    || diasList.contains(String.valueOf(lesson.getTimeslot().diaSemana()));
                            }
                            return false;
                        }))
                .penalize(HardSoftScore.ONE_HARD, (l1, regla) -> regla.getPeso())
                .asConstraint("Avanzado - Dias no permitidos docente");
    }

    private Constraint materiaFraccionada30Min(ConstraintFactory factory) {
        return factory.forEach(HorarioLeccion.class)
                .filter(lesson -> lesson.getTimeslot() != null)
                .join(mx.ades.modules.horarios.config.HorarioRegla.class,
                        Joiners.filtering((lesson, regla) -> {
                            if (!"materia_fraccionada_30min".equals(regla.getTipo()) || !regla.getActiva()) return false;
                            Object mat = regla.getParams().get("materia");
                            return mat != null && mat.equals(lesson.getMateriaNombre());
                        }))
                .groupBy((lesson, regla) -> lesson.getGrupoId(),
                         (lesson, regla) -> lesson.getMateriaNombre(),
                         (lesson, regla) -> regla,
                         ai.timefold.solver.core.api.score.stream.ConstraintCollectors.toList((lesson, regla) -> lesson))
                .filter((grupoId, materia, regla, leccionesList) -> {
                    // Mismo riesgo que en huecosDocente: re-filtrar timeslot != null aquí
                    // en vez de confiar en el filter de más arriba (entidad mutable).
                    long count30 = leccionesList.stream().filter(l -> l.getTimeslot() != null).filter(l -> {
                        long mins = java.time.Duration.between(l.getTimeslot().horaInicio(), l.getTimeslot().horaFin()).toMinutes();
                        return mins > 0 && mins <= 40;
                    }).count();
                    long count50 = leccionesList.stream().filter(l -> l.getTimeslot() != null).filter(l -> {
                        long mins = java.time.Duration.between(l.getTimeslot().horaInicio(), l.getTimeslot().horaFin()).toMinutes();
                        return mins > 40;
                    }).count();
                    return count30 != 1 || count50 != 1;
                })
                .penalize(HardSoftScore.ONE_HARD, (grupoId, materia, regla, list) -> regla.getPeso())
                .asConstraint("Avanzado - Materia fraccionada debe tener un bloque de 50m y uno de 30m");
    }

    private Constraint indisponibilidadRojo(ConstraintFactory factory) {
        return factory.forEach(HorarioLeccion.class)
                .filter(lesson -> lesson.getTimeslot() != null && lesson.getProfesorId() != null && lesson.getTimeslot().id() != null)
                .join(mx.ades.modules.horarios.config.HorarioIndisponibilidad.class,
                        Joiners.equal(HorarioLeccion::getProfesorId, mx.ades.modules.horarios.config.HorarioIndisponibilidad::getProfesorId),
                        Joiners.equal(lesson -> lesson.getTimeslot().id(), mx.ades.modules.horarios.config.HorarioIndisponibilidad::getFranjaId))
                .filter((lesson, indisponibilidad) -> "NO_DISPONIBLE".equals(indisponibilidad.getTipo()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Profesor No Disponible en esta franja");
    }

    private Constraint indisponibilidadAmarillo(ConstraintFactory factory) {
        return factory.forEach(HorarioLeccion.class)
                .filter(lesson -> lesson.getTimeslot() != null && lesson.getProfesorId() != null && lesson.getTimeslot().id() != null)
                .join(mx.ades.modules.horarios.config.HorarioIndisponibilidad.class,
                        Joiners.equal(HorarioLeccion::getProfesorId, mx.ades.modules.horarios.config.HorarioIndisponibilidad::getProfesorId),
                        Joiners.equal(lesson -> lesson.getTimeslot().id(), mx.ades.modules.horarios.config.HorarioIndisponibilidad::getFranjaId))
                .filter((lesson, indisponibilidad) -> "CONDICIONAL".equals(indisponibilidad.getTipo()))
                .penalize(HardSoftScore.ONE_SOFT)
                .asConstraint("Profesor Condicional (Evitar asignar)");
    }
}