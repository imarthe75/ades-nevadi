package mx.ades.modules.esquemas_ponderacion.domain.port.in;

import mx.ades.modules.esquemas_ponderacion.domain.model.ItemPonderacion;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ActualizarEsquemaUseCase {

    record Command(UUID esquemaId, String nombre, UUID nivelEducativoId, UUID materiaId,
                   LocalDate vigenteDesde, LocalDate vigenteHasta,
                   List<ItemPonderacion> items, String usuario) {
        public Command {
            if (esquemaId == null) throw new IllegalArgumentException("esquema_id es requerido");
            if (items == null || items.isEmpty()) throw new IllegalArgumentException("items son requeridos");
            double suma = items.stream().mapToDouble(ItemPonderacion::pesoPorcentaje).sum();
            if (Math.abs(suma - 100.0) > 0.01)
                throw new IllegalArgumentException("Los pesos deben sumar 100% (suma actual: " + suma + "%)");
        }
    }

    Map<String, Object> actualizar(Command cmd);
}
