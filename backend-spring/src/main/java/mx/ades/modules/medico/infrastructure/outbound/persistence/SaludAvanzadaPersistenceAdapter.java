package mx.ades.modules.medico.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.medico.domain.port.in.*;
import mx.ades.modules.medico.domain.port.out.SaludAvanzadaRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SaludAvanzadaPersistenceAdapter implements SaludAvanzadaRepositoryPort {

    private final JdbcTemplate jdbc;

    @Override
    public UUID insertMedicamento(RegistrarMedicamentoUseCase.Command cmd) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ades_medicamentos_alumno " +
            "(id, alumno_id, nombre_medicamento, dosis, frecuencia, horario, " +
            "via_administracion, prescrito_por, fecha_inicio, fecha_fin, " +
            "observaciones, usuario_creacion, usuario_modificacion) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            id, cmd.alumnoId(), cmd.nombreMedicamento(), cmd.dosis(), cmd.frecuencia(), cmd.horario(),
            cmd.viaAdministracion(), cmd.prescritoPor(), cmd.fechaInicio(), cmd.fechaFin(),
            cmd.observaciones(), cmd.usuario(), cmd.usuario());
        return id;
    }

    @Override
    public void suspenderMedicamento(SuspenderMedicamentoUseCase.Command cmd) {
        jdbc.update(
            "UPDATE ades_medicamentos_alumno SET is_active = FALSE, usuario_modificacion = ? WHERE id = ?",
            cmd.usuario(), cmd.medicamentoId());
    }

    @Override
    public boolean existeIncidente(UUID incidenteId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id FROM ades_incidentes_medicos WHERE id = ?", incidenteId);
        return !rows.isEmpty();
    }

    @Override
    public UUID insertActaIncidente(GenerarActaIncidenteUseCase.Command cmd) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ades_actas_incidente_medico " +
            "(id, incidente_id, descripcion_detallada, testigos, medidas_tomadas, " +
            "requirio_traslado, hospital_destino, notificado_familia, " +
            "firma_responsable, usuario_creacion, usuario_modificacion) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            id, cmd.incidenteId(), cmd.descripcionDetallada(), cmd.testigos(), cmd.medidasTomadas(),
            cmd.requirioTraslado(), cmd.hospitalDestino(), cmd.notificadoFamilia(),
            cmd.firmaResponsable(), cmd.usuario(), cmd.usuario());
        return id;
    }

    @Override
    public UUID insertPsicosocial(RegistrarPsicosocialUseCase.Command cmd) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ades_seguimiento_psicosocial " +
            "(id, alumno_id, tipo_atencion, motivo, observaciones, estrategias_sugeridas, " +
            "requiere_derivacion, derivado_a, proxima_sesion, " +
            "usuario_creacion, usuario_modificacion) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            id, cmd.alumnoId(), cmd.tipoAtencion(), cmd.motivo(), cmd.observaciones(),
            cmd.estrategiasSugeridas(), cmd.requiereDerivacion(), cmd.derivadoA(),
            cmd.proximaSesion(), cmd.usuario(), cmd.usuario());
        return id;
    }

    @Override
    public UUID insertTutoria(RegistrarTutoriaUseCase.Command cmd) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ades_tutorias " +
            "(id, alumno_id, tipo_tutoria, tema, descripcion, duracion_minutos, " +
            "acuerdos, proxima_sesion, requiere_seguimiento, " +
            "usuario_creacion, usuario_modificacion) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            id, cmd.alumnoId(), cmd.tipoTutoria(), cmd.tema(), cmd.descripcion(), cmd.duracionMinutos(),
            cmd.acuerdos(), cmd.proximaSesion(), cmd.requiereSeguimiento(),
            cmd.usuario(), cmd.usuario());
        return id;
    }
}
