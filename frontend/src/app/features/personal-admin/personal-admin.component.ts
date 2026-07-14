/**
 * Personal no-docente: directivos, coordinadores, secretarías, prefectos,
 * personal de apoyo y personal de salud.
 * Tabs por tipo de personal — Interactive Grid APEX-style.
 *
 * Validación integral (Fase 1-3): FormFieldComponent + InputFormattersService
 */
import { Component, OnDestroy, OnInit, inject, signal, computed, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, FormControl, Validators } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { TabsModule, Tab, TabList, TabPanel, TabPanels } from 'primeng/tabs';
import { TooltipModule } from 'primeng/tooltip';
import { TagModule } from 'primeng/tag';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { InputFormattersService } from '../../shared/services/input-formatters.service';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import { FormFieldComponent } from '../../shared/components/form-field/form-field.component';
import { HelpButtonComponent } from '../../shared/components/help-button/help-button.component';
import { AdesValidators } from '../../shared/validators/ades-validators';
import { ApexNotificationService } from 'apex-component-library';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';

// ── Tipos locales ─────────────────────────────────────────────────────────────

interface EmpleadoRow {
  id: string;
  persona_id?: string;
  plantel_id?: string;
  nombre_plantel?: string;
  nombre: string;
  apellido_paterno: string;
  apellido_materno?: string;
  nombre_completo?: string;
  tipo_rol?: string;
  cedula_profesional?: string;
  especialidad?: string;
  numero_empleado?: string;
  tipo_contrato?: string;
  nivel_estudios?: string;
  turno?: string;
  rfc?: string;
  nss?: string;
  fecha_ingreso_inst?: string;
  telefono?: string;
  email_personal?: string;
  is_active?: boolean;
  genero?: string;
  fecha_nacimiento?: string;
  estado_civil?: string;
  curp?: string;
  pais_nacimiento?: string;
  municipio_nacimiento?: string;
  estado_nacimiento?: string;
  nacionalidad?: string;
  foto_url?: string;
  clabe?: string;
  banco?: string;
  area?: string;
}

interface PersonaForm {
  nombre: string;
  apellido_paterno: string;
  apellido_materno: string;
  curp: string;
  genero: string;
  fecha_nacimiento: string;
  estado_civil: string;
  pais_nacimiento: string;
  nacionalidad: string;
}

interface LaboralesForm {
  tipo_rol: string;
  numero_empleado: string;
  area: string;
  tipo_contrato: string;
  nivel_estudios: string;
  cedula_profesional: string;
  especialidad: string;
  turno: string;
  rfc: string;
  nss: string;
  clabe: string;
  banco: string;
  fecha_ingreso_inst: string;
}

interface FormState {
  persona: PersonaForm;
  laborales: LaboralesForm;
  plantel_id: string;
}

// ── Catálogos ─────────────────────────────────────────────────────────────────

const ROLES_ADMIN = [
  { label: 'Director',                   value: 'DIRECTOR' },
  { label: 'Subdirector',                value: 'SUBDIRECTOR' },
  { label: 'Coordinador Académico',      value: 'COORDINADOR_ACADEMICO' },
  { label: 'Coordinador Administrativo', value: 'COORDINADOR_ADMINISTRATIVO' },
  { label: 'Coordinador de RH',          value: 'COORDINADOR_RH' },
  { label: 'Coordinador de Área',        value: 'COORDINADOR_AREA' },
  { label: 'Secretaría Académica',       value: 'SECRETARIA_ACADEMICA' },
  { label: 'Orientador',                 value: 'ORIENTADOR' },
  { label: 'Tutor',                      value: 'TUTOR' },
  { label: 'Prefecto',                   value: 'PREFECTO' },
  { label: 'Apoyo Administrativo',       value: 'APOYO_ADMINISTRATIVO' },
  { label: 'Apoyo Académico',            value: 'APOYO_ACADEMICO' },
];

const GENEROS   = ['MASCULINO','FEMENINO','NO_BINARIO','PREFIERO_NO_DECIR'].map(v => ({ label: v.replace('_',' '), value: v }));
const CONTRATOS = ['BASE','CONTRATO','EVENTUAL','CONFIANZA'].map(v => ({ label: v, value: v }));
const TURNOS    = ['MATUTINO','VESPERTINO','MIXTO'].map(v => ({ label: v, value: v }));

function emptyPersona(): PersonaForm {
  return { nombre:'', apellido_paterno:'', apellido_materno:'', curp:'',
           genero:'', fecha_nacimiento:'',
           estado_civil:'', pais_nacimiento:'México', nacionalidad:'Mexicana' };
}

function emptyLaborales(): LaboralesForm {
  return { tipo_rol:'', numero_empleado:'', area:'', tipo_contrato:'',
           nivel_estudios:'', cedula_profesional:'', especialidad:'',
           turno:'', rfc:'', nss:'', clabe:'', banco:'', fecha_ingreso_inst:'' };
}

function enrichRows(rows: EmpleadoRow[]): EmpleadoRow[] {
  return rows.map(r => ({
    ...r,
    nombre_completo: `${r.nombre} ${r.apellido_paterno} ${r.apellido_materno ?? ''}`.trim(),
  }));
}

@Component({
  selector: 'app-personal-admin',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule, ReactiveFormsModule,
    ButtonModule, InputTextModule, DialogModule, SelectModule,
    TabsModule, Tab, TabList, TabPanel, TabPanels,
    TooltipModule, TagModule,
    InteractiveGridComponent, FormFieldComponent, HelpButtonComponent,
  ],
  template: `
    <!-- ── Diálogo Alta ─────────────────────────────────────────────────── -->
    <p-dialog
      [visible]="showDialog()"
      (visibleChange)="showDialog.set($event)"
      [header]="modoSalud() ? 'Nuevo Personal de Salud' : 'Nuevo Personal Administrativo'"
      [modal]="true" [style]="{width:'560px'}" [draggable]="false">

      <div class="dlg-grid">
        <div class="dlg-section-title">Datos personales</div>
        <div class="dlg-row">
          <div>
            <app-form-field
              [control]="fcNombre"
              label="Nombre(s)"
              placeholder="Ej: Juan Carlos"
              [maxLength]="100"
              [required]="true"
              [formatter]="inputFormatters.formatNombre.bind(inputFormatters)"
              helpText="Solo letras, espacios y guiones"
            />
          </div>
          <div>
            <app-form-field
              [control]="fcApellidoPaterno"
              label="Apellido paterno"
              placeholder="Ej: García"
              [maxLength]="100"
              [required]="true"
              [formatter]="inputFormatters.formatNombre.bind(inputFormatters)"
              helpText="Solo letras, espacios y guiones"
            />
          </div>
        </div>
        <div class="dlg-row">
          <div>
            <app-form-field
              [control]="fcApellidoMaterno"
              label="Apellido materno"
              placeholder="Ej: López"
              [maxLength]="100"
              [formatter]="inputFormatters.formatNombre.bind(inputFormatters)"
              helpText="Opcional"
            />
          </div>
          <div>
            <app-form-field
              [control]="fcCURP"
              label="CURP"
              placeholder="Ej: AAAA999999HAAAAA01"
              [maxLength]="18"
              [formatter]="inputFormatters.formatCURP.bind(inputFormatters)"
              helpText="18 caracteres, se auto-completa a mayúsculas"
            />
          </div>
        </div>
        <div class="dlg-row">
          <div><label class="dlg-lbl">Género</label>
            <p-select [options]="generoOpts" [formControl]="fcGenero"
              optionLabel="label" optionValue="value" style="width:100%" [showClear]="true" placeholder="Género…" /></div>
          <div><label class="dlg-lbl">Fecha de nacimiento</label>
            <input pInputText [formControl]="fcFechaNacimiento" type="date" style="width:100%" /></div>
        </div>
        <p style="font-size:.78rem;color:var(--text-secondary);margin:0 0 .5rem">
          Teléfono y email se agregan desde el perfil en la sección <em>Domicilio y Contactos</em>.
        </p>

        <div class="dlg-section-title">Datos laborales</div>
        @if (!modoSalud()) {
          <div class="dlg-row">
            <div><label class="dlg-lbl">Tipo de rol *</label>
              <p-select [options]="rolesOpts" [formControl]="fcTipoRol"
                optionLabel="label" optionValue="value" style="width:100%" placeholder="Seleccionar rol…" /></div>
            <div>
              <app-form-field
                [control]="fcArea"
                label="Área / Departamento"
                placeholder="Ej: Recursos Humanos"
                [maxLength]="100"
                [formatter]="inputFormatters.formatNombre.bind(inputFormatters)"
              />
            </div>
          </div>
        }
        <div class="dlg-row">
          <div>
            <app-form-field
              [control]="fcNumeroEmpleado"
              label="Número de empleado"
              placeholder="Ej: EMP-001"
              [maxLength]="50"
            />
          </div>
          <div>
            <app-form-field
              [control]="fcCedulaProfesional"
              label="Cédula profesional"
              placeholder="Ej: 12345678"
              [maxLength]="50"
            />
          </div>
        </div>
        <div class="dlg-row">
          <div>
            <app-form-field
              [control]="fcEspecialidad"
              label="Especialidad"
              placeholder="Ej: Pedagogía"
              [maxLength]="100"
              [formatter]="inputFormatters.formatNombre.bind(inputFormatters)"
            />
          </div>
          <div><label class="dlg-lbl">Tipo contrato</label>
            <p-select [options]="contratoOpts" [formControl]="fcTipoContrato"
              optionLabel="label" optionValue="value" style="width:100%" [showClear]="true" placeholder="Tipo…" /></div>
        </div>
        <div class="dlg-row">
          <div><label class="dlg-lbl">Turno</label>
            <p-select [options]="turnoOpts" [formControl]="fcTurno"
              optionLabel="label" optionValue="value" style="width:100%" [showClear]="true" placeholder="Turno…" /></div>
          <div><label class="dlg-lbl">Fecha de ingreso</label>
            <input pInputText [formControl]="fcFechaIngreso" type="date" style="width:100%" /></div>
        </div>
        <div class="dlg-row">
          <div>
            <app-form-field
              [control]="fcRFC"
              label="RFC"
              placeholder="Ej: AAAA999999AAA"
              [maxLength]="13"
              [formatter]="inputFormatters.formatRFC.bind(inputFormatters)"
              helpText="12-13 caracteres, se auto-completa a mayúsculas"
            />
          </div>
          <div>
            <app-form-field
              [control]="fcNSS"
              label="NSS (IMSS)"
              placeholder="Ej: 12345678901"
              [maxLength]="11"
              [formatter]="inputFormatters.formatNumeric.bind(inputFormatters, 11)"
              helpText="11 dígitos"
            />
          </div>
        </div>
      </div>

      <ng-template pTemplate="footer">
        <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="showDialog.set(false)" />
        <p-button label="Guardar" icon="pi pi-check" [loading]="guardando()" (onClick)="guardar()" />
      </ng-template>
    </p-dialog>

    <!-- ── Encabezado ──────────────────────────────────────────────────── -->
    <div class="page-header">
      <div>
        <h2>Personal No-Docente</h2>
        <p class="subtitle">Directivos, coordinadores, secretarías, prefectos y personal de salud</p>
      </div>
      <div style="display:flex;gap:0.5rem;align-items:center">
        <app-help-button modulo="personal-admin" />
        <p-button label="Nuevo registro" icon="pi pi-plus" (onClick)="openDialog()" />
      </div>
    </div>

    <!-- ── Tabs por tipo ───────────────────────────────────────────────── -->
    <p-tabs [value]="tabActivo()" (valueChange)="onTabChange($event)">
      <p-tablist>
        <p-tab value="directivos"><i class="pi pi-star"></i> Directivos</p-tab>
        <p-tab value="coordinadores"><i class="pi pi-sitemap"></i> Coordinadores</p-tab>
        <p-tab value="operativo"><i class="pi pi-users"></i> Secretarías / Apoyo</p-tab>
        <p-tab value="salud"><i class="pi pi-heart"></i> Personal de Salud</p-tab>
      </p-tablist>

      <p-tabpanels>
        <p-tabpanel value="directivos">
          <app-interactive-grid [data]="directivos()" [columns]="columnasAdmin"
            [loading]="loading()" (rowSelected)="abrirPerfil($event)" />
        </p-tabpanel>
        <p-tabpanel value="coordinadores">
          <app-interactive-grid [data]="coordinadores()" [columns]="columnasAdmin"
            [loading]="loading()" (rowSelected)="abrirPerfil($event)" />
        </p-tabpanel>
        <p-tabpanel value="operativo">
          <app-interactive-grid [data]="operativo()" [columns]="columnasAdmin"
            [loading]="loading()" (rowSelected)="abrirPerfil($event)" />
        </p-tabpanel>
        <p-tabpanel value="salud">
          <app-interactive-grid [data]="personalSalud()" [columns]="columnasSalud"
            [loading]="loadingSalud()" (rowSelected)="abrirPerfilSalud($event)" />
        </p-tabpanel>
      </p-tabpanels>
    </p-tabs>

    <!-- ── Diálogo perfil ─────────────────────────────────────────────── -->
    <p-dialog
      [visible]="perfilVisible()"
      (visibleChange)="perfilVisible.set($event)"
      [header]="perfilHeader()"
      [modal]="true" [style]="{width:'700px'}" [draggable]="false">

      @if (seleccionado(); as sel) {
        <div class="perfil-grid">
          <div class="perfil-row"><span class="perfil-lbl">Nombre completo</span>
            <span>{{ sel.nombre }} {{ sel.apellido_paterno }} {{ sel.apellido_materno }}</span></div>
          <div class="perfil-row"><span class="perfil-lbl">CURP</span><span>{{ sel.curp || '—' }}</span></div>
          <div class="perfil-row"><span class="perfil-lbl">Plantel</span><span>{{ sel.nombre_plantel || '—' }}</span></div>
          @if (!modoSalud()) {
            <div class="perfil-row"><span class="perfil-lbl">Tipo de rol</span>
              <p-tag [value]="rolLabel(sel.tipo_rol)" severity="info" /></div>
            <div class="perfil-row"><span class="perfil-lbl">Área</span><span>{{ sel.area || '—' }}</span></div>
          }
          <div class="perfil-row"><span class="perfil-lbl">Cédula profesional</span><span>{{ sel.cedula_profesional || '—' }}</span></div>
          <div class="perfil-row"><span class="perfil-lbl">Especialidad</span><span>{{ sel.especialidad || '—' }}</span></div>
          <div class="perfil-row"><span class="perfil-lbl">Número empleado</span><span>{{ sel.numero_empleado || '—' }}</span></div>
          <div class="perfil-row"><span class="perfil-lbl">Tipo contrato</span><span>{{ sel.tipo_contrato || '—' }}</span></div>
          <div class="perfil-row"><span class="perfil-lbl">Turno</span><span>{{ sel.turno || '—' }}</span></div>
          <div class="perfil-row"><span class="perfil-lbl">Fecha ingreso</span>
            <span>{{ sel.fecha_ingreso_inst ? (sel.fecha_ingreso_inst | date:'dd/MM/yyyy') : '—' }}</span></div>

          <div class="perfil-row"><span class="perfil-lbl">RFC</span><span>{{ sel.rfc || '—' }}</span></div>
          <div class="perfil-row"><span class="perfil-lbl">NSS</span><span>{{ sel.nss || '—' }}</span></div>
        </div>
      }

      <ng-template pTemplate="footer">
        <p-button label="Dar de baja" icon="pi pi-trash" severity="danger" [text]="true"
          (onClick)="darDeBaja()" [loading]="guardando()" pTooltip="Soft delete — mantiene historial" />
        <p-button label="Cerrar" severity="secondary" [text]="true" (onClick)="perfilVisible.set(false)" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .page-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:1.25rem; }
    .page-header h2 { margin:0; font-size:1.4rem; font-weight:600; }
    .subtitle { margin:.25rem 0 0; color:#64748b; font-size:.875rem; }
    .dlg-grid { display:flex; flex-direction:column; gap:.75rem; padding:.25rem 0; }
    .dlg-section-title { font-weight:600; font-size:.8rem; text-transform:uppercase; color:#64748b;
      letter-spacing:.05em; padding:.25rem 0 .1rem; border-bottom:1px solid #e2e8f0; margin-top:.25rem; }
    .dlg-row { display:grid; grid-template-columns:1fr 1fr; gap:.75rem; }
    .dlg-lbl { display:block; font-size:.78rem; font-weight:500; color:#475569; margin-bottom:.25rem; }
    .perfil-grid { display:flex; flex-direction:column; gap:.6rem; }
    .perfil-row { display:flex; gap:.75rem; align-items:center;
      padding:.5rem .25rem; border-bottom:1px solid #f1f5f9; }
    .perfil-lbl { min-width:160px; font-size:.8rem; font-weight:500; color:#64748b; }
  `],
})
export class PersonalAdminComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();

  private api   = inject(ApiService);
  private ctx   = inject(ContextService);
  private fb    = inject(FormBuilder);
  private readonly notify = inject(ApexNotificationService);
  readonly inputFormatters = inject(InputFormattersService);

  tabActivo     = signal<string>('directivos');
  loading       = signal(false);
  loadingSalud  = signal(false);
  guardando     = signal(false);
  showDialog    = signal(false);
  perfilVisible = signal(false);

  allAdmin      = signal<EmpleadoRow[]>([]);
  personalSalud = signal<EmpleadoRow[]>([]);
  seleccionado  = signal<EmpleadoRow | null>(null);

  // FormControls para validación integral
  fcNombre = new FormControl('', [Validators.required, Validators.maxLength(100)]);
  fcApellidoPaterno = new FormControl('', [Validators.required, Validators.maxLength(100)]);
  fcApellidoMaterno = new FormControl('', [Validators.maxLength(100)]);
  fcCURP = new FormControl('', [AdesValidators.isCURP()]);
  fcGenero = new FormControl('');
  fcFechaNacimiento = new FormControl('');
  fcEstadoCivil = new FormControl('');
  fcPaisNacimiento = new FormControl('México');
  fcNacionalidad = new FormControl('Mexicana');

  fcTipoRol = new FormControl('', [Validators.required]);
  fcNumeroEmpleado = new FormControl('', [Validators.maxLength(50)]);
  fcArea = new FormControl('', [Validators.maxLength(100)]);
  fcTipoContrato = new FormControl('');
  fcNivelEstudios = new FormControl('');
  fcCedulaProfesional = new FormControl('', [Validators.maxLength(50)]);
  fcEspecialidad = new FormControl('', [Validators.maxLength(100)]);
  fcTurno = new FormControl('');
  fcRFC = new FormControl('', [AdesValidators.isRFC()]);
  fcNSS = new FormControl('', [Validators.maxLength(11), AdesValidators.isNumeric()]);
  fcFechaIngreso = new FormControl('');

  formPersona:   PersonaForm   = emptyPersona();
  formLaborales: LaboralesForm = emptyLaborales();
  formPlantelId  = '';

  modoSalud = computed(() => this.tabActivo() === 'salud');

  directivos    = computed(() => this.allAdmin().filter(r =>
    ['DIRECTOR','SUBDIRECTOR'].includes(r.tipo_rol ?? '')));
  coordinadores = computed(() => this.allAdmin().filter(r =>
    (r.tipo_rol ?? '').startsWith('COORDINADOR')));
  operativo     = computed(() => this.allAdmin().filter(r =>
    ['SECRETARIA_ACADEMICA','ORIENTADOR','TUTOR','PREFECTO',
     'APOYO_ADMINISTRATIVO','APOYO_ACADEMICO'].includes(r.tipo_rol ?? '')));

  perfilHeader = computed(() => {
    const s = this.seleccionado();
    if (!s) return 'Perfil';
    return `${s.nombre} ${s.apellido_paterno} — ${this.rolLabel(s.tipo_rol)}`;
  });

  generoOpts   = GENEROS;
  rolesOpts    = ROLES_ADMIN;
  contratoOpts = CONTRATOS;
  turnoOpts    = TURNOS;

  columnasAdmin: ColumnConfig[] = [
    { field: 'nombre_completo',  header: 'Nombre',         sortable: true },
    { field: 'tipo_rol',         header: 'Rol',            sortable: true },
    { field: 'area',             header: 'Área',           sortable: true },
    { field: 'nombre_plantel',   header: 'Plantel',        sortable: true },
    { field: 'numero_empleado',  header: 'Núm. Empleado',  sortable: true },
    { field: 'tipo_contrato',    header: 'Contrato',       sortable: true },
    { field: 'turno',            header: 'Turno',          sortable: true },
  ];

  columnasSalud: ColumnConfig[] = [
    { field: 'nombre_completo',    header: 'Nombre',         sortable: true },
    { field: 'cedula_profesional', header: 'Cédula Prof.',   sortable: true },
    { field: 'especialidad',       header: 'Especialidad',   sortable: true },
    { field: 'nombre_plantel',     header: 'Plantel',        sortable: true },
    { field: 'numero_empleado',    header: 'Núm. Empleado',  sortable: true },
    { field: 'tipo_contrato',      header: 'Contrato',       sortable: true },
    { field: 'turno',              header: 'Turno',          sortable: true },
  ];

  ngOnInit(): void {
    this.cargarAdmin();
    this.cargarSalud();
  }

  cargarAdmin(): void {
    this.loading.set(true);
    const plantelId = this.ctx.plantel()?.id;
    const params: Record<string, string> = {};
    if (plantelId) params['plantel_id'] = plantelId;
    this.api.get<{ data: EmpleadoRow[] }>('/personal-admin', params).pipe(takeUntil(this.destroy$)).subscribe({
      next: r => { this.allAdmin.set(enrichRows(r.data ?? [])); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  cargarSalud(): void {
    this.loadingSalud.set(true);
    const plantelId = this.ctx.plantel()?.id;
    const params: Record<string, string> = {};
    if (plantelId) params['plantel_id'] = plantelId;
    this.api.get<{ data: EmpleadoRow[] }>('/personal-salud', params).pipe(takeUntil(this.destroy$)).subscribe({
      next: r => { this.personalSalud.set(enrichRows(r.data ?? [])); this.loadingSalud.set(false); },
      error: () => this.loadingSalud.set(false),
    });
  }

  onTabChange(tab: string | number | undefined): void {
    this.tabActivo.set(String(tab ?? 'directivos'));
  }

  openDialog(): void {
    this.resetFormControls();
    this.formPlantelId = this.ctx.plantel()?.id ?? '';
    this.showDialog.set(true);
  }

  guardar(): void {
    // Validar campos requeridos
    if (this.fcNombre.invalid || this.fcApellidoPaterno.invalid) {
      this.notify.warning('Faltan datos', 'Nombre y apellido paterno son requeridos y válidos');
      return;
    }
    if (!this.modoSalud() && this.fcTipoRol.invalid) {
      this.notify.warning('Faltan datos', 'El tipo de rol es requerido');
      return;
    }

    // Validar que CURP y RFC sean válidos si se proporcionan
    if (this.fcCURP.value && this.fcCURP.invalid) {
      this.notify.warning('Datos inválidos', 'CURP no válida');
      return;
    }
    if (this.fcRFC.value && this.fcRFC.invalid) {
      this.notify.warning('Datos inválidos', 'RFC no válido');
      return;
    }

    this.guardando.set(true);
    const endpoint = this.modoSalud() ? '/personal-salud' : '/personal-admin';

    const payload: FormState = {
      plantel_id: this.formPlantelId,
      persona: {
        nombre: this.fcNombre.value || '',
        apellido_paterno: this.fcApellidoPaterno.value || '',
        apellido_materno: this.fcApellidoMaterno.value || '',
        curp: this.fcCURP.value || '',
        genero: this.fcGenero.value || '',
        fecha_nacimiento: this.fcFechaNacimiento.value || '',
        estado_civil: this.fcEstadoCivil.value || '',
        pais_nacimiento: this.fcPaisNacimiento.value || 'México',
        nacionalidad: this.fcNacionalidad.value || 'Mexicana',
      },
      laborales: {
        tipo_rol: this.fcTipoRol.value || '',
        numero_empleado: this.fcNumeroEmpleado.value || '',
        area: this.fcArea.value || '',
        tipo_contrato: this.fcTipoContrato.value || '',
        nivel_estudios: this.fcNivelEstudios.value || '',
        cedula_profesional: this.fcCedulaProfesional.value || '',
        especialidad: this.fcEspecialidad.value || '',
        turno: this.fcTurno.value || '',
        rfc: this.fcRFC.value || '',
        nss: this.fcNSS.value || '',
        clabe: '',
        banco: '',
        fecha_ingreso_inst: this.fcFechaIngreso.value || '',
      },
    };

    this.api.post<EmpleadoRow>(endpoint, payload).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.notify.success('Guardado', 'Registro creado correctamente');
        this.showDialog.set(false);
        this.guardando.set(false);
        this.resetFormControls();
        if (this.modoSalud()) this.cargarSalud(); else this.cargarAdmin();
      },
      error: (e: any) => {
        this.notify.error('Error', e?.error?.message ?? 'No se pudo guardar');
        this.guardando.set(false);
      },
    });
  }

  private resetFormControls(): void {
    this.fcNombre.reset();
    this.fcApellidoPaterno.reset();
    this.fcApellidoMaterno.reset();
    this.fcCURP.reset();
    this.fcGenero.reset();
    this.fcFechaNacimiento.reset();
    this.fcTipoRol.reset();
    this.fcNumeroEmpleado.reset();
    this.fcArea.reset();
    this.fcTipoContrato.reset();
    this.fcCedulaProfesional.reset();
    this.fcEspecialidad.reset();
    this.fcTurno.reset();
    this.fcRFC.reset();
    this.fcNSS.reset();
    this.fcFechaIngreso.reset();
  }

  abrirPerfil(row: EmpleadoRow): void {
    this.seleccionado.set(row);
    this.perfilVisible.set(true);
  }

  abrirPerfilSalud(row: EmpleadoRow): void {
    this.seleccionado.set(row);
    this.perfilVisible.set(true);
  }

  darDeBaja(): void {
    const sel = this.seleccionado();
    if (!sel) return;
    const endpoint = this.modoSalud() ? `/personal-salud/${sel.id}` : `/personal-admin/${sel.id}`;
    this.guardando.set(true);
    this.api.delete(endpoint).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.notify.success('Dado de baja', 'Registro desactivado (soft delete)');
        this.perfilVisible.set(false);
        this.guardando.set(false);
        if (this.modoSalud()) this.cargarSalud(); else this.cargarAdmin();
      },
      error: (e: any) => {
        this.notify.error('Error', e?.error?.message ?? 'No se pudo dar de baja');
        this.guardando.set(false);
      },
    });
  }

  rolLabel(rol?: string): string {
    return ROLES_ADMIN.find(r => r.value === rol)?.label ?? rol ?? '—';
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
