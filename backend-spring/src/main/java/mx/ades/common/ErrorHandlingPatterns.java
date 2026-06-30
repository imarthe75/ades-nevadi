package mx.ades.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Patrones de error handling para consistencia
 * 
 * Aplicado a: horarios, monitor_sistema, certificados, acta_evaluacion
 */
public class ErrorHandlingPatterns {
    
    /**
     * Error: No data found
     */
    public static ResponseStatusException noDataFound(String resource) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
            "No hay datos disponibles para: " + resource);
    }
    
    /**
     * Error: Operation failed
     */
    public static ResponseStatusException operationFailed(String operation, Throwable cause) {
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
            "Error al " + operation + ": " + (cause != null ? cause.getMessage() : "Error desconocido"));
    }
    
    /**
     * Error: Invalid request
     */
    public static ResponseStatusException invalidRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
    
    /**
     * Error: Unauthorized access
     */
    public static ResponseStatusException unauthorizedAccess(String resource) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN,
            "No tienes permisos para acceder a: " + resource);
    }
}
