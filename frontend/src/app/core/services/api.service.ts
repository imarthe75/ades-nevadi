/**
 * ApiService — cliente HTTP base para la API ADES.
 * Adjunta el JWT automáticamente via AuthInterceptor.
 */
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, shareReplay } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1`;

  /**
   * Cache de catálogos sin parámetros (misma URL ⇒ mismo resultado durante la sesión).
   * Auditoría 2026-07-20: varios componentes (rúbricas, calificaciones) pedían
   * GET /materias por separado cada vez que se montaban, sin ningún cacheo — el mismo
   * catálogo (rara vez cambia dentro de una sesión) viajaba por red repetidas veces.
   * `shareReplay(1)` deja el último valor disponible para cualquier suscriptor nuevo sin
   * volver a pegarle al backend. Solo apto para GETs SIN parámetros variables — una
   * clave por URL exacta; no usar con params que cambien el resultado (usar `get()`
   * normal en ese caso, o extender la clave de caché a incluir los params si hace falta).
   */
  private readonly catalogCache = new Map<string, Observable<unknown>>();

  getCached<T>(path: string): Observable<T> {
    let cached = this.catalogCache.get(path) as Observable<T> | undefined;
    if (!cached) {
      cached = this.http.get<T>(`${this.base}${path}`).pipe(shareReplay({ bufferSize: 1, refCount: false }));
      this.catalogCache.set(path, cached);
    }
    return cached;
  }

  get<T>(path: string, params?: Record<string, string | number | boolean | undefined>): Observable<T> {
    let httpParams = new HttpParams();
    if (params) {
      Object.entries(params).forEach(([k, v]) => {
        if (v !== undefined && v !== null) httpParams = httpParams.set(k, String(v));
      });
    }
    return this.http.get<T>(`${this.base}${path}`, { params: httpParams });
  }

  post<T>(path: string, body: unknown): Observable<T> {
    return this.http.post<T>(`${this.base}${path}`, body);
  }

  put<T>(path: string, body: unknown): Observable<T> {
    return this.http.put<T>(`${this.base}${path}`, body);
  }

  patch<T>(path: string, body: unknown): Observable<T> {
    return this.http.patch<T>(`${this.base}${path}`, body);
  }

  delete<T>(path: string): Observable<T> {
    return this.http.delete<T>(`${this.base}${path}`);
  }

  upload<T>(path: string, formData: FormData): Observable<T> {
    return this.http.post<T>(`${this.base}${path}`, formData);
  }

  postFormData<T>(path: string, formData: FormData): Observable<T> {
    return this.http.post<T>(`${this.base}${path}`, formData);
  }

  getBlob(path: string, params?: Record<string, string | number | undefined>): Observable<Blob> {
    let httpParams = new HttpParams();
    if (params) {
      Object.entries(params).forEach(([k, v]) => {
        if (v !== undefined && v !== null) httpParams = httpParams.set(k, String(v));
      });
    }
    return this.http.get(`${this.base}${path}`, { params: httpParams, responseType: 'blob' });
  }

  postBlob(path: string, body: unknown): Observable<Blob> {
    return this.http.post(`${this.base}${path}`, body, { responseType: 'blob' });
  }

  /** GET a ruta absoluta (sin /api/v1 prefix) — para endpoints públicos del portal */
  getAbs<T>(path: string, params?: Record<string, string | number | boolean | undefined>): Observable<T> {
    let httpParams = new HttpParams();
    if (params) {
      Object.entries(params).forEach(([k, v]) => {
        if (v !== undefined && v !== null) httpParams = httpParams.set(k, String(v));
      });
    }
    return this.http.get<T>(`${environment.apiUrl}${path}`, { params: httpParams });
  }

  /** POST multipart/form-data */
  postForm<T>(path: string, formData: FormData): Observable<T> {
    return this.http.post<T>(`${this.base}${path}`, formData);
  }
}
