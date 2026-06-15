package mx.ades.modules.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.util.UUID;

@Entity
@Table(name = "ades_identidad_institucional")
@Getter
@Setter
public class IdentidadInstitucional extends AdesBaseEntity {

    @Column(name = "tipo_elemento", nullable = false)
    private String tipoElemento;

    @Column(name = "nombre_clave")
    private String nombreClave;

    @Column(name = "texto_elemento")
    private String textoElemento;

    @Column(name = "url_archivo")
    private String urlArchivo;

    @Column(name = "color_hex")
    private String colorHex;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "plantel_id")
    private UUID plantelId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
