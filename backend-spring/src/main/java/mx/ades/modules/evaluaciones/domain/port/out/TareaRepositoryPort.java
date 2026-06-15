package mx.ades.modules.evaluaciones.domain.port.out;

import mx.ades.modules.evaluaciones.domain.model.ItemCalificacion;
import mx.ades.modules.evaluaciones.domain.model.TipoItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TareaRepositoryPort {

    record TareaDatos(
            String titulo,
            String descripcion,
            UUID grupoId,
            UUID materiaId,
            UUID temaId,
            UUID periodoEvaluacionId,
            LocalDate fechaAsignacion,
            LocalDate fechaEntrega,
            BigDecimal puntajeMaximo,
            TipoItem tipoItem,
            boolean permiteEntregaTarde,
            String instruccionesUrl,
            String creadorUsername) {
    }

    UUID guardar(TareaDatos datos);

    int crearSlots(UUID tareaId, List<UUID> estudianteIds);

    List<UUID> findEstudiantesEnGrupo(UUID grupoId);

    Optional<BigDecimal> findPuntajeMaximo(UUID tareaId);

    int calificarItems(UUID tareaId, List<ItemCalificacion> items, UUID calificadorId);
}
