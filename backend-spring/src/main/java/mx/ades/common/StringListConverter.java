package mx.ades.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Conversor JPA que serializa/deserializa {@code List<String>} como texto JSON
 * en columnas {@code TEXT} de PostgreSQL.
 * <p>
 * Empleado en entidades donde se almacenan listas cortas de cadenas (etiquetas,
 * categorías, permisos ad-hoc) sin necesidad de una tabla de relación. Ante errores
 * de deserialización retorna lista vacía para garantizar robustez.
 * </p>
 *
 * @author ADES
 * @since 2026
 */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return mapper.readValue(dbData, mapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }
}
