package mx.ades.modules.horarios.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.util.UUID;

@Entity
@Table(name = "ades_horario_indisponibilidad")
@Getter
@Setter
public class HorarioIndisponibilidad extends AdesBaseEntity {

    @Column(name = "profesor_id", nullable = false)
    private UUID profesorId;

    @Column(name = "ciclo_escolar_id", nullable = false)
    private UUID cicloEscolarId;

    @Column(name = "franja_id", nullable = false)
    private UUID franjaId;

    /**
     * Estados posibles:
     * DISPONIBLE
     * CONDICIONAL
     * NO_DISPONIBLE
     */
    @Column(name = "tipo", nullable = false)
    private String tipo = "NO_DISPONIBLE";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
