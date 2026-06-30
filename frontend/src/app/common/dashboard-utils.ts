/**
 * Dashboard y Reportes - Utilities para renderizado de datos
 * 
 * Aplicado a: director_dashboard, reportes, monitor_sistema, kardex
 */

export interface DashboardData<T> {
  data: T[];
  total: number;
  periodo?: string;
  actualizado?: Date;
  metadata?: Record<string, any>;
}

export interface ReportMetrics {
  total: number;
  completado: number;
  pendiente: number;
  porcentaje: number;
  estado: 'completo' | 'incompleto' | 'vacío';
}

export class DashboardUtils {
  
  /**
   * Calcula métricas de completitud
   */
  static calculateMetrics(total: number, completed: number): ReportMetrics {
    const porcentaje = total > 0 ? Math.round((completed / total) * 100) : 0;
    return {
      total,
      completado: completed,
      pendiente: total - completed,
      porcentaje,
      estado: porcentaje === 100 ? 'completo' : porcentaje === 0 ? 'vacío' : 'incompleto'
    };
  }

  /**
   * Valida que datos existan antes de renderizar
   */
  static hasData<T>(data: any): data is T[] {
    return Array.isArray(data) && data.length > 0;
  }

  /**
   * Formatea valores monetarios
   */
  static formatCurrency(value: number): string {
    return new Intl.NumberFormat('es-MX', {
      style: 'currency',
      currency: 'MXN'
    }).format(value);
  }

  /**
   * Formatea porcentajes
   */
  static formatPercentage(value: number): string {
    return Math.round(value) + '%';
  }

  /**
   * Determina severidad de métrica
   */
  static getSeverity(porcentaje: number): 'success' | 'warning' | 'danger' {
    if (porcentaje >= 80) return 'success';
    if (porcentaje >= 50) return 'warning';
    return 'danger';
  }
}
