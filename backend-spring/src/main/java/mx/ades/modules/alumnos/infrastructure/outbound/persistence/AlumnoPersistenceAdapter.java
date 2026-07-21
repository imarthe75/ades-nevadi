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

/**
 * Adaptador de persistencia que implementa {@link AlumnoRepositoryPort} accediendo
 * a la tabla {@code ades_estudiantes} vía JPA ({@code EstudianteRepository}) y JDBC
 * para operaciones específicas como generación de matrícula y resolución de plantel.
 *
 * @author ADES
 * @since 2026
 */
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
        // H-5 (auditoría 2026-07-20): MAX(...)+1 no es atómico — dos altas concurrentes
        // pueden leer el mismo MAX y generar la misma matrícula. nextval() de una
        // secuencia de Postgres sí lo es, sin importar la concurrencia.
        Long seq = jdbc.queryForObject("SELECT nextval('ades_estudiantes_matricula_seq')", Long.class);
        return String.format("MAT-%06d", seq);
    }
}
