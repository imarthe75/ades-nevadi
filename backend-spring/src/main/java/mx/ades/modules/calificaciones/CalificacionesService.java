package mx.ades.modules.calificaciones;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CalificacionesService {

    private final CalificacionesPeriodoRepository repository;
    private final JdbcTemplate jdbc;

    @Transactional
    @CacheEvict(value = "boletas", key = "#estudianteId")
    public void calcularCalificacionPeriodo(UUID estudianteId, UUID inscripcionId, UUID materiaId, UUID periodoId) {
        try {
            jdbc.update("SELECT calcular_calificacion_periodo(?, ?, ?)", inscripcionId, materiaId, periodoId);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al calcular la calificación en BD: " + e.getMessage(), e);
        }
    }

    @Transactional
    @CacheEvict(value = "boletas", key = "#calificacion.estudianteId")
    public CalificacionesPeriodo guardarCalificacionManual(CalificacionesPeriodo calificacion) {
        return repository.save(calificacion);
    }

    @Cacheable(value = "boletas", key = "#estudianteId")
    public List<CalificacionesPeriodo> obtenerBoleta(UUID estudianteId) {
        return repository.findByEstudianteId(estudianteId);
    }
}
