package mx.ades.shared.domain;

/**
 * Enum de dominio — sin dependencias JPA.
 * La entidad JPA del catálogo vive en mx.ades.modules.catalogos.NivelEducativo.
 * Conversión: NivelTipo.fromNombre(nivel.getNombreNivel())
 */
public enum NivelTipo {
    PRIMARIA,
    SECUNDARIA,
    PREPARATORIA;

    public boolean esSEP() {
        return this == PRIMARIA || this == SECUNDARIA;
    }

    public boolean esUAEMEX() {
        return this == PREPARATORIA;
    }

    public static NivelTipo fromNombre(String nombre) {
        return switch (nombre.toUpperCase()) {
            case "PRIMARIA"     -> PRIMARIA;
            case "SECUNDARIA"   -> SECUNDARIA;
            case "PREPARATORIA" -> PREPARATORIA;
            default -> throw new IllegalArgumentException("Nivel educativo desconocido: " + nombre);
        };
    }
}
