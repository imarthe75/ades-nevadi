import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';
import { TabsModule } from 'primeng/tabs';
import { InputNumberModule } from 'primeng/inputnumber';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { ProgressBarModule } from 'primeng/progressbar';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService, ExportColumn } from '../../core/services/export.service';

type TagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' | undefined;

interface Badge {
  id: string;
  nombre: string;
  descripcion: string | null;
  icono: string;
  color: string;
  tipo: string;
  criterio_tipo: string;
  criterio_metrica: string | null;
  criterio_valor: number | null;
  total_otorgados: number;
}

interface BadgeAlumno extends Badge {
  otorgado_id: string | null;
  fecha_otorgado: string | null;
  motivo: string | null;
}

interface AlumnoSugerencia {
  id: string;
  nombre: string;
  matricula: string;
  grupo: string;
}

@Component({
  selector: 'app-badges',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    ButtonModule, TableModule, TagModule, DialogModule, SelectModule,
    InputTextModule, TextareaModule, TooltipModule, TabsModule,
    InputNumberModule, AutoCompleteModule, ProgressBarModule,
  ],
  template: `
<div class="page-header">
  <div class="page-title">
    <i class="pi pi-star-fill" style="color:#F39C12"></i>
    <h1>Badges y Gamificación</h1>
  </div>
  <div class="page-actions">
    <p-button label="Exportar CSV" icon="pi pi-file" severity="secondary" size="small"
              (onClick)="exportCSV()" />
    <p-button label="Nueva Insignia" icon="pi pi-plus" size="small"
              (onClick)="abrirNuevo()" />
  </div>
</div>

<p-tabs [(value)]="tabActivo">

  <!-- ══ TAB CATÁLOGO ══════════════════════════════════════════════════════ -->
  <p-tabpanel value="catalogo" header="Catálogo de Insignias">
    <div class="badges-grid">
      @for (b of badges(); track b.id) {
        <div class="badge-card" [class.badge-manual]="b.criterio_tipo === 'MANUAL'">
          <div class="badge-icon-wrap" [style.background]="b.color + '22'">
            <i [class]="'pi ' + b.icono" [style.color]="b.color" style="font-size:2rem"></i>
          </div>
          <div class="badge-info">
            <div class="badge-nombre">{{ b.nombre }}</div>
            <div class="badge-desc">{{ b.descripcion }}</div>
            <div class="badge-meta">
              <p-tag [value]="b.tipo" [severity]="tipoSeverity(b.tipo)" />
              <p-tag [value]="b.criterio_tipo" [severity]="b.criterio_tipo === 'AUTOMATICO' ? 'success' : 'secondary'" />
            </div>
            @if (b.criterio_metrica) {
              <div class="badge-criterio">
                <i class="pi pi-filter" style="font-size:.75rem"></i>
                {{ metricaLabel(b.criterio_metrica) }} ≥ {{ b.criterio_valor }}
                {{ b.criterio_metrica === 'pct_asistencia' ? '%' : '' }}
              </div>
            }
          </div>
          <div class="badge-footer">
            <span class="badge-count">
              <i class="pi pi-users"></i> {{ b.total_otorgados }} alumnos
            </span>
            <div style="display:flex;gap:.25rem">
              <p-button icon="pi pi-eye" [text]="true" size="small" pTooltip="Ver alumnos"
                        (onClick)="verBadge(b)" />
              <p-button icon="pi pi-trash" [text]="true" size="small" severity="danger"
                        pTooltip="Eliminar" (onClick)="eliminar(b)" />
            </div>
          </div>
        </div>
      }
      @if (!badges().length) {
        <div class="empty-state"><i class="pi pi-star"></i><br>Sin insignias</div>
      }
    </div>
  </p-tabpanel>

  <!-- ══ TAB ALUMNOS ═══════════════════════════════════════════════════════ -->
  <p-tabpanel value="alumnos" header="Insignias por Alumno">
    <div class="alumno-search-bar">
      <p-autoComplete
        [(ngModel)]="alumnoQuery"
        [suggestions]="alumnosSugerencias()"
        field="nombre"
        placeholder="Buscar alumno por nombre o matrícula..."
        (completeMethod)="buscarAlumno($event)"
        (onSelect)="seleccionarAlumno($event)"
        style="flex:1"
      />
      <span style="color:var(--text-secondary);font-size:.85rem">
        {{ alumnoSeleccionado() ? alumnoSeleccionado()!.nombre : 'Selecciona un alumno para ver sus insignias' }}
      </span>
    </div>

    @if (alumnoSeleccionado()) {
      <div class="alumno-badge-stats">
        <div class="stat-pill">
          <i class="pi pi-star-fill" style="color:#F39C12"></i>
          <strong>{{ badgesAlumno().filter(b => b.otorgado_id).length }}</strong>
          / {{ badgesAlumno().length }} obtenidas
        </div>
      </div>
      <div class="badges-grid">
        @for (b of badgesAlumno(); track b.id) {
          <div class="badge-card" [class.badge-obtenido]="b.otorgado_id"
               [class.badge-no-obtenido]="!b.otorgado_id">
            <div class="badge-icon-wrap"
                 [style.background]="b.otorgado_id ? (b.color + '33') : '#eee'">
              <i [class]="'pi ' + b.icono"
                 [style.color]="b.otorgado_id ? b.color : '#bbb'"
                 style="font-size:2rem"></i>
            </div>
            <div class="badge-info">
              <div class="badge-nombre" [style.opacity]="b.otorgado_id ? '1' : '0.5'">
                {{ b.nombre }}
              </div>
              @if (b.otorgado_id) {
                <div class="badge-fecha">
                  <i class="pi pi-calendar"></i>
                  {{ b.fecha_otorgado | date:'dd/MM/yyyy' }}
                </div>
                @if (b.motivo) {
                  <div class="badge-motivo">{{ b.motivo }}</div>
                }
              } @else {
                <div class="badge-pendiente">No obtenida</div>
              }
            </div>
            <div class="badge-footer">
              @if (!b.otorgado_id && b.criterio_tipo === 'MANUAL') {
                <p-button label="Otorgar" icon="pi pi-plus" size="small"
                          (onClick)="otorgarManual(b)" />
              }
              @if (b.otorgado_id) {
                <p-button icon="pi pi-times" [text]="true" severity="danger" size="small"
                          pTooltip="Revocar" (onClick)="revocar(b)" />
              }
            </div>
          </div>
        }
      </div>
    }
  </p-tabpanel>

  <!-- ══ TAB AUTO-EVALUACIÓN ═══════════════════════════════════════════════ -->
  <p-tabpanel value="auto" header="Auto-Evaluación">
    <div class="auto-eval-panel">
      <div class="auto-eval-info">
        <i class="pi pi-info-circle" style="font-size:1.5rem;color:#3498DB"></i>
        <div>
          <strong>Evaluación automática de criterios</strong>
          <p>Analiza todos los alumnos del ciclo seleccionado y otorga automáticamente las insignias
             con criterios objetivos (asistencia, promedio, conducta).</p>
        </div>
      </div>
      <div class="auto-eval-form">
        <p-select [options]="ciclos()" optionLabel="nombre" optionValue="id"
                  [(ngModel)]="cicloAutoEval" placeholder="Seleccionar ciclo escolar" 
 [filter]="true" filterPlaceholder="Buscar..."/>
        <p-button label="Ejecutar Auto-Evaluación" icon="pi pi-play"
                  [loading]="autoEvaluando()" [disabled]="!cicloAutoEval"
                  (onClick)="autoEvaluar()" />
      </div>
      @if (resultadoAutoEval()) {
        <div class="auto-eval-result">
          <i class="pi pi-check-circle" style="color:#27AE60;font-size:1.5rem"></i>
          <div>
            <strong>{{ resultadoAutoEval()!.total_otorgados }} insignias otorgadas</strong>
            a partir de {{ resultadoAutoEval()!.badges_evaluados }} criterios evaluados.
          </div>
        </div>
      }
    </div>
  </p-tabpanel>

</p-tabs>

<!-- ── Dialog nueva insignia ─────────────────────────────────────────────── -->
<p-dialog [(visible)]="showDialog" [style]="{width:'520px'}" header="Nueva Insignia" [modal]="true">
  <div class="form-grid2">
    <div class="field full">
      <label>Nombre *</label>
      <input pInputText [(ngModel)]="form.nombre" placeholder="Nombre de la insignia" />
    </div>
    <div class="field full">
      <label>Descripción</label>
      <textarea pTextarea [(ngModel)]="form.descripcion" rows="2"
                placeholder="Descripción breve..."></textarea>
    </div>
    <div class="field">
      <label>Tipo *</label>
      <p-select [options]="tiposOpt" [(ngModel)]="form.tipo"
                optionLabel="label" optionValue="value" placeholder="Tipo" 
 [filter]="true" filterPlaceholder="Buscar..."/>
    </div>
    <div class="field">
      <label>Criterio</label>
      <p-select [options]="criterioOpt" [(ngModel)]="form.criterio_tipo"
                optionLabel="label" optionValue="value" 
 [filter]="true" filterPlaceholder="Buscar..."/>
    </div>
    <div class="field">
      <label>Ícono</label>
      <div style="display:flex;gap:.5rem;align-items:center">
        <p-select [options]="iconsOpt" [(ngModel)]="form.icono"
                  optionLabel="label" optionValue="value" placeholder="Seleccionar ícono" 
                  [filter]="true" filterPlaceholder="Buscar..." style="flex:1">
          <ng-template pTemplate="selectedItem" let-selectedOption>
            @if (selectedOption) {
              <div style="display:flex;align-items:center;gap:.5rem">
                <i [class]="'pi ' + selectedOption.value" style="font-size:1.1rem"></i>
                <span>{{ selectedOption.label }}</span>
              </div>
            }
          </ng-template>
          <ng-template pTemplate="option" let-option>
            <div style="display:flex;align-items:center;gap:.5rem">
              <i [class]="'pi ' + option.value" style="font-size:1.1rem;width:20px;text-align:center"></i>
              <span>{{ option.label }}</span>
            </div>
          </ng-template>
        </p-select>
        <div [style.background]="form.color + '22'" style="width:38px;height:38px;border-radius:6px;display:flex;align-items:center;justify-content:center;border:1px solid var(--surface-border)">
          <i [class]="'pi ' + form.icono" [style.color]="form.color" style="font-size:1.3rem"></i>
        </div>
      </div>
    </div>
    <div class="field">
      <label>Color</label>
      <input pInputText [(ngModel)]="form.color" placeholder="#D02030" />
    </div>
    @if (form.criterio_tipo === 'AUTOMATICO') {
      <div class="field">
        <label>Métrica</label>
        <p-select [options]="metricasOpt" [(ngModel)]="form.criterio_metrica"
                  optionLabel="label" optionValue="value" placeholder="Métrica" 
 [filter]="true" filterPlaceholder="Buscar..."/>
      </div>
      <div class="field">
        <label>Valor umbral</label>
        <p-inputNumber [(ngModel)]="form.criterio_valor" [minFractionDigits]="1" />
      </div>
    }
  </div>
  <ng-template pTemplate="footer">
    <p-button label="Cancelar" severity="secondary" (onClick)="showDialog = false" />
    <p-button label="Guardar" icon="pi pi-check" (onClick)="guardar()" />
  </ng-template>
</p-dialog>

<!-- ── Dialog detalle badge (alumnos que lo tienen) ──────────────────────── -->
<p-dialog [(visible)]="showDetalle" [style]="{width:'640px'}"
          [header]="badgeDetalle()?.nombre || ''" [modal]="true">
  @if (badgeDetalle()) {
    <p-table [value]="badgeDetalle()!.alumnos || []" [rows]="10" [paginator]="true" size="small">
      <ng-template pTemplate="header">
        <tr>
          <th>Alumno</th><th>Matrícula</th><th>Grupo</th><th>Ciclo</th><th>Fecha</th>
        </tr>
      </ng-template>
      <ng-template pTemplate="body" let-a>
        <tr>
          <td>{{ a.nombre_alumno }}</td>
          <td>{{ a.matricula }}</td>
          <td>{{ a.grupo }}</td>
          <td>{{ a.ciclo }}</td>
          <td>{{ a.fecha_otorgado | date:'dd/MM/yyyy' }}</td>
        </tr>
      </ng-template>
      <ng-template pTemplate="emptymessage">
        <tr><td colspan="5" style="text-align:center;color:#999">Sin alumnos con esta insignia</td></tr>
      </ng-template>
    </p-table>
  }
</p-dialog>

<!-- ── Dialog otorgar manual ─────────────────────────────────────────────── -->
<p-dialog [(visible)]="showOtorgar" [style]="{width:'400px'}" header="Otorgar Insignia" [modal]="true">
  <div class="form-grid1">
    <div class="field">
      <label>Motivo (opcional)</label>
      <textarea pTextarea [(ngModel)]="motivoOtorgar" rows="3" placeholder="Motivo del reconocimiento..."></textarea>
    </div>
    <div class="field">
      <label>Ciclo escolar</label>
      <p-select [options]="ciclos()" optionLabel="nombre" optionValue="id"
                [(ngModel)]="cicloOtorgar" placeholder="Ciclo" 
 [filter]="true" filterPlaceholder="Buscar..."/>
    </div>
  </div>
  <ng-template pTemplate="footer">
    <p-button label="Cancelar" severity="secondary" (onClick)="showOtorgar = false" />
    <p-button label="Otorgar" icon="pi pi-check" (onClick)="confirmarOtorgar()" />
  </ng-template>
</p-dialog>
  `,
  styles: [`
    .page-header { display:flex; align-items:center; justify-content:space-between;
                   margin-bottom:1.25rem; flex-wrap:wrap; gap:.5rem; }
    .page-title  { display:flex; align-items:center; gap:.5rem; }
    .page-title h1 { font-size:1.35rem; font-family:'Jost',sans-serif; margin:0; font-weight:600; }
    .page-actions { display:flex; gap:.5rem; flex-wrap:wrap; }

    .badges-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(220px,1fr));
                   gap:1rem; padding:.5rem 0; }
    .badge-card  { border:1px solid var(--surface-border); border-radius:8px; padding:1rem;
                   background:var(--surface-card); display:flex; flex-direction:column; gap:.5rem;
                   transition:box-shadow .15s; }
    .badge-card:hover { box-shadow:0 2px 8px rgba(0,0,0,.1); }
    .badge-obtenido  { border-color:#27AE60; background:#f0fff4; }
    .badge-no-obtenido { opacity:.6; filter:grayscale(.6); }

    .badge-icon-wrap { width:60px; height:60px; border-radius:50%; display:flex;
                       align-items:center; justify-content:center; margin:0 auto; }
    .badge-info   { text-align:center; }
    .badge-nombre { font-weight:600; font-size:.95rem; margin-bottom:.2rem; }
    .badge-desc   { font-size:.8rem; color:var(--text-secondary); margin-bottom:.4rem; }
    .badge-meta   { display:flex; justify-content:center; gap:.3rem; flex-wrap:wrap; }
    .badge-criterio { font-size:.75rem; color:var(--text-secondary); margin-top:.25rem; }
    .badge-fecha  { font-size:.8rem; color:#27AE60; }
    .badge-motivo { font-size:.8rem; color:var(--text-secondary); font-style:italic; }
    .badge-pendiente { font-size:.8rem; color:#bbb; }
    .badge-footer { display:flex; align-items:center; justify-content:space-between;
                    margin-top:auto; padding-top:.5rem; border-top:1px solid var(--surface-border); }
    .badge-count  { font-size:.8rem; color:var(--text-secondary); display:flex; align-items:center; gap:.3rem; }

    .alumno-search-bar { display:flex; align-items:center; gap:1rem; margin-bottom:1rem; }
    .alumno-badge-stats { margin-bottom:1rem; }
    .stat-pill { display:inline-flex; align-items:center; gap:.4rem; background:var(--surface-ground);
                 border-radius:20px; padding:.35rem .75rem; font-size:.9rem; }

    .auto-eval-panel { max-width:600px; display:flex; flex-direction:column; gap:1.5rem; }
    .auto-eval-info  { display:flex; gap:1rem; align-items:flex-start; padding:1rem;
                       background:var(--surface-ground); border-radius:8px; }
    .auto-eval-info p { margin:.25rem 0 0; font-size:.85rem; color:var(--text-secondary); }
    .auto-eval-form  { display:flex; gap:.75rem; align-items:center; flex-wrap:wrap; }
    .auto-eval-result { display:flex; align-items:center; gap:1rem; padding:1rem;
                        background:#f0fff4; border:1px solid #27AE60; border-radius:8px; }

    .form-grid2  { display:grid; grid-template-columns:1fr 1fr; gap:.75rem; }
    .form-grid1  { display:flex; flex-direction:column; gap:.75rem; }
    .field       { display:flex; flex-direction:column; gap:.35rem; }
    .field.full  { grid-column:1/-1; }
    .field label { font-size:.82rem; font-weight:500; color:var(--text-secondary); }
    .field input, .field textarea { width:100%; }

    .empty-state { text-align:center; color:#bbb; padding:3rem; grid-column:1/-1;
                   font-size:1rem; }
  `],
})
export class BadgesComponent implements OnInit {
  private api     = inject(ApiService);
  private ctx     = inject(ContextService);
  private exporter = inject(ExportService);

  // ── state ──────────────────────────────────────────────────────────────────
  badges            = signal<Badge[]>([]);
  ciclos            = signal<any[]>([]);
  alumnosSugerencias = signal<AlumnoSugerencia[]>([]);
  badgesAlumno      = signal<BadgeAlumno[]>([]);
  badgeDetalle      = signal<(Badge & { alumnos: any[] }) | null>(null);
  alumnoSeleccionado = signal<AlumnoSugerencia | null>(null);
  autoEvaluando     = signal(false);
  resultadoAutoEval  = signal<{ total_otorgados: number; badges_evaluados: number } | null>(null);

  tabActivo    = 'catalogo';
  alumnoQuery  = '';
  cicloAutoEval: string | null = null;
  showDialog   = false;
  showDetalle  = false;
  showOtorgar  = false;
  motivoOtorgar = '';
  cicloOtorgar: string | null = null;
  badgeParaOtorgar: BadgeAlumno | null = null;

  form: any = this.defaultForm();

  tiposOpt = [
    { label: 'Asistencia',    value: 'ASISTENCIA' },
    { label: 'Académico',     value: 'ACADEMICO' },
    { label: 'Conducta',      value: 'CONDUCTA' },
    { label: 'Participación', value: 'PARTICIPACION' },
    { label: 'Especial',      value: 'ESPECIAL' },
  ];
  criterioOpt = [
    { label: 'Manual',     value: 'MANUAL' },
    { label: 'Automático', value: 'AUTOMATICO' },
  ];
  metricasOpt = [
    { label: '% Asistencia',      value: 'pct_asistencia' },
    { label: 'Promedio general',  value: 'promedio_general' },
    { label: 'Sin reportes cond.', value: 'sin_reportes_conducta' },
  ];
  iconsOpt = [
    { label: 'Estrella Rellena', value: 'pi-star-fill' },
    { label: 'Estrella Vacía', value: 'pi-star' },
    { label: 'Trofeo', value: 'pi-trophy' },
    { label: 'Corona', value: 'pi-crown' },
    { label: 'Escudo', value: 'pi-shield' },
    { label: 'Medalla / Premio', value: 'pi-award' },
    { label: 'Verificado', value: 'pi-verified' },
    { label: 'Destello / Chispas', value: 'pi-sparkles' },
    { label: 'Rayo', value: 'pi-bolt' },
    { label: 'Corazón Relleno', value: 'pi-heart-fill' },
    { label: 'Corazón', value: 'pi-heart' },
    { label: 'Bandera', value: 'pi-flag' },
    { label: 'Libro', value: 'pi-book' },
    { label: 'Graduación', value: 'pi-graduation-cap' },
    { label: 'Pulgar Arriba', value: 'pi-thumbs-up-fill' },
    { label: 'Ojo', value: 'pi-eye' },
  ];

  // ── init ───────────────────────────────────────────────────────────────────
  ngOnInit() {
    this.cargarBadges();
    this.cargarCiclos();
  }

  async cargarBadges() {
    const plantelId = this.ctx.plantel()?.id;
    const params = plantelId ? `?plantel_id=${plantelId}` : '';
    const data = await this.api.get<Badge[]>(`/badges${params}`).toPromise();
    this.badges.set(data ?? []);
  }

  async cargarCiclos() {
    const data = await this.api.get<any[]>('/catalogs/ciclos').toPromise();
    this.ciclos.set(data ?? []);
  }

  // ── búsqueda alumnos ────────────────────────────────────────────────────────
  async buscarAlumno(event: any) {
    const q = event.query;
    const plantelId = this.ctx.plantel()?.id;
    const params = new URLSearchParams({ q });
    if (plantelId) params.set('plantel_id', plantelId);
    const data = await this.api.get<any[]>(`/alumnos/buscar?${params}`).toPromise().catch(() => []);
    this.alumnosSugerencias.set(
      (data ?? []).map((a: any) => ({
        id:      a.id,
        nombre:  `${a.nombre} ${a.ap_paterno} ${a.ap_materno ?? ''}`.trim(),
        matricula: a.matricula,
        grupo:   a.nombre_grupo ?? '',
      }))
    );
  }

  async seleccionarAlumno(event: any) {
    const al: AlumnoSugerencia = event.value ?? event;
    this.alumnoSeleccionado.set(al);
    const data = await this.api.get<BadgeAlumno[]>(`/badges/alumno/${al.id}`).toPromise();
    this.badgesAlumno.set(data ?? []);
  }

  // ── catálogo ────────────────────────────────────────────────────────────────
  abrirNuevo() { this.form = this.defaultForm(); this.showDialog = true; }

  async guardar() {
    await this.api.post('/badges', this.form).toPromise();
    this.showDialog = false;
    this.cargarBadges();
  }

  async eliminar(b: Badge) {
    if (!confirm(`¿Eliminar la insignia "${b.nombre}"?`)) return;
    await this.api.delete(`/badges/${b.id}`).toPromise();
    this.cargarBadges();
  }

  async verBadge(b: Badge) {
    const data = await this.api.get<any>(`/badges/${b.id}`).toPromise();
    this.badgeDetalle.set(data ?? null);
    this.showDetalle = true;
  }

  // ── otorgar / revocar ────────────────────────────────────────────────────────
  otorgarManual(b: BadgeAlumno) {
    this.badgeParaOtorgar = b;
    this.motivoOtorgar    = '';
    this.cicloOtorgar     = this.ciclos().length ? this.ciclos()[0].id : null;
    this.showOtorgar      = true;
  }

  async confirmarOtorgar() {
    const al = this.alumnoSeleccionado();
    if (!this.badgeParaOtorgar || !al) return;
    await this.api.post(`/badges/${this.badgeParaOtorgar.id}/otorgar`, {
      estudiante_id: al.id,
      ciclo_id:      this.cicloOtorgar,
      motivo:        this.motivoOtorgar || null,
    }).toPromise();
    this.showOtorgar = false;
    await this.seleccionarAlumno({ value: al });
  }

  async revocar(b: BadgeAlumno) {
    const al = this.alumnoSeleccionado();
    if (!al || !confirm('¿Revocar esta insignia?')) return;
    await this.api.delete(`/badges/${b.id}/otorgados/${al.id}`).toPromise();
    await this.seleccionarAlumno({ value: al });
  }

  // ── auto-evaluación ──────────────────────────────────────────────────────────
  async autoEvaluar() {
    if (!this.cicloAutoEval) return;
    this.autoEvaluando.set(true);
    this.resultadoAutoEval.set(null);
    try {
      const r = await this.api.post<any>(`/badges/auto-evaluar/${this.cicloAutoEval}`, {}).toPromise();
      this.resultadoAutoEval.set(r ?? null);
      this.cargarBadges();
    } finally {
      this.autoEvaluando.set(false);
    }
  }

  // ── exportar ─────────────────────────────────────────────────────────────────
  exportCSV() {
    const cols: ExportColumn[] = [
      { field: 'nombre',        header: 'Insignia' },
      { field: 'tipo',          header: 'Tipo' },
      { field: 'criterio_tipo', header: 'Criterio' },
      { field: 'criterio_metrica', header: 'Métrica' },
      { field: 'criterio_valor',   header: 'Umbral' },
      { field: 'total_otorgados',  header: 'Otorgadas' },
    ];
    this.exporter.toCSV(this.badges(), cols, 'badges');
  }

  // ── helpers ───────────────────────────────────────────────────────────────────
  tipoSeverity(tipo: string): TagSeverity {
    const m: Record<string, TagSeverity> = {
      ASISTENCIA:    'success',
      ACADEMICO:     'warn',
      CONDUCTA:      'info',
      PARTICIPACION: 'secondary',
      ESPECIAL:      'danger',
    };
    return m[tipo] ?? 'secondary';
  }

  metricaLabel(m: string): string {
    const map: Record<string, string> = {
      pct_asistencia:        'Asistencia',
      promedio_general:      'Promedio',
      sin_reportes_conducta: 'Sin conducta',
    };
    return map[m] ?? m;
  }

  defaultForm() {
    return { nombre: '', descripcion: '', icono: 'pi-star', color: '#D02030',
             tipo: 'ESPECIAL', criterio_tipo: 'MANUAL', criterio_metrica: null, criterio_valor: null };
  }
}
