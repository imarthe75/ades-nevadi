package mx.ades.modules.direcciones;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DireccionesQueryService {

    private final JdbcTemplate jdbc;

    static final String SEPOMEX_SELECT =
            "SELECT " +
            "  cp.id AS cp_id, l.id AS localidad_id, cp.codigo_postal, " +
            "  l.nombre_localidad, ta.nombre_tipo AS tipo_asentamiento, " +
            "  m.id AS municipio_id, m.nombre_municipio, " +
            "  e.id AS estado_id, e.nombre_estado, " +
            "  pa.nombre_pais " +
            "FROM ades_codigos_postales cp " +
            "JOIN ades_localidades l ON l.id = cp.localidad_id " +
            "JOIN ades_municipios m ON m.id = cp.municipio_id " +
            "JOIN ades_estados e ON e.id = cp.estado_id " +
            "JOIN ades_paises pa ON pa.id = e.pais_id " +
            "LEFT JOIN ades_tipos_asentamiento ta ON ta.id = l.tipo_asentamiento_id " +
            "WHERE cp.is_active = TRUE ";

    static final String DIR_SELECT =
            "SELECT d.id, d.entidad_tipo, d.entidad_id, d.tipo_direccion, d.es_principal, " +
            "  d.tipo_via, d.calle, d.numero_exterior, d.numero_interior, " +
            "  d.entre_calle_1, d.entre_calle_2, d.referencia, " +
            "  d.codigo_postal_id, d.localidad_id, " +
            "  cp.codigo_postal, l.nombre_localidad, ta.nombre_tipo AS tipo_asentamiento, " +
            "  m.nombre_municipio, e.nombre_estado, pa.nombre_pais, " +
            "  d.latitud, d.longitud, d.precision_gps, d.row_version, d.fecha_creacion " +
            "FROM ades_direcciones d " +
            "LEFT JOIN ades_codigos_postales cp ON cp.id = d.codigo_postal_id " +
            "LEFT JOIN ades_localidades l ON l.id = d.localidad_id " +
            "LEFT JOIN ades_municipios m ON m.id = cp.municipio_id " +
            "LEFT JOIN ades_estados e ON e.id = cp.estado_id " +
            "LEFT JOIN ades_paises pa ON pa.id = e.pais_id " +
            "LEFT JOIN ades_tipos_asentamiento ta ON ta.id = cp.tipo_asentamiento_id ";

    public DireccionesQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> porCp(String cp) {
        return jdbc.queryForList(
                SEPOMEX_SELECT + "AND cp.codigo_postal = ? ORDER BY l.nombre_localidad", cp);
    }

    public List<Map<String, Object>> buscar(String q, int limit) {
        String term = "%" + q.trim() + "%";
        return jdbc.queryForList(
                SEPOMEX_SELECT +
                "AND (l.nombre_localidad ILIKE ? OR cp.codigo_postal ILIKE ? OR m.nombre_municipio ILIKE ?) " +
                "ORDER BY l.nombre_localidad LIMIT ?",
                term, term, term, limit);
    }

    public List<Map<String, Object>> tiposAsentamiento() {
        return jdbc.queryForList(
                "SELECT id, clave_tipo, nombre_tipo FROM ades_tipos_asentamiento " +
                "WHERE is_active = TRUE ORDER BY nombre_tipo");
    }

    public List<Map<String, Object>> estados() {
        return jdbc.queryForList(
                "SELECT e.id, e.clave_estado, e.nombre_estado, pa.nombre_pais " +
                "FROM ades_estados e JOIN ades_paises pa ON pa.id = e.pais_id " +
                "WHERE e.is_active = TRUE ORDER BY e.nombre_estado");
    }

    public List<Map<String, Object>> municipios(UUID estadoId) {
        if (estadoId != null) {
            return jdbc.queryForList(
                    "SELECT id, clave_municipio, nombre_municipio FROM ades_municipios " +
                    "WHERE estado_id = ? AND is_active = TRUE ORDER BY nombre_municipio", estadoId);
        }
        return jdbc.queryForList(
                "SELECT id, clave_municipio, nombre_municipio FROM ades_municipios " +
                "WHERE is_active = TRUE ORDER BY nombre_municipio");
    }

    public List<Map<String, Object>> listarDirecciones(String entidadTipo, UUID entidadId) {
        return jdbc.queryForList(
                DIR_SELECT + "WHERE d.entidad_tipo = ? AND d.entidad_id = ? AND d.is_active = TRUE " +
                "ORDER BY d.es_principal DESC, d.fecha_creacion",
                entidadTipo, entidadId);
    }

    public Map<String, Object> getDirById(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(DIR_SELECT + "WHERE d.id = ?", id);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    public List<Map<String, Object>> fetchDirForUpdate(UUID id) {
        return jdbc.queryForList(
                "SELECT entidad_tipo, entidad_id, row_version FROM ades_direcciones " +
                "WHERE id = ? AND is_active = TRUE", id);
    }

    /** Usado por eliminarDir() (2026-07-16) para resolver entidad_tipo/entidad_id antes de borrar y poder verificar plantel. */
    public List<Map<String, Object>> fetchDirEntidad(UUID id) {
        return jdbc.queryForList(
                "SELECT entidad_tipo, entidad_id FROM ades_direcciones WHERE id = ? AND is_active = TRUE", id);
    }

    public List<Map<String, Object>> fetchDirPrincipalRef(UUID id) {
        return jdbc.queryForList(
                "SELECT entidad_tipo, entidad_id FROM ades_direcciones WHERE id = ? AND is_active = TRUE", id);
    }

    public List<Map<String, Object>> listarContactos(UUID personaId) {
        return jdbc.queryForList(
                "SELECT id, persona_id, medio, tipo, valor, etiqueta, es_principal, " +
                "orden, verificado, notas, row_version " +
                "FROM ades_persona_contactos " +
                "WHERE persona_id = ? AND is_active = TRUE " +
                "ORDER BY es_principal DESC, medio, orden",
                personaId);
    }

    public Map<String, Object> getContactoById(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, persona_id, medio, tipo, valor, etiqueta, es_principal, " +
                "orden, verificado, notas, row_version " +
                "FROM ades_persona_contactos WHERE id = ?", id);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    public List<Map<String, Object>> fetchContactoForUpdate(UUID id) {
        return jdbc.queryForList(
                "SELECT persona_id, row_version FROM ades_persona_contactos WHERE id = ? AND is_active = TRUE", id);
    }
}
