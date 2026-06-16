package mx.ades.modules.licencias.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** Pure domain value object — calculates working days between two dates (Mon-Fri only) */
public record DiasHabiles(int valor) {

    public DiasHabiles {
        if (valor < 1) throw new IllegalArgumentException("dias_habiles debe ser al menos 1");
    }

    public static DiasHabiles calcular(LocalDate inicio, LocalDate fin) {
        if (fin.isBefore(inicio))
            throw new IllegalArgumentException("fecha_fin debe ser >= fecha_inicio");
        int dias = 0;
        LocalDate current = inicio;
        while (!current.isAfter(fin)) {
            DayOfWeek dow = current.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                dias++;
            }
            current = current.plusDays(1);
        }
        return new DiasHabiles(Math.max(dias, 1));
    }
}
