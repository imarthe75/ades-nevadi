package mx.ades.modules.expediente.domain.port.out;

import java.time.LocalDate;
import java.util.UUID;

public interface ConstanciaRepositoryPort {

    String generarFolio(String tipoConstancia);

    UUID guardar(UUID estudianteId, String tipoConstancia, String folio,
                 UUID cicloEscolarId, String solicitadaPor, String proposito,
                 LocalDate fechaVencimiento, UUID emitidaPorId);

    void marcarEntregada(UUID constanciaId);
}
