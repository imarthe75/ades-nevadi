package mx.ades.modules.movilidad.infrastructure.outbound.persistence;

import mx.ades.modules.movilidad.Baja;
import mx.ades.modules.movilidad.BajaRepository;
import mx.ades.modules.movilidad.CambioGrupo;
import mx.ades.modules.movilidad.CambioGrupoRepository;
import mx.ades.modules.movilidad.domain.port.out.MovilidadRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class MovilidadPersistenceAdapter implements MovilidadRepositoryPort {

    private final JdbcTemplate jdbc;
    private final CambioGrupoRepository cambioGrupoRepo;
    private final BajaRepository bajaRepo;

    public MovilidadPersistenceAdapter(JdbcTemplate jdbc,
                                        CambioGrupoRepository cambioGrupoRepo,
                                        BajaRepository bajaRepo) {
        this.jdbc = jdbc;
        this.cambioGrupoRepo = cambioGrupoRepo;
        this.bajaRepo = bajaRepo;
    }

    @Override
    public Optional<InscripcionActiva> findInscripcionActiva(UUID estudianteId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT i.id, i.grupo_id, i.ciclo_escolar_id, e.is_active AS est_activo,
                       g.nombre_grupo, g.plantel_id, g.capacidad_maxima,
                       (SELECT COUNT(*) FROM ades_inscripciones WHERE grupo_id = i.grupo_id AND is_active = TRUE) AS inscritos
                FROM ades_inscripciones i
                JOIN ades_grupos g ON g.id = i.grupo_id
                JOIN ades_estudiantes e ON e.id = i.estudiante_id
                WHERE i.estudiante_id = ? AND i.is_active = TRUE
                ORDER BY i.fecha_inscripcion DESC LIMIT 1
                """, estudianteId);
        if (rows.isEmpty()) return Optional.empty();
        var r = rows.get(0);
        return Optional.of(new InscripcionActiva(
                (UUID) r.get("id"),
                (UUID) r.get("grupo_id"),
                (UUID) r.get("ciclo_escolar_id"),
                (String) r.get("nombre_grupo"),
                (UUID) r.get("plantel_id"),
                ((Number) r.get("capacidad_maxima")).intValue(),
                ((Number) r.get("inscritos")).longValue(),
                Boolean.TRUE.equals(r.get("est_activo"))
        ));
    }

    @Override
    public Optional<GrupoInfo> findGrupo(UUID grupoId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, nombre_grupo, plantel_id, capacidad_maxima,
                       (SELECT COUNT(*) FROM ades_inscripciones WHERE grupo_id = ? AND is_active = TRUE) AS inscritos
                FROM ades_grupos WHERE id = ? AND is_active = TRUE
                """, grupoId, grupoId);
        if (rows.isEmpty()) return Optional.empty();
        var r = rows.get(0);
        return Optional.of(new GrupoInfo(
                (UUID) r.get("id"),
                (String) r.get("nombre_grupo"),
                (UUID) r.get("plantel_id"),
                ((Number) r.get("capacidad_maxima")).intValue(),
                ((Number) r.get("inscritos")).longValue()
        ));
    }

    @Override
    public Optional<UUID> findCicloVigente() {
        List<UUID> ids = jdbc.queryForList(
                "SELECT id FROM ades_ciclos_escolares WHERE es_vigente = TRUE LIMIT 1", UUID.class);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    @Override
    public UUID guardarCambioGrupo(UUID estudianteId, UUID inscripcionId,
                                    UUID grupoOrigenId, UUID grupoDestinoId,
                                    String motivo, UUID autorizadoPorId) {
        var cg = new CambioGrupo();
        cg.setEstudianteId(estudianteId);
        cg.setInscripcionId(inscripcionId);
        cg.setGrupoOrigenId(grupoOrigenId);
        cg.setGrupoDestinoId(grupoDestinoId);
        cg.setMotivo(motivo);
        cg.setAutorizadoPorId(autorizadoPorId);
        return cambioGrupoRepo.save(cg).getId();
    }

    @Override
    public void actualizarGrupoInscripcion(UUID inscripcionId, UUID grupoDestinoId, String usuario) {
        jdbc.update("UPDATE ades_inscripciones SET grupo_id = ?, usuario_modificacion = ? WHERE id = ?",
                grupoDestinoId, usuario, inscripcionId);
    }

    @Override
    public void desactivarInscripcion(UUID inscripcionId, String usuario) {
        jdbc.update("UPDATE ades_inscripciones SET is_active = FALSE, usuario_modificacion = ? WHERE id = ?",
                usuario, inscripcionId);
    }

    @Override
    public UUID guardarBaja(UUID estudianteId, UUID inscripcionId, String tipoBaja, String motivo,
                             LocalDate fechaEfectiva, LocalDate fechaReingreso,
                             String plantelDestino, String claveCtDestino,
                             String observaciones, UUID autorizadoPorId) {
        var b = new Baja();
        b.setEstudianteId(estudianteId);
        b.setInscripcionId(inscripcionId);
        b.setTipoBaja(tipoBaja);
        b.setMotivo(motivo);
        b.setFechaEfectiva(fechaEfectiva);
        b.setFechaReingreso(fechaReingreso);
        b.setPlantelDestino(plantelDestino);
        b.setClaveCtDestino(claveCtDestino);
        b.setObservaciones(observaciones);
        b.setAutorizadoPorId(autorizadoPorId);
        return bajaRepo.save(b).getId();
    }

    @Override
    public void desactivarEstudiante(UUID estudianteId, String usuario) {
        jdbc.update("UPDATE ades_estudiantes SET is_active = FALSE, usuario_modificacion = ? WHERE id = ?",
                usuario, estudianteId);
    }

    @Override
    public void activarEstudiante(UUID estudianteId, String usuario) {
        jdbc.update("UPDATE ades_estudiantes SET is_active = TRUE, usuario_modificacion = ? WHERE id = ?",
                usuario, estudianteId);
    }

    @Override
    public UUID crearInscripcion(UUID estudianteId, UUID grupoId, UUID cicloId, String usuario) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ades_inscripciones
                  (id, estudiante_id, grupo_id, ciclo_escolar_id, fecha_inscripcion,
                   usuario_creacion, usuario_modificacion)
                VALUES (?, ?, ?, ?, CURRENT_DATE, ?, ?)
                """, id, estudianteId, grupoId, cicloId, usuario, usuario);
        return id;
    }

    @Override
    public Optional<UUID> findActiveBajaTemporal(UUID estudianteId) {
        java.util.List<UUID> ids = jdbc.queryForList(
                "SELECT id FROM ades_bajas WHERE estudiante_id = ? AND tipo_baja = 'TEMPORAL' " +
                "AND is_active = TRUE ORDER BY fecha_efectiva DESC LIMIT 1", UUID.class, estudianteId);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    @Override
    public void cerrarBajaTemporal(UUID bajaId, String usuario) {
        jdbc.update("UPDATE ades_bajas SET is_active = FALSE, usuario_modificacion = ? WHERE id = ?",
                usuario, bajaId);
    }
}
