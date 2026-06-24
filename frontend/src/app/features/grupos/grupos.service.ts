import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import type { Grupo } from '../../core/models';

export interface CrearGrupoPayload {
  nombre_grupo: string;
  grado_id: string;
  ciclo_escolar_id: string;
  plantel_id: string;
  turno?: string;
  capacidad_maxima?: number;
}

/**
 * Servicio compartido para la gestión de grupos escolares.
 * Provee métodos HTTP para CRUD de grupos y soporta la cascada
 * Plantel→Nivel→Grado→Grupo consumida vía computed() signals en los componentes.
 */
@Injectable({ providedIn: 'root' })
export class GruposService {
  private readonly api = inject(ApiService);

  listar(params: Record<string, string | undefined> = {}): Observable<Grupo[]> {
    return this.api.get<Grupo[]>('/grupos', params);
  }

  obtener(id: string): Observable<Grupo> {
    return this.api.get<Grupo>(`/grupos/${id}`);
  }

  crear(payload: CrearGrupoPayload): Observable<Grupo> {
    return this.api.post<Grupo>('/admin/grupos', payload);
  }

  actualizar(id: string, cambios: Partial<Grupo>): Observable<{ updated: boolean }> {
    return this.api.patch<{ updated: boolean }>(`/admin/grupos/${id}`, cambios);
  }

  // Catálogos relacionados
  listarGrados(): Observable<any[]> {
    return this.api.get<any[]>('/catalogs/grados');
  }

  listarNivelesPlantel(plantelId: string): Observable<any[]> {
    return this.api.get<any[]>(`/planteles/${plantelId}/niveles`);
  }

  listarGradosPorNivel(nivelId: string): Observable<any[]> {
    return this.api.get<any[]>(`/catalogs/niveles/${nivelId}/grados`);
  }
}
