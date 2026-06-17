package mx.ades.modules.alumnos.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.admin.query.AdminQueryService;
import mx.ades.modules.alumnos.Estudiante;
import mx.ades.modules.alumnos.EstudianteRepository;
import mx.ades.modules.alumnos.domain.port.out.AlumnoRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AlumnoPersistenceAdapter implements AlumnoRepositoryPort {

    private final EstudianteRepository jpa;
    private final JdbcTemplate         jdbc;
    private final AdminQueryService    adminQuery;

    @Override
    public Estudiante save(Estudiante estudiante) {
        return jpa.save(estudiante);
    }

    @Override
    public Optional<Estudiante> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public boolean existeByCurp(String curp) {
        return adminQuery.curpExiste(curp);
    }

    @Override
    public UUID resolverPrimerPlantelActivo() {
        return jdbc.queryForObject(
                "SELECT id FROM ades_planteles WHERE is_active = true ORDER BY fecha_creacion LIMIT 1",
                UUID.class);
    }

    @Override
    public String generarSiguienteMatricula() {
        Integer seq = jdbc.queryForObject(
                "SELECT COALESCE(MAX(CAST(REGEXP_REPLACE(matricula,'[^0-9]','','g') AS BIGINT)),0)::int + 1 " +
                "FROM ades_estudiantes WHERE matricula ~ '^MAT-[0-9]+$'",
                Integer.class);
        return String.format("MAT-%06d", seq == null ? 1 : seq);
    }
}
