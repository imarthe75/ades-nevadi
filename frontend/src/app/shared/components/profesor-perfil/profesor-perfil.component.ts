/**
 * ProfesorPerfilComponent — Drawer lateral con perfil completo del profesor.
 * 3 tabs: Datos Personales | Datos Laborales | Nómina y Banco
 */
import { Component, Input, Output, EventEmitter, OnChanges, OnInit, SimpleChanges, inject, signal, computed } from '@angular/core';
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
import { DomicilioComponent } from '../domicilio/domicilio.component';
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
    DomicilioComponent,
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
            <p-tab value="domicilio"><i class="pi pi-map-marker"></i> Domicilio</p-tab>
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
                <div class="form-row">
                  <label>Nacionalidad</label>
                  <p-select
                    [options]="nacionalidades()"
                    [(ngModel)]="form.nacionalidad"
                    [filter]="true"
                    filterPlaceholder="Buscar..."
                    [showClear]="true"
                    placeholder="Seleccionar..."
                    style="width:100%" />
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
                  <label>País nacimiento</label>
                  <p-select [options]="paises()"
                    [ngModel]="paisNacId()" (ngModelChange)="onPaisNacChange($event)"
                    optionLabel="nombre_pais" optionValue="id"
                    [filter]="true" filterBy="nombre_pais"
                    [showClear]="true" placeholder="Seleccionar país…"
                    style="width:100%" />
                </div>
                @if (esMexicano()) {
                  <div class="form-row">
                    <label>Estado nacimiento</label>
                    <p-select [options]="estadosMex()"
                      [(ngModel)]="estadoNacId"
                      optionLabel="nombre_estado" optionValue="id"
                      [filter]="true" filterBy="nombre_estado"
                      [showClear]="true" placeholder="Seleccionar estado…"
                      (onChange)="onEstadoNacChange($event.value)"
                      style="width:100%" />
                  </div>
                  <div class="form-row">
                    <label>Municipio nacimiento</label>
                    <p-select [options]="municipiosMex()"
                      [(ngModel)]="municipioNacId"
                      optionLabel="nombre_municipio" optionValue="id"
                      [filter]="true" filterBy="nombre_municipio"
                      [showClear]="true" placeholder="Seleccionar municipio…"
                      [disabled]="!estadoNacId"
                      (onChange)="onMunicipioNacChange($event.value)"
                      style="width:100%" />
                  </div>
                } @else {
                  <div class="form-row">
                    <label>Estado / Provincia</label>
                    <input pInputText [(ngModel)]="form.estado_nacimiento"
                      placeholder="Estado, provincia o región" />
                  </div>
                  <div class="form-row">
                    <label>Ciudad / Municipio</label>
                    <input pInputText [(ngModel)]="form.municipio_nacimiento"
                      placeholder="Ciudad o municipio" />
                  </div>
                }
              </div>
            </p-tabpanel>

            <!-- ── Tab 2: Domicilio y Contactos ─────────────────────────── -->
            <p-tabpanel value="domicilio">
              <app-domicilio [personaId]="profesor?.persona_id ?? null" />
            </p-tabpanel>

            <!-- ── Tab 3: Datos Laborales ─────────────────────────────── -->
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
export class ProfesorPerfilComponent implements OnInit, OnChanges {
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

  nacionalidades = signal<string[]>([]);
  paises         = signal<any[]>([]);
  estadosMex     = signal<any[]>([]);
  municipiosMex  = signal<any[]>([]);
  paisNacId      = signal('');
  estadoNacId    = '';
  municipioNacId = '';
  esMexicano     = computed(() => {
    const id = this.paisNacId();
    if (!id) return true;
    const p = this.paises().find((x: any) => x.id === id);
    return !p || p.nombre_pais === 'México';
  });

  ngOnInit(): void {
    this.api.get<{ nacionalidad: string }[]>('/catalogs/nacionalidades').subscribe({
      next: list => this.nacionalidades.set(list.map(n => n.nacionalidad)),
      error: () => {},
    });
    this.api.get<any[]>('/catalogs/estados-mexico').subscribe({
      next: list => {
        this.estadosMex.set(list);
        if (this.form?.estado_nacimiento) {
          const est = list.find((e: any) => e.nombre_estado === this.form.estado_nacimiento);
          if (est) this.cargarMunicipiosNac(est.id, false);
        }
      },
      error: () => {},
    });
    this.api.get<any[]>('/catalogs/paises').subscribe({
      next: list => {
        this.paises.set(list);
        if (this.form?.pais_nacimiento) {
          const p = list.find((x: any) => x.nombre_pais === this.form.pais_nacimiento);
          if (p) this.paisNacId.set(p.id);
        } else {
          const mx = list.find((x: any) => x.nombre_pais === 'México');
          if (mx) this.paisNacId.set(mx.id);
        }
      },
      error: () => {},
    });
  }

  onPaisNacChange(paisId: string): void {
    this.paisNacId.set(paisId ?? '');
    const p = this.paises().find((x: any) => x.id === paisId);
    this.form.pais_nacimiento = p?.nombre_pais ?? '';
    if (p?.nombre_pais !== 'México') {
      this.estadoNacId = '';
      this.municipioNacId = '';
      this.municipiosMex.set([]);
    }
  }

  onEstadoNacChange(estadoId: string): void {
    this.municipioNacId = '';
    this.form.municipio_nacimiento = '';
    this.municipiosMex.set([]);
    if (!estadoId) { this.form.estado_nacimiento = ''; return; }
    const est = this.estadosMex().find((e: any) => e.id === estadoId);
    this.form.estado_nacimiento = est?.nombre_estado ?? '';
    this.cargarMunicipiosNac(estadoId, true);
  }

  private cargarMunicipiosNac(estadoId: string, limpiar: boolean): void {
    this.api.get<any[]>('/catalogs/municipios', { estado_id: estadoId }).subscribe({
      next: list => {
        this.municipiosMex.set(list);
        if (!limpiar && this.form?.municipio_nacimiento) {
          const mun = list.find((m: any) => m.nombre_municipio === this.form.municipio_nacimiento);
          if (mun) this.municipioNacId = mun.id;
        }
      },
      error: () => {},
    });
  }

  onMunicipioNacChange(municipioId: string): void {
    const mun = this.municipiosMex().find((m: any) => m.id === municipioId);
    this.form.municipio_nacimiento = mun?.nombre_municipio ?? '';
  }

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
      pais_nacimiento: per.pais_nacimiento ?? 'México',
      municipio_nacimiento: per.municipio_nacimiento ?? '',
      estado_nacimiento: per.estado_nacimiento ?? '',
      nacionalidad: per.nacionalidad ?? 'Mexicana',
    };
    this.estadoNacId = '';
    this.municipioNacId = '';
    this.municipiosMex.set([]);
    const paisGuardado = per.pais_nacimiento ?? 'México';
    const paisObj = this.paises().find((x: any) => x.nombre_pais === paisGuardado);
    this.paisNacId.set(paisObj?.id ?? '');
    if (paisGuardado === 'México' || !paisGuardado) {
      if (per.estado_nacimiento && this.estadosMex().length > 0) {
        const est = this.estadosMex().find((e: any) => e.nombre_estado === per.estado_nacimiento);
        if (est) { this.estadoNacId = est.id; this.cargarMunicipiosNac(est.id, false); }
      }
    }
    this.form = { ...this.form,
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
        pais_nacimiento: this.form.pais_nacimiento || 'México',
        municipio_nacimiento: this.form.municipio_nacimiento || null,
        estado_nacimiento: this.form.estado_nacimiento || null,
        nacionalidad: this.form.nacionalidad || null,
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
