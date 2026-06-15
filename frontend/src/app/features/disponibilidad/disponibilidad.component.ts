import { Component, OnInit, signal, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { ToastModule } from 'primeng/toast';
import { CheckboxModule } from 'primeng/checkbox';
import { InputNumberModule } from 'primeng/inputnumber';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { MessageService } from 'primeng/api';
import { ApexNotificationService } from 'apex-component-library';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';

interface SlotDisponibilidad {
  id: string;
  profesor_id: string;
  dia_semana: number;
  dia_nombre: string;
  hora_inicio: string;
  hora_fin: string;
  disponible: boolean;
  motivo_no_disponible: string | null;
  ciclo_escolar_id: string | null;
}

interface ResumenDisponibilidad {
  profesor_id: string;
  dias_disponibles: string[];
  total_slots: number;
  slots_disponibles: number;
  horas_semana: number;
  horas_semana_max: number;
  horas_frente_grupo: number;
}

@Component({
  selector: 'app-disponibilidad',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ButtonModule,
    DialogModule, InputTextModule, SelectModule,
    ToastModule, CheckboxModule, InputNumberModule,
    AutoCompleteModule, InteractiveGridComponent,
  ],
  providers: [MessageService],
  template: `
    <p-toast />
    <div class="apex-page">
      <div class="apex-toolbar">
        <h2 class="apex-title">Disponibilidad Docente</h2>
        <div class="apex-toolbar-actions">
          <p-autocomplete
            [(ngModel)]="docenteSeleccionado"
            [suggestions]="docenteSugerencias()"
            (completeMethod)="buscarDocente($event)"
            (onSelect)="onDocenteSeleccionado($event)"
            (onClear)="onDocenteLimpiado()"
            optionLabel="label"
            [forceSelection]="true"
            [delay]="300"
            placeholder="Buscar docente por nombre…"
            style="width:280px"
          />
          <p-button label="Configurar disponibilidad" icon="pi pi-calendar-plus" size="small"
            (onClick)="abrirConfigurar()" [disabled]="!profesorId" />
        </div>
      </div>

      <!-- Resumen -->
      @if (resumen()) {
        <div class="resumen-bar">
          <div class="res-item">
            <span class="res-num">{{ resumen()!.horas_semana }}</span>
            <span class="res-lbl">Hrs disponibles/sem</span>
          </div>
          <div class="res-item">
            <span class="res-num text-primary-600">{{ resumen()!.horas_semana_max }}</span>
            <span class="res-lbl">Hrs máx/sem</span>
          </div>
          <div class="res-item">
            <span class="res-num text-green-600">{{ resumen()!.horas_frente_grupo }}</span>
            <span class="res-lbl">Hrs frente grupo</span>
          </div>
          <div class="res-item">
            <span class="res-num">{{ resumen()!.slots_disponibles }} / {{ resumen()!.total_slots }}</span>
            <span class="res-lbl">Slots disponibles</span>
          </div>
          <div class="res-dias">
            @for (dia of resumen()!.dias_disponibles; track dia) {
              <span class="dia-chip">{{ dia }}</span>
            }
          </div>
        </div>
      }

      <!-- Grid semanal visual -->
      <div class="semana-grid">
        @for (dia of dias; track dia.num) {
          <div class="dia-col">
            <div class="dia-header">{{ dia.nombre }}</div>
            @for (slot of slotsForDia(dia.num); track slot.id) {
              <div class="slot-card" [class.slot-no-disp]="!slot.disponible">
                <span class="slot-hora">{{ slot.hora_inicio | slice:0:5 }} – {{ slot.hora_fin | slice:0:5 }}</span>
                @if (!slot.disponible && slot.motivo_no_disponible) {
                  <span class="slot-motivo">{{ slot.motivo_no_disponible }}</span>
                }
              </div>
            }
            @if (slotsForDia(dia.num).length === 0) {
              <div class="slot-empty">Sin slots</div>
            }
          </div>
        }
      </div>

      <app-interactive-grid
        [data]="slotsFlat()"
        [columns]="slotsColumns"
        [loading]="cargando()"
        [showDelete]="false"
      />
    </div>

    <!-- Dialog: Configurar disponibilidad -->
    <p-dialog header="Configurar Disponibilidad Semanal" [(visible)]="dialogConfig"
      [modal]="true" [style]="{width:'720px'}" [draggable]="false">
      <div class="p-3">
        <div class="grid grid-cols-2 gap-3 mb-4">
          <div class="flex flex-col gap-1">
            <label class="text-sm font-medium">Horas máx/semana</label>
            <p-inputNumber [(ngModel)]="configHrsMax" [min]="1" [max]="48" [step]="0.5" />
          </div>
          <div class="flex flex-col gap-1">
            <label class="text-sm font-medium">Horas frente a grupo</label>
            <p-inputNumber [(ngModel)]="configHrsFG" [min]="1" [max]="40" [step]="0.5" />
          </div>
        </div>

        <div class="slots-editor">
          @for (dia of dias; track dia.num) {
            <div class="dia-section">
              <h4 class="dia-titulo">{{ dia.nombre }}</h4>
              @for (slot of nuevosSlotsForDia(dia.num); track slot._idx) {
                <div class="slot-row">
                  <input pInputText [(ngModel)]="slot.hora_inicio" placeholder="08:00" class="hora-input" />
                  <span class="sep">–</span>
                  <input pInputText [(ngModel)]="slot.hora_fin" placeholder="09:00" class="hora-input" />
                  <p-checkbox [(ngModel)]="slot.disponible" [binary]="true" pTooltip="Disponible" />
                  <p-button icon="pi pi-times" size="small" [text]="true" severity="danger"
                    (onClick)="removeSlot(dia.num, slot._idx)" />
                </div>
              }
              <p-button icon="pi pi-plus" [label]="'Agregar slot'" size="small" [text]="true"
                (onClick)="addSlot(dia.num)" class="mt-1" />
            </div>
          }
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dialogConfig=false" />
        <p-button label="Guardar disponibilidad" icon="pi pi-save" [loading]="guardando()"
          (onClick)="guardarDisponibilidad()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .apex-page { padding: 1rem; }
    .apex-toolbar { display:flex; align-items:center; justify-content:space-between; margin-bottom:1rem; flex-wrap:wrap; gap:.5rem; }
    .apex-title { margin:0; font-size:1.25rem; font-weight:600; }
    .apex-toolbar-actions { display:flex; gap:.5rem; align-items:center; flex-wrap:wrap; }
    .resumen-bar { display:flex; gap:1.5rem; padding:.75rem 1rem; background:var(--surface-100); border-radius:8px; margin-bottom:1rem; border:1px solid var(--surface-200); align-items:center; flex-wrap:wrap; }
    .res-item { display:flex; flex-direction:column; align-items:center; }
    .res-num { font-size:1.4rem; font-weight:700; }
    .res-lbl { font-size:.7rem; color:var(--text-color-secondary); }
    .res-dias { display:flex; gap:.25rem; flex-wrap:wrap; }
    .dia-chip { background:var(--primary-100); color:var(--primary-700); padding:.15rem .5rem; border-radius:4px; font-size:.75rem; font-weight:600; }
    .semana-grid { display:grid; grid-template-columns:repeat(5,1fr); gap:.5rem; margin-bottom:1rem; }
    .dia-col { border:1px solid var(--surface-200); border-radius:6px; overflow:hidden; }
    .dia-header { background:var(--primary-600); color:white; text-align:center; padding:.25rem; font-size:.8rem; font-weight:600; }
    .slot-card { background:var(--surface-50); margin:.2rem; padding:.25rem .5rem; border-radius:4px; border-left:3px solid var(--primary-400); }
    .slot-card.slot-no-disp { border-left-color:var(--red-400); background:var(--red-50); }
    .slot-hora { font-size:.75rem; font-weight:600; display:block; }
    .slot-motivo { font-size:.65rem; color:var(--text-color-secondary); }
    .slot-empty { text-align:center; color:var(--text-color-secondary); font-size:.75rem; padding:.5rem; }
    .slots-editor { display:grid; grid-template-columns:repeat(5,1fr); gap:1rem; }
    .dia-section { }
    .dia-titulo { font-weight:600; font-size:.85rem; margin-bottom:.5rem; color:var(--primary-600); }
    .slot-row { display:flex; align-items:center; gap:.25rem; margin-bottom:.25rem; }
    .hora-input { width:70px !important; font-size:.8rem; }
    .sep { font-weight:bold; }
  `],
})
export class DisponibilidadComponent implements OnInit {
  private http = inject(HttpClient);
  private notify = inject(ApexNotificationService);

  readonly slotsColumns: ColumnConfig[] = [
    { field: 'dia_nombre',     header: 'Día',         width: '110px' },
    { field: 'hora_inicio_str', header: 'Hora Inicio', width: '100px' },
    { field: 'hora_fin_str',   header: 'Hora Fin',    width: '100px' },
    { field: 'disponible_str', header: 'Disponible',  width: '120px' },
    { field: 'motivo_str',     header: 'Motivo' },
  ];

  readonly slotsFlat = computed(() =>
    this.slots().map(s => ({
      ...s,
      hora_inicio_str: s.hora_inicio.slice(0, 5),
      hora_fin_str:    s.hora_fin.slice(0, 5),
      disponible_str:  s.disponible ? 'Disponible' : 'No disponible',
      motivo_str:      s.motivo_no_disponible ?? '—',
    }))
  );

  slots    = signal<SlotDisponibilidad[]>([]);
  resumen  = signal<ResumenDisponibilidad | null>(null);
  cargando = signal(false);
  guardando = signal(false);
  docenteSugerencias = signal<{ label: string; value: string }[]>([]);
  docenteSeleccionado: { label: string; value: string } | null = null;

  dialogConfig = false;
  profesorId   = '';
  configHrsMax = 20;
  configHrsFG  = 16;
  nuevosSlots: { dia_semana: number; hora_inicio: string; hora_fin: string; disponible: boolean; _idx: number }[] = [];

  dias = [
    { num: 0, nombre: 'Lunes' }, { num: 1, nombre: 'Martes' },
    { num: 2, nombre: 'Miércoles' }, { num: 3, nombre: 'Jueves' },
    { num: 4, nombre: 'Viernes' },
  ];

  ngOnInit() {}

  buscarDocente(event: { query: string }) {
    if (!event.query || event.query.length < 2) { this.docenteSugerencias.set([]); return; }
    this.http.get<any>('/api/v1/profesores', { params: { buscar: event.query } }).subscribe({
      next: res => {
        const data = res?.data ?? res ?? [];
        this.docenteSugerencias.set(data.map((p: any) => ({
          label: [p.nombre ?? p.persona?.nombre, p.apellido_paterno ?? p.persona?.apellido_paterno, p.apellido_materno ?? p.persona?.apellido_materno].filter(Boolean).join(' '),
          value: p.id,
        })));
      },
      error: () => this.docenteSugerencias.set([]),
    });
  }

  onDocenteSeleccionado(event: any) {
    const item = event?.value ?? event;
    this.profesorId = item?.value ?? '';
    if (this.profesorId) this.cargar();
  }

  onDocenteLimpiado() {
    this.profesorId = '';
    this.slots.set([]);
    this.resumen.set(null);
  }

  cargar() {
    if (!this.profesorId) return;
    this.cargando.set(true);
    this.http.get<SlotDisponibilidad[]>('/api/v1/disponibilidad', { params: { profesor_id: this.profesorId } }).subscribe({
      next: d => { this.slots.set(d); this.cargando.set(false); this.cargarResumen(); },
      error: () => { this.cargando.set(false); this.notify.error('Error al cargar disponibilidad'); },
    });
  }

  cargarResumen() {
    this.http.get<ResumenDisponibilidad>(`/api/v1/disponibilidad/docente/${this.profesorId}/resumen`).subscribe({
      next: r => this.resumen.set(r),
      error: () => {},
    });
  }

  slotsForDia(dia: number): SlotDisponibilidad[] {
    return this.slots().filter(s => s.dia_semana === dia);
  }

  abrirConfigurar() {
    this.nuevosSlots = [];
    let idx = 0;
    // Precargar slots actuales
    for (const s of this.slots()) {
      this.nuevosSlots.push({
        dia_semana: s.dia_semana, hora_inicio: s.hora_inicio.slice(0,5),
        hora_fin: s.hora_fin.slice(0,5), disponible: s.disponible, _idx: idx++,
      });
    }
    if (this.resumen()) {
      this.configHrsMax = this.resumen()!.horas_semana_max;
      this.configHrsFG  = this.resumen()!.horas_frente_grupo;
    }
    this.dialogConfig = true;
  }

  nuevosSlotsForDia(dia: number) {
    return this.nuevosSlots.filter(s => s.dia_semana === dia);
  }

  addSlot(dia: number) {
    this.nuevosSlots.push({ dia_semana: dia, hora_inicio: '08:00', hora_fin: '09:00', disponible: true, _idx: this.nuevosSlots.length });
  }

  removeSlot(dia: number, idx: number) {
    this.nuevosSlots = this.nuevosSlots.filter(s => !(s.dia_semana === dia && s._idx === idx));
  }

  guardarDisponibilidad() {
    this.guardando.set(true);
    const payload = {
      slots: this.nuevosSlots.map(s => ({
        dia_semana: s.dia_semana, hora_inicio: s.hora_inicio,
        hora_fin: s.hora_fin, disponible: s.disponible,
      })),
      horas_semana_max:   this.configHrsMax,
      horas_frente_grupo: this.configHrsFG,
    };
    this.http.put(`/api/v1/disponibilidad/docente/${this.profesorId}`, payload).subscribe({
      next: () => {
        this.guardando.set(false); this.dialogConfig = false;
        this.notify.success('Disponibilidad guardada');
        this.cargar();
      },
      error: (e) => { this.guardando.set(false); this.notify.error(e.error?.detail ?? 'Error al guardar'); },
    });
  }
}
