/**
 * FASE 24 — Gestión de Padres de Familia (Parent/Family Management)
 * Admin module para CRUD de contactos familiares y tutores legales
 * Accessible: ADMIN_GLOBAL (nivel_acceso=0), DIRECTOR (nivel_acceso=1)
 */
import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { CardModule } from 'primeng/card';
import { ApiService } from '../../core/services/api.service';
import { ApexNotificationService } from 'apex-component-library';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';

interface EstudianteOpt { id: string; nombre_completo: string; matricula?: string; }
interface ContactoFamiliar {
  id: string;
  estudiante_id: string;
  persona_id: string;
  nombre_completo: string;
  parentesco: string;
  es_tutor_legal: boolean;
  es_contacto_emergencia: boolean;
  puede_recoger: boolean;
  telefono_principal: string;
  email: string;
  rfc?: string;
  ocupacion: string;
  nivel_estudios: string;
  toma_decision_conjunta: boolean;
  grado_responsabilidad: string;
  is_active: boolean;
}
interface ParentescoOpt { label: string; value: string; }

@Component({
  selector: 'app-padres-admin',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule,
    ButtonModule, DialogModule, SelectModule,
    InputTextModule, InputNumberModule, TooltipModule, ConfirmDialogModule, ToggleSwitchModule, CardModule,
    InteractiveGridComponent,
  ],
  providers: [ConfirmationService],
  template: `
    <p-confirmDialog />

    <div class="page-header">
      <div>
        <h2>Gestión de Padres de Familia</h2>
        <p class="subtitle">Administración de contactos familiares y tutores legales</p>
      </div>
    </div>

    <!-- FILTRO Y BÚSQUEDA -->
    <div class="toolbar">
      <div style="display:flex;gap:.5rem;align-items:center;flex:1">
        <label style="font-size:.85rem;color:var(--text-secondary)">Estudiante:</label>
        <p-select
          [(ngModel)]="estudianteSeleccionado"
          [options]="estudiantes()"
          optionLabel="nombre_completo"
          optionValue="id"
          placeholder="Seleccionar estudiante..."
          [showClear]="true"
          (onChange)="cargarContactos()"
          style="width:300px;flex-shrink:0"
              [filter]="true" filterPlaceholder="Buscar...">
        </p-select>
      </div>
      <p-button label="Agregar contacto" icon="pi pi-plus" size="small"
        (onClick)="abrirFormulario()"
        [disabled]="!estudianteSeleccionado" />
    </div>

    <!-- TABLA DE CONTACTOS FAMILIARES -->
    <app-interactive-grid
      [data]="contactosFlat()"
      [columns]="contactosColumns"
      [loading]="loading()"
      [showDelete]="true"
      (rowSelected)="editarContacto($event)"
      (rowDeleted)="eliminarContacto($event)"
    />

    <!-- DIÁLOGO FORMULARIO -->
    <p-dialog [visible]="mostrarFormulario()" (visibleChange)="mostrarFormulario.set($event)" [header]="formularioTitulo()"
      [modal]="true" [style]="{width:'90vw'}" [maximizable]="true">
      <form [formGroup]="form">
        <div class="form-grid">
          <!-- DATOS PERSONALES DEL CONTACTO -->
          <div class="form-section">
            <h4>Datos Personales del Contacto</h4>
            <div class="field">
              <label>Nombre Completo *</label>
              <input pInputText formControlName="nombre_completo"
                placeholder="Ej: Juan García Pérez" />
            </div>
            <div class="field">
              <label>Email</label>
              <input pInputText type="email" formControlName="email"
                placeholder="contacto@example.com" />
            </div>
            <div class="field">
              <label>Teléfono Principal *</label>
              <input pInputText formControlName="telefono_principal"
                placeholder="Ej: 5551234567" />
            </div>
            <div class="field">
              <label>RFC</label>
              <input pInputText formControlName="rfc" [maxLength]="13"
                placeholder="Ej: XXXX000000YYY" />
            </div>
          </div>

          <!-- RELACIÓN CON ALUMNO -->
          <div class="form-section">
            <h4>Relación con el Alumno</h4>
            <div class="field">
              <label>Parentesco *</label>
              <p-select formControlName="parentesco"
                [options]="parentescos()"
                optionLabel="label"
                optionValue="value"
                placeholder="Seleccionar parentesco..." 
 [filter]="true" filterPlaceholder="Buscar..."/>
            </div>
            <div class="field">
              <label>Ocupación</label>
              <input pInputText formControlName="ocupacion"
                placeholder="Ej: Ingeniero, Empleado, Independiente" />
            </div>
            <div class="field">
              <label>Nivel de Estudios</label>
              <p-select formControlName="nivel_estudios"
                [options]="[
                  {label: 'Primaria', value: 'PRIMARIA'},
                  {label: 'Secundaria', value: 'SECUNDARIA'},
                  {label: 'Preparatoria', value: 'PREPARATORIA'},
                  {label: 'Licenciatura', value: 'LICENCIATURA'},
                  {label: 'Posgrado', value: 'POSGRADO'}
                ]"
                optionLabel="label"
                optionValue="value"
                placeholder="Seleccionar..." 
 [filter]="true" filterPlaceholder="Buscar..."/>
            </div>
          </div>

          <!-- PERMISOS Y RESPONSABILIDADES -->
          <div class="form-section">
            <h4>Permisos y Responsabilidades</h4>
            <div class="field-toggle">
              <label>Tutor Legal</label>
              <p-toggleSwitch formControlName="es_tutor_legal" />
              <small>Tiene autoridad legal sobre decisiones del alumno</small>
            </div>
            <div class="field-toggle">
              <label>Contacto de Emergencia</label>
              <p-toggleSwitch formControlName="es_contacto_emergencia" />
              <small>A contactar en caso de emergencia médica</small>
            </div>
            <div class="field-toggle">
              <label>Puede Recoger al Alumno</label>
              <p-toggleSwitch formControlName="puede_recoger" />
              <small>Autorizado para recoger al alumno en la escuela</small>
            </div>
            <div class="field-toggle">
              <label>Toma de Decisión Conjunta</label>
              <p-toggleSwitch formControlName="toma_decision_conjunta" />
              <small>Requiere aprobación de ambos padres para decisiones mayores</small>
            </div>
            <div class="field">
              <label>Grado de Responsabilidad</label>
              <p-select formControlName="grado_responsabilidad"
                [options]="[
                  {label: 'Principal (Todas las decisiones)', value: 'PRINCIPAL'},
                  {label: 'Secundario (Coaprobación)', value: 'SECUNDARIO'},
                  {label: 'Consulta (Solo información)', value: 'CONSULTA'}
                ]"
                optionLabel="label"
                optionValue="value" 
 [filter]="true" filterPlaceholder="Buscar..."/>
            </div>
          </div>
        </div>

        <!-- VALIDACIÓN -->
        @if (mostrarErrores()) {
          <div style="background:#fef2f2;padding:1rem;border-radius:6px;margin:.5rem 0;color:#dc2626;font-size:.85rem">
            <strong>Errores de validación:</strong>
            <ul style="margin:.5rem 0;padding-left:1.5rem">
              @if (form.get('nombre_completo')?.hasError('required')) { <li>Nombre completo es requerido</li> }
              @if (form.get('telefono_principal')?.hasError('required')) { <li>Teléfono principal es requerido</li> }
              @if (form.get('parentesco')?.hasError('required')) { <li>Parentesco es requerido</li> }
              @if (form.get('email')?.hasError('email')) { <li>Email debe ser válido</li> }
            </ul>
          </div>
        }
      </form>

      <ng-template pTemplate="footer">
        <p-button label="Cancelar" icon="pi pi-times" severity="secondary"
          (onClick)="mostrarFormulario.set(false)" />
        <p-button label="Guardar" icon="pi pi-check" severity="success"
          (onClick)="guardarContacto()" [disabled]="!form.valid || guardando()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .page-header { display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:1.5rem }
    .page-header h2 { margin:0;font-size:1.5rem }
    .subtitle { font-size:.85rem;color:var(--text-color-secondary);margin:.25rem 0 0 }
    .toolbar { display:flex;gap:1rem;margin-bottom:1rem;align-items:center }
    .form-grid { display:grid;grid-template-columns:1fr 1fr;gap:2rem;margin:1.5rem 0 }
    .form-section { padding:1rem;background:var(--surface-50);border-radius:8px }
    .form-section h4 { margin:0 0 1rem;font-size:.95rem;font-weight:600;color:var(--primary-color) }
    .field { margin-bottom:1rem }
    .field label { display:block;font-size:.85rem;font-weight:600;margin-bottom:.35rem;color:var(--text-color) }
    .field input, .field p-select { width:100%;max-width:100% }
    .field-toggle { display:flex;align-items:center;gap:.75rem;margin-bottom:1rem;padding:0.75rem;background:var(--surface-0);border-radius:6px }
    .field-toggle label { margin:0;flex-shrink:0 }
    .field-toggle small { font-size:.75rem;color:var(--text-muted);margin-left:auto }
    :deep(.p-select-option) { padding:.5rem 1rem }
  `],
})
export class PadresAdminComponent implements OnInit {
  private api = inject(ApiService);
  private readonly notify = inject(ApexNotificationService);
  private confirm = inject(ConfirmationService);
  private fb = inject(FormBuilder);

  estudiantes = signal<EstudianteOpt[]>([]);

  readonly contactosFlat = computed(() =>
    this.contactos().map(c => ({
      ...c,
      tutor_str:     c.es_tutor_legal          ? 'Sí' : 'No',
      emergencia_str: c.es_contacto_emergencia ? 'Sí' : 'No',
      recoger_str:   c.puede_recoger           ? 'Sí' : 'No',
    }))
  );
  readonly contactosColumns: ColumnConfig[] = [
    { field: 'nombre_completo',  header: 'Nombre' },
    { field: 'parentesco',       header: 'Parentesco',    width: '120px' },
    { field: 'telefono_principal', header: 'Teléfono',    width: '130px' },
    { field: 'email',            header: 'Email' },
    { field: 'tutor_str',        header: 'Tutor Legal',   width: '110px' },
    { field: 'emergencia_str',   header: 'Emergencia',    width: '110px' },
    { field: 'recoger_str',      header: 'Puede Recoger', width: '120px' },
  ];
  estudianteSeleccionado: string | null = null;
  contactos = signal<ContactoFamiliar[]>([]);
  loading = signal(false);
  mostrarFormulario = signal(false);
  guardando = signal(false);
  mostrarErrores = signal(false);
  formularioTitulo = signal('Agregar Contacto Familiar');
  contactoEditado: ContactoFamiliar | null = null;

  parentescos = signal<ParentescoOpt[]>([
    { label: 'Padre', value: 'PADRE' },
    { label: 'Madre', value: 'MADRE' },
    { label: 'Tutor', value: 'TUTOR' },
    { label: 'Abuelo', value: 'ABUELO' },
    { label: 'Abuela', value: 'ABUELA' },
    { label: 'Tío', value: 'TIO' },
    { label: 'Tía', value: 'TIA' },
    { label: 'Hermano', value: 'HERMANO' },
    { label: 'Hermana', value: 'HERMANA' },
    { label: 'Otro', value: 'OTRO' },
  ]);

  form = this.fb.group({
    nombre_completo: ['', Validators.required],
    email: ['', [Validators.email]],
    telefono_principal: ['', Validators.required],
    rfc: [''],
    parentesco: ['', Validators.required],
    ocupacion: [''],
    nivel_estudios: [''],
    es_tutor_legal: [false],
    es_contacto_emergencia: [false],
    puede_recoger: [true],
    toma_decision_conjunta: [false],
    grado_responsabilidad: ['PRINCIPAL'],
  });

  ngOnInit(): void {
    this.cargarEstudiantes();
  }

  private cargarEstudiantes(): void {
    this.loading.set(true);
    this.api.get<{ data: any[] }>('/alumnos?por_pagina=1000').subscribe({
      next: (res) => {
        this.estudiantes.set((res.data || []).map((a: any) => ({
          id: a.id,
          matricula: a.matricula,
          nombre_completo: a.persona?.nombre_completo ?? a.nombre_completo ?? a.matricula ?? 'Sin nombre',
        })));
        this.loading.set(false);
      },
      error: () => {
        this.notify.error('Error', 'No se pudieron cargar los estudiantes');
        this.loading.set(false);
      },
    });
  }

  cargarContactos(): void {
    const estId = this.estudianteSeleccionado;
    if (!estId) return;

    this.loading.set(true);
    this.api.get<ContactoFamiliar[]>(`/contactos?estudiante_id=${estId}`).subscribe({
      next: (res) => {
        this.contactos.set(Array.isArray(res) ? res : (res as any).data || []);
        this.loading.set(false);
      },
      error: () => {
        this.notify.error('Error', 'No se pudieron cargar los contactos');
        this.loading.set(false);
      },
    });
  }

  abrirFormulario(): void {
    this.contactoEditado = null;
    this.formularioTitulo.set('Agregar Contacto Familiar');
    this.form.reset({ puede_recoger: true, grado_responsabilidad: 'PRINCIPAL' });
    this.mostrarErrores.set(false);
    this.mostrarFormulario.set(true);
  }

  editarContacto(row: ContactoFamiliar): void {
    this.contactoEditado = row;
    this.formularioTitulo.set(`Editar: ${row.nombre_completo}`);
    this.form.patchValue({
      nombre_completo: row.nombre_completo,
      email: row.email,
      telefono_principal: row.telefono_principal,
      rfc: row.rfc || '',
      parentesco: row.parentesco,
      ocupacion: row.ocupacion || '',
      nivel_estudios: row.nivel_estudios || '',
      es_tutor_legal: row.es_tutor_legal,
      es_contacto_emergencia: row.es_contacto_emergencia,
      puede_recoger: row.puede_recoger,
      toma_decision_conjunta: row.toma_decision_conjunta,
      grado_responsabilidad: row.grado_responsabilidad,
    });
    this.mostrarErrores.set(false);
    this.mostrarFormulario.set(true);
  }

  guardarContacto(): void {
    if (!this.form.valid) {
      this.mostrarErrores.set(true);
      return;
    }

    this.guardando.set(true);
    const estId = this.estudianteSeleccionado!;
    const payload = {
      estudiante_id: estId,
      ...this.form.value
    };

    const endpoint = this.contactoEditado
      ? `/contactos/${this.contactoEditado.id}`
      : '/contactos';
    const metodo = this.contactoEditado ? 'patch' : 'post';

    this.api[metodo as 'post'|'patch']<any>(endpoint, payload).subscribe({
      next: () => {
        this.notify.success('Éxito', `Contacto ${this.contactoEditado ? 'actualizado' : 'creado'} correctamente`);
        this.mostrarFormulario.set(false);
        this.guardando.set(false);
        this.cargarContactos();
      },
      error: () => {
        this.notify.error('Error', 'No se pudo guardar el contacto');
        this.guardando.set(false);
      },
    });
  }

  eliminarContacto(row: ContactoFamiliar): void {
    this.confirm.confirm({
      message: `¿Eliminar contacto "${row.nombre_completo}"?`,
      header: 'Confirmar eliminación',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.api.delete(`/contactos/${row.id}`).subscribe({
          next: () => {
            this.notify.success('Eliminado', 'Contacto eliminado correctamente');
            this.cargarContactos();
          },
          error: () => {
            this.notify.error('Error', 'No se pudo eliminar el contacto');
          },
        });
      },
    });
  }
}
