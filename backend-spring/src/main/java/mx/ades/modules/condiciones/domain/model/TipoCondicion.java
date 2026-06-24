package mx.ades.modules.condiciones.domain.model;

/**
 * Tipo de condición crónica de salud registrada para un alumno.
 * <p>Valores: EPILEPSIA, DIABETES, ASMA, ALERGIA, CARDIACA, HIPERTENSION,
 * DISCAPACIDAD_VISUAL, DISCAPACIDAD_AUDITIVA, OTRA.
 * Las condiciones EPILEPSIA, DIABETES, CARDIACA e HIPERTENSION requieren medicación.
 * Las condiciones DISCAPACIDAD_VISUAL y DISCAPACIDAD_AUDITIVA se clasifican como discapacidad
 * para el reporte 911-SEP Sección IX.</p>
 *
 * @author ADES
 * @since 2026
 */
public enum TipoCondicion {
    EPILEPSIA, DIABETES, ASMA, ALERGIA, CARDIACA,
    HIPERTENSION, DISCAPACIDAD_VISUAL, DISCAPACIDAD_AUDITIVA, OTRA;

    public boolean requiereMedicacion() {
        return this == EPILEPSIA || this == DIABETES || this == CARDIACA || this == HIPERTENSION;
    }

    public boolean esDiscapacidad() {
        return this == DISCAPACIDAD_VISUAL || this == DISCAPACIDAD_AUDITIVA;
    }

    public static TipoCondicion of(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("tipo_condicion es requerido");
        try {
            return TipoCondicion.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "tipo_condicion inválido: " + value +
                    ". Válidos: EPILEPSIA, DIABETES, ASMA, ALERGIA, CARDIACA, " +
                    "HIPERTENSION, DISCAPACIDAD_VISUAL, DISCAPACIDAD_AUDITIVA, OTRA");
        }
    }
}
