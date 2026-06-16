package mx.ades.modules.conducta.domain.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CrearPlanMejoraUseCase {

    record Command(
            UUID reporteId,
            UUID estudianteId,
            UUID cicloEscolarId,
            UUID elaboradoPorId,
            String objetivoGeneral,
            List<Map<String, Object>> compromisosAlumno,
            List<Map<String, Object>> compromisosPadre,
            List<Map<String, Object>> compromisosEscuela,
            LocalDate fechaPrimerSeguimiento,
            String username) {

        public Command {
            if (objetivoGeneral == null || objetivoGeneral.isBlank()) {
                throw new IllegalArgumentException("objetivo_general es obligatorio");
            }
            if (reporteId == null) {
                throw new IllegalArgumentException("reporte_id es obligatorio");
            }
            if (estudianteId == null) {
                throw new IllegalArgumentException("estudiante_id es obligatorio");
            }
        }
    }

    UUID ejecutar(Command command);
}
