package mx.ades.modules.certificados.domain.model;

/**
 * Estados del ciclo de vida de un certificado digital.
 * La firma Ed25519 ocurre en el microservicio FastAPI (firma_digital.py).
 */
public enum EstadoCertificado {
    PENDIENTE_FIRMA,
    FIRMADO,
    ANCLADO_BLOCKCHAIN,
    REVOCADO;

    public boolean esFirmado() {
        return this == FIRMADO || this == ANCLADO_BLOCKCHAIN;
    }

    public boolean esVerificable() {
        return esFirmado();
    }

    public static EstadoCertificado of(String valor) {
        try {
            return valueOf(valor.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("estado_certificado inválido: " + valor);
        }
    }
}
