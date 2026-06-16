package mx.ades.modules.portal_familias.infrastructure.outbound.persistence;

import mx.ades.modules.portal_familias.domain.port.in.AgregarTutorUseCase;
import mx.ades.modules.portal_familias.domain.port.out.PortalFamiliasRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class PortalFamiliasPersistenceAdapter implements PortalFamiliasRepositoryPort {

    private final JdbcTemplate jdbc;

    public PortalFamiliasPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existeAlumno(UUID alumnoId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id FROM ades_estudiantes WHERE id = ? AND is_active = TRUE", alumnoId);
        return !rows.isEmpty();
    }

    @Override
    public Map<String, Object> insertTutor(AgregarTutorUseCase.Command cmd) {
        UUID newId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ades_tutores_alumnos " +
            "(id, alumno_id, persona_id, relacion, prioridad, puede_recoger, " +
            " es_responsable_economico, es_contacto_emergencia, nivel_acceso_portal, " +
            " usuario_creacion, usuario_modificacion) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            newId, cmd.alumnoId(), cmd.personaId(), cmd.relacion(), cmd.prioridad(),
            cmd.puedeRecoger(), cmd.esResponsableEconomico(), cmd.esContactoEmergencia(),
            cmd.nivelAccesoPortal(), cmd.usuarioCreacion(), cmd.usuarioCreacion()
        );
        return Map.of("id", newId.toString(), "message", "Tutor vinculado al alumno");
    }

    @Override
    public void desvincularTutor(UUID tutorAlumnoId, String usuarioMod) {
        jdbc.update(
            "UPDATE ades_tutores_alumnos SET is_active = FALSE, usuario_modificacion = ? WHERE id = ?",
            usuarioMod, tutorAlumnoId);
    }

    @Override
    public boolean existeTutorAlumno(UUID tutorAlumnoId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id FROM ades_tutores_alumnos WHERE id = ? AND is_active = TRUE", tutorAlumnoId);
        return !rows.isEmpty();
    }

    @Override
    public UUID enqueueCrearUsuario(UUID tutorAlumnoId, String email, String nombreCompleto, String usuarioId) {
        String payload = String.format("{\"tutor_alumno_id\": \"%s\", \"email\": \"%s\", \"nombre\": \"%s\"}",
                tutorAlumnoId, email, nombreCompleto);
        UUID newTaskId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ades_tareas_sistema " +
            "(id, tipo_tarea, payload_json, estado, usuario_creacion, usuario_modificacion) " +
            "VALUES (?, 'CREAR_USUARIO_PADRE', ?::jsonb, 'PENDIENTE', ?, ?)",
            newTaskId, payload, usuarioId, usuarioId
        );
        return newTaskId;
    }

    @Override
    public void upsertRestriccion(UUID tutorAlumnoId, Map<String, Object> r, String usuarioId) {
        jdbc.update(
            "INSERT INTO ades_restricciones_tutor " +
            "(tutor_alumno_id, puede_ver_calificaciones, puede_ver_asistencias, " +
            " puede_ver_conducta, puede_ver_tareas, puede_descargar_documentos, " +
            " puede_comunicarse_docentes, razon_restriccion, usuario_creacion, usuario_modificacion) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT (tutor_alumno_id) DO UPDATE SET " +
            " puede_ver_calificaciones = EXCLUDED.puede_ver_calificaciones, " +
            " puede_ver_asistencias = EXCLUDED.puede_ver_asistencias, " +
            " puede_ver_conducta = EXCLUDED.puede_ver_conducta, " +
            " puede_ver_tareas = EXCLUDED.puede_ver_tareas, " +
            " puede_descargar_documentos = EXCLUDED.puede_descargar_documentos, " +
            " puede_comunicarse_docentes = EXCLUDED.puede_comunicarse_docentes, " +
            " razon_restriccion = EXCLUDED.razon_restriccion, " +
            " usuario_modificacion = EXCLUDED.usuario_modificacion",
            tutorAlumnoId,
            r.get("puede_ver_calificaciones"), r.get("puede_ver_asistencias"),
            r.get("puede_ver_conducta"), r.get("puede_ver_tareas"),
            r.get("puede_descargar_documentos"), r.get("puede_comunicarse_docentes"),
            r.get("razon_restriccion"), usuarioId, usuarioId
        );
    }
}
