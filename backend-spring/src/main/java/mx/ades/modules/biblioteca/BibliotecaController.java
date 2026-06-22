package mx.ades.modules.biblioteca;

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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    // ── Acervo ───────────────────────────────────────────────────────────────

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

    @GetMapping("/libros/{id}")
    public ResponseEntity<BibliotecaLibro> obtenerLibro(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(query.findLibro(id));
    }

    @PostMapping("/libros")
    public ResponseEntity<Map<String, Object>> crearLibro(
            @RequestBody BibliotecaLibro body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        UUID plantelId = scopePlantel(user, body.getPlantelId());
        int total = body.getEjemplaresTotal() != null ? body.getEjemplaresTotal() : 1;
        var cmd = new RegistrarLibroUseCase.Command(
                body.getTitulo(), body.getAutor(), body.getIsbn(), body.getEditorial(),
                body.getAnioPublicacion(), CategoriaLibro.of(body.getCategoria()),
                body.getUbicacion(), plantelId, total, nivel(user));
        UUID id = registrarLibro.registrar(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @PatchMapping("/libros/{id}")
    public ResponseEntity<Map<String, Object>> actualizarLibro(
            @PathVariable("id") UUID id,
            @RequestBody BibliotecaLibro body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        var cmd = new ActualizarLibroUseCase.Command(
                id, body.getTitulo(), body.getAutor(), body.getIsbn(), body.getEditorial(),
                body.getAnioPublicacion(), body.getCategoria(), body.getUbicacion(),
                body.getEjemplaresTotal(), nivel(user));
        actualizarLibro.actualizar(cmd);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/libros/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarLibro(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        eliminarLibro.eliminar(id, nivel(user));
    }

    // ── Circulación ──────────────────────────────────────────────────────────

    @GetMapping("/prestamos")
    public ResponseEntity<List<Map<String, Object>>> listarPrestamos(
            @RequestParam(value = "persona_id", required = false) UUID personaId,
            @RequestParam(value = "libro_id",   required = false) UUID libroId,
            @RequestParam(value = "estatus",    required = false) String estatus,
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        return ResponseEntity.ok(query.listPrestamos(personaId, libroId, estatus, scopePlantel(user, plantelId)));
    }

    @PostMapping("/prestamos")
    public ResponseEntity<Map<String, Object>> prestar(
            @RequestBody PrestamoRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        var cmd = new RegistrarPrestamoUseCase.Command(
                body.libroId(), body.personaId(), scopePlantel(user, null),
                body.fechaDevolucionEsperada(), body.observaciones(), nivel(user));
        UUID id = registrarPrestamo.prestar(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @PostMapping("/prestamos/{id}/devolver")
    public ResponseEntity<Map<String, Object>> devolver(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) DevolucionRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        String est = (body != null && body.estatusFinal() != null) ? body.estatusFinal() : "DEVUELTO";
        String obs = body != null ? body.observaciones() : null;
        var cmd = new DevolverPrestamoUseCase.Command(
                id, EstatusPrestamo.of(est), obs, nivel(user));
        devolverPrestamo.devolver(cmd);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── Payloads ───────────────────────────────────────────────────────────
    public record PrestamoRequest(
            UUID libroId, UUID personaId, LocalDate fechaDevolucionEsperada, String observaciones) {}

    public record DevolucionRequest(String estatusFinal, String observaciones) {}
}
