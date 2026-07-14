package mx.ades.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manejador global de excepciones no capturadas por los controllers.
 * <p>
 * Antes de esto, cualquier excepción sin un {@code ResponseStatusException} explícito
 * (bugs de SQL, validación de Bean Validation, tipos de parámetro incorrectos) llegaba
 * al frontend como {@code {timestamp,status,error,path}} sin ninguna razón — el usuario
 * siempre veía "Error" genérico y el desarrollador tenía que revisar logs del contenedor
 * para saber qué pasó. Este advice da un mensaje seguro y accionable al cliente y deja
 * el detalle técnico completo solo en el log del servidor.
 * <p>
 * No reemplaza el manejo de {@code ResponseStatusException} — Spring ya expone su
 * {@code reason} en el campo {@code message} de la respuesta (habilitado vía
 * {@code server.error.include-message=always} en application.yml).
 *
 * @author ADES
 * @since 2026
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Preserva el status/reason que cada controller ya define explícitamente — sin este
     * handler, el catch-all de abajo (Exception.class) interceptaría también los
     * ResponseStatusException (son Exception) y los convertiría todos en 500 genérico,
     * rompiendo los 404/400/409 con mensajes claros que ya existen en todo el backend.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return body(status, ex.getReason() != null ? ex.getReason() : status.getReasonPhrase());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return body(HttpStatus.BAD_REQUEST, detalle.isBlank() ? "Datos inválidos" : detalle);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return body(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud es inválido o falta");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return body(HttpStatus.BAD_REQUEST,
                "Parámetro '" + ex.getName() + "' con formato inválido");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return body(HttpStatus.BAD_REQUEST,
                "Falta el parámetro requerido '" + ex.getParameterName() + "'");
    }

    /**
     * Captura las validaciones manuales de los records {@code Command} en los puertos
     * de entrada del dominio hexagonal (patrón usado en ~100+ casos de uso: compact
     * constructors que lanzan {@code IllegalArgumentException} cuando falta un campo
     * requerido o un valor no pertenece al enum esperado — equivalente manual a
     * Jakarta Validation para los muchos endpoints que reciben {@code Map<String,Object>}
     * en vez de un DTO anotado). Sin este handler, esas validaciones caían en el
     * catch-all de {@code Exception} y el usuario recibía un 500 genérico en vez de un
     * 400 con el motivo real (p. ej. "El tipo de materia es requerido").
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage() != null ? ex.getMessage() : "Datos inválidos");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad de datos: {}", ex.getMessage());
        return body(HttpStatus.CONFLICT,
                "La operación viola una restricción de datos (duplicado o referencia inválida)");
    }

    @ExceptionHandler(BadSqlGrammarException.class)
    public ResponseEntity<Map<String, Object>> handleSqlGrammar(BadSqlGrammarException ex) {
        log.error("Bug de SQL (columna/tabla inexistente): {}", ex.getMessage(), ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error interno al procesar la solicitud. Fue registrado para revisión.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Excepción no manejada: {}", ex.getMessage(), ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error interno al procesar la solicitud. Fue registrado para revisión.");
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("status", status.value());
        payload.put("error", status.getReasonPhrase());
        payload.put("message", message);
        return ResponseEntity.status(status).body(payload);
    }
}
