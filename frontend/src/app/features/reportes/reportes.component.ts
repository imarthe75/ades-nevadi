/**
 * Módulo de Reportes — FASE 18 (Carbone)
 *
 * Tabs:
 *   1. Generador — selecciona alumno + plantilla → descarga PDF
 *   2. Plantillas — gestión de plantillas DOCX/XLSX (solo admin)
 *   3. Estado    — disponibilidad del servicio Carbone
 */
import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TabsModule } from 'primeng/tabs';
import { TooltipModule } from 'primeng/tooltip';
import { FileUploadModule } from 'primeng/fileupload';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { AuthService } from '../../core/services/auth.service';
import { ApexNotificationService } from 'apex-component-library';

interface Plantilla {
  id: string;
  nombre: string;
  tipo_documento: string;
  descripcion: string;
  extension: string;
  tamano_bytes: number;
  fccreacion: string;
}

interface Alumno {
  id: string;
  persona?: { nombre: string; apellido_paterno: string; apellido_materno?: string };
  matricula?: string;
}

const TIPOS_DOC = [
  { label: 'Boleta Oficial',           value: 'BOLETA' },
  { label: 'Constancia de Estudios',   value: 'CONSTANCIA_ESTUDIOS' },
  { label: 'Constancia Calificaciones',value: 'CONSTANCIA_CALIFICACIONES' },
  { label: 'Kardex Académico',         value: 'KARDEX' },
  { label: 'Reporte de Conducta',      value: 'CONDUCTA' },
  { label: 'Certificado',              value: 'CERTIFICADO' },
  { label: 'Genérico',                 value: 'GENERICO' },
];

@Component({
  selector: 'app-reportes',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    ButtonModule, SelectModule, TableModule, TagModule,
    TabsModule,
    TooltipModule, FileUploadModule, MessageModule, ProgressSpinnerModule,
  ],
  template: `

    <div class="page-header">
      <div>
        <h2>Generador de Reportes</h2>
        <p class="subtitle">Reportes PDF a partir de plantillas Word/Excel — Carbone</p>
      </div>
      <div style="display:flex;gap:.5rem">
        <p-tag [value]="carboneStatus()" [severity]="carboneOnline() ? 'success' : 'danger'" />
      </div>
    </div>

    <p-tabs [value]="tabActivo()">
      <p-tablist>
        <p-tab value="generar"><i class="pi pi-file-pdf"></i> Reporte Individual</p-tab>
        <p-tab value="grupo"><i class="pi pi-users"></i> Boletas Grupo</p-tab>
        <p-tab value="plantillas"><i class="pi pi-folder"></i> Plantillas</p-tab>
        <p-tab value="subir"><i class="pi pi-upload"></i> Subir Plantilla</p-tab>
      </p-tablist>

      <p-tabpanels>

        <!-- ── Generador ── -->
        <p-tabpanel value="generar">
          @if (!carboneOnline()) {
            <p-message severity="warn"
              text="Servicio Carbone no disponible. Verificar que el contenedor ades-carbone esté corriendo." />
          } @else {
            <div class="gen-form">
              <div class="form-row">
                <label>Alumno</label>
                <p-select
                  [options]="alumnos()"
                  [(ngModel)]="alumnoSel"
                  [filter]="true"
                  filterBy="label"
                  optionLabel="label"
                  optionValue="id"
                  placeholder="Buscar alumno..."
                  style="width:320px"
                  (onChange)="onAlumnoChange($event.value)" />
              </div>

              <div class="form-row">
                <label>Plantilla</label>
                <p-select
                  [options]="plantillas()"
                  [(ngModel)]="plantillaSel"
                  optionLabel="nombre"
                  optionValue="id"
                  placeholder="Seleccionar plantilla..."
                  style="width:320px" 
 [filter]="true" filterPlaceholder="Buscar..."/>
              </div>

              <div class="form-row">
                <label>Tipo de documento</label>
                <p-select
                  [options]="tiposDoc"
                  [(ngModel)]="tipoDocSel"
                  optionLabel="label"
                  optionValue="value"
                  style="width:220px" 
 [filter]="true" filterPlaceholder="Buscar..."/>
              </div>

              @if (tipoDocSel === 'BOLETA') {
                <div class="form-row">
                  <label>Periodo</label>
                  <p-select
                    [options]="[{label:'Todos los periodos',value:null},{label:'Periodo 1',value:1},{label:'Periodo 2',value:2},{label:'Periodo 3',value:3}]"
                    [(ngModel)]="periodoSel"
                    optionLabel="label"
                    optionValue="value"
                    style="width:200px" 
 [filter]="true" filterPlaceholder="Buscar..."/>
                </div>
              }

              <div class="form-row">
                <label></label>
                <p-button
                  label="Generar PDF"
                  icon="pi pi-file-pdf"
                  [loading]="generando()"
                  [disabled]="!alumnoSel || !plantillaSel"
                  (onClick)="generarPdf()" />
              </div>
            </div>
          }
        </p-tabpanel>

        <!-- ── Boletas del Grupo (Stirling-PDF merge) ── -->
        <p-tabpanel value="grupo">
          <p style="font-size:.83rem;color:var(--text-color-secondary);margin:0 0 1rem">
            Genera la boleta de <strong>todos los alumnos del grupo</strong> con Carbone y las fusiona en un único PDF
            usando Stirling-PDF. Si Stirling-PDF no está disponible, descarga un ZIP individual.
          </p>

          @if (!carboneOnline()) {
            <p-message severity="warn" text="Carbone no disponible — los PDFs no pueden generarse." />
          } @else {
            <div class="gen-form">
              <div class="form-row">
                <label>Grupo</label>
                <p-select [options]="grupos()" [(ngModel)]="grupoSel"
                  optionLabel="label" optionValue="id"
                  [filter]="true" filterBy="label"
                  placeholder="Seleccionar grupo..."
                  style="width:320px" />
              </div>

              <div class="form-row">
                <label>Plantilla boleta</label>
                <p-select [options]="plantillasBoleta()"
                  [(ngModel)]="plantillaGrupoSel"
                  optionLabel="nombre" optionValue="id"
                  placeholder="Plantilla de tipo BOLETA..."
                  style="width:320px" 
 [filter]="true" filterPlaceholder="Buscar..."/>
              </div>

              <div class="form-row">
                <label>Periodo</label>
                <p-select
                  [options]="[{label:'Todos',value:null},{label:'Periodo 1',value:1},{label:'Periodo 2',value:2},{label:'Periodo 3',value:3}]"
                  [(ngModel)]="periodoGrupoSel"
                  optionLabel="label" optionValue="value"
                  style="width:180px" 
 [filter]="true" filterPlaceholder="Buscar..."/>
              </div>

              <div class="form-row">
                <label>Marca de agua</label>
                <p-toggleswitch [(ngModel)]="marcaAgua" />
                <span style="font-size:.78rem;color:var(--text-muted);margin-left:.5rem">Añadir "INSTITUTO NEVADI" como marca de agua</span>
              </div>

              <div class="form-row">
                <label></label>
                <p-button label="Generar PDF del grupo"
                  icon="pi pi-file-pdf"
                  [loading]="generandoGrupo()"
                  [disabled]="!grupoSel || !plantillaGrupoSel"
                  (onClick)="generarBoletasGrupo()" />
              </div>

              <p-message severity="info"
                text="El proceso puede tardar 1-3 minutos según el tamaño del grupo. El PDF se descargará automáticamente cuando esté listo." />
            </div>
          }
        </p-tabpanel>

        <!-- ── Plantillas ── -->
        <p-tabpanel value="plantillas">
          <p-table [value]="plantillas()" [loading]="cargandoPlantillas()"
            styleClass="p-datatable-sm p-datatable-striped">
            <ng-template pTemplate="header">
              <tr>
                <th>Nombre</th>
                <th style="width:150px">Tipo</th>
                <th style="width:70px">Ext.</th>
                <th style="width:90px">Tamaño</th>
                <th style="width:130px">Fecha</th>
                @if (ctx.nivelAcceso() <= 1) {
                  <th style="width:60px"></th>
                }
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-p>
              <tr>
                <td>{{ p.nombre }}</td>
                <td><p-tag [value]="p.tipo_documento" severity="info" /></td>
                <td style="font-size:.8rem"><code>{{ p.extension }}</code></td>
                <td style="font-size:.78rem">{{ (p.tamano_bytes/1024).toFixed(0) }} KB</td>
                <td style="font-size:.78rem">{{ p.fccreacion | date:'dd/MM/yy HH:mm' }}</td>
                @if (ctx.nivelAcceso() <= 1) {
                  <td>
                    <p-button icon="pi pi-trash" [rounded]="true" [text]="true"
                      severity="danger" pTooltip="Eliminar"
                      (onClick)="eliminarPlantilla(p.id)" />
                  </td>
                }
              </tr>
            </ng-template>
            <ng-template pTemplate="emptymessage">
              <tr><td [colSpan]="6" style="text-align:center;padding:2rem;color:var(--text-muted)">
                Sin plantillas — sube una plantilla DOCX o XLSX en la pestaña "Subir Plantilla"
              </td></tr>
            </ng-template>
          </p-table>
        </p-tabpanel>

        <!-- ── Subir Plantilla (admin) ── -->
        <p-tabpanel value="subir">
          @if (ctx.nivelAcceso() <= 1) {
            <div class="upload-form">
              <div class="form-grid">
                <label>Nombre de la plantilla</label>
                <input pInputText [(ngModel)]="nuevaPlantillaNombre" placeholder="ej. Boleta Primaria 2026" />
                <label>Tipo de documento</label>
                <p-select [options]="tiposDoc" [(ngModel)]="nuevaPlantillaTipo"
                  optionLabel="label" optionValue="value" style="width:100%" 
 [filter]="true" filterPlaceholder="Buscar..."/>
                <label>Descripción</label>
                <input pInputText [(ngModel)]="nuevaPlantillaDesc" placeholder="Descripción opcional" />
              </div>

              <div style="margin-top:1.5rem">
                <p-fileupload
                  mode="advanced"
                  [customUpload]="true"
                  (uploadHandler)="onUpload($event)"
                  accept=".docx,.xlsx,.odt,.ods"
                  [maxFileSize]="20000000"
                  [auto]="false"
                  chooseLabel="Seleccionar archivo"
                  uploadLabel="Subir plantilla"
                  cancelLabel="Cancelar">
                  <ng-template pTemplate="empty">
                    <div style="text-align:center;padding:2rem;color:var(--text-muted)">
                      <i class="pi pi-cloud-upload" style="font-size:2rem"></i>
                      <p>Arrastra un archivo DOCX o XLSX aquí</p>
                      <p style="font-size:.75rem">
                        Usa marcadores <strong>&#123;d.alumno.nombre&#125;</strong> en el documento.<br>
                        Ver documentación de Carbone para la sintaxis completa.
                      </p>
                    </div>
                  </ng-template>
                </p-fileupload>
              </div>
            </div>
          } @else {
            <p-message severity="info" text="Solo administradores pueden subir plantillas." />
          }
        </p-tabpanel>

      </p-tabpanels>
    </p-tabs>
  `,
  styles: [`
    .page-header { display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:1rem }
    .subtitle { font-size:.82rem;color:var(--text-color-secondary);margin:0 }
    .gen-form { max-width:600px;padding:1rem 0 }
    .form-row { display:flex;align-items:center;gap:1rem;margin-bottom:1rem }
    .form-row label { width:150px;font-size:.85rem;color:var(--text-color-secondary);flex-shrink:0 }
    .upload-form { max-width:700px;padding:1rem 0 }
    .form-grid { display:grid;grid-template-columns:140px 1fr;gap:.75rem;align-items:center }
    .form-grid label { font-size:.85rem;color:var(--text-color-secondary) }
    code { background:var(--surface-100);padding:.1rem .3rem;border-radius:3px;font-size:.75rem }
  `],
})
export class ReportesComponent implements OnInit {
  readonly api = inject(ApiService);
  readonly ctx = inject(ContextService);
  private readonly auth = inject(AuthService);
  private readonly notify = inject(ApexNotificationService);

  tabActivo          = signal('generar');
  plantillas         = signal<Plantilla[]>([]);
  alumnos            = signal<{id:string;label:string}[]>([]);
  grupos             = signal<{id:string;label:string}[]>([]);
  cargandoPlantillas = signal(false);
  generando          = signal(false);
  generandoGrupo     = signal(false);
  carboneOnline      = signal(false);
  carboneStatus      = computed(() => this.carboneOnline() ? 'Carbone activo' : 'Carbone sin conexión');

  // Plantillas filtradas por tipo BOLETA para generación grupal
  plantillasBoleta = computed(() => this.plantillas().filter(p => p.tipo_documento === 'BOLETA' || p.tipo_documento === 'GENERICO'));

  tiposDoc = TIPOS_DOC;

  alumnoSel: string | null = null;
  plantillaSel: string | null = null;
  tipoDocSel = 'BOLETA';
  periodoSel: number | null = null;

  // Boletas de grupo (FASE 21 — Stirling-PDF)
  grupoSel: string | null = null;
  plantillaGrupoSel: string | null = null;
  periodoGrupoSel: number | null = null;
  marcaAgua = false;

  nuevaPlantillaNombre = '';
  nuevaPlantillaTipo   = 'GENERICO';
  nuevaPlantillaDesc   = '';

  ngOnInit(): void {
    this.verificarCarbone();
    this.cargarPlantillas();
    this.cargarAlumnos();
    this.cargarGrupos();
  }

  verificarCarbone(): void {
    this.api.get<{disponible:boolean}>('/carbone/status').subscribe({
      next: r  => this.carboneOnline.set(r.disponible),
      error: () => this.carboneOnline.set(false),
    });
  }

  cargarPlantillas(): void {
    this.cargandoPlantillas.set(true);
    this.api.get<Plantilla[]>('/carbone/templates').subscribe({
      next: p  => { this.plantillas.set(p); this.cargandoPlantillas.set(false); },
      error: () => this.cargandoPlantillas.set(false),
    });
  }

  cargarAlumnos(): void {
    const params: Record<string,any> = { pagina: 1, por_pagina: 500 };
    const plantelId = this.ctx.plantel()?.id;
    if (plantelId) params['plantel_id'] = plantelId;
    this.api.get<{data:Alumno[];total:number}>('/alumnos', params).subscribe({
      next: resp => {
        this.alumnos.set(resp.data.map(a => ({
          id: a.id,
          label: `${a.persona?.apellido_paterno} ${a.persona?.apellido_materno ?? ''}, ${a.persona?.nombre} — ${a.matricula ?? ''}`.trim(),
        })));
      },
      error: () => {},
    });
  }

  cargarGrupos(): void {
    const params: Record<string,any> = { solo_activos: true, ciclo_vigente: true };
    const plantelId = this.ctx.plantel()?.id;
    if (plantelId) params['plantel_id'] = plantelId;
    this.api.get<any[]>('/grupos', params).subscribe({
      next: gs => this.grupos.set(gs.map(g => ({
        id: g.id,
        label: g._label ?? `${g.nivel ?? ''} ${g.grado ?? g.numero_grado ?? ''} — ${g.nombre_grupo ?? g.nombre ?? g.id.slice(0,6)}`,
      }))),
      error: () => {},
    });
  }

  onAlumnoChange(_id: string): void {}

  // ── FASE 21 — Generar boletas fusionadas de todo el grupo ─────────────────
  generarBoletasGrupo(): void {
    if (!this.grupoSel || !this.plantillaGrupoSel) return;
    this.generandoGrupo.set(true);
    const token = this.auth.accessToken() ?? '';
    const base = (window as any).__env?.apiUrl || '';
    let qs = `template_id=${this.plantillaGrupoSel}&agregar_marca_agua=${this.marcaAgua}`;
    if (this.periodoGrupoSel) qs += `&periodo=${this.periodoGrupoSel}`;

    fetch(`${base}/api/v1/pdf/boletas-grupo/${this.grupoSel}?${qs}`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
    })
      .then(r => {
        if (!r.ok) return r.json().then(e => { throw new Error(e.detail || 'Error al generar'); });
        const ct = r.headers.get('content-type') ?? '';
        const ext = ct.includes('zip') ? 'zip' : 'pdf';
        return r.blob().then(b => ({ blob: b, ext }));
      })
      .then(({ blob, ext }) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `boletas_grupo_${this.grupoSel?.slice(0,6)}.${ext}`;
        a.click();
        URL.revokeObjectURL(url);
        this.notify.success('PDF generado', `Boletas del grupo descargadas (.${ext})`);
      })
      .catch(err => this.notify.error('Error', err.message))
      .finally(() => this.generandoGrupo.set(false));
  }

  generarPdf(): void {
    if (!this.alumnoSel || !this.plantillaSel) return;
    this.generando.set(true);

    const endpoint = this.tipoDocSel === 'BOLETA'
      ? `/carbone/boleta/${this.alumnoSel}`
      : `/carbone/constancia/${this.alumnoSel}`;

    const params: Record<string,any> = { template_id: this.plantillaSel };
    if (this.tipoDocSel === 'BOLETA' && this.periodoSel) params['periodo'] = this.periodoSel;

    // Usar fetch directo para descargar blob
    const token = this.auth.accessToken() ?? '';
    const base = (window as any).__env?.apiUrl || '';
    const qs = Object.entries(params).map(([k,v]) => `${k}=${v}`).join('&');

    fetch(`${base}/api/v1${endpoint}?${qs}`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
    })
      .then(r => {
        if (!r.ok) return r.json().then(e => { throw new Error(e.detail || 'Error'); });
        return r.blob();
      })
      .then(blob => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `reporte_${Date.now()}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
        this.notify.success('PDF generado', 'Descarga iniciada');
      })
      .catch(err => this.notify.error('Error', err.message))
      .finally(() => this.generando.set(false));
  }

  eliminarPlantilla(id: string): void {
    this.api.delete(`/carbone/templates/${id}`).subscribe({
      next: () => {
        this.plantillas.update(p => p.filter(x => x.id !== id));
        this.notify.success('Eliminada');
      },
      error: () => this.notify.error('Error al eliminar'),
    });
  }

  onUpload(event: any): void {
    const file: File = event.files[0];
    if (!file) return;

    const fd = new FormData();
    fd.append('template', file, file.name);
    fd.append('nombre', this.nuevaPlantillaNombre || file.name);
    fd.append('tipo_documento', this.nuevaPlantillaTipo);
    fd.append('descripcion', this.nuevaPlantillaDesc);

    this.api.postFormData<Plantilla>('/carbone/templates', fd).subscribe({
      next: p => {
        this.plantillas.update(lst => [...lst, p]);
        this.notify.success('Plantilla subida', p.nombre);
        this.nuevaPlantillaNombre = '';
        this.nuevaPlantillaDesc  = '';
      },
      error: () => this.notify.error('Error al subir plantilla'),
    });
  }
}
