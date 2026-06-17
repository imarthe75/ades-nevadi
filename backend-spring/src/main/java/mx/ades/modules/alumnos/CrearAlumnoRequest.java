package mx.ades.modules.alumnos;

import java.util.Map;
import java.util.UUID;

/**
 * DTO para el alta rápida de alumno desde el formulario Angular.
 * El frontend envía: { persona: { nombre, apellido_paterno, apellido_materno, curp }, plantel_id }
 */
public record CrearAlumnoRequest(
        Map<String, Object> persona,
        UUID plantel_id
) {
    public String nombre()            { return str("nombre"); }
    public String apellido_paterno()  { return str("apellido_paterno"); }
    public String apellido_materno()  { return str("apellido_materno"); }
    public String curp()              { return str("curp"); }

    private String str(String key) {
        return persona != null && persona.get(key) != null ? persona.get(key).toString() : null;
    }
}
