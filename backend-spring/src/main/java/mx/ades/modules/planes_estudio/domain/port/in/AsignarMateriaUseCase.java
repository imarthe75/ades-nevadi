package mx.ades.modules.planes_estudio.domain.port.in;

import java.util.UUID;

public interface AsignarMateriaUseCase {

    record Command(UUID materiaId, UUID gradoId, UUID cicloEscolarId, Double horasSemana, Boolean esObligatoria) {
        public Command {
            if (materiaId == null) throw new IllegalArgumentException("materia_id es requerido");
            if (gradoId == null) throw new IllegalArgumentException("grado_id es requerido");
            if (cicloEscolarId == null) throw new IllegalArgumentException("ciclo_escolar_id es requerido");
            if (horasSemana == null) horasSemana = 0.0;
            if (esObligatoria == null) esObligatoria = true;
        }
    }

    UUID asignar(Command cmd);
}
