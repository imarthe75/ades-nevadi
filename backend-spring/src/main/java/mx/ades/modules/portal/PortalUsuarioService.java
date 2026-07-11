package mx.ades.modules.portal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
public class PortalUsuarioService {

    private final JdbcTemplate jdbc;

    public PortalUsuarioService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> perfil(UUID uid) {
        return jdbc.queryForMap("""
            SELECT id, email, nombre_completo, telefono, fecha_nacimiento,
                   is_email_verified, consentimiento_privacidad, consentimiento_version,
                   fecha_ultimo_acceso, fecha_creacion
            FROM portal.usuarios WHERE id = ?
            """, uid);
    }

    @Transactional
    public void actualizarPerfil(UUID uid, String nombreCompleto, String telefono) {
        jdbc.update("""
            UPDATE portal.usuarios
            SET nombre_completo = COALESCE(?, nombre_completo),
                telefono = COALESCE(?, telefono),
                usuario_modificacion = 'portal-usuario'
            WHERE id = ?
            """, nombreCompleto, telefono, uid);
    }

    public List<Map<String, Object>> misPostulaciones(UUID uid) {
        return jdbc.queryForList("""
            SELECT po.id, po.folio, po.estado, po.fecha_envio, po.fecha_creacion,
                   c.titulo AS convocatoria_titulo, c.tipo AS convocatoria_tipo,
                   c.fecha_cierre_postulacion,
                   (SELECT COUNT(*) FROM portal.documentos d WHERE d.postulacion_id = po.id AND d.is_active = TRUE) AS total_documentos
            FROM portal.postulaciones po
            JOIN portal.convocatorias c ON c.id = po.convocatoria_id
            WHERE po.usuario_id = ? AND po.is_active = TRUE
            ORDER BY po.fecha_creacion DESC
            """, uid);
    }

    public List<Map<String, Object>> fetchConvocatoriaAbierta(UUID convocatoriaId) {
        return jdbc.queryForList("""
            SELECT id, tipo, titulo, cupo_maximo, cupo_actual, fecha_cierre_postulacion
            FROM portal.convocatorias
            WHERE id = ? AND is_published = TRUE AND is_active = TRUE
              AND fecha_inicio_postulacion <= NOW() AND fecha_cierre_postulacion >= NOW()
            """, convocatoriaId);
    }

    public boolean yaPostulo(UUID convocatoriaId, UUID uid) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM portal.postulaciones WHERE convocatoria_id = ? AND usuario_id = ? AND is_active = TRUE)",
                Boolean.class, convocatoriaId, uid));
    }

    public String generarFolio(String tipo) {
        return jdbc.queryForObject(
                "SELECT portal.generar_folio(?::portal.tipo_convocatoria)", String.class, tipo);
    }

    @Transactional
    public UUID insertarPostulacion(UUID convocatoriaId, UUID uid, String folio,
                                     String datosJson, String ip) {
        return jdbc.queryForObject("""
            INSERT INTO portal.postulaciones
              (convocatoria_id, usuario_id, folio, estado, datos_adicionales,
               consentimiento_privacidad, ip_postulacion, usuario_creacion)
            VALUES (?,?,?,'BORRADOR', ?::JSONB, TRUE,?::INET,'portal-usuario')
            RETURNING id
            """, UUID.class, convocatoriaId, uid, folio, datosJson, ip);
    }

    public List<Map<String, Object>> detallePostulacion(UUID id, UUID uid) {
        return jdbc.queryForList("""
            SELECT po.id, po.folio, po.estado, po.datos_adicionales, po.fecha_envio,
                   po.fecha_creacion, po.consentimiento_privacidad,
                   c.titulo AS convocatoria_titulo, c.tipo AS convocatoria_tipo,
                   c.fecha_cierre_postulacion, c.requisitos_generales
            FROM portal.postulaciones po
            JOIN portal.convocatorias c ON c.id = po.convocatoria_id
            WHERE po.id = ? AND po.usuario_id = ? AND po.is_active = TRUE
            """, id, uid);
    }

    public List<Map<String, Object>> documentosPostulacion(UUID postulacionId) {
        return jdbc.queryForList("""
            SELECT d.id, d.tipo_documento, d.nombre_original, d.mimetype,
                   d.tamano_bytes, d.fecha_creacion,
                   rd.nombre AS requisito_nombre, rd.es_obligatorio
            FROM portal.documentos d
            LEFT JOIN portal.requisitos_documentos rd ON rd.id = d.requisito_id
            WHERE d.postulacion_id = ? AND d.is_active = TRUE
            ORDER BY d.fecha_creacion
            """, postulacionId);
    }

    public List<Map<String, Object>> requisitosPostulacion(UUID postulacionId) {
        return jdbc.queryForList("""
            SELECT rd.id, rd.nombre, rd.descripcion, rd.es_obligatorio,
                   rd.tipos_mime_permitidos, rd.tamano_maximo_mb, rd.orden,
                   EXISTS(
                     SELECT 1 FROM portal.documentos d
                     WHERE d.postulacion_id = ? AND d.requisito_id = rd.id AND d.is_active = TRUE
                   ) AS cumplido
            FROM portal.requisitos_documentos rd
            JOIN portal.convocatorias c ON c.id = rd.convocatoria_id
            JOIN portal.postulaciones po ON po.id = ?
            WHERE po.convocatoria_id = c.id AND rd.is_active = TRUE
            ORDER BY rd.orden, rd.nombre
            """, postulacionId, postulacionId);
    }

    public String fetchEstadoPostulacion(UUID postulacionId, UUID uid) {
        return jdbc.queryForObject(
                "SELECT estado::TEXT FROM portal.postulaciones WHERE id = ? AND usuario_id = ? AND is_active = TRUE",
                String.class, postulacionId, uid);
    }

    @Transactional
    public UUID insertarDocumento(UUID postulacionId, UUID requisitoId, String tipoDocumento,
                                   String nombreOriginal, String mime, long tamanoBytes,
                                   String ruta, String sha256) {
        return jdbc.queryForObject("""
            INSERT INTO portal.documentos
              (postulacion_id, requisito_id, tipo_documento, nombre_original,
               mimetype, tamano_bytes, ruta_minio, hash_sha256, usuario_creacion)
            VALUES (?,?,?,?,?,?,?,?,'portal-usuario')
            RETURNING id
            """, UUID.class, postulacionId, requisitoId, tipoDocumento,
                nombreOriginal, mime, tamanoBytes, ruta, sha256);
    }

    public String fetchRutaDocumento(UUID docId, UUID postulacionId) {
        return jdbc.queryForObject(
                "SELECT ruta_minio FROM portal.documentos WHERE id = ? AND postulacion_id = ? AND is_active = TRUE",
                String.class, docId, postulacionId);
    }

    @Transactional
    public void softDeleteDocumento(UUID docId) {
        jdbc.update("UPDATE portal.documentos SET is_active = FALSE, usuario_modificacion = 'portal-usuario' WHERE id = ?", docId);
    }

    public List<Map<String, Object>> fetchDocumentoUrl(UUID docId, UUID postulacionId, UUID uid) {
        return jdbc.queryForList("""
            SELECT d.ruta_minio, d.nombre_original
            FROM portal.documentos d
            JOIN portal.postulaciones po ON po.id = d.postulacion_id
            WHERE d.id = ? AND d.postulacion_id = ? AND po.usuario_id = ? AND d.is_active = TRUE
            """, docId, postulacionId, uid);
    }

    public List<Map<String, Object>> fetchPostulacionParaEnvio(UUID postulacionId, UUID uid) {
        return jdbc.queryForList("""
            SELECT po.estado, po.folio, po.convocatoria_id,
                   u.email, u.nombre_completo,
                   c.titulo AS convocatoria_titulo, c.tipo AS convocatoria_tipo
            FROM portal.postulaciones po
            JOIN portal.usuarios u ON u.id = po.usuario_id
            JOIN portal.convocatorias c ON c.id = po.convocatoria_id
            WHERE po.id = ? AND po.usuario_id = ? AND po.is_active = TRUE
            """, postulacionId, uid);
    }

    public List<Map<String, Object>> fetchDocumentosObligatoriosFaltantes(UUID convocatoriaId, UUID postulacionId) {
        return jdbc.queryForList("""
            SELECT rd.nombre
            FROM portal.requisitos_documentos rd
            WHERE rd.convocatoria_id = ?
              AND rd.es_obligatorio = TRUE AND rd.is_active = TRUE
              AND NOT EXISTS (
                SELECT 1 FROM portal.documentos d
                WHERE d.postulacion_id = ? AND d.requisito_id = rd.id AND d.is_active = TRUE
              )
            """, convocatoriaId, postulacionId);
    }

    /**
     * @Transactional aquí Y en PortalUsuarioController.enviarPostulacion (que la
     * llama junto con incrementarCupo): el nivel de método cubre invocaciones
     * futuras independientes; el nivel de controller garantiza que ambas
     * llamadas de ese endpoint sean atómicas entre sí.
     */
    @Transactional
    public void marcarPostulacionEnviada(UUID postulacionId) {
        jdbc.update("""
            UPDATE portal.postulaciones
            SET estado = 'ENVIADA', fecha_envio = NOW(), usuario_modificacion = 'portal-usuario'
            WHERE id = ?
            """, postulacionId);
    }

    @Transactional
    public void incrementarCupo(Object convocatoriaId) {
        jdbc.update("UPDATE portal.convocatorias SET cupo_actual = cupo_actual + 1 WHERE id = ?", convocatoriaId);
    }
}
