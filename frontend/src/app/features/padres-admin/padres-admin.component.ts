import { Component, OnDestroy, OnInit, inject, signal, computed, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { DrawerModule } from 'primeng/drawer';
import { SelectModule } from 'primeng/select';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TooltipModule } from 'primeng/tooltip';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { CardModule } from 'primeng/card';
import { TabsModule, TabList, Tab, TabPanels, TabPanel } from 'primeng/tabs';
import { ConfirmationService } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ApiService } from '../../core/services/api.service';
import { ApexNotificationService } from 'apex-component-library';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import { DomicilioComponent } from '../../shared/components/domicilio/domicilio.component';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';

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

interface TutorAlumno {
  id: string;
  relacion: string;
  prioridad: number;
  puede_recoger: boolean;
  es_responsable_economico: boolean;
  es_contacto_emergencia: boolean;
  nivel_acceso_portal: string;
  is_active: boolean;
  nombre: string;
  apellido_paterno: string;
  apellido_materno: string | null;
  telefono_principal: string | null;
  email: string | null;
}

interface ParentescoOpt { label: string; value: string; }

const NIVELES_ACCESO_PORTAL = [
  { label: 'Lectura (solo ver)', value: 'LECTURA' },
  { label: 'Completo (ver y comunicarse)', value: 'COMPLETO' },
  { label: 'Restringido', value: 'RESTRINGIDO' },
];

/**
 * Módulo administrativo de tutores y padres de familia.
 * Permite gestionar el catálogo de tutores, vincularlos con alumnos y
 * administrar sus credenciales de acceso al portal de padres.
 * Requiere nivelAcceso 4 (AdminPlantel) o superior.
 */
@Component({
  selector: 'app-padres-admin',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule, ReactiveFormsModule,
    ButtonModule, DialogModule, DrawerModule, SelectModule, AutoCompleteModule,
    InputTextModule, InputNumberModule, TooltipModule,
    ToggleSwitchModule, CardModule,
    TabsModule, TabList, Tab, TabPanels, TabPanel,
    ConfirmDialogModule, InteractiveGridComponent,
    DomicilioComponent,
  ],
  providers: [ConfirmationService],
  template: `
    <p-confirmDialog />

    <div class="page-header">
      <div>
        <h2>Gestión de Padres de Familia</h2>
        <p class="subtitle">Contactos familiares y tutores legales con acceso al portal</p>
      </div>
    </div>

    <!-- Selector de estudiante compartido -->
    <div class="student-bar">
      <label class="student-label">Estudiante:</label>
      <p-autoComplete
        [(ngModel)]="estudianteSeleccionadoObj"
        [suggestions]="estudiantesSugg()"
        (completeMethod)="buscarEstudiantes($event)"
        optionLabel="nombre_completo"
        [forceSelection]="true"
        placeholder="Buscar estudiante por nombre o matrícula..."
        [showClear]="true"
        [dropdown]="true"
        (onSelect)="onEstudianteSelect($event.value)"
        (onClear)="onEstudianteClear()"
        [style]="{ width: '320px' }" />
    </div>

    <p-tabs [value]="tabActivo()" (valueChange)="onTabChange($event)">
      <p-tablist>
        <p-tab value="contactos"><i class="pi pi-users"></i> Contactos Familiares</p-tab>
        <p-tab value="tutores"><i class="pi pi-shield"></i> Tutores y Portal</p-tab>
      </p-tablist>

      <p-tabpanels>

        <!-- ═══ TAB 1: CONTACTOS FAMILIARES ═══ -->
        <p-tabpanel value="contactos">
          <div class="tab-toolbar">
            <div></div>
            <p-button label="Agregar contacto" icon="pi pi-plus" size="small"
              [disabled]="!estudianteSeleccionado" (onClick)="abrirFormulario()" />
          </div>

          <app-interactive-grid
            [data]="contactosFlat()"
            [columns]="contactosColumns"
            [loading]="loading()"
            [showDelete]="true"
            (rowSelected)="editarContacto($event)"
            (rowDeleted)="eliminarContacto($event)"
          />
        </p-tabpanel>

        <!-- ═══ TAB 2: TUTORES Y PORTAL (PE-029, PE-032, PE-033) ═══ -->
        <p-tabpanel value="tutores">
          <div class="tab-toolbar">
            <div class="hint-text" *ngIf="!estudianteSeleccionado">
              <i class="pi pi-info-circle"></i> Selecciona un estudiante para gestionar sus tutores legales.
            </div>
            <div *ngIf="estudianteSeleccionado" class="hint-text">
              <i class="pi pi-info-circle"></i> Los tutores legales tienen acceso al Portal de Familias. Haz clic en una fila para ver acciones.
            </div>
            <div class="tab-actions" *ngIf="estudianteSeleccionado">
              <p-button label="Añadir Tutor" icon="pi pi-user-plus" severity="secondary" size="small"
                (onClick)="abrirAgregarTutor()" />
            </div>
          </div>

          <app-interactive-grid
            [data]="tutoresFlat()"
            [columns]="tutoresColumns"
            [loading]="loadingTutores()"
            [showDelete]="true"
            (rowSelected)="onTutorSelected($event)"
            (rowDeleted)="eliminarTutor($event)"
          />

          <!-- Acciones por tutor seleccionado -->
          @if (tutorSeleccionado()) {
            <div class="tutor-actions-bar">
              <span class="tutor-sel-name">
                <i class="pi pi-user"></i>
                {{ tutorSeleccionado()!.nombre }} {{ tutorSeleccionado()!.apellido_paterno }}
              </span>
              <p-button label="Crear Cuenta Portal" icon="pi pi-key" severity="secondary" size="small"
                pTooltip="Crea un usuario en Authentik para que el tutor acceda al Portal de Familias"
                (onClick)="abrirCrearCuenta()" />
              <p-button label="Configurar Accesos" icon="pi pi-lock" severity="secondary" size="small"
                pTooltip="Define qué información puede ver este tutor en el portal"
                (onClick)="abrirConfigurarAccesos()" />
            </div>
          }
        </p-tabpanel>

      </p-tabpanels>
    </p-tabs>

    <!-- ─── Drawer: Agregar/Editar Contacto ─── -->
    <p-drawer [visible]="mostrarFormulario()" (visibleChange)="mostrarFormulario.set($event)"
      [header]="formularioTitulo()" position="right" [style]="{width:'560px'}">
      <p-tabs [(value)]="formTab">
        <p-tablist>
          <p-tab value="personal"><i class="pi pi-user"></i> Personal</p-tab>
          <p-tab value="domicilio" [disabled]="!contactoEditado"><i class="pi pi-map-marker"></i> Domicilio</p-tab>
          <p-tab value="relacion"><i class="pi pi-shield"></i> Relación</p-tab>
        </p-tablist>

        <p-tabpanels>
          <!-- Tab 1: Personal -->
          <p-tabpanel value="personal">
            <form [formGroup]="form">
              <div style="display:flex; flex-direction:column; gap:1rem; margin-top:1rem;">
                <div class="field">
                  <label>Nombre Completo *</label>
                  <input pInputText formControlName="nombre_completo" adesFormat="nombre" placeholder="Ej: Juan García Pérez" style="width:100%" />
                </div>
                <div class="field">
                  <label>RFC</label>
                  <input pInputText formControlName="rfc" adesFormat="rfc" placeholder="Ej: XXXX000000YYY" style="width:100%" />
                </div>
                <div class="field">
                  <label>Ocupación</label>
                  <p-select formControlName="ocupacion" [options]="ocupacionesOpts()" optionLabel="valor" optionValue="valor"
                    placeholder="Seleccionar ocupación..." [filter]="true" [style]="{width:'100%'}" ariaLabel="Ocupación"/>
                </div>
                <div class="field">
                  <label>Nivel de Estudios</label>
                  <p-select formControlName="nivel_estudios" [options]="nivelesEstudios" optionLabel="label" optionValue="value"
                    placeholder="Seleccionar..." [style]="{width:'100%'}" ariaLabel="Nivel de Estudios"/>
                </div>
              </div>
            </form>
          </p-tabpanel>

          <!-- Tab 2: Domicilio y Contactos -->
          <p-tabpanel value="domicilio">
            <div style="margin-top:1rem;">
              @if (contactoEditado) {
                <app-domicilio [personaId]="contactoEditado.persona_id" />
              }
            </div>
          </p-tabpanel>

          <!-- Tab 3: Relación y Permisos -->
          <p-tabpanel value="relacion">
            <form [formGroup]="form">
              <div style="display:flex; flex-direction:column; gap:1rem; margin-top:1rem;">
                <div class="field">
                  <label>Parentesco *</label>
                  <p-select formControlName="parentesco" [options]="parentescos()" optionLabel="label" optionValue="value"
                    placeholder="Seleccionar parentesco..." [filter]="true" [style]="{width:'100%'}" ariaLabel="Parentesco"/>
                </div>
                
                <div class="field">
                  <label>Email</label>
                  <input pInputText type="email" formControlName="email" adesFormat="email" placeholder="contacto@example.com" style="width:100%" />
                </div>
                <div class="field">
                  <label>Teléfono Principal *</label>
                  <input pInputText formControlName="telefono_principal" adesFormat="telefono" placeholder="Ej: 5551234567" style="width:100%" />
                </div>

                <div class="field-toggle">
                  <label>Tutor Legal</label>
                  <p-toggleSwitch formControlName="es_tutor_legal" />
                  <small style="display:block;color:var(--text-color-secondary);font-size:0.75rem">Autoridad legal sobre decisiones del alumno</small>
                </div>
                
                <div class="field-toggle">
                  <label>Contacto de Emergencia</label>
                  <p-toggleSwitch formControlName="es_contacto_emergencia" />
                  <small style="display:block;color:var(--text-color-secondary);font-size:0.75rem">A contactar en caso de emergencia médica</small>
                </div>
                
                <div class="field-toggle">
                  <label>Puede Recoger</label>
                  <p-toggleSwitch formControlName="puede_recoger" />
                  <small style="display:block;color:var(--text-color-secondary);font-size:0.75rem">Autorizado para recoger al alumno</small>
                </div>
                
                <div class="field-toggle">
                  <label>Decisión Conjunta</label>
                  <p-toggleSwitch formControlName="toma_decision_conjunta" />
                  <small style="display:block;color:var(--text-color-secondary);font-size:0.75rem">Requiere aprobación de ambos padres</small>
                </div>
                
                <div class="field">
                  <label>Grado de Responsabilidad</label>
                  <p-select formControlName="grado_responsabilidad" [options]="gradosResponsabilidad"
                    optionLabel="label" optionValue="value" [style]="{width:'100%'}" ariaLabel="Grado de Responsabilidad"/>
                </div>
              </div>
            </form>
          </p-tabpanel>
        </p-tabpanels>
      </p-tabs>

      @if (mostrarErrores()) {
        <div class="validation-errors" style="margin-top:1rem;">
          <strong>Errores:</strong>
          <ul>
            @if (form.get('nombre_completo')?.hasError('required')) { <li>Nombre completo es requerido</li> }
            @if (form.get('telefono_principal')?.hasError('required')) { <li>Teléfono principal es requerido</li> }
            @if (form.get('parentesco')?.hasError('required')) { <li>Parentesco es requerido</li> }
            @if (form.get('email')?.hasError('email')) { <li>Email debe ser válido</li> }
          </ul>
        </div>
      }

      <div class="drawer-footer" style="display:flex; justify-content:flex-end; gap:0.5rem; margin-top:1.5rem; padding-top:1rem; border-top:1px solid var(--surface-200)">
        <p-button label="Cancelar" icon="pi pi-times" severity="secondary" [text]="true" (onClick)="mostrarFormulario.set(false)" />
        @if (formTab !== 'domicilio') {
          <p-button label="Guardar" icon="pi pi-check" severity="success"
            (onClick)="guardarContacto()" [loading]="guardando()" [disabled]="guardando()" />
        }
      </div>
    </p-drawer>

    <!-- ─── Dialog: Añadir Tutor Legal ─── -->
    <p-dialog header="Añadir Tutor Legal" [(visible)]="dlgAgregarTutor"
      [modal]="true" [style]="{width:'480px'}" [draggable]="false">
      <div class="dlg-grid">
        <p class="hint-text">
          <i class="pi pi-info-circle"></i>
          Selecciona un contacto familiar ya registrado para asignarlo como tutor legal con acceso al portal.
        </p>
        <div class="field">
          <label>Contacto Familiar *</label>
          <p-select [options]="contactosLov()" optionLabel="label" optionValue="value"
            [(ngModel)]="agregarTutorForm.personaId" [filter]="true" filterBy="label"
            placeholder="Seleccionar contacto..." [style]="{width:'100%'}" ariaLabel="Contacto Familiar"/>
        </div>
        <div class="field">
          <label>Relación</label>
          <p-select [options]="relacionesTutor" optionLabel="label" optionValue="value"
            [(ngModel)]="agregarTutorForm.relacion" [style]="{width:'100%'}" ariaLabel="Relación"/>
        </div>
        <div class="field">
          <label>Prioridad</label>
          <p-inputnumber [(ngModel)]="agregarTutorForm.prioridad" [min]="1" [max]="5" [style]="{width:'100%'}" />
        </div>
        <div class="field">
          <label>Nivel de Acceso al Portal</label>
          <p-select [options]="nivelesAccesoPortal" optionLabel="label" optionValue="value"
            [(ngModel)]="agregarTutorForm.nivelAccesoPortal" [style]="{width:'100%'}" ariaLabel="Nivel de Acceso al Portal"/>
        </div>
        <div class="field-toggle">
          <label>Responsable Económico</label>
          <p-toggleSwitch [(ngModel)]="agregarTutorForm.esResponsableEconomico" />
        </div>
        <div class="field-toggle">
          <label>Contacto de Emergencia</label>
          <p-toggleSwitch [(ngModel)]="agregarTutorForm.esContactoEmergencia" />
        </div>
        <div class="field-toggle">
          <label>Puede Recoger</label>
          <p-toggleSwitch [(ngModel)]="agregarTutorForm.puedeRecoger" />
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dlgAgregarTutor = false" />
        <p-button label="Agregar Tutor" icon="pi pi-user-plus" severity="secondary"
          [loading]="guardandoTutor()" (onClick)="guardarTutor()" />
      </ng-template>
    </p-dialog>

    <!-- ─── Dialog: Crear Cuenta Portal (PE-032) ─── -->
    <p-dialog header="Crear Cuenta en Portal de Familias" [(visible)]="dlgCrearCuenta"
      [modal]="true" [style]="{width:'440px'}" [draggable]="false">
      <div class="dlg-grid">
        <p class="hint-text">
          <i class="pi pi-key"></i>
          Se creará un usuario en Authentik para que <strong>{{ tutorSeleccionado()?.nombre }} {{ tutorSeleccionado()?.apellido_paterno }}</strong>
          acceda al portal con email y contraseña temporal.
        </p>
        <div class="field">
          <label>Email de acceso *</label>
          <input pInputText [(ngModel)]="crearCuentaForm.email" adesFormat="email" type="email"
            placeholder="email@example.com" style="width:100%" />
        </div>
        <div class="field">
          <label>Nombre completo (para el portal)</label>
          <input pInputText [(ngModel)]="crearCuentaForm.nombreCompleto" adesFormat="nombre" style="width:100%" />
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dlgCrearCuenta = false" />
        <p-button label="Crear Cuenta" icon="pi pi-key" severity="success"
          [loading]="guardandoTutor()" (onClick)="crearCuentaPortal()" />
      </ng-template>
    </p-dialog>

    <!-- ─── Dialog: Configurar Accesos (PE-033) ─── -->
    <p-dialog header="Configurar Accesos al Portal" [(visible)]="dlgConfigurarAccesos"
      [modal]="true" [style]="{width:'480px'}" [draggable]="false">
      <div class="dlg-grid">
        <p class="hint-text">
          <i class="pi pi-lock"></i>
          Define qué información puede ver <strong>{{ tutorSeleccionado()?.nombre }} {{ tutorSeleccionado()?.apellido_paterno }}</strong>
          en el Portal de Familias.
        </p>
        <div class="field-toggle">
          <label>Ver Calificaciones</label>
          <p-toggleSwitch [(ngModel)]="accesoForm.puedeVerCalificaciones" />
        </div>
        <div class="field-toggle">
          <label>Ver Asistencias</label>
          <p-toggleSwitch [(ngModel)]="accesoForm.puedeVerAsistencias" />
        </div>
        <div class="field-toggle">
          <label>Ver Conducta</label>
          <p-toggleSwitch [(ngModel)]="accesoForm.puedeVerConducta" />
        </div>
        <div class="field-toggle">
          <label>Ver Tareas</label>
          <p-toggleSwitch [(ngModel)]="accesoForm.puedeVerTareas" />
        </div>
        <div class="field-toggle">
          <label>Descargar Documentos</label>
          <p-toggleSwitch [(ngModel)]="accesoForm.puedeDescargarDocumentos" />
        </div>
        <div class="field-toggle">
          <label>Comunicarse con Docentes</label>
          <p-toggleSwitch [(ngModel)]="accesoForm.puedeComunicarseDocentes" />
        </div>
        <div class="field">
          <label>Razón de restricción (si aplica)</label>
          <input pInputText [(ngModel)]="accesoForm.razonRestriccion"
            placeholder="Ej: Orden judicial, custodia parcial..." style="width:100%" />
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dlgConfigurarAccesos = false" />
        <p-button label="Guardar Configuración" icon="pi pi-save" severity="secondary"
          [loading]="guardandoTutor()" (onClick)="guardarAccesos()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .page-header { display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:1rem }
    .page-header h2 { margin:0;font-size:1.5rem }
    .subtitle { font-size:.85rem;color:var(--text-color-secondary);margin:.25rem 0 0 }
    .student-bar { display:flex;gap:.75rem;align-items:center;margin-bottom:1rem;padding:.5rem .75rem;background:var(--surface-50);border-radius:8px;border:1px solid var(--surface-200) }
    .student-label { font-size:.85rem;font-weight:600;color:var(--text-color-secondary);flex-shrink:0 }
    .tab-toolbar { display:flex;align-items:center;justify-content:space-between;padding:.75rem 0;flex-wrap:wrap;gap:.5rem }
    .tab-actions { display:flex;gap:.5rem }
    .hint-text { font-size:.8rem;color:var(--text-color-secondary) }
    .tutor-actions-bar { display:flex;align-items:center;gap:.75rem;padding:.75rem 1rem;margin-top:.5rem;background:var(--surface-50);border-radius:8px;border:1px solid var(--primary-200,#bfdbfe);flex-wrap:wrap }
    .tutor-sel-name { font-size:.9rem;font-weight:600;color:var(--primary-color);display:flex;align-items:center;gap:.35rem;flex:1 }
    .form-grid { display:grid;grid-template-columns:1fr 1fr;gap:2rem;margin:1.5rem 0 }
    .form-section { padding:1rem;background:var(--surface-50);border-radius:8px }
    .form-section h4 { margin:0 0 1rem;font-size:.95rem;font-weight:600;color:var(--primary-color) }
    .field { margin-bottom:1rem }
    .field label { display:block;font-size:.85rem;font-weight:500;margin-bottom:.35rem;color:var(--text-color-secondary) }
    .field-toggle { display:flex;align-items:center;gap:.75rem;margin-bottom:.75rem;padding:.5rem .75rem;background:var(--surface-0);border-radius:6px }
    .field-toggle label { margin:0;flex-shrink:0;font-size:.85rem;font-weight:500;min-width:170px }
    .field-toggle small { font-size:.75rem;color:var(--text-color-secondary);margin-left:auto }
    .validation-errors { background:#fef2f2;padding:1rem;border-radius:6px;margin:.5rem 0;color:var(--color-danger);font-size:.85rem }
    .validation-errors ul { margin:.5rem 0;padding-left:1.5rem }
    .dlg-grid { display:flex;flex-direction:column;gap:.75rem;padding:.25rem 0 }
  `],
})
export class PadresAdminComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private readonly api    = inject(ApiService);
  private readonly notify = inject(ApexNotificationService);
  private readonly confirm = inject(ConfirmationService);
  private readonly fb     = inject(FormBuilder);

  // ── Shared state ──────────────────────────────────────────────
  tabActivo             = signal('contactos');
  formTab               = 'personal';
  ocupacionesOpts       = signal<any[]>([]);
  estudiantesSugg       = signal<any[]>([]);
  estudianteSeleccionadoObj: any = null;
  estudianteSeleccionado: string | null = null;
  loading               = signal(false);
  guardando             = signal(false);

  // ── Contactos tab ─────────────────────────────────────────────
  contactos             = signal<ContactoFamiliar[]>([]);
  mostrarFormulario     = signal(false);
  mostrarErrores        = signal(false);
  formularioTitulo      = signal('Agregar Contacto Familiar');
  contactoEditado: ContactoFamiliar | null = null;

  readonly contactosFlat = computed(() =>
    this.contactos().map(c => ({
      ...c,
      tutor_str:      c.es_tutor_legal          ? 'Sí' : 'No',
      emergencia_str: c.es_contacto_emergencia  ? 'Sí' : 'No',
      recoger_str:    c.puede_recoger           ? 'Sí' : 'No',
    }))
  );

  readonly contactosColumns: ColumnConfig[] = [
    { field: 'nombre_completo',    header: 'Nombre' },
    { field: 'parentesco',         header: 'Parentesco',    width: '120px' },
    { field: 'telefono_principal', header: 'Teléfono',      width: '130px' },
    { field: 'email',              header: 'Email' },
    { field: 'tutor_str',          header: 'Tutor Legal',   width: '110px' },
    { field: 'emergencia_str',     header: 'Emergencia',    width: '110px' },
    { field: 'recoger_str',        header: 'Puede Recoger', width: '120px' },
  ];

  // Contactos LOV para añadir tutor (persona_id como value)
  readonly contactosLov = computed(() =>
    this.contactos().map(c => ({
      label: `${c.nombre_completo} (${c.parentesco})`,
      value: c.persona_id,
    }))
  );

  // ── Tutores tab ───────────────────────────────────────────────
  tutores               = signal<TutorAlumno[]>([]);
  loadingTutores        = signal(false);
  guardandoTutor        = signal(false);
  tutorSeleccionado     = signal<TutorAlumno | null>(null);

  dlgAgregarTutor      = false;
  dlgCrearCuenta       = false;
  dlgConfigurarAccesos = false;

  readonly tutoresFlat = computed(() =>
    this.tutores().map(t => ({
      ...t,
      nombre_completo:   `${t.nombre} ${t.apellido_paterno}${t.apellido_materno ? ' ' + t.apellido_materno : ''}`,
      puede_recoger_str: t.puede_recoger          ? 'Sí' : 'No',
      responsable_str:   t.es_responsable_economico ? 'Sí' : 'No',
    }))
  );

  readonly tutoresColumns: ColumnConfig[] = [
    { field: 'nombre_completo',    header: 'Nombre' },
    { field: 'relacion',           header: 'Relación',         width: '100px' },
    { field: 'prioridad',          header: 'Prioridad',        width: '90px' },
    { field: 'nivel_acceso_portal',header: 'Acceso Portal',    width: '130px' },
    { field: 'puede_recoger_str',  header: 'Puede Recoger',    width: '120px' },
    { field: 'responsable_str',    header: 'Resp. Económico',  width: '140px' },
  ];

  agregarTutorForm = {
    personaId: '',
    relacion: 'TUTOR',
    esResponsableEconomico: false,
    esContactoEmergencia: false,
    prioridad: 1,
    puedeRecoger: true,
    nivelAccesoPortal: 'LECTURA',
  };

  crearCuentaForm = { email: '', nombreCompleto: '' };

  accesoForm = {
    puedeVerCalificaciones: true,
    puedeVerAsistencias: true,
    puedeVerConducta: true,
    puedeVerTareas: true,
    puedeDescargarDocumentos: true,
    puedeComunicarseDocentes: true,
    razonRestriccion: '',
  };

  // ── Catálogos ─────────────────────────────────────────────────
  readonly nivelesAccesoPortal = NIVELES_ACCESO_PORTAL;

  readonly relacionesTutor = [
    { label: 'Tutor', value: 'TUTOR' },
    { label: 'Padre', value: 'PADRE' },
    { label: 'Madre', value: 'MADRE' },
    { label: 'Abuelo', value: 'ABUELO' },
    { label: 'Otro familiar', value: 'OTRO' },
  ];

  readonly nivelesEstudios = [
    { label: 'Primaria',     value: 'PRIMARIA' },
    { label: 'Secundaria',   value: 'SECUNDARIA' },
    { label: 'Preparatoria', value: 'PREPARATORIA' },
    { label: 'Licenciatura', value: 'LICENCIATURA' },
    { label: 'Posgrado',     value: 'POSGRADO' },
  ];

  readonly gradosResponsabilidad = [
    { label: 'Principal (todas las decisiones)', value: 'PRINCIPAL' },
    { label: 'Secundario (coaprobación)',         value: 'SECUNDARIO' },
    { label: 'Consulta (solo información)',       value: 'CONSULTA' },
  ];

  readonly parentescos = signal([
    { label: 'Padre',    value: 'PADRE' },
    { label: 'Madre',    value: 'MADRE' },
    { label: 'Tutor',    value: 'TUTOR' },
    { label: 'Abuelo',   value: 'ABUELO' },
    { label: 'Abuela',   value: 'ABUELA' },
    { label: 'Tío',      value: 'TIO' },
    { label: 'Tía',      value: 'TIA' },
    { label: 'Hermano',  value: 'HERMANO' },
    { label: 'Hermana',  value: 'HERMANA' },
    { label: 'Otro',     value: 'OTRO' },
  ]);

  form: FormGroup = this.fb.group({
    nombre_completo:       ['', Validators.required],
    email:                 ['', [Validators.email]],
    telefono_principal:    ['', Validators.required],
    rfc:                   [''],
    parentesco:            ['', Validators.required],
    ocupacion:             [''],
    nivel_estudios:        [''],
    es_tutor_legal:        [false],
    es_contacto_emergencia:[false],
    puede_recoger:         [true],
    toma_decision_conjunta:[false],
    grado_responsabilidad: ['PRINCIPAL'],
  });

  // ─────────────────────────────────────────────────────────────
  ngOnInit(): void {
    this.api.get<any[]>('/catalogos').pipe(takeUntil(this.destroy$)).subscribe({
      next: cats => {
        const ocup = cats.find(c => c.codigo === 'CAT_OCUPACIONES');
        if (ocup) this.ocupacionesOpts.set(ocup.items ?? []);
      }
    });
  }

  onTabChange(tab: string | number | undefined): void {
    this.tabActivo.set(String(tab ?? 'contactos'));
    if (tab === 'tutores' && this.estudianteSeleccionado) {
      this.cargarTutores();
    }
  }

  onEstudianteChange(): void {
    this.tutorSeleccionado.set(null);
    if (this.tabActivo() === 'contactos') {
      this.cargarContactos();
    } else {
      this.cargarTutores();
    }
  }

  // ── Carga de datos ────────────────────────────────────────────

  buscarEstudiantes(event: { query: string }): void {
    if (!event.query || event.query.trim().length < 2) {
      this.estudiantesSugg.set([]);
      return;
    }
    this.api.get<any[]>('/portal/buscar', { q: event.query }).pipe(takeUntil(this.destroy$)).subscribe({
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

  onEstudianteSelect(alumno: any): void {
    this.estudianteSeleccionado = alumno?.id ?? null;
    this.estudianteSeleccionadoObj = alumno;
    this.onEstudianteChange();
  }

  onEstudianteClear(): void {
    this.estudianteSeleccionado = null;
    this.estudianteSeleccionadoObj = null;
    this.onEstudianteChange();
  }

  cargarContactos(): void {
    if (!this.estudianteSeleccionado) return;
    this.loading.set(true);
    this.api.get<ContactoFamiliar[]>(`/contactos?estudiante_id=${this.estudianteSeleccionado}`).pipe(takeUntil(this.destroy$)).subscribe({
      next: (res) => {
        this.contactos.set(Array.isArray(res) ? res : (res as any).data ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.notify.error('Error', 'No se pudieron cargar los contactos');
        this.loading.set(false);
      },
    });
  }

  cargarTutores(): void {
    if (!this.estudianteSeleccionado) return;
    this.loadingTutores.set(true);
    this.api.get<TutorAlumno[]>(`/portal-familias/tutores/${this.estudianteSeleccionado}`).pipe(takeUntil(this.destroy$)).subscribe({
      next: (d) => { this.tutores.set(Array.isArray(d) ? d : []); this.loadingTutores.set(false); },
      error: () => { this.loadingTutores.set(false); },
    });
  }

  // ── Contactos CRUD ────────────────────────────────────────────

  abrirFormulario(): void {
    this.contactoEditado = null;
    this.formularioTitulo.set('Agregar Contacto Familiar');
    this.form.reset({ puede_recoger: true, grado_responsabilidad: 'PRINCIPAL' });
    this.formTab = 'personal';
    this.mostrarErrores.set(false);
    this.mostrarFormulario.set(true);
  }

  editarContacto(row: ContactoFamiliar): void {
    this.contactoEditado = row;
    this.formularioTitulo.set(`Editar: ${row.nombre_completo}`);
    this.formTab = 'personal';
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
    if (!this.form.valid) { this.mostrarErrores.set(true); return; }
    this.guardando.set(true);
    const payload = { estudiante_id: this.estudianteSeleccionado!, ...this.form.value };
    const endpoint = this.contactoEditado ? `/contactos/${this.contactoEditado.id}` : '/contactos';
    const req = this.contactoEditado
      ? this.api.patch<any>(endpoint, payload)
      : this.api.post<any>(endpoint, payload);

    req.pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.notify.success('Guardado', `Contacto ${this.contactoEditado ? 'actualizado' : 'creado'} correctamente`);
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
        this.api.delete(`/contactos/${row.id}`).pipe(takeUntil(this.destroy$)).subscribe({
          next: () => { this.notify.success('Eliminado', 'Contacto eliminado correctamente'); this.cargarContactos(); },
          error: () => { this.notify.error('Error', 'No se pudo eliminar el contacto'); },
        });
      },
    });
  }

  // ── Tutores CRUD (PE-029) ─────────────────────────────────────

  abrirAgregarTutor(): void {
    // Ensure contactos are loaded (used as LOV for persona selection)
    if (this.contactos().length === 0) {
      this.cargarContactos();
    }
    this.agregarTutorForm = {
      personaId: '', relacion: 'TUTOR', esResponsableEconomico: false,
      esContactoEmergencia: false, prioridad: 1, puedeRecoger: true, nivelAccesoPortal: 'LECTURA',
    };
    this.dlgAgregarTutor = true;
  }

  guardarTutor(): void {
    if (!this.agregarTutorForm.personaId) {
      this.notify.warning('Campo requerido', 'Selecciona un contacto familiar para asignar como tutor');
      return;
    }
    this.guardandoTutor.set(true);
    const body = {
      persona_id: this.agregarTutorForm.personaId,
      relacion: this.agregarTutorForm.relacion,
      es_responsable_economico: this.agregarTutorForm.esResponsableEconomico,
      es_contacto_emergencia: this.agregarTutorForm.esContactoEmergencia,
      prioridad: this.agregarTutorForm.prioridad,
      puede_recoger: this.agregarTutorForm.puedeRecoger,
      nivel_acceso_portal: this.agregarTutorForm.nivelAccesoPortal,
    };
    this.api.post(`/portal-familias/tutores/${this.estudianteSeleccionado}`, body).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.guardandoTutor.set(false);
        this.dlgAgregarTutor = false;
        this.notify.success('Tutor agregado', 'El tutor legal ha sido vinculado al alumno');
        this.cargarTutores();
      },
      error: (e: any) => {
        this.guardandoTutor.set(false);
        this.notify.error('Error', e?.error?.message ?? 'No se pudo agregar el tutor');
      },
    });
  }

  onTutorSelected(row: any): void {
    this.tutorSeleccionado.set(row as TutorAlumno);
  }

  eliminarTutor(row: any): void {
    this.confirm.confirm({
      message: `¿Eliminar el vínculo de tutor para "${row.nombre} ${row.apellido_paterno}"?`,
      header: 'Confirmar eliminación',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.api.delete(`/portal-familias/tutores/${row.id}`).pipe(takeUntil(this.destroy$)).subscribe({
          next: () => {
            this.notify.success('Eliminado', 'Vínculo de tutor eliminado');
            if (this.tutorSeleccionado()?.id === row.id) this.tutorSeleccionado.set(null);
            this.cargarTutores();
          },
          error: () => { this.notify.error('Error', 'No se pudo eliminar el tutor'); },
        });
      },
    });
  }

  // ── Crear Cuenta Portal (PE-032) ──────────────────────────────

  abrirCrearCuenta(): void {
    const t = this.tutorSeleccionado();
    if (!t) return;
    this.crearCuentaForm = {
      email: t.email ?? '',
      nombreCompleto: `${t.nombre} ${t.apellido_paterno}`,
    };
    this.dlgCrearCuenta = true;
  }

  crearCuentaPortal(): void {
    if (!this.crearCuentaForm.email) {
      this.notify.warning('Email requerido', 'Ingresa el email para la cuenta del portal');
      return;
    }
    const tutor = this.tutorSeleccionado()!;
    this.guardandoTutor.set(true);
    const body = {
      tutor_alumno_id: tutor.id,
      email: this.crearCuentaForm.email,
      nombre_completo: this.crearCuentaForm.nombreCompleto,
    };
    this.api.post('/portal-familias/crear-usuario', body).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.guardandoTutor.set(false);
        this.dlgCrearCuenta = false;
        this.notify.success('Cuenta creada', `Se ha creado el acceso al portal para ${tutor.nombre}`);
      },
      error: (e: any) => {
        this.guardandoTutor.set(false);
        this.notify.error('Error', e?.error?.message ?? 'No se pudo crear la cuenta');
      },
    });
  }

  // ── Configurar Accesos (PE-033) ───────────────────────────────

  abrirConfigurarAccesos(): void {
    this.accesoForm = {
      puedeVerCalificaciones: true, puedeVerAsistencias: true, puedeVerConducta: true,
      puedeVerTareas: true, puedeDescargarDocumentos: true, puedeComunicarseDocentes: true,
      razonRestriccion: '',
    };
    this.dlgConfigurarAccesos = true;
  }

  guardarAccesos(): void {
    const tutor = this.tutorSeleccionado()!;
    this.guardandoTutor.set(true);
    const body = {
      puede_ver_calificaciones: this.accesoForm.puedeVerCalificaciones,
      puede_ver_asistencias: this.accesoForm.puedeVerAsistencias,
      puede_ver_conducta: this.accesoForm.puedeVerConducta,
      puede_ver_tareas: this.accesoForm.puedeVerTareas,
      puede_descargar_documentos: this.accesoForm.puedeDescargarDocumentos,
      puede_comunicarse_docentes: this.accesoForm.puedeComunicarseDocentes,
      razon_restriccion: this.accesoForm.razonRestriccion || null,
    };
    this.api.post(`/portal-familias/restriccion/${tutor.id}`, body).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.guardandoTutor.set(false);
        this.dlgConfigurarAccesos = false;
        this.notify.success('Accesos guardados', `Configuración de accesos actualizada para ${tutor.nombre}`);
      },
      error: (e: any) => {
        this.guardandoTutor.set(false);
        this.notify.error('Error', e?.error?.message ?? 'No se pudo guardar la configuración');
      },
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
