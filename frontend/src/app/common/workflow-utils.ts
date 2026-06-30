/**
 * Workflow Approval utilities para planeación, reportes, etc.
 */

export enum ApprovalStatus {
  BORRADOR = 'BORRADOR',
  EN_REVISION = 'EN_REVISION',
  APROBADO = 'APROBADO',
  RECHAZADO = 'RECHAZADO'
}

export class WorkflowUtils {
  
  private static readonly LABELS: Record<ApprovalStatus, string> = {
    [ApprovalStatus.BORRADOR]: 'Borrador',
    [ApprovalStatus.EN_REVISION]: 'En revisión',
    [ApprovalStatus.APROBADO]: 'Aprobado',
    [ApprovalStatus.RECHAZADO]: 'Rechazado'
  };

  private static readonly SEVERITIES: Record<ApprovalStatus, 'success' | 'warning' | 'danger' | 'info'> = {
    [ApprovalStatus.BORRADOR]: 'info',
    [ApprovalStatus.EN_REVISION]: 'warning',
    [ApprovalStatus.APROBADO]: 'success',
    [ApprovalStatus.RECHAZADO]: 'danger'
  };

  /**
   * Obtiene label de estado
   */
  static getLabel(status: ApprovalStatus): string {
    return this.LABELS[status] || status;
  }

  /**
   * Obtiene severidad visual
   */
  static getSeverity(status: ApprovalStatus): 'success' | 'warning' | 'danger' | 'info' {
    return this.SEVERITIES[status];
  }

  /**
   * Valida si se puede transicionar
   */
  static canTransition(from: ApprovalStatus, to: ApprovalStatus): boolean {
    if (from === to) return false;
    
    const validTransitions: Record<ApprovalStatus, ApprovalStatus[]> = {
      [ApprovalStatus.BORRADOR]: [ApprovalStatus.EN_REVISION],
      [ApprovalStatus.EN_REVISION]: [ApprovalStatus.APROBADO, ApprovalStatus.RECHAZADO],
      [ApprovalStatus.APROBADO]: [],
      [ApprovalStatus.RECHAZADO]: [ApprovalStatus.BORRADOR]
    };

    return validTransitions[from]?.includes(to) ?? false;
  }

  /**
   * Obtiene estados permitidos desde el estado actual
   */
  static getAllowedTransitions(current: ApprovalStatus): ApprovalStatus[] {
    const allowed: Record<ApprovalStatus, ApprovalStatus[]> = {
      [ApprovalStatus.BORRADOR]: [ApprovalStatus.EN_REVISION],
      [ApprovalStatus.EN_REVISION]: [ApprovalStatus.APROBADO, ApprovalStatus.RECHAZADO],
      [ApprovalStatus.APROBADO]: [],
      [ApprovalStatus.RECHAZADO]: [ApprovalStatus.BORRADOR]
    };
    return allowed[current] || [];
  }
}
