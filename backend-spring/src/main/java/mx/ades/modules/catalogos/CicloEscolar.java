package mx.ades.modules.catalogos;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.time.LocalDate;

@Entity
@Table(name = "ades_ciclos_escolares")
@Getter
@Setter
public class CicloEscolar extends AdesBaseEntity {

    @Column(name = "nombre_ciclo", nullable = false)
    private String nombreCiclo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "nivel_educativo_id", nullable = false)
    private NivelEducativo nivelEducativo;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "tipo_ciclo", nullable = false)
    private String tipoCiclo = "ANUAL";

    @Column(name = "es_vigente", nullable = false)
    private Boolean esVigente = true;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
