package mx.ades.modules.expediente_laboral.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.expediente_laboral.domain.port.in.ActualizarExpedienteLaboralUseCase;
import mx.ades.modules.expediente_laboral.domain.port.in.CrearExpedienteLaboralUseCase;
import mx.ades.modules.expediente_laboral.domain.port.out.ExpedienteLaboralRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Adaptador JDBC que implementa {@link ExpedienteLaboralRepositoryPort}.
 * Gestiona operaciones de lectura, escritura y soft-delete sobre {@code ades_expediente_laboral}.
 *
 * @author ADES
 * @since 2026
 */
@Component
@RequiredArgsConstructor
public class ExpedienteLaboralPersistenceAdapter implements ExpedienteLaboralRepositoryPort {

    private final JdbcTemplate jdbc;

    /**
     * BOLA fix (2026-07-16): {@code plantelId} filtra por el plantel resuelto vía
     * COALESCE entre las 3 tablas que pueden vincular una persona a un plantel
     * (docente, personal administrativo, personal de salud) — {@code null} para
     * nivelAcceso 0 (alcance institucional), forzado al plantel del usuario en el
     * resto de roles por {@code AdesUserService#getEffectivePlantelId}.
     */
    @Override
    public List<Map<String, Object>> list(UUID personaId, String tipoContrato, String q, UUID plantelId) {
        StringBuilder sql = new StringBuilder(
            "SELECT el.*, " +
            "  CONCAT(pe.nombre, ' ', pe.apellido_paterno, CASE WHEN pe.apellido_materno IS NOT NULL " +
            "    THEN CONCAT(' ', pe.apellido_materno) ELSE '' END) AS nombre_completo, " +
            "  pe.nombre AS nombre_persona, pe.apellido_paterno, pe.apellido_materno " +
            "FROM ades_expediente_laboral el " +
            "LEFT JOIN ades_personas pe ON pe.id = el.persona_id " +
            "WHERE el.is_active = TRUE ");
        List<Object> params = new ArrayList<>();

        if (personaId != null) { sql.append("AND el.persona_id = ? "); params.add(personaId); }
        if (q != null && !q.isBlank()) {
            sql.append("AND (pe.nombre ILIKE ? OR pe.apellido_paterno ILIKE ? OR CONCAT(pe.nombre,' ',pe.apellido_paterno) ILIKE ?) ");
            String like = "%" + q.trim() + "%";
            params.add(like); params.add(like); params.add(like);
        }
        if (tipoContrato != null && !tipoContrato.isBlank()) { sql.append("AND el.tipo_contrato = ? "); params.add(tipoContrato); }
        if (plantelId != null) {
            sql.append("AND COALESCE(" +
                "(SELECT plantel_id FROM ades_profesores WHERE persona_id = el.persona_id), " +
                "(SELECT plantel_id FROM ades_personal_administrativo WHERE persona_id = el.persona_id), " +
                "(SELECT plantel_id FROM ades_personal_salud WHERE persona_id = el.persona_id)" +
                ") = ? ");
            params.add(plantelId);
        }
        sql.append("ORDER BY el.fecha_creacion DESC");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    /**
     * Resuelve el plantel de una persona (docente, administrativo o personal de
     * salud) para el chequeo BOLA de plantel en el controller.
     */
    @Override
    public UUID plantelDePersona(UUID personaId) {
        List<UUID> rows = jdbc.queryForList(
            "SELECT COALESCE(" +
            "(SELECT plantel_id FROM ades_profesores WHERE persona_id = ?), " +
            "(SELECT plantel_id FROM ades_personal_administrativo WHERE persona_id = ?), " +
            "(SELECT plantel_id FROM ades_personal_salud WHERE persona_id = ?)" +
            ")", UUID.class, personaId, personaId, personaId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public UUID insert(CrearExpedienteLaboralUseCase.Command cmd) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ades_expediente_laboral " +
            "(id, persona_id, tipo_contrato, fecha_contratacion, fecha_fin_contrato, " +
            " salario_mensual, imss_numero, infonavit_numero, curp, rfc, " +
            " cedula_profesional, nivel_estudios, especialidad, institucion_formacion, " +
            " clave_ct, clave_issste, usuario_creacion, usuario_modificacion) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            id, cmd.personaId(), cmd.tipoContrato(), cmd.fechaContratacion(),
            cmd.fechaFinContrato(), cmd.salarioMensual(), cmd.imssNumero(),
            cmd.infonavitNumero(), cmd.curp(), cmd.rfc(), cmd.cedulaProfesional(),
            cmd.nivelEstudios(), cmd.especialidad(), cmd.institucionFormacion(),
            cmd.claveCt(), cmd.claveIssste(), cmd.usuarioId(), cmd.usuarioId());
        return id;
    }

    @Override
    public Optional<Map<String, Object>> findById(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM ades_expediente_laboral WHERE id = ? AND is_active = TRUE", id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public Map<String, Object> patch(UUID id, ActualizarExpedienteLaboralUseCase.Patch p, String usuarioId) {
        StringBuilder sql = new StringBuilder(
            "UPDATE ades_expediente_laboral SET usuario_modificacion = ?, row_version = row_version + 1 ");
        List<Object> params = new ArrayList<>();
        params.add(usuarioId);

        if (p.tipoContrato() != null) { sql.append(", tipo_contrato = ? "); params.add(p.tipoContrato()); }
        if (p.fechaFinContrato() != null) { sql.append(", fecha_fin_contrato = ? "); params.add(p.fechaFinContrato()); }
        if (p.salarioMensual() != null) { sql.append(", salario_mensual = ? "); params.add(p.salarioMensual()); }
        if (p.imssNumero() != null) { sql.append(", imss_numero = ? "); params.add(p.imssNumero()); }
        if (p.infonavitNumero() != null) { sql.append(", infonavit_numero = ? "); params.add(p.infonavitNumero()); }
        if (p.curp() != null) { sql.append(", curp = ? "); params.add(p.curp()); }
        if (p.rfc() != null) { sql.append(", rfc = ? "); params.add(p.rfc()); }
        if (p.cedulaProfesional() != null) { sql.append(", cedula_profesional = ? "); params.add(p.cedulaProfesional()); }
        if (p.nivelEstudios() != null) { sql.append(", nivel_estudios = ? "); params.add(p.nivelEstudios()); }
        if (p.especialidad() != null) { sql.append(", especialidad = ? "); params.add(p.especialidad()); }
        if (p.institucionFormacion() != null) { sql.append(", institucion_formacion = ? "); params.add(p.institucionFormacion()); }
        if (p.claveCt() != null) { sql.append(", clave_ct = ? "); params.add(p.claveCt()); }
        if (p.claveIssste() != null) { sql.append(", clave_issste = ? "); params.add(p.claveIssste()); }

        sql.append("WHERE id = ? RETURNING *");
        params.add(id);
        List<Map<String, Object>> updated = jdbc.queryForList(sql.toString(), params.toArray());
        return updated.get(0);
    }

    @Override
    public void agregarDocumento(UUID id, String tipoDocumento, String url, String usuarioId) {
        jdbc.update(
            "UPDATE ades_expediente_laboral " +
            "SET documentos_urls = documentos_urls || jsonb_build_object(?, ?), " +
            "usuario_modificacion = ?, row_version = row_version + 1 " +
            "WHERE id = ?",
            tipoDocumento, url, usuarioId, id);
    }

    @Override
    public Map<String, Object> fetchById(UUID id) {
        return jdbc.queryForMap("SELECT * FROM ades_expediente_laboral WHERE id = ? AND is_active = TRUE", id);
    }

    @Override
    public void softDelete(UUID id, String usuarioId) {
        jdbc.update(
            "UPDATE ades_expediente_laboral SET is_active = FALSE, " +
            "usuario_modificacion = ?, row_version = row_version + 1 WHERE id = ?",
            usuarioId, id);
    }
}
