package mx.ades.modules.licencias.domain.model;

/**
 * Tipo de licencia del personal: MEDICA, MATERNIDAD, PATERNIDAD, DUELO, PERSONAL,
 * COMISION, CAPACITACION u OTRO.
 *
 * @author ADES
 * @since 2026
 */
public enum TipoLicencia {
    MEDICA, MATERNIDAD, PATERNIDAD, DUELO, PERSONAL, COMISION, CAPACITACION, OTRO;

    public boolean esMaternoPaternal() {
        return this == MATERNIDAD || this == PATERNIDAD;
    }

    public boolean esLaboralInstitucional() {
        return this == COMISION || this == CAPACITACION;
    }

    public static TipoLicencia of(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("tipo_licencia es requerido");
        try {
            return TipoLicencia.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "tipo_licencia inválido: " + value +
                    ". Válidos: MEDICA, MATERNIDAD, PATERNIDAD, DUELO, PERSONAL, COMISION, CAPACITACION, OTRO");
        }
    }
}
