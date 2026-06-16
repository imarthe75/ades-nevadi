package mx.ades.modules.movilidad.domain.port.out;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface MovilidadRepositoryPort {

    record InscripcionActiva(
            UUID id, UUID grupoId, UUID cicloEscolarId,
            String nombreGrupo, UUID plantelId,
            int capacidadMaxima, long inscritos, boolean estudianteActivo
    ) {}

    record GrupoInfo(
            UUID id, String nombreGrupo, UUID plantelId, int capacidadMaxima, long inscritos
    ) {
        public boolean estaLleno() { return inscritos >= capacidadMaxima; }
    }

    Optional<InscripcionActiva> findInscripcionActiva(UUID estudianteId);

    Optional<GrupoInfo> findGrupo(UUID grupoId);

    Optional<UUID> findCicloVigente();

    UUID guardarCambioGrupo(UUID estudianteId, UUID inscripcionId,
                            UUID grupoOrigenId, UUID grupoDestinoId,
                            String motivo, UUID autorizadoPorId);

    void actualizarGrupoInscripcion(UUID inscripcionId, UUID grupoDestinoId, String usuario);

    void desactivarInscripcion(UUID inscripcionId, String usuario);

    UUID guardarBaja(UUID estudianteId, UUID inscripcionId, String tipoBaja, String motivo,
                     LocalDate fechaEfectiva, LocalDate fechaReingreso,
                     String plantelDestino, String claveCtDestino,
                     String observaciones, UUID autorizadoPorId);

    void desactivarEstudiante(UUID estudianteId, String usuario);

    void activarEstudiante(UUID estudianteId, String usuario);

    UUID crearInscripcion(UUID estudianteId, UUID grupoId, UUID cicloId, String usuario);

    Optional<UUID> findActiveBajaTemporal(UUID estudianteId);

    void cerrarBajaTemporal(UUID bajaId, String usuario);
}
