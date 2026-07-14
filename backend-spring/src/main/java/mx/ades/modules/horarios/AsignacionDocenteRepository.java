package mx.ades.modules.horarios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AsignacionDocenteRepository extends JpaRepository<AsignacionDocente, UUID> {

    List<AsignacionDocente> findByCicloEscolarIdAndIsActiveTrue(UUID cicloEscolarId);

    List<AsignacionDocente> findByProfesorIdAndCicloEscolarIdAndIsActiveTrue(UUID profesorId, UUID cicloEscolarId);

    Optional<AsignacionDocente> findByGrupoIdAndMateriaIdAndCicloEscolarIdAndIsActiveTrue(
            UUID grupoId, UUID materiaId, UUID cicloEscolarId);

    @Query(value = """
        SELECT a.* FROM ades_asignaciones_docentes a
        JOIN ades_grupos g ON g.id = a.grupo_id
        JOIN ades_grados gr ON gr.id = g.grado_id
        WHERE gr.plantel_id = :plantelId
          AND a.ciclo_escolar_id = :cicloEscolarId
          AND a.is_active = true
        """, nativeQuery = true)
    List<AsignacionDocente> findByPlantelIdAndCicloEscolarId(
            @Param("plantelId") UUID plantelId, @Param("cicloEscolarId") UUID cicloEscolarId);
}
