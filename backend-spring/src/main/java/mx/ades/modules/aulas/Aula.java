package mx.ades.modules.aulas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.util.UUID;

@Entity
@Table(name = "ades_aulas")
@Getter
@Setter
public class Aula extends AdesBaseEntity {

    @Column(name = "nombre_aula", nullable = false)
    private String nombreAula;

    @Column(name = "plantel_id", nullable = false)
    private UUID plantelId;

    @Column(name = "tipo_aula", nullable = false)
    private String tipoAula = "AULA";

    @Column(name = "capacidad_alumnos")
    private Integer capacidadAlumnos;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "clave_aula", length = 20)
    private String claveAula;

    @Column(name = "piso", nullable = false)
    private Short piso = 1;

    @Column(name = "edificio", length = 40)
    private String edificio;

    @Column(name = "capacidad_maxima")
    private Short capacidadMaxima;

    @Column(name = "tiene_proyector", nullable = false)
    private boolean tieneProyector = false;

    @Column(name = "tiene_pizarra_digital", nullable = false)
    private boolean tienePizarraDigital = false;

    @Column(name = "tiene_pizarron", nullable = false)
    private boolean tienePizarron = true;

    @Column(name = "tiene_aire_acondicionado", nullable = false)
    private boolean tieneAireAcondicionado = false;

    @Column(name = "tiene_ventiladores", nullable = false)
    private boolean tieneVentiladores = false;

    @Column(name = "tiene_internet", nullable = false)
    private boolean tieneInternet = false;

    @Column(name = "num_computadoras", nullable = false)
    private short numComputadoras = 0;

    @Column(name = "estado_aula", nullable = false, length = 20)
    private String estadoAula = "ACTIVA";

    @Column(name = "observaciones", columnDefinition = "text")
    private String observaciones;
}
