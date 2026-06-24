package mx.ades.modules.asistencias.infrastructure.outbound.persistence;

import mx.ades.modules.asistencias.domain.model.Asistencia;
import mx.ades.modules.asistencias.domain.model.EstatusAsistencia;
import mx.ades.shared.infrastructure.AdesBaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct que convierte entre {@link AsistenciaEntity} y el objeto de dominio
 * {@link mx.ades.modules.asistencias.domain.model.Asistencia}.
 *
 * @author ADES
 * @since 2026
 */
@Mapper(config = AdesBaseMapper.class)
public interface AsistenciaMapper {

    @Mapping(target = "estatus",
             expression = "java(EstatusAsistencia.valueOf(entity.getEstatusAsistencia()))")
    Asistencia toDomain(AsistenciaEntity entity);

    @Mapping(target = "estatusAsistencia", expression = "java(domain.estatus().name())")
    @Mapping(target = "isActive",          constant = "true")
    @Mapping(target = "id",                source = "domain.id")
    @Mapping(target = "rowVersion",        ignore = true)
    @Mapping(target = "fechaCreacion",     ignore = true)
    @Mapping(target = "fechaModificacion", ignore = true)
    @Mapping(target = "usuarioCreacion",   ignore = true)
    @Mapping(target = "usuarioModificacion", ignore = true)
    @Mapping(target = "ref",               ignore = true)
    AsistenciaEntity toEntity(Asistencia domain);
}
