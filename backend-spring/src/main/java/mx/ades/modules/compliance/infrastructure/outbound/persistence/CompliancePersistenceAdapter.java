package mx.ades.modules.compliance.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.compliance.domain.port.in.CrearAlertaUseCase;
import mx.ades.modules.compliance.domain.port.in.RegistrarNormativaUseCase;
import mx.ades.modules.compliance.domain.port.in.RegistrarRetencionUseCase;
import mx.ades.modules.compliance.domain.port.out.ComplianceRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CompliancePersistenceAdapter implements ComplianceRepositoryPort {

    private final JdbcTemplate jdbc;

    @Override
    public UUID insertNormativa(RegistrarNormativaUseCase.Command cmd) {
        UUID id = UUID.randomUUID();
        LocalDate fi = cmd.fechaVigenciaInicio() != null ? cmd.fechaVigenciaInicio() : LocalDate.now();
        jdbc.update(
                "INSERT INTO ades_normatividad " +
                "(id, nombre, tipo, descripcion, fecha_vigencia_inicio, fecha_vigencia_fin, " +
                "url_documento, aplica_primaria, aplica_secundaria, aplica_preparatoria, " +
                "usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, cmd.nombre(), cmd.tipo(), cmd.descripcion(), fi, cmd.fechaVigenciaFin(),
                cmd.urlDocumento(), cmd.aplicaPrimaria(), cmd.aplicaSecundaria(), cmd.aplicaPreparatoria(),
                cmd.usuario(), cmd.usuario());
        return id;
    }

    @Override
    public UUID insertRetencion(RegistrarRetencionUseCase.Command cmd) {
        UUID id = UUID.randomUUID();
        LocalDate fi = cmd.fechaInicio() != null ? cmd.fechaInicio() : LocalDate.now();
        jdbc.update(
                "INSERT INTO ades_retenciones " +
                "(id, alumno_id, tipo_retencion, motivo, fecha_inicio, fecha_fin, " +
                "acciones_requeridas, autorizado_por, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, cmd.alumnoId(), cmd.tipoRetencion(), cmd.motivo(), fi, cmd.fechaFin(),
                cmd.accionesRequeridas(), cmd.autorizadoPor(), cmd.usuario(), cmd.usuario());
        return id;
    }

    @Override
    public UUID insertAlerta(CrearAlertaUseCase.Command cmd) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_alertas_cumplimiento " +
                "(id, tipo_alerta, descripcion, alumno_id, plantel_id, severidad, " +
                "requiere_accion, estado, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDIENTE', ?, ?)",
                id, cmd.tipoAlerta(), cmd.descripcion(), cmd.alumnoId(), cmd.plantelId(),
                cmd.severidad().name(), cmd.requiereAccion(), cmd.usuario(), cmd.usuario());
        return id;
    }
}
