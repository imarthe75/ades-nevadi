package mx.ades.modules.portal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Component
public class PortalPublicoService {

    private final JdbcTemplate jdbc;

    public PortalPublicoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> listarConvocatorias(String categoria, String tipo,
            UUID plantelId, UUID nivelId, int limit, int skip) {
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
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> detalleConvocatoria(UUID id) {
        return jdbc.queryForList("""
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
    }

    public List<Map<String, Object>> requisitosConvocatoria(UUID id) {
        return jdbc.queryForList("""
            SELECT id, nombre, descripcion, es_obligatorio,
                   array_to_json(tipos_mime_permitidos)::text AS tipos_mime_permitidos,
                   tamano_maximo_mb, orden
            FROM portal.requisitos_documentos
            WHERE convocatoria_id = ? AND is_active = TRUE
            ORDER BY orden, nombre
            """, id);
    }

    public List<Map<String, Object>> seccionesConvocatoria(UUID id) {
        return jdbc.queryForList("""
            SELECT id, tipo_seccion, titulo, contenido, datos::text AS datos, orden
            FROM portal.secciones_convocatoria
            WHERE convocatoria_id = ? AND is_active = TRUE
            ORDER BY orden, fecha_creacion
            """, id);
    }

    public List<Map<String, Object>> catalogo() {
        return jdbc.queryForList("""
            SELECT DISTINCT p.id, p.nombre_plantel, p.clave_ct
            FROM ades_planteles p
            JOIN portal.convocatorias c ON c.plantel_id = p.id
            WHERE c.is_published = TRUE AND c.is_active = TRUE
              AND c.fecha_cierre_postulacion >= NOW()
            ORDER BY p.nombre_plantel
            """);
    }

    public List<Map<String, Object>> nivelesEnConvocatorias() {
        return jdbc.queryForList("""
            SELECT DISTINCT ne.id, ne.nombre_nivel, ne.autoridad_educativa
            FROM ades_niveles_educativos ne
            JOIN portal.convocatorias c ON c.nivel_educativo_id = ne.id
            WHERE c.is_published = TRUE AND c.is_active = TRUE
              AND c.fecha_cierre_postulacion >= NOW()
            ORDER BY ne.nombre_nivel
            """);
    }

    public List<Map<String, Object>> seguimiento(String folio) {
        return jdbc.queryForList("""
            SELECT po.folio, po.estado, po.fecha_envio, po.fecha_creacion,
                   c.titulo AS convocatoria_titulo, c.tipo AS convocatoria_tipo
            FROM portal.postulaciones po
            JOIN portal.convocatorias c ON c.id = po.convocatoria_id
            WHERE po.folio = ? AND po.is_active = TRUE
            """, folio.toUpperCase().trim());
    }

    public boolean emailExiste(String email) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM portal.usuarios WHERE email = ?)", Boolean.class, email));
    }

    @Transactional
    public UUID registrarUsuario(String email, String hash, String nombreCompleto,
                                  String telefono, String fechaNacimiento,
                                  String token, String consentimientoVersion) {
        return jdbc.queryForObject("""
            INSERT INTO portal.usuarios
              (email, password_hash, nombre_completo, telefono, fecha_nacimiento,
               is_email_verified, token_verificacion,
               consentimiento_privacidad, consentimiento_fecha, consentimiento_version,
               is_active, usuario_creacion)
            VALUES (?,?,?,?,?::DATE, FALSE,?,  TRUE,NOW(),?,  TRUE,'portal-registro')
            RETURNING id
            """, UUID.class,
                email, hash, nombreCompleto, telefono, fechaNacimiento, token, consentimientoVersion);
    }

    @Transactional
    public int verificarEmail(String token) {
        return jdbc.update("""
            UPDATE portal.usuarios
            SET is_email_verified = TRUE, token_verificacion = NULL, usuario_modificacion = 'portal-verify'
            WHERE token_verificacion = ? AND is_active = TRUE
            """, token);
    }

    public List<Map<String, Object>> fetchUserByEmail(String email) {
        return jdbc.queryForList("""
            SELECT id, password_hash, nombre_completo, is_email_verified, is_active
            FROM portal.usuarios WHERE email = ?
            """, email);
    }

    @Transactional
    public void actualizarUltimoAcceso(UUID uid) {
        jdbc.update("UPDATE portal.usuarios SET fecha_ultimo_acceso = NOW() WHERE id = ?", uid);
    }

    @Transactional
    public void solicitarRecuperacion(String email, String token) {
        jdbc.update("""
            UPDATE portal.usuarios
            SET token_recuperacion = ?, token_expira_en = NOW() + INTERVAL '2 hours',
                usuario_modificacion = 'portal-recuperar'
            WHERE email = ? AND is_active = TRUE
            """, token, email);
    }

    @Transactional
    public int actualizarClave(String token, String hash) {
        return jdbc.update("""
            UPDATE portal.usuarios
            SET password_hash = ?, token_recuperacion = NULL, token_expira_en = NULL,
                usuario_modificacion = 'portal-nueva-clave'
            WHERE token_recuperacion = ? AND token_expira_en > NOW() AND is_active = TRUE
            """, hash, token);
    }

    public UUID fetchPortalUserId(String email) {
        return jdbc.query(
                "SELECT id FROM portal.usuarios WHERE email = ?",
                rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
                email);
    }

    @Transactional
    public void insertarSolicitudArco(UUID portalUserId, String email, String nombre,
                                       String tipo, String descripcion, LocalDate fechaLimite) {
        jdbc.update("""
            INSERT INTO portal.solicitudes_arco
              (usuario_id, email_solicitante, nombre_solicitante, tipo, descripcion,
               estado, fecha_limite_respuesta, usuario_creacion)
            VALUES (?,?,?,?::portal.tipo_solicitud_arco,?,  'RECIBIDA',?,  'portal-arco')
            """,
                portalUserId, email, nombre, tipo.toUpperCase(), descripcion, fechaLimite);
    }
}
