import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';
import { TagModule } from 'primeng/tag';
import { DividerModule } from 'primeng/divider';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';

interface Rubrica {
  id: string;
  nombre_rubrica: string;
  descripcion: string | null;
  nombre_materia: string | null;
  nombre_nivel: string | null;
  total_criterios: number;
  ponderacion_total: number;
}

interface Criterio {
  id: string;
  nombre_criterio: string;
  descripcion: string | null;
  ponderacion: number;
  orden: number;
  niveles_logro: NivelLogro[] | null;
  _editando?: boolean;
}

interface NivelLogro {
  nivel: number;
  etiqueta: string;
  descripcion: string;
}

const NIVEL_COLORS = ['var(--red-400)', 'var(--yellow-400)', 'var(--teal-400)', 'var(--green-500)'];

@Component({
  selector: 'app-rubricas',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    ButtonModule, TableModule, DialogModule, SelectModule,
    InputTextModule, InputNumberModule, TextareaModule,
    TooltipModule, TagModule, DividerModule,
  ],
  template: `
    <div class="page-header">
      <div>
        <h2 class="page-title">Rúbricas de Evaluación</h2>
        <p class="page-subtitle">Criterios ponderados y niveles de logro por materia</p>
      </div>
      <div class="page-actions">
        <p-button icon="pi pi-plus" label="Nueva rúbrica" (onClick)="abrirNueva()" />
      </div>
    </div>

    <div class="layout-split">

      <!-- Panel izquierdo: lista de rúbricas -->
      <div class="panel-list">
        <div class="filter-bar">
          <p-select [options]="materias()" [(ngModel)]="filtroMateriaId"
                    optionLabel="label" optionValue="value"
                    placeholder="Filtrar por materia..."
                    [showClear]="true" styleClass="w-full"
                    (onChange)="cargar()" 
 [filter]="true" filterPlaceholder="Buscar..."/>
        </div>

        @if (rubricas().length === 0) {
          <div class="empty-msg">No hay rúbricas. Crea la primera.</div>
        }
        @for (r of rubricas(); track r.id) {
          <div class="rubrica-item" [class.selected]="selRubrica?.id === r.id"
               (click)="seleccionar(r)">
            <div class="rubrica-item-title">{{ r.nombre_rubrica }}</div>
            <div class="rubrica-item-meta">
              @if (r.nombre_materia) { <span>{{ r.nombre_materia }}</span> }
              @if (r.nombre_nivel)   { <span>{{ r.nombre_nivel }}</span> }
              <span>{{ r.total_criterios }} criterios</span>
            </div>
            <div class="rubrica-ponderacion">
              <span [class]="r.ponderacion_total === 100 ? 'pond-ok' : 'pond-warn'">
                {{ r.ponderacion_total | number:'1.0-1' }}% total
              </span>
            </div>
          </div>
        }
      </div>

      <!-- Panel derecho: builder de rúbrica -->
      <div class="panel-detail">
        @if (!selRubrica) {
          <div class="empty-panel">
            <i class="pi pi-table" style="font-size:3rem; color:var(--surface-400)"></i>
            <p>Selecciona una rúbrica para editarla</p>
          </div>
        } @else {
          <div class="rubrica-detail-header">
            <div>
              <h3>{{ selRubrica.nombre_rubrica }}</h3>
              @if (detalle()?.descripcion) {
                <p class="text-muted" style="font-size:.82rem; margin:.25rem 0 0">{{ detalle()?.descripcion }}</p>
              }
            </div>
            <div class="page-actions">
              <p-button icon="pi pi-trash" severity="danger" [text]="true"
                        ariaLabel="Eliminar rúbrica" pTooltip="Eliminar rúbrica" (onClick)="eliminarRubrica()" />
            </div>
          </div>

          <!-- Tabla de criterios -->
          <div class="criterios-builder">
            @for (c of criterios(); track c.id; let i = $index) {
              <div class="criterio-card">
                <div class="criterio-header">
                  <div class="criterio-info">
                    <span class="criterio-num">{{ i + 1 }}</span>
                    <strong>{{ c.nombre_criterio }}</strong>
                    @if (c.descripcion) {
                      <span class="text-muted" style="font-size:.78rem">— {{ c.descripcion }}</span>
                    }
                  </div>
                  <div class="criterio-meta">
                    <span class="pond-badge">{{ c.ponderacion | number:'1.0-1' }}%</span>
                    <p-button icon="pi pi-trash" size="small" severity="danger" [text]="true"
                              ariaLabel="Eliminar criterio" (onClick)="eliminarCriterio(c)" />
                  </div>
                </div>

                <!-- Niveles de logro -->
                @if (c.niveles_logro && c.niveles_logro.length > 0) {
                  <div class="niveles-grid">
                    @for (n of c.niveles_logro; track n.nivel) {
                      <div class="nivel-card" [style.border-top]="'3px solid ' + nivelColor(n.nivel)">
                        <div class="nivel-header" [style.color]="nivelColor(n.nivel)">
                          {{ n.nivel }}. {{ n.etiqueta }}
                        </div>
                        <div class="nivel-desc">{{ n.descripcion }}</div>
                      </div>
                    }
                  </div>
                }
              </div>
            }

            <p-button icon="pi pi-plus" label="Agregar criterio"
                      severity="secondary" [outlined]="true"
                      styleClass="w-full mt-2"
                      (onClick)="abrirCriterio()" />
          </div>
        }
      </div>
    </div>

    <!-- Dialog nueva rúbrica -->
    <p-dialog header="Nueva rúbrica" [(visible)]="showNueva" [modal]="true"
              [style]="{width:'460px'}">
      <div class="form-grid">
        <div class="form-field full">
          <label>Nombre *</label>
          <input pInputText [(ngModel)]="formNueva.nombre" placeholder="Ej. Rúbrica redacción" style="width:100%" />
        </div>
        <div class="form-field full">
          <label>Descripción</label>
          <textarea pTextarea [(ngModel)]="formNueva.descripcion" rows="2" style="width:100%"></textarea>
        </div>
        <div class="form-field">
          <label>Materia</label>
          <p-select [options]="materias()" [(ngModel)]="formNueva.materia_id"
                    optionLabel="label" optionValue="value"
                    placeholder="Opcional..." [showClear]="true" styleClass="w-full" 
 [filter]="true" filterPlaceholder="Buscar..."/>
        </div>
        <div class="form-field">
          <label>Nivel educativo</label>
          <p-select [options]="niveles()" [(ngModel)]="formNueva.nivel_educativo_id"
                    optionLabel="label" optionValue="value"
                    placeholder="Opcional..." [showClear]="true" styleClass="w-full" 
 [filter]="true" filterPlaceholder="Buscar..."/>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="showNueva = false" />
        <p-button label="Crear" icon="pi pi-check" (onClick)="crearRubrica()" />
      </ng-template>
    </p-dialog>

    <!-- Dialog nuevo criterio -->
    <p-dialog header="Agregar criterio" [(visible)]="showCriterio" [modal]="true"
              [style]="{width:'560px'}">
      <div class="form-grid">
        <div class="form-field full">
          <label>Nombre del criterio *</label>
          <input pInputText [(ngModel)]="formCrit.nombre" style="width:100%" />
        </div>
        <div class="form-field">
          <label>Descripción</label>
          <input pInputText [(ngModel)]="formCrit.descripcion" style="width:100%" />
        </div>
        <div class="form-field">
          <label>Ponderación (%)</label>
          <p-inputNumber [(ngModel)]="formCrit.ponderacion" [min]="0" [max]="100"
                         suffix="%" inputStyleClass="w-full" />
        </div>
        <div class="form-field">
          <label>Orden</label>
          <p-inputNumber [(ngModel)]="formCrit.orden" [min]="1" inputStyleClass="w-full" />
        </div>
      </div>

      <p-divider />
      <p style="font-size:.85rem; font-weight:600; margin-bottom:.75rem">Niveles de logro (opcional)</p>
      <div class="niveles-form">
        @for (n of formCrit.niveles; track n.nivel) {
          <div class="nivel-form-row">
            <div class="nivel-num" [style.background]="nivelColor(n.nivel)">{{ n.nivel }}</div>
            <input pInputText [(ngModel)]="n.etiqueta" placeholder="Etiqueta (ej. Destacado)"
                   style="width:110px; font-size:.82rem" />
            <input pInputText [(ngModel)]="n.descripcion" placeholder="Descripción del nivel"
                   style="flex:1; font-size:.82rem" />
          </div>
        }
      </div>

      <ng-template pTemplate="footer">
        <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="showCriterio = false" />
        <p-button label="Agregar" icon="pi pi-plus" (onClick)="guardarCriterio()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .page-header   { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:1.25rem; }
    .page-title    { font-size:1.25rem; font-weight:700; margin:0 0 .2rem; }
    .page-subtitle { color:var(--text-color-secondary); font-size:.82rem; margin:0; }
    .page-actions  { display:flex; gap:.5rem; align-items:center; }

    /* Layout split */
    .layout-split { display:grid; grid-template-columns:280px 1fr; gap:1.25rem; }
    @media (max-width:800px) { .layout-split { grid-template-columns:1fr; } }

    /* Panel lista */
    .panel-list { display:flex; flex-direction:column; gap:.4rem; }
    .filter-bar { margin-bottom:.5rem; }

    .rubrica-item {
      padding:.65rem .85rem; border-radius:6px; cursor:pointer;
      background:var(--surface-card); border:1px solid var(--surface-200);
      transition:border-color .12s, box-shadow .12s;
    }
    .rubrica-item:hover { border-color:var(--primary-color); }
    .rubrica-item.selected { border-color:var(--primary-color); box-shadow:0 0 0 2px var(--primary-200, #ffd0d5); }
    .rubrica-item-title { font-weight:600; font-size:.88rem; margin-bottom:.2rem; }
    .rubrica-item-meta  { display:flex; gap:.5rem; font-size:.72rem; color:var(--text-color-secondary); flex-wrap:wrap; }
    .rubrica-item-meta span + span::before { content:'·'; margin-right:.5rem; }
    .rubrica-ponderacion { margin-top:.3rem; font-size:.72rem; }
    .pond-ok   { color:var(--green-600); font-weight:600; }
    .pond-warn { color:var(--orange-500); font-weight:600; }

    /* Panel detalle */
    .panel-detail { min-height:400px; }
    .empty-panel  { height:300px; display:flex; flex-direction:column; align-items:center;
      justify-content:center; gap:1rem; color:var(--text-color-secondary); }

    .rubrica-detail-header { display:flex; justify-content:space-between; align-items:flex-start;
      margin-bottom:1rem; padding-bottom:.75rem; border-bottom:1px solid var(--surface-200); }
    .rubrica-detail-header h3 { font-size:1.05rem; font-weight:700; margin:0; }

    /* Criterios builder */
    .criterios-builder { display:flex; flex-direction:column; gap:.6rem; }
    .criterio-card {
      background:var(--surface-card); border:1px solid var(--surface-200); border-radius:8px;
      padding:.75rem 1rem;
    }
    .criterio-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:.5rem; }
    .criterio-info   { display:flex; align-items:center; gap:.5rem; flex-wrap:wrap; }
    .criterio-num    { background:var(--primary-color); color:#fff; border-radius:50%;
      width:20px; height:20px; display:flex; align-items:center; justify-content:center;
      font-size:.7rem; font-weight:700; flex-shrink:0; }
    .criterio-meta   { display:flex; align-items:center; gap:.35rem; }
    .pond-badge      { background:var(--surface-200); border-radius:10px; font-size:.72rem;
      font-weight:700; padding:0 .5rem; }

    /* Niveles grid */
    .niveles-grid { display:grid; grid-template-columns:repeat(4,1fr); gap:.4rem; margin-top:.5rem; }
    @media (max-width:900px) { .niveles-grid { grid-template-columns:repeat(2,1fr); } }
    .nivel-card { border-radius:5px; padding:.4rem .5rem; background:var(--surface-50); }
    .nivel-header { font-size:.72rem; font-weight:700; margin-bottom:.2rem; }
    .nivel-desc   { font-size:.72rem; color:var(--text-color-secondary); }

    /* Form */
    .form-grid  { display:grid; grid-template-columns:1fr 1fr; gap:.75rem; }
    .form-field { display:flex; flex-direction:column; gap:.3rem; font-size:.875rem; }
    .form-field.full { grid-column:1/-1; }
    label       { font-weight:500; font-size:.82rem; }

    .niveles-form { display:flex; flex-direction:column; gap:.35rem; }
    .nivel-form-row { display:flex; align-items:center; gap:.5rem; }
    .nivel-num { width:24px; height:24px; border-radius:50%; display:flex; align-items:center;
      justify-content:center; color:#fff; font-weight:700; font-size:.8rem; flex-shrink:0; }

    .text-muted { color:var(--text-color-secondary); }
    .empty-msg  { text-align:center; padding:2rem; color:var(--text-color-secondary); font-size:.85rem; }
    .w-full     { width:100% !important; }
    .mt-2       { margin-top:.5rem; }
  `],
})
export class RubricasComponent implements OnInit {
  private readonly api = inject(ApiService);
  readonly ctx = inject(ContextService);

  rubricas = signal<Rubrica[]>([]);
  materias = signal<{ label: string; value: string }[]>([]);
  niveles = signal<{ label: string; value: string }[]>([]);
  criterios = signal<Criterio[]>([]);
  detalle = signal<Rubrica | null>(null);

  selRubrica: Rubrica | null = null;
  filtroMateriaId = '';
  showNueva = false;
  showCriterio = false;

  formNueva = { nombre: '', descripcion: '', materia_id: '', nivel_educativo_id: '' };
  formCrit = this.emptyFormCrit();

  ngOnInit(): void {
    this.cargar();
    this.cargarCatalogos();
  }

  cargar(): void {
    const params: Record<string, string> = {};
    if (this.filtroMateriaId) params['materia_id'] = this.filtroMateriaId;
    this.api.get<Rubrica[]>('/rubricas', params).subscribe(r => this.rubricas.set(r));
  }

  cargarCatalogos(): void {
    this.api.get<any[]>('/materias').subscribe(list => {
      this.materias.set(list.map(m => ({ label: m.nombre_materia, value: m.id })));
    });
    this.api.get<any[]>('/catalogs/niveles').subscribe(list => {
      this.niveles.set(list.map(n => ({ label: n.nombre_nivel, value: n.id })));
    });
  }

  seleccionar(r: Rubrica): void {
    this.selRubrica = r;
    this.api.get<any>(`/rubricas/${r.id}`).subscribe(d => {
      this.detalle.set(d);
      this.criterios.set(d.criterios ?? []);
    });
  }

  abrirNueva(): void { this.formNueva = { nombre: '', descripcion: '', materia_id: '', nivel_educativo_id: '' }; this.showNueva = true; }

  crearRubrica(): void {
    if (!this.formNueva.nombre) return;
    this.api.post('/rubricas', {
      nombre_rubrica: this.formNueva.nombre,
      descripcion: this.formNueva.descripcion || null,
      materia_id: this.formNueva.materia_id || null,
      nivel_educativo_id: this.formNueva.nivel_educativo_id || null,
    }).subscribe(() => { this.showNueva = false; this.cargar(); });
  }

  abrirCriterio(): void { this.formCrit = this.emptyFormCrit(); this.showCriterio = true; }

  guardarCriterio(): void {
    if (!this.selRubrica || !this.formCrit.nombre) return;
    const nivelesValidos = this.formCrit.niveles.filter(n => n.etiqueta.trim());
    this.api.post(`/rubricas/${this.selRubrica.id}/criterios`, {
      nombre_criterio: this.formCrit.nombre,
      descripcion: this.formCrit.descripcion || null,
      ponderacion: this.formCrit.ponderacion,
      orden: this.formCrit.orden,
      niveles_logro: nivelesValidos.length ? nivelesValidos : null,
    }).subscribe(() => { this.showCriterio = false; this.seleccionar(this.selRubrica!); });
  }

  eliminarCriterio(c: Criterio): void {
    if (!this.selRubrica) return;
    this.api.delete(`/rubricas/${this.selRubrica.id}/criterios/${c.id}`).subscribe(() => {
      this.seleccionar(this.selRubrica!);
    });
  }

  eliminarRubrica(): void {
    if (!this.selRubrica) return;
    this.api.delete(`/rubricas/${this.selRubrica.id}`).subscribe(() => {
      this.selRubrica = null;
      this.criterios.set([]);
      this.cargar();
    });
  }

  nivelColor(nivel: number): string { return NIVEL_COLORS[nivel - 1] ?? '#999'; }

  private emptyFormCrit() {
    return {
      nombre: '', descripcion: '', ponderacion: 25.0, orden: 1,
      niveles: [
        { nivel: 1, etiqueta: 'Inicial', descripcion: '' },
        { nivel: 2, etiqueta: 'En desarrollo', descripcion: '' },
        { nivel: 3, etiqueta: 'Logrado', descripcion: '' },
        { nivel: 4, etiqueta: 'Destacado', descripcion: '' },
      ],
    };
  }
}
