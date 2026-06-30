package mx.ades.common;

import java.util.*;

/**
 * Utilidades de validación de datos para reportes y dashboards
 * 
 * Aplicado a: kardex, reportes, director_dashboard, monitor_sistema
 */
public class DataValidationUtils {
    
    /**
     * Valida que una colección tenga datos
     */
    public static <T> void validateNonEmpty(Collection<T> data, String resourceName) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("No hay datos disponibles para: " + resourceName);
        }
    }
    
    /**
     * Valida rango de valores numéricos
     */
    public static void validateRange(Number value, Number min, Number max, String fieldName) {
        if (value == null) return;
        double v = value.doubleValue();
        double minVal = min.doubleValue();
        double maxVal = max.doubleValue();
        
        if (v < minVal || v > maxVal) {
            throw new IllegalArgumentException(
                fieldName + " debe estar entre " + min + " y " + max + ", recibido: " + v);
        }
    }
    
    /**
     * Valida que un porcentaje sea válido (0-100)
     */
    public static void validatePercentage(Double value, String fieldName) {
        if (value != null) {
            validateRange(value, 0.0, 100.0, fieldName);
        }
    }
    
    /**
     * Sanitiza strings para evitar inyección
     */
    public static String sanitize(String input) {
        if (input == null) return null;
        return input
            .replaceAll("[<>\"'%;()&+]", "")
            .trim();
    }
}
