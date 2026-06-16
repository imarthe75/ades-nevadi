package mx.ades.modules.procesos.infrastructure.outbound.persistence;

import mx.ades.modules.procesos.domain.port.in.ProcesarPreinscripcionUseCase;
import mx.ades.modules.procesos.domain.port.out.PreinscripcionRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
    public ProcesarPreinscripcionUseCase.PreinscripcionResult guardar(
            ProcesarPreinscripcionUseCase.Command command, AdmisionData admision) {

        UUID personaId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_personas (id, nombre, apellido_paterno, curp, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                personaId, admision.nombre(), admision.apellidoPaterno(), admision.curp(),
                command.username(), command.username());

        UUID estudianteId = UUID.randomUUID();
        String matricula = "MAT-" + (100000 + new Random().nextInt(900000));
        jdbc.update(
                "INSERT INTO ades_estudiantes (id, persona_id, matricula, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?)",
                estudianteId, personaId, matricula, command.username(), command.username());

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
