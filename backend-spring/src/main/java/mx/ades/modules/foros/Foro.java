package mx.ades.modules.foros;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.util.UUID;

@Entity
@Table(name = "ades_foros")
@Getter
@Setter
public class Foro extends AdesBaseEntity {

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "tipo")
    private String tipo = "GRUPO";

    @Column(name = "grupo_id")
    private UUID grupoId;

    @Column(name = "plantel_id")
    private UUID plantelId;

    @Column(name = "materia_id")
    private UUID materiaId;

    @Column(name = "es_moderado")
    private Boolean esModerado = false;

    @Column(name = "creado_por")
    private UUID creadoPor;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
