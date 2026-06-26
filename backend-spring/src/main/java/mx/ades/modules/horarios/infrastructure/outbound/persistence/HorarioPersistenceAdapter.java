package mx.ades.modules.horarios.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.horarios.domain.port.in.ActualizarHorarioUseCase;
import mx.ades.modules.horarios.domain.port.in.CrearHorarioUseCase;
import mx.ades.modules.horarios.domain.port.out.HorarioWriteRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Adaptador JDBC que implementa {@link HorarioWriteRepositoryPort}.
 * Gestiona inserciones, actualizaciones y soft-delete en {@code ades_horarios}.
 *
 * @author ADES
 * @since 2026
 */
@Component
@RequiredArgsConstructor
public class HorarioPersistenceAdapter implements HorarioWriteRepositoryPort {

    private final JdbcTemplate jdbc;

    @Override
    public UUID insert(CrearHorarioUseCase.Command cmd) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ades_horarios " +
            "(id, grupo_id, materia_id, profesor_id, aula_id, ciclo_escolar_id, corrida_id, " +
            " dia_semana, hora_inicio, hora_fin, origen, fijado, usuario_creacion, usuario_modificacion) " +
            "VALUES (?,?,?,?,?,?,?, ?,CAST(? AS time),CAST(? AS time),?,?,?,?)",
            id, cmd.grupoId(), cmd.materiaId(), cmd.profesorId(), cmd.aulaId(),
            cmd.cicloEscolarId(), null, cmd.diaSemana(),
            cmd.horaInicio(), cmd.horaFin(), cmd.origen(), false,
            cmd.usuario(), cmd.usuario());
        return id;
    }

    @Override
    public boolean exists(UUID horarioId) {
        List<?> rows = jdbc.queryForList(
            "SELECT id FROM ades_horarios WHERE id = ? AND is_active = TRUE", horarioId);
        return !rows.isEmpty();
    }

    @Override
    public void update(ActualizarHorarioUseCase.Command cmd) {
        StringBuilder sql = new StringBuilder("UPDATE ades_horarios SET usuario_modificacion = ?");
        List<Object> params = new ArrayList<>();
        params.add(cmd.usuario());

        if (cmd.materiaId()    != null) { sql.append(", materia_id = ?");    params.add(cmd.materiaId()); }
        if (cmd.profesorId()   != null) { sql.append(", profesor_id = ?");   params.add(cmd.profesorId()); }
        if (cmd.aulaId()       != null) { sql.append(", aula_id = ?");       params.add(cmd.aulaId()); }
        if (cmd.diaSemana()    != null) { sql.append(", dia_semana = ?");    params.add(cmd.diaSemana()); }
        if (cmd.horaInicio()   != null) { sql.append(", hora_inicio = CAST(? AS time)"); params.add(cmd.horaInicio()); }
        if (cmd.horaFin()      != null) { sql.append(", hora_fin = CAST(? AS time)");    params.add(cmd.horaFin()); }
        if (cmd.origen()       != null) { sql.append(", origen = ?");        params.add(cmd.origen()); }
        if (cmd.motivoCambio() != null) {
            sql.append(", motivo_cambio = ?, fecha_cambio = NOW()");
            params.add(cmd.motivoCambio());
        }

        sql.append(" WHERE id = ?");
        params.add(cmd.horarioId());
        jdbc.update(sql.toString(), params.toArray());
    }

    @Override
    public void softDelete(UUID horarioId) {
        jdbc.update("UPDATE ades_horarios SET is_active = FALSE WHERE id = ?", horarioId);
    }
}
