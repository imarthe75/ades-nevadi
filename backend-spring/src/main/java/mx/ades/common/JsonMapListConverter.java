package mx.ades.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Conversor JPA que serializa/deserializa {@code List<Map<String, Object>>} como
 * texto JSON en columnas {@code TEXT} o {@code JSONB} de PostgreSQL.
 * <p>
 * Utilizado en entidades que almacenan estructuras de datos dinámicas (p.ej. campos
 * adicionales de formularios, configuraciones por plantel) sin requerir una tabla
 * separada. Ante errores de deserialización retorna lista vacía para evitar romper
 * la carga de entidades.
 * </p>
 *
 * @author ADES
 * @since 2026
 */
@Converter
public class JsonMapListConverter implements AttributeConverter<List<Map<String, Object>>, String> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<Map<String, Object>> attribute) {
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
    public List<Map<String, Object>> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return mapper.readValue(dbData, mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }
}
