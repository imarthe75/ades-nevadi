package mx.ades.modules.conducta.infrastructure.outbound.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ades.common.WebhookService;
import mx.ades.modules.conducta.domain.event.SancionConductaEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SancionConductaListener {

    private final WebhookService webhookService;

    @EventListener
    @Async
    public void onSancionAplicada(SancionConductaEvent event) {
        log.info("Sanción {} aplicada al estudiante {} — notificar padres: {}",
                event.tipoSancion(), event.estudianteId(), event.debNotificarPadres());

        webhookService.dispatchWebhook("sancion-conducta", Map.of(
                "sancion_id",         event.sancionId().toString(),
                "estudiante_id",      event.estudianteId().toString(),
                "tipo_sancion",       event.tipoSancion().name(),
                "notificar_padres",   event.debNotificarPadres(),
                "ocurrido_en",        event.ocurridoEn().toString()
        ));
    }
}
