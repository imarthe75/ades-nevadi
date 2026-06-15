package mx.ades.modules.asistencias.application.service;

import mx.ades.modules.asistencias.domain.model.Asistencia;
import mx.ades.modules.asistencias.domain.port.in.ConsultarAsistenciasPorClaseUseCase;
import mx.ades.modules.asistencias.domain.port.in.RegistrarAsistenciaCommand;
import mx.ades.modules.asistencias.domain.port.in.RegistrarAsistenciaMasivaUseCase;
import mx.ades.modules.asistencias.domain.port.out.AsistenciaRepositoryPort;

import java.util.List;
import java.util.UUID;

/**
 * Servicio de aplicación — sin @Service, se registra como @Bean en HexagonalConfig.
 * No depende de Spring ni JPA: solo orquesta dominio y puertos.
 */
public class RegistrarAsistenciaMasivaService
        implements RegistrarAsistenciaMasivaUseCase, ConsultarAsistenciasPorClaseUseCase {

    private final AsistenciaRepositoryPort repository;

    public RegistrarAsistenciaMasivaService(AsistenciaRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public void ejecutar(List<RegistrarAsistenciaCommand> comandos, String usuarioCreacion) {
        if (comandos == null || comandos.isEmpty()) return;

        List<Asistencia> asistencias = comandos.stream()
                .map(cmd -> Asistencia.registrar(
                        cmd.claseId(),
                        cmd.estudianteId(),
                        cmd.estatus(),
                        cmd.observacion()))
                .toList();

        repository.guardarMasivo(asistencias);
    }

    @Override
    public List<Asistencia> ejecutar(UUID claseId) {
        return repository.findByClaseId(claseId);
    }
}
