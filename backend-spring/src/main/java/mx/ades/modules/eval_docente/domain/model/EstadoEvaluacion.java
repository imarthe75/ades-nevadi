package mx.ades.modules.eval_docente.domain.model;

import java.util.Arrays;

/**
 * Estado del ciclo de vida de una evaluación docente 360°.
 * <p>Valores: BORRADOR (editable, criterios en captura), ENVIADA (finalizada, en revisión),
 * APROBADA (validada por director). Solo BORRADOR permite editar criterios.</p>
 *
 * @author ADES
 * @since 2026
 */
public enum EstadoEvaluacion {
    BORRADOR, ENVIADA, APROBADA;

    public boolean esEditable() {
        return this == BORRADOR;
    }

    public boolean esAprobada() {
        return this == APROBADA;
    }

    public static EstadoEvaluacion of(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("estatus es requerido");
        return Arrays.stream(values())
                .filter(e -> e.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("estatus inválido: " + value));
    }
}
