package mx.ades.common;

import java.util.*;

/**
 * Utilidades para flujos de aprobación en planeación, reportes, etc.
 * 
 * Estados de workflow:
 * BORRADOR -> EN_REVISION -> APROBADO / RECHAZADO
 */
public class WorkflowApprovalUtils {
    
    public enum ApprovalStatus {
        BORRADOR("Borrador", "info"),
        EN_REVISION("En revisión", "warning"),
        APROBADO("Aprobado", "success"),
        RECHAZADO("Rechazado", "danger");
        
        public final String label;
        public final String severity;
        
        ApprovalStatus(String label, String severity) {
            this.label = label;
            this.severity = severity;
        }
    }
    
    /**
     * Valida transición de estado
     */
    public static boolean canTransition(ApprovalStatus from, ApprovalStatus to) {
        if (from == to) return false;
        
        return switch (from) {
            case BORRADOR -> to == ApprovalStatus.EN_REVISION;
            case EN_REVISION -> to == ApprovalStatus.APROBADO || to == ApprovalStatus.RECHAZADO;
            case APROBADO -> false;
            case RECHAZADO -> to == ApprovalStatus.BORRADOR;
        };
    }
    
    /**
     * Obtiene el siguiente estado en el workflow
     */
    public static ApprovalStatus getNextStatus(ApprovalStatus current) {
        return switch (current) {
            case BORRADOR -> ApprovalStatus.EN_REVISION;
            case EN_REVISION -> ApprovalStatus.EN_REVISION; // esperar decisión
            case APROBADO, RECHAZADO -> current;
        };
    }
}
