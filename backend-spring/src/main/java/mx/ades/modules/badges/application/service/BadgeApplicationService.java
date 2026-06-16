package mx.ades.modules.badges.application.service;

import mx.ades.modules.badges.Badge;
import mx.ades.modules.badges.domain.port.in.*;
import mx.ades.modules.badges.domain.port.out.BadgeRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

public class BadgeApplicationService
        implements CrearBadgeUseCase, OtorgarBadgeUseCase, RevocarBadgeUseCase, AutoEvaluarBadgesUseCase {

    private final BadgeRepositoryPort repo;

    public BadgeApplicationService(BadgeRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public UUID crear(CrearBadgeUseCase.Command cmd) {
        Badge b = new Badge();
        b.setNombre(cmd.nombre());
        b.setDescripcion(cmd.descripcion());
        if (cmd.icono() != null) b.setIcono(cmd.icono());
        if (cmd.color() != null) b.setColor(cmd.color());
        b.setTipo(cmd.tipo().name());
        b.setCriterioTipo(cmd.criterioTipo().name());
        if (cmd.criterioMetrica() != null) b.setCriterioMetrica(cmd.criterioMetrica());
        if (cmd.criterioValor() != null) b.setCriterioValor(cmd.criterioValor());
        b.setPlantelId(cmd.plantelId());
        return repo.save(b).getId();
    }

    @Override
    public boolean otorgar(OtorgarBadgeUseCase.Command cmd) {
        return repo.otorgar(cmd.badgeId(), cmd.estudianteId(), cmd.cicloId(), cmd.motivo(), cmd.otorgadoPor());
    }

    @Override
    public void revocar(UUID badgeId, UUID estudianteId, UUID cicloId) {
        repo.revocar(badgeId, estudianteId, cicloId);
    }

    @Override
    public AutoEvaluarBadgesUseCase.Result autoEvaluar(UUID cicloId) {
        List<Badge> badges = repo.findAllAutomatic();
        int totalOtorgados = 0;

        for (Badge badge : badges) {
            String metrica = badge.getCriterioMetrica();
            double umbral = badge.getCriterioValor() != null ? badge.getCriterioValor().doubleValue() : 0.0;
            List<UUID> elegibles;

            if ("pct_asistencia".equalsIgnoreCase(metrica)) {
                elegibles = repo.eligiblesByPctAsistencia(cicloId, umbral);
            } else if ("promedio_general".equalsIgnoreCase(metrica)) {
                elegibles = repo.eligiblesByPromedioGeneral(cicloId, umbral);
            } else if ("sin_reportes_conducta".equalsIgnoreCase(metrica)) {
                elegibles = repo.eligiblesBySinReportes(cicloId);
            } else {
                continue;
            }

            totalOtorgados += repo.otorgarBulk(badge.getId(), cicloId, elegibles);
        }

        return new AutoEvaluarBadgesUseCase.Result(badges.size(), totalOtorgados);
    }

    public void eliminar(UUID id) {
        Badge b = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Badge no encontrado"));
        b.setIsActive(false);
        repo.save(b);
    }
}
