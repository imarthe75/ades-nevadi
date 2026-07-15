package mx.ades.modules.biblioteca;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.biblioteca.domain.model.CategoriaLibro;
import mx.ades.modules.biblioteca.domain.model.EstatusPrestamo;
import mx.ades.modules.biblioteca.domain.port.in.ActualizarLibroUseCase;
import mx.ades.modules.biblioteca.domain.port.in.DevolverPrestamoUseCase;
import mx.ades.modules.biblioteca.domain.port.in.EliminarLibroUseCase;
import mx.ades.modules.biblioteca.domain.port.in.RegistrarLibroUseCase;
import mx.ades.modules.biblioteca.domain.port.in.RegistrarPrestamoUseCase;
import mx.ades.modules.biblioteca.query.BibliotecaQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controlador REST para la administración de la biblioteca en el Instituto Nevadi.
 * Permite registrar y actualizar libros en el catálogo del acervo, consultar préstamos,
 * realizar préstamos de ejemplares a alumnos o personal y gestionar devoluciones.
 *
 * @author ADES
 * @version 2.0
 */
@RestController
@RequestMapping("/api/v1/biblioteca")
@RequiredArgsConstructor
public class BibliotecaController {

    private final AdesUserService          userService;
    private final RegistrarLibroUseCase    registrarLibro;
    private final ActualizarLibroUseCase   actualizarLibro;
    private final EliminarLibroUseCase     eliminarLibro;
    private final RegistrarPrestamoUseCase registrarPrestamo;
    private final DevolverPrestamoUseCase  devolverPrestamo;
    private final BibliotecaQueryService   query;

    private int nivel(AdesUser u) {
        return u.getNivelAcceso() != null ? u.getNivelAcceso() : 5;
    }

    /** No-admins (nivel > 2) quedan acotados a su propio plantel. */
    private UUID scopePlantel(AdesUser u, UUID solicitado) {
        if (nivel(u) > 2 && u.getPlantelId() != null) {
            return u.getPlantelId();
        }
        return solicitado;
    }

    /**
     * Los movimientos del acervo (alta/baja/edición de libros, préstamos y devoluciones)
     * son operación de personal escolar (nivelAcceso &le;4). Antes de este fix, crearLibro/
     * actualizarLibro/eliminarLibro/prestar/devolver solo aplicaban scopePlantel() (que
     * ACOTA el plantel objetivo cuando nivel(u)&gt;2, pero nunca rechaza al solicitante) —
     * cualquier cuenta autenticada, incluidos alumnos/padres (nivelAcceso &ge;5), podía dar
     * de alta/baja libros y registrar préstamos/devoluciones de su propio plantel (BFLA,
     * OWASP API5 — asimetría con listarLibros()/obtenerLibro(), que sí son de solo lectura
     * abiertas a cualquier autenticado por diseño).
     */
    private void requireStaff(AdesUser user) {
        if (nivel(user) > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
    }

    // ── Acervo ───────────────────────────────────────────────────────────────

    /**
     * Recupera el catálogo de libros aplicando filtros por texto de búsqueda,
     * categoría, plantel y disponibilidad.
     *
     * @param texto Palabra o frase de búsqueda en título o autor.
     * @param categoria Categoría del libro (opcional).
     * @param plantelId Identificador del plantel (opcional).
     * @param soloDisponibles Si es true, retorna solo los libros con ejemplares disponibles.
     * @param jwt Credenciales del usuario.
     * @return ResponseEntity con la lista de libros del catálogo que coinciden.
     */
    @GetMapping("/libros")
    public ResponseEntity<List<Map<String, Object>>> listarLibros(
            @RequestParam(value = "q",         required = false) String texto,
            @RequestParam(value = "categoria", required = false) String categoria,
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "solo_disponibles", defaultValue = "false") boolean soloDisponibles,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        return ResponseEntity.ok(query.listLibros(texto, categoria, scopePlantel(user, plantelId), soloDisponibles));
    }

    /**
     * Obtiene los detalles de un libro del catálogo por su identificador único.
     *
     * @param id Identificador único del libro.
     * @param jwt Credenciales del usuario.
     * @return ResponseEntity con los atributos del libro.
     */
    @GetMapping("/libros/{id}")
    public ResponseEntity<BibliotecaLibro> obtenerLibro(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(query.findLibro(id));
    }

    /**
     * Registra un nuevo libro en el acervo del plantel correspondiente.
     *
     * @param body Atributos del libro.
     * @param jwt Credenciales del usuario.
     * @return ResponseEntity con el identificador único del libro creado.
     */
    @PostMapping("/libros")
    public ResponseEntity<Map<String, Object>> crearLibro(
            @RequestBody BibliotecaLibro body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        // BibliotecaLibro es entidad JPA (no anotamos validación en la entidad — riesgo
        // sobre Hibernate); titulo/categoria son NOT NULL en BD, validamos manualmente
        // para dar un 400 claro en vez de una excepción de dominio no capturada (500).
        if (body.getTitulo() == null || body.getTitulo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "titulo es obligatorio");
        }
        if (body.getCategoria() == null || body.getCategoria().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoria es obligatoria");
        }
        UUID plantelId = scopePlantel(user, body.getPlantelId());
        int total = body.getEjemplaresTotal() != null ? body.getEjemplaresTotal() : 1;
        var cmd = new RegistrarLibroUseCase.Command(
                body.getTitulo(), body.getAutor(), body.getIsbn(), body.getEditorial(),
                body.getAnioPublicacion(), CategoriaLibro.of(body.getCategoria()),
                body.getUbicacion(), plantelId, total, nivel(user));
        UUID id = registrarLibro.registrar(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    /**
     * Actualiza parcialmente la información de un libro registrado.
     *
     * @param id Identificador único del libro.
     * @param body Atributos modificados.
     * @param jwt Credenciales del usuario.
     * @return ResponseEntity indicando éxito.
     */
    @PatchMapping("/libros/{id}")
    public ResponseEntity<Map<String, Object>> actualizarLibro(
            @PathVariable("id") UUID id,
            @RequestBody BibliotecaLibro body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        BibliotecaLibro libro = query.findLibro(id);
        if (nivel(user) > 2 && user.getPlantelId() != null && !libro.getPlantelId().equals(user.getPlantelId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puede modificar un libro de otro plantel");
        }
        var cmd = new ActualizarLibroUseCase.Command(
                id, body.getTitulo(), body.getAutor(), body.getIsbn(), body.getEditorial(),
                body.getAnioPublicacion(), body.getCategoria(), body.getUbicacion(),
                body.getEjemplaresTotal(), nivel(user));
        actualizarLibro.actualizar(cmd);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /**
     * Elimina un libro del acervo catalogado.
     *
     * @param id Identificador único del libro a borrar.
     * @param jwt Credenciales del usuario.
     */
    @DeleteMapping("/libros/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarLibro(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        BibliotecaLibro libro = query.findLibro(id);
        if (nivel(user) > 2 && user.getPlantelId() != null && !libro.getPlantelId().equals(user.getPlantelId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puede eliminar un libro de otro plantel");
        }
        eliminarLibro.eliminar(id, nivel(user));
    }

    // ── Circulación ──────────────────────────────────────────────────────────

    /**
     * Lista los préstamos de libros registrados bajo diferentes filtros.
     *
     * @param personaId Identificador opcional del lector.
     * @param libroId Identificador opcional del libro.
     * @param estatus Estado del préstamo (VIGENTE, DEVUELTO, etc.).
     * @param plantelId Identificador del plantel.
     * @param jwt Credenciales del usuario.
     * @return ResponseEntity con la lista de préstamos activos e históricos.
     */
    @GetMapping("/prestamos")
    public ResponseEntity<List<Map<String, Object>>> listarPrestamos(
            @RequestParam(value = "persona_id", required = false) UUID personaId,
            @RequestParam(value = "libro_id",   required = false) UUID libroId,
            @RequestParam(value = "estatus",    required = false) String estatus,
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "pagina", defaultValue = "1") int pagina,
            @RequestParam(value = "por_pagina", defaultValue = "30") int porPagina,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        pagina = Math.max(pagina, 1);
        porPagina = Math.min(Math.max(porPagina, 1), 200);
        return ResponseEntity.ok(query.listPrestamos(personaId, libroId, estatus, scopePlantel(user, plantelId), pagina, porPagina));
    }

    /**
     * Registra el préstamo de un ejemplar de libro a un alumno o personal de la escuela.
     *
     * @param body Parámetros del préstamo (libroId, personaId, fechaDevolucionEsperada, observaciones).
     * @param jwt Credenciales del usuario.
     * @return ResponseEntity con el identificador del préstamo creado.
     */
    @PostMapping("/prestamos")
    public ResponseEntity<Map<String, Object>> prestar(
            @RequestBody @Valid PrestamoRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        BibliotecaLibro libro = query.findLibro(body.libroId());
        if (nivel(user) > 2 && user.getPlantelId() != null && !libro.getPlantelId().equals(user.getPlantelId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puede prestar un libro de otro plantel");
        }
        var cmd = new RegistrarPrestamoUseCase.Command(
                body.libroId(), body.personaId(), scopePlantel(user, null),
                body.fechaDevolucionEsperada(), body.observaciones(), nivel(user));
        UUID id = registrarPrestamo.prestar(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    /**
     * Registra la devolución física de un libro prestado.
     *
     * @param id Identificador único del préstamo.
     * @param body Detalles finales de la devolución (estatusFinal, observaciones).
     * @param jwt Credenciales del usuario.
     * @return ResponseEntity indicando éxito en el registro de devolución.
     */
    @PostMapping("/prestamos/{id}/devolver")
    public ResponseEntity<Map<String, Object>> devolver(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) DevolucionRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        BibliotecaPrestamo prestamo = query.findPrestamoAbierto(id);
        if (nivel(user) > 2 && user.getPlantelId() != null && !prestamo.getPlantelId().equals(user.getPlantelId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puede registrar la devolución de un préstamo de otro plantel");
        }
        String est = (body != null && body.estatusFinal() != null) ? body.estatusFinal() : "DEVUELTO";
        String obs = body != null ? body.observaciones() : null;
        var cmd = new DevolverPrestamoUseCase.Command(
                id, EstatusPrestamo.of(est), obs, nivel(user));
        devolverPrestamo.devolver(cmd);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── Payloads ───────────────────────────────────────────────────────────
    public record PrestamoRequest(
            @NotNull(message = "libroId es obligatorio") UUID libroId,
            @NotNull(message = "personaId es obligatorio") UUID personaId,
            @NotNull(message = "fechaDevolucionEsperada es obligatoria") LocalDate fechaDevolucionEsperada,
            String observaciones) {}

    public record DevolucionRequest(String estatusFinal, String observaciones) {}
}
