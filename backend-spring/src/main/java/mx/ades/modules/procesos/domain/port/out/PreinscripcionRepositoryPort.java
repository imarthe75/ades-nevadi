package mx.ades.modules.procesos.domain.port.out;

import mx.ades.modules.procesos.domain.port.in.ProcesarPreinscripcionUseCase;

import java.util.Optional;
import java.util.UUID;

public interface PreinscripcionRepositoryPort {

    record AdmisionData(String nombre, String apellidoPaterno, String curp) {}

    record GrupoCapacidad(int capacidadMaxima, int inscritos) {
        public boolean estaLleno() { return inscritos >= capacidadMaxima; }
    }

    Optional<AdmisionData> findAdmisionAceptada(UUID admisionId);

    Optional<GrupoCapacidad> findCapacidadGrupo(UUID grupoId);

    ProcesarPreinscripcionUseCase.PreinscripcionResult guardar(
            ProcesarPreinscripcionUseCase.Command command, AdmisionData admision);
}
