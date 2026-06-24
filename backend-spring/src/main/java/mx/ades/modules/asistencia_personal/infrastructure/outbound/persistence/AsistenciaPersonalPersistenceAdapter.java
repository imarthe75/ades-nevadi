package mx.ades.modules.asistencia_personal.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.asistencia_personal.domain.port.in.ActualizarAsistenciaUseCase;
import mx.ades.modules.asistencia_personal.domain.port.in.RegistrarAsistenciaUseCase;
import mx.ades.modules.asistencia_personal.domain.port.out.AsistenciaPersonalRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/**
 * Adaptador de persistencia que implementa {@link AsistenciaPersonalRepositoryPort}
 * accediendo a la tabla {@code ades_asistencia_personal} vía JDBC.
 *
 * <p>Soporta filtros dinámicos (persona, fechas, tipo de jornada, búsqueda por nombre)
 * y actualización parcial de campos sin sobreescribir los no enviados.</p>
 *
 * @author ADES
 * @since 2026
 */
@Component
@RequiredArgsConstructor
public class AsistenciaPersonalPersistenceAdapter implements AsistenciaPersonalRepositoryPort {

    private final JdbcTemplate jdbc;

    @Override
    public List<Map<String, Object>> list(UUID personaId, LocalDate fechaInicio, LocalDate fechaFin,
                                          String tipoJornada, String q) {
        StringBuilder sql = new StringBuilder(
            "SELECT ap.*, " +
            "  CONCAT(pe.nombre, ' ', pe.apellido_paterno, CASE WHEN pe.apellido_materno IS NOT NULL " +
            "    THEN CONCAT(' ', pe.apellido_materno) ELSE '' END) AS nombre_completo, " +
            "  pe.nombre AS nombre_persona, pe.apellido_paterno, pe.apellido_materno " +
            "FROM public.ades_asistencia_personal ap " +
            "LEFT JOIN ades_personas pe ON pe.id = ap.persona_id " +
            "WHERE ap.is_active = TRUE "
        );

        List<Object> params = new ArrayList<>();
        if (personaId != null) {
            sql.append("AND ap.persona_id = ? ");
            params.add(personaId);
        }
        if (q != null && !q.isBlank()) {
            sql.append("AND (pe.nombre ILIKE ? OR pe.apellido_paterno ILIKE ? OR CONCAT(pe.nombre,' ',pe.apellido_paterno) ILIKE ?) ");
            String like = "%" + q.trim() + "%";
            params.add(like); params.add(like); params.add(like);
        }
        if (fechaInicio != null) {
            sql.append("AND ap.fecha >= ? ");
            params.add(fechaInicio);
        }
        if (fechaFin != null) {
            sql.append("AND ap.fecha <= ? ");
            params.add(fechaFin);
        }
        if (tipoJornada != null && !tipoJornada.isBlank()) {
            sql.append("AND ap.tipo_jornada = ? ");
            params.add(tipoJornada);
        }
        sql.append("ORDER BY ap.fecha DESC, pe.apellido_paterno, pe.nombre");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    @Override
    public boolean existeRegistro(UUID personaId, LocalDate fecha) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM public.ades_asistencia_personal WHERE persona_id = ? AND fecha = ?",
            Integer.class, personaId, fecha);
        return count != null && count > 0;
    }

    @Override
    public void insert(RegistrarAsistenciaUseCase.Command cmd) {
        jdbc.update(
            "INSERT INTO public.ades_asistencia_personal " +
            "(id, persona_id, fecha, hora_entrada, hora_salida, tipo_jornada, es_retardo, " +
            "minutos_retardo, observaciones, usuario_creacion) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), cmd.personaId(), cmd.fecha(), cmd.horaEntrada(), cmd.horaSalida(),
            cmd.tipoJornada().name(), cmd.esRetardo(), cmd.minutosRetardo(), cmd.observaciones(), cmd.usuario());
    }

    @Override
    public void update(RegistrarAsistenciaUseCase.Command cmd) {
        jdbc.update(
            "UPDATE public.ades_asistencia_personal " +
            "SET hora_entrada = ?, hora_salida = ?, tipo_jornada = ?, es_retardo = ?, " +
            "minutos_retardo = ?, observaciones = ?, usuario_modificacion = ?, row_version = row_version + 1 " +
            "WHERE persona_id = ? AND fecha = ?",
            cmd.horaEntrada(), cmd.horaSalida(), cmd.tipoJornada().name(), cmd.esRetardo(),
            cmd.minutosRetardo(), cmd.observaciones(), cmd.usuario(),
            cmd.personaId(), cmd.fecha());
    }

    @Override
    public Optional<Map<String, Object>> findById(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM public.ades_asistencia_personal WHERE id = ? AND is_active = TRUE", id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public Map<String, Object> findByPersonaFecha(UUID personaId, LocalDate fecha) {
        return jdbc.queryForMap(
            "SELECT * FROM public.ades_asistencia_personal WHERE persona_id = ? AND fecha = ?",
            personaId, fecha);
    }

    @Override
    public void patch(ActualizarAsistenciaUseCase.Command cmd) {
        ActualizarAsistenciaUseCase.Patch p = cmd.patch();
        StringBuilder sql = new StringBuilder(
            "UPDATE public.ades_asistencia_personal SET usuario_modificacion = ?, row_version = row_version + 1");
        List<Object> params = new ArrayList<>();
        params.add(cmd.usuario());

        if (p.horaEntrada() != null) { sql.append(", hora_entrada = ?"); params.add(p.horaEntrada()); }
        if (p.horaSalida() != null) { sql.append(", hora_salida = ?"); params.add(p.horaSalida()); }
        if (p.tipoJornada() != null) { sql.append(", tipo_jornada = ?"); params.add(p.tipoJornada().name()); }
        if (p.esRetardo() != null) { sql.append(", es_retardo = ?"); params.add(p.esRetardo()); }
        if (p.minutosRetardo() != null) { sql.append(", minutos_retardo = ?"); params.add(p.minutosRetardo()); }
        if (p.observaciones() != null) { sql.append(", observaciones = ?"); params.add(p.observaciones()); }
        if (p.justificado() != null) {
            sql.append(", justificado = ?, justificado_por = ?");
            params.add(p.justificado());
            params.add(cmd.justificadoPor());
        }
        if (p.justificacion() != null) { sql.append(", justificacion = ?"); params.add(p.justificacion()); }

        sql.append(" WHERE id = ?");
        params.add(cmd.id());
        jdbc.update(sql.toString(), params.toArray());
    }

    @Override
    public Map<String, Object> fetchById(UUID id) {
        return jdbc.queryForMap(
            "SELECT * FROM public.ades_asistencia_personal WHERE id = ?", id);
    }

    @Override
    public List<Map<String, Object>> reporte(UUID personaId, int mes, int anio) {
        return jdbc.queryForList(
            "SELECT tipo_jornada, es_retardo " +
            "FROM public.ades_asistencia_personal " +
            "WHERE persona_id = ? AND EXTRACT(MONTH FROM fecha) = ? AND EXTRACT(YEAR FROM fecha) = ? AND is_active = TRUE",
            personaId, mes, anio);
    }

    @Override
    public void softDelete(UUID id, String usuario) {
        jdbc.update(
            "UPDATE public.ades_asistencia_personal SET is_active = FALSE, " +
            "usuario_modificacion = ?, row_version = row_version + 1 WHERE id = ?",
            usuario, id);
    }
}
