package mx.ades.modules.procesos.infrastructure.outbound.persistence;

import mx.ades.modules.procesos.domain.port.in.ProcesarPreinscripcionUseCase;
import mx.ades.modules.procesos.domain.port.out.PreinscripcionRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
public class ProcesosPersistenceAdapter implements PreinscripcionRepositoryPort {

    private final JdbcTemplate jdbc;

    public ProcesosPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AdmisionData> findAdmisionAceptada(UUID admisionId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT nombre, apellido_paterno, curp FROM ades_solicitudes_admision " +
                "WHERE id = ? AND estado = 'ACEPTADO'", admisionId);
        if (rows.isEmpty()) return Optional.empty();
        Map<String, Object> r = rows.get(0);
        return Optional.of(new AdmisionData(
                (String) r.get("nombre"),
                (String) r.get("apellido_paterno"),
                (String) r.get("curp")));
    }

    @Override
    public Optional<GrupoCapacidad> findCapacidadGrupo(UUID grupoId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT capacidad_maxima, " +
                "(SELECT COUNT(*) FROM ades_inscripciones WHERE grupo_id = ? AND is_active = TRUE) AS inscritos " +
                "FROM ades_grupos WHERE id = ? AND is_active = TRUE",
                grupoId, grupoId);
        if (rows.isEmpty()) return Optional.empty();
        Map<String, Object> r = rows.get(0);
        int max = ((Number) r.get("capacidad_maxima")).intValue();
        int cur = ((Number) r.get("inscritos")).intValue();
        return Optional.of(new GrupoCapacidad(max, cur));
    }

    @Override
    @Transactional
    // 4 INSERT/UPDATE encadenados (persona, estudiante, inscripción, solicitud) sin
    // @Transactional: si el 2° o 3° fallaba, la persona ya insertada quedaba huérfana
    // sin alumno asociado. Hallazgo colateral al corregir la matrícula racy (H-5, 2026-07-21).
    public ProcesarPreinscripcionUseCase.PreinscripcionResult guardar(
            ProcesarPreinscripcionUseCase.Command command, AdmisionData admision) {

        UUID personaId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_personas (id, nombre, apellido_paterno, curp, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                personaId, admision.nombre(), admision.apellidoPaterno(), admision.curp(),
                command.username(), command.username());

        // plantel_id es NOT NULL en ades_estudiantes — se resuelve desde el grupo destino
        // (grupo -> grado -> plantel), no llegaba antes y el INSERT siempre fallaba.
        UUID plantelId = jdbc.queryForObject(
                "SELECT gr.plantel_id FROM ades_grados gr JOIN ades_grupos g ON g.grado_id = gr.id WHERE g.id = ?",
                UUID.class, command.grupoId());

        UUID estudianteId = UUID.randomUUID();
        // H-5 (auditoría 2026-07-20): Random().nextInt() sin verificación de colisión
        // reemplazado por la secuencia atómica compartida (ver AlumnoPersistenceAdapter).
        Long matSeq = jdbc.queryForObject("SELECT nextval('ades_estudiantes_matricula_seq')", Long.class);
        String matricula = String.format("MAT-%06d", matSeq);
        jdbc.update(
                "INSERT INTO ades_estudiantes (id, persona_id, matricula, plantel_id, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                estudianteId, personaId, matricula, plantelId, command.username(), command.username());

        jdbc.update(
                "INSERT INTO ades_inscripciones " +
                "(id, estudiante_id, grupo_id, ciclo_escolar_id, fecha_inscripcion, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, CURRENT_DATE, ?, ?)",
                UUID.randomUUID(), estudianteId,
                command.grupoId(), command.cicloEscolarId(),
                command.username(), command.username());

        jdbc.update(
                "UPDATE ades_solicitudes_admision SET estado = 'INSCRITO', usuario_modificacion = ? WHERE id = ?",
                command.username(), command.admisionId());

        return new ProcesarPreinscripcionUseCase.PreinscripcionResult(
                estudianteId, matricula, admision.nombre(), admision.apellidoPaterno(), admision.curp());
    }
}
