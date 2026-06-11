/**
 * ProfesorPerfilComponent — Drawer lateral con perfil completo del profesor.
 * 3 tabs: Datos Personales | Datos Laborales | Nómina y Banco
 */
import { Component, Input, Output, EventEmitter, OnChanges, SimpleChanges, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DrawerModule } from 'primeng/drawer';
import { ButtonModule } from 'primeng/button';
import { ToastModule } from 'primeng/toast';
import { TabsModule } from 'primeng/tabs';
import { TabList, Tab, TabPanels, TabPanel } from 'primeng/tabs';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';
import { MessageService } from 'primeng/api';
import { ApiService } from '../../../core/services/api.service';
import type { Profesor } from '../../../core/models';

const CONTRATOS       = ['BASE','INTERINO','CONTRATO','HONORARIOS','TIEMPO_COMPLETO','MEDIO_TIEMPO'];
const TIPOS_CONTRATO  = CONTRATOS;
const NIVELES_EST     = ['BACHILLERATO','LICENCIATURA','MAESTRIA','DOCTORADO','POSDOCTORADO'];
const TURNOS          = ['MATUTINO','VESPERTINO','NOCTURNO','MIXTO'];
const ESTADOS_CIVIL   = ['SOLTERO','CASADO','UNION_LIBRE','DIVORCIADO','VIUDO'];
const BANCOS          = ['BBVA','SANTANDER','BANAMEX','BANORTE','HSBC','SCOTIABANK','INBURSA','BANBAJIO','OTRO'];

@Component({
  selector: 'app-profesor-perfil',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    DrawerModule, ButtonModule, ToastModule,
    TabsModule, TabList, Tab, TabPanels, TabPanel,
    InputTextModule, SelectModule, DatePickerModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast />

    <p-drawer [(visible)]="visible" position="right"
      [style]="{width:'560px'}" [header]="profesor?.persona?.nombre_completo ?? 'Perfil del Profesor'"
      (onHide)="onCerrar()">

      @if (profesor) {
        <div class="perfil-meta">
          <span class="emp-chip">Núm. Empleado: {{ profesor.numero_empleado }}</span>
          @if (profesor.cedula_profesional) {
            <span class="cedula-chip">Cédula: {{ profesor.cedula_profesional }}</span>
          }
        </div>

        <p-tabs [(value)]="tabActivo">
          <p-tablist>
            <p-tab value="personal"><i class="pi pi-user"></i> Personal</p-tab>
            <p-tab value="laboral"><i class="pi pi-briefcase"></i> Laboral</p-tab>
            <p-tab value="nomina"><i class="pi pi-credit-card"></i> Nómina</p-tab>
          </p-tablist>

          <p-tabpanels>
            <!-- ── Tab 1: Datos Personales ────────────────────────────── -->
            <p-tabpanel value="personal">
              <div class="form-section">
                <h4 class="sec-title">Identificación</h4>
                <div class="form-row">
                  <label>Nombre(s)</label>
                  <input pInputText [(ngModel)]="form.nombre" />
                </div>
                <div class="form-row">
                  <label>Apellido paterno</label>
                  <input pInputText [(ngModel)]="form.apellido_paterno" />
                </div>
                <div class="form-row">
                  <label>Apellido materno</label>
                  <input pInputText [(ngModel)]="form.apellido_materno" />
                </div>
                <div class="form-row">
                  <label>CURP</label>
                  <input pInputText [(ngModel)]="form.curp" maxlength="18"
                    style="font-family:monospace;text-transform:uppercase" readonly />
                </div>
                <div class="form-row">
                  <label>RFC</label>
                  <input pInputText [(ngModel)]="form.rfc" maxlength="13"
                    style="font-family:monospace;text-transform:uppercase" />
                </div>
                <div class="form-row">
                  <label>NSS (ISSSTE/IMSS)</label>
                  <input pInputText [(ngModel)]="form.nss" maxlength="11"
                    style="font-family:monospace" placeholder="11 dígitos" />
                </div>
                <div class="form-row">
                  <label>Género</label>
                  <p-select [options]="[{l:'Masculino',v:'M'},{l:'Femenino',v:'F'}]"
                    [(ngModel)]="form.genero" optionLabel="l" optionValue="v" 
 [filter]="true" filterPlaceholder="Buscar..."/>
                </div>
                <div class="form-row">
                  <label>Fecha nacimiento</label>
                  <p-datepicker [(ngModel)]="form.fecha_nacimiento" dateFormat="dd/mm/yy" />
                </div>
                <div class="form-row">
                  <label>Estado civil</label>
                  <p-select [options]="estadosCivil" [(ngModel)]="form.estado_civil" [showClear]="true" />
                </div>
              </div>
              <div class="form-section">
                <h4 class="sec-title">Contacto</h4>
                <div class="form-row">
                  <label>Teléfono</label>
                  <input pInputText [(ngModel)]="form.telefono" maxlength="15" />
                </div>
                <div class="form-row">
                  <label>Email personal</label>
                  <input pInputText [(ngModel)]="form.email_personal" type="email" />
                </div>
                <div class="form-row">
                  <label>Municipio nacimiento</label>
                  <input pInputText [(ngModel)]="form.municipio_nacimiento" />
                </div>
                <div class="form-row">
                  <label>Estado nacimiento</label>
                  <input pInputText [(ngModel)]="form.estado_nacimiento" />
                </div>
              </div>
            </p-tabpanel>

            <!-- ── Tab 2: Datos Laborales ─────────────────────────────── -->
            <p-tabpanel value="laboral">
              <div class="form-section">
                <h4 class="sec-title">Contratación</h4>
                <div class="form-row">
                  <label>Tipo contrato</label>
                  <p-select [options]="tiposContrato" [(ngModel)]="form.tipo_contrato" />
                </div>
                <div class="form-row">
                  <label>Turno</label>
                  <p-select [options]="turnos" [(ngModel)]="form.turno" [showClear]="true" />
                </div>
                <div class="form-row">
                  <label>Fecha ingreso inst.</label>
                  <p-datepicker [(ngModel)]="form.fecha_ingreso_inst" dateFormat="dd/mm/yy" />
                </div>
              </div>
              <div class="form-section">
                <h4 class="sec-title">Escolaridad y especialidad</h4>
                <div class="form-row">
                  <label>Nivel estudios</label>
                  <p-select [options]="nivelesEst" [(ngModel)]="form.nivel_estudios" [showClear]="true" />
                </div>
                <div class="form-row">
                  <label>Especialidad / área</label>
                  <input pInputText [(ngModel)]="form.especialidad" placeholder="Ej: Matemáticas, Historia" />
                </div>
                <div class="form-row">
                  <label>Cédula profesional</label>
                  <input pInputText [(ngModel)]="form.cedula_profesional" maxlength="20"
                    style="font-family:monospace" />
                </div>
              </div>
            </p-tabpanel>

            <!-- ── Tab 3: Nómina ──────────────────────────────────────── -->
            <p-tabpanel value="nomina">
              <div class="form-section">
                <p class="nomina-aviso">
                  <i class="pi pi-shield"></i>
                  Los datos bancarios se almacenan cifrados y solo son visibles para RRHH.
                </p>
                <div class="form-row">
                  <label>Banco</label>
                  <p-select [options]="bancos" [(ngModel)]="form.banco" [showClear]="true" />
                </div>
                <div class="form-row">
                  <label>CLABE interbancaria</label>
                  <input pInputText [(ngModel)]="form.clabe" maxlength="18"
                    style="font-family:monospace;letter-spacing:.05em" placeholder="18 dígitos" />
                </div>
                <div class="form-row">
                  <label>RFC (para nómina)</label>
                  <input pInputText [(ngModel)]="form.rfc" maxlength="13"
                    style="font-family:monospace;text-transform:uppercase" />
                </div>
              </div>
            </p-tabpanel>
          </p-tabpanels>
        </p-tabs>

        <div class="drawer-footer">
          <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="onCerrar()" />
          <p-button label="Guardar cambios" icon="pi pi-save" [loading]="saving()" (onClick)="guardar()" />
        </div>
      }
    </p-drawer>
  `,
  styles: [`
    .perfil-meta { display: flex; gap: .5rem; flex-wrap: wrap; margin-bottom: 1rem; }
    .emp-chip, .cedula-chip {
      font-family: monospace; font-size: .8rem; background: var(--surface-100);
      border: 1px solid var(--surface-300); padding: .2rem .6rem; border-radius: 4px; color: #475569;
    }
    .form-section { margin-bottom: 1.25rem; }
    .sec-title { font-size: .82rem; font-weight: 700; text-transform: uppercase;
      letter-spacing: .05em; color: var(--primary-color); margin: 0 0 .6rem;
      border-bottom: 1px solid var(--surface-200); padding-bottom: .3rem; }
    .form-row { display: grid; grid-template-columns: 150px 1fr; gap: .5rem; align-items: center; margin-bottom: .5rem; }
    .form-row label { font-size: .82rem; color: var(--text-color-secondary); }
    .nomina-aviso {
      font-size: .8rem; background: var(--blue-50); border: 1px solid var(--blue-200);
      border-radius: 6px; padding: .6rem .8rem; color: var(--blue-800);
      display: flex; gap: .4rem; align-items: center; margin-bottom: 1rem;
    }
    .drawer-footer { display: flex; justify-content: flex-end; gap: .75rem;
      margin-top: 1.5rem; padding-top: 1rem; border-top: 1px solid var(--surface-200); }
  `],
})
export class ProfesorPerfilComponent implements OnChanges {
  private readonly api = inject(ApiService);
  private readonly msg = inject(MessageService);

  @Input() profesor: Profesor | null = null;
  @Input() visible = false;
  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() saved = new EventEmitter<void>();

  tabActivo = 'personal';
  form: any = {};
  saving = signal(false);

  readonly tiposContrato = TIPOS_CONTRATO;
  readonly nivelesEst    = NIVELES_EST;
  readonly turnos        = TURNOS;
  readonly estadosCivil  = ESTADOS_CIVIL;
  readonly bancos        = BANCOS;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['profesor'] && this.profesor) {
      this.initForm();
    }
  }

  private initForm(): void {
    const p = this.profesor!;
    const per = (p.persona ?? {}) as any;
    this.form = {
      nombre: per.nombre ?? '',
      apellido_paterno: per.apellido_paterno ?? '',
      apellido_materno: per.apellido_materno ?? '',
      curp: per.curp ?? '',
      genero: per.genero ?? null,
      fecha_nacimiento: per.fecha_nacimiento ? new Date(per.fecha_nacimiento) : null,
      telefono: per.telefono ?? '',
      email_personal: per.email_personal ?? '',
      estado_civil: per.estado_civil ?? null,
      municipio_nacimiento: per.municipio_nacimiento ?? '',
      estado_nacimiento: per.estado_nacimiento ?? '',
      // Laborales
      tipo_contrato: p.tipo_contrato ?? null,
      rfc: p.rfc ?? '',
      nss: p.nss ?? '',
      cedula_profesional: p.cedula_profesional ?? '',
      especialidad: p.especialidad ?? '',
      nivel_estudios: p.nivel_estudios ?? null,
      fecha_ingreso_inst: p.fecha_ingreso_inst ? new Date(p.fecha_ingreso_inst) : null,
      clabe: p.clabe ?? '',
      banco: p.banco ?? null,
      turno: p.turno ?? null,
    };
  }

  guardar(): void {
    if (!this.profesor) return;
    this.saving.set(true);

    const payload = {
      persona: {
        nombre: this.form.nombre,
        apellido_paterno: this.form.apellido_paterno,
        apellido_materno: this.form.apellido_materno || null,
        genero: this.form.genero,
        fecha_nacimiento: this.form.fecha_nacimiento ? this.formatDate(this.form.fecha_nacimiento) : null,
        telefono: this.form.telefono || null,
        email_personal: this.form.email_personal || null,
        estado_civil: this.form.estado_civil,
        municipio_nacimiento: this.form.municipio_nacimiento || null,
        estado_nacimiento: this.form.estado_nacimiento || null,
      },
      laborales: {
        tipo_contrato: this.form.tipo_contrato,
        rfc: this.form.rfc || null,
        nss: this.form.nss || null,
        cedula_profesional: this.form.cedula_profesional || null,
        especialidad: this.form.especialidad || null,
        nivel_estudios: this.form.nivel_estudios,
        fecha_ingreso_inst: this.form.fecha_ingreso_inst ? this.formatDate(this.form.fecha_ingreso_inst) : null,
        clabe: this.form.clabe || null,
        banco: this.form.banco,
        turno: this.form.turno,
      },
    };

    this.api.patch(`/profesores/${this.profesor.id}`, payload).subscribe({
      next: () => {
        this.msg.add({ severity: 'success', summary: 'Guardado', detail: 'Perfil actualizado' });
        this.saving.set(false);
        this.saved.emit();
      },
      error: e => {
        this.msg.add({ severity: 'error', summary: 'Error', detail: e.error?.detail ?? 'Error' });
        this.saving.set(false);
      },
    });
  }

  onCerrar(): void {
    this.visible = false;
    this.visibleChange.emit(false);
  }

  private formatDate(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
  }
}
