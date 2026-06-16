package mx.ades.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.*;
import io.micrometer.core.instrument.binder.system.DiskSpaceMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * Registra métricas JVM/sistema directamente vía @PostConstruct.
 * MeterRegistryCustomizer no funciona aquí porque jobRegistryBeanPostProcessor
 * (Spring Batch) crea el PrometheusMeterRegistry de forma eager, antes de que
 * los customizers estén disponibles.
 */
@Configuration
public class MetricsConfig {

    @Autowired
    private MeterRegistry registry;

    @PostConstruct
    public void bindJvmMetrics() {
        new JvmMemoryMetrics().bindTo(registry);
        new JvmGcMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new ClassLoaderMetrics().bindTo(registry);
        new JvmHeapPressureMetrics().bindTo(registry);
        new ProcessorMetrics().bindTo(registry);
        new UptimeMetrics().bindTo(registry);
        new DiskSpaceMetrics(new File("/")).bindTo(registry);
    }
}
