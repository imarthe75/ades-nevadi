package mx.ades.modules.expediente_laboral.domain.model;

import java.util.Arrays;

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
