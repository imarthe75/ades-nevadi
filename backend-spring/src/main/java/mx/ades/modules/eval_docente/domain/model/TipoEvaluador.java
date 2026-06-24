package mx.ades.modules.eval_docente.domain.model;

import java.util.Arrays;

/**
 * Rol del evaluador en una evaluación docente 360° del módulo eval_docente.
 * <p>Valores: AUTOEVALUACION (el propio docente), DIRECTIVO (director o coordinador),
 * ALUMNO (grupo evaluador), PARES (colegas docentes).
 * <strong>Nota:</strong> en este módulo el valor canónico de autoevaluación es AUTOEVALUACION
 * (a diferencia de otros módulos donde puede usarse AUTO).</p>
 *
 * @author ADES
 * @since 2026
 */
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
