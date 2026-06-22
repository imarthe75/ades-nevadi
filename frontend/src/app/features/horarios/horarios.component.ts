/**
 * HorariosComponent — Vista semanal + CRUD de horarios escolares.
 *
 * Vista tipo grid 5×N (Lun-Vie × franjas horarias).
 * Permite ver el horario de un grupo O de un docente, y crear/editar/eliminar entradas.
 * Exporta el XML para aSc TimeTables.
 */
import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TooltipModule } from 'primeng/tooltip';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import type { Grupo, Profesor } from '../../core/models';
import { grupoLabel } from '../../core/models';
import { ApexNotificationService } from 'apex-component-library';

type GrupoConLabel = Grupo & { _label: string };

interface HorarioEntry {
  id: string;
  dia_semana: number;
  hora_inicio: string;
  hora_fin: string;
  nombre_materia: string | null;
  materia_id: string | null;
  nombre_grupo: string | null;
  grupo_id: string | null;
  nombre_profesor: string | null;
  profesor_id: string | null;
  nombre_aula: string | null;
  aula_id: string | null;
}

interface MateriaOpt { id: string; nombre_materia: string; }
interface AulaOpt    { id: string; nombre: string; codigo: string | null; }

const DIAS = ['', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes'];
const HORAS_INICIO = [
  '07:00','07:30','08:00','08:30','09:00','09:30','10:00','10:30',
  '11:00','11:30','12:00','12:30','13:00','13:30','14:00','14:30',
  '15:00','15:30','16:00','16:30','17:00','17:30','18:00','18:30',
];
const DURACIONES = [
  { label: '30 min', value: 30 }, { label: '45 min', value: 45 },
  { label: '50 min', value: 50 }, { label: '1 hora', value: 60 },
  { label: '1.5 horas', value: 90 }, { label: '2 horas', value: 120 },
];
const COLORS: Record<string, string> = {
  'Matemáticas': '#FEE2E2', 'Español': '#E0F2FE', 'Ciencias': '#D1FAE5',
  'Historia': '#FEF3C7', 'Inglés': '#EDE9FE', 'Física': '#FCE7F3',
  'Química': '#ECFDF5', 'default': '#F1F5F9',
};

@Component({
  selector: 'app-horarios',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    ButtonModule, SelectModule, TagModule, DialogModule,
    InputTextModule, SelectButtonModule, TooltipModule,
  ],
  template: `
    <!-- ── Diálogo crear/editar entrada ── -->
    <p-dialog
      [visible]="showDialog()"
      (visibleChange)="showDialog.set($event)"
      [header]="editEntry ? 'Editar entrada de horario' : 'Nueva entrada de horario'"
      [modal]="true" [style]="{width:'480px'}" [draggable]="false">

      <div class="dlg-grid">
        <div class="dlg-row">
          <div>
            <label class="dlg-lbl">Día *</label>
            <p-select [options]="diasOpts" [(ngModel)]="form.dia_semana"
              optionLabel="label" optionValue="value" style="width:100%" placeholder="Día…" />
          </div>
          <div>
            <label class="dlg-lbl">Hora inicio *</label>
            <p-select [options]="horasOpts" [(ngModel)]="form.hora_inicio"
              style="width:100%" placeholder="HH:MM" />
          </div>
        </div>
        <div class="dlg-row">
          <div>
            <label class="dlg-lbl">Duración *</label>
            <p-select [options]="duracionOpts" [(ngModel)]="form.duracion"
              optionLabel="label" optionValue="value" style="width:100%" placeholder="Duración…" />
          </div>
          <div>
            <label class="dlg-lbl">Materia *</label>
            <p-select [options]="materias()" [(ngModel)]="form.materia_id"
              optionLabel="nombre_materia" optionValue="id"
              style="width:100%" [filter]="true" filterPlaceholder="Buscar…" placeholder="Materia…" />
          </div>
        </div>
        <div class="dlg-row">
          @if (modo === 'grupo') {
            <div>
              <label class="dlg-lbl">Docente</label>
              <p-select [options]="profesores()" [(ngModel)]="form.profesor_id"
                optionLabel="_label" optionValue="id"
                style="width:100%" [filter]="true" filterPlaceholder="Buscar…" placeholder="Docente…" [showClear]="true" />
            </div>
          } @else {
            <div>
              <label class="dlg-lbl">Grupo</label>
              <p-select [options]="grupos()" [(ngModel)]="form.grupo_id"
                optionLabel="_label" optionValue="id"
                style="width:100%" [filter]="true" filterPlaceholder="Buscar…" placeholder="Grupo…" [showClear]="true" />
            </div>
          }
          <div>
            <label class="dlg-lbl">Aula</label>
            <p-select [options]="aulas()" [(ngModel)]="form.aula_id"
              optionLabel="_label" optionValue="id"
              style="width:100%" [filter]="true" filterPlaceholder="Buscar…" placeholder="Aula…" [showClear]="true" />
          </div>
        </div>
      </div>

      <ng-template pTemplate="footer">
        @if (editEntry) {
          <p-button label="Eliminar" icon="pi pi-trash" severity="danger" [text]="true"
            (onClick)="eliminar()" [loading]="guardando()" />
        }
        <p-button label="Cancelar" severity="secondary" [text]="true"
          (onClick)="showDialog.set(false)" />
        <p-button [label]="editEntry ? 'Guardar cambios' : 'Agregar al horario'"
          icon="pi pi-check" (onClick)="guardar()" [loading]="guardando()" />
      </ng-template>
    </p-dialog>

    <div class="page-header">
      <div>
        <h2>Horarios</h2>
        <p class="subtitle">Vista semanal — {{ modoLabel() }}</p>
      </div>
      <div style="display:flex;gap:.5rem;align-items:center">
        <p-button
          label="Exportar XML aSc"
          icon="pi pi-download"
          severity="secondary"
          [text]="true"
          (onClick)="exportarASC()"
          [loading]="exportandoAsc()"
          [disabled]="!ctx.ciclo()"
          pTooltip="Exportar horarios al formato XML de aSc TimeTables"
        />
        @if (ctx.nivelAcceso() <= 3) {
          <label class="asc-reemplazar" pTooltip="Si está activo, reemplaza los horarios vigentes del ciclo antes de importar">
            <input type="checkbox" [(ngModel)]="reemplazarAsc" />
            <span>Reemplazar al importar</span>
          </label>
          <p-button
            label="Importar XML aSc"
            icon="pi pi-upload"
            severity="secondary"
            [text]="true"
            (onClick)="ascInput.click()"
            [loading]="importandoAsc()"
            [disabled]="!ctx.ciclo()"
            pTooltip="Importar la solución resuelta desde aSc TimeTables"
          />
          <input #ascInput type="file" accept=".xml" style="display:none"
                 (change)="onArchivoAscSeleccionado($event)" />
        }
        @if (selectedGrupo || selectedProfesor) {
          <p-button
            label="Nueva entrada"
            icon="pi pi-plus"
            (onClick)="abrirNuevo()"
          />
        }
      </div>
    </div>

    <!-- ── Controles ── -->
    <div class="filter-bar">
      <p-selectButton
        [options]="modoOpts"
        [(ngModel)]="modo"
        optionLabel="label"
        optionValue="value"
        (onChange)="onModoChange()"
      />

      @if (modo === 'grupo') {
        <p-select
          [options]="grupos()"
          [(ngModel)]="selectedGrupo"
          optionLabel="_label"
          placeholder="Seleccionar grupo..."
          (onChange)="onGrupoChange()"
          [showClear]="true"
          [filter]="true" filterPlaceholder="Buscar..."/>
      } @else {
        <p-select
          [options]="profesores()"
          [(ngModel)]="selectedProfesor"
          optionLabel="_label"
          placeholder="Seleccionar docente..."
          (onChange)="cargar()"
          [showClear]="true"
          [filter]="true" filterPlaceholder="Buscar..."/>
      }
    </div>

    <!-- ── Grid semanal ── -->
    @if (!selectedGrupo && !selectedProfesor) {
      <div class="empty-state">
        <i class="pi pi-calendar" style="font-size:2.5rem;color:#CBD5E1"></i>
        <p>Selecciona un {{ modo === 'grupo' ? 'grupo' : 'docente' }} para ver el horario.</p>
      </div>
    } @else if (entradas().length === 0) {
      <div class="empty-state">
        <i class="pi pi-calendar-plus" style="font-size:2.5rem;color:#CBD5E1"></i>
        <p>No hay entradas de horario. Usa "Nueva entrada" para comenzar.</p>
      </div>
    } @else {
      <div class="horario-grid">
        <!-- Cabecera días -->
        <div class="grid-header">
          <div class="hora-col"></div>
          @for (dia of dias; track dia.num) {
            <div class="dia-header">{{ dia.label }}</div>
          }
        </div>

        <!-- Franjas horarias -->
        @for (franja of franjas(); track franja) {
          <div class="grid-row">
            <div class="hora-col">{{ franja }}</div>
            @for (dia of dias; track dia.num) {
              <div class="dia-cell">
                @for (e of entradasPor(dia.num, franja); track e.id) {
                  <div class="clase-chip" [style.background]="colorMateria(e.nombre_materia)"
                       (click)="abrirEditar(e)" pTooltip="Click para editar" style="cursor:pointer">
                    <strong>{{ e.nombre_materia }}</strong>
                    @if (modo === 'grupo') {
                      <span>{{ e.nombre_profesor }}</span>
                    } @else {
                      <span>{{ e.nombre_grupo }}</span>
                    }
                    @if (e.nombre_aula) { <span class="aula-tag">{{ e.nombre_aula }}</span> }
                    <span class="hora-tag">{{ e.hora_inicio | slice:0:5 }}–{{ e.hora_fin | slice:0:5 }}</span>
                  </div>
                }
              </div>
            }
          </div>
        }
      </div>
    }
  `,
  styles: [`
    .filter-bar { display: flex; gap: 0.75rem; align-items: center; margin-bottom: 1.25rem; flex-wrap: wrap; }
    .page-header { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:1.25rem; }
    .page-header h2 { margin:0; }
    .subtitle { margin:0; font-size:.82rem; color:var(--p-text-color-secondary); }
    .empty-state { display: flex; flex-direction: column; align-items: center; gap: 0.75rem; padding: 4rem; color: #94A3B8; }

    /* Grid */
    .horario-grid { overflow-x: auto; }
    .grid-header, .grid-row {
      display: grid;
      grid-template-columns: 70px repeat(5, 1fr);
      gap: 2px;
    }
    .grid-header { margin-bottom: 2px; }
    .dia-header {
      background: var(--nevadi-red);
      color: #fff;
      text-align: center;
      padding: 0.4rem;
      font-family: var(--font-display);
      font-weight: 700;
      font-size: 0.82rem;
      border-radius: 4px;
    }
    .hora-col {
      font-size: 0.75rem;
      color: var(--text-secondary);
      padding: 0.3rem 0.4rem;
      display: flex; align-items: flex-start;
      font-family: var(--font-body);
    }
    .dia-cell {
      min-height: 52px;
      background: var(--surface-hover);
      border-radius: 4px;
      padding: 2px;
      display: flex; flex-direction: column; gap: 2px;
    }
    .clase-chip {
      border-radius: 4px;
      padding: 0.3rem 0.5rem;
      font-size: 0.78rem;
      display: flex; flex-direction: column;
      border-left: 3px solid var(--nevadi-red);
    }
    .clase-chip:hover { filter: brightness(.95); }
    .clase-chip strong { font-family: var(--font-display); font-size: 0.8rem; }
    .clase-chip span { color: var(--text-secondary); font-size: 0.72rem; }
    .aula-tag { font-weight: 600; color: var(--nevadi-red-dark) !important; }
    .hora-tag { font-size: 0.68rem !important; color: var(--text-muted) !important; }
    .grid-row { margin-bottom: 2px; }

    /* Diálogo */
    .dlg-grid { display:flex; flex-direction:column; gap:.75rem; padding:.5rem 0; }
    .dlg-row  { display:grid; grid-template-columns:1fr 1fr; gap:.75rem; }
    .dlg-lbl  { display:block; font-size:.8rem; font-weight:600; margin-bottom:.3rem; color:var(--p-text-color); }
    .asc-reemplazar { display:flex; align-items:center; gap:.35rem; font-size:.8rem; color:var(--p-text-muted-color); cursor:pointer; user-select:none; }
    .asc-reemplazar input { cursor:pointer; }
  `],
})
export class HorariosComponent implements OnInit {
  private readonly api    = inject(ApiService);
  readonly ctx            = inject(ContextService);
  private readonly notify = inject(ApexNotificationService);

  readonly dias    = DIAS.slice(1).map((label, i) => ({ num: i + 1, label }));
  readonly diasOpts   = DIAS.slice(1).map((label, i) => ({ label, value: i + 1 }));
  readonly horasOpts  = HORAS_INICIO;
  readonly duracionOpts = DURACIONES;

  readonly modoOpts = [
    { label: 'Por grupo', value: 'grupo' },
    { label: 'Por docente', value: 'profesor' },
  ];

  modo: 'grupo' | 'profesor' = 'grupo';
  selectedGrupo:   GrupoConLabel | null   = null;
  selectedProfesor: any  | null   = null;

  grupos    = signal<GrupoConLabel[]>([]);
  profesores = signal<any[]>([]);
  materias  = signal<MateriaOpt[]>([]);
  aulas     = signal<AulaOpt[]>([]);
  entradas  = signal<HorarioEntry[]>([]);

  showDialog  = signal(false);
  guardando   = signal(false);
  editEntry: HorarioEntry | null = null;

  // aSc TimeTables
  exportandoAsc = signal(false);
  importandoAsc = signal(false);
  reemplazarAsc = false;

  form: {
    dia_semana: number | null;
    hora_inicio: string;
    duracion: number;
    materia_id: string | null;
    profesor_id: string | null;
    grupo_id: string | null;
    aula_id: string | null;
  } = this.resetForm();

  modoLabel = computed(() => this.modo === 'grupo' ? 'Grupo' : 'Docente');

  readonly franjas = computed(() => {
    const horas = [...new Set(this.entradas().map(e => e.hora_inicio))];
    return horas.sort();
  });

  readonly profeLabel = (p: any) => `${p.nombre ?? ''} ${p.apellido_paterno ?? ''}`.trim() || p.numero_empleado;
  readonly aulaLabel  = (a: AulaOpt) => a.codigo ? `${a.codigo} — ${a.nombre}` : a.nombre;

  ngOnInit(): void {
    this.cargarCatalogos();
  }

  cargarCatalogos(): void {
    const plantelId = this.ctx.plantel()?.id;
    if (plantelId) {
      this.api.get<Grupo[]>('/grupos', { plantel_id: plantelId, solo_activos: true, ciclo_vigente: true })
        .subscribe(g => this.grupos.set(g.map(x => ({ ...x, _label: grupoLabel(x) }))));
      this.api.get<any[]>('/profesores', { plantel_id: plantelId })
        .subscribe(p => this.profesores.set(
          p.map(x => ({ ...x, _label: `${x.nombre ?? ''} ${x.apellido_paterno ?? ''}`.trim() || x.numero_empleado }))
        ));
      this.api.get<AulaOpt[]>('/aulas', { plantel_id: plantelId })
        .subscribe(a => this.aulas.set(
          a.map(x => ({ ...x, _label: x.codigo ? `${x.codigo} — ${x.nombre}` : x.nombre }))
        ));
    }
  }

  onModoChange(): void {
    this.selectedGrupo = null;
    this.selectedProfesor = null;
    this.entradas.set([]);
  }

  onGrupoChange(): void {
    if (this.selectedGrupo) {
      this.api.get<MateriaOpt[]>('/materias', { grupo_id: (this.selectedGrupo as any).id })
        .subscribe(m => this.materias.set(m));
    }
    this.cargar();
  }

  cargar(): void {
    if (this.modo === 'grupo' && this.selectedGrupo) {
      this.api.get<any>(`/horarios/grupo/${(this.selectedGrupo as any).id}`)
        .subscribe(r => this.entradas.set(r.entradas || r || []));
    } else if (this.modo === 'profesor' && this.selectedProfesor) {
      this.api.get<any>(`/horarios/profesor/${this.selectedProfesor.id}`)
        .subscribe(r => this.entradas.set(r.entradas || r || []));
    }
  }

  entradasPor(dia: number, hora: string): HorarioEntry[] {
    return this.entradas().filter(e => e.dia_semana === dia && e.hora_inicio === hora);
  }

  colorMateria(nombre: string | null): string {
    if (!nombre) return COLORS['default'];
    const key = Object.keys(COLORS).find(k => nombre.includes(k));
    return key ? COLORS[key] : COLORS['default'];
  }

  abrirNuevo(): void {
    this.editEntry = null;
    this.form = {
      ...this.resetForm(),
      grupo_id:   this.modo === 'grupo'    ? (this.selectedGrupo as any)?.id ?? null : null,
      profesor_id: this.modo === 'profesor' ? this.selectedProfesor?.id ?? null : null,
    };
    this.showDialog.set(true);
  }

  abrirEditar(e: HorarioEntry): void {
    this.editEntry = e;
    const [h, m] = e.hora_inicio.split(':').map(Number);
    const [hFin, mFin] = e.hora_fin.split(':').map(Number);
    this.form = {
      dia_semana:  e.dia_semana,
      hora_inicio: `${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}`,
      duracion:    (hFin * 60 + mFin) - (h * 60 + m),
      materia_id:  e.materia_id,
      profesor_id: e.profesor_id,
      grupo_id:    e.grupo_id,
      aula_id:     e.aula_id,
    };
    this.showDialog.set(true);
  }

  guardar(): void {
    if (!this.form.dia_semana || !this.form.hora_inicio || !this.form.materia_id) {
      this.notify.warning('Validación', 'Completa los campos obligatorios: Día, Hora inicio y Materia.');
      return;
    }
    this.guardando.set(true);

    const horaFin = this.calcHoraFin(this.form.hora_inicio, this.form.duracion);
    const cicloId = this.ctx.ciclo()?.id;

    const body: Record<string, any> = {
      dia_semana:      this.form.dia_semana,
      hora_inicio:     this.form.hora_inicio,
      hora_fin:        horaFin,
      materia_id:      this.form.materia_id,
      profesor_id:     this.form.profesor_id || null,
      aula_id:         this.form.aula_id || null,
      ciclo_escolar_id: cicloId || null,
      origen:          'MANUAL',
    };

    if (this.modo === 'grupo') {
      body['grupo_id'] = (this.selectedGrupo as any)?.id;
    } else {
      body['grupo_id'] = this.form.grupo_id || null;
    }

    const req = this.editEntry
      ? this.api.patch(`/horarios/${this.editEntry.id}`, body)
      : this.api.post('/horarios', body);

    req.subscribe({
      next: () => {
        this.notify.success(this.editEntry ? 'Entrada actualizada.' : 'Entrada agregada al horario.');
        this.guardando.set(false);
        this.showDialog.set(false);
        this.cargar();
      },
      error: (err: any) => {
        this.notify.error(err?.error?.detail ?? 'Error al guardar la entrada.');
        this.guardando.set(false);
      },
    });
  }

  eliminar(): void {
    if (!this.editEntry) return;
    if (!confirm('¿Eliminar esta entrada del horario?')) return;
    this.guardando.set(true);
    this.api.delete(`/horarios/${this.editEntry.id}`).subscribe({
      next: () => {
        this.notify.success('Entrada eliminada.');
        this.guardando.set(false);
        this.showDialog.set(false);
        this.cargar();
      },
      error: () => {
        this.notify.error('Error al eliminar.');
        this.guardando.set(false);
      },
    });
  }

  exportarASC(): void {
    const cicloId   = this.ctx.ciclo()?.id;
    const plantelId = this.ctx.plantel()?.id;
    if (!cicloId) return;
    const params: Record<string, string> = {};
    if (plantelId) params['plantel_id'] = plantelId;
    this.exportandoAsc.set(true);
    this.api.getBlob(`/horarios/exportar-asc/${cicloId}`, params).subscribe({
      next: blob => {
        this.exportandoAsc.set(false);
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'horarios_asc.xml';
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.exportandoAsc.set(false);
        this.notify.error('Error', 'No se pudo exportar el XML aSc');
      },
    });
  }

  onArchivoAscSeleccionado(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    input.value = '';   // permite re-subir el mismo archivo
    const cicloId = this.ctx.ciclo()?.id;
    if (!cicloId) {
      this.notify.warning('Ciclo requerido', 'Selecciona un ciclo escolar en el contexto antes de importar');
      return;
    }
    const plantelId = this.ctx.plantel()?.id;
    const form = new FormData();
    form.append('file', file);
    if (plantelId) form.append('plantel_id', plantelId);
    form.append('reemplazar', String(this.reemplazarAsc));

    this.importandoAsc.set(true);
    this.api.upload<any>(`/horarios/importar-asc/${cicloId}`, form).subscribe({
      next: r => {
        this.importandoAsc.set(false);
        const detalle = r.errores > 0 ? ` (${r.errores} con error)` : '';
        const reemplazo = r.eliminados > 0 ? `, ${r.eliminados} reemplazadas` : '';
        this.notify.success('Importación aSc',
          `${r.exitosos} de ${r.total} clases importadas${reemplazo}${detalle}`);
        this.cargar();
      },
      error: e => {
        this.importandoAsc.set(false);
        this.notify.error('Error', e.error?.detail ?? e.error?.message ?? 'No se pudo importar el XML aSc');
      },
    });
  }

  private resetForm() {
    return {
      dia_semana:  null as number | null,
      hora_inicio: '08:00',
      duracion:    50,
      materia_id:  null as string | null,
      profesor_id: null as string | null,
      grupo_id:    null as string | null,
      aula_id:     null as string | null,
    };
  }

  private calcHoraFin(inicio: string, duracionMin: number): string {
    const [h, m] = inicio.split(':').map(Number);
    const total = h * 60 + m + duracionMin;
    return `${String(Math.floor(total / 60)).padStart(2,'0')}:${String(total % 60).padStart(2,'0')}`;
  }
}
