package mx.ades.modules.asistencias.domain.port.in;

import java.util.List;

public interface RegistrarAsistenciaMasivaUseCase {
    void ejecutar(List<RegistrarAsistenciaCommand> comandos, String usuarioCreacion);
}
