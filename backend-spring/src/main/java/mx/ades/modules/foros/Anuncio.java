package mx.ades.modules.foros;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ades_anuncios")
@Getter
@Setter
public class Anuncio extends AdesBaseEntity {

    @Column(name = "titulo", nullable = false)
    private String titulo;

    @Column(name = "contenido", nullable = false)
    private String contenido;

    @Column(name = "plantel_id")
    private UUID plantelId;

    @Column(name = "nivel_educativo")
    private String nivelEducativo;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio = LocalDate.now();

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Column(name = "es_urgente")
    private Boolean esUrgente = false;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
