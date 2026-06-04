import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { ProgressBarModule } from 'primeng/progressbar';
import { TooltipModule } from 'primeng/tooltip';
import { DialogModule } from 'primeng/dialog';
import { TabsModule } from 'primeng/tabs';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';

interface MateriaSummary {
  materia_id: string;
  nombre_materia: string;
  calificacion_final: number | null;
  calificacion_calculada: number | null;
  escala_maxima: number;
  minimo_aprobatorio: number;
  score_por_item: Record<string, number>;
}

interface EntregaPendiente {
  id: string;
  titulo: string;
  tipo_item: string;
  fecha_limite: string;
  nombre_materia: string;
  vencida: boolean;
  estatus_entrega: string;
}

interface EntregaHistorial extends EntregaPendiente {
  calificacion_obtenida: number | null;
  comentario_profesor: string | null;
  fecha_entrega: string;
}

@Component({
  selector: 'app-mi-progreso',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TableModule, ButtonModule, SelectModule,
    CardModule, TagModule, ProgressBarModule, TooltipModule,
    DialogModule, TabsModule, ToastModule,
  ],
  providers: [MessageService],
  template: `
<p-toast />
<div class="page-header">
  <div class="page-title"><i class="pi pi-chart-line"></i> Mi Progreso</div>
</div>

<p-tabs value="0">
  <!-- ── TAB 1: Resumen por materia ── -->
  <p-tabpanel header="Calificaciones" value="0">
    <div class="materias-grid">
      <div *ngFor="let m of materias()" class="materia-card"
           [class.reprobada]="m.calificacion_final !== null && m.calificacion_final < m.minimo_aprobatorio">
        <div class="materia-nombre">{{ m.nombre_materia }}</div>
        <div [class]="calClass(m.calificacion_final, m.escala_maxima)" class="materia-cal">
          {{ m.calificacion_final ?? '—' }}
        </div>
        <p-progressBar [value]="pctCal(m)" [style]="{'height':'6px','margin':'8px 0'}"
                       [color]="progressColor(m)" [showValue]="false" />
        <div class="score-desglose" *ngIf="m.score_por_item">
          <span *ngFor="let item of itemsScore(m)" class="score-item">
            {{ item.label }}: <strong>{{ item.val }}</strong>
          </span>
        </div>
        <small *ngIf="m.calificacion_final !== null && m.calificacion_final < m.minimo_aprobatorio"
               class="text-danger">⚠ En riesgo</small>
      </div>
    </div>
    <p *ngIf="!materias().length && !cargando()" class="text-center text-muted p-4">
      No hay calificaciones para el período seleccionado.
    </p>
  </p-tabpanel>

  <!-- ── TAB 2: Tareas pendientes ── -->
  <p-tabpanel header="Tareas pendientes" value="1">
    <p-table [value]="pendientes()" [loading]="cargando()" styleClass="p-datatable-sm"
             [rows]="50" [paginator]="pendientes().length > 50">
      <ng-template pTemplate="header">
        <tr>
          <th>Tarea</th>
          <th>Materia</th>
          <th>Tipo</th>
          <th>Fecha límite</th>
          <th>Estado</th>
          <th></th>
        </tr>
      </ng-template>
      <ng-template pTemplate="body" let-t>
        <tr [class.row-vencida]="t.vencida">
          <td>{{ t.titulo }}</td>
          <td>{{ t.nombre_materia }}</td>
          <td><p-tag [value]="t.tipo_item" severity="info" /></td>
          <td>
            <span [class]="t.vencida ? 'text-danger' : ''">
              {{ t.fecha_limite | date:'dd/MM/yyyy' }}
            </span>
          </td>
          <td><p-tag [value]="t.estatus_entrega" [severity]="estatusSev(t.estatus_entrega)" /></td>
          <td>
            <button pButton icon="pi pi-upload" text size="small"
                    pTooltip="Subir entrega" (click)="abrirSubirEntrega(t)"
                    [disabled]="t.estatus_entrega === 'CALIFICADA'"></button>
          </td>
        </tr>
      </ng-template>
    </p-table>
  </p-tabpanel>

  <!-- ── TAB 3: Historial de entregas ── -->
  <p-tabpanel header="Historial" value="2">
    <p-table [value]="historial()" styleClass="p-datatable-sm" [rows]="100">
      <ng-template pTemplate="header">
        <tr>
          <th>Actividad</th>
          <th>Materia</th>
          <th>Entregada</th>
          <th style="width:90px">Calificación</th>
          <th>Feedback</th>
        </tr>
      </ng-template>
      <ng-template pTemplate="body" let-e>
        <tr>
          <td>{{ e.titulo }}</td>
          <td>{{ e.nombre_materia }}</td>
          <td>{{ e.fecha_entrega | date:'dd/MM/yyyy' }}</td>
          <td class="text-center">
            <strong [class]="calClass(e.calificacion_obtenida, 10)">
              {{ e.calificacion_obtenida ?? '—' }}
            </strong>
          </td>
          <td><small class="text-muted">{{ e.comentario_profesor ?? '' }}</small></td>
        </tr>
      </ng-template>
    </p-table>
  </p-tabpanel>
</p-tabs>

<!-- ── Dialog: subir entrega ── -->
<p-dialog [(visible)]="dialogSubirVisible" header="Subir entrega"
          [modal]="true" [style]="{width:'420px'}">
  <div *ngIf="entregaActiva">
    <p><strong>{{ entregaActiva.titulo }}</strong></p>
    <p><small>{{ entregaActiva.nombre_materia }}</small></p>
    <div class="field mt-3">
      <label>Archivo</label>
      <input type="file" (change)="onFileChange($event)"
             class="w-full" style="width:100%;padding:4px;border:1px solid #ccc;border-radius:4px" />
    </div>
    <div class="field mt-2">
      <label>Comentario (opcional)</label>
      <textarea [(ngModel)]="comentarioEntrega" rows="3"
                class="w-full" style="width:100%;padding:6px;border:1px solid #ccc;border-radius:4px"></textarea>
    </div>
  </div>
  <ng-template pTemplate="footer">
    <button pButton label="Cancelar" severity="secondary" (click)="dialogSubirVisible = false"></button>
    <button pButton label="Subir" icon="pi pi-upload" (click)="subirEntrega()" [loading]="subiendoArchivo()"></button>
  </ng-template>
</p-dialog>
  `,
  styles: [`
    .materias-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(220px,1fr)); gap:16px; padding:8px 0; }
    .materia-card { background:var(--p-surface-0); border:1px solid var(--p-surface-border);
                    border-radius:8px; padding:16px; }
    .materia-card.reprobada { border-left:4px solid #dc2626; }
    .materia-nombre { font-weight:600; margin-bottom:4px; font-size:0.9rem; }
    .materia-cal { font-size:2.4rem; font-weight:700; font-family:'Jost',sans-serif; }
    .score-desglose { display:flex; flex-wrap:wrap; gap:4px; margin-top:8px; }
    .score-item { font-size:0.75rem; background:#f3f4f6; border-radius:4px; padding:2px 6px; }
    .row-vencida td { background:#fff5f5 !important; }
    .cal-excelente { color:#15803d; } .cal-bien { color:#0369a1; }
    .cal-regular   { color:#b45309; } .cal-reprobado { color:#dc2626; }
  `],
})
export class MiProgresoComponent implements OnInit {
  private api = inject(ApiService);
  private ctx = inject(ContextService);
  private msg = inject(MessageService);

  materias = signal<MateriaSummary[]>([]);
  pendientes = signal<EntregaPendiente[]>([]);
  historial = signal<EntregaHistorial[]>([]);
  cargando = signal(false);
  subiendoArchivo = signal(false);

  dialogSubirVisible = false;
  entregaActiva: EntregaPendiente | null = null;
  archivoSeleccionado: File | null = null;
  comentarioEntrega = '';

  // En producción, el alumno_id viene del token JWT
  alumnoId: string | null = null;

  ngOnInit() {
    // Para demo, se obtiene de ContextService o del perfil del usuario
    this.api.get('/usuarios/mi-perfil').subscribe((u: any) => {
      if (u.estudiante_id) {
        this.alumnoId = u.estudiante_id;
        this.cargarDatos();
      }
    });
  }

  cargarDatos() {
    if (!this.alumnoId) return;
    this.cargando.set(true);

    this.api.get(`/gradebook/alumno/${this.alumnoId}/boleta`).subscribe({
      next: (r: any) => { this.materias.set(r); this.cargando.set(false); },
      error: () => this.cargando.set(false),
    });

    this.api.get(`/entregas/alumno/${this.alumnoId}?solo_pendientes=true`).subscribe(
      (r: any) => this.pendientes.set(r));

    this.api.get(`/entregas/alumno/${this.alumnoId}`).subscribe(
      (r: any) => this.historial.set(r.filter((e: any) => e.estatus_entrega === 'CALIFICADA')));
  }

  abrirSubirEntrega(t: EntregaPendiente) {
    this.entregaActiva = t;
    this.comentarioEntrega = '';
    this.archivoSeleccionado = null;
    this.dialogSubirVisible = true;
  }

  onFileChange(evt: Event) {
    const input = evt.target as HTMLInputElement;
    this.archivoSeleccionado = input.files?.[0] ?? null;
  }

  subirEntrega() {
    if (!this.entregaActiva || !this.alumnoId) return;
    const fd = new FormData();
    fd.append('tarea_id', this.entregaActiva.id);
    fd.append('alumno_id', this.alumnoId);
    if (this.comentarioEntrega) fd.append('comentario', this.comentarioEntrega);
    if (this.archivoSeleccionado) fd.append('archivo', this.archivoSeleccionado);

    this.subiendoArchivo.set(true);
    this.api.post('/entregas', fd).subscribe({
      next: () => {
        this.msg.add({ severity: 'success', summary: 'Entrega registrada' });
        this.dialogSubirVisible = false;
        this.subiendoArchivo.set(false);
        this.cargarDatos();
      },
      error: () => this.subiendoArchivo.set(false),
    });
  }

  itemsScore(m: MateriaSummary) {
    return Object.entries(m.score_por_item ?? {}).map(([k, v]) => ({ label: k, val: v }));
  }

  pctCal(m: MateriaSummary): number {
    const cal = m.calificacion_final ?? 0;
    return Math.round((cal / m.escala_maxima) * 100);
  }

  progressColor(m: MateriaSummary): string {
    const pct = this.pctCal(m);
    if (pct >= 80) return '#15803d';
    if (pct >= 60) return '#0369a1';
    return '#dc2626';
  }

  calClass(cal: number | null, escala: number): string {
    if (cal === null) return '';
    const ratio = escala >= 100 ? cal / 10 : cal;
    if (ratio >= 9) return 'cal-excelente';
    if (ratio >= 7.5) return 'cal-bien';
    if (ratio >= 6) return 'cal-regular';
    return 'cal-reprobado';
  }

  estatusSev(s: string): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
    const m: Record<string, any> = { CALIFICADA: 'success', ENTREGADA: 'info', PENDIENTE: 'warn', SIN_ENTREGA: 'danger' };
    return m[s] ?? 'secondary';
  }
}
