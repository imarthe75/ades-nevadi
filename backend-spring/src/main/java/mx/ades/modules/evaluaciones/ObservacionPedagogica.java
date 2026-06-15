package mx.ades.modules.evaluaciones;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesAuditEntity;

import java.util.UUID;

@Entity
@Table(name = "ades_observaciones_pedagogicas")
@Getter
@Setter
public class ObservacionPedagogica extends AdesAuditEntity {

    @Column(name = "alumno_id", nullable = false)
    private UUID alumnoId;

    @Column(name = "observacion", nullable = false)
    private String observacion;

    @Column(name = "periodo")
    private String periodo;

    @Column(name = "tipo")
    private String tipo = "GENERAL";

    @Column(name = "autor_id")
    private UUID autorId;
}
