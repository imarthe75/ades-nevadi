package mx.ades.modules.asistencias.infrastructure.outbound.persistence;

import java.util.UUID;
import javax.annotation.processing.Generated;
import mx.ades.modules.asistencias.domain.model.Asistencia;
import mx.ades.modules.asistencias.domain.model.EstatusAsistencia;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-06-16T03:42:19+0000",
    comments = "version: 1.6.2, compiler: javac, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class AsistenciaMapperImpl implements AsistenciaMapper {

    @Override
    public Asistencia toDomain(AsistenciaEntity entity) {
        if ( entity == null ) {
            return null;
        }

        UUID id = null;
        UUID claseId = null;
        UUID estudianteId = null;
        String observacion = null;

        id = entity.getId();
        claseId = entity.getClaseId();
        estudianteId = entity.getEstudianteId();
        observacion = entity.getObservacion();

        EstatusAsistencia estatus = EstatusAsistencia.valueOf(entity.getEstatusAsistencia());

        Asistencia asistencia = new Asistencia( id, claseId, estudianteId, estatus, observacion );

        return asistencia;
    }

    @Override
    public AsistenciaEntity toEntity(Asistencia domain) {
        if ( domain == null ) {
            return null;
        }

        AsistenciaEntity asistenciaEntity = new AsistenciaEntity();

        asistenciaEntity.setId( domain.id() );
        asistenciaEntity.setClaseId( domain.claseId() );
        asistenciaEntity.setEstudianteId( domain.estudianteId() );
        asistenciaEntity.setObservacion( domain.observacion() );

        asistenciaEntity.setEstatusAsistencia( domain.estatus().name() );
        asistenciaEntity.setIsActive( true );

        return asistenciaEntity;
    }
}
