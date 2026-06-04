/**
 * ContextService — Equivale a los Application Items de Oracle APEX.
 * Mantiene el plantel, ciclo y nivel seleccionados por el usuario.
 * Todos los componentes se suscriben a este servicio para filtrar datos.
 */
import { Injectable, signal, computed, effect } from '@angular/core';
import type { AppContext, Plantel, CicloEscolar, NivelEducativo, UsuarioMe } from '../models';

const STORAGE_KEY = 'ades_context';

@Injectable({ providedIn: 'root' })
export class ContextService {
  private readonly _plantel = signal<Plantel | null>(this._restore('plantel'));
  private readonly _ciclo   = signal<CicloEscolar | null>(this._restore('ciclo'));
  private readonly _nivel   = signal<NivelEducativo | null>(this._restore('nivel'));
  private readonly _usuario = signal<UsuarioMe | null>(null);

  readonly plantel = this._plantel.asReadonly();
  readonly ciclo   = this._ciclo.asReadonly();
  readonly nivel   = this._nivel.asReadonly();
  readonly usuario = this._usuario.asReadonly();

  readonly context = computed<AppContext>(() => ({
    plantel: this._plantel(),
    ciclo:   this._ciclo(),
    nivel:   this._nivel(),
    usuario: this._usuario(),
  }));

  /** true si el contexto mínimo está configurado */
  readonly isReady = computed(() => !!this._plantel() && !!this._ciclo());

  constructor() {
    // Persistir cambios en sessionStorage
    effect(() => {
      const ctx = this.context();
      if (ctx.plantel) sessionStorage.setItem('ades_plantel', JSON.stringify(ctx.plantel));
      if (ctx.ciclo)   sessionStorage.setItem('ades_ciclo',   JSON.stringify(ctx.ciclo));
      if (ctx.nivel)   sessionStorage.setItem('ades_nivel',   JSON.stringify(ctx.nivel));
    });
  }

  setPlantel(p: Plantel | null): void { this._plantel.set(p); }
  setCiclo(c: CicloEscolar | null): void { this._ciclo.set(c); }
  setNivel(n: NivelEducativo | null): void { this._nivel.set(n); }
  setUsuario(u: UsuarioMe | null): void { this._usuario.set(u); }

  private _restore<T>(key: string): T | null {
    try {
      const raw = sessionStorage.getItem(`ades_${key}`);
      return raw ? (JSON.parse(raw) as T) : null;
    } catch { return null; }
  }
}
