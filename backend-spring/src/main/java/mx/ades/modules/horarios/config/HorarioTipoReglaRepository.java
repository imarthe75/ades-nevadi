package mx.ades.modules.horarios.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HorarioTipoReglaRepository extends JpaRepository<HorarioTipoRegla, UUID> {

    /** Catálogo activo ordenado, para servir a la UI y al asistente IA. */
    List<HorarioTipoRegla> findByIsActiveTrueOrderByOrdenAsc();

    /** ¿El motor soporta este tipo de regla? (validación al crear). */
    boolean existsByCodigoAndIsActiveTrue(String codigo);
}
