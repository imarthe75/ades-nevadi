package mx.ades.modules.badges.domain.port.out;

import mx.ades.modules.badges.Badge;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida: define el contrato de persistencia para badges y su otorgamiento
 * a alumnos en las tablas {@code ades_badges} y {@code ades_badge_otorgados}.
 *
 * <p>Incluye consultas de elegibilidad por métrica (asistencia, promedio, sin reportes)
 * y operaciones de otorgamiento individual y masivo (bulk).</p>
 *
 * @author ADES
 * @since 2026
 */
public interface BadgeRepositoryPort {
    Badge save(Badge badge);
    Optional<Badge> findById(UUID id);
    List<Badge> findAllAutomatic();
    List<Map<String, Object>> list(String tipo, UUID plantelId);
    Map<String, Object> detalle(UUID badgeId);
    List<Map<String, Object>> badgesAlumno(UUID estudianteId, UUID cicloId);
    boolean otorgar(UUID badgeId, UUID estudianteId, UUID cicloId, String motivo, UUID otorgadoPor);
    void revocar(UUID badgeId, UUID estudianteId, UUID cicloId);
    List<UUID> eligiblesByPctAsistencia(UUID cicloId, double umbral);
    List<UUID> eligiblesByPromedioGeneral(UUID cicloId, double umbral);
    List<UUID> eligiblesBySinReportes(UUID cicloId);
    int otorgarBulk(UUID badgeId, UUID cicloId, List<UUID> estudianteIds);
}
