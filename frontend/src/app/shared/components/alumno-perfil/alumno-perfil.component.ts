/**
 * AlumnoPerfilComponent — Drawer lateral con perfil completo del alumno.
 * 5 tabs: Datos Personales | Académico | Salud | Contactos | Bajas
 * Se abre desde el listado de alumnos al hacer clic en una fila.
 */
import { Component, Input, Output, EventEmitter, OnChanges, OnInit, SimpleChanges, inject, signal, computed } from '@angular/core';
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
import { DomicilioComponent } from '../domicilio/domicilio.component';
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
    DomicilioComponent,
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
          @if (!alumno.is_active) {
            <p-tag value="BAJA" severity="danger" />
          } @else {
            <p-tag value="ACTIVO" severity="success" />
          }
        </div>

        <p-tabs [(value)]="tabActivo">
          <p-tablist>
            <p-tab value="personal"><i class="pi pi-user"></i> Personal</p-tab>
            <p-tab value="domicilio"><i class="pi pi-map-marker"></i> Domicilio</p-tab>
            <p-tab value="academico"><i class="pi pi-book"></i> Académico</p-tab>
            <p-tab value="salud"><i class="pi pi-heart"></i> Salud</p-tab>
            <p-tab value="contactos"><i class="pi pi-users"></i> Familia</p-tab>
            <p-tab value="bajas"><i class="pi pi-user-minus"></i> Bajas</p-tab>
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
                <h4 class="sec-title">Lugar de nacimiento</h4>
                <div class="form-row">
                  <label>País</label>
                  <p-select [options]="paises()"
                    [ngModel]="paisNacId()" (ngModelChange)="onPaisNacChange($event)"
                    optionLabel="nombre_pais" optionValue="id"
                    [filter]="true" filterBy="nombre_pais"
                    [showClear]="true" placeholder="Seleccionar país…"
                    style="width:100%" />
                </div>
                @if (esMexicano()) {
                  <div class="form-row">
                    <label>Estado</label>
                    <p-select [options]="estadosMex()"
                      [(ngModel)]="estadoNacId"
                      optionLabel="nombre_estado" optionValue="id"
                      [filter]="true" filterBy="nombre_estado"
                      [showClear]="true" placeholder="Seleccionar estado…"
                      (onChange)="onEstadoNacChange($event.value)"
                      style="width:100%" />
                  </div>
                  <div class="form-row">
                    <label>Municipio</label>
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

            <!-- ── Tab 2: Domicilio y Contactos ─────────────────────────── -->
            <p-tabpanel value="domicilio">
              <app-domicilio [personaId]="alumno?.persona_id ?? alumno?.persona?.id ?? null" />
            </p-tabpanel>

            <!-- ── Tab 3: Académico ───────────────────────────────────── -->
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
                  <label>Etnia / Autoidentificación</label>
                  <input pInputText [(ngModel)]="form.etnia" placeholder="Ej. Náhuatl, Otomí, Mestizo…" />
                </div>
                <div class="form-row">
                  <label>Lengua indígena (INALI)</label>
                  <p-select [options]="lenguasIndigenas()" [(ngModel)]="form.lengua_indigena_id"
                    optionLabel="label" optionValue="value"
                    [showClear]="true" [filter]="true" filterBy="label"
                    placeholder="Seleccionar agrupación lingüística…" />
                </div>
                <div class="form-row">
                  <label>Nivel de inglés (CEFR)</label>
                  <p-select [options]="nivelesIngles()" [(ngModel)]="form.nivel_ingles_id"
                    optionLabel="label" optionValue="value"
                    [showClear]="true"
                    placeholder="Seleccionar nivel…" />
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
                    <label>Nacionalidad</label>
                    <p-select
                      [options]="nacionalidades()"
                      [(ngModel)]="contactoEdit.nacionalidad"
                      [filter]="true"
                      filterPlaceholder="Buscar..."
                      [showClear]="true"
                      placeholder="Seleccionar..."
                      style="width:100%" />
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

            <!-- ── Tab 5: Bajas y Reactivaciones ────────────────────── -->
            <p-tabpanel value="bajas">
              <div class="form-section">
                @if (alumno.is_active) {
                  <h4 class="sec-title">Registrar Baja del Alumno</h4>
                  <div class="form-row">
                    <label>Tipo de baja *</label>
                    <p-select [options]="['TEMPORAL', 'DEFINITIVA', 'TRASLADO', 'DESERCION']" [(ngModel)]="bajaForm.tipo_baja" />
                  </div>
                  <div class="form-row">
                    <label>Fecha efectiva *</label>
                    <p-datepicker [(ngModel)]="bajaForm.fecha_efectiva" dateFormat="dd/mm/yy" />
                  </div>
                  <div class="form-row aligns-start">
                    <label>Motivo</label>
                    <textarea pTextarea [(ngModel)]="bajaForm.motivo" rows="2" style="width:100%"></textarea>
                  </div>
                  <div class="form-row aligns-start">
                    <label>Observaciones</label>
                    <textarea pTextarea [(ngModel)]="bajaForm.observaciones" rows="2" style="width:100%"></textarea>
                  </div>
                  <div style="display:flex;justify-content:flex-end;margin-top:1rem">
                    <p-button label="Registrar Baja" icon="pi pi-user-minus" severity="danger" (onClick)="ejecutarBaja()" />
                  </div>
                } @else {
                  <h4 class="sec-title">Reactivación de Alumno</h4>
                  <p class="text-sm text-gray-600 mb-3">El alumno se encuentra actualmente en estado de <strong>BAJA</strong>.</p>
                  
                  @if (ultimaBaja()) {
                    <div class="contacto-card" style="border-color:var(--red-300); background:var(--red-50); margin-bottom: 1rem;">
                      <div><strong>Tipo de baja:</strong> {{ ultimaBaja().tipo_baja }}</div>
                      <div><strong>Fecha efectiva:</strong> {{ ultimaBaja().fecha_efectiva | date:'dd/MM/yyyy' }}</div>
                      @if (ultimaBaja().motivo) { <div><strong>Motivo:</strong> {{ ultimaBaja().motivo }}</div> }
                    </div>
                  }
                  
                  <div style="display:flex;justify-content:flex-end">
                    <p-button label="Reactivar Alumno" icon="pi pi-user-plus" severity="success" (onClick)="ejecutarReactivacion()" />
                  </div>
                }
              </div>
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
export class AlumnoPerfilComponent implements OnInit, OnChanges {
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

  // Bajas Form
  bajaForm = {
    tipo_baja: 'TEMPORAL',
    fecha_efectiva: new Date(),
    motivo: '',
    observaciones: ''
  };
  ultimaBaja = signal<any | null>(null);

  readonly tiposSangre  = TIPOS_SANGRE;
  readonly estadosCivil = ESTADOS_CIVIL;
  readonly nivelesSocio = NIVELES_SOCIO;
  readonly parentescos  = PARENTESCOS;
  readonly nivelesEst   = NIVELESEST;
  readonly becas        = BECAS;

  nacionalidades    = signal<string[]>([]);
  lenguasIndigenas  = signal<{ label: string; value: string }[]>([]);
  nivelesIngles     = signal<{ label: string; value: string }[]>([]);
  paises            = signal<any[]>([]);
  estadosMex        = signal<any[]>([]);
  municipiosMex     = signal<any[]>([]);
  paisNacId         = signal('');
  estadoNacId       = '';
  municipioNacId    = '';
  esMexicano        = computed(() => {
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
    this.api.get<any[]>('/catalogs/lenguas-indigenas').subscribe({
      next: list => this.lenguasIndigenas.set(list.map(l => ({
        label: l.autonym ? `${l.agrupacion} (${l.autonym})` : l.agrupacion,
        value: l.id,
      }))),
      error: () => {},
    });
    this.api.get<any[]>('/catalogs/niveles-ingles').subscribe({
      next: list => this.nivelesIngles.set(list.map(n => ({
        label: `${n.nivel} — ${n.nombre}`,
        value: n.id,
      }))),
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
        // Pre-seleccionar país si ya hay uno guardado
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
    // Si cambia a no-México, limpiar los LOVs de estado/municipio
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
    if (changes['alumno'] && this.alumno) {
      this.initForm();
      this.cargarContactos();
      this.cargarExpedienteMedico();
      this.cargarUltimaBaja();
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
      pais_nacimiento: (p as any).pais_nacimiento ?? 'México',
      municipio_nacimiento: (p as any).municipio_nacimiento ?? '',
      estado_nacimiento: (p as any).estado_nacimiento ?? '',
      nacionalidad: (p as any).nacionalidad ?? 'Mexicana',
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
      lengua_indigena_id: a.lengua_indigena_id ?? null,
      nivel_ingles_id: a.nivel_ingles_id ?? null,
    };
    // Pre-seleccionar estado/municipio si el catálogo ya cargó
    this.estadoNacId = '';
    this.municipioNacId = '';
    this.municipiosMex.set([]);
    const paisGuardado = (p as any).pais_nacimiento ?? 'México';
    const paisObj = this.paises().find((x: any) => x.nombre_pais === paisGuardado);
    this.paisNacId.set(paisObj?.id ?? '');
    if (paisGuardado === 'México' || !paisGuardado) {
      const estadoGuardado = (p as any).estado_nacimiento;
      if (estadoGuardado && this.estadosMex().length > 0) {
        const est = this.estadosMex().find((e: any) => e.nombre_estado === estadoGuardado);
        if (est) { this.estadoNacId = est.id; this.cargarMunicipiosNac(est.id, false); }
      }
    }
    this.bajaForm = {
      tipo_baja: 'TEMPORAL',
      fecha_efectiva: new Date(),
      motivo: '',
      observaciones: ''
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

  cargarUltimaBaja(): void {
    if (!this.alumno?.id) return;
    this.api.get<any[]>('/procesos/bajas').subscribe({
      next: list => {
        const userBajas = list.filter(b => b.estudiante_id === this.alumno!.id);
        if (userBajas.length > 0) {
          this.ultimaBaja.set(userBajas[0]);
        } else {
          this.ultimaBaja.set(null);
        }
      },
      error: () => {}
    });
  }

  ejecutarBaja(): void {
    if (!this.alumno?.id) return;
    const activeInscription = (this.alumno as any).inscripciones?.find((i: any) => i.is_active);
    const payload = {
      estudiante_id: this.alumno.id,
      inscripcion_id: activeInscription ? activeInscription.id : null,
      tipo_baja: this.bajaForm.tipo_baja,
      motivo: this.bajaForm.motivo || null,
      fecha_efectiva: this.formatDate(this.bajaForm.fecha_efectiva),
      observaciones: this.bajaForm.observaciones || null
    };
    this.api.post('/procesos/bajas', payload).subscribe({
      next: (r: any) => {
        this.msg.add({ severity: 'success', summary: 'Baja registrada', detail: r.message });
        this.alumno!.is_active = false;
        this.cargarUltimaBaja();
        this.saved.emit();
      },
      error: e => {
        this.msg.add({ severity: 'error', summary: 'Error', detail: e.error?.detail ?? 'Error' });
      }
    });
  }

  ejecutarReactivacion(): void {
    const baja = this.ultimaBaja();
    if (!baja) {
      this.msg.add({ severity: 'warn', summary: 'No se puede reactivar', detail: 'No hay registro de baja activo' });
      return;
    }
    this.api.post(`/procesos/bajas/${baja.id}/reactivar`, {}).subscribe({
      next: (r: any) => {
        this.msg.add({ severity: 'success', summary: 'Reactivado', detail: r.message });
        this.alumno!.is_active = true;
        this.ultimaBaja.set(null);
        this.saved.emit();
      },
      error: e => {
        this.msg.add({ severity: 'error', summary: 'Error', detail: e.error?.detail ?? 'Error' });
      }
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
        pais_nacimiento: this.form.pais_nacimiento || 'México',
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
        lengua_indigena_id: this.form.lengua_indigena_id || null,
        nivel_ingles_id: this.form.nivel_ingles_id || null,
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
      nacionalidad: 'Mexicana',
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
