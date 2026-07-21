package mx.ades.modules.entregas.application.service;

import mx.ades.modules.entregas.domain.port.in.CalificarEntregaUseCase;
import mx.ades.modules.entregas.domain.port.in.RegistrarExcusaUseCase;
import mx.ades.modules.entregas.domain.port.in.SubirEntregaUseCase;
import mx.ades.modules.entregas.domain.port.out.EntregaRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Caso de uso: gestión de entregas de tareas, proyectos y trabajos de alumnos.
 * Implementa {@link SubirEntregaUseCase}, {@link CalificarEntregaUseCase}
 * y {@link RegistrarExcusaUseCase} coordinando el dominio de entregas con
 * el puerto de repositorio para los tres niveles educativos del instituto.
 *
 * @author ADES
 * @since 2026
 */
public class EntregaApplicationService
        implements SubirEntregaUseCase, CalificarEntregaUseCase, RegistrarExcusaUseCase {

    private final EntregaRepositoryPort repository;

    public EntregaApplicationService(EntregaRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Map<String, Object> subir(SubirEntregaUseCase.Command cmd) {
        repository.upsertEntrega(cmd);
        return Map.of("message", "Entrega registrada", "archivo_url",
                cmd.archivoUrl() != null ? cmd.archivoUrl() : "");
    }

    @Override
    @Transactional
    public Map<String, Object> calificar(CalificarEntregaUseCase.Command cmd) {
        var resultado = repository.calificar(cmd);
        if (resultado.rows() == 0) throw new IllegalArgumentException("Entrega no encontrada: " + cmd.entregaId());
        // H-3: avisar explícitamente si se calificó una entrega que el alumno nunca subió.
        return resultado.sinEntrega()
                ? Map.of("message", "Calificación registrada", "sinEntrega", true)
                : Map.of("message", "Calificación registrada");
    }

    @Override
    @Transactional
    public Map<String, Object> registrar(RegistrarExcusaUseCase.Command cmd) {
        int updated = repository.registrarExcusa(cmd.entregaId(), cmd.motivo(), cmd.usuario());
        if (updated == 0) throw new IllegalArgumentException("Entrega no encontrada: " + cmd.entregaId());
        return Map.of("message", "Excusa registrada");
    }
}
