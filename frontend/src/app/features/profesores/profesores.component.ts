/**
 * FASE 1 & FASE 24 — Profesores (Teachers) + Interactive Grid APEX-style
 * Manages teacher records with filtering, sorting, and profile management.
 * Cascade plantel→nivel→grado→grupo is driven by ContextService (global topbar).
 */
import { Component, OnDestroy, OnInit, inject, signal, computed, effect, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DialogModule } from 'primeng/dialog';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ContextCatalogService } from '../../core/services/context-catalog.service';
import { ExportService, ExportColumn } from '../../core/services/export.service';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import { ProfesorPerfilComponent } from '../../shared/components/profesor-perfil/profesor-perfil.component';
import { HelpButtonComponent } from '../../shared/components/help-button/help-button.component';
import { ImportButtonComponent } from '../../shared/components/import-button/import-button.component';
import { Profesor } from '../../core/models';
import { ApexNotificationService } from 'apex-component-library';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';
import { AdesValidators } from '../../shared/validators/ades-validators';

@Component({
  selector: 'app-profesores',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule,
    ButtonModule, InputTextModule, DialogModule,
    InteractiveGridComponent, ProfesorPerfilComponent, HelpButtonComponent, ImportButtonComponent,
  ],
  template: `

    <app-profesor-perfil
      [visible]="perfilVisible()"
      (visibleChange)="perfilVisible.set($event)"
      [profesor]="profesorSeleccionado()"
      (saved)="cargarProfesores()"
    />

    <div class="page-header">
      <div>
        <h2>Profesores</h2>
        <p class="subtitle">Plantilla docente — {{ totalProfesores() | number }} activos</p>
      </div>
      <div style="display:flex;gap:0.5rem;align-items:center">
        <app-help-button modulo="profesores" />
        <app-import-button entidad="profesores" [onSuccess]="recargar" />
        <p-button label="CSV"   icon="pi pi-file"       severity="secondary" [text]="true" (onClick)="exportCSV()"  pTooltip="Exportar CSV" />
        <p-button label="Excel" icon="pi pi-file-excel" severity="secondary" [text]="true" (onClick)="exportXLSX()" pTooltip="Exportar Excel" />
        <p-button label="Nuevo profesor" icon="pi pi-plus" (onClick)="openDialog()" />
      </div>
    </div>

    <!-- Búsqueda rápida -->
    <div style="display:flex; gap:0.75rem; align-items:center; margin-bottom:1rem; flex-wrap:wrap">
      <div style="flex:1; min-width:250px">
        <input
          pInputText
          type="text"
          placeholder="Buscar profesor..."
          [(ngModel)]="busqueda"
          style="width:100%" aria-label="Buscar profesor"/>
      </div>
    </div>

    <!-- Interactive Grid APEX-style — cascada plantel→nivel→grado→grupo vive en topbar.
         El grid refleja el contexto y escribe de vuelta vía filterChange (lazo bilateral). -->
    <app-interactive-grid
      [data]="profesoresFiltrados()"
      [columns]="columnas"
      [loading]="loadingTabla()"
      [externalFilters]="catalog.contextFilters()"
      [externalSuggestions]="catalog.contextSuggestions()"
      [serverFilteredFields]="cascadeFields"
      (filterChange)="catalog.applyGridFilter($event.field, $event.value)"
      (rowSelected)="abrirPerfil($event)"
    />

    <p-dialog [visible]="showDialog()" (visibleChange)="showDialog.set($event)" header="Nuevo Profesor" [modal]="true" [style]="{width:'400px'}">
      <div style="display:flex;flex-direction:column;gap:1rem">
        <div>
          <label class="dlg-lbl" for="prof-nombre">Nombre(s) *</label>
          <input pInputText id="prof-nombre" [(ngModel)]="form.nombre" style="width:100%"/>
        </div>
        <div>
          <label class="dlg-lbl" for="prof-ap">Apellido paterno *</label>
          <input pInputText id="prof-ap" [(ngModel)]="form.apellido_paterno" style="width:100%"/>
        </div>
        <div>
          <label class="dlg-lbl" for="prof-am">Apellido materno</label>
          <input pInputText id="prof-am" [(ngModel)]="form.apellido_materno" style="width:100%"/>
        </div>
        <div>
          <label class="dlg-lbl" for="prof-num-empleado">Número de empleado *</label>
          <input pInputText id="prof-num-empleado" [(ngModel)]="form.numero_empleado" style="width:100%"/>
        </div>
        <div>
          <label class="dlg-lbl" for="prof-curp">CURP (18 caracteres) *</label>
          <input pInputText id="prof-curp" [(ngModel)]="form.curp" style="width:100%;text-transform:uppercase;font-family:monospace" maxlength="18"/>
        </div>
        <div>
          <label class="dlg-lbl" for="prof-contrato">Tipo de contrato</label>
          <input pInputText id="prof-contrato" [(ngModel)]="form.tipo_contrato" placeholder="Ej: TIEMPO_COMPLETO" style="width:100%"/>
        </div>
        <p-button label="Crear profesor" (onClick)="crearProfesor()" [loading]="loading()" />
      </div>
    </p-dialog>
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1rem; }
    .subtitle { font-size: 0.82rem; color: var(--text-color-secondary); margin: 0; }
    .dlg-lbl { display: block; font-size: .85rem; margin-bottom: .25rem; color: var(--text-color-secondary); }
  `],
})
export class ProfesoresComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private readonly api = inject(ApiService);
  private readonly ctx = inject(ContextService);
  readonly catalog = inject(ContextCatalogService);
  private readonly exp = inject(ExportService);
  private readonly notify = inject(ApexNotificationService);

  profesores = signal<Profesor[]>([]);
  profesoresDatos = signal<any[]>([]);
  totalProfesores = signal(0);
  busqueda = signal('');
  readonly profesoresFiltrados = computed(() => {
    const q = this.busqueda().toLowerCase();
    if (!q) return this.profesoresDatos();
    return this.profesoresDatos().filter(p =>
      (p.nombre_completo || '').toLowerCase().includes(q) ||
      (p.numero_empleado || '').toLowerCase().includes(q) ||
      (p.curp || '').toLowerCase().includes(q)
    );
  });
  profesorSeleccionado = signal<Profesor | null>(null);
  perfilVisible = signal(false);
  showDialog = signal(false);
  loading = signal(false);
  loadingTabla = signal(false);
  form = { nombre: '', apellido_paterno: '', apellido_materno: '', numero_empleado: '', curp: '', tipo_contrato: '' };

  /** Columnas gobernadas por el contexto global (filtrado server-side + lazo bilateral). */
  readonly cascadeFields = ['plantel', 'nivel', 'grado', 'grupo'];

  columnas: ColumnConfig[] = [
    { field: 'numero_empleado', header: 'Empleado',       sortable: true, filterable: true, width: '120px' },
    { field: 'nombre_completo', header: 'Nombre Completo',sortable: true, filterable: true, width: '220px' },
    { field: 'plantel',         header: 'Plantel',        sortable: true, filterable: true, width: '140px' },
    { field: 'rfc',             header: 'RFC',            sortable: true, filterable: true, width: '140px' },
    { field: 'tipo_contrato',   header: 'Contrato',       sortable: true, filterable: true, width: '130px' },
    { field: 'especialidad',    header: 'Especialidad',   sortable: true, filterable: true, width: '120px' },
    { field: 'turno',           header: 'Turno',          sortable: true, filterable: true, width: '90px' },
  ];

  private readonly exportCols: ExportColumn[] = [
    { field: 'numero_empleado', header: 'No. Empleado' },
    { field: 'nombre_completo', header: 'Nombre' },
    { field: 'curp', header: 'CURP' },
    { field: 'rfc', header: 'RFC' },
    { field: 'genero', header: 'Género' },
    { field: 'fecha_nacimiento', header: 'Fecha Nacimiento' },
    { field: 'telefono', header: 'Teléfono' },
    { field: 'email_personal', header: 'Email' },
    { field: 'plantel', header: 'Plantel' },
    { field: 'tipo_contrato', header: 'Contrato' },
    { field: 'especialidad', header: 'Especialidad' },
    { field: 'nivel_estudios', header: 'Nivel Estudios' },
    { field: 'cedula_profesional', header: 'Cédula Profesional' },
    { field: 'nss', header: 'NSS' },
    { field: 'turno', header: 'Turno' },
    { field: 'fecha_ingreso_inst', header: 'Fecha Ingreso' },
  ];

  readonly recargar = () => this.cargarProfesores();

  constructor() {
    // Recargar cuando cambia cualquier dimensión de la cascada global.
    effect(() => {
      this.ctx.plantel(); this.ctx.nivel(); this.ctx.grado(); this.ctx.grupo();
      this.cargarProfesores();
    });
  }

  ngOnInit(): void {
    // ngOnInit solo necesario para lógica no relacionada con la cascada.
  }

  cargarProfesores(): void {
    const params: Record<string, any> = { pagina: 1, por_pagina: 500 };
    const plantel = this.ctx.plantel(); if (plantel?.id) params['plantel_id'] = plantel.id;
    const nivel   = this.ctx.nivel();   if (nivel?.id)   params['nivel_id']   = nivel.id;
    const grado   = this.ctx.grado();   if (grado?.id)   params['grado_id']   = grado.id;
    const grupo   = this.ctx.grupo();   if (grupo?.id)   params['grupo_id']   = grupo.id;

    this.loadingTabla.set(true);
    this.api.get<{ data: Profesor[]; total: number }>('/profesores', params).pipe(takeUntil(this.destroy$)).subscribe({
      next: resp => {
        this.profesores.set(resp.data);
        this.totalProfesores.set(resp.total);
        this.profesoresDatos.set(resp.data.map((p: any) => ({
          id: p.id,
          numero_empleado: p.numero_empleado,
          nombre_completo: `${p.persona?.nombre} ${p.persona?.apellido_paterno} ${p.persona?.apellido_materno || ''}`.trim(),
          curp: p.persona?.curp || '',
          rfc: p.rfc || '',
          genero: p.persona?.genero || '',
          fecha_nacimiento: p.persona?.fecha_nacimiento || '',
          telefono: p.persona?.telefono || '',
          email_personal: p.persona?.email_personal || '',
          plantel: p.plantel_nombre || '—',
          tipo_contrato: p.tipo_contrato || '',
          especialidad: p.especialidad || '',
          nivel_estudios: p.nivel_estudios || '',
          cedula_profesional: p.cedula_profesional || '',
          nss: p.nss || '',
          turno: p.turno || '',
          fecha_ingreso_inst: p.fecha_ingreso_inst || '',
          _original: p,
        })));
        this.loadingTabla.set(false);
      },
      error: () => {
        this.loadingTabla.set(false);
        this.notify.error('Error', 'No se pudieron cargar los profesores');
      },
    });
  }

  abrirPerfil(row: any): void {
    const prof = row._original || this.profesores().find(p => p.id === row.id);
    if (!prof) return;
    this.api.get<Profesor>(`/profesores/${prof.id}`).pipe(takeUntil(this.destroy$)).subscribe({
      next: full => {
        this.profesorSeleccionado.set(full);
        this.perfilVisible.set(true);
      },
      error: () => {
        this.notify.error('Error', 'No se pudo cargar el expediente');
      },
    });
  }

  openDialog(): void {
    this.showDialog.set(true);
    const nextEmp = `EMP-${this.totalProfesores() + 101}`;
    this.form = { nombre: '', apellido_paterno: '', apellido_materno: '', numero_empleado: nextEmp, curp: '', tipo_contrato: 'TIEMPO_COMPLETO' };
  }

  crearProfesor(): void {
    if (!this.form.nombre || !this.form.apellido_paterno || !this.form.numero_empleado || !this.form.curp) {
      this.notify.warning('Campos requeridos', 'Nombre, apellido paterno, número de empleado y CURP son obligatorios');
      return;
    }
    if (!AdesValidators.curpValido(this.form.curp)) {
      this.notify.warning('CURP inválido', 'Formato esperado: AAAA000000HAAAAA00');
      return;
    }
    this.loading.set(true);
    // Corregido 2026-07-20: ProfesorController#create ahora acepta CrearProfesorRequest
    // (persona anidada → crea la Persona nueva y luego el Profesor referenciándola),
    // mismo patrón que el alta de alumno. Antes fallaba el 100% de las veces porque el
    // backend esperaba un persona_id de una persona ya existente que este formulario
    // nunca enviaba.
    const payload = {
      numero_empleado: this.form.numero_empleado,
      tipo_contrato: this.form.tipo_contrato || 'TIEMPO_COMPLETO',
      persona: {
        nombre: this.form.nombre,
        apellido_paterno: this.form.apellido_paterno,
        apellido_materno: this.form.apellido_materno || '',
        curp: this.form.curp.toUpperCase(),
      },
      plantel_id: this.ctx.plantel()?.id,
    };
    this.api.post<Profesor>('/profesores', payload).pipe(takeUntil(this.destroy$)).subscribe({
      next: (newProf) => {
        this.showDialog.set(false);
        this.loading.set(false);
        this.notify.success('Creado', `Profesor: ${newProf.numero_empleado}`);
        this.cargarProfesores();
      },
      error: (e) => {
        this.loading.set(false);
        const detail = e.error?.detail;
        const msg = Array.isArray(detail) ? detail.map((d: any) => d.msg || d).join('; ')
                  : typeof detail === 'string' ? detail
                  : 'Error al crear profesor';
        this.notify.error('Error', msg);
      },
    });
  }

  exportCSV():  void { this.exp.toCSV(this.profesoresDatos(), this.exportCols, 'profesores'); }
  exportXLSX(): void { this.exp.toXLSX(this.profesoresDatos(), this.exportCols, 'Profesores', 'profesores'); }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
