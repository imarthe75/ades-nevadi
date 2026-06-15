package mx.ades.modules.asistencias;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClaseService {

    private final ClaseRepository claseRepository;
    private final JdbcTemplate jdbc;

    public List<Clase> listar(UUID grupoId, UUID materiaId, UUID profesorId,
                              LocalDate fechaDesde, LocalDate fechaHasta, String estatus) {
        if (grupoId != null && fechaDesde == null && fechaHasta == null && estatus == null
                && materiaId == null && profesorId == null) {
            return claseRepository.findByGrupoIdOrderByFechaClaseDescHoraInicioAsc(grupoId);
        }
        return claseRepository.findFiltered(grupoId, materiaId, profesorId, fechaDesde, fechaHasta,
                estatus != null ? estatus.toUpperCase() : null);
    }

    public Clase obtener(UUID id) {
        return claseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clase no encontrada"));
    }

    @Transactional
    public Clase crear(Clase clase) {
        return claseRepository.save(clase);
    }

    @Transactional
    public Clase actualizar(UUID id, Clase update) {
        Clase clase = obtener(id);
        if (update.getTemaVisto() != null) clase.setTemaVisto(update.getTemaVisto());
        if (update.getObservaciones() != null) clase.setObservaciones(update.getObservaciones());
        if (update.getEstatusClase() != null) clase.setEstatusClase(update.getEstatusClase());
        if (update.getHoraInicio() != null) clase.setHoraInicio(update.getHoraInicio());
        if (update.getHoraFin() != null) clase.setHoraFin(update.getHoraFin());
        return claseRepository.save(clase);
    }

    /** Alumnos inscritos en el grupo de la clase + su estado de asistencia si ya fue registrado. */
    public List<Map<String, Object>> alumnosEsperados(UUID claseId) {
        Clase clase = obtener(claseId);
        return jdbc.queryForList("""
            SELECT
                e.id::text            AS estudiante_id,
                e.matricula,
                p.nombre || ' ' || p.apellido_paterno || COALESCE(' ' || p.apellido_materno, '') AS nombre,
                a.estatus_asistencia  AS estatus,
                (a.id IS NOT NULL)    AS asistencia_registrada
            FROM ades_inscripciones i
            JOIN ades_estudiantes e  ON e.id = i.estudiante_id
            JOIN ades_personas    p  ON p.id = e.persona_id
            LEFT JOIN ades_asistencias a
                   ON a.clase_id = ?::uuid AND a.estudiante_id = e.id
            WHERE i.grupo_id   = ?::uuid
              AND i.is_active   = true
              AND e.is_active   = true
            ORDER BY p.apellido_paterno, p.apellido_materno, p.nombre
            """, claseId.toString(), clase.getGrupoId().toString());
    }
}
