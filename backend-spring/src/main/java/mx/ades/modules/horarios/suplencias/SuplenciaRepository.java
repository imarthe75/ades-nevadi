package mx.ades.modules.horarios.suplencias;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface SuplenciaRepository extends JpaRepository<Suplencia, UUID> {
    List<Suplencia> findByProfesorAusenteIdAndFechaAndIsActiveTrue(UUID profesorAusenteId, LocalDate fecha);
    List<Suplencia> findByFechaAndIsActiveTrue(LocalDate fecha);

    /**
     * BOLA fix (2026-07-16, docs/hallazgos/2026-07-16_auditoria_gaps_no_revisados.md
     * #1 — SuplenciaController): {@code findByFechaAndIsActiveTrue} no filtraba por
     * plantel — el calendario institucional completo de ausencias/suplencias quedaba
     * visible a cualquier personal escolar de un solo plantel. {@code ades_suplencias}
     * no tiene columna plantel_id propia (Regla Mandatoria #3, mig. 138); se resuelve
     * vía el profesor ausente.
     */
    @Query(value = "SELECT s.* FROM ades_suplencias s " +
            "JOIN ades_profesores p ON p.id = s.profesor_ausente_id " +
            "WHERE s.fecha = :fecha AND s.is_active = TRUE AND p.plantel_id = :plantelId",
            nativeQuery = true)
    List<Suplencia> findByFechaAndIsActiveTrueAndPlantel(@Param("fecha") LocalDate fecha, @Param("plantelId") UUID plantelId);
}
