package mx.ades.modules.biblioteca;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BibliotecaPrestamoRepository extends JpaRepository<BibliotecaPrestamo, UUID> {
}
