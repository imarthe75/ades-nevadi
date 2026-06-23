/**
 * ContextCatalogService — Catálogos de la cascada plantel→nivel→ciclo→grado→grupo.
 *
 * Fuente ÚNICA de la cascada institucional. Concentra la carga de catálogos (antes
 * embebida en ShellComponent) para que la compartan:
 *   - el topbar (ShellComponent), que es el único selector visible de la cascada;
 *   - los grids (InteractiveGridComponent), mediante el lazo bilateral
 *     contexto ⇄ filtros de columna (ver `contextFilters` / `contextSuggestions` /
 *     `applyGridFilter`).
 *
 * El estado seleccionado vive en ContextService; este servicio solo gestiona las
 * LISTAS de opciones y la lógica de cascada (cargar dependientes / auto-seleccionar).
 */
import { Injectable, inject, signal, computed } from '@angular/core';
import { ContextService } from './context.service';
import { ApiService } from './api.service';
import type { Plantel, CicloEscolar, NivelEducativo, Grado, Grupo } from '../models';
import { grupoLabel } from '../models';

// Sentinelas "Todo el Instituto / Todos…" (admin global) — espejo de ShellComponent.
export const TODO_INSTITUTO: Plantel = { id: '', nombre_plantel: '— Todo el Instituto —', escuela_id: '', is_active: true };
export const TODOS_NIVELES: NivelEducativo = { id: '', nombre_nivel: 'TODOS' as any, autoridad_educativa: '', tipo_ciclo: '', num_periodos_eval: 0 };
export const TODOS_CICLOS: CicloEscolar = { id: '', nombre_ciclo: '— TODOS —', nivel_educativo_id: '', fecha_inicio: '', fecha_fin: '', tipo_ciclo: '', es_vigente: true, _label: '— TODOS —' };
export const TODOS_GRADOS: Grado = { id: '', numero_grado: 0, nombre_grado: '— TODOS —', nivel_educativo_id: '', plantel_id: '' };
export const TODOS_GRUPOS: Grupo = { id: '', nombre_grupo: '— TODOS —', grado_id: '', ciclo_escolar_id: '', capacidad_maxima: 0, turno: '', is_active: true };

export type CascadeRole = 'plantel' | 'nivel' | 'grado' | 'grupo';

@Injectable({ providedIn: 'root' })
export class ContextCatalogService {
  private readonly ctx = inject(ContextService);
  private readonly api = inject(ApiService);

  // ── Listas de opciones ────────────────────────────────────────────────────────
  readonly planteles = signal<Plantel[]>([]);
  readonly niveles   = signal<NivelEducativo[]>([]);
  readonly ciclos    = signal<CicloEscolar[]>([]);
  readonly grados    = signal<Grado[]>([]);
  readonly grupos    = signal<Grupo[]>([]);

  // ── Para el lazo bilateral con el grid ──────────────────────────────────────────
  /** Labels seleccionados actualmente — siembran los filtros de columna del grid. */
  readonly contextFilters = computed<Record<CascadeRole, string>>(() => ({
    plantel: this.ctx.plantel()?.nombre_plantel ?? '',
    nivel:   this.ctx.nivel()?.nombre_nivel ?? '',
    grado:   this.ctx.grado()?.nombre_grado ?? '',
    grupo:   this.ctx.grupo()?.nombre_grupo ?? '',
  }));

  /** Catálogo completo de labels por rol — alimenta el LOV del grid (permite ampliar). */
  readonly contextSuggestions = computed<Record<CascadeRole, string[]>>(() => ({
    plantel: this.planteles().filter(p => p.id).map(p => p.nombre_plantel),
    nivel:   this.niveles().filter(n => n.id).map(n => String(n.nombre_nivel)),
    grado:   this.grados().filter(g => g.id).map(g => g.nombre_grado),
    grupo:   this.grupos().filter(g => g.id).map(g => g.nombre_grupo),
  }));

  /**
   * Camino grid → contexto: el usuario filtró una columna de rol cascada.
   * Resuelve el label al objeto del catálogo y dispara el cambio de contexto
   * (con su cascada-clear). Label vacío/null ⇒ ampliar (sentinela "TODOS" si existe).
   */
  applyGridFilter(field: string, label: string | null): void {
    const role = field as CascadeRole;
    if (!['plantel', 'nivel', 'grado', 'grupo'].includes(role)) return;
    const clear = !label || !label.trim();

    switch (role) {
      case 'plantel': {
        const t = clear ? this.planteles().find(p => !p.id)
                        : this.planteles().find(p => p.nombre_plantel === label);
        if (t) this.onPlantelChange(t);
        break;
      }
      case 'nivel': {
        const t = clear ? this.niveles().find(n => !n.id)
                        : this.niveles().find(n => String(n.nombre_nivel) === label);
        if (t) this.onNivelChange(t);
        break;
      }
      case 'grado': {
        const t = clear ? this.grados().find(g => !g.id)
                        : this.grados().find(g => g.nombre_grado === label);
        if (t) this.onGradoChange(t);
        break;
      }
      case 'grupo': {
        const t = clear ? this.grupos().find(g => !g.id)
                        : this.grupos().find(g => g.nombre_grupo === label);
        if (t) this.onGrupoChange(t);
        break;
      }
    }
  }

  // ── Inicialización del topbar ───────────────────────────────────────────────────
  initTopbar(): void {
    const usuario = this.ctx.usuario();
    this.api.get<Plantel[]>('/planteles').subscribe(p => {
      const opciones = this.ctx.esAdminGlobal() ? [TODO_INSTITUTO, ...p] : p;
      this.planteles.set(opciones);

      if (!this.ctx.plantel()) {
        let inicial: Plantel | null;
        if (usuario?.plantel_id) {
          inicial = p.find(x => x.id === usuario.plantel_id) ?? p[0] ?? null;
        } else if (this.ctx.esAdminGlobal()) {
          inicial = TODO_INSTITUTO;
        } else {
          inicial = p[0] ?? null;
        }
        this.ctx.setPlantel(inicial?.id ? inicial : null);
        if (inicial?.id) this.loadNiveles(); else this.loadNivelesTodos();
      } else if (this.ctx.plantel()?.id) {
        this.loadNiveles();
      } else {
        this.loadNivelesTodos();
      }
    });
  }

  // ── Cambios de cascada (topbar y grid pasan por aquí) ───────────────────────────
  onPlantelChange(p: Plantel): void {
    const esGlobal = !p?.id;
    this.ctx.setPlantel(esGlobal ? null : p);
    this.ctx.setNivel(null);
    this.ctx.setCiclo(null);
    this.ctx.setGrado(null);
    this.ctx.setGrupo(null);
    this.ciclos.set([]);
    this.grados.set([]);
    this.grupos.set([]);
    if (esGlobal) this.loadNivelesTodos(); else this.loadNiveles();
  }

  onNivelChange(n: NivelEducativo): void {
    const esGlobal = !n?.id;
    this.ctx.setNivel(esGlobal ? null : n);
    this.ctx.setCiclo(null);
    this.ctx.setGrado(null);
    this.ctx.setGrupo(null);
    this.grados.set([]);
    this.grupos.set([]);
    this.loadCiclos();
  }

  onCicloChange(c: CicloEscolar): void {
    const esGlobal = !c?.id;
    this.ctx.setCiclo(esGlobal ? null : c);
    this.ctx.setGrupo(null);
    this.grupos.set([]);
    this.loadGrados();
  }

  onGradoChange(g: Grado): void {
    const esGlobal = !g?.id;
    this.ctx.setGrado(esGlobal ? null : g);
    this.ctx.setGrupo(null);
    this.grupos.set([]);
    this.loadGrupos();
  }

  onGrupoChange(g: Grupo): void {
    const esGlobal = !g?.id;
    this.ctx.setGrupo(esGlobal ? null : g);
  }

  // ── Carga de catálogos dependientes ─────────────────────────────────────────────
  private loadNivelesTodos(): void {
    this.api.get<NivelEducativo[]>('/catalogs/niveles').subscribe(ns => {
      this.niveles.set([TODOS_NIVELES, ...ns]);
      this.ctx.setNivel(null);
      this.loadCiclos();
    });
  }

  private loadNiveles(): void {
    const plantelId = this.ctx.plantel()?.id;
    if (!plantelId) return;

    this.api.get<NivelEducativo[]>(`/planteles/${plantelId}/niveles`).subscribe(ns => {
      const opciones = this.ctx.esAdminGlobal() ? [TODOS_NIVELES, ...ns] : ns;
      this.niveles.set(opciones);

      const usuario = this.ctx.usuario();
      let inicial: NivelEducativo | null = null;
      if (usuario?.nivel_educativo_id) inicial = ns.find(x => x.id === usuario.nivel_educativo_id) ?? null;
      if (!inicial && this.ctx.nivel()?.id) inicial = ns.find(x => x.id === this.ctx.nivel()!.id) ?? null;
      if (!inicial && ns.length) inicial = ns[0];

      if (inicial) {
        this.ctx.setNivel(inicial);
        this.loadCiclos();
      }
    });
  }

  private loadCiclos(): void {
    const nivel = this.ctx.nivel()?.id ? this.ctx.nivel()!.nombre_nivel : undefined;
    const params: Record<string, any> = { solo_vigentes: true };
    if (nivel) params['nivel'] = nivel;

    this.api.get<CicloEscolar[]>('/catalogs/ciclos', params).subscribe(c => {
      const labeled = c.map(x => ({
        ...x,
        _label: (!this.ctx.nivel()?.id && x.nivel_educativo?.nombre_nivel)
          ? `${x.nombre_ciclo} — ${x.nivel_educativo?.nombre_nivel}`
          : x.nombre_ciclo,
      }));
      const opciones = this.ctx.esAdminGlobal() ? [TODOS_CICLOS, ...labeled] : labeled;
      this.ciclos.set(opciones);

      if (opciones.length) {
        const existente = this.ctx.ciclo() ? opciones.find(x => x.id === this.ctx.ciclo()!.id) : null;
        const elegido = existente ?? opciones[0];
        this.ctx.setCiclo(elegido?.id ? elegido : null);
      }
      this.loadGrados();
    });
  }

  private loadGrados(): void {
    const params: Record<string, any> = {};
    if (this.ctx.nivel()?.id) params['nivel_id'] = this.ctx.nivel()!.id;
    if (this.ctx.plantel()?.id) params['plantel_id'] = this.ctx.plantel()!.id;

    this.api.get<Grado[]>('/catalogs/grados', params).subscribe(gs => {
      const opciones = this.ctx.esAdminGlobal() ? [TODOS_GRADOS, ...gs] : gs;
      this.grados.set(opciones);

      const usuario = this.ctx.usuario();
      let inicial: Grado | null = null;
      if (usuario?.grado_id) inicial = gs.find(x => x.id === usuario.grado_id) ?? null;
      if (!inicial && this.ctx.grado()?.id) inicial = gs.find(x => x.id === this.ctx.grado()!.id) ?? null;
      if (!inicial && opciones.length) inicial = opciones[0];

      this.ctx.setGrado(inicial?.id ? inicial : null);
      this.loadGrupos();
    });
  }

  private loadGrupos(): void {
    const params: Record<string, any> = { solo_activos: true };
    if (this.ctx.plantel()?.id) params['plantel_id'] = this.ctx.plantel()!.id;
    if (this.ctx.grado()?.id) params['grado_id'] = this.ctx.grado()!.id;
    if (this.ctx.ciclo()?.id) params['ciclo_escolar_id'] = this.ctx.ciclo()!.id;

    this.api.get<Grupo[]>('/grupos', params).subscribe(gps => {
      const labeled = gps.map(x => ({ ...x, _label: grupoLabel(x) }));
      const opciones = this.ctx.esAdminGlobal() ? [TODOS_GRUPOS, ...labeled] : labeled;
      this.grupos.set(opciones);

      const usuario = this.ctx.usuario();
      let inicial: Grupo | null = null;
      if (usuario?.grupo_id) inicial = gps.find(x => x.id === usuario.grupo_id) ?? null;
      if (!inicial && this.ctx.grupo()?.id) inicial = gps.find(x => x.id === this.ctx.grupo()!.id) ?? null;
      if (!inicial && opciones.length) inicial = opciones[0];

      this.ctx.setGrupo(inicial?.id ? inicial : null);
    });
  }
}
