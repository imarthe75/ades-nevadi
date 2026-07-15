package mx.ades.modules.asistencias.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.asistencias.domain.model.Asistencia;
import mx.ades.modules.asistencias.domain.port.out.AsistenciaRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

/**
 * Adaptador de persistencia que implementa {@link AsistenciaRepositoryPort} accediendo
 * a la tabla {@code ades_asistencias} vía JPA y JDBC.
 *
 * <p>El método {@code guardarMasivo} utiliza {@code batchUpdate} con upsert para
 * garantizar idempotencia en registros duplicados de la misma clase y estudiante.</p>
 *
 * @author ADES
 * @since 2026
 */
@Component
@RequiredArgsConstructor
public class AsistenciaPersistenceAdapter implements AsistenciaRepositoryPort {

    private final AsistenciaJpaRepository jpaRepository;
    private final AsistenciaMapper mapper;
    private final JdbcTemplate jdbc;

    @Override
    public List<Asistencia> findByClaseId(UUID claseId) {
        return jpaRepository.findByClaseId(claseId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void guardarMasivo(List<Asistencia> asistencias) {
        if (asistencias.isEmpty()) return;

        String sql = """
                INSERT INTO ades_asistencias (clase_id, estudiante_id, estatus_asistencia, observacion)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (clase_id, estudiante_id) DO UPDATE
                SET estatus_asistencia = EXCLUDED.estatus_asistencia,
                    observacion        = EXCLUDED.observacion
                """;

        jdbc.batchUpdate(sql, asistencias, asistencias.size(),
                (PreparedStatement ps, Asistencia a) -> {
                    ps.setObject(1, a.claseId());
                    ps.setObject(2, a.estudianteId());
                    ps.setString(3, a.estatus().name());
                    ps.setString(4, a.observacion());
                });
    }

    @Override
    public long contarAsistenciasByEstudiante(UUID estudianteId, UUID grupoId) {
        String sql = """
                SELECT COUNT(*)
                FROM ades_asistencias  a
                JOIN ades_clases       c ON c.id = a.clase_id
                WHERE a.estudiante_id = ?
                  AND c.grupo_id      = ?
                  AND a.estatus_asistencia IN ('PRESENTE', 'TARDE', 'JUSTIFICADO')
                """;
        Long result = jdbc.queryForObject(sql, Long.class, estudianteId, grupoId);
        return result != null ? result : 0L;
    }
}
