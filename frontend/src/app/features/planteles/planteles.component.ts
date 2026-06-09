import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { SkeletonModule } from 'primeng/skeleton';
import { TooltipModule } from 'primeng/tooltip';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import type { Plantel } from '../../core/models';

interface NivelStats {
  nivel_educativo_id: string;
  nombre_nivel: string;
  grupos_activos: number;
  grados: number;
}
interface PlantelStats {
  id: string;
  nombre_plantel: string;
  clave_ct: string | null;
  total_alumnos: number;
  total_profesores: number;
  total_grupos: number;
  niveles: NivelStats[];
}

const NIVEL_COLOR: Record<string, string> = {
  PRIMARIA: '#22c55e', SECUNDARIA: '#3b82f6', PREPARATORIA: '#a855f7',
};
const NIVEL_ICON: Record<string, string> = {
  PRIMARIA: 'pi-star', SECUNDARIA: 'pi-book', PREPARATORIA: 'pi-graduation-cap',
};

@Component({
  selector: 'app-planteles',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, ButtonModule, TagModule, SkeletonModule, TooltipModule, DialogModule, InputTextModule, ToastModule],
  providers: [MessageService],
  template: `
    <p-toast />

    <div class="page-header">
      <div>
        <h2>Planteles</h2>
        <p class="subtitle">Instituto Nevadi — {{ stats().length }} plantel(es)</p>
      </div>
    </div>

    @if (loading()) {
      <div class="cards-grid">
        @for (i of [1,2,3]; track i) {
          <p-skeleton height="300px" />
        }
      </div>
    } @else {
      <div class="cards-grid">
        @for (p of stats(); track p.id) {
          <div class="plantel-card" [class.activo]="ctx.plantel()?.id === p.id">

            <div class="card-top">
              <div class="card-title-row">
                <div class="avatar">{{ p.nombre_plantel[0] }}</div>
                <div>
                  <h3 class="card-nombre">{{ p.nombre_plantel }}</h3>
                  @if (p.clave_ct) { <code class="clave-ct">{{ p.clave_ct }}</code> }
                </div>
              </div>
              @if (ctx.plantel()?.id === p.id) {
                <p-tag value="Contexto actual" severity="success" />
              }
            </div>

            <div class="kpi-row">
              <div class="kpi">
                <span class="kv">{{ p.total_alumnos | number }}</span>
                <span class="kl">Alumnos</span>
              </div>
              <div class="sep"></div>
              <div class="kpi">
                <span class="kv">{{ p.total_profesores }}</span>
                <span class="kl">Profesores</span>
              </div>
              <div class="sep"></div>
              <div class="kpi">
                <span class="kv">{{ p.total_grupos }}</span>
                <span class="kl">Grupos</span>
              </div>
            </div>

            <div class="niveles">
              @for (n of p.niveles; track n.nivel_educativo_id) {
                <div class="nivel-row" [style.border-left-color]="color(n.nombre_nivel)">
                  <i class="pi" [class]="icon(n.nombre_nivel)" [style.color]="color(n.nombre_nivel)"></i>
                  <span class="nn">{{ n.nombre_nivel | titlecase }}</span>
                  <span class="nd">{{ n.grados }} grados</span>
                  <span class="nd">{{ n.grupos_activos }} grupos</span>
                </div>
              }
            </div>

            <div class="card-btns">
              <p-button label="Alumnos" icon="pi pi-users" severity="secondary" size="small"
                [text]="true" routerLink="/alumnos" (onClick)="seleccionar(p)" />
              <p-button label="Profesores" icon="pi pi-id-card" severity="secondary" size="small"
                [text]="true" routerLink="/profesores" (onClick)="seleccionar(p)" />
              <p-button label="Grupos" icon="pi pi-building" severity="secondary" size="small"
                [text]="true" routerLink="/grupos" (onClick)="seleccionar(p)" />
              @if (isAdmin()) {
                <p-button icon="pi pi-pencil" severity="warn" size="small"
                  [text]="true" pTooltip="Editar plantel" (onClick)="abrirEditar(p)" />
              }
            </div>

          </div>
        }
      </div>
    }

    <!-- Diálogo editar plantel -->
    <p-dialog [(visible)]="dlgPlantel" header="Editar plantel"
      [modal]="true" [draggable]="false" [style]="{width:'380px'}">
      @if (plantelEdit) {
        <div class="form-grid">
          <label>Nombre *</label>
          <input pInputText [(ngModel)]="plantelEdit.nombre_plantel" />
          <label>Clave CT</label>
          <input pInputText [(ngModel)]="plantelEdit.clave_ct" placeholder="CCT12345" />
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="dlgPlantel=false" />
          <p-button label="Guardar" icon="pi pi-save" [loading]="saving()" (onClick)="guardarPlantel()" />
        </ng-template>
      }
    </p-dialog>
  `,
  styles: [`
    .page-header { margin-bottom: 1.5rem; }
    .page-header h2 { margin: 0; }
    .subtitle { font-size: .82rem; color: var(--text-color-secondary); margin: 0; }
    .cards-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px,1fr)); gap: 1.25rem; }

    .plantel-card {
      background: var(--surface-0); border: 1px solid var(--surface-200); border-radius: 12px;
      padding: 1.25rem; display: flex; flex-direction: column; gap: .9rem;
      transition: box-shadow .15s, border-color .15s;
    }
    .plantel-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,.08); border-color: var(--primary-200); }
    .plantel-card.activo { border-color: var(--primary-color); box-shadow: 0 0 0 2px var(--primary-100); }

    .card-top { display: flex; justify-content: space-between; align-items: flex-start; }
    .card-title-row { display: flex; gap: .7rem; align-items: center; }
    .avatar {
      width: 42px; height: 42px; border-radius: 10px; background: var(--primary-color); color: #fff;
      display: flex; align-items: center; justify-content: center;
      font-weight: 800; font-size: 1.1rem; flex-shrink: 0;
    }
    .card-nombre { font-size: 1rem; font-weight: 700; margin: 0; }
    .clave-ct { font-size: .72rem; color: var(--text-color-secondary); background: var(--surface-100); padding: .1rem .4rem; border-radius: 3px; }

    .kpi-row { display: flex; align-items: center; justify-content: space-around; background: var(--surface-50); border-radius: 8px; padding: .7rem; }
    .kpi { display: flex; flex-direction: column; align-items: center; }
    .kv { font-size: 1.35rem; font-weight: 700; color: var(--primary-color); line-height: 1.1; }
    .kl { font-size: .7rem; color: var(--text-color-secondary); }
    .sep { width: 1px; height: 28px; background: var(--surface-200); }

    .niveles { display: flex; flex-direction: column; gap: .35rem; }
    .nivel-row {
      display: flex; align-items: center; gap: .55rem; padding: .4rem .65rem;
      border-radius: 6px; background: var(--surface-50); border-left: 3px solid #ccc;
    }
    .nn { font-size: .83rem; font-weight: 600; flex: 1; text-transform: capitalize; }
    .nd { font-size: .72rem; color: var(--text-color-secondary); background: var(--surface-100); padding: .1rem .35rem; border-radius: 10px; }
    .nivel-row i { font-size: .82rem; }

    .card-btns { display: flex; gap: .4rem; padding-top: .25rem; border-top: 1px solid var(--surface-100); flex-wrap: wrap; }
    .form-grid { display: grid; grid-template-columns: 100px 1fr; gap: .75rem 1rem; align-items: center; padding: .5rem 0; }
    .form-grid label { font-size: .85rem; color: var(--text-color-secondary); font-weight: 600; }
  `],
})
export class PlantelesComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly msg = inject(MessageService);
  readonly ctx = inject(ContextService);

  stats   = signal<PlantelStats[]>([]);
  loading = signal(true);

  dlgPlantel = false;
  plantelEdit: { id: string; nombre_plantel: string; clave_ct: string | null } | null = null;
  saving = signal(false);

  ngOnInit(): void {
    this.api.get<PlantelStats[]>('/planteles/stats').subscribe({
      next: s => { this.stats.set(s); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  isAdmin(): boolean { return this.ctx.nivelAcceso() <= 1; }

  abrirEditar(p: PlantelStats): void {
    this.plantelEdit = { id: p.id, nombre_plantel: p.nombre_plantel, clave_ct: p.clave_ct };
    this.dlgPlantel = true;
  }

  guardarPlantel(): void {
    if (!this.plantelEdit || !this.plantelEdit.nombre_plantel.trim()) return;
    this.saving.set(true);
    this.api.patch(`/admin/planteles/${this.plantelEdit.id}`, {
      nombre_plantel: this.plantelEdit.nombre_plantel,
      clave_ct: this.plantelEdit.clave_ct,
    }).subscribe({
      next: () => {
        this.stats.update(list => list.map(s =>
          s.id === this.plantelEdit!.id
            ? { ...s, nombre_plantel: this.plantelEdit!.nombre_plantel, clave_ct: this.plantelEdit!.clave_ct }
            : s
        ));
        this.msg.add({ severity: 'success', summary: 'Guardado', detail: 'Plantel actualizado' });
        this.dlgPlantel = false;
        this.saving.set(false);
      },
      error: e => {
        this.msg.add({ severity: 'error', summary: 'Error', detail: e.error?.detail ?? 'Error al guardar' });
        this.saving.set(false);
      },
    });
  }

  seleccionar(p: PlantelStats): void {
    this.ctx.setPlantel({
      id: p.id, nombre_plantel: p.nombre_plantel,
      clave_ct: p.clave_ct ?? undefined,
      escuela_id: '', is_active: true,
    } as Plantel);
  }

  color(n: string): string { return NIVEL_COLOR[n] ?? '#94a3b8'; }
  icon(n: string):  string { return NIVEL_ICON[n]  ?? 'pi-school'; }
}
