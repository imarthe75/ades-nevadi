import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AgenteResidenteRequest {
  agente_id: string;
  contexto_sesion?: Record<string, any>;
}

export interface AgenteResidenteResponse {
  status: 'active' | 'degraded';
  valkey_connected: boolean;
  postgres_connected: boolean;
  mensaje: string;
}

@Injectable({
  providedIn: 'root'
})
export class ResidentAgentService {
  private readonly API_URL = '/api/v1/agente';

  constructor(private http: HttpClient) {}

  /**
   * Inicializa el agente residente en el backend y valida la conexión a la memoria dual.
   */
  initAgent(request: AgenteResidenteRequest): Observable<AgenteResidenteResponse> {
    return this.http.post<AgenteResidenteResponse>(`${this.API_URL}/init`, request);
  }
}
