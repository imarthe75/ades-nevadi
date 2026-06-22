package mx.ades.modules.biblioteca;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.util.UUID;

@Entity
@Table(name = "ades_biblioteca_libros")
@Getter
@Setter
public class BibliotecaLibro extends AdesBaseEntity {

    @Column(name = "titulo", nullable = false)
    private String titulo;

    @Column(name = "autor")
    private String autor;

    @Column(name = "isbn")
    private String isbn;

    @Column(name = "editorial")
    private String editorial;

    @Column(name = "anio_publicacion")
    private Integer anioPublicacion;

    @Column(name = "categoria", nullable = false)
    private String categoria;

    @Column(name = "ubicacion")
    private String ubicacion;

    @Column(name = "plantel_id")
    private UUID plantelId;

    @Column(name = "ejemplares_total", nullable = false)
    private Integer ejemplaresTotal = 1;

    @Column(name = "ejemplares_disponibles", nullable = false)
    private Integer ejemplaresDisponibles = 1;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
