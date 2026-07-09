/**
 * AulasComponent — Gestión de aulas y espacios físicos (AC-006).
 *
 * Interactive Grid APEX-style con:
 *  - Filtros: plantel, tipo_aula, estado, capacidad mínima, equipamiento
 *  - KPI strip: total aulas, activas, en mantenimiento, % promedio ocupación
 *  - Dialog crear/editar con tabs: Datos generales / Equipamiento / Disponibilidad
 *  - Verificación de conflictos de franja horaria antes de asignar
 */
import { Component, OnDestroy, OnInit, inject, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { TabsModule } from 'primeng/tabs';
import { DividerModule } from 'primeng/divider';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService, ExportColumn } from '../../core/services/export.service';
import { ApexNotificationService } from 'apex-component-library';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import { ImportButtonComponent } from '../../shared/components/import-button/import-button.component';

type TagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

const TIPO_AULA_OPTS = [
  { label: 'Salón',           value: 'SALON' },
  { label: 'Aula (genérico)', value: 'AULA' },
  { label: 'Laboratorio',     value: 'LABORATORIO' },
  { label: 'Cómputo',        value: 'COMPUTO' },
  { label: 'Taller',          value: 'TALLER' },
  { label: 'Auditorio',       value: 'AUDITORIO' },
  { label: 'Biblioteca',      value: 'BIBLIOTECA' },
  { label: 'Gimnasio',        value: 'GIMNASIO' },
  { label: 'Cancha',          value: 'CANCHA' },
  { label: 'Área deportiva',  value: 'AREA_DEPORTIVA' },
  { label: 'Sala de maestros','value': 'SALA_MAESTROS' },
  { label: 'Dirección',       value: 'DIRECCION' },
  { label: 'Otro',            value: 'OTRO' },
];

const ESTADO_OPTS = [
  { label: 'Activa',           value: 'ACTIVA' },
  { label: 'En mantenimiento', value: 'EN_MANTENIMIENTO' },
  { label: 'Inhabilitada',     value: 'INHABILITADA' },
  { label: 'Fuera de servicio','value': 'FUERA_DE_SERVICIO' },
];

const DIAS_SEMANA = [
  { label: 'Lunes',     value: 1 },
  { label: 'Martes',    value: 2 },
  { label: 'Miércoles', value: 3 },
  { label: 'Jueves',    value: 4 },
  { label: 'Viernes',   value: 5 },
  { label: 'Sábado',    value: 6 },
];

const ESTADO_SEVERITY: Record<string, TagSeverity> = {
  ACTIVA: 'success', EN_MANTENIMIENTO: 'warn', INHABILITADA: 'secondary', FUERA_DE_SERVICIO: 'danger',
};

@Component({
  selector: 'app-aulas',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    ButtonModule, SelectModule, InputTextModule, InputNumberModule,
    ToggleSwitchModule, TagModule, DialogModule, TabsModule,
    DividerModule,
    InteractiveGridComponent, ImportButtonComponent,
  ],
  template: `
    <div class="page-header">
      <div>
        <h2>Aulas y Espacios Físicos</h2>
        <p class="subtitle">Gestión de capacidad, equipamiento y disponibilidad — AC-006</p>
      </div>
      <div style="display:flex;gap:0.5rem;align-items:center">
        <p-button label="CSV"   icon="pi pi-file"       severity="secondary" [text]="true" (onClick)="exportCSV()" />
        <p-button label="Excel" icon="pi pi-file-excel" severity="secondary" [text]="true" (onClick)="exportXLSX()" />
        @if (puedeGestionar()) {
          <app-import-button entidad="aulas" [onSuccess]="cargar.bind(this)" />
          <p-button label="Nueva aula" icon="pi pi-plus" (onClick)="abrirNueva()" />
        }
      </div>
    </div>

    <!-- KPI Strip -->
    <div class="kpi-strip">
      <div class="kpi-card">
        <span class="kpi-value">{{ kpis().total }}</span>
        <span class="kpi-label">Total aulas</span>
      </div>
      <div class="kpi-card kpi-green">
        <span class="kpi-value">{{ kpis().activas }}</span>
        <span class="kpi-label">Activas</span>
      </div>
      <div class="kpi-card kpi-yellow">
        <span class="kpi-value">{{ kpis().mantenimiento }}</span>
        <span class="kpi-label">En mantenimiento</span>
      </div>
      <div class="kpi-card kpi-blue">
        <span class="kpi-value">{{ kpis().conProyector }}</span>
        <span class="kpi-label">Con proyector</span>
      </div>
      <div class="kpi-card kpi-purple">
        <span class="kpi-value">{{ kpis().capPromedio }}</span>
        <span class="kpi-label">Cap. promedio</span>
      </div>
    </div>

    <!-- Filtros -->
    <div class="filter-bar">
      <p-select [options]="tipoAulaOpts" [(ngModel)]="filtroTipo" optionLabel="label" optionValue="value"
                placeholder="Tipo de aula" [showClear]="true" (onChange)="cargar()" />
      <p-select [options]="estadoOpts" [(ngModel)]="filtroEstado" optionLabel="label" optionValue="value"
                placeholder="Estado" [showClear]="true" (onChange)="cargar()" />
      <div style="display:flex;align-items:center;gap:0.4rem">
        <p-toggleSwitch [(ngModel)]="filtroProyector" (onChange)="cargar()" />
        <span style="font-size:0.82rem">Con proyector</span>
      </div>
      <div style="display:flex;align-items:center;gap:0.4rem">
        <p-toggleSwitch [(ngModel)]="filtroInternet" (onChange)="cargar()" />
        <span style="font-size:0.82rem">Con internet</span>
      </div>
    </div>

    <app-interactive-grid
      [data]="aulas()"
      [columns]="columnas"
      [loading]="loading()"
      (rowSelected)="abrirDetalle($event)"
    />

    <!-- ════════════════════════════════════════════════════════════
         DIALOG: Crear / Editar aula
    ═════════════════════════════════════════════════════════════════ -->
    <p-dialog [(visible)]="showDialog" [header]="dialogHeader()"
              [modal]="true" [style]="{ width: '680px' }" [closable]="true"
              (onHide)="resetForm()">

      <p-tabs [(value)]="dialogTab">
        <p-tablist>
          <p-tab value="0">Datos generales</p-tab>
          <p-tab value="1">Equipamiento</p-tab>
          <p-tab value="2" [disabled]="!editandoId()">
            Disponibilidad
            @if (franjas().length) {
              <p-tag [value]="'' + franjas().length" severity="secondary" styleClass="ml-1" style="font-size:.7rem" />
            }
          </p-tab>
        </p-tablist>

        <p-tabpanels>

          <!-- Tab 0: Datos generales -->
          <p-tabpanel value="0">
            <div class="form-grid">
              <div class="field full-width">
                <label>Plantel *</label>
                <p-select [options]="plantelesOpts()" [(ngModel)]="form.plantel_id"
                          optionLabel="nombre_plantel" optionValue="id"
                          placeholder="Seleccionar plantel" style="width:100%" [filter]="true" />
              </div>
              <div class="field">
                <label>Nombre del aula *</label>
                <input pInputText [(ngModel)]="form.nombre_aula" style="width:100%" placeholder="Ej: Salón 1A" />
              </div>
              <div class="field">
                <label>Clave corta</label>
                <input pInputText [(ngModel)]="form.clave_aula" style="width:100%" placeholder="Ej: A-101" />
              </div>
              <div class="field">
                <label>Tipo de espacio</label>
                <p-select [options]="tipoAulaOpts" [(ngModel)]="form.tipo_aula"
                          optionLabel="label" optionValue="value" style="width:100%" />
              </div>
              <div class="field">
                <label>Estado</label>
                <p-select [options]="estadoOpts" [(ngModel)]="form.estado_aula"
                          optionLabel="label" optionValue="value" style="width:100%" />
              </div>
              <div class="field">
                <label>Piso</label>
                <p-select [options]="pisosOpts()" [(ngModel)]="form.piso"
                          optionLabel="label" optionValue="value"
                          placeholder="Seleccionar piso" style="width:100%" />
              </div>
              <div class="field">
                <label>Edificio / Bloque</label>
                <p-select [options]="edificiosOpts()" [(ngModel)]="form.edificio"
                          optionLabel="valor" optionValue="valor"
                          placeholder="Seleccionar edificio" style="width:100%" [filter]="true" />
              </div>
              <div class="field">
                <label>Capacidad alumnos</label>
                <p-inputnumber [(ngModel)]="form.capacidad_alumnos" [min]="1" [max]="500" style="width:100%" />
              </div>
              <div class="field">
                <label>Capacidad máxima</label>
                <p-inputnumber [(ngModel)]="form.capacidad_maxima" [min]="1" [max]="500" style="width:100%" />
              </div>
              <div class="field full-width">
                <label>Observaciones</label>
                <input pInputText [(ngModel)]="form.observaciones" style="width:100%" placeholder="Notas adicionales..." />
              </div>
            </div>
          </p-tabpanel>

          <!-- Tab 1: Equipamiento -->
          <p-tabpanel value="1">
            <div class="equip-grid">
              <div class="equip-item">
                <p-toggleSwitch [(ngModel)]="form.tiene_proyector" />
                <label>Proyector / cañón</label>
              </div>
              <div class="equip-item">
                <p-toggleSwitch [(ngModel)]="form.tiene_pizarra_digital" />
                <label>Pizarra digital</label>
              </div>
              <div class="equip-item">
                <p-toggleSwitch [(ngModel)]="form.tiene_pizarron" />
                <label>Pizarrón</label>
              </div>
              <div class="equip-item">
                <p-toggleSwitch [(ngModel)]="form.tiene_aire_acondicionado" />
                <label>Aire acondicionado</label>
              </div>
              <div class="equip-item">
                <p-toggleSwitch [(ngModel)]="form.tiene_ventiladores" />
                <label>Ventiladores</label>
              </div>
              <div class="equip-item">
                <p-toggleSwitch [(ngModel)]="form.tiene_internet" />
                <label>Internet / WiFi</label>
              </div>
              <div class="equip-item full-row">
                <label style="min-width:160px">Computadoras</label>
                <p-inputnumber [(ngModel)]="form.num_computadoras" [min]="0" [max]="100" [showButtons]="true" />
              </div>
            </div>
          </p-tabpanel>

          <!-- Tab 2: Disponibilidad semanal -->
          <p-tabpanel value="2">
            <!-- Lista de franjas existentes -->
            @if (franjas().length) {
              <app-interactive-grid
                [data]="franjasFlat()"
                [columns]="franjasColumns"
                [showDelete]="true"
                (rowDeleted)="eliminarFranja($event.id)"
                style="margin-bottom:1rem;display:block"
              />
            } @else {
              <p style="color:var(--text-secondary);font-size:0.85rem;margin-bottom:1rem">Sin franjas asignadas.</p>
            }

            <p-divider align="left"><span style="font-size:0.82rem;font-weight:600">Agregar franja</span></p-divider>
            <div class="form-grid">
              <div class="field">
                <label>Día</label>
                <p-select [options]="diasOpts" [(ngModel)]="franjaForm.dia_semana"
                          optionLabel="label" optionValue="value" style="width:100%" />
              </div>
              <div class="field">
                <label>Hora inicio (HH:MM)</label>
                <input pInputText [(ngModel)]="franjaForm.hora_inicio" placeholder="07:00" style="width:100%" />
              </div>
              <div class="field">
                <label>Hora fin (HH:MM)</label>
                <input pInputText [(ngModel)]="franjaForm.hora_fin" placeholder="13:00" style="width:100%" />
              </div>
              <div class="field full-width">
                <label>Motivo / descripción</label>
                <input pInputText [(ngModel)]="franjaForm.motivo_bloqueo" style="width:100%" placeholder="Ej: Clase de Matemáticas 2A" />
              </div>
            </div>
            <div style="display:flex;gap:0.75rem;align-items:center;margin-top:0.5rem">
              <p-button label="Verificar conflicto" icon="pi pi-search" severity="secondary"
                        (onClick)="verificarConflicto()" [loading]="checkingConflicto()" />
              <p-button label="Asignar franja" icon="pi pi-plus" [loading]="savingFranja()"
                        [disabled]="conflictoDetectado()"
                        (onClick)="asignarFranja()" />
              @if (conflictoDetectado()) {
                <p-tag value="⚠ Conflicto detectado" severity="danger" />
              }
              @if (conflictoOk()) {
                <p-tag value="✓ Sin conflicto" severity="success" />
              }
            </div>
          </p-tabpanel>

        </p-tabpanels>
      </p-tabs>

      <ng-template pTemplate="footer">
        <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="showDialog = false" />
        @if (dialogTab !== '2') {
          <p-button [label]="editandoId() ? 'Guardar cambios' : 'Crear aula'"
                    icon="pi pi-save" [loading]="saving()" (onClick)="guardar()" />
        }
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .kpi-strip { display:flex; gap:1rem; margin-bottom:1rem; flex-wrap:wrap; }
    .kpi-card { background:var(--surface-card); border:1px solid var(--surface-border);
                border-radius:8px; padding:0.75rem 1.25rem; display:flex; flex-direction:column;
                align-items:center; min-width:110px; }
    .kpi-value { font-size:1.6rem; font-weight:700; font-family:'Jost',sans-serif; }
    .kpi-label { font-size:0.72rem; color:var(--text-secondary); text-transform:uppercase; }
    .kpi-green  .kpi-value { color:var(--green-600); }
    .kpi-yellow .kpi-value { color:var(--yellow-600); }
    .kpi-blue   .kpi-value { color:var(--blue-600); }
    .kpi-purple .kpi-value { color:var(--purple-600); }
    .filter-bar { display:flex; gap:0.75rem; margin-bottom:1rem; flex-wrap:wrap; align-items:center; }
    .form-grid { display:grid; grid-template-columns:1fr 1fr; gap:0.75rem 1rem; }
    .field { display:flex; flex-direction:column; gap:0.25rem; }
    .field.full-width { grid-column:1/-1; }
    .field label { font-size:0.82rem; font-weight:600; color:var(--text-secondary); }
    .equip-grid { display:grid; grid-template-columns:1fr 1fr; gap:0.85rem; }
    .equip-item { display:flex; align-items:center; gap:0.6rem; }
    .equip-item label { font-size:0.88rem; cursor:pointer; }
    .equip-item.full-row { grid-column:1/-1; }
  `],
})
export class AulasComponent implements OnInit implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private readonly api    = inject(ApiService);
  readonly ctx            = inject(ContextService);
  private readonly notify = inject(ApexNotificationService);
  private readonly export = inject(ExportService);

  // ── State ─────────────────────────────────────────────────────
  aulas    = signal<any[]>([]);
  loading  = signal(false);
  saving   = signal(false);

  plantelesOpts = signal<any[]>([]);
  edificiosOpts = signal<any[]>([]);
  pisosOpts     = signal<any[]>([]);

  readonly pisoLabels: Record<number, string> = {
    0: 'Planta Baja',
    1: 'Primer Piso',
    2: 'Segundo Piso',
    3: 'Tercer Piso',
    99: 'Otro'
  };

  // Filtros
  filtroPlantel: string | null = null;
  filtroTipo:    string | null = null;
  filtroEstado:  string | null = null;
  filtroProyector = false;
  filtroInternet  = false;

  readonly tipoAulaOpts = TIPO_AULA_OPTS;
  readonly estadoOpts   = ESTADO_OPTS;
  readonly diasOpts     = DIAS_SEMANA;

  // KPIs
  readonly kpis = computed(() => {
    const a = this.aulas();
    return {
      total:        a.length,
      activas:      a.filter(x => x.estado_aula === 'ACTIVA').length,
      mantenimiento: a.filter(x => x.estado_aula === 'EN_MANTENIMIENTO').length,
      conProyector: a.filter(x => x.tiene_proyector).length,
      capPromedio:  a.length ? Math.round(a.reduce((s, x) => s + (x.capacidad_alumnos ?? 0), 0) / a.length) : 0,
    };
  });

  readonly puedeGestionar = computed(() => (this.ctx.usuario()?.nivel_acceso ?? 99) <= 3);

  readonly columnas: ColumnConfig[] = [
    { field: 'nombre_plantel',    header: 'Plantel',     sortable: true,  filterable: true,  width: '120px' },
    { field: 'nombre_aula',       header: 'Aula',        sortable: true,  filterable: true  },
    { field: 'clave_aula',        header: 'Clave',       sortable: false, filterable: false, width: '80px'  },
    { field: 'tipo_aula',         header: 'Tipo',        sortable: true,  filterable: true,  width: '110px' },
    { field: 'capacidad_alumnos', header: 'Capacidad',   sortable: true,  filterable: false, width: '90px'  },
    { field: 'piso_str',          header: 'Piso',        sortable: true,  filterable: false, width: '90px'  },
    { field: 'estado_str',        header: 'Estado',      sortable: true,  filterable: true,  width: '120px' },
    { field: 'equip_str',         header: 'Equipamiento',sortable: false, filterable: false, width: '150px' },
  ];

  // ── Dialog ────────────────────────────────────────────────────
  showDialog  = false;
  dialogTab   = '0';
  editandoId  = signal<string | null>(null);
  readonly dialogHeader = computed(() => this.editandoId() ? 'Editar aula' : 'Nueva aula');

  form = this.formVacio();

  // ── Disponibilidad ────────────────────────────────────────────
  franjas          = signal<any[]>([]);
  savingFranja     = signal(false);

  readonly franjasFlat = computed(() =>
    this.franjas().map(f => ({
      ...f,
      dia_str:    this.diaNombre(f.dia_semana),
      motivo_str: f.motivo_bloqueo ?? f.nombre_grupo ?? '—',
    }))
  );
  readonly franjasColumns: ColumnConfig[] = [
    { field: 'dia_str',    header: 'Día',           width: '100px' },
    { field: 'hora_inicio',header: 'Inicio',        width: '90px' },
    { field: 'hora_fin',   header: 'Fin',           width: '90px' },
    { field: 'motivo_str', header: 'Grupo / Motivo' },
  ];
  checkingConflicto = signal(false);
  conflictoDetectado = signal(false);
  conflictoOk       = signal(false);

  franjaForm = { dia_semana: 1, hora_inicio: '', hora_fin: '', motivo_bloqueo: '' };

  constructor() {
    effect(() => {
      this.filtroPlantel = this.ctx.plantel()?.id ?? null;
      this.cargar();
    });
  }

  ngOnInit(): void {
    this.api.get<any[]>('/planteles').subscribe({
      next: p => this.plantelesOpts.set(p),
      error: () => {},
    });
    
    // Cargar catálogos dinámicos
    this.api.get<any[]>('/catalogos').subscribe({
      next: cats => {
        const edif = cats.find(c => c.codigo === 'CAT_EDIFICIOS');
        if (edif) this.edificiosOpts.set(edif.items ?? []);
        
        const piso = cats.find(c => c.codigo === 'CAT_PISOS');
        if (piso) {
          const mapped = (piso.items ?? []).map((item: any) => {
            let numVal = 99;
            if (item.valor === 'Planta Baja') numVal = 0;
            else if (item.valor === 'Primer Piso') numVal = 1;
            else if (item.valor === 'Segundo Piso') numVal = 2;
            else if (item.valor === 'Tercer Piso') numVal = 3;
            return { label: item.valor, value: numVal };
          });
          this.pisosOpts.set(mapped);
        }
      },
      error: () => {},
    });

    this.cargar();
  }

  cargar(): void {
    this.loading.set(true);
    const params: Record<string, any> = {};
    if (this.filtroPlantel) params['plantel_id'] = this.filtroPlantel;
    if (this.filtroTipo)    params['tipo_aula']   = this.filtroTipo;
    if (this.filtroEstado)  params['estado_aula'] = this.filtroEstado;
    if (this.filtroProyector) params['con_proyector'] = true;
    if (this.filtroInternet)  params['con_internet']  = true;

    this.api.get<any[]>('/aulas', params).subscribe({
      next: r => {
        this.aulas.set(r.map(a => ({
          ...a,
          piso_str: this.pisoLabels[a.piso] ?? `Piso ${a.piso}`,
          estado_str: a.estado_aula?.replace(/_/g, ' ') ?? '—',
          equip_str: [
            a.tiene_proyector      ? 'Proyector'  : '',
            a.tiene_pizarra_digital ? 'Pizarra dig.' : '',
            a.tiene_internet       ? 'WiFi'       : '',
            a.num_computadoras > 0 ? `${a.num_computadoras} PC` : '',
          ].filter(Boolean).join(', ') || '—',
        })));
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  abrirNueva(): void {
    this.form = this.formVacio();
    this.editandoId.set(null);
    this.franjas.set([]);
    this.dialogTab = '0';
    this.showDialog = true;
  }

  abrirDetalle(aula: any): void {
    this.editandoId.set(aula.id);
    this.form = {
      plantel_id:            aula.plantel_id,
      nombre_aula:           aula.nombre_aula,
      clave_aula:            aula.clave_aula ?? '',
      piso:                  aula.piso ?? 1,
      edificio:              aula.edificio ?? '',
      capacidad_alumnos:     aula.capacidad_alumnos ?? 30,
      capacidad_maxima:      aula.capacidad_maxima ?? null,
      tipo_aula:             aula.tipo_aula,
      estado_aula:           aula.estado_aula,
      tiene_proyector:       aula.tiene_proyector,
      tiene_pizarra_digital: aula.tiene_pizarra_digital,
      tiene_pizarron:        aula.tiene_pizarron,
      tiene_aire_acondicionado: aula.tiene_aire_acondicionado,
      tiene_ventiladores:    aula.tiene_ventiladores,
      tiene_internet:        aula.tiene_internet,
      num_computadoras:      aula.num_computadoras,
      observaciones:         aula.observaciones ?? '',
    };
    this.dialogTab = '0';
    this.showDialog = true;
    this.cargarFranjas(aula.id);
  }

  cargarFranjas(aulaId: string): void {
    this.api.get<any>(`/aulas/${aulaId}`).subscribe({
      next: d => this.franjas.set(d.franjas ?? []),
      error: () => this.franjas.set([]),
    });
  }

  guardar(): void {
    if (!this.form.plantel_id || !this.form.nombre_aula) {
      this.notify.warning('Campos requeridos', 'Plantel y nombre son obligatorios');
      return;
    }
    this.saving.set(true);
    const id = this.editandoId();
    const req = id
      ? this.api.patch(`/aulas/${id}`, this.form)
      : this.api.post('/aulas', this.form);

    req.subscribe({
      next: () => {
        this.notify.success(id ? 'Aula actualizada' : 'Aula creada');
        this.saving.set(false);
        this.showDialog = false;
        this.cargar();
      },
      error: (e: any) => {
        this.notify.error('Error', e.error?.detail ?? 'Error al guardar');
        this.saving.set(false);
      },
    });
  }

  verificarConflicto(): void {
    const id = this.editandoId();
    if (!id) return;
    this.checkingConflicto.set(true);
    this.conflictoDetectado.set(false);
    this.conflictoOk.set(false);

    this.api.post<any>(`/aulas/${id}/verificar-conflicto`, {
      dia_semana:  this.franjaForm.dia_semana,
      hora_inicio: this.franjaForm.hora_inicio,
      hora_fin:    this.franjaForm.hora_fin,
    }).subscribe({
      next: r => {
        this.conflictoDetectado.set(r.conflicto);
        this.conflictoOk.set(!r.conflicto);
        this.checkingConflicto.set(false);
        if (r.conflicto) {
          this.notify.warning('Conflicto', `El aula ya está asignada en ${r.num_conflictos} franja(s) que se solapan`);
        }
      },
      error: () => this.checkingConflicto.set(false),
    });
  }

  asignarFranja(): void {
    const id = this.editandoId();
    if (!id) return;
    if (!this.franjaForm.hora_inicio || !this.franjaForm.hora_fin) {
      this.notify.warning('Campos requeridos', 'Hora inicio y hora fin son obligatorios');
      return;
    }
    this.savingFranja.set(true);
    this.api.post(`/aulas/${id}/disponibilidad`, this.franjaForm).subscribe({
      next: () => {
        this.notify.success('Franja asignada');
        this.savingFranja.set(false);
        this.franjaForm = { dia_semana: 1, hora_inicio: '', hora_fin: '', motivo_bloqueo: '' };
        this.conflictoDetectado.set(false);
        this.conflictoOk.set(false);
        this.cargarFranjas(id);
      },
      error: (e: any) => {
        this.notify.error('Error', e.error?.detail ?? 'Error al asignar franja');
        this.savingFranja.set(false);
      },
    });
  }

  eliminarFranja(franjaId: string): void {
    this.savingFranja.set(true);
    this.api.delete(`/aulas/disponibilidad/${franjaId}`).subscribe({
      next: () => {
        this.notify.success('Franja liberada');
        this.savingFranja.set(false);
        const id = this.editandoId();
        if (id) this.cargarFranjas(id);
      },
      error: (e: any) => {
        this.notify.error('Error', e.error?.detail ?? 'Error al eliminar');
        this.savingFranja.set(false);
      },
    });
  }

  resetForm(): void {
    this.editandoId.set(null);
    this.franjas.set([]);
    this.conflictoDetectado.set(false);
    this.conflictoOk.set(false);
    this.form = this.formVacio();
  }

  diaNombre(dia: number): string {
    return DIAS_SEMANA.find(d => d.value === dia)?.label ?? `Día ${dia}`;
  }

  estadoSeverity(estado: string): TagSeverity {
    return ESTADO_SEVERITY[estado] ?? 'secondary';
  }

  private formVacio() {
    return {
      plantel_id: this.ctx.plantel()?.id ?? '',
      nombre_aula: '', clave_aula: '', piso: 1, edificio: '',
      capacidad_alumnos: 30, capacidad_maxima: null as number | null,
      tipo_aula: 'SALON', estado_aula: 'ACTIVA',
      tiene_proyector: false, tiene_pizarra_digital: false, tiene_pizarron: true,
      tiene_aire_acondicionado: false, tiene_ventiladores: false, tiene_internet: false,
      num_computadoras: 0, observaciones: '',
    };
  }

  private readonly exportCols: ExportColumn[] = [
    { field: 'nombre_plantel',    header: 'Plantel' },
    { field: 'nombre_aula',       header: 'Aula' },
    { field: 'clave_aula',        header: 'Clave', format: v => v || '' },
    { field: 'tipo_aula',         header: 'Tipo' },
    { field: 'capacidad_alumnos', header: 'Capacidad' },
    { field: 'edificio',          header: 'Edificio', format: v => v || '—' },
    { field: 'piso',              header: 'Piso' },
    { field: 'estado_aula',       header: 'Estado' },
    { field: 'tiene_proyector',     header: 'Proyector', format: v => v ? 'Sí' : 'No' },
    { field: 'tiene_pizarra_digital', header: 'Pizarra Digital', format: v => v ? 'Sí' : 'No' },
    { field: 'tiene_pizarron',      header: 'Pizarrón', format: v => v ? 'Sí' : 'No' },
    { field: 'tiene_aire_acondicionado', header: 'A/C', format: v => v ? 'Sí' : 'No' },
    { field: 'tiene_internet',      header: 'Internet', format: v => v ? 'Sí' : 'No' },
    { field: 'num_computadoras',    header: 'Computadoras', format: v => String(v ?? 0) },
    { field: 'observaciones',       header: 'Observaciones', format: v => v || '' },
  ];
  exportCSV():  void { this.export.toCSV(this.aulas(), this.exportCols, 'aulas'); }
  exportXLSX(): void { this.export.toXLSX(this.aulas(), this.exportCols, 'Aulas', 'aulas'); }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
