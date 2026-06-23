import { Injectable, inject, signal, computed } from '@angular/core';
import { ApiService } from './api.service';
import { tap } from 'rxjs/operators';
import { Observable, of } from 'rxjs';

export interface ModuloPermisos {
  puedeVer: boolean;
  puedeEditar: boolean;
  puedeCrear: boolean;
  puedeEliminar: boolean;
}

const DEFAULT_PERMISOS: ModuloPermisos = {
  puedeVer: true,
  puedeEditar: true,
  puedeCrear: true,
  puedeEliminar: false,
};

const READ_ONLY: ModuloPermisos = {
  puedeVer: true,
  puedeEditar: false,
  puedeCrear: false,
  puedeEliminar: false,
};

@Injectable({ providedIn: 'root' })
export class PermisosService {
  private readonly api = inject(ApiService);

  private readonly _permisos = signal<Record<string, ModuloPermisos>>({});
  private _loaded = false;

  readonly permisos = this._permisos.asReadonly();

  loadPermisos(): Observable<void> {
    if (this._loaded) return of(void 0);
    return this.api.get<Record<string, ModuloPermisos>>('/menus/mis-permisos').pipe(
      tap(data => {
        this._permisos.set(data);
        this._loaded = true;
      }),
    ) as unknown as Observable<void>;
  }

  /** Get permissions for a module clave, falling back to default if not configured */
  get(clave: string): ModuloPermisos {
    return this._permisos()[clave] ?? DEFAULT_PERMISOS;
  }

  puedeVer(clave: string):      boolean { return this.get(clave).puedeVer; }
  puedeEditar(clave: string):   boolean { return this.get(clave).puedeEditar; }
  puedeCrear(clave: string):    boolean { return this.get(clave).puedeCrear; }
  puedeEliminar(clave: string): boolean { return this.get(clave).puedeEliminar; }

  /** Signal that returns true if user can edit the given module */
  puedeEditarSignal(clave: string) {
    return computed(() => (this._permisos()[clave] ?? DEFAULT_PERMISOS).puedeEditar);
  }

  invalidate(): void {
    this._loaded = false;
    this._permisos.set({});
  }
}
