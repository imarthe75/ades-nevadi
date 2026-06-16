package mx.ades.modules.capacitaciones.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.capacitaciones.CapacitacionDocente;
import mx.ades.modules.capacitaciones.CapacitacionDocenteRepository;
import mx.ades.modules.capacitaciones.domain.port.out.CapacitacionRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CapacitacionPersistenceAdapter implements CapacitacionRepositoryPort {

    private final CapacitacionDocenteRepository jpa;
    private final JdbcTemplate jdbc;

    @Override
    public CapacitacionDocente save(CapacitacionDocente cap) {
        return jpa.save(cap);
    }

    @Override
    public Optional<CapacitacionDocente> findActiveById(UUID id) {
        return jpa.findById(id).filter(CapacitacionDocente::getIsActive);
    }

    @Override
    public List<Map<String, Object>> list(UUID docenteId, String tipo, String modalidad, Boolean validado, String q) {
        StringBuilder sq = new StringBuilder(
                "SELECT cd.*, " +
                "  CONCAT(pe.nombre, ' ', pe.apellido_paterno, " +
                "    CASE WHEN pe.apellido_materno IS NOT NULL THEN CONCAT(' ', pe.apellido_materno) ELSE '' END) AS nombre_docente, " +
                "  pe.nombre AS nombre_persona, pe.apellido_paterno, pe.apellido_materno, " +
                "  pr.numero_empleado " +
                "FROM ades_capacitaciones_docente cd " +
                "LEFT JOIN ades_profesores pr ON pr.id = cd.docente_id " +
                "LEFT JOIN ades_personas pe ON pe.id = pr.persona_id " +
                "WHERE cd.is_active = TRUE ");

        List<Object> params = new ArrayList<>();
        if (docenteId != null) { sq.append("AND cd.docente_id = ? "); params.add(docenteId); }
        if (q != null && !q.isBlank()) {
            sq.append("AND (pe.nombre ILIKE ? OR pe.apellido_paterno ILIKE ? OR CONCAT(pe.nombre,' ',pe.apellido_paterno) ILIKE ?) ");
            String like = "%" + q.trim() + "%";
            params.add(like); params.add(like); params.add(like);
        }
        if (tipo != null && !tipo.isBlank())     { sq.append("AND cd.tipo_certificacion = ? "); params.add(tipo.toUpperCase()); }
        if (modalidad != null && !modalidad.isBlank()) { sq.append("AND cd.modalidad = ? "); params.add(modalidad.toUpperCase()); }
        if (validado != null)                    { sq.append("AND cd.validado_rh = ? "); params.add(validado); }

        sq.append("ORDER BY cd.fecha_inicio DESC");
        return jdbc.queryForList(sq.toString(), params.toArray());
    }

    @Override
    public List<Map<String, Object>> resumen(UUID docenteId) {
        return jdbc.queryForList(
                "SELECT tipo_certificacion, modalidad, duracion_hrs, validado_rh " +
                "FROM ades_capacitaciones_docente WHERE docente_id = ? AND is_active = TRUE",
                docenteId);
    }
}
