import { Component, OnDestroy, OnInit, signal, inject, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { DatePickerModule } from 'primeng/datepicker';
import { InputNumberModule } from 'primeng/inputnumber';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { MessageService } from 'primeng/api';
import { ApexNotificationService } from 'apex-component-library';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';
import { AdesValidators } from '../../shared/validators/ades-validators';

interface ExpedienteLab {
  id: string;
  persona_id: string;
  nombre_completo?: string;
  tipo_contrato: string;
  fecha_contratacion: string;
  fecha_fin_contrato: string | null;
  salario_mensual: number;
  imss_numero: string | null;
  infonavit_numero: string | null;
  curp: string | null;
  rfc: string | null;
  cedula_profesional: string | null;
  nivel_estudios: string | null;
  especialidad: string | null;
  institucion_formacion: string | null;
  clave_ct: string | null;
  documentos_urls: Record<string, string>;
  row_version: number;
  fecha_creacion: string;
}

/** Sub-objeto `persona` embebido en cada fila de GET /api/v1/profesores (ProfesorQueryService#listar). */
interface PersonaResumen {
  nombre: string;
  apellido_paterno: string;
  apellido_materno: string | null;
}

/** Fila de `data` en GET /api/v1/profesores (ProfesorQueryService#listar devuelve { data, total }). */
interface ProfesorListado {
  id: string;
  persona_id: string;
  persona: PersonaResumen;
}

/**
 * Módulo de expediente laboral del personal docente y administrativo.
 * Permite consultar y gestionar documentos del historial laboral de cada empleado.
 * Requiere nivelAcceso 4 (AdminPlantel) o superior.
 */
@Component({
  selector: 'app-expediente-laboral',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule, TableModule, ButtonModule,
    DialogModule, InputTextModule, SelectModule, TagModule,
    ToastModule, DatePickerModule, InputNumberModule, AutoCompleteModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast />
    <div class="apex-page">
      <div class="apex-toolbar">
        <h2 class="apex-title">Expediente Laboral Digital</h2>
        <div class="apex-toolbar-actions">
          <input pInputText [(ngModel)]="busquedaNombre" (input)="onBusquedaNombre()"
            placeholder="Buscar por nombre de persona…" style="width:280px" aria-label="Buscar por nombre de persona"/>
          <p-button label="Nuevo Expediente" icon="pi pi-plus" size="small" (onClick)="abrirNuevo()" />
        </div>
      </div>

      <p-table [value]="expedientes()" [loading]="cargando()" [paginator]="true" [rows]="15"
        stripedRows styleClass="p-datatable-sm">
        <ng-template pTemplate="header">
          <tr>
            <th>Personal</th>
            <th style="width:130px">Tipo Contrato</th>
            <th style="width:120px">F. Contratación</th>
            <th style="width:120px">F. Fin</th>
            <th style="width:110px">Salario</th>
            <th style="width:100px">RFC</th>
            <th>Estudios</th>
            <th style="width:90px">Documentos</th>
            <th style="width:120px">Acciones</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-exp>
          <tr>
            <td class="font-medium">{{ exp.nombre_completo || exp.persona_id }}</td>
            <td><p-tag [value]="exp.tipo_contrato" [severity]="contratoSev(exp.tipo_contrato)" /></td>
            <td>{{ exp.fecha_contratacion ? (exp.fecha_contratacion | date:'dd/MM/yyyy') : '—' }}</td>
            <td>{{ exp.fecha_fin_contrato ? (exp.fecha_fin_contrato | date:'dd/MM/yyyy') : '—' }}</td>
            <td class="font-semibold">$ {{ exp.salario_mensual | number:'1.0-0' }}</td>
            <td class="text-sm font-mono">{{ exp.rfc ?? '—' }}</td>
            <td class="text-sm">
              {{ exp.nivel_estudios ?? '—' }}
              @if (exp.cedula_profesional) { <span class="text-gray-400"> · Céd. {{ exp.cedula_profesional }}</span> }
            </td>
            <td class="text-center">
              <span class="font-semibold text-primary-600">{{ docCount(exp.documentos_urls) }}</span>
            </td>
            <td>
              <div class="flex gap-1">
                <p-button icon="pi pi-eye" ariaLabel="Ver detalle"    size="small" [text]="true" severity="info"     (onClick)="ver(exp)"     pTooltip="Ver detalle" />
                <p-button icon="pi pi-file" ariaLabel="Documentos"   size="small" [text]="true" severity="secondary"(onClick)="verDocs(exp)" pTooltip="Documentos" />
                <p-button icon="pi pi-pencil" ariaLabel="Editar" size="small" [text]="true"                     (onClick)="editar(exp)"  pTooltip="Editar" />
              </div>
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr><td colspan="9" class="text-center py-4 text-gray-500">Sin expedientes. Busque por nombre de persona o cree uno nuevo.</td></tr>
        </ng-template>
      </p-table>
    </div>

    <!-- Dialog: Form -->
    <p-dialog [header]="editandoId ? 'Editar Expediente Laboral' : 'Nuevo Expediente Laboral'"
      [(visible)]="dialogForm" [modal]="true" [style]="{width:'680px'}" [draggable]="false">
      <div class="grid grid-cols-2 gap-3 p-2">
        @if (!editandoId) {
          <div class="col-span-2 flex flex-col gap-1">
            <label class="text-sm font-medium">Personal <span class="text-red-500">*</span></label>
            <p-autocomplete
              [(ngModel)]="personaSeleccionada"
              [suggestions]="personaSugerencias()"
              (completeMethod)="buscarPersona($event)"
              optionLabel="label"
              [forceSelection]="true"
              [delay]="300"
              placeholder="Escriba nombre del empleado…"
              style="width:100%" ariaLabel="Personal"/>
          </div>
        }
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Tipo Contrato</label>
          <p-select [options]="contratoOpts" [(ngModel)]="form.tipo_contrato" ariaLabel="Tipo Contrato"/>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">F. Contratación <span class="text-red-500">*</span></label>
          <p-datepicker [(ngModel)]="form.fecha_contratacion" dateFormat="yy-mm-dd" [showIcon]="true" ariaLabel="F. Contratación"/>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">F. Fin Contrato</label>
          <p-datepicker [(ngModel)]="form.fecha_fin_contrato" dateFormat="yy-mm-dd" [showIcon]="true" [showClear]="true" ariaLabel="F. Fin Contrato"/>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Salario Mensual</label>
          <p-inputNumber [(ngModel)]="form.salario_mensual" mode="currency" currency="MXN" locale="es-MX" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium" for="el-imss">No. IMSS</label>
          <input pInputText id="el-imss" [(ngModel)]="form.imss_numero" placeholder="11 dígitos"/>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium" for="el-infonavit">No. INFONAVIT</label>
          <input pInputText id="el-infonavit" [(ngModel)]="form.infonavit_numero"/>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium" for="el-curp">CURP</label>
          <input pInputText id="el-curp" [(ngModel)]="form.curp" placeholder="18 caracteres" class="uppercase"/>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium" for="el-rfc">RFC</label>
          <input pInputText id="el-rfc" [(ngModel)]="form.rfc" placeholder="13 caracteres" class="uppercase"/>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium" for="el-cedula">Cédula Profesional</label>
          <input pInputText id="el-cedula" [(ngModel)]="form.cedula_profesional"/>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Nivel de Estudios</label>
          <p-select [options]="estudiosOpts" [(ngModel)]="form.nivel_estudios" [showClear]="true" ariaLabel="Nivel de Estudios"/>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium" for="el-especialidad">Especialidad</label>
          <input pInputText id="el-especialidad" [(ngModel)]="form.especialidad"/>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium" for="el-institucion">Institución de Formación</label>
          <input pInputText id="el-institucion" [(ngModel)]="form.institucion_formacion" placeholder="UNAM, IPN, UAEMEX…"/>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium" for="el-clave-ct">Clave CT (SEP)</label>
          <input pInputText id="el-clave-ct" [(ngModel)]="form.clave_ct" placeholder="15MEP00…"/>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium" for="el-clave-issste">Clave ISSSTE</label>
          <input pInputText id="el-clave-issste" [(ngModel)]="form.clave_issste"/>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dialogForm=false" />
        <p-button [label]="editandoId ? 'Guardar cambios' : 'Crear expediente'" icon="pi pi-save" ariaLabel="Guardar"
          [loading]="guardando()" (onClick)="guardar()" />
      </ng-template>
    </p-dialog>

    <!-- Dialog: Documentos -->
    <p-dialog header="Documentos del Expediente" [(visible)]="dialogDocs" [modal]="true"
      [style]="{width:'520px'}" [draggable]="false">
      @if (seleccionado()) {
        <div class="p-3">
          @for (entry of docEntries(seleccionado()!.documentos_urls); track entry[0]) {
            <div class="flex items-center justify-between py-2 border-b border-gray-100">
              <span class="capitalize text-sm font-medium">{{ entry[0] }}</span>
              <a [href]="entry[1]" target="_blank" class="text-primary-600 text-sm pi pi-external-link"> Ver</a>
            </div>
          }
          @if (docEntries(seleccionado()!.documentos_urls).length === 0) {
            <p class="text-gray-500 text-sm text-center py-4">Sin documentos registrados.</p>
          }
          <div class="mt-4 border-t pt-3 flex gap-2">
            <p-select [options]="docTipoOpts" [(ngModel)]="nuevoDocTipo" placeholder="Tipo doc." style="flex:1" ariaLabel="Tipo doc" />
            <input pInputText [(ngModel)]="nuevoDocUrl" placeholder="URL en MinIO…" style="flex:2" aria-label="URL en MinIO"/>
            <p-button icon="pi pi-plus" ariaLabel="Agregar documento" size="small" (onClick)="agregarDoc()" />
          </div>
        </div>
      }
    </p-dialog>
  `,
  styles: [`
    .apex-page { padding: 1rem; }
    .apex-toolbar { display:flex; align-items:center; justify-content:space-between; margin-bottom:1rem; flex-wrap:wrap; gap:.5rem; }
    .apex-title { margin:0; font-size:1.25rem; font-weight:600; }
    .apex-toolbar-actions { display:flex; gap:.5rem; align-items:center; flex-wrap:wrap; }
  `],
})
export class ExpedienteLaboralComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private api = inject(ApiService);
  private notify = inject(ApexNotificationService);

  expedientes   = signal<ExpedienteLab[]>([]);
  cargando      = signal(false);
  guardando     = signal(false);
  seleccionado  = signal<ExpedienteLab | null>(null);
  personaSugerencias = signal<{ label: string; value: string }[]>([]);
  personaSeleccionada: { label: string; value: string } | null = null;

  dialogForm  = false;
  dialogDocs  = false;
  editandoId  = '';
  busquedaNombre = '';
  nuevoDocTipo = '';
  nuevoDocUrl  = '';

  private busquedaTimer: ReturnType<typeof setTimeout> | null = null;

  form: any = this.resetForm();

  contratoOpts = [
    { label: 'Indefinido',    value: 'INDEFINIDO' },
    { label: 'Determinado',   value: 'DETERMINADO' },
    { label: 'Honorarios',    value: 'HONORARIOS' },
    { label: 'Comisión',      value: 'COMISION' },
  ];
  estudiosOpts = [
    { label: 'Licenciatura',    value: 'LICENCIATURA' },
    { label: 'Maestría',        value: 'MAESTRIA' },
    { label: 'Doctorado',       value: 'DOCTORADO' },
    { label: 'Normal Básica',   value: 'NORMAL_BASICA' },
    { label: 'Bachillerato',    value: 'BACHILLERATO' },
    { label: 'Otro',            value: 'OTRO' },
  ];
  docTipoOpts = [
    { label: 'Contrato',    value: 'contrato' },
    { label: 'Título',      value: 'titulo' },
    { label: 'Cédula',      value: 'cedula' },
    { label: 'NSS',         value: 'nss' },
    { label: 'ID oficial',  value: 'identificacion' },
    { label: 'Acta nac.',   value: 'acta_nacimiento' },
    { label: 'CURP doc.',   value: 'curp_doc' },
    { label: 'IMSS',        value: 'imss' },
    { label: 'Otro',        value: 'otro' },
  ];

  ngOnInit() { this.cargar(); }

  onBusquedaNombre() {
    if (this.busquedaTimer) clearTimeout(this.busquedaTimer);
    this.busquedaTimer = setTimeout(() => this.cargar(), 400);
  }

  cargar() {
    this.cargando.set(true);
    const params: any = {};
    if (this.busquedaNombre) params['q'] = this.busquedaNombre;
    this.api.get<ExpedienteLab[]>('/expediente-laboral', params).pipe(takeUntil(this.destroy$)).subscribe({
      next: d => { this.expedientes.set(d); this.cargando.set(false); },
      error: () => { this.cargando.set(false); this.notify.error('Error al cargar expedientes'); },
    });
  }

  buscarPersona(event: { query: string }) {
    if (!event.query || event.query.length < 2) { this.personaSugerencias.set([]); return; }
    this.api.get<{ data: ProfesorListado[]; total: number }>('/profesores', { buscar: event.query }).pipe(takeUntil(this.destroy$)).subscribe({
      next: res => {
        const data = res?.data ?? [];
        this.personaSugerencias.set(data.map((p) => ({
          label: [p.persona?.nombre, p.persona?.apellido_paterno, p.persona?.apellido_materno].filter(Boolean).join(' '),
          value: p.persona_id ?? p.id,
        })));
      },
      error: () => this.personaSugerencias.set([]),
    });
  }

  abrirNuevo() {
    this.editandoId = '';
    this.form = this.resetForm();
    this.personaSeleccionada = null;
    this.dialogForm = true;
  }

  editar(exp: ExpedienteLab) {
    this.editandoId = exp.id;
    this.form = {
      tipo_contrato: exp.tipo_contrato, salario_mensual: exp.salario_mensual,
      imss_numero: exp.imss_numero, infonavit_numero: exp.infonavit_numero,
      curp: exp.curp, rfc: exp.rfc, cedula_profesional: exp.cedula_profesional,
      nivel_estudios: exp.nivel_estudios, especialidad: exp.especialidad,
      institucion_formacion: exp.institucion_formacion, clave_ct: exp.clave_ct,
    };
    this.dialogForm = true;
  }

  guardar() {
    if (this.form.curp && !AdesValidators.curpValido(this.form.curp)) {
      this.notify.warning('CURP inválido', 'Formato esperado: AAAA000000HAAAAA00');
      return;
    }
    if (this.form.rfc && !AdesValidators.rfcValido(this.form.rfc)) {
      this.notify.warning('RFC inválido', 'Formato esperado: AAAA000000AAA');
      return;
    }
    this.guardando.set(true);
    let req;
    if (this.editandoId) {
      req = this.api.patch<ExpedienteLab>(`/expediente-laboral/${this.editandoId}`, this.form);
    } else {
      if (!this.personaSeleccionada?.value) {
        this.guardando.set(false);
        this.notify.warning('Seleccione una persona');
        return;
      }
      req = this.api.post<ExpedienteLab>('/expediente-laboral', {
        ...this.form,
        persona_id: this.personaSeleccionada.value,
        fecha_contratacion: this.toDateStr(this.form.fecha_contratacion),
        fecha_fin_contrato: this.form.fecha_fin_contrato ? this.toDateStr(this.form.fecha_fin_contrato) : null,
      });
    }
    req.pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.guardando.set(false); this.dialogForm = false;
        this.notify.success(this.editandoId ? 'Expediente actualizado' : 'Expediente creado');
        this.cargar();
      },
      error: (e: any) => { this.guardando.set(false); this.notify.error(e.error?.detail ?? 'Error al guardar'); },
    });
  }

  ver(exp: ExpedienteLab) { this.seleccionado.set(exp); this.dialogDocs = true; }
  verDocs(exp: ExpedienteLab) { this.seleccionado.set(exp); this.dialogDocs = true; }

  agregarDoc() {
    if (!this.seleccionado() || !this.nuevoDocTipo || !this.nuevoDocUrl) return;
    this.api.post<ExpedienteLab>(`/expediente-laboral/${this.seleccionado()!.id}/documento`,
      { tipo_documento: this.nuevoDocTipo, url: this.nuevoDocUrl }
    ).pipe(takeUntil(this.destroy$)).subscribe({
      next: (updated) => {
        this.seleccionado.set(updated); this.nuevoDocTipo = ''; this.nuevoDocUrl = '';
        this.notify.success('Documento registrado');
      },
      error: () => this.notify.error('Error al agregar documento'),
    });
  }

  contratoSev(tipo: string): 'success' | 'warn' | 'danger' | 'secondary' {
    const m: Record<string, any> = { INDEFINIDO:'success', DETERMINADO:'warn', HONORARIOS:'secondary', COMISION:'secondary' };
    return m[tipo] ?? 'secondary';
  }
  docCount(docs: Record<string, string>): number { return Object.keys(docs ?? {}).length; }
  docEntries(docs: Record<string, string>): [string, string][] { return Object.entries(docs ?? {}); }
  private toDateStr(d: Date | null): string { return d ? d.toISOString().split('T')[0] : ''; }
  private resetForm() {
    return { tipo_contrato:'INDEFINIDO', fecha_contratacion:null, fecha_fin_contrato:null,
             salario_mensual:0, imss_numero:'', infonavit_numero:'', curp:'', rfc:'',
             cedula_profesional:'', nivel_estudios:'', especialidad:'', institucion_formacion:'', clave_ct:'', clave_issste:'' };
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
