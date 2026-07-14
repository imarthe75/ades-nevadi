package mx.ades.modules.horarios;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.util.UUID;

/**
 * Asignación docente: quién imparte qué materia a qué grupo en un ciclo escolar.
 * Es el dato de origen que el generador de horarios (Timefold) usa para saber
 * cuántas lecciones sin horario debe programar por grupo/materia/profesor.
 */
@Entity
@Table(name = "ades_asignaciones_docentes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"grupo_id", "materia_id", "ciclo_escolar_id"}))
@Getter
@Setter
public class AsignacionDocente extends AdesBaseEntity {

    @Column(name = "grupo_id", nullable = false)
    private UUID grupoId;

    @Column(name = "materia_id", nullable = false)
    private UUID materiaId;

    @Column(name = "profesor_id", nullable = false)
    private UUID profesorId;

    @Column(name = "ciclo_escolar_id", nullable = false)
    private UUID cicloEscolarId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
