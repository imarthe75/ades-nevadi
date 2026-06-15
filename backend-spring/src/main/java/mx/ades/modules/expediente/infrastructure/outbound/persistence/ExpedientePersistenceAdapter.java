package mx.ades.modules.expediente.infrastructure.outbound.persistence;

import mx.ades.modules.expediente.domain.model.TipoBaja;
import mx.ades.modules.expediente.domain.port.out.BajaRepositoryPort;
import mx.ades.modules.expediente.domain.port.out.ConstanciaRepositoryPort;
import mx.ades.modules.expediente.domain.port.out.ExtraordinarioRepositoryPort;
import mx.ades.modules.expediente.domain.port.out.ExpedienteRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ExpedientePersistenceAdapter
        implements BajaRepositoryPort, ExtraordinarioRepositoryPort,
                   ConstanciaRepositoryPort, ExpedienteRepositoryPort {

    private final JdbcTemplate jdbc;

    public ExpedientePersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── BajaRepositoryPort ────────────────────────────────────────────────────

    @Override
    public UUID guardar(UUID estudianteId, TipoBaja tipo, String motivo,
                        LocalDate fechaEfectiva, LocalDate fechaReingreso,
                        String plantelDestino, String claveCtDestino,
                        String observaciones, UUID autorizadoPorId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ades_bajas " +
            "(id, estudiante_id, tipo_baja, motivo, fecha_efectiva, fecha_reingreso, " +
            " plantel_destino, clave_ct_destino, observaciones, autorizado_por_id, " +
            " usuario_creacion, usuario_modificacion) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            id, estudianteId, tipo.name(), motivo, fechaEfectiva, fechaReingreso,
            plantelDestino, claveCtDestino, observaciones, autorizadoPorId,
            autorizadoPorId.toString(), autorizadoPorId.toString()
        );
        return id;
    }

    @Override
    public void desactivarEstudiante(UUID estudianteId) {
        jdbc.update("UPDATE ades_estudiantes SET is_active = FALSE WHERE id = ?", estudianteId);
    }

    // ── ExtraordinarioRepositoryPort ──────────────────────────────────────────

    @Override
    public boolean existeActivo(UUID extraordinarioId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id FROM ades_extraordinarias WHERE id = ? AND is_active = TRUE",
            extraordinarioId);
        return !rows.isEmpty();
    }

    @Override
    public void calificar(UUID extraordinarioId, BigDecimal calificacion,
                          boolean acredita, LocalDate fechaExamen) {
        StringBuilder sql = new StringBuilder(
            "UPDATE ades_extraordinarias SET calificacion = ?, acredita = ? ");
        List<Object> params = new java.util.ArrayList<>();
        params.add(calificacion);
        params.add(acredita);
        if (fechaExamen != null) {
            sql.append(", fecha_examen = ? ");
            params.add(fechaExamen);
        }
        sql.append("WHERE id = ?");
        params.add(extraordinarioId);
        jdbc.update(sql.toString(), params.toArray());
    }

    // ── ConstanciaRepositoryPort ──────────────────────────────────────────────

    @Override
    public String generarFolio(String tipoConstancia) {
        int anio = LocalDate.now().getYear();
        String prefix = tipoConstancia.length() >= 3
                ? tipoConstancia.substring(0, 3).toUpperCase()
                : tipoConstancia.toUpperCase();
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ades_constancias WHERE tipo_constancia = ? AND EXTRACT(YEAR FROM fecha_emision) = ?",
            Integer.class, tipoConstancia, anio);
        int seq = (count != null ? count : 0) + 1;
        return String.format("%s-%d-%04d", prefix, anio, seq);
    }

    @Override
    public UUID guardar(UUID estudianteId, String tipoConstancia, String folio,
                        UUID cicloEscolarId, String solicitadaPor, String proposito,
                        LocalDate fechaVencimiento, UUID emitidaPorId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ades_constancias " +
            "(id, estudiante_id, tipo_constancia, folio, ciclo_escolar_id, fecha_emision, " +
            " fecha_vencimiento, solicitada_por, proposito, emitida_por_id, entregada, " +
            " usuario_creacion, usuario_modificacion) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, ?, ?)",
            id, estudianteId, tipoConstancia, folio, cicloEscolarId, LocalDate.now(),
            fechaVencimiento, solicitadaPor, proposito, emitidaPorId,
            emitidaPorId.toString(), emitidaPorId.toString()
        );
        return id;
    }

    @Override
    public void marcarEntregada(UUID constanciaId) {
        int rows = jdbc.update(
            "UPDATE ades_constancias SET entregada = TRUE, fecha_entrega = ? WHERE id = ? AND is_active = TRUE",
            LocalDate.now(), constanciaId);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Constancia no encontrada");
        }
    }

    // ── ExpedienteRepositoryPort ──────────────────────────────────────────────

    @Override
    public UUID findByEstudianteId(UUID estudianteId) {
        List<UUID> ids = jdbc.queryForList(
            "SELECT id FROM public.ades_expedientes_alumno WHERE estudiante_id = ? AND is_active = TRUE ORDER BY fecha_creacion DESC LIMIT 1",
            UUID.class, estudianteId);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expediente no encontrado para el estudiante");
        }
        return ids.get(0);
    }

    @Override
    public void marcarVerificado(UUID expedienteId, String observaciones, UUID verificadoPorId) {
        int rows = jdbc.update(
            "UPDATE public.ades_expedientes_alumno " +
            "SET estado = 'VERIFICADO', revisado_por = ?, fecha_revision = NOW(), " +
            "    observaciones = COALESCE(?, observaciones) " +
            "WHERE id = ? AND is_active = TRUE",
            verificadoPorId, observaciones, expedienteId);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expediente no encontrado");
        }
    }

    @Override
    public boolean documentoRequerido(UUID expedienteId, String tipoDocumento) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id FROM public.ades_expediente_documentos WHERE expediente_id = ? AND tipo_documento = ? AND is_active = TRUE",
            expedienteId, tipoDocumento);
        return !rows.isEmpty();
    }
}
