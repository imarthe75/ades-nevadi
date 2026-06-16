package mx.ades.modules.licencias.domain.port.in;

import java.util.UUID;

public interface ResolverLicenciaUseCase {

    enum Accion { APROBAR, RECHAZAR }

    record Command(
            UUID licenciaId,
            Accion accion,
            String observaciones,
            UUID usuarioId,
            String usuarioNombre,
            int nivelAcceso
    ) {
        public Command {
            if (licenciaId == null)
                throw new IllegalArgumentException("licencia_id es requerido");
            if (accion == null)
                throw new IllegalArgumentException("accion es requerida");
            if (accion == Accion.RECHAZAR && (observaciones == null || observaciones.isBlank()))
                throw new IllegalArgumentException("observaciones (motivo_rechazo) es requerido al RECHAZAR");
        }
    }

    void resolver(Command cmd);
}
