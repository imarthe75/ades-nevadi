package mx.ades.modules.expediente_laboral.domain.model;

import java.util.Arrays;

/**
 * Clasificación del nivel de estudios de un empleado: LICENCIATURA, MAESTRIA, DOCTORADO,
 * NORMAL_BASICA, BACHILLERATO u OTRO.
 *
 * @author ADES
 * @since 2026
 */
public enum NivelEstudios {
    LICENCIATURA, MAESTRIA, DOCTORADO, NORMAL_BASICA, BACHILLERATO, OTRO;

    public boolean esPosgrado() {
        return this == MAESTRIA || this == DOCTORADO;
    }

    public static NivelEstudios of(String value) {
        if (value == null || value.isBlank()) return null;
        return Arrays.stream(values())
                .filter(n -> n.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("nivel_estudios inválido: " + value));
    }
}
