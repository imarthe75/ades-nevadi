package mx.ades.modules.horarios.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

/**
 * Catálogo (tabla {@code ades_horario_turno}) de los turnos escolares posibles
 * (Matutino, Vespertino, Nocturno, …). Fuente de verdad —en BD, no en un enum de
 * código— para poblar el select de turno del diálogo de Franjas Horarias y demás
 * campos {@code turno} del sistema. Solo {@code MATUTINO} está en uso hoy, pero el
 * catálogo lista todos los que pudieran existir.
 */
@Entity
@Table(name = "ades_horario_turno")
@Getter
@Setter
public class HorarioTurno extends AdesBaseEntity {

    @Column(name = "codigo", nullable = false, unique = true)
    private String codigo;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "orden", nullable = false)
    private Integer orden = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
