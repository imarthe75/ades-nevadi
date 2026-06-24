package mx.ades.modules.esquemas_ponderacion.domain.port.in;

import mx.ades.modules.esquemas_ponderacion.domain.model.ItemPonderacion;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para crear un nuevo esquema de ponderación de calificaciones
 * en el módulo esquemas_ponderacion.
 * <p>Los pesos de los ítems deben sumar exactamente 100%. El flag {@code esNee} indica
 * si el esquema aplica a alumnos con Necesidades Educativas Especiales.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface CrearEsquemaUseCase {

    record Command(String nombre, UUID nivelEducativoId, UUID materiaId,
                   LocalDate vigenteDesde, LocalDate vigenteHasta,
                   List<ItemPonderacion> items, UUID creadoPorId, String usuario, Boolean esNee) {
        public Command {
            if (nombre == null || nombre.isBlank()) throw new IllegalArgumentException("nombre es requerido");
            if (items == null || items.isEmpty()) throw new IllegalArgumentException("items son requeridos");
            double suma = items.stream().mapToDouble(ItemPonderacion::pesoPorcentaje).sum();
            if (Math.abs(suma - 100.0) > 0.01)
                throw new IllegalArgumentException("Los pesos deben sumar 100% (suma actual: " + suma + "%)");
            if (esNee == null) esNee = false;
        }

        public Command(String nombre, UUID nivelEducativoId, UUID materiaId,
                       LocalDate vigenteDesde, LocalDate vigenteHasta,
                       List<ItemPonderacion> items, UUID creadoPorId, String usuario) {
            this(nombre, nivelEducativoId, materiaId, vigenteDesde, vigenteHasta, items, creadoPorId, usuario, false);
        }
    }

    Map<String, Object> crear(Command cmd);
}
