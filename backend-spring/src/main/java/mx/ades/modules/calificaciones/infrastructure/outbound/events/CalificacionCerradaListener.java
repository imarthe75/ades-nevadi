package mx.ades.modules.calificaciones.infrastructure.outbound.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ades.common.WebhookService;
import mx.ades.modules.calificaciones.domain.event.CalificacionCerradaEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listener de infraestructura — reacciona a CalificacionCerradaEvent.
 * Si el alumno reprobó, dispara webhook n8n para notificar a los padres.
 * @Async: no bloquea la transacción principal.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalificacionCerradaListener {

    private final WebhookService webhookService;

    @EventListener
    @Async
    public void onCalificacionCerrada(CalificacionCerradaEvent event) {
        if (!event.esReprobado()) return;

        log.info("Alumno {} reprobó materia {} — notificando padres via n8n",
                event.estudianteId(), event.materiaId());

        webhookService.dispatchWebhook("calificacion-reprobada", Map.of(
                "estudiante_id",  event.estudianteId().toString(),
                "materia_id",     event.materiaId().toString(),
                "grupo_id",       event.grupoId() != null ? event.grupoId().toString() : "",
                "periodo_id",     event.periodoId().toString(),
                "calificacion",   event.calificacionFinal(),
                "ocurrido_en",    event.ocurridoEn().toString()
        ));
    }
}
