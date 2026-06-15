package mx.ades.modules.sistema;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

@Entity
@Table(name = "ades_variables_sistema")
@Getter
@Setter
public class VariableSistema extends AdesBaseEntity {

    @Column(name = "nombre", nullable = false, unique = true)
    private String nombre;

    @Column(name = "tipo_valor", nullable = false)
    private String tipoValor;

    @Column(name = "valor")
    private String valor;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "encriptado", nullable = false)
    private Boolean encriptado = false;

    @Column(name = "solo_lectura", nullable = false)
    private Boolean soloLectura = false;

    @Column(name = "grupo")
    private String grupo;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
