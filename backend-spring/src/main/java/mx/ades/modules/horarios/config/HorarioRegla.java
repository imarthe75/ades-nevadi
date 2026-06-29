package mx.ades.modules.horarios.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;
import java.util.Map;

@Entity
@Table(name = "ades_horario_regla")
@Getter
@Setter
public class HorarioRegla extends AdesBaseEntity {

    @Column(name = "plantel_id", nullable = false)
    private UUID plantelId;

    @Column(name = "ciclo_escolar_id", nullable = false)
    private UUID cicloEscolarId;

    @Column(name = "nivel_educativo_id")
    private UUID nivelEducativoId;

    @Column(name = "tipo", nullable = false)
    private String tipo;

    @Column(name = "dura", nullable = false)
    private Boolean dura = true;

    @Column(name = "peso", nullable = false)
    private Integer peso = 1;

    @Column(name = "activa", nullable = false)
    private Boolean activa = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> params;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
