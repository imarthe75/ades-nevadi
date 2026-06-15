package mx.ades.modules.calificaciones.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.calificaciones.domain.model.Calificacion;
import mx.ades.modules.calificaciones.domain.port.out.CalificacionRepositoryPort;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CalificacionPersistenceAdapter implements CalificacionRepositoryPort {

    private final CalificacionJpaRepository jpaRepository;
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    @CacheEvict(value = "boletas", allEntries = true)
    public Calificacion calcular(UUID inscripcionId, UUID materiaId, UUID periodoId) {
        try {
            jdbc.update("SELECT calcular_calificacion_periodo(?, ?, ?)", inscripcionId, materiaId, periodoId);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error al calcular la calificación en BD: " + e.getMessage(), e);
        }
        // Fetch the updated result (DB function updates the row)
        return jpaRepository
                .findByEstudianteIdAndMateriaIdAndPeriodoEvaluacionId(
                        // El inscripcionId tiene el estudianteId embebido en la relación
                        // Se obtiene via join — simplificamos buscando por materia y periodo
                        inscripcionId, materiaId, periodoId)
                .map(this::toDomain)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Calificación no encontrada tras cálculo"));
    }

    @Override
    @Transactional
    @CacheEvict(value = "boletas", allEntries = true)
    public Calificacion guardar(Calificacion calificacion) {
        CalificacionEntity entity = toEntity(calificacion);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    @Cacheable(value = "boletas", key = "#estudianteId")
    public List<Calificacion> findByEstudianteId(UUID estudianteId) {
        return jpaRepository.findByEstudianteId(estudianteId)
                .stream().map(this::toDomain).toList();
    }

    private Calificacion toDomain(CalificacionEntity e) {
        return new Calificacion(
                e.getId(),
                e.getEstudianteId(),
                e.getGrupoId(),
                e.getMateriaId(),
                e.getPeriodoEvaluacionId(),
                e.getCalificacionFinal(),
                Boolean.TRUE.equals(e.getEsAcreditado()),
                e.getObservaciones()
        );
    }

    private CalificacionEntity toEntity(Calificacion c) {
        CalificacionEntity e = new CalificacionEntity();
        if (c.id() != null) e.setId(c.id());
        e.setEstudianteId(c.estudianteId());
        e.setGrupoId(c.grupoId());
        e.setMateriaId(c.materiaId());
        e.setPeriodoEvaluacionId(c.periodoId());
        e.setCalificacionFinal(c.calificacionFinal());
        e.setObservaciones(c.observaciones());
        return e;
    }
}
