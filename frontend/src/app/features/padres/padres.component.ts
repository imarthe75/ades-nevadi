import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TabsModule } from 'primeng/tabs';
import { TabList, Tab, TabPanels, TabPanel } from 'primeng/tabs';
import { ProgressBarModule } from 'primeng/progressbar';
import { TooltipModule } from 'primeng/tooltip';
import { SkeletonModule } from 'primeng/skeleton';
import { CardModule } from 'primeng/card';
import { SelectModule } from 'primeng/select';
import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';

interface AlumnoVinculado {
  estudiante_id: string;
  nombre_completo: string;
  matricula: string;
  nivel: string;
  grado: string;
  grupo: string;
  plantel: string;
  parentesco: string;
  es_tutor_legal: boolean;
}

interface ResumenAlumno {
  promedio_general: number | null;
  pct_asistencia: number | null;
  tareas_pendientes: number;
  badges_count: number;
  alertas: any[];
}

interface CalificacionResumen {
  materia: string;
  periodo: string;
  calificacion: number | null;
  es_acreditado: boolean;
}

@Component({
  selector: 'app-padres',
  standalone: true,
  imports: [
    CommonModule, RouterModule, FormsModule, ButtonModule, TagModule, TooltipModule,
    TabsModule, TabList, Tab, TabPanels, TabPanel,
    ProgressBarModule, SkeletonModule, CardModule, SelectModule,
    InteractiveGridComponent,
  ],
  template: `
    <div class="page-header">
      <div>
        <h2>Portal de Padres de Familia</h2>
        <p class="subtitle">Seguimiento académico de su(s) alumno(s)</p>
      </div>
    </div>

    <!-- Selector de alumno para administradores (vista de padre) -->
    @if (isAdmin()) {
      <div class="admin-sim-bar">
        <div class="sim-label"><i class="pi pi-shield"></i> Vista de administrador — Seleccionar alumno:</div>
        <p-select
          [options]="todosAlumnos()"
          [(ngModel)]="simulatedAlumno"
          optionLabel="nombre_completo"
          placeholder="Buscar alumno..."
          [filter]="true"
          filterBy="nombre_completo,matricula"
          styleClass="sim-select"
          (onChange)="onSimulateChange($event.value)"
        />
      </div>
    }

    @if (loading()) {
      <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:1rem">
        @for (i of [1,2]; track i) { <p-skeleton height="200px" /> }
      </div>
    } @else if (alumnos().length === 0 && !simulatedAlumno) {
      <div class="empty-state">
        <i class="pi pi-users" style="font-size:3rem;color:var(--text-muted)"></i>
        <h3>Sin alumnos vinculados</h3>
        <p>Su cuenta no tiene alumnos asociados. Contacte a la administración del plantel.</p>
      </div>
    } @else {
      <!-- Tarjetas de alumnos vinculados -->
      @if (alumnos().length > 0) {
        <div class="alumnos-grid">
          @for (alumno of alumnos(); track alumno.estudiante_id) {
            <div class="alumno-card"
              [class.selected]="alumnoActivo()?.estudiante_id === alumno.estudiante_id"
              (click)="seleccionarAlumno(alumno)">
              <div class="alumno-avatar" [class.femenino]="alumno.nombre_completo.includes('a')">
                {{ alumno.nombre_completo.split(' ')[0][0] }}{{ alumno.nombre_completo.split(' ')[1]?.[0] || '' }}
              </div>
              <div class="alumno-info">
                <strong>{{ alumno.nombre_completo }}</strong>
                <span class="mat">{{ alumno.matricula }}</span>
                <span class="nivel-badge">{{ alumno.nivel | titlecase }} · {{ alumno.grado }}</span>
              </div>
              @if (alumno.es_tutor_legal) {
                <p-tag value="Tutor legal" severity="success" styleClass="ml-auto" />
              }
            </div>
          }
        </div>
      }

      @if (alumnoActivo()) {
        <!-- Detalle del alumno seleccionado -->
        <div class="detalle-header">
          <h3>{{ alumnoActivo()!.nombre_completo }}</h3>
          <span class="plantel-chip">{{ alumnoActivo()!.plantel }}</span>
          <span class="grupo-chip">{{ alumnoActivo()!.nivel | titlecase }} · {{ alumnoActivo()!.grado }} / Grupo {{ alumnoActivo()!.grupo }}</span>
        </div>

        @if (loadingDetalle()) {
          <p-skeleton height="300px" />
        } @else if (resumen()) {
          <!-- KPIs -->
          <div class="kpi-bar">
            <div class="kpi-item">
              <span class="kval" [class.alto]="(resumen()!.promedio_general ?? 0) >= 8"
                [class.bajo]="(resumen()!.promedio_general ?? 10) < 6">
                {{ resumen()!.promedio_general?.toFixed(1) ?? '—' }}
              </span>
              <span class="klbl">Promedio general</span>
            </div>
            <div class="kpi-sep"></div>
            <div class="kpi-item">
              <span class="kval">{{ resumen()!.pct_asistencia?.toFixed(0) ?? '—' }}%</span>
              <span class="klbl">Asistencia</span>
            </div>
            <div class="kpi-sep"></div>
            <div class="kpi-item">
              <span class="kval" [class.bajo]="(resumen()!.tareas_pendientes) > 0">
                {{ resumen()!.tareas_pendientes }}
              </span>
              <span class="klbl">Tareas pendientes</span>
            </div>
            <div class="kpi-sep"></div>
            <div class="kpi-item">
              <span class="kval alto">{{ resumen()!.badges_count }}</span>
              <span class="klbl">Insignias obtenidas</span>
            </div>
          </div>

          @if (resumen()!.alertas.length > 0) {
            <div class="alertas-bar">
              @for (a of resumen()!.alertas; track a.tipo_alerta) {
                <div class="alerta-item" [class.riesgo-alto]="a.nivel_riesgo === 'ALTO'">
                  <i class="pi pi-exclamation-triangle"></i>
                  {{ a.descripcion }}
                </div>
              }
            </div>
          }

          <!-- Calificaciones -->
          <p-tabs value="calificaciones">
            <p-tablist>
              <p-tab value="calificaciones"><i class="pi pi-star"></i> Calificaciones</p-tab>
              <p-tab value="asistencias"><i class="pi pi-check-square"></i> Asistencia</p-tab>
              <p-tab value="tareas"><i class="pi pi-file-edit"></i> Tareas</p-tab>
              <p-tab value="conducta"><i class="pi pi-exclamation-circle"></i> Conducta</p-tab>
            </p-tablist>

            <p-tabpanels>
              <!-- Calificaciones -->
              <p-tabpanel value="calificaciones">
                <app-interactive-grid
                  [data]="calificacionesFlat()"
                  [columns]="calificacionesColumns"
                />
              </p-tabpanel>

              <!-- Asistencia (placeholder) -->
              <p-tabpanel value="asistencias">
                <div class="asist-summary">
                  <div class="asist-bar-wrap">
                    <span class="asist-lbl">Asistencia del ciclo</span>
                    <p-progressBar
                      [value]="resumen()!.pct_asistencia ?? 0"
                      [style]="{height:'20px'}"
                      [styleClass]="(resumen()!.pct_asistencia ?? 0) >= 85 ? 'asist-ok' : 'asist-low'" />
                    <span class="asist-pct">{{ resumen()!.pct_asistencia?.toFixed(1) ?? 0 }}%</span>
                  </div>
                  <p class="asist-nota">
                    Para ver el detalle por clase, consulte con el tutor o director del plantel.
                  </p>
                </div>
              </p-tabpanel>

              <!-- Tareas -->
              <p-tabpanel value="tareas">
                @if (tareasAlumno().length === 0) {
                  <div style="padding:2rem;text-align:center;color:var(--text-color-secondary)">
                    <i class="pi pi-check-circle" style="font-size:2rem;display:block;margin-bottom:.5rem;color:var(--color-success)"></i>
                    <p>Sin tareas pendientes. ¡Excelente!</p>
                  </div>
                } @else {
                  <app-interactive-grid
                    [data]="tareasFlat()"
                    [columns]="tareasColumns"
                  />
                }
              </p-tabpanel>

              <!-- Conducta -->
              <p-tabpanel value="conducta">
                @if (conductaAlumno().length === 0) {
                  <div style="padding:2rem;text-align:center;color:var(--text-color-secondary)">
                    <i class="pi pi-shield" style="font-size:2rem;display:block;margin-bottom:.5rem;color:var(--color-success)"></i>
                    <p>Sin reportes de conducta en el ciclo actual.</p>
                  </div>
                } @else {
                  <app-interactive-grid
                    [data]="conductaFlat()"
                    [columns]="conductaColumns"
                  />
                }
              </p-tabpanel>
            </p-tabpanels>
          </p-tabs>
        }
      }
    }
  `,
  styles: [`
    .page-header { margin-bottom: 1.5rem; }
    .page-header h2 { margin: 0; }
    .subtitle { font-size: .82rem; color: var(--text-color-secondary); margin: 0; }

    .admin-sim-bar {
      display: flex; align-items: center; justify-content: space-between; gap: 1rem;
      background: var(--surface-100); border-left: 4px solid var(--primary-color);
      border-radius: 6px; padding: 0.75rem 1rem; margin-bottom: 1.5rem;
    }
    .sim-label { font-size: 0.9rem; font-weight: 600; color: var(--text-color); }
    ::ng-deep .sim-select { width: 350px; }

    .alumnos-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px,1fr)); gap: .75rem; margin-bottom: 1.5rem; }
    .alumno-card {
      display: flex; align-items: center; gap: .75rem;
      background: var(--surface-0); border: 2px solid var(--surface-200);
      border-radius: 10px; padding: .75rem 1rem; cursor: pointer;
      transition: border-color .15s, box-shadow .15s;
    }
    .alumno-card:hover { border-color: var(--primary-200); box-shadow: 0 2px 8px rgba(0,0,0,.06); }
    .alumno-card.selected { border-color: var(--primary-color); background: var(--primary-50); }
    .alumno-avatar {
      width: 46px; height: 46px; border-radius: 50%; flex-shrink: 0;
      background: var(--primary-color); color: #fff;
      display: flex; align-items: center; justify-content: center;
      font-weight: 700; font-size: 1rem;
    }
    .alumno-avatar.femenino { background: #ec4899; }
    .alumno-info { display: flex; flex-direction: column; gap: .1rem; }
    .alumno-info strong { font-size: .9rem; }
    .mat { font-size: .72rem; font-family: monospace; color: var(--text-secondary); }
    .nivel-badge { font-size: .72rem; color: var(--text-secondary); }

    .detalle-header { display: flex; align-items: center; gap: .75rem; margin-bottom: 1rem; flex-wrap: wrap; }
    .detalle-header h3 { margin: 0; font-size: 1.1rem; }
    .plantel-chip, .grupo-chip {
      font-size: .78rem; background: var(--surface-100); padding: .2rem .6rem;
      border-radius: 20px; color: var(--text-secondary);
    }

    .kpi-bar {
      display: flex; align-items: center; background: var(--surface-50);
      border: 1px solid var(--surface-200); border-radius: 10px;
      padding: 1rem; margin-bottom: 1rem; flex-wrap: wrap; gap: .5rem;
    }
    .kpi-item { display: flex; flex-direction: column; align-items: center; min-width: 100px; }
    .kval { font-size: 1.6rem; font-weight: 700; color: var(--text-primary); }
    .kval.alto { color: var(--color-success, #16a34a); }
    .kval.bajo { color: var(--color-danger, #dc2626); }
    .klbl { font-size: .72rem; color: var(--text-secondary); }
    .kpi-sep { width: 1px; height: 40px; background: var(--surface-200); }

    .alertas-bar { display: flex; flex-direction: column; gap: .4rem; margin-bottom: 1rem; }
    .alerta-item {
      background: #fef3c7; border: 1px solid #fbbf24; border-radius: 6px;
      padding: .5rem .75rem; font-size: .82rem; color: #92400e;
      display: flex; align-items: center; gap: .5rem;
    }
    .alerta-item.riesgo-alto { background: #fee2e2; border-color: #f87171; color: #991b1b; }

    .asist-summary { padding: 1.5rem; }
    .asist-bar-wrap { display: flex; align-items: center; gap: 1rem; margin-bottom: 1rem; }
    .asist-lbl { font-size: .85rem; font-weight: 600; min-width: 150px; }
    .asist-pct { font-weight: 700; min-width: 45px; }
    :host ::ng-deep .asist-ok .p-progressbar-value { background: #22c55e; }
    :host ::ng-deep .asist-low .p-progressbar-value { background: #f97316; }
    .asist-nota { font-size: .8rem; color: var(--text-secondary); margin: 0; }

    .empty-state { display: flex; flex-direction: column; align-items: center; gap: .5rem; padding: 4rem; color: var(--text-muted); text-align: center; }
    .empty-state h3 { color: var(--text-secondary); margin: 0; }
  `],
})
export class PadresComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly ctx = inject(ContextService);

  alumnos        = signal<AlumnoVinculado[]>([]);
  todosAlumnos   = signal<any[]>([]);
  alumnoActivo   = signal<AlumnoVinculado | null>(null);
  resumen        = signal<ResumenAlumno | null>(null);
  calificaciones = signal<CalificacionResumen[]>([]);
  tareasAlumno   = signal<any[]>([]);
  conductaAlumno = signal<any[]>([]);
  loading        = signal(true);
  loadingDetalle = signal(false);

  simulatedAlumno: any = null;

  readonly calificacionesFlat = computed(() =>
    this.calificaciones().map(c => ({
      ...c,
      cal_str:     c.calificacion !== null ? Number(c.calificacion).toFixed(1) : '—',
      estado_str:  c.es_acreditado ? 'Acredita' : 'No acredita',
    }))
  );
  readonly calificacionesColumns: ColumnConfig[] = [
    { field: 'materia',    header: 'Materia' },
    { field: 'periodo',    header: 'Período',      width: '140px' },
    { field: 'cal_str',    header: 'Calificación', width: '110px' },
    { field: 'estado_str', header: 'Estado',       width: '110px' },
  ];

  readonly tareasFlat = computed(() =>
    this.tareasAlumno().map(t => ({
      ...t,
      fecha_str:   t.fecha_limite ? new Date(t.fecha_limite).toLocaleDateString('es-MX') : '—',
      estado_str:  t.estatus_entrega === 'PENDIENTE' ? 'Pendiente' : 'Entregada',
    }))
  );
  readonly tareasColumns: ColumnConfig[] = [
    { field: 'titulo',     header: 'Tarea' },
    { field: 'fecha_str',  header: 'Fecha límite', width: '120px' },
    { field: 'estado_str', header: 'Estado',       width: '110px' },
  ];

  readonly conductaFlat = computed(() =>
    this.conductaAlumno().map(r => ({
      ...r,
      fecha_str: r.fecha_reporte ? new Date(r.fecha_reporte).toLocaleDateString('es-MX') : '—',
    }))
  );
  readonly conductaColumns: ColumnConfig[] = [
    { field: 'fecha_str',  header: 'Fecha',       width: '110px' },
    { field: 'tipo_falta', header: 'Tipo',        width: '100px' },
    { field: 'descripcion',header: 'Descripción' },
  ];

  isAdmin(): boolean {
    return this.ctx.nivelAcceso() <= 1;
  }

  ngOnInit(): void {
    if (this.isAdmin()) {
      // Cargar lista de alumnos para simulación
      this.api.get<{ data: any[] }>('/alumnos', { por_pagina: 1000 }).subscribe({
        next: resp => {
          this.todosAlumnos.set(
            resp.data.map(x => ({
              estudiante_id: x.id,
              nombre_completo: `${x.persona?.apellido_paterno || ''} ${x.persona?.apellido_materno || ''} ${x.persona?.nombre || ''}`.trim(),
              matricula: x.matricula,
              nivel: x.grupo?.nivel_nombre || 'Primaria',
              grado: String(x.grupo?.grado || '1'),
              grupo: x.grupo?.nombre_grupo || 'A',
              plantel: x.plantel?.nombre_plantel || 'Nevadi',
              parentesco: 'Admin',
              es_tutor_legal: true
            }))
          );
        }
      });
    }

    this.api.get<AlumnoVinculado[]>('/padres/mis-alumnos').subscribe({
      next: lista => {
        this.alumnos.set(lista);
        this.loading.set(false);
        if (lista.length === 1) this.seleccionarAlumno(lista[0]);
      },
      error: () => this.loading.set(false),
    });
  }

  onSimulateChange(alumno: AlumnoVinculado): void {
    if (alumno) {
      this.seleccionarAlumno(alumno);
    }
  }

  seleccionarAlumno(alumno: AlumnoVinculado): void {
    this.alumnoActivo.set(alumno);
    this.loadingDetalle.set(true);
    this.resumen.set(null);
    this.calificaciones.set([]);
    this.tareasAlumno.set([]);
    this.conductaAlumno.set([]);

    // Cargar resumen del portal (reutiliza el endpoint existente)
    this.api.get<any>(`/portal/alumno/${alumno.estudiante_id}`).subscribe({
      next: data => {
        this.resumen.set({
          promedio_general: data.kpis?.promedio_general ?? null,
          pct_asistencia: data.kpis?.pct_asistencia ?? null,
          tareas_pendientes: data.kpis?.tareas_pendientes ?? 0,
          badges_count: data.kpis?.badges_count ?? 0,
          alertas: data.alertas ?? [],
        });
        this.loadingDetalle.set(false);
      },
      error: () => this.loadingDetalle.set(false),
    });

    // Calificaciones
    this.api.get<CalificacionResumen[]>(`/padres/calificaciones/${alumno.estudiante_id}`).subscribe({
      next: c => this.calificaciones.set(c),
      error: () => {},
    });

    // Tareas pendientes del alumno
    this.api.get<any[]>(`/entregas/alumno/${alumno.estudiante_id}`, { solo_pendientes: false }).subscribe({
      next: e => this.tareasAlumno.set(e),
      error: () => {},
    });

    // Reportes de conducta del alumno
    this.api.get<any[]>('/conducta', { estudiante_id: alumno.estudiante_id }).subscribe({
      next: r => this.conductaAlumno.set(r),
      error: () => {},
    });
  }
}
