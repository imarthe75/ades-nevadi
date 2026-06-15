package mx.ades.modules.medico;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.util.UUID;

@Entity
@Table(name = "ades_expedientes_medicos")
@Getter
@Setter
public class ExpedienteMedico extends AdesBaseEntity {

    @Column(name = "estudiante_id", nullable = false, unique = true)
    private UUID estudianteId;

    @Column(name = "tipo_sangre")
    private String tipoSangre;

    @Column(name = "alergias")
    private String alergias;

    @Column(name = "medicamentos_autorizados")
    private String medicamentosAutorizados;

    @Column(name = "condiciones_cronicas")
    private String condicionesCronicas;

    @Column(name = "observaciones_generales")
    private String observacionesGenerales;

    @Column(name = "nss")
    private String nss;

    @Column(name = "discapacidad")
    private String discapacidad;

    @Column(name = "seguro_medico_tipo")
    private String seguroMedicoTipo;

    @Column(name = "seguro_medico_numero")
    private String seguroMedicoNumero;

    @Column(name = "vacunas_al_dia")
    private Boolean vacunasAlDia = true;

    @Column(name = "padecimiento_cronico")
    private Boolean padecimientoCronico = false;

    @Column(name = "requiere_medicacion")
    private Boolean requiereMedicacion = false;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
