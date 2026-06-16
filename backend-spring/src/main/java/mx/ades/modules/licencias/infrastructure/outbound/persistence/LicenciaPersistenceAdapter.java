package mx.ades.modules.licencias.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.licencias.LicenciaPersonal;
import mx.ades.modules.licencias.LicenciaPersonalRepository;
import mx.ades.modules.licencias.domain.port.out.LicenciaRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LicenciaPersistenceAdapter implements LicenciaRepositoryPort {

    private final LicenciaPersonalRepository jpa;
    private final JdbcTemplate jdbc;

    @Override
    public LicenciaPersonal save(LicenciaPersonal licencia) {
        return jpa.save(licencia);
    }

    @Override
    public Optional<LicenciaPersonal> findActiveById(UUID id) {
        return jpa.findById(id).filter(LicenciaPersonal::getIsActive);
    }

    @Override
    public List<Map<String, Object>> list(UUID personalId, String estado, String tipo, String q) {
        StringBuilder sq = new StringBuilder(
                "SELECT lp.*, " +
                "  CONCAT(pe.nombre, ' ', pe.apellido_paterno, " +
                "    CASE WHEN pe.apellido_materno IS NOT NULL THEN CONCAT(' ', pe.apellido_materno) ELSE '' END) AS nombre_completo, " +
                "  pe.nombre AS nombre_persona, pe.apellido_paterno, pe.apellido_materno, " +
                "  pr.numero_empleado " +
                "FROM ades_licencias_personal lp " +
                "LEFT JOIN ades_profesores pr ON pr.id = lp.personal_id " +
                "LEFT JOIN ades_personas pe ON pe.id = pr.persona_id " +
                "WHERE lp.is_active = TRUE ");

        List<Object> params = new ArrayList<>();
        if (personalId != null) { sq.append("AND lp.personal_id = ? "); params.add(personalId); }
        if (q != null && !q.isBlank()) {
            sq.append("AND (pe.nombre ILIKE ? OR pe.apellido_paterno ILIKE ? OR CONCAT(pe.nombre,' ',pe.apellido_paterno) ILIKE ?) ");
            String like = "%" + q.trim() + "%";
            params.add(like); params.add(like); params.add(like);
        }
        if (estado != null && !estado.isBlank()) { sq.append("AND lp.estado = ? "); params.add(estado.toUpperCase()); }
        if (tipo != null && !tipo.isBlank())     { sq.append("AND lp.tipo_licencia = ? "); params.add(tipo.toUpperCase()); }

        sq.append("ORDER BY lp.fecha_creacion DESC");
        return jdbc.queryForList(sq.toString(), params.toArray());
    }
}
