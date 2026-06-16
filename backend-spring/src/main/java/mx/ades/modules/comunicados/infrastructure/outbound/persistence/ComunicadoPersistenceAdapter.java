package mx.ades.modules.comunicados.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.comunicados.AcuseComunicado;
import mx.ades.modules.comunicados.AcuseComunicadoRepository;
import mx.ades.modules.comunicados.Comunicado;
import mx.ades.modules.comunicados.ComunicadoRepository;
import mx.ades.modules.comunicados.domain.port.out.ComunicadoRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ComunicadoPersistenceAdapter implements ComunicadoRepositoryPort {

    private final ComunicadoRepository jpa;
    private final AcuseComunicadoRepository acuseJpa;
    private final JdbcTemplate jdbc;

    @Override
    public Comunicado save(Comunicado comunicado) { return jpa.save(comunicado); }

    @Override
    public Optional<Comunicado> findById(UUID id) { return jpa.findById(id); }

    @Override
    public Optional<AcuseComunicado> findAcuse(UUID comunicadoId, UUID usuarioId) {
        return acuseJpa.findByComunicadoIdAndUsuarioId(comunicadoId, usuarioId);
    }

    @Override
    public AcuseComunicado saveAcuse(AcuseComunicado acuse) { return acuseJpa.save(acuse); }

    @Override
    public List<UUID> getRecipientsForGrupo(UUID grupoId) {
        return jdbc.queryForList(
                "SELECT DISTINCT u.id FROM ades_usuarios u " +
                "WHERE u.id IN (SELECT u2.id FROM ades_usuarios u2 JOIN ades_estudiantes est ON est.id = (" +
                "SELECT i.estudiante_id FROM ades_inscripciones i WHERE i.grupo_id = ? AND i.is_active = TRUE LIMIT 1))",
                UUID.class, grupoId);
    }

    @Override
    public List<UUID> getRecipientsForNivel(UUID nivelId) {
        return jdbc.queryForList(
                "SELECT DISTINCT u.id FROM ades_usuarios u WHERE u.nivel_educativo_id = ? LIMIT 500",
                UUID.class, nivelId);
    }

    @Override
    public List<UUID> getRecipientsForPlantel(UUID plantelId) {
        return jdbc.queryForList(
                "SELECT DISTINCT u.id FROM ades_usuarios u WHERE u.plantel_id = ? OR u.nivel_acceso <= 2 LIMIT 500",
                UUID.class, plantelId);
    }

    @Override
    public List<UUID> getAllRecipients() {
        return jdbc.queryForList("SELECT DISTINCT id FROM ades_usuarios LIMIT 500", UUID.class);
    }
}
