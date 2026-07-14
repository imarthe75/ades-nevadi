package mx.ades.modules.asistencias;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.planeacion.command.PlaneacionCommandService;
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
    private final PlaneacionCommandService planeacionCommands;

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

    private static final java.util.Set<String> MODALIDADES_VALIDAS =
            java.util.Set.of("PRESENCIAL", "REMOTA", "HIBRIDA");

    private static void validarModalidad(String modalidad) {
        if (modalidad != null && !MODALIDADES_VALIDAS.contains(modalidad.toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "modalidad inválida: debe ser PRESENCIAL, REMOTA o HIBRIDA");
        }
    }

    /**
     * Valida las columnas NOT NULL sin default de {@code ades_clases} (grupo_id,
     * materia_id, profesor_id, fecha_clase, hora_inicio, hora_fin) y el CHECK
     * {@code ades_clases_modalidad_check} (PRESENCIAL/REMOTA/HIBRIDA) ANTES de
     * persistir — el controller ({@code ClaseController#crear}) hace bind directo
     * del JSON al entity JPA sin ninguna anotación de validación, por lo que sin
     * este chequeo cualquier campo faltante caía directo en
     * DataIntegrityViolationException -> 409 engañoso.
     */
    @Transactional
    public Clase crear(Clase clase) {
        if (clase.getGrupoId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "grupoId es obligatorio");
        if (clase.getMateriaId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "materiaId es obligatorio");
        if (clase.getProfesorId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "profesorId es obligatorio");
        if (clase.getFechaClase() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fechaClase es obligatoria");
        if (clase.getHoraInicio() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "horaInicio es obligatoria");
        if (clase.getHoraFin() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "horaFin es obligatoria");
        validarModalidad(clase.getModalidad());
        return claseRepository.save(clase);
    }

    @Transactional
    public Clase actualizar(UUID id, Clase update) {
        Clase clase = obtener(id);
        if (update.getTemaVisto() != null) clase.setTemaVisto(update.getTemaVisto());
        if (update.getObservaciones() != null) clase.setObservaciones(update.getObservaciones());
        boolean seSuspendio = update.getEstatusClase() != null
                && "SUSPENDIDA".equalsIgnoreCase(update.getEstatusClase())
                && !"SUSPENDIDA".equalsIgnoreCase(clase.getEstatusClase());
        if (update.getEstatusClase() != null) clase.setEstatusClase(update.getEstatusClase());
        if (update.getModalidad() != null) {
            validarModalidad(update.getModalidad());
            clase.setModalidad(update.getModalidad());
        }
        if (update.getHoraInicio() != null) clase.setHoraInicio(update.getHoraInicio());
        if (update.getHoraFin() != null) clase.setHoraFin(update.getHoraFin());
        Clase saved = claseRepository.save(clase);

        // OA-012: al suspender una clase, marcar los temas planeados de ese día para reprogramar.
        if (seSuspendio) {
            planeacionCommands.marcarPendientesPorSuspension(saved.getGrupoId(), saved.getFechaClase());
        }
        return saved;
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
