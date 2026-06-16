package mx.ades.modules.comunicados.domain.model;

import java.time.LocalDateTime;

public enum Periodicidad {
    DIARIA {
        @Override public LocalDateTime calcularSiguiente(LocalDateTime base) { return base.plusDays(1); }
    },
    SEMANAL {
        @Override public LocalDateTime calcularSiguiente(LocalDateTime base) { return base.plusWeeks(1); }
    },
    QUINCENAL {
        @Override public LocalDateTime calcularSiguiente(LocalDateTime base) { return base.plusDays(15); }
    },
    MENSUAL {
        @Override public LocalDateTime calcularSiguiente(LocalDateTime base) { return base.plusMonths(1); }
    },
    TRIMESTRAL {
        @Override public LocalDateTime calcularSiguiente(LocalDateTime base) { return base.plusMonths(3); }
    };

    public abstract LocalDateTime calcularSiguiente(LocalDateTime base);

    public static Periodicidad of(String valor) {
        if (valor == null) return MENSUAL;
        try {
            return Periodicidad.valueOf(valor.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MENSUAL;
        }
    }
}
