package mx.ades.modules.biblioteca.domain.model;

public enum EstatusPrestamo {
    PRESTADO, DEVUELTO, VENCIDO, EXTRAVIADO;

    public boolean estaAbierto() {
        return this == PRESTADO || this == VENCIDO;
    }

    public static EstatusPrestamo of(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("estatus es requerido");
        try {
            return EstatusPrestamo.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "estatus inválido: " + value +
                    ". Válidos: PRESTADO, DEVUELTO, VENCIDO, EXTRAVIADO");
        }
    }
}
