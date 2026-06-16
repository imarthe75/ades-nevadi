package mx.ades.modules.planes_estudio.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.planes_estudio.domain.port.in.AsignarMateriaUseCase;
import mx.ades.modules.planes_estudio.domain.port.out.PlanEstudioRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PlanEstudioPersistenceAdapter implements PlanEstudioRepositoryPort {

    private final JdbcTemplate jdbc;

    @Override
    public UUID insert(AsignarMateriaUseCase.Command cmd) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ades_materias_plan (id, materia_id, grado_id, ciclo_escolar_id, " +
            "horas_semana, es_obligatoria, usuario_creacion, usuario_modificacion) " +
            "VALUES (?, ?, ?, ?, ?, ?, 'sistema', 'sistema')",
            id, cmd.materiaId(), cmd.gradoId(), cmd.cicloEscolarId(),
            cmd.horasSemana(), cmd.esObligatoria());
        return id;
    }

    @Override
    public Map<String, Object> fetchById(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT mp.id, mp.materia_id, mp.grado_id, mp.ciclo_escolar_id, mp.horas_semana, " +
            "mp.es_obligatoria, m.nombre_materia, g.nombre_grado " +
            "FROM ades_materias_plan mp JOIN ades_materias m ON m.id = mp.materia_id " +
            "JOIN ades_grados g ON g.id = mp.grado_id WHERE mp.id = ?", id);
        return rows.isEmpty() ? Map.of("id", id.toString()) : rows.get(0);
    }

    @Override
    public void patchHorasSemana(UUID id, double horas) {
        jdbc.update("UPDATE ades_materias_plan SET horas_semana = ? WHERE id = ?", horas, id);
    }

    @Override
    public void patchObligatoria(UUID id, boolean esObligatoria) {
        jdbc.update("UPDATE ades_materias_plan SET es_obligatoria = ? WHERE id = ?", esObligatoria, id);
    }

    @Override
    public void patchOrden(UUID id, int orden) {
        jdbc.update("UPDATE ades_materias_plan SET orden = ? WHERE id = ?", orden, id);
    }

    @Override
    public int softDelete(UUID id) {
        return jdbc.update("UPDATE ades_materias_plan SET is_active = FALSE WHERE id = ?", id);
    }
}
