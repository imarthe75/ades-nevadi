import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { FileUploadModule } from 'primeng/fileupload';
import { ProgressBarModule } from 'primeng/progressbar';
import { TooltipModule } from 'primeng/tooltip';
import { TableModule } from 'primeng/table';
import { ApexNotificationService } from 'apex-component-library';
import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { compressImageIfNeeded } from '../../core/utils/file-compressor';

interface DocumentoRequerido {
  tipo: string;
  label: string;
  presente: boolean;
}

interface Documento {
  id: string;
  expediente_id: string;
  paperless_doc_id: number | null;
  tipo_documento: string;
  tipo_label: string;
  nombre_archivo: string | null;
  estado_ocr: string;
  fecha_carga: string;
  metadatos_ia: Record<string, unknown> | null;
}

interface Expediente {
  id: string;
  estudiante_id: string;
  ciclo_escolar_id: string;
  estado: string;
  completitud_pct: number;
  revisado_por: string | null;
  fecha_revision: string | null;
  observaciones: string | null;
  documentos: Documento[];
  documentos_requeridos: DocumentoRequerido[];
}

interface AlumnoOpt { id: string; label: string; }

interface AnalisisIA {
  expediente_id: string;
  completitud_pct: number;
  documentos_presentes: string[];
  documentos_faltantes: string[];
  analisis: string;
  recomendaciones: string[];
  alertas: string[];
}

const TIPOS_DOCUMENTO = [
  { value: 'CURP',                   label: 'CURP' },
  { value: 'ACTA_NACIMIENTO',        label: 'Acta de Nacimiento' },
  { value: 'CERTIFICADO_PREV',       label: 'Certificado de Nivel Previo' },
  { value: 'COMPROBANTE_DOMICILIO',  label: 'Comprobante de Domicilio' },
  { value: 'FOTOGRAFIA',             label: 'Fotografía' },
  { value: 'NSS',                    label: 'NSS' },
  { value: 'CREDENCIAL_ESCOLAR',     label: 'Credencial Escolar' },
  { value: 'CONSTANCIA_INSCRIPCION', label: 'Constancia de Inscripción' },
  { value: 'OTRO',                   label: 'Otro' },
];

/**
 * Gestión del expediente documental de alumnos integrado con Paperless-ngx.
 * Controla la carga, almacenamiento y análisis OCR de documentos requeridos
 * (CURP, acta de nacimiento, certificado previo, comprobante de domicilio, etc.).
 * Calcula el porcentaje de completitud y ejecuta análisis con IA para detectar
 * documentos faltantes o con alertas. Los documentos se almacenan en SeaweedFS
 * (S3). El acceso a PII requiere nivelAcceso ≥ 4 y scope por plantel.
 */
@Component({
  selector: 'app-expediente-doc',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    ButtonModule, TagModule, DialogModule, SelectModule,
    InputTextModule, AutoCompleteModule, ProgressSpinnerModule,
    FileUploadModule, ProgressBarModule, TooltipModule, TableModule,
  ],
  template: `
    <div class="page-header">
      <div>
        <h2>Expediente Digital</h2>
        <p class="subtitle">Gestión documental con OCR · Instituto Nevadi</p>
      </div>
    </div>

    <!-- Buscador de alumno -->
    <div class="card" style="margin-bottom:1rem">
      <div style="display:flex;gap:1rem;align-items:flex-end;flex-wrap:wrap">
        <div style="flex:1;min-width:280px">
          <label style="display:block;margin-bottom:.35rem;font-weight:500">Buscar alumno</label>
          <p-autoComplete
            [(ngModel)]="alumnoSeleccionado"
            [suggestions]="alumnosSugeridos()"
            (completeMethod)="buscarAlumnos($event)"
            (onSelect)="onAlumnoSeleccionado($event.value)"
            optionLabel="label"
            placeholder="Nombre, matrícula o CURP..."
            [style]="{'width':'100%'}"
            [inputStyle]="{'width':'100%'}"
            [delay]="300"
          />
        </div>
        @if (expediente()) {
          <p-button label="Subir documento" icon="pi pi-upload"
            (onClick)="showSubir.set(true)" />
          <p-button label="✨ Analizar con IA" icon="pi pi-sparkles"
            severity="secondary" [outlined]="true"
            [loading]="loadingIA()"
            (onClick)="analizarIA()" />
          @if (puedeVerificar()) {
            <p-button label="Verificar expediente" icon="pi pi-check-circle"
              severity="success" [outlined]="true"
              (onClick)="verificarExpediente()" />
          }
        }
      </div>
    </div>

    @if (loading()) {
      <div style="text-align:center;padding:3rem">
        <p-progressSpinner strokeWidth="4" />
      </div>
    }

    @if (expediente()) {

      <!-- Búsqueda OCR full-text -->
      <div class="card" style="margin-bottom:1rem">
        <div style="display:flex;gap:.75rem;align-items:center">
          <i class="pi pi-search" style="color:var(--primary-color)"></i>
          <input pInputText [(ngModel)]="queryOcr" placeholder="Buscar en documentos OCR..."
            style="flex:1" (keydown.enter)="buscarOcr()" />
          <p-button label="Buscar" icon="pi pi-search" size="small"
            [loading]="buscandoOcr()" (onClick)="buscarOcr()" />
          @if (resultadosOcr().length > 0) {
            <p-button icon="pi pi-times" severity="secondary" [text]="true"
              size="small" ariaLabel="Limpiar búsqueda" pTooltip="Limpiar búsqueda" (onClick)="limpiarBusqueda()" />
          }
        </div>
        @if (resultadosOcr().length > 0) {
          <div style="margin-top:.75rem;border-top:1px solid var(--surface-200);padding-top:.75rem">
            <div style="font-size:.78rem;color:var(--text-muted);margin-bottom:.5rem">
              {{ resultadosOcr().length }} resultado(s) para "{{ queryOcr }}"
            </div>
            @for (r of resultadosOcr(); track r.doc_id) {
              <div style="padding:.5rem;background:var(--surface-50);border-radius:6px;margin-bottom:.35rem;font-size:.82rem">
                <div style="font-weight:600;margin-bottom:.2rem">
                  <i class="pi pi-file-pdf" style="color:var(--red-400);margin-right:.3rem"></i>
                  {{ r.tipo }} — {{ r.nombre_archivo || '—' }}
                </div>
                <div style="color:var(--text-secondary);line-height:1.5"
                  [innerHTML]="r.fragmento"></div>
              </div>
            }
          </div>
        }
        @if (resultadosOcr().length === 0 && buscandoOcr() === false && queryOcr.length >= 3) {
          <div style="margin-top:.5rem;font-size:.8rem;color:var(--text-muted)">
            Sin resultados en los documentos procesados.
          </div>
        }
      </div>

      <!-- Panel principal: dos columnas -->
      <div style="display:grid;grid-template-columns:340px 1fr;gap:1rem;align-items:start">

        <!-- Panel izquierdo: resumen + lista documentos -->
        <div>
          <!-- Tarjeta estado -->
          <div class="card" style="margin-bottom:1rem">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:.75rem">
              <span style="font-weight:600;font-size:.95rem">Estado del expediente</span>
              <p-tag [value]="estadoLabel()" [severity]="estadoSeverity()" />
            </div>
            <div style="margin-bottom:.5rem;font-size:.85rem;color:var(--text-secondary)">
              Completitud
            </div>
            <p-progressBar
              [value]="expediente()!.completitud_pct"
              [style]="{'height':'10px','border-radius':'6px'}"
              [showValue]="true"
            />
            @if (expediente()!.fecha_revision) {
              <div style="margin-top:.75rem;font-size:.8rem;color:var(--text-secondary)">
                Verificado: {{ expediente()!.fecha_revision | date:'dd/MM/yyyy HH:mm' }}
              </div>
            }
          </div>

          <!-- Lista de documentos requeridos -->
          <div class="card" style="margin-bottom:1rem">
            <div style="font-weight:600;margin-bottom:.75rem;font-size:.9rem">Documentos requeridos</div>
            @for (req of expediente()!.documentos_requeridos; track req.tipo) {
              <div style="display:flex;align-items:center;gap:.5rem;padding:.35rem 0;border-bottom:1px solid var(--surface-border)">
                <i [class]="req.presente ? 'pi pi-check-circle' : 'pi pi-times-circle'"
                   [style]="{'color': req.presente ? 'var(--green-500)' : 'var(--red-400)', 'font-size':'1rem'}"></i>
                <span style="font-size:.85rem;flex:1">{{ req.label }}</span>
              </div>
            }
          </div>

          <!-- Lista de todos los documentos cargados -->
          <div class="card">
            <div style="font-weight:600;margin-bottom:.75rem;font-size:.9rem">
              Documentos cargados ({{ expediente()!.documentos.length }})
            </div>
            @if (expediente()!.documentos.length === 0) {
              <p style="color:var(--text-secondary);font-size:.85rem">Sin documentos aún.</p>
            }
            @for (doc of expediente()!.documentos; track doc.id) {
              <div
                class="doc-item"
                [class.doc-selected]="docSeleccionado()?.id === doc.id"
                (click)="seleccionarDoc(doc)"
                style="cursor:pointer;padding:.5rem;border-radius:6px;margin-bottom:.35rem;border:1px solid var(--surface-border);transition:background .15s"
              >
                <div style="display:flex;align-items:center;gap:.5rem">
                  <i class="pi pi-file-pdf" style="color:var(--red-400)"></i>
                  <div style="flex:1;min-width:0">
                    <div style="font-size:.82rem;font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">
                      {{ doc.tipo_label }}
                    </div>
                    <div style="font-size:.75rem;color:var(--text-secondary)">
                      {{ doc.nombre_archivo || '—' }}
                    </div>
                  </div>
                  <p-tag [value]="doc.estado_ocr" [severity]="ocrSeverity(doc.estado_ocr)" style="font-size:.7rem" />
                </div>
              </div>
            }
          </div>
        </div>

        <!-- Panel derecho: visor -->
        <div class="card" style="min-height:500px">
          @if (!docSeleccionado()) {
            <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;height:460px;color:var(--text-secondary)">
              <i class="pi pi-folder-open" style="font-size:3rem;margin-bottom:1rem;opacity:.4"></i>
              <p>Selecciona un documento para previsualizarlo</p>
            </div>
          } @else {
            <div style="margin-bottom:.75rem;display:flex;justify-content:space-between;align-items:center">
              <div>
                <span style="font-weight:600">{{ docSeleccionado()!.tipo_label }}</span>
                <span style="font-size:.8rem;color:var(--text-secondary);margin-left:.5rem">
                  {{ docSeleccionado()!.nombre_archivo }}
                </span>
              </div>
              <p-button icon="pi pi-trash" severity="danger" [outlined]="true" size="small"
                ariaLabel="Eliminar documento" pTooltip="Eliminar documento"
                (onClick)="eliminarDoc(docSeleccionado()!)" />
            </div>

            @if (docSeleccionado()!.paperless_doc_id) {
              <iframe
                [src]="previewUrl()"
                style="width:100%;height:500px;border:none;border-radius:8px"
                title="Previsualización del documento"
              ></iframe>
            } @else {
              <div style="display:flex;flex-direction:column;align-items:center;padding:3rem;color:var(--text-secondary)">
                <i class="pi pi-clock" style="font-size:2rem;margin-bottom:.75rem;opacity:.5"></i>
                <p style="text-align:center">El documento está pendiente de procesamiento OCR en Paperless-ngx.</p>
              </div>
            }
          }
        </div>
      </div>
    }

    <!-- Dialog: Subir documento -->
    <p-dialog header="Subir Documento" [(visible)]="showSubir" [modal]="true" [style]="{width:'480px'}">
      <div style="display:flex;flex-direction:column;gap:1rem;padding:.5rem 0">
        <div>
          <label style="display:block;margin-bottom:.35rem;font-weight:500">Tipo de documento *</label>
          <p-select [(ngModel)]="tipoDocumentoNuevo" [options]="tiposDocumento"
            optionLabel="label" optionValue="value"
            placeholder="Seleccionar tipo..." [style]="{'width':'100%'}" />
        </div>
        <div>
          <label style="display:block;margin-bottom:.35rem;font-weight:500">Archivo (PDF, JPEG, PNG)</label>
          <input type="file" #fileInput accept=".pdf,.jpg,.jpeg,.png,.tiff"
            (change)="onFileSelected($event)"
            style="width:100%;padding:.5rem;border:1px solid var(--surface-border);border-radius:6px;background:var(--surface-card)" />
        </div>
        @if (archivoSeleccionado()) {
          <div style="font-size:.82rem;color:var(--text-secondary)">
            ✅ {{ archivoSeleccionado()!.name }} ({{ (archivoSeleccionado()!.size / 1024).toFixed(0) }} KB)
          </div>
        }
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" severity="secondary" [outlined]="true"
          (onClick)="showSubir.set(false)" />
        <p-button label="Subir" icon="pi pi-upload"
          [loading]="subiendoDoc()"
          [disabled]="!archivoSeleccionado() || !tipoDocumentoNuevo"
          (onClick)="subirDocumento()" />
      </ng-template>
    </p-dialog>

    <!-- Dialog: Análisis IA -->
    <p-dialog header="✨ Análisis IA del Expediente" [(visible)]="showAnalisis" [modal]="true" [style]="{width:'640px'}">
      @if (analisisIA()) {
        <div style="display:flex;flex-direction:column;gap:1rem">
          <div style="background:var(--surface-50);border-radius:8px;padding:1rem">
            <p style="margin:0;line-height:1.6">{{ analisisIA()!.analisis }}</p>
          </div>

          @if (analisisIA()!.documentos_faltantes.length > 0) {
            <div>
              <div style="font-weight:600;margin-bottom:.5rem;color:var(--orange-500)">
                <i class="pi pi-exclamation-triangle"></i> Documentos faltantes
              </div>
              @for (f of analisisIA()!.documentos_faltantes; track f) {
                <div style="padding:.25rem 0;font-size:.88rem">• {{ f }}</div>
              }
            </div>
          }

          @if (analisisIA()!.recomendaciones.length > 0) {
            <div>
              <div style="font-weight:600;margin-bottom:.5rem">Recomendaciones</div>
              @for (r of analisisIA()!.recomendaciones; track r) {
                <div style="padding:.25rem 0;font-size:.88rem;color:var(--text-secondary)">• {{ r }}</div>
              }
            </div>
          }

          @if (analisisIA()!.alertas.length > 0) {
            <div style="background:var(--red-50);border-radius:6px;padding:.75rem">
              <div style="font-weight:600;margin-bottom:.5rem;color:var(--red-600)">
                <i class="pi pi-shield"></i> Alertas de coherencia
              </div>
              @for (a of analisisIA()!.alertas; track a) {
                <div style="font-size:.88rem;color:var(--red-700)">• {{ a }}</div>
              }
            </div>
          }
        </div>
      }
      <ng-template pTemplate="footer">
        <p-button label="Cerrar" (onClick)="showAnalisis.set(false)" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .doc-item:hover { background: var(--surface-100) !important; }
    .doc-selected { background: var(--primary-50) !important; border-color: var(--primary-300) !important; }
  `],
})
export class ExpedienteDocComponent implements OnInit {
  private api = inject(ApiService);
  private ctx = inject(ContextService);
  private notify = inject(ApexNotificationService);
  private sanitizer = inject(DomSanitizer);

  // State
  loading = signal(false);
  loadingIA = signal(false);
  subiendoDoc = signal(false);
  buscandoOcr = signal(false);
  showSubir = signal(false);
  showAnalisis = signal(false);

  alumnoSeleccionado: AlumnoOpt | null = null;
  alumnosSugeridos = signal<AlumnoOpt[]>([]);
  expediente = signal<Expediente | null>(null);
  docSeleccionado = signal<Documento | null>(null);
  analisisIA = signal<AnalisisIA | null>(null);
  archivoSeleccionado = signal<File | null>(null);
  resultadosOcr = signal<any[]>([]);
  queryOcr = '';
  tipoDocumentoNuevo = '';
  tiposDocumento = TIPOS_DOCUMENTO;

  puedeVerificar = computed(() => this.ctx.nivelAcceso() <= 2);

  estadoLabel = computed(() => {
    const e = this.expediente();
    if (!e) return '';
    const m: Record<string, string> = {
      PENDIENTE: 'Sin documentos', INCOMPLETO: 'Incompleto',
      COMPLETO: 'Completo', VERIFICADO: 'Verificado',
    };
    return m[e.estado] ?? e.estado;
  });

  estadoSeverity = computed((): 'success' | 'info' | 'warn' | 'danger' | 'secondary' => {
    const e = this.expediente();
    if (!e) return 'secondary';
    const m: Record<string, 'success' | 'info' | 'warn' | 'danger' | 'secondary'> = {
      PENDIENTE: 'danger', INCOMPLETO: 'warn', COMPLETO: 'info', VERIFICADO: 'success',
    };
    return m[e.estado] ?? 'secondary';
  });

  previewUrl = computed((): SafeResourceUrl => {
    const doc = this.docSeleccionado();
    if (!doc || !doc.paperless_doc_id) return '';
    const url = `/api/v1/expediente/alumno/${doc.expediente_id}/documentos/${doc.id}/preview`;
    return this.sanitizer.bypassSecurityTrustResourceUrl(url);
  });

  ocrSeverity(estado: string): 'success' | 'warn' | 'danger' | 'secondary' {
    return { PROCESADO: 'success', PENDIENTE: 'warn', ERROR: 'danger' }[estado] as any ?? 'secondary';
  }

  ngOnInit() {}

  buscarAlumnos(event: { query: string }) {
    this.api.get<any[]>('/portal/buscar', { q: event.query })
      .subscribe({
        next: (res) => {
          this.alumnosSugeridos.set(
            (res || []).map((a: any) => ({
              id: a.id,
              label: `${[a.nombre, a.apellido_paterno, a.apellido_materno].filter(Boolean).join(' ')} — ${a.matricula || ''}`,
            }))
          );
        },
        error: () => this.alumnosSugeridos.set([]),
      });
  }

  onAlumnoSeleccionado(alumno: AlumnoOpt) {
    this.cargarExpediente(alumno.id);
  }

  cargarExpediente(estudianteId: string) {
    this.loading.set(true);
    this.docSeleccionado.set(null);
    this.api.get<Expediente>(`/expediente/alumno/${estudianteId}`)
      .subscribe({
        next: (exp) => {
          this.expediente.set(exp);
          this.loading.set(false);
        },
        error: (e) => {
          this.notify.error('Error al cargar expediente', e.message);
          this.loading.set(false);
        },
      });
  }

  seleccionarDoc(doc: Documento) {
    this.docSeleccionado.set(doc);
  }

  async onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const originalFile = input.files[0];
      if (!originalFile.type.startsWith('image/') && originalFile.size > 2 * 1024 * 1024) {
        this.notify.error('Archivo no permitido', 'Los archivos PDF o de texto no pueden superar los 2 MB.');
        input.value = '';
        this.archivoSeleccionado.set(null);
        return;
      }
      const processedFile = await compressImageIfNeeded(originalFile);
      this.archivoSeleccionado.set(processedFile);
    }
  }

  subirDocumento() {
    const archivo = this.archivoSeleccionado();
    const exp = this.expediente();
    const alumno = this.alumnoSeleccionado;
    if (!archivo || !exp || !alumno) return;

    this.subiendoDoc.set(true);
    const fd = new FormData();
    fd.append('archivo', archivo);
    fd.append('tipo_documento', this.tipoDocumentoNuevo || 'OTRO');

    this.api.post<any>(`/expediente/alumno/${alumno.id}/documentos`, fd)
      .subscribe({
        next: (res) => {
          this.notify.success('Documento subido', res.mensaje);
          this.showSubir.set(false);
          this.archivoSeleccionado.set(null);
          this.tipoDocumentoNuevo = '';
          this.subiendoDoc.set(false);
          this.cargarExpediente(alumno.id);
        },
        error: (e) => {
          this.notify.error('Error al subir documento', e.error?.detail || e.message);
          this.subiendoDoc.set(false);
        },
      });
  }

  eliminarDoc(doc: Documento) {
    const exp = this.expediente();
    if (!exp) return;
    this.api.delete(`/expediente/${exp.id}/documentos/${doc.id}`)
      .subscribe({
        next: () => {
          this.notify.success('Documento eliminado');
          this.docSeleccionado.set(null);
          if (this.alumnoSeleccionado) this.cargarExpediente(this.alumnoSeleccionado.id);
        },
        error: (e) => this.notify.error('Error', e.error?.detail || e.message),
      });
  }

  analizarIA() {
    const alumno = this.alumnoSeleccionado;
    if (!alumno) return;
    this.loadingIA.set(true);
    this.api.post<AnalisisIA>(`/expediente/alumno/${alumno.id}/analizar-ia`, {})
      .subscribe({
        next: (res) => {
          this.analisisIA.set(res);
          this.showAnalisis.set(true);
          this.loadingIA.set(false);
        },
        error: (e) => {
          this.notify.error('Error en análisis IA', e.error?.detail || e.message);
          this.loadingIA.set(false);
        },
      });
  }

  verificarExpediente() {
    const exp = this.expediente();
    if (!exp) return;
    this.api.post<any>(`/expediente/${exp.id}/verificar`, {})
      .subscribe({
        next: () => {
          this.notify.success('Expediente verificado', 'Estado actualizado a VERIFICADO');
          if (this.alumnoSeleccionado) this.cargarExpediente(this.alumnoSeleccionado.id);
        },
        error: (e) => this.notify.error('Error', e.error?.detail || e.message),
      });
  }

  buscarOcr() {
    const alumno = this.alumnoSeleccionado;
    if (!alumno || this.queryOcr.trim().length < 3) return;
    this.buscandoOcr.set(true);
    this.api.get<any>(`/expediente/alumno/${alumno.id}/buscar?q=${encodeURIComponent(this.queryOcr)}`)
      .subscribe({
        next: (res) => {
          this.resultadosOcr.set(res.resultados || []);
          this.buscandoOcr.set(false);
        },
        error: () => {
          this.buscandoOcr.set(false);
          this.notify.error('Error en búsqueda OCR');
        },
      });
  }

  limpiarBusqueda() {
    this.queryOcr = '';
    this.resultadosOcr.set([]);
  }
}
