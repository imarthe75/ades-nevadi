package mx.ades.modules.evaluaciones.domain.model;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;

public record SlotHorario(LocalTime inicio, LocalTime fin) {

    public SlotHorario {
        if (!fin.isAfter(inicio)) {
            throw new IllegalArgumentException(
                    "hora_fin (" + fin + ") debe ser posterior a hora_inicio (" + inicio + ")");
        }
    }

    public static SlotHorario of(String inicio, String fin) {
        try {
            return new SlotHorario(LocalTime.parse(inicio), LocalTime.parse(fin));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Formato de hora inválido (esperado HH:mm): " + e.getMessage());
        }
    }

    /** True cuando este slot se superpone con otro (excluye el caso borde de adyacentes). */
    public boolean conflictaCon(SlotHorario otro) {
        return inicio.isBefore(otro.fin) && fin.isAfter(otro.inicio);
    }
}
