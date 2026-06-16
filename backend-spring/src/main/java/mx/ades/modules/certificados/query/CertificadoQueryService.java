package mx.ades.modules.certificados.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CertificadoQueryService {

    private final JdbcTemplate jdbc;

    public CertificadoQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> listar(UUID estudianteId, String tipoCertificado, int limit) {
        StringBuilder sql = new StringBuilder(
            "SELECT c.id, c.folio, c.tipo_certificado, c.nivel_educativo, " +
            "c.grado_completado, c.promedio_final, " +
            "c.fecha_emision, c.fecha_vencimiento, c.vigente, " +
            "c.estado_firma, c.fecha_firma, c.verificable_url, " +
            "c.blockchain_tx, c.blockchain_status, c.fecha_anclaje, c.blockchain_network, " +
            "p.nombre || ' ' || p.apellido_paterno || COALESCE(' ' || p.apellido_materno, '') AS nombre_alumno, " +
            "ce.nombre_ciclo " +
            "FROM ades_certificados c " +
            "JOIN ades_estudiantes est ON est.id = c.estudiante_id " +
            "JOIN ades_personas p ON p.id = est.persona_id " +
            "JOIN ades_ciclos_escolares ce ON ce.id = c.ciclo_escolar_id " +
            "WHERE c.is_active = TRUE ");

        List<Object> params = new ArrayList<>();
        if (estudianteId != null) {
            sql.append("AND c.estudiante_id = ? ");
            params.add(estudianteId);
        }
        if (tipoCertificado != null && !tipoCertificado.isBlank()) {
            sql.append("AND c.tipo_certificado = ? ");
            params.add(tipoCertificado);
        }
        sql.append("ORDER BY c.fecha_emision DESC LIMIT ?");
        params.add(limit);

        return jdbc.queryForList(sql.toString(), params.toArray());
    }
}
