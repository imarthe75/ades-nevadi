import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import type { Estudiante } from '../../core/models';

export interface AlumnosListResponse {
  data: Estudiante[];
  total: number;
}

export interface CrearAlumnoPayload {
  persona: {
    nombre: string;
    apellido_paterno: string;
    apellido_materno?: string;
    curp: string;
    fecha_nacimiento?: string;
    genero?: string;
    email?: string;
    telefono?: string;
  };
  plantel_id?: string;
}

@Injectable({ providedIn: 'root' })
export class AlumnosService {
  private readonly api = inject(ApiService);

  listar(params: Record<string, string | undefined> = {}): Observable<AlumnosListResponse> {
    return this.api.get<AlumnosListResponse>('/alumnos', params);
  }

  obtener(id: string): Observable<Estudiante> {
    return this.api.get<Estudiante>(`/alumnos/${id}`);
  }

  crear(payload: CrearAlumnoPayload): Observable<Estudiante> {
    return this.api.post<Estudiante>('/alumnos', payload);
  }

  actualizar(id: string, cambios: Partial<Estudiante>): Observable<{ updated: boolean }> {
    return this.api.patch<{ updated: boolean }>(`/alumnos/${id}`, cambios);
  }

  eliminar(id: string): Observable<void> {
    return this.api.delete<void>(`/alumnos/${id}`);
  }
}
