/**
 * ContextService — Equivale a los Application Items de Oracle APEX.
 * Mantiene el plantel, ciclo y nivel seleccionados por el usuario.
 * Todos los componentes se suscriben a este servicio para filtrar datos.
 */
import { Injectable, signal, computed, effect } from '@angular/core';
import type { AppContext, Plantel, CicloEscolar, NivelEducativo, UsuarioMe, Grado, Grupo } from '../models';
import { esAdminGlobal, esAdminPlantel, tieneScopeNivel, rolLabel } from '../models';

const STORAGE_KEY = 'ades_context';

@Injectable({ providedIn: 'root' })
export class ContextService {
  private readonly _plantel = signal<Plantel | null>(this._restore('plantel'));
  private readonly _ciclo   = signal<CicloEscolar | null>(this._restore('ciclo'));
  private readonly _nivel   = signal<NivelEducativo | null>(this._restore('nivel'));
  private readonly _grado   = signal<Grado | null>(this._restore('grado'));
  private readonly _grupo   = signal<Grupo | null>(this._restore('grupo'));
  private readonly _usuario = signal<UsuarioMe | null>(this._restore('usuario'));

  readonly plantel = this._plantel.asReadonly();
  readonly ciclo   = this._ciclo.asReadonly();
  readonly nivel   = this._nivel.asReadonly();
  readonly grado   = this._grado.asReadonly();
  readonly grupo   = this._grupo.asReadonly();
  readonly usuario = this._usuario.asReadonly();

  readonly context = computed<AppContext>(() => ({
    plantel: this._plantel(),
    ciclo:   this._ciclo(),
    nivel:   this._nivel(),
    grado:   this._grado(),
    grupo:   this._grupo(),
    usuario: this._usuario(),
  }));

  /** true si el contexto mínimo está configurado */
  readonly isReady = computed(() => !!this._plantel() && !!this._ciclo());

  // ── Helpers de rol ───────────────────────────────────────────────────────────
  readonly esAdminGlobal  = computed(() => esAdminGlobal(this._usuario()));
  readonly esAdminPlantel = computed(() => esAdminPlantel(this._usuario()));
  readonly tieneScopeNivel = computed(() => tieneScopeNivel(this._usuario()));
  /** El usuario puede seleccionar plantel libremente (admin global o plantel) */
  readonly puedeSeleccionarPlantel = computed(() =>
    !this.tieneScopeNivel() && this._usuario() != null
  );
  readonly rolLabel = computed(() => rolLabel(this._usuario()));
  readonly nivelAcceso = computed(() => this._usuario()?.nivel_acceso ?? 99);

  constructor() {
    // Persistir cambios en sessionStorage
    effect(() => {
      const ctx = this.context();
      if (ctx.plantel)  sessionStorage.setItem('ades_plantel',  JSON.stringify(ctx.plantel));
      if (ctx.ciclo)    sessionStorage.setItem('ades_ciclo',    JSON.stringify(ctx.ciclo));
      if (ctx.nivel)    sessionStorage.setItem('ades_nivel',    JSON.stringify(ctx.nivel));
      if (ctx.grado)    sessionStorage.setItem('ades_grado',    JSON.stringify(ctx.grado));
      if (ctx.grupo)    sessionStorage.setItem('ades_grupo',    JSON.stringify(ctx.grupo));
      if (ctx.usuario)  sessionStorage.setItem('ades_usuario',  JSON.stringify(ctx.usuario));
    });
  }

  setPlantel(p: Plantel | null): void { this._plantel.set(p); }
  setCiclo(c: CicloEscolar | null): void { this._ciclo.set(c); }
  setNivel(n: NivelEducativo | null): void { this._nivel.set(n); }
  setGrado(g: Grado | null): void { this._grado.set(g); }
  setGrupo(g: Grupo | null): void { this._grupo.set(g); }
  setUsuario(u: UsuarioMe | null): void {
    this._usuario.set(u);
    if (!u) {
      sessionStorage.removeItem('ades_usuario');
      sessionStorage.removeItem('ades_plantel');
      sessionStorage.removeItem('ades_ciclo');
      sessionStorage.removeItem('ades_nivel');
      sessionStorage.removeItem('ades_grado');
      sessionStorage.removeItem('ades_grupo');
    }
  }

  private _restore<T>(key: string): T | null {
    try {
      const raw = sessionStorage.getItem(`ades_${key}`);
      return raw ? (JSON.parse(raw) as T) : null;
    } catch { return null; }
  }
}
