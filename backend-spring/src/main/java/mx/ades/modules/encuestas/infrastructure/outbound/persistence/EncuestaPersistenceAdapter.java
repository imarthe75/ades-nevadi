package mx.ades.modules.encuestas.infrastructure.outbound.persistence;

import mx.ades.modules.encuestas.domain.port.out.EncuestaRespuestaRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
public class EncuestaPersistenceAdapter implements EncuestaRespuestaRepositoryPort {

    private final JdbcTemplate jdbc;

    public EncuestaPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public EncuestaEstado findEstado(UUID encuestaId) {
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT id, titulo, activa, anonima, plantel_id FROM ades_encuestas WHERE id = ? AND is_active = TRUE",
                    encuestaId);
            return new EncuestaEstado(
                    (UUID) row.get("id"),
                    (String) row.get("titulo"),
                    Boolean.TRUE.equals(row.get("activa")),
                    Boolean.TRUE.equals(row.get("anonima")),
                    (UUID) row.get("plantel_id"));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Encuesta no encontrada");
        }
    }

    @Override
    public boolean existeRespuesta(UUID preguntaId, String sesionId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_encuesta_respuestas WHERE pregunta_id = ? AND sesion_id = ?",
                Integer.class, preguntaId, sesionId);
        return count != null && count > 0;
    }

    @Override
    public void guardarRespuesta(RespuestaData data) {
        jdbc.update("""
                INSERT INTO ades_encuesta_respuestas
                    (encuesta_id, pregunta_id, respondido_por_id, sesion_id,
                     texto_respuesta, valor_numerico, opcion_seleccionada)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                data.encuestaId(), data.preguntaId(), data.usuarioId(), data.sesionId(),
                data.textoRespuesta(),
                data.valorNumerico() != null ? BigDecimal.valueOf(data.valorNumerico()) : null,
                data.opcionSeleccionada());
    }

    @Override
    public void crearAlertaBullying(UUID estudianteId, UUID plantelId, String texto,
                                    String encuestaTitulo, String sesionId) {
        String desc = String.format(
                "Alerta semántica de Acoso/Bullying detectada en Encuesta '%s' (Sesión: %s): '%s'",
                encuestaTitulo, sesionId, texto);
        jdbc.update("""
                INSERT INTO ades_alertas_cumplimiento
                    (tipo_alerta, descripcion, alumno_id, plantel_id, severidad,
                     requiere_accion, estado, usuario_creacion, usuario_modificacion)
                VALUES ('ACOSO_BULLYING', ?, ?, ?, 'CRITICA', TRUE, 'PENDIENTE', 'system_ia', 'system_ia')
                """, desc, estudianteId, plantelId);
    }

    @Override
    public UUID findEstudianteIdPorUsuario(UUID usuarioId) {
        try {
            return jdbc.queryForObject("""
                    SELECT e.id FROM ades_estudiantes e
                    JOIN ades_usuarios u ON u.persona_id = e.persona_id
                    WHERE u.id = ? LIMIT 1
                    """, UUID.class, usuarioId);
        } catch (Exception e) {
            return null;
        }
    }
}
