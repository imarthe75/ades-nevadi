package mx.ades.modules.contactos.query;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Servicio de lectura CQRS para el módulo contactos.
 * <p>Expone listado de contactos familiares de un alumno y la matriz de documentos del expediente
 * escolar con su estado (PENDIENTE / ENTREGADO / RECHAZADO).</p>
 *
 * @author ADES
 * @since 2026
 */
@Service
@RequiredArgsConstructor
public class ContactosQueryService {

    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> listarContactos(UUID estudianteId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT c.id, c.persona_id, c.estudiante_id, c.nombre_completo, c.parentesco, " +
            "c.telefono_principal, c.email, c.es_tutor_legal, c.es_contacto_emergencia, " +
            "c.puede_recoger, c.ocupacion, c.nivel_estudios, c.rfc, c.nacionalidad, " +
            "c.toma_decision_conjunta, c.grado_responsabilidad, c.is_active, c.row_version, " +
            "p.nombre || ' ' || p.apellido_paterno || COALESCE(' ' || p.apellido_materno, '') AS nombre_completo_persona " +
            "FROM ades_contactos_familiares c " +
            "LEFT JOIN ades_personas p ON p.id = c.persona_id " +
            "WHERE c.estudiante_id = ? AND c.is_active = TRUE " +
            "ORDER BY c.es_tutor_legal DESC, c.prioridad",
            estudianteId
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> c : rows) {
            Map<String, Object> map = new HashMap<>(c);
            String nombre = (String) c.get("nombre_completo");
            if ((nombre == null || nombre.isBlank()) && c.get("nombre_completo_persona") != null) {
                nombre = (String) c.get("nombre_completo_persona");
            }
            map.put("nombre_completo", nombre != null ? nombre : "");
            result.add(map);
        }
        return result;
    }

    public boolean existeEmailContacto(String email) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ades_contactos_familiares WHERE email = ? AND is_active = TRUE",
            Integer.class, email);
        return count != null && count > 0;
    }

    public boolean existeEmailContactoExcepto(String email, UUID contactoId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ades_contactos_familiares WHERE email = ? AND id != ? AND is_active = TRUE",
            Integer.class, email, contactoId);
        return count != null && count > 0;
    }

    public List<Map<String, Object>> listarExpedienteDocs(UUID estudianteId, UUID cicloId) {
        List<Map<String, Object>> tipos = jdbc.queryForList(
            "SELECT id, nombre_documento, descripcion, obligatorio FROM ades_documentos_tipo WHERE is_active = TRUE ORDER BY orden");

        String docsSql = "SELECT documento_tipo_id, estatus, fecha_entrega, observaciones, id AS doc_id " +
            "FROM ades_expediente_docs WHERE estudiante_id = ? AND is_active = TRUE";
        List<Map<String, Object>> docsList;
        if (cicloId != null) {
            docsList = jdbc.queryForList(docsSql + " AND ciclo_escolar_id = ?", estudianteId, cicloId);
        } else {
            docsList = jdbc.queryForList(docsSql, estudianteId);
        }

        Map<UUID, Map<String, Object>> docsMap = new HashMap<>();
        for (Map<String, Object> doc : docsList) {
            docsMap.put((UUID) doc.get("documento_tipo_id"), doc);
        }

        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Map<String, Object> tipo : tipos) {
            UUID tipoId = (UUID) tipo.get("id");
            Map<String, Object> doc = docsMap.get(tipoId);
            Map<String, Object> r = new HashMap<>();
            r.put("documento_tipo_id", tipoId);
            r.put("nombre_documento", tipo.get("nombre_documento"));
            r.put("descripcion", tipo.get("descripcion"));
            r.put("obligatorio", tipo.get("obligatorio"));
            r.put("estatus", doc != null ? doc.get("estatus") : "PENDIENTE");
            r.put("fecha_entrega", doc != null && doc.get("fecha_entrega") != null ? doc.get("fecha_entrega").toString() : null);
            r.put("observaciones", doc != null ? doc.get("observaciones") : null);
            r.put("doc_id", doc != null ? doc.get("doc_id") : null);
            resultado.add(r);
        }
        return resultado;
    }
}
