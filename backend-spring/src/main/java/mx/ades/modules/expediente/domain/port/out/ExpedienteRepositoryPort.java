package mx.ades.modules.expediente.domain.port.out;

import java.util.UUID;

public interface ExpedienteRepositoryPort {

    UUID findByEstudianteId(UUID estudianteId);

    void marcarVerificado(UUID expedienteId, String observaciones, UUID verificadoPorId);

    boolean documentoRequerido(UUID expedienteId, String tipoDocumento);
}
