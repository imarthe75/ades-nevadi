package mx.ades.modules.portal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class PortalAdminService {

    private final JdbcTemplate jdbc;

    public PortalAdminService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> listarConvocatorias(String categoria, String tipo,
            UUID plantelId, int limit, int skip) {
        StringBuilder sql = new StringBuilder("""
            SELECT c.id, c.categoria, c.tipo, c.titulo,
                   c.fecha_inicio_postulacion, c.fecha_cierre_postulacion,
                   c.cupo_maximo, c.cupo_actual, c.is_published, c.is_active,
                   p.nombre_plantel,
                   (SELECT COUNT(*) FROM portal.postulaciones po WHERE po.convocatoria_id = c.id AND po.is_active = TRUE) AS total_postulaciones,
                   (SELECT COUNT(*) FROM portal.postulaciones po WHERE po.convocatoria_id = c.id AND po.estado = 'ENVIADA' AND po.is_active = TRUE) AS pendientes_revision
            FROM portal.convocatorias c
            LEFT JOIN ades_planteles p ON p.id = c.plantel_id
            WHERE c.is_active = TRUE
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
            sql.append("AND c.plantel_id = ? ");
            params.add(plantelId);
        }
        sql.append("ORDER BY c.fecha_creacion DESC LIMIT ? OFFSET ?");
        params.add(Math.min(limit, 100));
        params.add(skip);
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public String inferirCategoria(String tipo) {
        return jdbc.queryForObject(
            "SELECT portal.inferir_categoria(?::portal.tipo_convocatoria)::TEXT", String.class, tipo);
    }

    public UUID crearConvocatoria(String categoria, String tipo, String titulo, String descripcion,
                                   String requisitosGenerales, UUID plantelId, UUID nivelEducativoId,
                                   String fechaInicio, String fechaCierre, Integer cupoMaximo,
                                   String imagenUrl, String avisoVersion, String usuario) {
        return jdbc.queryForObject("""
            INSERT INTO portal.convocatorias
              (categoria, tipo, titulo, descripcion, requisitos_generales, plantel_id, nivel_educativo_id,
               fecha_inicio_postulacion, fecha_cierre_postulacion,
               cupo_maximo, imagen_url, aviso_privacidad_version,
               is_published, is_active, usuario_creacion)
            VALUES (?::portal.categoria_convocatoria,?::portal.tipo_convocatoria,?,?,?,?,?,
                    ?::TIMESTAMPTZ,?::TIMESTAMPTZ,
                    ?,?,?,
                    FALSE, TRUE, ?)
            RETURNING id
            """, UUID.class,
                categoria, tipo, titulo, descripcion, requisitosGenerales,
                plantelId, nivelEducativoId, fechaInicio, fechaCierre,
                cupoMaximo, imagenUrl, avisoVersion, usuario);
    }

    public int actualizarConvocatoria(UUID id, String categoria, String tipo, String titulo,
                                       String descripcion, String requisitosGenerales,
                                       UUID plantelId, UUID nivelEducativoId,
                                       String fechaInicio, String fechaCierre,
                                       Integer cupoMaximo, String imagenUrl, String usuario) {
        return jdbc.update("""
            UPDATE portal.convocatorias SET
              categoria = ?::portal.categoria_convocatoria,
              tipo = ?::portal.tipo_convocatoria, titulo = ?, descripcion = ?,
              requisitos_generales = ?, plantel_id = ?, nivel_educativo_id = ?,
              fecha_inicio_postulacion = ?::TIMESTAMPTZ, fecha_cierre_postulacion = ?::TIMESTAMPTZ,
              cupo_maximo = ?, imagen_url = ?, usuario_modificacion = ?
            WHERE id = ? AND is_active = TRUE
            """,
                categoria, tipo, titulo, descripcion, requisitosGenerales,
                plantelId, nivelEducativoId, fechaInicio, fechaCierre,
                cupoMaximo, imagenUrl, usuario, id);
    }

    public void desactivarRequisitos(UUID convId) {
        jdbc.update("UPDATE portal.requisitos_documentos SET is_active = FALSE WHERE convocatoria_id = ?", convId);
    }

    public int togglePublicar(UUID id, String usuario) {
        return jdbc.update("""
            UPDATE portal.convocatorias
            SET is_published = NOT is_published, fecha_publicacion = CASE WHEN NOT is_published THEN NOW() ELSE fecha_publicacion END,
                usuario_modificacion = ?
            WHERE id = ? AND is_active = TRUE
            RETURNING is_published
            """, usuario, id);
    }

    public void actualizarImagenUrl(UUID id, String url, String usuario) {
        jdbc.update("UPDATE portal.convocatorias SET imagen_url = ?, usuario_modificacion = ? WHERE id = ?",
                url, usuario, id);
    }

    public void eliminarConvocatoria(UUID id, String usuario) {
        jdbc.update("UPDATE portal.convocatorias SET is_active = FALSE, usuario_modificacion = ? WHERE id = ?",
                usuario, id);
    }

    public void verifyConvocatoriaExists(UUID convId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM portal.convocatorias WHERE id = ? AND is_active = TRUE",
                Integer.class, convId);
        if (count == null || count == 0)
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Convocatoria no encontrada");
    }

    public void insertarRequisitos(UUID convId, List<PortalAdminController.RequisitoRequest> reqs, String usuario) {
        for (PortalAdminController.RequisitoRequest r : reqs) {
            String mimes = r.getTiposMimePermitidos() != null
                    ? "{" + String.join(",", r.getTiposMimePermitidos()) + "}"
                    : "{application/pdf}";
            jdbc.update("""
                INSERT INTO portal.requisitos_documentos
                  (convocatoria_id, nombre, descripcion, es_obligatorio,
                   tipos_mime_permitidos, tamano_maximo_mb, orden, usuario_creacion)
                VALUES (?,?,?,?,?::TEXT[],?,?,?)
                """,
                    convId, r.getNombre(), r.getDescripcion(), r.isEsObligatorio(),
                    mimes, r.getTamanoMaximoMb() != null ? r.getTamanoMaximoMb() : 5,
                    r.getOrden() != null ? r.getOrden() : 0, usuario);
        }
    }

    public List<Map<String, Object>> listarPostulaciones(UUID convocatoriaId, String estado,
            String q, int limit, int skip) {
        StringBuilder sql = new StringBuilder("""
            SELECT po.id, po.folio, po.estado, po.fecha_envio, po.fecha_creacion,
                   u.nombre_completo, u.email, u.telefono,
                   c.titulo AS convocatoria_titulo, c.tipo AS convocatoria_tipo,
                   (SELECT COUNT(*) FROM portal.documentos d WHERE d.postulacion_id = po.id AND d.is_active = TRUE) AS total_documentos
            FROM portal.postulaciones po
            JOIN portal.usuarios u ON u.id = po.usuario_id
            JOIN portal.convocatorias c ON c.id = po.convocatoria_id
            WHERE po.is_active = TRUE
            """);
        List<Object> params = new ArrayList<>();
        if (convocatoriaId != null) { sql.append("AND po.convocatoria_id = ? "); params.add(convocatoriaId); }
        if (estado != null && !estado.isBlank()) { sql.append("AND po.estado = ?::portal.estado_postulacion "); params.add(estado); }
        if (q != null && !q.isBlank()) {
            sql.append("AND (u.nombre_completo ILIKE ? OR u.email ILIKE ? OR po.folio ILIKE ?) ");
            String like = "%" + q + "%";
            params.add(like); params.add(like); params.add(like);
        }
        sql.append("ORDER BY po.fecha_creacion DESC LIMIT ? OFFSET ?");
        params.add(Math.min(limit, 200)); params.add(skip);
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> detallePostulacion(UUID id) {
        return jdbc.queryForList("""
            SELECT po.id, po.folio, po.estado, po.datos_adicionales,
                   po.consentimiento_privacidad, po.ip_postulacion, po.fecha_envio,
                   po.fecha_creacion, po.observaciones_admin,
                   u.nombre_completo, u.email, u.telefono, u.fecha_nacimiento,
                   c.titulo AS convocatoria_titulo, c.tipo AS convocatoria_tipo
            FROM portal.postulaciones po
            JOIN portal.usuarios u ON u.id = po.usuario_id
            JOIN portal.convocatorias c ON c.id = po.convocatoria_id
            WHERE po.id = ?
            """, id);
    }

    public List<Map<String, Object>> documentosPostulacion(UUID postulacionId) {
        return jdbc.queryForList("""
            SELECT d.id, d.tipo_documento, d.nombre_original, d.mimetype,
                   d.tamano_bytes, d.ruta_minio, d.fecha_creacion,
                   rd.nombre AS requisito_nombre, rd.es_obligatorio
            FROM portal.documentos d
            LEFT JOIN portal.requisitos_documentos rd ON rd.id = d.requisito_id
            WHERE d.postulacion_id = ? AND d.is_active = TRUE
            ORDER BY d.fecha_creacion
            """, postulacionId);
    }

    public List<Map<String, Object>> fetchPostulacionParaCambioEstado(UUID id) {
        return jdbc.queryForList("""
            SELECT po.folio, po.estado, u.email, u.nombre_completo
            FROM portal.postulaciones po JOIN portal.usuarios u ON u.id = po.usuario_id
            WHERE po.id = ? AND po.is_active = TRUE
            """, id);
    }

    public void cambiarEstadoPostulacion(UUID id, String estado, String observaciones, String usuario) {
        jdbc.update("""
            UPDATE portal.postulaciones
            SET estado = ?::portal.estado_postulacion,
                observaciones_admin = COALESCE(?, observaciones_admin),
                usuario_modificacion = ?
            WHERE id = ?
            """, estado.toUpperCase(), observaciones, usuario, id);
    }

    public List<Map<String, Object>> exportarPostulaciones(UUID convocatoriaId) {
        return jdbc.queryForList("""
            SELECT po.folio, po.estado, po.fecha_envio,
                   u.nombre_completo, u.email, u.telefono,
                   c.titulo AS convocatoria, c.tipo
            FROM portal.postulaciones po
            JOIN portal.usuarios u ON u.id = po.usuario_id
            JOIN portal.convocatorias c ON c.id = po.convocatoria_id
            WHERE po.is_active = TRUE
              AND (? IS NULL OR po.convocatoria_id = ?)
            ORDER BY c.titulo, po.estado, u.nombre_completo
            """, convocatoriaId, convocatoriaId);
    }

    public List<Map<String, Object>> listarArco(String estado) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, email_solicitante, nombre_solicitante, tipo, estado,
                   descripcion, fecha_limite_respuesta, fecha_resolucion, fecha_creacion
            FROM portal.solicitudes_arco
            """);
        List<Object> params = new ArrayList<>();
        if (estado != null && !estado.isBlank()) {
            sql.append("WHERE estado = ?::portal.estado_solicitud_arco ");
            params.add(estado);
        }
        sql.append("ORDER BY fecha_creacion DESC");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public void responderArco(UUID id, String estado, String respuesta, String usuario) {
        jdbc.update("""
            UPDATE portal.solicitudes_arco
            SET estado = ?::portal.estado_solicitud_arco,
                respuesta_admin = ?,
                fecha_resolucion = CASE WHEN ? IN ('RESUELTA','IMPROCEDENTE') THEN NOW() ELSE fecha_resolucion END,
                usuario_modificacion = ?
            WHERE id = ?
            """, estado, respuesta, estado, usuario, id);
    }

    public Map<String, Object> estadisticas() {
        return Map.of(
            "convocatorias_activas", jdbc.queryForObject(
                "SELECT COUNT(*) FROM portal.convocatorias WHERE is_published=TRUE AND is_active=TRUE AND fecha_cierre_postulacion >= NOW()", Long.class),
            "total_postulaciones", jdbc.queryForObject(
                "SELECT COUNT(*) FROM portal.postulaciones WHERE is_active=TRUE", Long.class),
            "pendientes_revision", jdbc.queryForObject(
                "SELECT COUNT(*) FROM portal.postulaciones WHERE estado='ENVIADA' AND is_active=TRUE", Long.class),
            "usuarios_registrados", jdbc.queryForObject(
                "SELECT COUNT(*) FROM portal.usuarios WHERE is_active=TRUE", Long.class),
            "solicitudes_arco_pendientes", jdbc.queryForObject(
                "SELECT COUNT(*) FROM portal.solicitudes_arco WHERE estado IN ('RECIBIDA','EN_PROCESO')", Long.class),
            "por_categoria", jdbc.queryForList("""
                SELECT c.categoria, COUNT(DISTINCT c.id) AS convocatorias,
                       COUNT(po.id) AS postulaciones
                FROM portal.convocatorias c
                LEFT JOIN portal.postulaciones po ON po.convocatoria_id = c.id AND po.is_active = TRUE
                WHERE c.is_active = TRUE
                GROUP BY c.categoria ORDER BY c.categoria
                """),
            "por_tipo", jdbc.queryForList("""
                SELECT c.categoria, c.tipo, COUNT(po.id) AS postulaciones
                FROM portal.convocatorias c
                LEFT JOIN portal.postulaciones po ON po.convocatoria_id = c.id AND po.is_active = TRUE
                WHERE c.is_active = TRUE
                GROUP BY c.categoria, c.tipo ORDER BY c.categoria, postulaciones DESC
                """)
        );
    }

    public List<Map<String, Object>> listarSecciones(UUID convId) {
        return jdbc.queryForList("""
            SELECT id, tipo_seccion, titulo, contenido, datos, orden, is_active,
                   fecha_creacion, fecha_modificacion, usuario_creacion
            FROM portal.secciones_convocatoria
            WHERE convocatoria_id = ? AND is_active = TRUE
            ORDER BY orden, fecha_creacion
            """, convId);
    }

    public UUID crearSeccion(UUID convId, String tipoSeccion, String titulo,
                              String contenido, String datosJson, Integer orden, String usuario) {
        return jdbc.queryForObject("""
            INSERT INTO portal.secciones_convocatoria
              (convocatoria_id, tipo_seccion, titulo, contenido, datos, orden, usuario_creacion)
            VALUES (?, ?::portal.tipo_seccion, ?, ?, ?::JSONB,
                    COALESCE(?, (SELECT COALESCE(MAX(orden),0)+1 FROM portal.secciones_convocatoria
                                  WHERE convocatoria_id = ?)), ?)
            RETURNING id
            """, UUID.class,
                convId, tipoSeccion.toUpperCase(), titulo, contenido, datosJson,
                orden, convId, usuario);
    }

    public int actualizarSeccion(UUID seccionId, UUID convId, String tipoSeccion, String titulo,
                                  String contenido, String datosJson, Integer orden, String usuario) {
        return jdbc.update("""
            UPDATE portal.secciones_convocatoria
            SET tipo_seccion = ?::portal.tipo_seccion,
                titulo = ?,
                contenido = ?,
                datos = ?::JSONB,
                orden = COALESCE(?, orden),
                usuario_modificacion = ?
            WHERE id = ? AND convocatoria_id = ? AND is_active = TRUE
            """,
                tipoSeccion.toUpperCase(), titulo, contenido, datosJson,
                orden, usuario, seccionId, convId);
    }

    public void reordenarSecciones(List<UUID> ids, UUID convId, String usuario) {
        for (int i = 0; i < ids.size(); i++) {
            jdbc.update("""
                UPDATE portal.secciones_convocatoria
                SET orden = ?, usuario_modificacion = ?
                WHERE id = ? AND convocatoria_id = ?
                """, i, usuario, ids.get(i), convId);
        }
    }

    public void eliminarSeccion(UUID seccionId, UUID convId, String usuario) {
        jdbc.update("""
            UPDATE portal.secciones_convocatoria
            SET is_active = FALSE, usuario_modificacion = ?
            WHERE id = ? AND convocatoria_id = ?
            """, usuario, seccionId, convId);
    }
}
