import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { DialogModule } from 'primeng/dialog';
import { DrawerModule } from 'primeng/drawer';
import { TabsModule } from 'primeng/tabs';
import { ProgressBarModule } from 'primeng/progressbar';
import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService } from '../../core/services/export.service';
import { ApexNotificationService } from 'apex-component-library';

interface Actividad {
  id: string;
  titulo: string;
  tipo_item: string;
  fecha_entrega: string;
  fecha_examen: string | null;
  puntaje_maximo: number;
  nombre_materia: string;
  nombre_periodo: string;
  total_alumnos: number;
  entregadas: number;
  calificadas: number;
}

interface Entrega {
  id: string;
  estudiante_id: string;
  alumno_nombre: string;
  numero_matricula: string;
  estatus_entrega: string;
  calificacion_obtenida: number | null;
  comentario_profesor: string | null;
  archivo_url: string | null;
  _cal?: number | null;
}

interface CalPeriodo {
  alumno: string;
  numero_matricula: string;
  nombre_materia: string;
  calificacion_final: number | null;
  calificacion_calculada: number | null;
  ajuste_manual: number | null;
  score_por_item: Record<string, number>;
  cerrada: boolean;
  en_riesgo: boolean;
  minimo_aprobatorio: number;
  escala_maxima: number;
  cal_periodo_id: string;
}

interface GrupoOpt { id: string; nombre_grupo: string; }
interface MateriaOpt { id: string; nombre_materia: string; }
interface PeriodoOpt { id: string; nombre_periodo: string; }

@Component({
  selector: 'app-gradebook',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TableModule, ButtonModule, SelectModule,
    InputTextModule, InputNumberModule, TagModule, TooltipModule,
    DialogModule, DrawerModule, TabsModule, ProgressBarModule,
  ],
  template: `
<div class="page-header">
  <div class="page-title">
    <i class="pi pi-book"></i> Gradebook — Calificaciones
  </div>
  <div class="page-actions">
    <button pButton icon="pi pi-refresh" label="Recalcular" severity="secondary"
            (click)="recalcularPeriodo()" [disabled]="!periodoSel"></button>
    <button pButton icon="pi pi-file-excel" label="Excel" severity="success"
            (click)="exportarExcel()"></button>
    <button pButton icon="pi pi-plus" label="Nueva actividad"
            (click)="mostrarNuevaActividad = true"></button>
  </div>
</div>

<!-- Filtros -->
<div class="filter-bar">
  <p-select [options]="grupos()" optionLabel="nombre_grupo" optionValue="id"
            placeholder="Grupo" [(ngModel)]="grupoSel"
            (onChange)="onGrupoChange()" 
 [filter]="true" filterPlaceholder="Buscar..."/>
  <p-select [options]="materias()" optionLabel="nombre_materia" optionValue="id"
            placeholder="Materia" [(ngModel)]="materiaSel"
            (onChange)="cargarActividades()" [disabled]="!grupoSel" 
 [filter]="true" filterPlaceholder="Buscar..."/>
  <p-select [options]="periodos()" optionLabel="nombre_periodo" optionValue="id"
            placeholder="Período" [(ngModel)]="periodoSel"
            (onChange)="cargarConcentrado()" [disabled]="!grupoSel" 
 [filter]="true" filterPlaceholder="Buscar..."/>
</div>

<p-tabs value="0">
  <!-- ── TAB 1: Spreadsheet de actividades ── -->
  <p-tabpanel header="Actividades" value="0">
    <p-table [value]="actividades()" [loading]="cargando()"
             styleClass="p-datatable-sm" [rows]="50" [paginator]="actividades().length > 50">
      <ng-template pTemplate="header">
        <tr>
          <th>Actividad</th>
          <th style="width:100px">Tipo</th>
          <th style="width:130px">Fecha entrega</th>
          <th style="width:80px" pTooltip="Entregadas/Total">Entregas</th>
          <th style="width:80px">Calificadas</th>
          <th style="width:100px"></th>
        </tr>
      </ng-template>
      <ng-template pTemplate="body" let-act>
        <tr>
          <td>{{ act.titulo }}<br>
            <small class="text-muted">{{ act.nombre_materia }}</small></td>
          <td><p-tag [value]="act.tipo_item" [severity]="tipoSeverity(act.tipo_item)" /></td>
          <td>{{ act.fecha_entrega | date:'dd/MM/yyyy' }}</td>
          <td>{{ act.entregadas }}/{{ act.total_alumnos }}
            <p-progressBar [value]="pctEntregas(act)" [style]="{'height':'4px','margin-top':'4px'}" [showValue]="false" /></td>
          <td>{{ act.calificadas }}</td>
          <td>
            <button pButton icon="pi pi-pencil" text size="small"
                    pTooltip="Calificar" (click)="abrirCalificacion(act)"></button>
          </td>
        </tr>
      </ng-template>
      <ng-template pTemplate="emptymessage">
        <tr><td colspan="6" class="text-center p-4 text-muted">
          Selecciona un grupo para ver actividades
        </td></tr>
      </ng-template>
    </p-table>
  </p-tabpanel>

  <!-- ── TAB 2: Concentrado de calificaciones ── -->
  <p-tabpanel header="Concentrado por período" value="1">
    <p-table [value]="concentrado()" [loading]="cargandoConc()"
             styleClass="p-datatable-sm" [rows]="100">
      <ng-template pTemplate="header">
        <tr>
          <th>Alumno</th>
          <th>Matrícula</th>
          <th>Materia</th>
          <th style="width:90px">Calculada</th>
          <th style="width:90px">Ajuste</th>
          <th style="width:90px">Final</th>
          <th style="width:80px">Estado</th>
          <th style="width:60px"></th>
        </tr>
      </ng-template>
      <ng-template pTemplate="body" let-row>
        <tr [class.row-riesgo]="row.en_riesgo">
          <td>{{ row.alumno }}</td>
          <td>{{ row.numero_matricula }}</td>
          <td>{{ row.nombre_materia }}</td>
          <td class="text-center">
            <span [class]="calClass(row.calificacion_calculada, row.escala_maxima)">
              {{ row.calificacion_calculada ?? '—' }}
            </span>
          </td>
          <td class="text-center">{{ row.ajuste_manual ?? '—' }}</td>
          <td class="text-center">
            <strong [class]="calClass(row.calificacion_final, row.escala_maxima)">
              {{ row.calificacion_final ?? '—' }}
            </strong>
          </td>
          <td>
            <p-tag [value]="row.cerrada ? 'Cerrada' : 'Abierta'"
                   [severity]="row.cerrada ? 'secondary' : 'info'" />
          </td>
          <td>
            <button pButton icon="pi pi-sliders-h" text size="small"
                    pTooltip="Ajuste manual" (click)="abrirAjuste(row)"
                    [disabled]="row.cerrada"></button>
          </td>
        </tr>
      </ng-template>
    </p-table>
  </p-tabpanel>

  <!-- ── TAB 3: Cobertura curricular ── -->
  <p-tabpanel header="Cobertura curricular" value="2">
    @if (cobertura()) {
      <div class="kpi-row">
        <div class="kpi-card">
          <div class="kpi-value">{{ cobertura()?.pct_cobertura }}%</div>
          <div class="kpi-label">Cobertura</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-value text-success">{{ cobertura()?.con_evidencia }}</div>
          <div class="kpi-label">Con evidencia</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-value text-danger">{{ cobertura()?.sin_evidencia }}</div>
          <div class="kpi-label">Sin evidencia</div>
        </div>
      </div>
    }
    <p-table [value]="cobertura()?.temas || []" styleClass="p-datatable-sm" [rows]="100">
      <ng-template pTemplate="header">
        <tr>
          <th>#</th>
          <th>Tema</th>
          <th>Materia</th>
          <th>Actividades</th>
          <th>Evidencia</th>
        </tr>
      </ng-template>
      <ng-template pTemplate="body" let-t>
        <tr>
          <td>{{ t.orden }}</td>
          <td>{{ t.nombre_tema }}</td>
          <td>{{ t.nombre_materia }}</td>
          <td>{{ t.num_actividades }}</td>
          <td>
            <p-tag [value]="t.tiene_evidencia ? 'Sí' : 'Sin evidencia'"
                   [severity]="t.tiene_evidencia ? 'success' : 'warn'" />
          </td>
        </tr>
      </ng-template>
    </p-table>
  </p-tabpanel>
</p-tabs>

<!-- ── Drawer: calificar actividad ── -->
<p-drawer [(visible)]="drawerCalifVisible" header="Calificar actividad"
           position="right" [style]="{width:'540px'}">
  @if (actividadSeleccionada()) {
    <div>
      <div class="mb-3">
        <strong>{{ actividadSeleccionada()?.titulo }}</strong><br>
        <small>{{ actividadSeleccionada()?.nombre_materia }} — {{ actividadSeleccionada()?.nombre_periodo }}</small>
      </div>
      <p-table [value]="entregasActiva()" [loading]="cargandoEntregas()"
               styleClass="p-datatable-sm">
        <ng-template pTemplate="header">
          <tr>
            <th>Alumno</th>
            <th>Estado</th>
            <th style="width:120px">Calificación</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-e>
          <tr>
            <td>{{ e.alumno_nombre }}</td>
            <td><p-tag [value]="e.estatus_entrega" [severity]="estatusSeverity(e.estatus_entrega)" /></td>
            <td>
              <p-inputNumber [(ngModel)]="e._cal" [min]="0" [max]="actividadSeleccionada()?.puntaje_maximo"
                             [step]="0.1" [useGrouping]="false" styleClass="w-full" inputStyleClass="p-inputtext-sm" />
            </td>
          </tr>
        </ng-template>
      </p-table>
      <div class="mt-3 flex gap-2 justify-end">
        <button pButton label="Guardar calificaciones" icon="pi pi-save"
                (click)="guardarCalificacionMasiva()"></button>
      </div>
    </div>
  }
</p-drawer>

<!-- ── Dialog: ajuste manual ── -->
<p-dialog [(visible)]="dialogAjusteVisible" header="Ajuste manual" [modal]="true" [style]="{width:'420px'}">
  @if (rowAjuste()) {
    <div>
      <p><strong>{{ rowAjuste()?.alumno }}</strong> — {{ rowAjuste()?.nombre_materia }}</p>
      <p>Calificación calculada: <strong>{{ rowAjuste()?.calificacion_calculada }}</strong></p>
      <div class="field mt-3">
        <label>Nueva calificación</label>
        <p-inputNumber [(ngModel)]="ajusteValor" [min]="0" [max]="rowAjuste()?.escala_maxima"
                       [step]="0.1" [useGrouping]="false" styleClass="w-full" />
      </div>
      <div class="field mt-2">
        <label>Justificación (mín. 20 caracteres)</label>
        <textarea pInputText [(ngModel)]="ajusteJustificacion" rows="3"
                  class="w-full" style="width:100%;resize:vertical;padding:6px;border:1px solid #ccc;border-radius:4px"></textarea>
        <small class="text-muted">{{ ajusteJustificacion.length }} caracteres</small>
      </div>
    </div>
  }
  <ng-template pTemplate="footer">
    <button pButton label="Cancelar" severity="secondary" (click)="dialogAjusteVisible = false"></button>
    <button pButton label="Aplicar ajuste" (click)="aplicarAjuste()"></button>
  </ng-template>
</p-dialog>

<!-- ── Dialog: nueva actividad ── -->
<p-dialog [(visible)]="mostrarNuevaActividad" header="Nueva actividad evaluable"
          [modal]="true" [style]="{width:'560px'}">
  <div class="grid grid-cols-2 gap-3">
    <div class="field col-span-2">
      <label>Título *</label>
      <input pInputText [(ngModel)]="nuevaAct.titulo" class="w-full" />
    </div>
    <div class="field">
      <label>Tipo</label>
      <p-select [options]="tiposItem" optionLabel="label" optionValue="value"
                [(ngModel)]="nuevaAct.tipo_item" styleClass="w-full" 
 [filter]="true" filterPlaceholder="Buscar..."/>
    </div>
    <div class="field">
      <label>Puntaje máximo</label>
      <p-inputNumber [(ngModel)]="nuevaAct.puntaje_maximo" [min]="0" [step]="0.1"
                     styleClass="w-full" />
    </div>
    <div class="field">
      <label>Fecha asignación</label>
      <input pInputText type="date" [(ngModel)]="nuevaAct.fecha_asignacion" class="w-full" />
    </div>
    <div class="field">
      <label>Fecha entrega</label>
      <input pInputText type="date" [(ngModel)]="nuevaAct.fecha_entrega" class="w-full" />
    </div>
    <div class="field col-span-2">
      <label>Descripción</label>
      <textarea pInputText [(ngModel)]="nuevaAct.descripcion" rows="2"
                class="w-full" style="width:100%;padding:6px;border:1px solid #ccc;border-radius:4px"></textarea>
    </div>
  </div>
  <ng-template pTemplate="footer">
    <button pButton label="Cancelar" severity="secondary" (click)="mostrarNuevaActividad = false"></button>
    <button pButton label="Crear y generar slots" icon="pi pi-plus" (click)="crearActividad()"></button>
  </ng-template>
</p-dialog>
  `,
  styles: [`
    .filter-bar { display:flex; gap:8px; flex-wrap:wrap; margin-bottom:16px; }
    .row-riesgo td { background:#fff5f5 !important; }
    .kpi-row { display:flex; gap:16px; margin-bottom:16px; }
    .kpi-card { background:var(--p-surface-0); border:1px solid var(--p-surface-border);
                border-radius:8px; padding:16px; min-width:120px; text-align:center; }
    .kpi-value { font-size:2rem; font-weight:700; font-family:'Jost',sans-serif; }
    .cal-excelente { color:#15803d; font-weight:600; }
    .cal-bien      { color:#0369a1; font-weight:600; }
    .cal-regular   { color:#b45309; font-weight:600; }
    .cal-reprobado { color:#dc2626; font-weight:600; }
    :host ::ng-deep .p-datatable-sm .p-datatable-tbody > tr > td { padding:4px 8px; }
  `],
})
export class GradebookComponent implements OnInit {
  private api = inject(ApiService);
  private ctx = inject(ContextService);
  private exporter = inject(ExportService);
  private readonly notify = inject(ApexNotificationService);

  grupos = signal<GrupoOpt[]>([]);
  materias = signal<MateriaOpt[]>([]);
  periodos = signal<PeriodoOpt[]>([]);
  actividades = signal<Actividad[]>([]);
  concentrado = signal<CalPeriodo[]>([]);
  entregasActiva = signal<Entrega[]>([]);
  cobertura = signal<any>(null);
  actividadSeleccionada = signal<Actividad | null>(null);
  rowAjuste = signal<CalPeriodo | null>(null);

  cargando = signal(false);
  cargandoConc = signal(false);
  cargandoEntregas = signal(false);

  grupoSel: string | null = null;
  materiaSel: string | null = null;
  periodoSel: string | null = null;

  drawerCalifVisible = false;
  dialogAjusteVisible = false;
  mostrarNuevaActividad = false;
  ajusteValor: number | null = null;
  ajusteJustificacion = '';

  tiposItem = [
    { label: 'Tarea', value: 'tarea' },
    { label: 'Examen', value: 'examen' },
    { label: 'Proyecto', value: 'proyecto' },
    { label: 'Laboratorio', value: 'laboratorio' },
    { label: 'Participación', value: 'participacion' },
    { label: 'Otro', value: 'otro' },
  ];

  nuevaAct = {
    titulo: '', descripcion: '', tipo_item: 'tarea',
    fecha_asignacion: '', fecha_entrega: '', puntaje_maximo: 10.0,
  };

  ngOnInit() { this.cargarGrupos(); }

  cargarGrupos() {
    const plantelId = this.ctx.plantel()?.id;
    const params = plantelId ? `?plantel_id=${plantelId}` : '';
    this.api.get(`/grupos${params}`).subscribe((r: any) => this.grupos.set(r.data ?? r));
  }

  onGrupoChange() {
    this.materiaSel = null;
    this.periodoSel = null;
    this.actividades.set([]);
    this.concentrado.set([]);
    if (!this.grupoSel) return;
    this.api.get(`/materias?grupo_id=${this.grupoSel}`).subscribe((r: any) =>
      this.materias.set(r.data ?? r));
    this.api.get(`/catalogs/periodos?grupo_id=${this.grupoSel}`).subscribe((r: any) =>
      this.periodos.set(r ?? []));
    this.cargarActividades();
    this.cargarCobertura();
  }

  cargarActividades() {
    if (!this.grupoSel) return;
    let url = `/actividades/grupo/${this.grupoSel}?`;
    if (this.materiaSel) url += `materia_id=${this.materiaSel}&`;
    if (this.periodoSel) url += `periodo_id=${this.periodoSel}&`;
    this.cargando.set(true);
    this.api.get(url).subscribe({ next: (r: any) => { this.actividades.set(r); this.cargando.set(false); },
      error: () => this.cargando.set(false) });
  }

  cargarConcentrado() {
    if (!this.grupoSel || !this.periodoSel) return;
    this.cargandoConc.set(true);
    const url = `/gradebook/grupo/${this.grupoSel}/concentrado?periodo_id=${this.periodoSel}`;
    this.api.get(url).subscribe({ next: (r: any) => {
      const rows = (r.detalle ?? []).map((row: any) => ({
        ...row,
        en_riesgo: row.calificacion_final !== null && row.calificacion_final < row.minimo_aprobatorio,
      }));
      this.concentrado.set(rows);
      this.cargandoConc.set(false);
    }, error: () => this.cargandoConc.set(false) });
  }

  cargarCobertura() {
    if (!this.grupoSel) return;
    let url = `/gradebook/grupo/${this.grupoSel}/cobertura-curricular`;
    if (this.materiaSel) url += `?materia_id=${this.materiaSel}`;
    this.api.get(url).subscribe((r: any) => this.cobertura.set(r));
  }

  abrirCalificacion(act: Actividad) {
    this.actividadSeleccionada.set(act);
    this.cargandoEntregas.set(true);
    this.drawerCalifVisible = true;
    this.api.get(`/actividades/${act.id}/entregas`).subscribe({ next: (r: any) => {
      const entregas = r.map((e: any) => ({ ...e, _cal: e.calificacion_obtenida ?? null }));
      this.entregasActiva.set(entregas);
      this.cargandoEntregas.set(false);
    }, error: () => this.cargandoEntregas.set(false) });
  }

  guardarCalificacionMasiva() {
    const act = this.actividadSeleccionada();
    if (!act) return;
    const items = this.entregasActiva()
      .filter(e => e._cal !== null && e._cal !== undefined)
      .map(e => ({ alumno_id: e.estudiante_id, calificacion: e._cal!, comentario: e.comentario_profesor }));
    if (!items.length) return;
    this.api.patch(`/actividades/${act.id}/calificar-masivo`, items).subscribe(() => {
      this.notify.success('Guardado', `${items.length} calificaciones guardadas`);
      this.drawerCalifVisible = false;
      this.cargarActividades();
      this.cargarConcentrado();
    });
  }

  abrirAjuste(row: CalPeriodo) {
    this.rowAjuste.set(row);
    this.ajusteValor = row.ajuste_manual ?? row.calificacion_calculada;
    this.ajusteJustificacion = '';
    this.dialogAjusteVisible = true;
  }

  aplicarAjuste() {
    const row = this.rowAjuste();
    if (!row || this.ajusteValor === null) return;
    if (this.ajusteJustificacion.trim().length < 20) {
      this.notify.warning('Justificación corta', 'Mínimo 20 caracteres');
      return;
    }
    this.api.post(`/gradebook/${row.cal_periodo_id}/ajuste-manual`, {
      ajuste_manual: this.ajusteValor,
      justificacion_ajuste: this.ajusteJustificacion,
    }).subscribe(() => {
      this.notify.success('Ajuste aplicado', 'Calificación actualizada');
      this.dialogAjusteVisible = false;
      this.cargarConcentrado();
    });
  }

  recalcularPeriodo() {
    if (!this.grupoSel || !this.periodoSel) return;
    this.api.post(`/gradebook/periodo/${this.periodoSel}/recalcular-todo`,
      { grupo_id: this.grupoSel }).subscribe((r: any) => {
      this.notify.info('Recalculado', `${r.recalculados} registros actualizados`);
      this.cargarConcentrado();
    });
  }

  crearActividad() {
    if (!this.grupoSel || !this.materiaSel) {
      this.notify.warning('Falta información', 'Selecciona grupo y materia');
      return;
    }
    this.api.post('/actividades', {
      ...this.nuevaAct,
      grupo_id: this.grupoSel,
      materia_id: this.materiaSel,
      periodo_evaluacion_id: this.periodoSel,
    }).subscribe((r: any) => {
      this.notify.success('Creada', `${r.slots_creados} slots generados`);
      this.mostrarNuevaActividad = false;
      this.nuevaAct = { titulo: '', descripcion: '', tipo_item: 'tarea', fecha_asignacion: '', fecha_entrega: '', puntaje_maximo: 10 };
      this.cargarActividades();
    });
  }

  exportarExcel() {
    const cols = [
      { field: 'alumno', header: 'Alumno' },
      { field: 'numero_matricula', header: 'Matrícula' },
      { field: 'nombre_materia', header: 'Materia' },
      { field: 'calificacion_calculada', header: 'Calculada' },
      { field: 'ajuste_manual', header: 'Ajuste' },
      { field: 'calificacion_final', header: 'Final' },
    ];
    this.exporter.toXLSX(this.concentrado(), cols, 'Calificaciones', 'gradebook');
  }

  pctEntregas(act: Actividad): number {
    return act.total_alumnos ? Math.round((act.entregadas / act.total_alumnos) * 100) : 0;
  }

  calClass(cal: number | null, escala: number): string {
    if (cal === null) return '';
    const ratio = escala >= 100 ? cal / 10 : cal;
    if (ratio >= 9) return 'cal-excelente';
    if (ratio >= 7.5) return 'cal-bien';
    if (ratio >= 6) return 'cal-regular';
    return 'cal-reprobado';
  }

  tipoSeverity(tipo: string): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
    const map: Record<string, any> = {
      examen: 'danger', tarea: 'info', proyecto: 'success',
      laboratorio: 'warn', participacion: 'secondary', otro: 'secondary',
    };
    return map[tipo] ?? 'secondary';
  }

  estatusSeverity(s: string): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
    const map: Record<string, any> = {
      CALIFICADA: 'success', ENTREGADA: 'info',
      PENDIENTE: 'warn', SIN_ENTREGA: 'danger', EXCUSA: 'secondary',
    };
    return map[s] ?? 'secondary';
  }
}
