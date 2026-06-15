package mx.ades.modules.portal;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private final JdbcTemplate jdbc;
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

        StringBuilder sql = new StringBuilder("""
            SELECT c.id, c.categoria, c.tipo, c.titulo, c.descripcion,
                   c.fecha_inicio_postulacion, c.fecha_cierre_postulacion,
                   c.cupo_maximo, c.cupo_actual, c.imagen_url,
                   p.nombre_plantel,
                   ne.nombre_nivel
            FROM portal.convocatorias c
            LEFT JOIN ades_planteles p ON p.id = c.plantel_id
            LEFT JOIN ades_niveles_educativos ne ON ne.id = c.nivel_educativo_id
            WHERE c.is_published = TRUE
              AND c.is_active = TRUE
              AND c.fecha_cierre_postulacion >= NOW()
            """);

        List<Object> params = new ArrayList<>();
        if (categoria != null && !categoria.isBlank()) {
            sql.append("AND c.categoria = ?::portal.categoria_convocatoria ");
            params.add(categoria.toUpperCase());
        }
        if (tipo != null && !tipo.isBlank()) {
            sql.append("AND c.tipo = ?::portal.tipo_convocatoria ");
            params.add(tipo.toUpperCase());
        }
        if (plantelId != null) {
            sql.append("AND (c.plantel_id = ? OR c.plantel_id IS NULL) ");
            params.add(plantelId);
        }
        if (nivelId != null) {
            sql.append("AND (c.nivel_educativo_id = ? OR c.nivel_educativo_id IS NULL) ");
            params.add(nivelId);
        }
        sql.append("ORDER BY c.fecha_cierre_postulacion ASC LIMIT ? OFFSET ?");
        params.add(Math.min(limit, 50));
        params.add(skip);

        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }

    @GetMapping("/convocatorias/{id}")
    public ResponseEntity<Map<String, Object>> detalleConvocatoria(@PathVariable UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT c.id, c.categoria, c.tipo, c.titulo, c.descripcion, c.requisitos_generales,
                   c.fecha_inicio_postulacion, c.fecha_cierre_postulacion,
                   c.cupo_maximo, c.cupo_actual, c.imagen_url,
                   c.aviso_privacidad_version,
                   p.nombre_plantel, p.id AS plantel_id,
                   ne.nombre_nivel, ne.id AS nivel_educativo_id
            FROM portal.convocatorias c
            LEFT JOIN ades_planteles p ON p.id = c.plantel_id
            LEFT JOIN ades_niveles_educativos ne ON ne.id = c.nivel_educativo_id
            WHERE c.id = ? AND c.is_active = TRUE
            """, id);

        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Convocatoria no encontrada");

        Map<String, Object> conv = new HashMap<>(rows.get(0));
        List<Map<String, Object>> requisitos = jdbc.queryForList("""
            SELECT id, nombre, descripcion, es_obligatorio, tipos_mime_permitidos, tamano_maximo_mb, orden
            FROM portal.requisitos_documentos
            WHERE convocatoria_id = ? AND is_active = TRUE
            ORDER BY orden, nombre
            """, id);
        conv.put("requisitos_documentos", requisitos);

        // Secciones de contenido LMS (página descriptiva)
        List<Map<String, Object>> secciones = jdbc.queryForList("""
            SELECT id, tipo_seccion, titulo, contenido, datos, orden
            FROM portal.secciones_convocatoria
            WHERE convocatoria_id = ? AND is_active = TRUE
            ORDER BY orden, fecha_creacion
            """, id);
        conv.put("secciones", secciones);

        return ResponseEntity.ok(conv);
    }

    @GetMapping("/seguimiento/{folio}")
    public ResponseEntity<Map<String, Object>> seguimiento(@PathVariable String folio) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT po.folio, po.estado, po.fecha_envio, po.fecha_creacion,
                   c.titulo AS convocatoria_titulo, c.tipo AS convocatoria_tipo
            FROM portal.postulaciones po
            JOIN portal.convocatorias c ON c.id = po.convocatoria_id
            WHERE po.folio = ? AND po.is_active = TRUE
            """, folio.toUpperCase().trim());

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
        boolean existe = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM portal.usuarios WHERE email = ?)", Boolean.class, emailNorm));
        if (existe) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ya existe una cuenta con ese correo electrónico");

        String hash  = passwordEncoder.encode(req.getPassword());
        String token = UUID.randomUUID().toString().replace("-", "");

        UUID id = jdbc.queryForObject("""
            INSERT INTO portal.usuarios
              (email, password_hash, nombre_completo, telefono, fecha_nacimiento,
               is_email_verified, token_verificacion,
               consentimiento_privacidad, consentimiento_fecha, consentimiento_version,
               is_active, usuario_creacion)
            VALUES (?,?,?,?,?::DATE, FALSE,?,  TRUE,NOW(),?,  TRUE,'portal-registro')
            RETURNING id
            """, UUID.class,
                emailNorm, hash, req.getNombreCompleto().trim(),
                req.getTelefono(), req.getFechaNacimiento(),
                token,
                req.getConsentimientoVersion() != null ? req.getConsentimientoVersion() : "1.0");

        emailService.enviarVerificacion(emailNorm, req.getNombreCompleto(), token);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "mensaje", "Cuenta creada. Revisa tu correo para verificar tu dirección.",
                "email", emailNorm));
    }

    @PostMapping("/auth/verificar/{token}")
    public ResponseEntity<Map<String, Object>> verificarEmail(@PathVariable String token) {
        int updated = jdbc.update("""
            UPDATE portal.usuarios
            SET is_email_verified = TRUE, token_verificacion = NULL, usuario_modificacion = 'portal-verify'
            WHERE token_verificacion = ? AND is_active = TRUE
            """, token);
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

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id, password_hash, nombre_completo, is_email_verified, is_active
            FROM portal.usuarios WHERE email = ?
            """, emailNorm);

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
        jdbc.update("UPDATE portal.usuarios SET fecha_ultimo_acceso = NOW() WHERE id = ?", uid);

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
        jdbc.update("""
            UPDATE portal.usuarios
            SET token_recuperacion = ?, token_expira_en = NOW() + INTERVAL '2 hours',
                usuario_modificacion = 'portal-recuperar'
            WHERE email = ? AND is_active = TRUE
            """, token, emailNorm);
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
        int updated = jdbc.update("""
            UPDATE portal.usuarios
            SET password_hash = ?, token_recuperacion = NULL, token_expira_en = NULL,
                usuario_modificacion = 'portal-nueva-clave'
            WHERE token_recuperacion = ? AND token_expira_en > NOW() AND is_active = TRUE
            """, passwordEncoder.encode(req.getPassword()), token);
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
        private String tipo; // ACCESO | RECTIFICACION | CANCELACION | OPOSICION
        private String descripcion;
    }

    @PostMapping("/arco")
    public ResponseEntity<Map<String, Object>> solicitarArco(@RequestBody ArcoRequest req) {
        if (req.getEmail() == null || req.getNombre() == null || req.getTipo() == null || req.getDescripcion() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Todos los campos son obligatorios");
        }
        String emailNorm = req.getEmail().toLowerCase().trim();
        LocalDate fechaLimite = LocalDate.now().plusDays(28); // ~20 días hábiles

        UUID portalUserId = jdbc.query(
                "SELECT id FROM portal.usuarios WHERE email = ?",
                rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
                emailNorm);

        String folio = "ARCO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        jdbc.update("""
            INSERT INTO portal.solicitudes_arco
              (usuario_id, email_solicitante, nombre_solicitante, tipo, descripcion,
               estado, fecha_limite_respuesta, usuario_creacion)
            VALUES (?,?,?,?::portal.tipo_solicitud_arco,?,  'RECIBIDA',?,  'portal-arco')
            """,
                portalUserId, emailNorm, req.getNombre().trim(),
                req.getTipo().toUpperCase(), req.getDescripcion(),
                fechaLimite);

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
