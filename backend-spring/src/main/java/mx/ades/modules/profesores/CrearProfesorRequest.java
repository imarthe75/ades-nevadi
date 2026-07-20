package mx.ades.modules.profesores;

import java.util.Map;
import java.util.UUID;

/**
 * DTO para el alta de profesor desde cero (persona nueva, no existente) desde el
 * formulario Angular. El frontend envía:
 * { persona: { nombre, apellido_paterno, apellido_materno, curp }, numero_empleado,
 *   tipo_contrato, plantel_id }
 * Mismo patrón que {@code mx.ades.modules.alumnos.CrearAlumnoRequest}. Construido
 * 2026-07-20 — antes {@code ProfesorController#create} deserializaba el body directo
 * como la entidad JPA {@link Profesor}, que espera un {@code persona_id} de una
 * persona YA EXISTENTE; el formulario "Nuevo profesor" nunca lo envía (no ofrece
 * selección de persona), así que el alta fallaba el 100% de las veces con
 * "persona_id es requerido".
 */
public record CrearProfesorRequest(
        Map<String, Object> persona,
        UUID plantel_id,
        String numero_empleado,
        String tipo_contrato
) {
    public String nombre()           { return str("nombre"); }
    public String apellidoPaterno()  { return str("apellido_paterno"); }
    public String apellidoMaterno()  { return str("apellido_materno"); }
    public String curp()             { return str("curp"); }

    private String str(String key) {
        return persona != null && persona.get(key) != null ? persona.get(key).toString() : null;
    }
}
