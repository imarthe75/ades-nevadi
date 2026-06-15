package mx.ades.modules.foros;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AnuncioRepository extends JpaRepository<Anuncio, UUID> {
}
