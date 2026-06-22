package mx.ades.modules.biblioteca;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ades_biblioteca_prestamos")
@Getter
@Setter
public class BibliotecaPrestamo extends AdesBaseEntity {

    @Column(name = "libro_id", nullable = false)
    private UUID libroId;

    @Column(name = "persona_id", nullable = false)
    private UUID personaId;

    @Column(name = "plantel_id")
    private UUID plantelId;

    @Column(name = "fecha_prestamo", nullable = false)
    private LocalDate fechaPrestamo;

    @Column(name = "fecha_devolucion_esperada", nullable = false)
    private LocalDate fechaDevolucionEsperada;

    @Column(name = "fecha_devolucion_real")
    private LocalDate fechaDevolucionReal;

    @Column(name = "estatus", nullable = false)
    private String estatus = "PRESTADO";

    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
