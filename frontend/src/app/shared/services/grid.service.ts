/**
 * GridService — Servicio central para operaciones de grillas interactivas.
 * Maneja: sorting, filtering, paging, inline editing, conflict detection
 */

import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export interface GridState {
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
  filters: Map<string, string>;
  currentPage: number;
  pageSize: number;
  selectedRows: any[];
}

export interface EditEvent {
  row: any;
  field: string;
  oldValue: any;
  newValue: any;
  rowVersion: number;
}

export interface ConflictResolution {
  action: 'reload' | 'overwrite' | 'cancel';
  mergedData?: any;
}

@Injectable({
  providedIn: 'root',
})
export class GridService {
  private gridStateSubject = new BehaviorSubject<GridState>({
    filters: new Map(),
    currentPage: 0,
    pageSize: 20,
    selectedRows: [],
  });

  gridState$ = this.gridStateSubject.asObservable();

  /**
   * Aplicar filtro a columna específica.
   */
  setColumnFilter(column: string, value: string): void {
    const state = this.gridStateSubject.value;
    if (value.trim()) {
      state.filters.set(column, value.toLowerCase());
    } else {
      state.filters.delete(column);
    }
    this.gridStateSubject.next(state);
  }

  /**
   * Limpiar todos los filtros.
   */
  clearFilters(): void {
    const state = this.gridStateSubject.value;
    state.filters.clear();
    this.gridStateSubject.next(state);
  }

  /**
   * Establecer orden de columna.
   */
  setSortOrder(column: string, order: 'asc' | 'desc'): void {
    const state = this.gridStateSubject.value;
    state.sortBy = column;
    state.sortOrder = order;
    this.gridStateSubject.next(state);
  }

  /**
   * Cambiar página actual.
   */
  setPage(page: number): void {
    const state = this.gridStateSubject.value;
    state.currentPage = page;
    this.gridStateSubject.next(state);
  }

  /**
   * Establecer tamaño de página.
   */
  setPageSize(size: number): void {
    const state = this.gridStateSubject.value;
    state.pageSize = size;
    state.currentPage = 0;
    this.gridStateSubject.next(state);
  }

  /**
   * Marcar filas seleccionadas (para operaciones masivas).
   */
  selectRows(rows: any[]): void {
    const state = this.gridStateSubject.value;
    state.selectedRows = rows;
    this.gridStateSubject.next(state);
  }

  /**
   * Detectar conflicto de row_version en edición simultánea.
   * Retorna true si hay conflicto, false si no.
   */
  detectConflict(clientVersion: number, serverVersion: number): boolean {
    return clientVersion !== serverVersion;
  }

  /**
   * Formatear respuesta de conflicto para mostrar al usuario.
   */
  formatConflictMessage(clientVersion: number, serverVersion: number): string {
    return `Tu versión: ${clientVersion}, versión en servidor: ${serverVersion}. ` +
           'Otro usuario modificó este registro. ¿Deseas recargar los datos?';
  }

  /**
   * Estrategia de resolución: permitir al usuario elegir entre:
   *   - reload: cargar datos nuevos (pierde cambios locales)
   *   - overwrite: mantener cambios locales (sobrescribe otros)
   *   - cancel: cancelar edición
   */
  async resolveConflict(
    localData: any,
    serverData: any,
    strategy: 'reload' | 'overwrite' | 'merge' = 'reload'
  ): Promise<ConflictResolution> {
    switch (strategy) {
      case 'reload':
        return { action: 'reload', mergedData: serverData };
      case 'overwrite':
        return { action: 'overwrite', mergedData: localData };
      case 'merge':
        // Merge inteligente: cambios del usuario + campos actualizados del servidor
        const merged = { ...serverData };
        for (const key in localData) {
          if (localData[key] !== serverData[key]) {
            merged[key] = localData[key];
          }
        }
        return { action: 'overwrite', mergedData: merged };
      default:
        return { action: 'cancel' };
    }
  }

  /**
   * Validar integridad de datos después de edición.
   */
  validateEditEvent(event: EditEvent): { valid: boolean; errors: string[] } {
    const errors: string[] = [];

    if (!event.row || !event.field) {
      errors.push('Datos de edición incompletos');
    }

    if (event.newValue === undefined) {
      errors.push('Valor nuevo no especificado');
    }

    if (event.rowVersion === undefined) {
      errors.push('Versión de fila no especificada (requiere row_version)');
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }

  /**
   * Formatear valor para visualización según tipo de dato.
   */
  formatValue(value: any, type: string): string {
    switch (type) {
      case 'date':
        return new Date(value).toLocaleDateString('es-MX');
      case 'number':
        return Number(value).toFixed(2);
      case 'boolean':
        return value ? 'Sí' : 'No';
      default:
        return String(value || '');
    }
  }
}
