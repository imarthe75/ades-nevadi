import { Component, OnDestroy, OnInit, signal, computed, inject, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { TextareaModule } from 'primeng/textarea';
import { DatePickerModule } from 'primeng/datepicker';
import { TagModule } from 'primeng/tag';
import { TabsModule, TabList, TabPanels, Tab, TabPanel } from 'primeng/tabs';
import { ApexNotificationService } from 'apex-component-library';
import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import { grupoLabel } from '../../core/models';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';

interface Alumno {
  id: string;
  matricula: string;
  persona: { nombre: string; apellido_paterno: string; apellido_materno: string | null; };
}

interface Grupo {
  id: string;
  nombre_grupo: string;
  [key: string]: any;
}

interface Baja {
  id: string;
  estudiante_id: string;
  tipo_baja: string;
  motivo: string;
  fecha_efectiva: string;
  fecha_reingreso: string | null;
  plantel_destino: string | null;
  is_active: boolean;
  nombre_alumno: string;
  numero_control: string;
  nombre_grupo: string;
  nombre_plantel: string;
}

interface CambioGrupo {
  id: string;
  fecha_cambio: string;
  motivo: string;
  nombre_alumno: string;
  numero_control: string;
  grupo_origen: string;
  grupo_destino: string;
}

@Component({
  selector: 'app-movilidad',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule, ButtonModule, DialogModule,
    InputTextModule, SelectModule, TextareaModule, DatePickerModule, AutoCompleteModule,
    TagModule, TabsModule, TabList, TabPanels, Tab, TabPanel,
    InteractiveGridComponent,
  ],
  template: `
    <div class="apex-page">
      <div class="apex-page-header">
        <h2 class="apex-title">Movilidad Estudiantil</h2>
      </div>

      <p-tabs [value]="tabActivo()" (valueChange)="onTabChange($event)">
        <p-tablist>
          <p-tab value="bajas"><i class="pi pi-user-minus"></i> Bajas</p-tab>
          <p-tab value="cambios"><i class="pi pi-arrows-h"></i> Cambios de Grupo</p-tab>
          <p-tab value="traslados"><i class="pi pi-send"></i> Traslados</p-tab>
        </p-tablist>

        <p-tabpanels>

          <!-- TAB: BAJAS (PE-020, PE-021, PE-022) -->
          <p-tabpanel value="bajas">
            <div class="tab-toolbar">
              <div class="stats-bar">
                <span class="stat-chip">Total activas: <strong>{{ countBajasActivas() }}</strong></span>
                <span class="stat-chip warn">Temporales: <strong>{{ countTipo('TEMPORAL') }}</strong></span>
                <span class="stat-chip danger">Definitivas: <strong>{{ countTipo('DEFINITIVA') }}</strong></span>
              </div>
              <div class="tab-actions">
                <p-button label="Baja Temporal" icon="pi pi-clock" severity="secondary" size="small"
                  (onClick)="abrirBajaTemporal()" />
                <p-button label="Baja Definitiva" icon="pi pi-ban" severity="danger" size="small"
                  (onClick)="abrirBajaDefinitiva()" />
              </div>
            </div>
            <p class="hint-text"><i class="pi pi-info-circle"></i> Haz clic en una baja temporal activa para reactivar al alumno.</p>
            <app-interactive-grid
              [data]="bajasFlat()"
              [columns]="bajasColumns"
              [loading]="cargando()"
              [showDelete]="false"
              (rowSelected)="onBajaSelected($event)"
            />
          </p-tabpanel>

          <!-- TAB: CAMBIOS DE GRUPO (PE-018) -->
          <p-tabpanel value="cambios">
            <div class="tab-toolbar">
              <div></div>
              <div class="tab-actions">
                <p-button label="Nuevo Cambio de Grupo" icon="pi pi-arrows-h" severity="secondary" size="small"
                   (onClick)="dlgCambioGrupo = true; cambioGrupoForm = { estudianteId: '', grupoDestinoId: '', motivo: '' }; cambioGrupoAlumnoObj = null" />
              </div>
            </div>
            <app-interactive-grid
              [data]="cambiosFlat()"
              [columns]="cambiosColumns"
              [loading]="cargandoCambios()"
              [showDelete]="false"
            />
          </p-tabpanel>

          <!-- TAB: TRASLADOS (PE-019) -->
          <p-tabpanel value="traslados">
            <div class="tab-toolbar">
              <div></div>
              <div class="tab-actions">
                <p-button label="Registrar Traslado" icon="pi pi-send" severity="secondary" size="small"
                   (onClick)="dlgTraslado = true; trasladoForm = { estudianteId: '', plantelDestinoNombre: '', motivo: '' }; trasladoAlumnoObj = null" />
              </div>
            </div>
            <app-interactive-grid
              [data]="trasladosFlat()"
              [columns]="trasladosColumns"
              [loading]="cargando()"
              [showDelete]="false"
            />
          </p-tabpanel>

        </p-tabpanels>
      </p-tabs>
    </div>

    <!-- Dialog: Baja Temporal -->
    <p-dialog header="Registrar Baja Temporal" [(visible)]="dlgBajaTemporal"
      [modal]="true" [style]="{width:'480px'}" [draggable]="false">
      <div class="dlg-grid">
        <div class="field">
          <label>Alumno *</label>
          <p-autoComplete
            [(ngModel)]="bajaTemporalAlumnoObj"
            [suggestions]="estudiantesSugg()"
            (completeMethod)="buscarEstudiantes($event)"
            optionLabel="nombre_completo"
            [forceSelection]="true"
            placeholder="Buscar alumno..."
            [showClear]="true"
            [dropdown]="true"
            (onSelect)="bajaTemporalForm.estudianteId = $event.value.id"
            (onClear)="bajaTemporalForm.estudianteId = ''; bajaTemporalAlumnoObj = null"
            [style]="{ width: '100%' }" />
        </div>
        <div class="field">
          <label>Motivo *</label>
          <textarea pTextarea [(ngModel)]="bajaTemporalForm.motivo" rows="3" style="width:100%"
            [class.p-invalid]="btIntento() && !bajaTemporalForm.motivo.trim()"></textarea>
          @if (btIntento() && !bajaTemporalForm.motivo.trim()) {
            <small class="field-error">El motivo es obligatorio</small>
          }
        </div>
        <div class="field">
          <label>Fecha Efectiva *</label>
          <p-datepicker [(ngModel)]="bajaTemporalForm.fechaEfectiva" [showIcon]="true" dateFormat="dd/mm/yy" [style]="{width:'100%'}"
            [class.p-invalid]="btIntento() && !bajaTemporalForm.fechaEfectiva" />
          @if (btIntento() && !bajaTemporalForm.fechaEfectiva) {
            <small class="field-error">La fecha efectiva es obligatoria</small>
          }
        </div>
        <div class="field">
          <label>Fecha Estimada de Reingreso</label>
          <p-datepicker [(ngModel)]="bajaTemporalForm.fechaReingreso" [showIcon]="true" dateFormat="dd/mm/yy" [style]="{width:'100%'}"
            [class.p-invalid]="reingresoAnteriorAEfectiva" />
          @if (reingresoAnteriorAEfectiva) {
            <small class="field-error">Debe ser igual o posterior a la fecha efectiva</small>
          }
        </div>
        <div class="field">
          <label>Observaciones</label>
          <textarea pTextarea [(ngModel)]="bajaTemporalForm.observaciones" rows="2" style="width:100%"></textarea>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dlgBajaTemporal = false" />
        <p-button label="Registrar Baja" icon="pi pi-save" severity="secondary"
          [loading]="guardando()" (onClick)="guardarBajaTemporal()" />
      </ng-template>
    </p-dialog>

    <!-- Dialog: Baja Definitiva -->
    <p-dialog header="Registrar Baja Definitiva" [(visible)]="dlgBajaDefinitiva"
      [modal]="true" [style]="{width:'480px'}" [draggable]="false">
      <div class="dlg-grid">
        <div class="field">
          <label>Alumno *</label>
          <p-autoComplete
            [(ngModel)]="bajaDefinitivaAlumnoObj"
            [suggestions]="estudiantesSugg()"
            (completeMethod)="buscarEstudiantes($event)"
            optionLabel="nombre_completo"
            [forceSelection]="true"
            placeholder="Buscar alumno..."
            [showClear]="true"
            [dropdown]="true"
            (onSelect)="bajaDefinitivaForm.estudianteId = $event.value.id"
            (onClear)="bajaDefinitivaForm.estudianteId = ''; bajaDefinitivaAlumnoObj = null"
            [style]="{ width: '100%' }" />
        </div>
        <div class="field">
          <label>Motivo *</label>
          <textarea pTextarea [(ngModel)]="bajaDefinitivaForm.motivo" rows="3" style="width:100%"></textarea>
        </div>
        <div class="field">
          <label>Fecha Efectiva *</label>
          <p-datepicker [(ngModel)]="bajaDefinitivaForm.fechaEfectiva" [showIcon]="true" dateFormat="dd/mm/yy" [style]="{width:'100%'}" />
        </div>
        <div class="field">
          <label>Plantel Destino</label>
          <input pInputText [(ngModel)]="bajaDefinitivaForm.plantelDestino" placeholder="Nombre del plantel destino" style="width:100%" />
        </div>
        <div class="field">
          <label>Observaciones</label>
          <textarea pTextarea [(ngModel)]="bajaDefinitivaForm.observaciones" rows="2" style="width:100%"></textarea>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dlgBajaDefinitiva = false" />
        <p-button label="Dar de Baja" icon="pi pi-ban" severity="danger"
          [loading]="guardando()" (onClick)="guardarBajaDefinitiva()" />
      </ng-template>
    </p-dialog>

    <!-- Dialog: Cambio de Grupo -->
    <p-dialog header="Cambio de Grupo" [(visible)]="dlgCambioGrupo"
      [modal]="true" [style]="{width:'480px'}" [draggable]="false">
      <div class="dlg-grid">
        <div class="field">
          <label>Alumno *</label>
          <p-autoComplete
            [(ngModel)]="cambioGrupoAlumnoObj"
            [suggestions]="estudiantesSugg()"
            (completeMethod)="buscarEstudiantes($event)"
            optionLabel="nombre_completo"
            [forceSelection]="true"
            placeholder="Buscar alumno..."
            [showClear]="true"
            [dropdown]="true"
            (onSelect)="cambioGrupoForm.estudianteId = $event.value.id"
            (onClear)="cambioGrupoForm.estudianteId = ''; cambioGrupoAlumnoObj = null"
            [style]="{ width: '100%' }" />
        </div>
        <div class="field">
          <label>Grupo Destino *</label>
          <p-select [options]="gruposLov()" optionLabel="label" optionValue="value"
            [(ngModel)]="cambioGrupoForm.grupoDestinoId" [filter]="true" filterBy="label"
            placeholder="Seleccionar grupo..." [style]="{width:'100%'}" />
        </div>
        <div class="field">
          <label>Motivo</label>
          <textarea pTextarea [(ngModel)]="cambioGrupoForm.motivo" rows="2" style="width:100%"></textarea>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dlgCambioGrupo = false" />
        <p-button label="Confirmar Cambio" icon="pi pi-check" severity="secondary"
          [loading]="guardando()" (onClick)="guardarCambioGrupo()" />
      </ng-template>
    </p-dialog>

    <!-- Dialog: Traslado -->
    <p-dialog header="Registrar Traslado" [(visible)]="dlgTraslado"
      [modal]="true" [style]="{width:'480px'}" [draggable]="false">
      <div class="dlg-grid">
        <div class="field">
          <label>Alumno *</label>
          <p-autoComplete
            [(ngModel)]="trasladoAlumnoObj"
            [suggestions]="estudiantesSugg()"
            (completeMethod)="buscarEstudiantes($event)"
            optionLabel="nombre_completo"
            [forceSelection]="true"
            placeholder="Buscar alumno..."
            [showClear]="true"
            [dropdown]="true"
            (onSelect)="trasladoForm.estudianteId = $event.value.id"
            (onClear)="trasladoForm.estudianteId = ''; trasladoAlumnoObj = null"
            [style]="{ width: '100%' }" />
        </div>
        <div class="field">
          <label>Plantel Destino *</label>
          <input pInputText [(ngModel)]="trasladoForm.plantelDestinoNombre" placeholder="Nombre del plantel destino" style="width:100%" />
        </div>
        <div class="field">
          <label>Motivo *</label>
          <textarea pTextarea [(ngModel)]="trasladoForm.motivo" rows="2" style="width:100%"></textarea>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dlgTraslado = false" />
        <p-button label="Registrar Traslado" icon="pi pi-send" severity="secondary"
          [loading]="guardando()" (onClick)="guardarTraslado()" />
      </ng-template>
    </p-dialog>

    <!-- Dialog: Reactivar -->
    <p-dialog header="Reactivar Alumno" [(visible)]="dlgReactivar"
      [modal]="true" [style]="{width:'440px'}" [draggable]="false">
      <div class="dlg-grid">
        <p class="alumno-label">
          Alumno: <strong>{{ reactivandoBaja?.nombre_alumno }}</strong>
        </p>
        <div class="field">
          <label>Grupo de Reingreso *</label>
          <p-select [options]="gruposLov()" optionLabel="label" optionValue="value"
            [(ngModel)]="reactivarForm.grupoId" [filter]="true" filterBy="label"
            placeholder="Seleccionar grupo..." [style]="{width:'100%'}" />
        </div>
        <div class="field">
          <label>Observaciones</label>
          <textarea pTextarea [(ngModel)]="reactivarForm.observaciones" rows="2" style="width:100%"></textarea>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dlgReactivar = false" />
        <p-button label="Reactivar Alumno" icon="pi pi-user-plus" severity="success"
          [loading]="guardando()" (onClick)="confirmarReactivar()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .apex-page { padding: 1rem; }
    .apex-page-header { margin-bottom: .75rem; }
    .apex-title { margin: 0; font-size: 1.25rem; font-weight: 600; }
    .tab-toolbar { display: flex; align-items: center; justify-content: space-between; padding: .75rem 0; flex-wrap: wrap; gap: .5rem; }
    .tab-actions { display: flex; gap: .5rem; flex-wrap: wrap; }
    .stats-bar { display: flex; gap: .75rem; flex-wrap: wrap; align-items: center; }
    .stat-chip { background: var(--surface-100); border: 1px solid var(--surface-200); padding: .2rem .75rem; border-radius: 12px; font-size: .8rem; }
    .stat-chip.warn { border-color: var(--yellow-400); background: var(--yellow-50); }
    .stat-chip.danger { border-color: var(--red-400); background: var(--red-50); }
    .hint-text { font-size: .8rem; color: var(--text-color-secondary); margin: 0 0 .5rem 0; }
    .dlg-grid { display: flex; flex-direction: column; gap: .75rem; padding: .25rem 0; }
    .field { display: flex; flex-direction: column; gap: .25rem; }
    .field label { font-size: .85rem; font-weight: 500; color: var(--text-color-secondary); }
    .field-error { color: var(--red-600, #dc2626); font-size: .78rem; }
    .alumno-label { margin: 0; font-size: .9rem; }
  `],
})
export class MovilidadComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private readonly api    = inject(ApiService);
  private readonly notify = inject(ApexNotificationService);
  readonly ctx            = inject(ContextService);

  tabActivo       = signal('bajas');
  bajas           = signal<Baja[]>([]);
  cambiosGrupo    = signal<CambioGrupo[]>([]);
  estudiantesSugg = signal<any[]>([]);
  bajaTemporalAlumnoObj: any = null;
  bajaDefinitivaAlumnoObj: any = null;
  cambioGrupoAlumnoObj: any = null;
  trasladoAlumnoObj: any = null;
  grupos          = signal<Grupo[]>([]);
  cargando        = signal(false);
  cargandoCambios = signal(false);
  guardando       = signal(false);

  dlgBajaTemporal  = false;
  dlgBajaDefinitiva = false;
  dlgCambioGrupo   = false;
  dlgTraslado      = false;
  dlgReactivar     = false;
  reactivandoBaja: Baja | null = null;

  /** Indica que el usuario intentó guardar — activa errores inline */
  btIntento = signal(false);

  get reingresoAnteriorAEfectiva(): boolean {
    const ef = this.bajaTemporalForm.fechaEfectiva;
    const re = this.bajaTemporalForm.fechaReingreso;
    return !!ef && !!re && re < ef;
  }



  readonly gruposLov = computed(() =>
    this.grupos().map(g => ({ label: grupoLabel(g as any) || g.nombre_grupo, value: g.id }))
  );

  readonly bajasColumns: ColumnConfig[] = [
    { field: 'nombre_alumno',       header: 'Alumno' },
    { field: 'numero_control',      header: 'N° Control',     width: '110px' },
    { field: 'nombre_grupo',        header: 'Grupo',          width: '90px' },
    { field: 'tipo_baja',           header: 'Tipo',           width: '100px' },
    { field: 'fecha_efectiva_str',  header: 'Fecha Efectiva', width: '130px' },
    { field: 'fecha_reingreso_str', header: 'Reingreso Est.', width: '120px' },
    { field: 'estado_str',          header: 'Estado',         width: '90px' },
  ];

  readonly cambiosColumns: ColumnConfig[] = [
    { field: 'nombre_alumno',    header: 'Alumno' },
    { field: 'numero_control',   header: 'N° Control',    width: '110px' },
    { field: 'grupo_origen',     header: 'Grupo Origen',  width: '130px' },
    { field: 'grupo_destino',    header: 'Grupo Destino', width: '130px' },
    { field: 'fecha_cambio_str', header: 'Fecha',         width: '110px' },
    { field: 'motivo',           header: 'Motivo' },
  ];

  readonly trasladosColumns: ColumnConfig[] = [
    { field: 'nombre_alumno',      header: 'Alumno' },
    { field: 'numero_control',     header: 'N° Control',      width: '110px' },
    { field: 'plantel_destino',    header: 'Plantel Destino' },
    { field: 'fecha_efectiva_str', header: 'Fecha',           width: '110px' },
    { field: 'estado_str',         header: 'Estado',          width: '90px' },
  ];

  readonly bajasFlat = computed(() =>
    this.bajas()
      .filter(b => b.tipo_baja !== 'TRASLADO')
      .map(b => ({
        ...b,
        fecha_efectiva_str:  b.fecha_efectiva  ? new Date(b.fecha_efectiva).toLocaleDateString('es-MX')  : '—',
        fecha_reingreso_str: b.fecha_reingreso ? new Date(b.fecha_reingreso).toLocaleDateString('es-MX') : '—',
        estado_str: b.is_active ? 'Activa' : 'Cerrada',
      }))
  );

  readonly trasladosFlat = computed(() =>
    this.bajas()
      .filter(b => b.tipo_baja === 'TRASLADO')
      .map(b => ({
        ...b,
        fecha_efectiva_str: b.fecha_efectiva ? new Date(b.fecha_efectiva).toLocaleDateString('es-MX') : '—',
        estado_str: b.is_active ? 'Activo' : 'Cerrado',
      }))
  );

  readonly cambiosFlat = computed(() =>
    this.cambiosGrupo().map(c => ({
      ...c,
      fecha_cambio_str: c.fecha_cambio ? new Date(c.fecha_cambio).toLocaleDateString('es-MX') : '—',
    }))
  );

  bajaTemporalForm = {
    estudianteId: '', motivo: '',
    fechaEfectiva: null as Date | null,
    fechaReingreso: null as Date | null,
    observaciones: '',
  };

  bajaDefinitivaForm = {
    estudianteId: '', motivo: '',
    fechaEfectiva: null as Date | null,
    plantelDestino: '', observaciones: '',
  };

  trasladoForm = { estudianteId: '', plantelDestinoNombre: '', motivo: '' };

  cambioGrupoForm = { estudianteId: '', grupoDestinoId: '', motivo: '' };

  reactivarForm = { grupoId: '', observaciones: '' };

  ngOnInit(): void {
    this.cargarCatalogos();
    this.cargar();
    this.cargarCambiosGrupo();
  }

  cargarCatalogos(): void {
    this.api.get<any[]>('/grupos').subscribe({
      next: g => this.grupos.set(g ?? []),
      error: () => {},
    });
  }

  cargar(): void {
    this.cargando.set(true);
    this.api.get<Baja[]>('/movilidad/bajas', { solo_activas: false }).subscribe({
      next: d => { this.bajas.set(Array.isArray(d) ? d : []); this.cargando.set(false); },
      error: () => {
        this.cargando.set(false);
        this.notify.error('Error', 'No se pudieron cargar las bajas');
      },
    });
  }

  cargarCambiosGrupo(): void {
    this.cargandoCambios.set(true);
    this.api.get<CambioGrupo[]>('/movilidad/cambios-grupo').subscribe({
      next: d => { this.cambiosGrupo.set(Array.isArray(d) ? d : []); this.cargandoCambios.set(false); },
      error: () => { this.cargandoCambios.set(false); },
    });
  }

  onTabChange(tab: string | number | undefined): void {
    this.tabActivo.set(String(tab ?? 'bajas'));
  }

  countBajasActivas(): number {
    return this.bajas().filter(b => b.is_active && b.tipo_baja !== 'TRASLADO').length;
  }

  countTipo(tipo: string): number {
    return this.bajas().filter(b => b.tipo_baja === tipo && b.is_active).length;
  }

  abrirBajaTemporal(): void {
    this.bajaTemporalForm = { estudianteId: '', motivo: '', fechaEfectiva: null, fechaReingreso: null, observaciones: '' };
    this.bajaTemporalAlumnoObj = null;
    this.btIntento.set(false);
    this.dlgBajaTemporal = true;
  }

  abrirBajaDefinitiva(): void {
    this.bajaDefinitivaForm = { estudianteId: '', motivo: '', fechaEfectiva: null, plantelDestino: '', observaciones: '' };
    this.bajaDefinitivaAlumnoObj = null;
    this.dlgBajaDefinitiva = true;
  }

  buscarEstudiantes(event: { query: string }): void {
    if (!event.query || event.query.trim().length < 2) {
      this.estudiantesSugg.set([]);
      return;
    }
    this.api.get<any[]>('/portal/buscar', { q: event.query }).subscribe({
      next: (res) => {
        this.estudiantesSugg.set((res ?? []).map((a: any) => ({
          id: a.id,
          matricula: a.matricula,
          nombre_completo: [a.nombre, a.apellido_paterno, a.apellido_materno]
            .filter(Boolean).join(' ') + (a.matricula ? ` — ${a.matricula}` : ''),
        })));
      },
      error: () => {
        this.estudiantesSugg.set([]);
      }
    });
  }

  onBajaSelected(baja: any): void {
    if (baja.tipo_baja === 'TEMPORAL' && baja.is_active) {
      this.reactivandoBaja = baja as Baja;
      this.reactivarForm = { grupoId: '', observaciones: '' };
      this.dlgReactivar = true;
    }
  }

  guardarBajaTemporal(): void {
    this.btIntento.set(true);
    if (!this.bajaTemporalForm.estudianteId || !this.bajaTemporalForm.motivo.trim() || !this.bajaTemporalForm.fechaEfectiva) {
      this.notify.warning('Campos requeridos', 'Alumno, motivo y fecha efectiva son obligatorios');
      return;
    }
    if (this.reingresoAnteriorAEfectiva) {
      this.notify.warning('Fecha inválida', 'La fecha de reingreso debe ser igual o posterior a la fecha efectiva');
      return;
    }
    this.guardando.set(true);
    const body = {
      motivo: this.bajaTemporalForm.motivo,
      fecha_efectiva: this.bajaTemporalForm.fechaEfectiva!.toISOString().split('T')[0],
      fecha_reingreso: this.bajaTemporalForm.fechaReingreso?.toISOString().split('T')[0] ?? null,
      observaciones: this.bajaTemporalForm.observaciones || null,
    };
    this.api.post(`/movilidad/baja-temporal/${this.bajaTemporalForm.estudianteId}`, body).subscribe({
      next: () => {
        this.guardando.set(false);
        this.dlgBajaTemporal = false;
        this.notify.success('Baja temporal registrada', 'El alumno ha sido dado de baja temporalmente');
        this.cargar();
      },
      error: (e: any) => {
        this.guardando.set(false);
        this.notify.error('Error', e?.error?.message ?? e?.error?.detail ?? 'No se pudo registrar la baja');
      },
    });
  }

  guardarBajaDefinitiva(): void {
    if (!this.bajaDefinitivaForm.estudianteId || !this.bajaDefinitivaForm.motivo || !this.bajaDefinitivaForm.fechaEfectiva) {
      this.notify.warning('Campos requeridos', 'Alumno, motivo y fecha efectiva son obligatorios');
      return;
    }
    this.guardando.set(true);
    const body = {
      motivo: this.bajaDefinitivaForm.motivo,
      fecha_efectiva: this.bajaDefinitivaForm.fechaEfectiva!.toISOString().split('T')[0],
      plantel_destino: this.bajaDefinitivaForm.plantelDestino || null,
      observaciones: this.bajaDefinitivaForm.observaciones || null,
    };
    this.api.post(`/movilidad/baja-definitiva/${this.bajaDefinitivaForm.estudianteId}`, body).subscribe({
      next: () => {
        this.guardando.set(false);
        this.dlgBajaDefinitiva = false;
        this.notify.success('Baja definitiva registrada', 'El alumno ha sido dado de baja definitivamente');
        this.cargar();
      },
      error: (e: any) => {
        this.guardando.set(false);
        this.notify.error('Error', e?.error?.message ?? e?.error?.detail ?? 'No se pudo registrar la baja');
      },
    });
  }

  guardarTraslado(): void {
    if (!this.trasladoForm.estudianteId || !this.trasladoForm.plantelDestinoNombre || !this.trasladoForm.motivo) {
      this.notify.warning('Campos requeridos', 'Alumno, plantel destino y motivo son obligatorios');
      return;
    }
    this.guardando.set(true);
    const body = {
      plantel_destino_nombre: this.trasladoForm.plantelDestinoNombre,
      motivo: this.trasladoForm.motivo,
    };
    this.api.post(`/movilidad/traslado/${this.trasladoForm.estudianteId}`, body).subscribe({
      next: () => {
        this.guardando.set(false);
        this.dlgTraslado = false;
        this.notify.success('Traslado registrado', 'El alumno ha sido trasladado exitosamente');
        this.cargar();
      },
      error: (e: any) => {
        this.guardando.set(false);
        this.notify.error('Error', e?.error?.message ?? e?.error?.detail ?? 'No se pudo registrar el traslado');
      },
    });
  }

  guardarCambioGrupo(): void {
    if (!this.cambioGrupoForm.estudianteId || !this.cambioGrupoForm.grupoDestinoId) {
      this.notify.warning('Campos requeridos', 'Alumno y grupo destino son obligatorios');
      return;
    }
    this.guardando.set(true);
    const body = {
      grupo_destino_id: this.cambioGrupoForm.grupoDestinoId,
      motivo: this.cambioGrupoForm.motivo || null,
      ciclo_escolar_id: this.ctx.ciclo()?.id ?? null,
    };
    this.api.post(`/movilidad/cambio-grupo/${this.cambioGrupoForm.estudianteId}`, body).subscribe({
      next: () => {
        this.guardando.set(false);
        this.dlgCambioGrupo = false;
        this.notify.success('Cambio de grupo realizado', 'El alumno ha sido movido al nuevo grupo');
        this.cargarCambiosGrupo();
      },
      error: (e: any) => {
        this.guardando.set(false);
        this.notify.error('Error', e?.error?.message ?? e?.error?.detail ?? 'No se pudo realizar el cambio');
      },
    });
  }

  confirmarReactivar(): void {
    if (!this.reactivarForm.grupoId) {
      this.notify.warning('Grupo requerido', 'Seleccione el grupo de reingreso');
      return;
    }
    if (!this.reactivandoBaja?.estudiante_id) {
      this.notify.error('Error', 'No se identificó al alumno a reactivar');
      return;
    }
    this.guardando.set(true);
    const body = {
      grupo_id: this.reactivarForm.grupoId,
      ciclo_escolar_id: this.ctx.ciclo()?.id ?? null,
      observaciones: this.reactivarForm.observaciones || null,
    };
    this.api.post(`/movilidad/reactivar/${this.reactivandoBaja.estudiante_id}`, body).subscribe({
      next: () => {
        this.guardando.set(false);
        this.dlgReactivar = false;
        this.notify.success('Alumno reactivado', 'El alumno ha sido reincorporado exitosamente');
        this.cargar();
      },
      error: (e: any) => {
        this.guardando.set(false);
        this.notify.error('Error', e?.error?.message ?? e?.error?.detail ?? 'No se pudo reactivar al alumno');
      },
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
