package mx.ades.modules.horarios.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * Catálogo (tabla {@code ades_horario_tipo_regla}) de los tipos de regla que el
 * motor de horarios interpreta. Es la fuente de verdad —en BD, no en un enum de
 * código— de qué tipos son válidos. Cada {@code codigo} debe tener su restricción
 * correspondiente en {@code HorarioConstraintProvider}; un tipo que no esté aquí
 * (o esté inactivo) se rechaza al crear una regla y se marca "sin efecto" en la UI.
 */
@Entity
@Table(name = "ades_horario_tipo_regla")
@Getter
@Setter
public class HorarioTipoRegla extends AdesBaseEntity {

    @Column(name = "codigo", nullable = false, unique = true)
    private String codigo;

    @Column(name = "dura_por_defecto", nullable = false)
    private Boolean duraPorDefecto = true;

    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params_requeridos", nullable = false, columnDefinition = "jsonb")
    private List<String> paramsRequeridos;

    @Column(name = "orden", nullable = false)
    private Integer orden = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
