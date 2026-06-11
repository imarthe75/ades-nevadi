/**
 * AlumnoPerfilComponent — Drawer lateral con perfil completo del alumno.
 * 4 tabs: Datos Personales | Académico | Salud | Contactos
 * Se abre desde el listado de alumnos al hacer clic en una fila.
 */
import { Component, Input, Output, EventEmitter, OnChanges, SimpleChanges, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DrawerModule } from 'primeng/drawer';
import { ButtonModule } from 'primeng/button';
import { TabsModule } from 'primeng/tabs';
import { TabList, Tab, TabPanels, TabPanel } from 'primeng/tabs';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { ApiService } from '../../../core/services/api.service';
import type { Estudiante, ContactoEmergencia } from '../../../core/models';

interface ExpedienteMedico {
  estudiante_id: string;
  tipo_sangre: string | null;
  alergias: string | null;
  medicamentos_autorizados: string | null;
  condiciones_cronicas: string | null;
  observaciones_generales: string | null;
  nss: string | null;
  discapacidad: string | null;
  seguro_medico_tipo: string | null;
  seguro_medico_numero: string | null;
  vacunas_al_dia: boolean;
  padecimiento_cronico: boolean;
  requiere_medicacion: boolean;
}

const TIPOS_SANGRE  = ['A+','A-','B+','B-','O+','O-','AB+','AB-'];
const ESTADOS_CIVIL = ['SOLTERO','CASADO','UNION_LIBRE','DIVORCIADO','VIUDO'];
const NIVELES_SOCIO = ['BAJO','MEDIO_BAJO','MEDIO','MEDIO_ALTO','ALTO'];
const PARENTESCOS   = ['PADRE','MADRE','TUTOR','ABUELO','ABUELA','TIO','TIA','HERMANO','HERMANA','OTRO'];
const NIVELESEST    = ['PRIMARIA','SECUNDARIA','BACHILLERATO','LICENCIATURA','MAESTRIA','DOCTORADO'];
const BECAS         = ['PRONABES','BECA_MANUTENCIÓN','SEIEM','BIENESTAR','EXCELENCIA','OTRA'];

@Component({
  selector: 'app-alumno-perfil',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    DrawerModule, ButtonModule, ToastModule, TagModule,
    TabsModule, TabList, Tab, TabPanels, TabPanel,
    InputTextModule, SelectModule, DatePickerModule,
    InputNumberModule, TextareaModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast />

    <p-drawer [(visible)]="visible" position="right"
      [style]="{width:'560px'}" [header]="alumno?.persona?.nombre_completo ?? 'Perfil del Alumno'"
      (onHide)="onCerrar()">

      @if (alumno) {
        <div class="perfil-meta">
          <span class="matricula-chip">{{ alumno.matricula }}</span>
          @if (expedienteMedico()?.tipo_sangre) {
            <p-tag [value]="expedienteMedico()!.tipo_sangre!" severity="danger" />
          }
        </div>

        <p-tabs [(value)]="tabActivo">
          <p-tablist>
            <p-tab value="personal"><i class="pi pi-user"></i> Personal</p-tab>
            <p-tab value="academico"><i class="pi pi-book"></i> Académico</p-tab>
            <p-tab value="salud"><i class="pi pi-heart"></i> Salud</p-tab>
            <p-tab value="contactos"><i class="pi pi-phone"></i> Contactos</p-tab>
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
                    style="font-family:monospace;text-transform:uppercase" />
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
                  <input pInputText [(ngModel)]="form.nacionalidad" />
                </div>
              </div>

              <div class="form-section">
                <h4 class="sec-title">Lugar de nacimiento</h4>
                <div class="form-row">
                  <label>Municipio</label>
                  <input pInputText [(ngModel)]="form.municipio_nacimiento" />
                </div>
                <div class="form-row">
                  <label>Estado</label>
                  <input pInputText [(ngModel)]="form.estado_nacimiento" />
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
              </div>
            </p-tabpanel>

            <!-- ── Tab 2: Académico ───────────────────────────────────── -->
            <p-tabpanel value="academico">
              <div class="form-section">
                <h4 class="sec-title">Escuela de procedencia</h4>
                <div class="form-row">
                  <label>Nombre escuela</label>
                  <input pInputText [(ngModel)]="form.escuela_procedencia" />
                </div>
                <div class="form-row">
                  <label>Clave CT</label>
                  <input pInputText [(ngModel)]="form.clave_ct_procedencia" maxlength="20" />
                </div>
                <div class="form-row">
                  <label>Promedio ingreso</label>
                  <p-inputnumber [(ngModel)]="form.promedio_procedencia"
                    [min]="0" [max]="10" [maxFractionDigits]="2" />
                </div>
              </div>

              <div class="form-section">
                <h4 class="sec-title">Beca</h4>
                <div class="form-row">
                  <label>Tipo de beca</label>
                  <p-select [options]="becas" [(ngModel)]="form.beca_tipo" [showClear]="true" />
                </div>
                <div class="form-row">
                  <label>Monto mensual</label>
                  <p-inputnumber [(ngModel)]="form.beca_monto" mode="currency" currency="MXN"
                    locale="es-MX" />
                </div>
              </div>

              <div class="form-section">
                <h4 class="sec-title">Datos socioeconómicos</h4>
                <div class="form-row">
                  <label>Nivel socieconómico</label>
                  <p-select [options]="nivelesSocio" [(ngModel)]="form.nivel_socioeconomico" [showClear]="true" />
                </div>
                <div class="form-row">
                  <label>Etnia / Origen</label>
                  <input pInputText [(ngModel)]="form.etnia" placeholder="Mestizo, Nahua, etc." />
                </div>
                <div class="form-row">
                  <label>Lengua indígena</label>
                  <input pInputText [(ngModel)]="form.lengua_indigena" />
                </div>
              </div>
            </p-tabpanel>

            <!-- ── Tab 3: Salud ──────────────────────────────────────── -->
            <p-tabpanel value="salud">
              <div class="form-section">
                <h4 class="sec-title">Identificación médica</h4>
                <div class="form-row">
                  <label>NSS (IMSS/ISSSTE)</label>
                  <input pInputText [(ngModel)]="medForm.nss" maxlength="11"
                    style="font-family:monospace" placeholder="11 dígitos" />
                </div>
                <div class="form-row">
                  <label>Tipo de sangre</label>
                  <p-select [options]="tiposSangre" [(ngModel)]="medForm.tipo_sangre" [showClear]="true" />
                </div>
                <div class="form-row">
                  <label>Seguro médico</label>
                  <p-select [options]="['IMSS','ISSSTE','PRIVADO','NINGUNO']"
                    [(ngModel)]="medForm.seguro_medico_tipo" [showClear]="true" />
                </div>
                <div class="form-row">
                  <label>Núm. póliza/afiliación</label>
                  <input pInputText [(ngModel)]="medForm.seguro_medico_numero" />
                </div>
              </div>
              <div class="form-section">
                <h4 class="sec-title">Condiciones</h4>
                <div class="form-row aligns-start">
                  <label>Alergias</label>
                  <textarea pTextarea [(ngModel)]="medForm.alergias" [rows]="3"
                    placeholder="Alergias conocidas..." style="width:100%"></textarea>
                </div>
                <div class="form-row aligns-start">
                  <label>Condiciones crónicas</label>
                  <textarea pTextarea [(ngModel)]="medForm.condiciones_cronicas" [rows]="2"
                    placeholder="Diabetes, asma, epilepsia..." style="width:100%"></textarea>
                </div>
                <div class="form-row aligns-start">
                  <label>Discapacidad / NEE</label>
                  <textarea pTextarea [(ngModel)]="medForm.discapacidad" [rows]="2"
                    placeholder="Necesidades educativas especiales..." style="width:100%"></textarea>
                </div>
                <div class="form-row aligns-start">
                  <label>Medicamentos autorizados</label>
                  <textarea pTextarea [(ngModel)]="medForm.medicamentos_autorizados" [rows]="2"
                    placeholder="Medicamentos que puede tomar en la escuela..." style="width:100%"></textarea>
                </div>
                <div class="form-row aligns-start">
                  <label>Observaciones</label>
                  <textarea pTextarea [(ngModel)]="medForm.observaciones_generales" [rows]="2"
                    style="width:100%"></textarea>
                </div>
              </div>
              <div class="form-section">
                <h4 class="sec-title">Estatus</h4>
                <div style="display:flex;flex-direction:column;gap:.5rem">
                  <label style="display:flex;align-items:center;gap:.5rem;font-size:.85rem">
                    <input type="checkbox" [(ngModel)]="medForm.vacunas_al_dia" />
                    Vacunas al día (Cartilla de Vacunación completa)
                  </label>
                  <label style="display:flex;align-items:center;gap:.5rem;font-size:.85rem">
                    <input type="checkbox" [(ngModel)]="medForm.padecimiento_cronico" />
                    Tiene padecimiento crónico
                  </label>
                  <label style="display:flex;align-items:center;gap:.5rem;font-size:.85rem">
                    <input type="checkbox" [(ngModel)]="medForm.requiere_medicacion" />
                    Requiere medicación durante clases
                  </label>
                </div>
              </div>
              <div style="display:flex;justify-content:flex-end;margin-top:1rem">
                <p-button label="Guardar expediente médico" icon="pi pi-heart"
                  severity="danger" [loading]="savingMedico()"
                  (onClick)="guardarExpedienteMedico()" />
              </div>
            </p-tabpanel>

            <!-- ── Tab 4: Contactos de emergencia ────────────────────── -->
            <p-tabpanel value="contactos">
              <div class="contactos-header">
                <span class="contactos-count">{{ contactos().length }} contacto(s) registrado(s)</span>
                <p-button label="Agregar contacto" icon="pi pi-plus" size="small"
                  (onClick)="abrirNuevoContacto()" />
              </div>

              @for (c of contactos(); track c.id) {
                <div class="contacto-card" [class.tutor-legal]="c.es_tutor_legal">
                  <div class="contacto-header">
                    <div>
                      <strong>{{ c.nombre_completo }}</strong>
                      @if (c.parentesco) { <span class="parentesco-tag">{{ c.parentesco }}</span> }
                    </div>
                    <div class="contacto-badges">
                      @if (c.es_tutor_legal) { <p-tag value="Tutor Legal" severity="success" /> }
                      @if (c.es_contacto_prim) { <p-tag value="Principal" severity="info" /> }
                      <p-button icon="pi pi-pencil" [text]="true" [rounded]="true" size="small"
                        (onClick)="editarContacto(c)" />
                      <p-button icon="pi pi-trash" [text]="true" [rounded]="true" size="small"
                        severity="danger" (onClick)="eliminarContacto(c.id)" />
                    </div>
                  </div>
                  <div class="contacto-data">
                    @if (c.telefono) { <span><i class="pi pi-phone"></i> {{ c.telefono }}</span> }
                    @if (c.email) { <span><i class="pi pi-envelope"></i> {{ c.email }}</span> }
                    @if (c.ocupacion) { <span><i class="pi pi-briefcase"></i> {{ c.ocupacion }}</span> }
                  </div>
                </div>
              }

              @if (editandoContacto()) {
                <div class="contacto-form">
                  <h4>{{ contactoEdit.id ? 'Editar' : 'Nuevo' }} contacto</h4>
                  <div class="form-row">
                    <label>Nombre completo *</label>
                    <input pInputText [(ngModel)]="contactoEdit.nombre_completo" />
                  </div>
                  <div class="form-row">
                    <label>Parentesco</label>
                    <p-select [options]="parentescos" [(ngModel)]="contactoEdit.parentesco" [showClear]="true" />
                  </div>
                  <div class="form-row">
                    <label>Teléfono</label>
                    <input pInputText [(ngModel)]="contactoEdit.telefono" maxlength="15" />
                  </div>
                  <div class="form-row">
                    <label>Teléfono alt.</label>
                    <input pInputText [(ngModel)]="contactoEdit.telefono_alt" maxlength="15" />
                  </div>
                  <div class="form-row">
                    <label>Email</label>
                    <input pInputText [(ngModel)]="contactoEdit.email" type="email" />
                  </div>
                  <div class="form-row">
                    <label>Ocupación</label>
                    <input pInputText [(ngModel)]="contactoEdit.ocupacion" />
                  </div>
                  <div class="form-row">
                    <label>Nivel estudios</label>
                    <p-select [options]="nivelesEst" [(ngModel)]="contactoEdit.nivel_estudios" [showClear]="true" />
                  </div>
                  <div class="form-row">
                    <label>RFC</label>
                    <input pInputText [(ngModel)]="contactoEdit.rfc" maxlength="13" style="font-family:monospace;text-transform:uppercase" />
                  </div>
                  <div class="form-row">
                    <label>¿Tutor legal?</label>
                    <input type="checkbox" [(ngModel)]="contactoEdit.es_tutor_legal" />
                  </div>
                  <div class="form-row">
                    <label>¿Contacto principal?</label>
                    <input type="checkbox" [(ngModel)]="contactoEdit.es_contacto_prim" />
                  </div>
                  <div class="form-btns">
                    <p-button label="Cancelar" severity="secondary" [text]="true" size="small"
                      (onClick)="cancelarContacto()" />
                    <p-button label="Guardar contacto" icon="pi pi-save" size="small"
                      [loading]="savingContacto()" (onClick)="guardarContacto()" />
                  </div>
                </div>
              }
            </p-tabpanel>
          </p-tabpanels>
        </p-tabs>

        <!-- Footer del drawer -->
        <div class="drawer-footer">
          <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="onCerrar()" />
          <p-button label="Guardar cambios" icon="pi pi-save" [loading]="saving()"
            (onClick)="guardar()" />
        </div>
      }
    </p-drawer>
  `,
  styles: [`
    .perfil-meta { display: flex; gap: .5rem; align-items: center; margin-bottom: 1rem; }
    .matricula-chip {
      font-family: monospace; font-size: .82rem; font-weight: 700;
      background: var(--surface-100); border: 1px solid var(--surface-300);
      padding: .2rem .6rem; border-radius: 4px; color: #475569;
    }
    .form-section { margin-bottom: 1.25rem; }
    .sec-title { font-size: .82rem; font-weight: 700; text-transform: uppercase;
      letter-spacing: .05em; color: var(--primary-color); margin: 0 0 .6rem; border-bottom: 1px solid var(--surface-200); padding-bottom: .3rem; }
    .form-row { display: grid; grid-template-columns: 140px 1fr; gap: .5rem; align-items: center; margin-bottom: .5rem; }
    .form-row.aligns-start { align-items: flex-start; padding-top: .2rem; }
    .form-row label { font-size: .82rem; color: var(--text-color-secondary); }

    .contactos-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: .75rem; }
    .contactos-count { font-size: .82rem; color: var(--text-color-secondary); }
    .contacto-card { border: 1px solid var(--surface-200); border-radius: 8px; padding: .75rem; margin-bottom: .6rem; }
    .contacto-card.tutor-legal { border-color: var(--green-300); background: var(--green-50); }
    .contacto-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: .3rem; }
    .contacto-badges { display: flex; gap: .3rem; align-items: center; }
    .parentesco-tag { font-size: .72rem; color: var(--text-color-secondary); margin-left: .3rem; }
    .contacto-data { display: flex; flex-wrap: wrap; gap: .75rem; font-size: .8rem; color: #475569; }
    .contacto-data i { margin-right: .2rem; font-size: .75rem; }
    .contacto-form { background: var(--surface-50); border: 1px solid var(--surface-200); border-radius: 8px; padding: 1rem; margin-top: .75rem; }
    .contacto-form h4 { margin: 0 0 .75rem; font-size: .9rem; }
    .form-btns { display: flex; justify-content: flex-end; gap: .5rem; margin-top: .75rem; }

    .drawer-footer { display: flex; justify-content: flex-end; gap: .75rem; margin-top: 1.5rem; padding-top: 1rem; border-top: 1px solid var(--surface-200); }
  `],
})
export class AlumnoPerfilComponent implements OnChanges {
  private readonly api = inject(ApiService);
  private readonly msg = inject(MessageService);

  @Input() alumno: Estudiante | null = null;
  @Input() visible = false;
  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() saved = new EventEmitter<void>();

  tabActivo = 'personal';
  form: any = {};

  contactos        = signal<ContactoEmergencia[]>([]);
  expedienteMedico = signal<ExpedienteMedico | null>(null);
  saving           = signal(false);
  savingContacto   = signal(false);
  savingMedico     = signal(false);
  editandoContacto = signal(false);
  contactoEdit: any = {};
  medForm: any = {};

  readonly tiposSangre  = TIPOS_SANGRE;
  readonly estadosCivil = ESTADOS_CIVIL;
  readonly nivelesSocio = NIVELES_SOCIO;
  readonly parentescos  = PARENTESCOS;
  readonly nivelesEst   = NIVELESEST;
  readonly becas        = BECAS;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['alumno'] && this.alumno) {
      this.initForm();
      this.cargarContactos();
      this.cargarExpedienteMedico();
    }
  }

  private initForm(): void {
    const a = this.alumno!;
    const p = a.persona ?? {};
    this.form = {
      // Persona
      nombre: (p as any).nombre ?? '',
      apellido_paterno: (p as any).apellido_paterno ?? '',
      apellido_materno: (p as any).apellido_materno ?? '',
      curp: (p as any).curp ?? '',
      genero: (p as any).genero ?? null,
      fecha_nacimiento: (p as any).fecha_nacimiento ? new Date((p as any).fecha_nacimiento) : null,
      telefono: (p as any).telefono ?? '',
      email_personal: (p as any).email_personal ?? '',
      estado_civil: (p as any).estado_civil ?? null,
      municipio_nacimiento: (p as any).municipio_nacimiento ?? '',
      estado_nacimiento: (p as any).estado_nacimiento ?? '',
      nacionalidad: (p as any).nacionalidad ?? 'MEXICANA',
      // Estudiante
      nss: a.nss ?? '',
      discapacidad: a.discapacidad ?? '',
      escuela_procedencia: a.escuela_procedencia ?? '',
      clave_ct_procedencia: a.clave_ct_procedencia ?? '',
      promedio_procedencia: a.promedio_procedencia ?? null,
      beca_tipo: a.beca_tipo ?? null,
      beca_monto: a.beca_monto ?? null,
      nivel_socioeconomico: a.nivel_socioeconomico ?? null,
      etnia: a.etnia ?? '',
      lengua_indigena: a.lengua_indigena ?? '',
    };
  }

  private cargarContactos(): void {
    if (!this.alumno?.id) return;
    this.api.get<ContactoEmergencia[]>('/contactos', { estudiante_id: this.alumno.id })
      .subscribe({ next: c => this.contactos.set(c), error: () => {} });
  }

  private cargarExpedienteMedico(): void {
    if (!this.alumno?.id) return;
    this.api.get<ExpedienteMedico>(`/expediente-medico/${this.alumno.id}`)
      .subscribe({
        next: exp => {
          this.expedienteMedico.set(exp);
          this.medForm = { ...exp };
        },
        error: () => {},
      });
  }

  guardarExpedienteMedico(): void {
    if (!this.alumno?.id) return;
    this.savingMedico.set(true);
    this.api.put(`/expediente-medico/${this.alumno.id}`, this.medForm).subscribe({
      next: (exp) => {
        this.expedienteMedico.set(exp as ExpedienteMedico);
        this.msg.add({ severity: 'success', summary: 'Guardado', detail: 'Expediente médico actualizado' });
        this.savingMedico.set(false);
      },
      error: e => {
        this.msg.add({ severity: 'error', summary: 'Error', detail: e.error?.detail ?? 'Error' });
        this.savingMedico.set(false);
      },
    });
  }

  guardar(): void {
    if (!this.alumno) return;
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
        nacionalidad: this.form.nacionalidad,
      },
      complementarios: {
        nss: this.form.nss || null,
        discapacidad: this.form.discapacidad || null,
        escuela_procedencia: this.form.escuela_procedencia || null,
        clave_ct_procedencia: this.form.clave_ct_procedencia || null,
        promedio_procedencia: this.form.promedio_procedencia,
        beca_tipo: this.form.beca_tipo,
        beca_monto: this.form.beca_monto,
        nivel_socioeconomico: this.form.nivel_socioeconomico,
        etnia: this.form.etnia || null,
        lengua_indigena: this.form.lengua_indigena || null,
      },
    };

    this.api.patch(`/alumnos/${this.alumno.id}`, payload).subscribe({
      next: () => {
        this.msg.add({ severity: 'success', summary: 'Guardado', detail: 'Perfil actualizado' });
        this.saving.set(false);
        this.saved.emit();
      },
      error: e => {
        this.msg.add({ severity: 'error', summary: 'Error', detail: e.error?.detail ?? 'Error al guardar' });
        this.saving.set(false);
      },
    });
  }

  abrirNuevoContacto(): void {
    this.contactoEdit = {
      nombre_completo: '', parentesco: null, telefono: '',
      telefono_alt: '', email: '', es_tutor_legal: false,
      es_contacto_prim: false, ocupacion: '', nivel_estudios: null, rfc: '',
    };
    this.editandoContacto.set(true);
  }

  editarContacto(c: ContactoEmergencia): void {
    this.contactoEdit = { ...c };
    this.editandoContacto.set(true);
  }

  cancelarContacto(): void {
    this.editandoContacto.set(false);
    this.contactoEdit = {};
  }

  guardarContacto(): void {
    if (!this.alumno?.persona?.id) return;
    this.savingContacto.set(true);

    const isNew = !this.contactoEdit.id;
    const payload = isNew
      ? { ...this.contactoEdit, persona_id: this.alumno.persona.id }
      : { ...this.contactoEdit };

    const req = isNew
      ? this.api.post('/contactos', payload)
      : this.api.patch(`/contactos/${this.contactoEdit.id}`, payload);

    req.subscribe({
      next: () => {
        this.editandoContacto.set(false);
        this.savingContacto.set(false);
        this.cargarContactos();
      },
      error: e => {
        this.msg.add({ severity: 'error', summary: 'Error', detail: e.error?.detail ?? 'Error' });
        this.savingContacto.set(false);
      },
    });
  }

  eliminarContacto(id: string): void {
    this.api.delete(`/contactos/${id}`).subscribe(() => this.cargarContactos());
  }

  onCerrar(): void {
    this.visible = false;
    this.visibleChange.emit(false);
  }

  private formatDate(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
  }
}
