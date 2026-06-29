package mx.ades.modules.horarios.suplencias;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface SuplenciaRepository extends JpaRepository<Suplencia, UUID> {
    List<Suplencia> findByProfesorAusenteIdAndFechaAndIsActiveTrue(UUID profesorAusenteId, LocalDate fecha);
    List<Suplencia> findByFechaAndIsActiveTrue(LocalDate fecha);
}
