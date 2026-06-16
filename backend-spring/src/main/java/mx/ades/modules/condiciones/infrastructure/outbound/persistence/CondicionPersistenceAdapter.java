package mx.ades.modules.condiciones.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.condiciones.CondicionCronica;
import mx.ades.modules.condiciones.CondicionCronicaRepository;
import mx.ades.modules.condiciones.domain.port.out.CondicionRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CondicionPersistenceAdapter implements CondicionRepositoryPort {

    private final CondicionCronicaRepository jpa;
    private final JdbcTemplate jdbc;

    @Override
    public CondicionCronica save(CondicionCronica condicion) {
        return jpa.save(condicion);
    }

    @Override
    public Optional<CondicionCronica> findActiveById(UUID id) {
        return jpa.findById(id).filter(CondicionCronica::getIsActive);
    }

    @Override
    public List<Map<String, Object>> list(UUID alumnoId, String tipoCondicion, boolean soloActivas) {
        StringBuilder q = new StringBuilder(
                "SELECT c.id, c.alumno_id, e.numero_control, " +
                "p.nombre || ' ' || p.apellido_paterno AS alumno_nombre, " +
                "c.tipo_condicion, c.descripcion, c.medicacion_nombre, c.dosis, c.frecuencia, " +
                "c.alergias, c.medico_responsable, c.telefono_medico, c.activa, c.fecha_creacion " +
                "FROM ades_condiciones_cronicas c " +
                "JOIN ades_estudiantes e ON e.id = c.alumno_id " +
                "JOIN ades_personas p ON p.id = e.persona_id " +
                "WHERE c.is_active = TRUE ");

        List<Object> params = new ArrayList<>();
        if (alumnoId != null)                           { q.append("AND c.alumno_id = ? ");       params.add(alumnoId); }
        if (tipoCondicion != null && !tipoCondicion.isBlank()) { q.append("AND c.tipo_condicion = ? "); params.add(tipoCondicion.toUpperCase()); }
        if (soloActivas)                                { q.append("AND c.activa = TRUE "); }

        q.append("ORDER BY p.apellido_paterno, p.nombre, c.tipo_condicion");
        return jdbc.queryForList(q.toString(), params.toArray());
    }

    @Override
    public List<Map<String, Object>> alertaEmergencia(UUID alumnoId) {
        // Fix: ades_contactos_familiares uses telefono_principal (not telefono)
        String sql = "SELECT c.tipo_condicion, c.descripcion, c.medicacion_nombre, " +
                "c.dosis, c.frecuencia, c.alergias, c.medico_responsable, c.telefono_medico, " +
                "p.nombre || ' ' || p.apellido_paterno AS alumno_nombre, " +
                "e.numero_control, " +
                "cf.nombre_completo AS contacto_emergencia, " +
                "cf.telefono_principal AS tel_emergencia " +
                "FROM ades_condiciones_cronicas c " +
                "JOIN ades_estudiantes e ON e.id = c.alumno_id " +
                "JOIN ades_personas p ON p.id = e.persona_id " +
                "LEFT JOIN ades_contactos_familiares cf ON cf.estudiante_id = c.alumno_id " +
                "  AND cf.es_contacto_emergencia = TRUE AND cf.is_active = TRUE " +
                "WHERE c.alumno_id = ? AND c.activa = TRUE AND c.is_active = TRUE " +
                "ORDER BY c.tipo_condicion";
        return jdbc.queryForList(sql, alumnoId);
    }
}
