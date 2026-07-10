package mx.ades.modules.procesos.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ProcesosQueryService {

    private final JdbcTemplate jdbc;

    public ProcesosQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> listarAdmisiones(UUID plantelId, String estado, UUID cicloId,
                                                       int skip, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, nombre, apellido_paterno, apellido_materno, curp, " +
                "nivel_solicitado, grado_solicitado, estado, " +
                "nombre_tutor, email_tutor, fecha_solicitud, " +
                "promedio_procedencia, escuela_procedencia " +
                "FROM ades_solicitudes_admision WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (plantelId != null) { sql.append("AND plantel_id = ? "); params.add(plantelId); }
        if (estado != null && !estado.isBlank()) { sql.append("AND estado = ? "); params.add(estado); }
        if (cicloId != null) { sql.append("AND ciclo_escolar_id = ? "); params.add(cicloId); }

        sql.append("ORDER BY fecha_solicitud DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(skip);

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> listarDocumentosAdmision(UUID admisionId) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, admision_id, tipo_documento, nombre_archivo, url_documento, " +
                "estado_validacion, observaciones, fecha_creacion " +
                "FROM ades_documentos_admision WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (admisionId != null) { sql.append("AND admision_id = ? "); params.add(admisionId); }
        sql.append("ORDER BY tipo_documento");

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> listaEspera(UUID plantelId, String nivelSolicitado, int skip, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, nombre, apellido_paterno, nivel_solicitado, grado_solicitado, " +
                "nombre_tutor, telefono_tutor, email_tutor, fecha_solicitud " +
                "FROM ades_solicitudes_admision WHERE estado = 'LISTA_ESPERA' ");
        List<Object> params = new ArrayList<>();

        if (plantelId != null) { sql.append("AND plantel_id = ? "); params.add(plantelId); }
        if (nivelSolicitado != null && !nivelSolicitado.isBlank()) {
            sql.append("AND nivel_solicitado = ? "); params.add(nivelSolicitado);
        }
        sql.append("ORDER BY fecha_solicitud ASC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(skip);

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> listarOptativas(UUID cicloId, UUID estudianteId) {
        StringBuilder sql = new StringBuilder(
                "SELECT io.id, io.fecha_inscripcion, m.nombre_materia, m.clave_materia, " +
                "p.nombre || ' ' || p.apellido_paterno AS alumno, e.matricula " +
                "FROM ades_inscripciones_optativas io " +
                "JOIN ades_materias m ON m.id = io.materia_id " +
                "JOIN ades_estudiantes e ON e.id = io.estudiante_id " +
                "JOIN ades_personas p ON p.id = e.persona_id " +
                "WHERE io.is_active = TRUE ");
        List<Object> params = new ArrayList<>();

        if (cicloId != null) { sql.append("AND io.ciclo_escolar_id = ? "); params.add(cicloId); }
        if (estudianteId != null) { sql.append("AND io.estudiante_id = ? "); params.add(estudianteId); }
        sql.append("ORDER BY m.nombre_materia");

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> listarCalendarios(UUID cicloId, String nivelEducativo, String tipo) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, nombre, tipo, nivel_educativo, fecha_inicio, fecha_fin, " +
                "descripcion, es_oficial, fecha_creacion " +
                "FROM ades_calendarios_academicos WHERE is_active = TRUE ");
        List<Object> params = new ArrayList<>();

        if (cicloId != null) { sql.append("AND ciclo_escolar_id = ? "); params.add(cicloId); }
        if (nivelEducativo != null && !nivelEducativo.isBlank()) {
            sql.append("AND nivel_educativo = ? "); params.add(nivelEducativo);
        }
        if (tipo != null && !tipo.isBlank()) { sql.append("AND tipo = ? "); params.add(tipo); }
        sql.append("ORDER BY fecha_inicio");

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> calendarioActividades(UUID cicloId, UUID plantelId,
                                                            UUID userPlantelId, Integer mes) {
        StringBuilder sql = new StringBuilder(
                "SELECT ca.id, ca.nombre, ca.tipo, ca.nivel_educativo, " +
                "ca.fecha_inicio, ca.fecha_fin, ca.descripcion, ca.es_oficial, " +
                "pl.nombre_plantel " +
                "FROM ades_calendarios_academicos ca " +
                "LEFT JOIN ades_planteles pl ON pl.id = ca.plantel_id " +
                "WHERE ca.is_active = TRUE ");
        List<Object> params = new ArrayList<>();

        if (cicloId != null) { sql.append("AND ca.ciclo_escolar_id = ? "); params.add(cicloId); }

        UUID filtroP = plantelId != null ? plantelId : userPlantelId;
        if (filtroP != null) {
            sql.append("AND (ca.plantel_id = ? OR ca.plantel_id IS NULL) ");
            params.add(filtroP);
        }
        if (mes != null) { sql.append("AND EXTRACT(MONTH FROM ca.fecha_inicio) = ? "); params.add(mes); }

        sql.append("ORDER BY ca.fecha_inicio");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    // ── Reads extracted from ProcesosEscolaresController ─────────────────────

    public List<UUID> fetchCicloPorVigente() {
        return jdbc.queryForList(
                "SELECT id FROM public.ades_ciclos_escolares WHERE es_vigente = TRUE ORDER BY fecha_inicio DESC LIMIT 1",
                UUID.class);
    }

    public List<UUID> fetchExpedienteId(UUID estudianteId, UUID cicloId) {
        return jdbc.queryForList(
                "SELECT id FROM public.ades_expedientes_alumno WHERE estudiante_id = ? AND ciclo_escolar_id = ? AND is_active = TRUE",
                UUID.class, estudianteId, cicloId);
    }

    public List<Map<String, Object>> fetchDocumentosExpediente(UUID expedienteId) {
        return jdbc.queryForList(
                "SELECT paperless_doc_id, nombre_archivo, tipo_documento FROM public.ades_expediente_documentos " +
                "WHERE expediente_id = ? AND is_active = TRUE AND paperless_doc_id IS NOT NULL",
                expedienteId);
    }

    public List<Map<String, Object>> fetchSolicitudEstado(UUID id) {
        return jdbc.queryForList(
                "SELECT estado FROM ades_solicitudes_admision WHERE id = ?", id);
    }

    public List<Map<String, Object>> fetchSolicitudParaCarta(UUID id) {
        return jdbc.queryForList(
                "SELECT nombre, apellido_paterno, apellido_materno, curp, estado, " +
                "nivel_solicitado, grado_solicitado, nombre_tutor, fecha_solicitud " +
                "FROM ades_solicitudes_admision WHERE id = ?", id);
    }

    /** PE-006: timeline de cambios de estado de una solicitud de admisión. */
    public List<Map<String, Object>> fetchHistorialAdmision(UUID id, int skip, int limit) {
        return jdbc.queryForList(
                "SELECT id, estado_anterior, estado_nuevo, usuario, fecha " +
                "FROM ades_admision_historial_estados WHERE solicitud_id = ? " +
                "ORDER BY fecha ASC LIMIT ? OFFSET ?", id, limit, skip);
    }

    public List<Map<String, Object>> fetchSolicitudListaEspera(UUID id) {
        return jdbc.queryForList(
                "SELECT email_tutor, nombre FROM ades_solicitudes_admision WHERE id = ? AND estado='LISTA_ESPERA'", id);
    }

    public boolean checkMateriaExists(UUID materiaId) {
        return !jdbc.queryForList(
                "SELECT tipo_materia FROM ades_materias WHERE id = ? AND is_active = TRUE", materiaId).isEmpty();
    }

    public boolean checkDupOptativa(UUID estudianteId, UUID materiaId, UUID cicloId) {
        return !jdbc.queryForList(
                "SELECT id FROM ades_inscripciones_optativas " +
                "WHERE estudiante_id = ? AND materia_id = ? AND ciclo_escolar_id = ? AND is_active = TRUE",
                estudianteId, materiaId, cicloId).isEmpty();
    }

    public List<Map<String, Object>> listarBajas(int skip, int limit) {
        return jdbc.queryForList(
                "SELECT b.id, b.estudiante_id, b.tipo_baja, b.motivo, b.fecha_efectiva, " +
                "p.nombre || ' ' || p.apellido_paterno AS alumno, g.nombre_grupo AS grupo " +
                "FROM ades_bajas b " +
                "JOIN ades_estudiantes e ON e.id = b.estudiante_id " +
                "JOIN ades_personas p ON p.id = e.persona_id " +
                "LEFT JOIN ades_inscripciones i ON i.id = b.inscripcion_id " +
                "LEFT JOIN ades_grupos g ON g.id = i.grupo_id " +
                "ORDER BY b.fecha_creacion DESC LIMIT ? OFFSET ?", limit, skip);
    }

    public List<Map<String, Object>> fetchBajaParaReactivar(UUID bajaId) {
        return jdbc.queryForList(
                "SELECT estudiante_id, inscripcion_id FROM ades_bajas WHERE id = ?", bajaId);
    }

    public List<Map<String, Object>> fetchInscripcionGrupo(UUID inscripcionId) {
        return jdbc.queryForList(
                "SELECT grupo_id FROM ades_inscripciones WHERE id = ?", inscripcionId);
    }

    public List<Map<String, Object>> fetchCapacidadGrupo(UUID grupoId) {
        return jdbc.queryForList(
                "SELECT capacidad_maxima, " +
                "(SELECT COUNT(*) FROM ades_inscripciones WHERE grupo_id = ? AND is_active = TRUE) AS inscritos " +
                "FROM ades_grupos WHERE id = ?", grupoId, grupoId);
    }
}
