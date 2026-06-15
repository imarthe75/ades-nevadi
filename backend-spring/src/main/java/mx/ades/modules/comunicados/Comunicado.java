package mx.ades.modules.comunicados;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ades_comunicados")
@Getter
@Setter
public class Comunicado extends AdesBaseEntity {

    @Column(name = "titulo", nullable = false)
    private String titulo;

    @Column(name = "contenido", nullable = false)
    private String contenido;

    @Column(name = "tipo_comunicado")
    private String tipoComunicado = "GENERAL";

    @Column(name = "plantel_id")
    private UUID plantelId;

    @Column(name = "nivel_educativo_id")
    private UUID nivelEducativoId;

    @Column(name = "grupo_id")
    private UUID grupoId;

    @Column(name = "requiere_acuse")
    private Boolean requiereAcuse = false;

    @Column(name = "fecha_publicacion")
    private LocalDateTime fechaPublicacion = LocalDateTime.now();

    @Column(name = "fecha_vencimiento")
    private LocalDateTime fechaVencimiento;

    @Column(name = "es_recurrente")
    private Boolean esRecurrente = false;

    @Column(name = "periodicidad")
    private String periodicidad;

    @Column(name = "proximo_envio")
    private LocalDateTime proximoEnvio;

    @Column(name = "total_destinatarios")
    private Integer totalDestinatarios = 0;

    @Column(name = "creado_por_id")
    private UUID creadoPorId;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
