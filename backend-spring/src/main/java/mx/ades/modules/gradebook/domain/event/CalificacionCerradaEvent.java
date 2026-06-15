package mx.ades.modules.gradebook.domain.event;

import java.time.Instant;
import java.util.UUID;

public record CalificacionCerradaEvent(UUID calPeriodoId, String cerradoPor, Instant ocurridoEn) {}
