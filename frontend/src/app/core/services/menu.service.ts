import { Injectable, inject, signal, computed } from '@angular/core';
import { ApiService } from './api.service';
import { tap } from 'rxjs/operators';
import { Observable, of } from 'rxjs';

export interface MenuItemConfig {
  clave: string;
  seccion: string;
  label: string;
  icono: string;
  ruta: string;
  nivel_maximo: number;
  nivel_minimo: number;
  activo: boolean;
  orden: number;
}

export interface NavItem {
  route: string;
  icon: string;
  label: string;
  clave: string;
  maxNivel: number;
  minNivel: number;
}

export interface NavGroup {
  section: string;
  items: NavItem[];
}

@Injectable({ providedIn: 'root' })
export class MenuService {
  private readonly api = inject(ApiService);

  private readonly _menuConfig = signal<MenuItemConfig[]>([]);
  private _loaded = false;

  readonly menuConfig = this._menuConfig.asReadonly();

  readonly navGroups = computed<NavGroup[]>(() => {
    const items = this._menuConfig();
    if (items.length === 0) return [];

    const sectionMap = new Map<string, NavItem[]>();
    for (const item of items) {
      if (!item.activo) continue;
      if (!sectionMap.has(item.seccion)) sectionMap.set(item.seccion, []);
      sectionMap.get(item.seccion)!.push({
        route: item.ruta,
        icon: item.icono,
        label: item.label,
        clave: item.clave,
        maxNivel: item.nivel_maximo,
        minNivel: item.nivel_minimo,
      });
    }

    const groups: NavGroup[] = [];
    for (const [section, navItems] of sectionMap) {
      groups.push({ section, items: navItems });
    }
    return groups;
  });

  loadMenuConfig(): Observable<void> {
    if (this._loaded) return of(void 0);
    return this.api.get<MenuItemConfig[]>('/menus/catalogo').pipe(
      tap(items => {
        this._menuConfig.set(items);
        this._loaded = true;
      }),
    ) as unknown as Observable<void>;
  }

  /** Invalidate cache so next call reloads from API */
  invalidate(): void {
    this._loaded = false;
    this._menuConfig.set([]);
  }
}
