package mx.ades.modules.biblioteca.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.biblioteca.BibliotecaLibro;
import mx.ades.modules.biblioteca.BibliotecaLibroRepository;
import mx.ades.modules.biblioteca.BibliotecaPrestamo;
import mx.ades.modules.biblioteca.BibliotecaPrestamoRepository;
import mx.ades.modules.biblioteca.domain.port.out.BibliotecaRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de persistencia que implementa {@link BibliotecaRepositoryPort} accediendo
 * a las tablas {@code ades_biblioteca_libros} y {@code ades_biblioteca_prestamos}
 * vía JPA y JDBC.
 *
 * <p>El método {@code tomarEjemplar} realiza el decremento atómico del stock con
 * guardián de concurrencia ({@code ejemplares_disponibles > 0}) para prevenir préstamos
 * cuando no hay ejemplares.</p>
 *
 * @author ADES
 * @since 2026
 */
@Component
@RequiredArgsConstructor
public class BibliotecaPersistenceAdapter implements BibliotecaRepositoryPort {

    private final BibliotecaLibroRepository    libros;
    private final BibliotecaPrestamoRepository prestamos;
    private final JdbcTemplate                 jdbc;

    // ── Acervo ───────────────────────────────────────────────────────────────

    @Override
    public BibliotecaLibro saveLibro(BibliotecaLibro libro) {
        return libros.save(libro);
    }

    @Override
    public Optional<BibliotecaLibro> findLibroActivo(UUID id) {
        return libros.findById(id).filter(BibliotecaLibro::getIsActive);
    }

    @Override
    public List<Map<String, Object>> listLibros(String texto, String categoria, UUID plantelId, boolean soloDisponibles) {
        StringBuilder q = new StringBuilder(
                "SELECT l.id, l.titulo, l.autor, l.isbn, l.editorial, l.anio_publicacion, " +
                "l.categoria, l.ubicacion, l.plantel_id, " +
                "pl.nombre_plantel AS plantel_nombre, " +
                "l.ejemplares_total, l.ejemplares_disponibles, l.fecha_creacion " +
                "FROM ades_biblioteca_libros l " +
                "LEFT JOIN ades_planteles pl ON pl.id = l.plantel_id " +
                "WHERE l.is_active = TRUE ");

        List<Object> params = new ArrayList<>();
        if (texto != null && !texto.isBlank()) {
            q.append("AND (l.titulo ILIKE ? OR l.autor ILIKE ? OR l.isbn ILIKE ?) ");
            String like = "%" + texto.trim() + "%";
            params.add(like); params.add(like); params.add(like);
        }
        if (categoria != null && !categoria.isBlank()) {
            q.append("AND l.categoria = ? "); params.add(categoria.toUpperCase());
        }
        if (plantelId != null) {
            q.append("AND l.plantel_id = ? "); params.add(plantelId);
        }
        if (soloDisponibles) {
            q.append("AND l.ejemplares_disponibles > 0 ");
        }
        q.append("ORDER BY l.titulo");
        return jdbc.queryForList(q.toString(), params.toArray());
    }

    // ── Circulación ──────────────────────────────────────────────────────────

    @Override
    public BibliotecaPrestamo savePrestamo(BibliotecaPrestamo prestamo) {
        return prestamos.save(prestamo);
    }

    @Override
    public Optional<BibliotecaPrestamo> findPrestamoAbierto(UUID id) {
        return prestamos.findById(id)
                .filter(BibliotecaPrestamo::getIsActive)
                .filter(p -> "PRESTADO".equals(p.getEstatus()) || "VENCIDO".equals(p.getEstatus()));
    }

    @Override
    public List<Map<String, Object>> listPrestamos(UUID personaId, UUID libroId, String estatus, UUID plantelId,
                                                     int pagina, int porPagina) {
        StringBuilder q = new StringBuilder(
                "SELECT pr.id, pr.libro_id, l.titulo AS libro_titulo, " +
                "pr.persona_id, p.nombre || ' ' || p.apellido_paterno AS persona_nombre, " +
                "e.matricula AS numero_control, pr.plantel_id, " +
                "pr.fecha_prestamo, pr.fecha_devolucion_esperada, pr.fecha_devolucion_real, " +
                "pr.estatus, pr.observaciones, " +
                "(pr.estatus = 'PRESTADO' AND pr.fecha_devolucion_esperada < CURRENT_DATE) AS vencido " +
                "FROM ades_biblioteca_prestamos pr " +
                "JOIN ades_biblioteca_libros l ON l.id = pr.libro_id " +
                "JOIN ades_personas p ON p.id = pr.persona_id " +
                "LEFT JOIN ades_estudiantes e ON e.persona_id = pr.persona_id " +
                "WHERE pr.is_active = TRUE ");

        List<Object> params = new ArrayList<>();
        if (personaId != null) { q.append("AND pr.persona_id = ? "); params.add(personaId); }
        if (libroId != null)   { q.append("AND pr.libro_id = ? ");   params.add(libroId); }
        if (estatus != null && !estatus.isBlank()) {
            q.append("AND pr.estatus = ? "); params.add(estatus.toUpperCase());
        }
        if (plantelId != null) { q.append("AND pr.plantel_id = ? "); params.add(plantelId); }

        q.append("ORDER BY pr.fecha_prestamo DESC, pr.estatus LIMIT ? OFFSET ?");
        params.add(porPagina);
        params.add((pagina - 1) * porPagina);
        return jdbc.queryForList(q.toString(), params.toArray());
    }

    @Override
    public boolean tomarEjemplar(UUID libroId) {
        int rows = jdbc.update(
                "UPDATE ades_biblioteca_libros " +
                "SET ejemplares_disponibles = ejemplares_disponibles - 1 " +
                "WHERE id = ? AND is_active = TRUE AND ejemplares_disponibles > 0",
                libroId);
        return rows > 0;
    }

    @Override
    public void devolverEjemplar(UUID libroId) {
        jdbc.update(
                "UPDATE ades_biblioteca_libros " +
                "SET ejemplares_disponibles = LEAST(ejemplares_disponibles + 1, ejemplares_total) " +
                "WHERE id = ?",
                libroId);
    }
}
