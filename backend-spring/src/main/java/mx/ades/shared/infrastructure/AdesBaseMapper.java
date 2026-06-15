package mx.ades.shared.infrastructure;

import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

/**
 * Configuración base para todos los mappers MapStruct de ADES.
 * Los mappers concretos extienden con: @Mapper(config = AdesBaseMapper.class)
 *
 * componentModel="spring" → el processor ya está configurado globalmente en pom.xml,
 * pero se declara aquí también como documentación explícita.
 */
@MapperConfig(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface AdesBaseMapper {
}
