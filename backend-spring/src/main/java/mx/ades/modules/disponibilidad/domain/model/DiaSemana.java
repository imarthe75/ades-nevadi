package mx.ades.modules.disponibilidad.domain.model;

import java.util.Arrays;

public enum DiaSemana {
    LUNES(0, "Lunes"),
    MARTES(1, "Martes"),
    MIERCOLES(2, "Miércoles"),
    JUEVES(3, "Jueves"),
    VIERNES(4, "Viernes"),
    SABADO(5, "Sábado"),
    DOMINGO(6, "Domingo");

    private final int indice;
    private final String nombre;

    DiaSemana(int indice, String nombre) {
        this.indice = indice;
        this.nombre = nombre;
    }

    public int getIndice() { return indice; }
    public String getNombre() { return nombre; }

    public boolean esLaborable() {
        return this != SABADO && this != DOMINGO;
    }

    public static DiaSemana fromIndice(int indice) {
        return Arrays.stream(values())
                .filter(d -> d.indice == indice)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("dia_semana inválido: " + indice + " (0=Lunes … 6=Domingo)"));
    }

    public static String nombreDeIndice(int indice) {
        return Arrays.stream(values())
                .filter(d -> d.indice == indice)
                .map(DiaSemana::getNombre)
                .findFirst()
                .orElse("?");
    }
}
