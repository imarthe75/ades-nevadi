package mx.ades.modules.planeacion.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompetenciaRepository extends JpaRepository<Competencia, UUID> {

    /**
     * Obtener competencia por código.
     */
    Optional<Competencia> findByCodigo(String codigo);

    /**
     * Obtener todas las competencias activas por nivel educativo.
     */
    @Query("""
        SELECT c FROM Competencia c
        WHERE c.nivelEducativoId = :nivelEducativoId
          AND c.activo = TRUE
        ORDER BY c.orden ASC, c.codigo ASC
    """)
    List<Competencia> findByNivelEducativo(UUID nivelEducativoId);

    /**
     * Obtener competencias por campo formativo (NEM).
     */
    @Query("""
        SELECT c FROM Competencia c
        WHERE c.campoFormativo = :campoFormativo
          AND c.activo = TRUE
        ORDER BY c.orden ASC
    """)
    List<Competencia> findByCampoFormativo(String campoFormativo);

    /**
     * Obtener competencias por área de conocimiento (CBU).
     */
    @Query("""
        SELECT c FROM Competencia c
        WHERE c.areaConocimiento = :areaConocimiento
          AND c.activo = TRUE
        ORDER BY c.orden ASC
    """)
    List<Competencia> findByAreaConocimiento(String areaConocimiento);
}
