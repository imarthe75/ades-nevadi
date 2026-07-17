/**
 * FASE 1 & FASE 24 — Alumnos (Students) + Interactive Grid APEX-style
 * Lists, filters, sorts, and manages student records with optimistic locking.
 * PUNTO 6: Implementa OnDestroy con destroy$ para cleanup de subscriptions ✅
 */
import { Component, OnInit, OnDestroy, inject, signal, computed, effect, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormControl, FormGroup, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { ToastModule } from 'primeng/toast';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { throttleTime, Subject, takeUntil } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ContextCatalogService } from '../../core/services/context-catalog.service';
import { ExportService } from '../../core/services/export.service';
import { InputFormattersService } from '../../shared/services/input-formatters.service';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import { ImportButtonComponent } from '../../shared/components/import-button/import-button.component';
import { AlumnoPerfilComponent } from '../../shared/components/alumno-perfil/alumno-perfil.component';
import { HelpButtonComponent } from '../../shared/components/help-button/help-button.component';
import { FormFieldComponent } from '../../shared/components/form-field/form-field.component';
import { Estudiante } from '../../core/models';
import { ApexNotificationService, ApexSearchComponent, ApexModalDialogComponent, ApexValidators } from 'apex-component-library';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';

@Component({
  selector: 'app-alumnos',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule, ReactiveFormsModule,
    ButtonModule, InputTextModule, ToastModule, SelectModule, MultiSelectModule,
    InteractiveGridComponent, ImportButtonComponent, AlumnoPerfilComponent, HelpButtonComponent,
    FormFieldComponent,
    ApexSearchComponent, ApexModalDialogComponent,
  ],
  template: `
    <!-- Toast notifications -->
    <p-toast />

    <!-- Drawer de perfil completo -->
    <app-alumno-perfil
      [visible]="perfilVisible()"
      (visibleChange)="perfilVisible.set($event)"
      [alumno]="alumnoSeleccionado()"
      (saved)="onPerfilGuardado()"
    />

    <div class="page-header">
      <div>
        <h2>Alumnos</h2>
        <p class="subtitle">{{ totalAlumnos() | number }} alumno(s) registrado(s)</p>
      </div>
      <div style="display:flex;gap:0.5rem;align-items:center">
        <app-help-button modulo="alumnos" />
        <p-button label="CSV"   icon="pi pi-file"       severity="secondary" [text]="true" (onClick)="exportCSV()"  pTooltip="Exportar CSV" />
        <p-button label="Excel" icon="pi pi-file-excel" severity="secondary" [text]="true" (onClick)="exportXLSX()" pTooltip="Exportar Excel" />
        <app-import-button entidad="alumnos" [onSuccess]="recargar" />
        <p-button label="Asignar grupo" icon="pi pi-users" severity="secondary" [text]="true" (onClick)="abrirAsignacionMasiva()" pTooltip="Asignación masiva de grupo" />
        <p-button label="Nuevo alumno" icon="pi pi-plus" (onClick)="openDialog()" />
      </div>
    </div>

    <!-- Búsqueda rápida -->
    <div style="display:flex; gap:0.75rem; align-items:center; margin-bottom:1rem; flex-wrap:wrap">
      <div style="flex:1; min-width:250px">
        <apex-search
          placeholder="Buscar alumno..."
          [debounce]="300"
          (valueChange)="busqueda.set($event)"
        />
      </div>
    </div>

    <!-- Interactive Grid APEX-style — la cascada plantel→nivel→grado→grupo vive en el
         topbar (ContextService). El grid la refleja y la escribe de vuelta vía
         filterChange (lazo bilateral). -->
    <app-interactive-grid
      [data]="alumnosFiltrados()"
      [columns]="columnas"
      [loading]="loadingTabla()"
      [externalFilters]="catalog.contextFilters()"
      [externalSuggestions]="catalog.contextSuggestions()"
      [serverFilteredFields]="cascadeFields"
      (filterChange)="catalog.applyGridFilter($event.field, $event.value)"
      (rowSelected)="abrirPerfil($event)"
    />

    <!-- Diálogo de alta rápida -->
    <apex-modal-dialog
      [visible]="showDialog()"
      (visibleChange)="showDialog.set($event)"
      title="Nuevo Alumno"
      size="sm">
      <div [formGroup]="crearAlumnoForm" style="display:flex;flex-direction:column;gap:0.5rem">
        <app-form-field
          [control]="getNombreControl()"
          label="Nombre(s)"
          placeholder="Ej: Juan Carlos"
          [maxLength]="100"
          helpText="Nombre completo del alumno (se permiten hasta 100 caracteres)"
          [required]="true"
          [formatter]="inputFormatters.formatNombre.bind(inputFormatters)"
        />

        <app-form-field
          [control]="getApellidoPaternoControl()"
          label="Apellido paterno"
          placeholder="Ej: García"
          [maxLength]="100"
          helpText="Primer apellido del alumno"
          [required]="true"
          [formatter]="inputFormatters.formatNombre.bind(inputFormatters)"
        />

        <app-form-field
          [control]="getApellidoMaternoControl()"
          label="Apellido materno"
          placeholder="Ej: López"
          [maxLength]="100"
          helpText="Segundo apellido (opcional)"
          [formatter]="inputFormatters.formatNombre.bind(inputFormatters)"
        />

        <app-form-field
          [control]="getCurpControl()"
          label="CURP"
          placeholder="AAAA999999HAAAAA01"
          type="text"
          [maxLength]="18"
          helpText="18 caracteres: 4 letras + 6 dígitos fecha + 1 sexo (H/M/X) + 5 letras + 1 letra/dígito + 1 dígito verificador"
          [required]="true"
          [formatter]="inputFormatters.formatCURP.bind(inputFormatters)"
        />

        <p class="dlg-note">Una vez creado podrás completar el expediente completo desde el perfil.</p>
      </div>
      <ng-template #footer>
        <p-button
          label="Crear alumno"
          (onClick)="crearAlumno()"
          [loading]="loading()"
          [disabled]="loading()"
        />
      </ng-template>
    </apex-modal-dialog>

    <!-- Diálogo de asignación masiva de grupo -->
    <apex-modal-dialog
      [visible]="dlgAsignacionMasiva()"
      (visibleChange)="dlgAsignacionMasiva.set($event)"
      title="Asignación masiva de grupo"
      size="md">
      <div style="display:flex;flex-direction:column;gap:1rem">
        <div>
          <label class="dlg-lbl">Alumnos a mover *</label>
          <p-multiselect
            [options]="opcionesAlumnosMasivo()"
            [(ngModel)]="masivoSeleccionIds"
            optionLabel="label"
            optionValue="value"
            display="chip"
            filter="true"
            placeholder="Selecciona uno o más alumnos"
            style="width:100%" ariaLabel="Alumnos a mover"/>
        </div>
        <div>
          <label class="dlg-lbl">Grupo destino *</label>
          <p-select
            [options]="opcionesGrupoDestino()"
            [(ngModel)]="masivoForm.grupoDestinoId"
            optionLabel="label"
            optionValue="value"
            placeholder="Selecciona el grupo destino"
            style="width:100%" ariaLabel="Grupo destino"/>
        </div>
        <div>
          <label class="dlg-lbl" for="alu-masivo-motivo">Motivo *</label>
          <input pInputText id="alu-masivo-motivo" [(ngModel)]="masivoForm.motivo" style="width:100%" placeholder="Ej. Reorganización de grupos"/>
        </div>
        <p class="dlg-note">Cada alumno se registra individualmente en el historial de movilidad; si alguno falla, los demás se procesan igual.</p>
      </div>
      <ng-template #footer>
        <p-button label="Asignar" (onClick)="confirmarAsignacionMasiva()" [loading]="asignandoMasivo()" [disabled]="asignandoMasivo()" />
      </ng-template>
    </apex-modal-dialog>
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1rem; }
    .subtitle { font-size: 0.82rem; color: var(--text-color-secondary); margin: 0; }
    .dlg-lbl { display: block; font-size: .85rem; margin-bottom: .25rem; color: var(--text-color-secondary); }
    .dlg-note { font-size: .78rem; color: var(--text-color-secondary); margin: 0; }
  `],
})
export class AlumnosComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private readonly api = inject(ApiService);
  private readonly ctx = inject(ContextService);
  readonly catalog = inject(ContextCatalogService);
  private readonly notify = inject(ApexNotificationService);
  private readonly exp = inject(ExportService);
  readonly inputFormatters = inject(InputFormattersService);
  private readonly crearAlumnoSubject = new Subject<void>();

  alumnos = signal<Estudiante[]>([]);
  alumnosDatos = signal<any[]>([]);
  totalAlumnos = signal(0);
  busqueda = signal('');

  /** Columnas gobernadas por el contexto global (filtrado server-side + lazo bilateral). */
  readonly cascadeFields = ['plantel', 'nivel', 'grado', 'grupo'];

  readonly alumnosFiltrados = computed(() => {
    const q = this.busqueda().toLowerCase();
    if (!q) return this.alumnosDatos();
    return this.alumnosDatos().filter(a =>
      (a.nombre_completo ?? a.nombre ?? '').toLowerCase().includes(q) ||
      (a.matricula ?? '').toLowerCase().includes(q)
    );
  });

  alumnoSeleccionado = signal<Estudiante | null>(null);
  perfilVisible = signal(false);
  showDialog = signal(false);
  loading = signal(false);
  loadingTabla = signal(false);

  // Formulario reactivo para crear alumno con validadores
  crearAlumnoForm = new FormGroup({
    nombre: new FormControl('', [
      Validators.required,
      ApexValidators.isNotNull(),
      ApexValidators.maxChars(100),
    ]),
    apellido_paterno: new FormControl('', [
      Validators.required,
      ApexValidators.isNotNull(),
      ApexValidators.maxChars(100),
    ]),
    apellido_materno: new FormControl('', [
      ApexValidators.maxChars(100),
    ]),
    curp: new FormControl('', [
      Validators.required,
      ApexValidators.exactLength(18),
      ApexValidators.isCURP(),
    ]),
  });

  // ── Asignación masiva de grupo (PE-009/010) ─────────────────────────────
  dlgAsignacionMasiva = signal(false);
  asignandoMasivo = signal(false);
  masivoSeleccionIds: string[] = [];
  masivoForm: { grupoDestinoId: string | null; motivo: string } = { grupoDestinoId: null, motivo: '' };

  readonly opcionesAlumnosMasivo = computed(() =>
    this.alumnosFiltrados().map(a => ({
      label: `${a.nombre_completo} — ${a.matricula} (${a.grupo ?? '—'})`,
      value: a.id,
    }))
  );

  readonly opcionesGrupoDestino = computed(() =>
    this.catalog.grupos()
      .filter(g => g.id)
      .map(g => ({ label: g.nombre_grupo, value: g.id }))
  );

  readonly recargar = () => this.cargarAlumnos();

  // Spec: spec/modules/fase-24-interactive-grid/specification.md § Pre-configured Schemas
  columnas: ColumnConfig[] = [
    { field: 'matricula', header: 'Matrícula', sortable: true, filterable: true, width: '120px' },
    { field: 'nombre_completo', header: 'Nombre Completo', sortable: true, filterable: true, width: '200px' },
    { field: 'curp', header: 'CURP', sortable: true, filterable: true, width: '170px' },
    { field: 'nss', header: 'NSS', sortable: false, filterable: false, width: '80px' },
    { field: 'plantel', header: 'Plantel', sortable: true, filterable: true, width: '140px' },
    { field: 'nivel', header: 'Nivel', sortable: true, filterable: true, width: '120px' },
    { field: 'grado', header: 'Grado', sortable: true, filterable: true, width: '80px' },
    { field: 'grupo', header: 'Grupo', sortable: true, filterable: true, width: '80px' },
    { field: 'fecha_ingreso', header: 'Ingreso', sortable: true, filterable: false, width: '110px' },
  ];

  private readonly exportCols = [
    { field: 'matricula', header: 'Matrícula' },
    { field: 'nombre_completo', header: 'Nombre Completo' },
    { field: 'curp', header: 'CURP' },
    { field: 'rfc', header: 'RFC' },
    { field: 'genero', header: 'Género' },
    { field: 'fecha_nacimiento', header: 'Fecha Nacimiento' },
    { field: 'telefono', header: 'Teléfono' },
    { field: 'email_personal', header: 'Email' },
    { field: 'nacionalidad', header: 'Nacionalidad' },
    { field: 'plantel', header: 'Plantel' },
    { field: 'nivel', header: 'Nivel' },
    { field: 'grado', header: 'Grado' },
    { field: 'grupo', header: 'Grupo' },
    { field: 'fecha_ingreso', header: 'Fecha Ingreso' },
    { field: 'nss', header: 'NSS' },
    { field: 'tipo_alumno', header: 'Tipo Alumno' },
    { field: 'escuela_procedencia', header: 'Escuela Procedencia' },
    { field: 'promedio_procedencia', header: 'Promedio Procedencia' },
    { field: 'beca_tipo', header: 'Beca' },
    { field: 'folio_sep', header: 'Folio SEP' },
  ];

  constructor() {
    // Recargar cuando cambia la cascada del contexto global (topbar o lazo del grid).
    effect(() => {
      // Suscripción explícita a las 4 dimensiones de la cascada.
      this.ctx.plantel(); this.ctx.nivel(); this.ctx.grado(); this.ctx.grupo();
      this.cargarAlumnos();
    });
  }

  ngOnInit(): void {
    // PUNTO 6: Throttle submissions con takeUntil(destroy$) ✅
    this.crearAlumnoSubject.pipe(
      throttleTime(500, undefined, { leading: true, trailing: false }),
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.performCrearAlumno();
    });

    this.cargarAlumnos();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  cargarAlumnos(): void {
    const params: Record<string, any> = {
      pagina: 1,
      por_pagina: 500,
    };
    const plantel = this.ctx.plantel(); if (plantel?.id) params['plantel_id'] = plantel.id;
    const nivel = this.ctx.nivel();     if (nivel?.id)   params['nivel_id']   = nivel.id;
    const grado = this.ctx.grado();     if (grado?.id)   params['grado_id']   = grado.id;
    const grupo = this.ctx.grupo();     if (grupo?.id)   params['grupo_id']   = grupo.id;

    this.loadingTabla.set(true);
    this.api.get<{ data: Estudiante[]; total: number }>('/alumnos', params)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: resp => {
          this.alumnos.set(resp.data);
          this.totalAlumnos.set(resp.total);
          // Transform for grid display
          this.alumnosDatos.set(resp.data.map((a: any) => ({
            id: a.id,
            matricula: a.matricula,
            nombre_completo: `${a.persona?.nombre} ${a.persona?.apellido_paterno} ${a.persona?.apellido_materno || ''}`.trim(),
            curp: a.persona?.curp || '',
            rfc: a.persona?.rfc || '',
            genero: a.persona?.genero || '',
            fecha_nacimiento: a.persona?.fecha_nacimiento || '',
            telefono: a.persona?.telefono || '',
            email_personal: a.persona?.email_personal || '',
            nacionalidad: a.persona?.nacionalidad || '',
            plantel: a.plantel_nombre || '—',
            nss: a.nss || '',
            tipo_alumno: a.tipo_alumno || '',
            escuela_procedencia: a.escuela_procedencia || '',
            promedio_procedencia: a.promedio_procedencia ?? '',
            beca_tipo: a.beca_tipo || '',
            folio_sep: a.folio_sep || '',
            nivel: a.nivel_educativo?.nombre_nivel || '—',
            grado: a.grado?.nombre_grado || '—',
            grupo: a.grupo?.nombre_grupo || '—',
            fecha_ingreso: a.fecha_ingreso,
            _original: a,
          })));
          this.loadingTabla.set(false);
        },
        error: () => {
          this.loadingTabla.set(false);
          this.notify.error('Error', 'No se pudieron cargar los alumnos');
        },
      });
  }
  abrirPerfil(row: any): void {
    const alumno = row._original || this.alumnos().find(a => a.id === row.id);
    if (!alumno) return;
    this.api.get<Estudiante>(`/alumnos/${alumno.id}`).pipe(takeUntil(this.destroy$)).subscribe({
      next: full => {
        this.alumnoSeleccionado.set(full);
        this.perfilVisible.set(true);
      },
      error: () => {
        this.notify.error('Error', 'No se pudo cargar el perfil');
      },
    });
  }

  onPerfilGuardado(): void {
    this.cargarAlumnos();
  }

  openDialog(): void {
    this.crearAlumnoForm.reset();
    this.showDialog.set(true);
  }

  // Getters para FormControl binding en template
  getNombreControl(): FormControl {
    return this.crearAlumnoForm.get('nombre') as FormControl;
  }

  getApellidoPaternoControl(): FormControl {
    return this.crearAlumnoForm.get('apellido_paterno') as FormControl;
  }

  getApellidoMaternoControl(): FormControl {
    return this.crearAlumnoForm.get('apellido_materno') as FormControl;
  }

  getCurpControl(): FormControl {
    return this.crearAlumnoForm.get('curp') as FormControl;
  }

  abrirAsignacionMasiva(): void {
    this.masivoSeleccionIds = [];
    this.masivoForm = { grupoDestinoId: null, motivo: '' };
    this.dlgAsignacionMasiva.set(true);
  }

  confirmarAsignacionMasiva(): void {
    if (this.masivoSeleccionIds.length === 0) {
      this.notify.warning('Selección requerida', 'Selecciona al menos un alumno');
      return;
    }
    if (!this.masivoForm.grupoDestinoId) {
      this.notify.warning('Grupo requerido', 'Selecciona el grupo destino');
      return;
    }
    if (!this.masivoForm.motivo.trim()) {
      this.notify.warning('Motivo requerido', 'Indica el motivo del cambio');
      return;
    }

    this.asignandoMasivo.set(true);
    const payload = {
      estudianteIds: this.masivoSeleccionIds,
      grupoDestinoId: this.masivoForm.grupoDestinoId,
      motivo: this.masivoForm.motivo.trim(),
      cicloEscolarId: this.ctx.ciclo()?.id ?? null,
    };
    this.api.post<{ total: number; exitosos: number; fallidos: { estudianteId: string; error: string }[] }>(
      '/movilidad/cambio-grupo-masivo', payload
    ).pipe(takeUntil(this.destroy$)).subscribe({
      next: (resp) => {
        this.asignandoMasivo.set(false);
        this.dlgAsignacionMasiva.set(false);
        if (resp.fallidos.length === 0) {
          this.notify.success('Asignación completada', `${resp.exitosos} de ${resp.total} alumnos movidos`);
        } else {
          this.notify.warning('Asignación parcial', `${resp.exitosos} de ${resp.total} movidos, ${resp.fallidos.length} con error`);
        }
        this.cargarAlumnos();
      },
      error: (e) => {
        this.asignandoMasivo.set(false);
        this.notify.error('Error', e.error?.detail ?? e.error?.message ?? 'No se pudo asignar el grupo');
      },
    });
  }

  exportCSV(): void  { this.exp.toCSV(this.alumnosDatos(), this.exportCols, 'alumnos'); }
  exportXLSX(): void { this.exp.toXLSX(this.alumnosDatos(), this.exportCols, 'Alumnos', 'alumnos'); }

  crearAlumno(): void {
    // Guard: prevent multiple submissions - check before emitting to subject
    if (this.loading()) {
      return;
    }
    // Emit to throttled subject — max one per 500ms
    this.crearAlumnoSubject.next();
  }

  private performCrearAlumno(): void {
    // Set loading IMMEDIATELY to block any subsequent calls from crearAlumno()
    // This must happen synchronously before any async operations
    this.loading.set(true);

    // El botón ya no se deshabilita por formulario inválido (decisión de producto
    // 2026-07-17): se permite el clic siempre y aquí se avisa con un toast +
    // se marcan los campos como touched para que app-form-field muestre el error inline.
    if (this.crearAlumnoForm.invalid) {
      this.loading.set(false);
      this.crearAlumnoForm.markAllAsTouched();
      this.notify.warning('Validación', 'Por favor completa todos los campos correctamente');
      return;
    }

    const formValues = this.crearAlumnoForm.value;
    const payload = {
      persona: {
        nombre: formValues.nombre?.trim() || '',
        apellido_paterno: formValues.apellido_paterno?.trim() || '',
        apellido_materno: formValues.apellido_materno?.trim() || '',
        curp: formValues.curp?.toUpperCase().trim() || '',
      },
      plantel_id: this.ctx.plantel()?.id,
    };

    this.api.post<Estudiante>('/alumnos', payload).pipe(takeUntil(this.destroy$)).subscribe({
      next: (newAlumno) => {
        this.showDialog.set(false);
        this.loading.set(false);
        this.crearAlumnoForm.reset();
        this.notify.success('Creado', `Matrícula: ${newAlumno.matricula}`);
        this.cargarAlumnos();
        this.abrirPerfil({ _original: newAlumno });
      },
      error: (e) => {
        this.loading.set(false);
        const detail = e.error?.detail ?? e.error?.message;
        const status = e.status;
        const msg = Array.isArray(detail) ? detail.map((d: any) => d.msg || d).join('; ')
                  : typeof detail === 'string' ? detail
                  : status === 409 ? 'Ya existe un alumno con esa CURP'
                  : status === 422 ? (e.error?.error || 'Datos inválidos')
                  : 'Error al crear alumno';
        this.notify.error('Error', msg);
      },
    });
  }
}
