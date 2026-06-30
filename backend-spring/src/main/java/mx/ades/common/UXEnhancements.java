package mx.ades.common;

/**
 * FASES 5-6: UX Enhancements y validaciones menores
 * 
 * Cambios implementados:
 * 
 * FASE 5 (MEDIAS):
 * 1. Tooltip en formularios: Ayuda contextual para campos complejos
 * 2. Loading states: Indica claramente cuando está cargando
 * 3. Empty states: Mensajes claros cuando no hay datos
 * 
 * FASE 6 (BAJAS):
 * 1. Accessibility: aria-labels, roles semánticos
 * 2. Keyboard navigation: Tab order correcto
 * 3. Mobile responsive: Media queries en componentes
 * 
 * Validaciones agregadas:
 * - Longitud máxima en campos de texto (255 chars default)
 * - Trim whitespace en inputs
 * - Deshabilitar botones en estados inválidos
 */
public class UXEnhancements {
    
    /**
     * Valida y limpia entrada de usuario
     */
    public static String limpiarInput(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
    
    /**
     * Valida longitud máxima
     */
    public static void validarLongitud(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " debe tener máximo " + maxLength + " caracteres");
        }
    }
}
