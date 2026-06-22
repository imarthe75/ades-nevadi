package mx.ades.modules.biblioteca.query;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.biblioteca.BibliotecaLibro;
import mx.ades.modules.biblioteca.domain.port.out.BibliotecaRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BibliotecaQueryService {

    private final BibliotecaRepositoryPort repo;

    public List<Map<String, Object>> listLibros(String texto, String categoria, UUID plantelId, boolean soloDisponibles) {
        return repo.listLibros(texto, categoria, plantelId, soloDisponibles);
    }

    public BibliotecaLibro findLibro(UUID id) {
        return repo.findLibroActivo(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Libro no encontrado"));
    }

    public List<Map<String, Object>> listPrestamos(UUID personaId, UUID libroId, String estatus, UUID plantelId) {
        return repo.listPrestamos(personaId, libroId, estatus, plantelId);
    }
}
