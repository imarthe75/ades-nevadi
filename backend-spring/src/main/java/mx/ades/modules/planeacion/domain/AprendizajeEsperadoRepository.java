package mx.ades.modules.planeacion.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AprendizajeEsperadoRepository extends JpaRepository<AprendizajeEsperado, UUID> {

    /**
     * Obtener aprendizajes por grado y materia.
     * Usado para llenar selector al crear planeación.
     */
    @Query("""
        SELECT ae FROM AprendizajeEsperado ae
        WHERE ae.gradoId = :gradoId
          AND ae.materiaId = :materiaId
          AND ae.activo = TRUE
        ORDER BY ae.orden ASC, ae.codigo ASC
    """)
    List<AprendizajeEsperado> findByGradoAndMateria(UUID gradoId, UUID materiaId);

    /**
     * Obtener aprendizajes por grado, materia y competencia.
     */
    @Query("""
        SELECT ae FROM AprendizajeEsperado ae
        WHERE ae.gradoId = :gradoId
          AND ae.materiaId = :materiaId
          AND ae.competenciaId = :competenciaId
          AND ae.activo = TRUE
        ORDER BY ae.orden ASC
    """)
    List<AprendizajeEsperado> findByGradoMateriaCompetencia(UUID gradoId, UUID materiaId, UUID competenciaId);

    /**
     * Obtener aprendizajes por competencia.
     */
    @Query("""
        SELECT ae FROM AprendizajeEsperado ae
        WHERE ae.competenciaId = :competenciaId
          AND ae.activo = TRUE
        ORDER BY ae.orden ASC
    """)
    List<AprendizajeEsperado> findByCompetencia(UUID competenciaId);

    /**
     * Obtener aprendizajes activos por grado.
     */
    @Query("""
        SELECT ae FROM AprendizajeEsperado ae
        WHERE ae.gradoId = :gradoId
          AND ae.activo = TRUE
        ORDER BY ae.orden ASC, ae.codigo ASC
    """)
    List<AprendizajeEsperado> findByGrado(UUID gradoId);
}
