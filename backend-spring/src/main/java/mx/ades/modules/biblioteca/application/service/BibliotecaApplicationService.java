package mx.ades.modules.biblioteca.application.service;

import mx.ades.modules.biblioteca.BibliotecaLibro;
import mx.ades.modules.biblioteca.BibliotecaPrestamo;
import mx.ades.modules.biblioteca.domain.model.CategoriaLibro;
import mx.ades.modules.biblioteca.domain.model.EstatusPrestamo;
import mx.ades.modules.biblioteca.domain.port.in.ActualizarLibroUseCase;
import mx.ades.modules.biblioteca.domain.port.in.DevolverPrestamoUseCase;
import mx.ades.modules.biblioteca.domain.port.in.EliminarLibroUseCase;
import mx.ades.modules.biblioteca.domain.port.in.RegistrarLibroUseCase;
import mx.ades.modules.biblioteca.domain.port.in.RegistrarPrestamoUseCase;
import mx.ades.modules.biblioteca.domain.port.out.BibliotecaRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.UUID;

public class BibliotecaApplicationService implements
        RegistrarLibroUseCase, ActualizarLibroUseCase, EliminarLibroUseCase,
        RegistrarPrestamoUseCase, DevolverPrestamoUseCase {

    private final BibliotecaRepositoryPort repo;

    public BibliotecaApplicationService(BibliotecaRepositoryPort repo) {
        this.repo = repo;
    }

    // ── Acervo ───────────────────────────────────────────────────────────────

    @Override
    public UUID registrar(RegistrarLibroUseCase.Command cmd) {
        if (cmd.nivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permisos (requiere COORDINADOR+)");
        }
        BibliotecaLibro libro = new BibliotecaLibro();
        libro.setTitulo(cmd.titulo().trim());
        libro.setAutor(cmd.autor());
        libro.setIsbn(cmd.isbn());
        libro.setEditorial(cmd.editorial());
        libro.setAnioPublicacion(cmd.anioPublicacion());
        libro.setCategoria(cmd.categoria().name());
        libro.setUbicacion(cmd.ubicacion());
        libro.setPlantelId(cmd.plantelId());
        libro.setEjemplaresTotal(cmd.ejemplaresTotal());
        libro.setEjemplaresDisponibles(cmd.ejemplaresTotal());
        libro.setIsActive(true);
        return repo.saveLibro(libro).getId();
    }

    @Override
    public void actualizar(ActualizarLibroUseCase.Command cmd) {
        if (cmd.nivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permisos (requiere COORDINADOR+)");
        }
        BibliotecaLibro libro = repo.findLibroActivo(cmd.libroId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Libro no encontrado"));

        if (cmd.titulo() != null && !cmd.titulo().isBlank()) libro.setTitulo(cmd.titulo().trim());
        if (cmd.autor() != null)            libro.setAutor(cmd.autor());
        if (cmd.isbn() != null)             libro.setIsbn(cmd.isbn());
        if (cmd.editorial() != null)        libro.setEditorial(cmd.editorial());
        if (cmd.anioPublicacion() != null)  libro.setAnioPublicacion(cmd.anioPublicacion());
        if (cmd.categoria() != null)        libro.setCategoria(CategoriaLibro.of(cmd.categoria()).name());
        if (cmd.ubicacion() != null)        libro.setUbicacion(cmd.ubicacion());

        if (cmd.ejemplaresTotal() != null) {
            int prestados = libro.getEjemplaresTotal() - libro.getEjemplaresDisponibles();
            if (cmd.ejemplaresTotal() < prestados) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "ejemplares_total (" + cmd.ejemplaresTotal() + ") no puede ser menor que los " +
                        prestados + " ejemplares actualmente prestados");
            }
            libro.setEjemplaresTotal(cmd.ejemplaresTotal());
            libro.setEjemplaresDisponibles(cmd.ejemplaresTotal() - prestados);
        }
        repo.saveLibro(libro);
    }

    @Override
    public void eliminar(UUID libroId, int nivelAcceso) {
        if (nivelAcceso > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permisos (requiere DIRECTOR+)");
        }
        BibliotecaLibro libro = repo.findLibroActivo(libroId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Libro no encontrado"));
        if (libro.getEjemplaresDisponibles() < libro.getEjemplaresTotal()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar: hay ejemplares prestados pendientes de devolución");
        }
        libro.setIsActive(false);
        repo.saveLibro(libro);
    }

    // ── Circulación ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public UUID prestar(RegistrarPrestamoUseCase.Command cmd) {
        if (cmd.nivelAcceso() > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permisos para registrar préstamos");
        }
        BibliotecaLibro libro = repo.findLibroActivo(cmd.libroId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Libro no encontrado"));

        if (cmd.fechaDevolucionEsperada().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "fecha_devolucion_esperada no puede ser anterior a hoy");
        }
        // Decremento atómico: evita sobre-préstamo bajo concurrencia.
        if (!repo.tomarEjemplar(cmd.libroId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No hay ejemplares disponibles de \"" + libro.getTitulo() + "\"");
        }

        BibliotecaPrestamo p = new BibliotecaPrestamo();
        p.setLibroId(cmd.libroId());
        p.setPersonaId(cmd.personaId());
        p.setPlantelId(cmd.plantelId() != null ? cmd.plantelId() : libro.getPlantelId());
        p.setFechaPrestamo(LocalDate.now());
        p.setFechaDevolucionEsperada(cmd.fechaDevolucionEsperada());
        p.setEstatus(EstatusPrestamo.PRESTADO.name());
        p.setObservaciones(cmd.observaciones());
        p.setIsActive(true);
        return repo.savePrestamo(p).getId();
    }

    @Override
    @Transactional
    public void devolver(DevolverPrestamoUseCase.Command cmd) {
        if (cmd.nivelAcceso() > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permisos para registrar devoluciones");
        }
        BibliotecaPrestamo p = repo.findPrestamoAbierto(cmd.prestamoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Préstamo no encontrado o ya cerrado"));

        p.setEstatus(cmd.estatusFinal().name());
        p.setFechaDevolucionReal(LocalDate.now());
        if (cmd.observaciones() != null) p.setObservaciones(cmd.observaciones());
        repo.savePrestamo(p);

        // El ejemplar regresa al acervo solo si fue DEVUELTO (EXTRAVIADO se pierde).
        if (cmd.estatusFinal() == EstatusPrestamo.DEVUELTO) {
            repo.devolverEjemplar(p.getLibroId());
        }
    }
}
