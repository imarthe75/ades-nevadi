package mx.ades.modules.portal;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

/**
 * Endpoints públicos del portal de convocatorias (sin autenticación).
 * Prefijo: /api/portal/  — no pasa por Spring Security OAuth2.
 */
@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"https://portalnvd.setag.mx", "http://localhost:4201"})
public class PortalPublicoController {

    private final PortalPublicoService portalSvc;
    private final PortalJwtService jwtService;
    private final PortalEmailService emailService;
    private final BCryptPasswordEncoder passwordEncoder;

    // ─────────────────────────────────────────────────────────
    // CONVOCATORIAS — públicas
    // ─────────────────────────────────────────────────────────

    @GetMapping("/convocatorias")
    public ResponseEntity<List<Map<String, Object>>> listarConvocatorias(
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String tipo,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @RequestParam(name = "nivel_id", required = false) UUID nivelId,
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(portalSvc.listarConvocatorias(categoria, tipo, plantelId, nivelId, limit, skip));
    }

    @GetMapping("/convocatorias/{id}")
    public ResponseEntity<Map<String, Object>> detalleConvocatoria(@PathVariable UUID id) {
        List<Map<String, Object>> rows = portalSvc.detalleConvocatoria(id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Convocatoria no encontrada");
        Map<String, Object> conv = new HashMap<>(rows.get(0));
        conv.put("requisitos_documentos", portalSvc.requisitosConvocatoria(id));
        conv.put("secciones", portalSvc.seccionesConvocatoria(id));
        return ResponseEntity.ok(conv);
    }

    @GetMapping("/catalogo")
    public ResponseEntity<Map<String, Object>> catalogo() {
        var categorias = java.util.Arrays.asList("OFERTA_EDUCATIVA", "RECURSOS_HUMANOS");
        var tipos = java.util.Arrays.asList("INSCRIPCION", "REINSCRIPCION", "BECA",
                "INTERCAMBIO", "EXTRACURRICULAR", "VACANTE_DOCENTE", "VACANTE_ADMINISTRATIVA");
        return ResponseEntity.ok(Map.of(
                "planteles", portalSvc.catalogo(),
                "niveles", portalSvc.nivelesEnConvocatorias(),
                "categorias", categorias,
                "tipos", tipos));
    }

    @GetMapping("/seguimiento/{folio}")
    public ResponseEntity<Map<String, Object>> seguimiento(@PathVariable String folio) {
        List<Map<String, Object>> rows = portalSvc.seguimiento(folio);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No se encontró ninguna postulación con el folio indicado");
        return ResponseEntity.ok(rows.get(0));
    }

    // ─────────────────────────────────────────────────────────
    // AUTENTICACIÓN — registro y login de postulantes externos
    // ─────────────────────────────────────────────────────────

    @Data
    public static class RegistroRequest {
        private String email;
        private String password;
        private String nombreCompleto;
        private String telefono;
        private String fechaNacimiento;
        private boolean consentimientoPrivacidad;
        private String consentimientoVersion;
    }

    @PostMapping("/auth/registro")
    public ResponseEntity<Map<String, Object>> registro(@RequestBody RegistroRequest req) {
        if (req.getEmail() == null || !req.getEmail().contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email inválido");
        }
        if (req.getPassword() == null || req.getPassword().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La contraseña debe tener al menos 8 caracteres");
        }
        if (req.getNombreCompleto() == null || req.getNombreCompleto().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre es obligatorio");
        }
        if (!req.isConsentimientoPrivacidad()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debes aceptar el Aviso de Privacidad para registrarte");
        }
        String emailNorm = req.getEmail().toLowerCase().trim();
        if (portalSvc.emailExiste(emailNorm)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una cuenta con ese correo electrónico");
        }
        String hash  = passwordEncoder.encode(req.getPassword());
        String token = UUID.randomUUID().toString().replace("-", "");
        portalSvc.registrarUsuario(emailNorm, hash, req.getNombreCompleto().trim(),
                req.getTelefono(), req.getFechaNacimiento(), token,
                req.getConsentimientoVersion() != null ? req.getConsentimientoVersion() : "1.0");
        emailService.enviarVerificacion(emailNorm, req.getNombreCompleto(), token);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "mensaje", "Cuenta creada. Revisa tu correo para verificar tu dirección.",
                "email", emailNorm));
    }

    @PostMapping("/auth/verificar/{token}")
    public ResponseEntity<Map<String, Object>> verificarEmail(@PathVariable String token) {
        int updated = portalSvc.verificarEmail(token);
        if (updated == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Token de verificación inválido o ya utilizado");
        return ResponseEntity.ok(Map.of("mensaje", "Correo verificado correctamente. Ya puedes iniciar sesión."));
    }

    @Data
    public static class LoginRequest {
        private String email;
        private String password;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest req) {
        if (req.getEmail() == null || req.getPassword() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email y contraseña son requeridos");
        }
        String emailNorm = req.getEmail().toLowerCase().trim();
        List<Map<String, Object>> rows = portalSvc.fetchUserByEmail(emailNorm);
        if (rows.isEmpty() || !Boolean.TRUE.equals(rows.get(0).get("is_active"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }
        Map<String, Object> u = rows.get(0);
        if (!passwordEncoder.matches(req.getPassword(), (String) u.get("password_hash"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }
        if (!Boolean.TRUE.equals(u.get("is_email_verified"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Debes verificar tu correo electrónico antes de iniciar sesión");
        }
        UUID uid = (UUID) u.get("id");
        portalSvc.actualizarUltimoAcceso(uid);
        String token = jwtService.generarToken(uid, emailNorm, (String) u.get("nombre_completo"));
        return ResponseEntity.ok(Map.of(
                "token", token,
                "nombre_completo", u.get("nombre_completo"),
                "email", emailNorm));
    }

    @Data
    public static class RecuperarRequest { private String email; }

    @PostMapping("/auth/recuperar")
    public ResponseEntity<Map<String, Object>> recuperar(@RequestBody RecuperarRequest req) {
        String emailNorm = req.getEmail().toLowerCase().trim();
        String token = UUID.randomUUID().toString().replace("-", "");
        portalSvc.solicitarRecuperacion(emailNorm, token);
        emailService.enviarRecuperacionClave(emailNorm, token);
        return ResponseEntity.ok(Map.of("mensaje",
                "Si el correo existe en nuestro sistema recibirás instrucciones para recuperar tu contraseña."));
    }

    @Data
    public static class NuevaClaveRequest { private String password; }

    @PostMapping("/auth/nueva-clave/{token}")
    public ResponseEntity<Map<String, Object>> nuevaClave(
            @PathVariable String token, @RequestBody NuevaClaveRequest req) {
        if (req.getPassword() == null || req.getPassword().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La contraseña debe tener al menos 8 caracteres");
        }
        int updated = portalSvc.actualizarClave(token, passwordEncoder.encode(req.getPassword()));
        if (updated == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Token inválido o expirado. Solicita un nuevo enlace de recuperación.");
        return ResponseEntity.ok(Map.of("mensaje", "Contraseña actualizada correctamente."));
    }

    // ─────────────────────────────────────────────────────────
    // ARCO — ejercicio de derechos (LFPDPPP)
    // ─────────────────────────────────────────────────────────

    @Data
    public static class ArcoRequest {
        private String email;
        private String nombre;
        private String tipo;
        private String descripcion;
    }

    @PostMapping("/arco")
    public ResponseEntity<Map<String, Object>> solicitarArco(@RequestBody ArcoRequest req) {
        if (req.getEmail() == null || req.getNombre() == null || req.getTipo() == null || req.getDescripcion() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Todos los campos son obligatorios");
        }
        String emailNorm = req.getEmail().toLowerCase().trim();
        LocalDate fechaLimite = LocalDate.now().plusDays(28);
        UUID portalUserId = portalSvc.fetchPortalUserId(emailNorm);
        String folio = "ARCO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        portalSvc.insertarSolicitudArco(portalUserId, emailNorm, req.getNombre().trim(),
                req.getTipo(), req.getDescripcion(), fechaLimite);
        emailService.enviarAcuseArco(emailNorm, req.getNombre(), req.getTipo(), folio);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "mensaje", "Solicitud ARCO recibida. Recibirás respuesta en un máximo de 20 días hábiles.",
                "fecha_limite", fechaLimite.toString(),
                "contacto", "privacidad@nevadi.edu.mx"));
    }

    @GetMapping("/aviso-privacidad")
    public ResponseEntity<Map<String, Object>> avisoPrivacidad() {
        return ResponseEntity.ok(Map.of(
                "version", "1.0",
                "fecha_actualizacion", "2026-06-15",
                "responsable", "Instituto Nevadi S.C.",
                "contacto_privacidad", "privacidad@nevadi.edu.mx",
                "arco_plazo_dias_habiles", 20,
                "url_pdf", "/assets/aviso-privacidad-v1.pdf"
        ));
    }
}
