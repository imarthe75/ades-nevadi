package mx.ades.modules.horarios.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HorarioTurnoRepository extends JpaRepository<HorarioTurno, UUID> {

    /** Catálogo de turnos activos ordenado, para servir al select del frontend. */
    List<HorarioTurno> findByIsActiveTrueOrderByOrdenAsc();
}
