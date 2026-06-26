package mx.ades.modules.horarios.solver;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.util.UUID;

@Entity
@Table(name = "ades_horario_corrida")
@Getter
@Setter
public class HorarioCorrida extends AdesBaseEntity {

    @Column(name = "plantel_id", nullable = false)
    private UUID plantelId;

    @Column(name = "ciclo_escolar_id", nullable = false)
    private UUID cicloEscolarId;

    @Column(name = "estado", nullable = false)
    private String estado = "PENDIENTE";

    @Column(name = "score_text")
    private String scoreText;

    @Column(name = "score_analysis_json", columnDefinition = "jsonb")
    private String scoreAnalysisJson;

    @Column(name = "tiempo_solving_ms")
    private Long tiempoSolvingMs;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "generado_por", nullable = false)
    private String generadoPor;

    @Column(name = "resultado_excel_url")
    private String resultadoExcelUrl;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}