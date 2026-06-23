package mx.ades.modules.catalogos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CicloEscolarRepository extends JpaRepository<CicloEscolar, UUID> {
    List<CicloEscolar> findByEsVigenteTrue();
    List<CicloEscolar> findByNivelEducativoIdAndEsVigenteTrue(UUID nivelId);
}
