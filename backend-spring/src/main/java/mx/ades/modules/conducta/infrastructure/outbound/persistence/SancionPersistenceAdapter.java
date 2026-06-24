package mx.ades.modules.conducta.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.conducta.domain.port.in.AplicarSancionCommand;
import mx.ades.modules.conducta.domain.port.out.SancionRepositoryPort;
import mx.ades.modules.conducta.SancionDisciplinaria;
import mx.ades.modules.conducta.SancionDisciplinariaRepository;
import mx.ades.modules.conducta.ReporteConducta;
import mx.ades.modules.conducta.ReporteConductaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Adaptador JPA que implementa {@code SancionRepositoryPort}.
 * <p>Persiste sanciones en {@code ades_sanciones_disciplinarias} y actualiza el campo
 * {@code requiere_seguimiento} del reporte de conducta asociado en {@code ades_reportes_conducta}.</p>
 *
 * @author ADES
 * @since 2026
 */
@Component
@RequiredArgsConstructor
public class SancionPersistenceAdapter implements SancionRepositoryPort {

    private final SancionDisciplinariaRepository sancionRepo;
    private final ReporteConductaRepository      reporteRepo;

    @Override
    @Transactional
    public UUID guardar(UUID reporteId, AplicarSancionCommand cmd) {
        ReporteConducta reporte = reporteRepo.findById(reporteId)
                .filter(ReporteConducta::getIsActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reporte no encontrado: " + reporteId));

        if (sancionRepo.findByReporteConductaIdAndIsActiveTrue(reporteId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este reporte ya tiene una sanción aplicada");
        }

        SancionDisciplinaria s = new SancionDisciplinaria();
        s.setReporteConductaId(reporteId);
        s.setEstudianteId(reporte.getEstudianteId());
        s.setTipoSancion(cmd.tipoSancion().name());
        s.setJustificacion(cmd.justificacion());
        s.setAutorizadoPorId(cmd.autorizadoPorId());
        s.setFechaSancion(cmd.fechaSancion() != null ? cmd.fechaSancion() : LocalDate.now());
        s.setFechaFinSancion(cmd.fechaFinSancion());
        s.setNotificadoPadres(cmd.notificadoPadres());
        s.setFechaNotificacion(cmd.fechaNotificacion());
        s.setMedioNotificacion(cmd.medioNotificacion());
        s.setNotasAdicionales(cmd.notasAdicionales());

        UUID sancionId = sancionRepo.save(s).getId();

        // Marcar reporte como requiere_seguimiento
        reporte.setRequiereSeguimiento(true);
        reporteRepo.save(reporte);

        return sancionId;
    }
}
