package mx.ades.modules.expediente.query;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

@Service
public class ExpedienteQueryService {

    private static final List<String> TIPOS_REQUERIDOS = List.of(
            "CURP", "ACTA_NACIMIENTO", "CERTIFICADO_PREV", "COMPROBANTE_DOMICILIO", "FOTOGRAFIA");

    private static final Map<String, String> LABEL_TIPO = Map.of(
            "CURP", "CURP",
            "ACTA_NACIMIENTO", "Acta de Nacimiento",
            "CERTIFICADO_PREV", "Certificado de Nivel Previo",
            "COMPROBANTE_DOMICILIO", "Comprobante de Domicilio",
            "FOTOGRAFIA", "Fotografía",
            "NSS", "Número de Seguro Social",
            "CREDENCIAL_ESCOLAR", "Credencial Escolar",
            "CONSTANCIA_INSCRIPCION", "Constancia de Inscripción",
            "OTRO", "Otro");

    private final JdbcTemplate jdbc;

    public ExpedienteQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID cicloActivoId() {
        List<UUID> ids = jdbc.queryForList(
            "SELECT id FROM public.ades_ciclos_escolares WHERE es_vigente = TRUE ORDER BY fecha_inicio DESC LIMIT 1",
            UUID.class);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay ciclo escolar activo configurado.");
        }
        return ids.get(0);
    }

    public Map<String, Object> obtenerOCrearExpediente(UUID estudianteId, UUID cicloId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, estado, completitud_pct, revisado_por, fecha_revision, observaciones, ciclo_escolar_id " +
            "FROM public.ades_expedientes_alumno WHERE estudiante_id = ? AND ciclo_escolar_id = ?",
            estudianteId, cicloId);
        if (!rows.isEmpty()) return rows.get(0);

        UUID newId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO public.ades_expedientes_alumno (id, estudiante_id, ciclo_escolar_id, estado, completitud_pct, is_active) " +
            "VALUES (?, ?, ?, 'PENDIENTE', 0.00, TRUE)",
            newId, estudianteId, cicloId);
        return jdbc.queryForMap(
            "SELECT id, estado, completitud_pct, revisado_por, fecha_revision, observaciones, ciclo_escolar_id " +
            "FROM public.ades_expedientes_alumno WHERE id = ?", newId);
    }

    public Map<String, Object> detalleExpediente(UUID estudianteId, UUID cicloId) {
        Map<String, Object> exp = obtenerOCrearExpediente(estudianteId, cicloId);
        UUID expId = (UUID) exp.get("id");

        List<Map<String, Object>> docsRaw = jdbc.queryForList(
            "SELECT id, expediente_id, paperless_doc_id, tipo_documento, nombre_archivo, estado_ocr, fecha_carga, metadatos_ia " +
            "FROM public.ades_expediente_documentos WHERE expediente_id = ? AND is_active = TRUE ORDER BY fecha_carga DESC",
            expId);

        List<Map<String, Object>> documentos = new ArrayList<>();
        Set<String> tiposPresentes = new HashSet<>();
        for (Map<String, Object> d : docsRaw) {
            String tipo = (String) d.get("tipo_documento");
            tiposPresentes.add(tipo);
            Map<String, Object> docOut = new HashMap<>(d);
            docOut.put("tipo_label", LABEL_TIPO.getOrDefault(tipo, tipo));
            if (d.get("fecha_carga") != null) docOut.put("fecha_carga", d.get("fecha_carga").toString());
            documentos.add(docOut);
        }

        List<Map<String, Object>> docsRequeridos = new ArrayList<>();
        for (String t : TIPOS_REQUERIDOS) {
            docsRequeridos.add(Map.of("tipo", t, "label", LABEL_TIPO.getOrDefault(t, t), "presente", tiposPresentes.contains(t)));
        }

        Map<String, Object> out = new HashMap<>();
        out.put("id", exp.get("id"));
        out.put("estudiante_id", estudianteId);
        out.put("ciclo_escolar_id", exp.get("ciclo_escolar_id"));
        out.put("estado", exp.get("estado"));
        out.put("completitud_pct", exp.get("completitud_pct"));
        out.put("revisado_por", exp.get("revisado_por"));
        out.put("fecha_revision", exp.get("fecha_revision") != null ? exp.get("fecha_revision").toString() : null);
        out.put("observaciones", exp.get("observaciones"));
        out.put("documentos", documentos);
        out.put("documentos_requeridos", docsRequeridos);
        return out;
    }

    public List<Map<String, Object>> listarBajas(UUID estudianteId) {
        return jdbc.queryForList(
            "SELECT * FROM ades_bajas WHERE estudiante_id = ? AND is_active = TRUE ORDER BY fecha_efectiva DESC",
            estudianteId);
    }

    public List<Map<String, Object>> listarExtraordinarios(UUID estudianteId, UUID cicloId) {
        StringBuilder sql = new StringBuilder(
            "SELECT * FROM ades_extraordinarias WHERE estudiante_id = ? AND is_active = TRUE ");
        List<Object> params = new ArrayList<>();
        params.add(estudianteId);
        if (cicloId != null) {
            sql.append("AND ciclo_escolar_id = ? ");
            params.add(cicloId);
        }
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> listarConstancias(UUID estudianteId) {
        return jdbc.queryForList(
            "SELECT * FROM ades_constancias WHERE estudiante_id = ? AND is_active = TRUE ORDER BY fecha_emision DESC",
            estudianteId);
    }

    public Map<String, Object> documentoById(UUID docId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT paperless_doc_id, nombre_archivo FROM public.ades_expediente_documentos WHERE id = ? AND is_active = TRUE",
            docId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento no encontrado.");
        return rows.get(0);
    }

    public Map<String, Object> alumnoParaAnalisis(UUID estudianteId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT p.nombre, p.apellido_paterno, p.apellido_materno, p.curp, " +
            "       e.matricula, n.nombre_nivel as nivel " +
            "FROM public.ades_estudiantes e " +
            "JOIN public.ades_personas p ON p.id = e.persona_id " +
            "LEFT JOIN public.ades_grupos g ON g.id = e.grupo_id " +
            "LEFT JOIN public.ades_grados gr ON gr.id = g.grado_id " +
            "LEFT JOIN public.ades_niveles_educativos n ON n.id = gr.nivel_educativo_id " +
            "WHERE e.id = ? LIMIT 1",
            estudianteId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado.");
        return rows.get(0);
    }

    public List<Map<String, Object>> documentosExpediente(UUID expId) {
        return jdbc.queryForList(
            "SELECT tipo_documento, ocr_texto FROM public.ades_expediente_documentos WHERE expediente_id = ? AND is_active = TRUE",
            expId);
    }

    public List<String> tiposRequeridos() {
        return TIPOS_REQUERIDOS;
    }

    public String labelTipo(String tipo) {
        return LABEL_TIPO.getOrDefault(tipo, tipo);
    }

    public List<Map<String, Object>> fetchExtraordinarioById(UUID id) {
        return jdbc.queryForList("SELECT * FROM ades_extraordinarias WHERE id = ?", id);
    }

    public List<Map<String, Object>> fetchConstanciaById(UUID id) {
        return jdbc.queryForList("SELECT * FROM ades_constancias WHERE id = ?", id);
    }

    public List<Map<String, Object>> fetchDocForDelete(UUID docId, UUID expedienteId) {
        return jdbc.queryForList(
            "SELECT paperless_doc_id FROM public.ades_expediente_documentos " +
            "WHERE id = ? AND expediente_id = ? AND is_active = TRUE",
            docId, expedienteId);
    }
}
