package mx.ades.modules.gradebook.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio publicado cuando una calificación de periodo es cerrada definitivamente.
 *
 * @author ADES
 * @since 2026
 */
public record CalificacionCerradaEvent(UUID calPeriodoId, String cerradoPor, Instant ocurridoEn) {}
