package mx.ades.modules.asistencias;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AsistenciaService {

    private final AsistenciaRepository repository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void registrarAsistenciaMasiva(List<Asistencia> asistencias) {
        String sql = "INSERT INTO ades_asistencias (clase_id, estudiante_id, estatus_asistencia, observacion) " +
                     "VALUES (?, ?, ?, ?) " +
                     "ON CONFLICT (clase_id, estudiante_id) DO UPDATE SET " +
                     "  estatus_asistencia = EXCLUDED.estatus_asistencia, " +
                     "  observacion = EXCLUDED.observacion";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Asistencia item = asistencias.get(i);
                ps.setObject(1, item.getClaseId());
                ps.setObject(2, item.getEstudianteId());
                ps.setString(3, item.getEstatusAsistencia());
                ps.setString(4, item.getObservacion());
            }

            @Override
            public int getBatchSize() {
                return asistencias.size();
            }
        });
    }

    public List<Asistencia> listarPorClase(UUID claseId) {
        return repository.findByClaseId(claseId);
    }
}
