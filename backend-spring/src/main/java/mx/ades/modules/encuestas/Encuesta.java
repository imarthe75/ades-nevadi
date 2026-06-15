package mx.ades.modules.encuestas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ades_encuestas")
@Getter
@Setter
public class Encuesta extends AdesBaseEntity {

    @Column(name = "titulo", nullable = false)
    private String titulo;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "tipo")
    private String tipo = "SATISFACCION";

    @Column(name = "audiencia")
    private String audiencia = "ALUMNO";

    @Column(name = "plantel_id")
    private UUID plantelId;

    @Column(name = "nivel_educativo_id")
    private UUID nivelEducativoId;

    @Column(name = "grupo_id")
    private UUID grupoId;

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Column(name = "anonima")
    private Boolean anonima = false;

    @Column(name = "activa")
    private Boolean activa = true;

    @Column(name = "creado_por_id")
    private UUID creadoPorId;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
