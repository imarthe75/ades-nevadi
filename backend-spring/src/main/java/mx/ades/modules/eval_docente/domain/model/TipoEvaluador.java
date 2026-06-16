package mx.ades.modules.eval_docente.domain.model;

import java.util.Arrays;

public enum TipoEvaluador {
    AUTOEVALUACION, DIRECTIVO, ALUMNO, PARES;

    public static TipoEvaluador of(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("tipo_evaluador es requerido");
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("tipo_evaluador inválido: " + value));
    }
}
