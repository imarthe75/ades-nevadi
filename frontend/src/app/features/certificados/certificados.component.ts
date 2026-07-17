import { Component, OnDestroy, inject, signal, OnInit, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { TooltipModule } from 'primeng/tooltip';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ApexNotificationService, ApexDynamicActionTargetDirective, ApexDynamicActionService } from 'apex-component-library';
import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';

interface Certificado {
  id: string;
  folio: string;
  tipo_certificado: string;
  nivel_educativo: string;
  grado_completado: string | null;
  promedio_final: number | null;
  fecha_emision: string;
  vigente: boolean;
  estado_firma: 'PENDIENTE' | 'FIRMADO' | 'REVOCADO';
  fecha_firma: string | null;
  verificable_url: string | null;
  nombre_alumno: string;
  nombre_ciclo: string;
  blockchain_tx: string | null;
  blockchain_status: 'PENDIENTE' | 'ANCLADO' | 'FALLIDO' | null;
  fecha_anclaje: string | null;
  blockchain_network: string | null;
}

interface AlumnoOpt { id: string; label: string; }

/**
 * Emisión y verificación de certificados digitales con firma Ed25519.
 * Gestiona el ciclo PENDIENTE → FIRMADO → REVOCADO y el anclaje opcional
 * en blockchain para certificados de término de nivel. Los certificados
 * son verificables públicamente vía `/verificar/:folio`. Integra
 * `ApexDynamicActionService` para acciones contextuales. Requiere nivelAcceso ≥ 4.
 */
@Component({
  selector: 'app-certificados',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule,
    TableModule, ButtonModule, TagModule, DialogModule,
    SelectModule, InputTextModule, AutoCompleteModule, TooltipModule,
    ProgressSpinnerModule, ApexDynamicActionTargetDirective,
  ],
  template: `
    <div class="page-header">
      <div>
        <h2>Certificados Digitales</h2>
        <p class="subtitle">Emisión y verificación Ed25519 · Instituto Nevadi</p>
      </div>
      @if (esAdmin()) {
        <div style="display:flex;gap:.5rem">
          <p-button label="Emitir certificado" icon="pi pi-plus-circle"
            (onClick)="abrirEmitir()" />
          <p-button label="Gestión de llaves" icon="pi pi-key"
            severity="secondary" [outlined]="true"
            (onClick)="showLlaves.set(true)" />
        </div>
      }
    </div>

    <!-- ── KPI strip ── -->
    <div class="kpi-strip">
      <div class="kpi-card">
        <span class="kpi-val">{{ total() }}</span>
        <span class="kpi-lbl">Total emitidos</span>
      </div>
      <div class="kpi-card kpi-verde">
        <span class="kpi-val">{{ firmados() }}</span>
        <span class="kpi-lbl">Firmados Ed25519</span>
      </div>
      <div class="kpi-card kpi-amber">
        <span class="kpi-val">{{ pendientes() }}</span>
        <span class="kpi-lbl">Pendientes firma</span>
      </div>
    </div>

    <!-- ── Tabla ── -->
    <p-table
      [value]="certificados()"
      [loading]="cargando()"
      [paginator]="true" [rows]="20" [rowsPerPageOptions]="[10,20,50]"
      [sortMode]="'single'"
      styleClass="p-datatable-sm p-datatable-striped apex-grid">
      <ng-template pTemplate="header">
        <tr>
          <th pSortableColumn="folio">Folio <p-sortIcon field="folio" /></th>
          <th pSortableColumn="nombre_alumno">Alumno <p-sortIcon field="nombre_alumno" /></th>
          <th pSortableColumn="tipo_certificado">Tipo <p-sortIcon field="tipo_certificado" /></th>
          <th>Nivel / Grado</th>
          <th>Ciclo</th>
          <th>Promedio</th>
          <th pSortableColumn="fecha_emision">Fecha <p-sortIcon field="fecha_emision" /></th>
          <th>Estado Firma</th>
          <th>Anclaje Blockchain</th>
          <th class="col-acciones">Acciones</th>
        </tr>
      </ng-template>
      <ng-template pTemplate="body" let-c>
        <tr>
          <td><code style="font-size:.8rem">{{ c.folio }}</code></td>
          <td>{{ c.nombre_alumno }}</td>
          <td>{{ tipoLabel(c.tipo_certificado) }}</td>
          <td>{{ c.nivel_educativo }}{{ c.grado_completado ? ' · ' + c.grado_completado : '' }}</td>
          <td>{{ c.nombre_ciclo }}</td>
          <td>{{ c.promedio_final ? c.promedio_final : '—' }}</td>
          <td>{{ c.fecha_emision | date:'dd/MM/yyyy' }}</td>
          <td>
            <p-tag
              [value]="estadoLabel(c.estado_firma)"
              [severity]="estadoSeverity(c.estado_firma)"
              [pTooltip]="c.fecha_firma ? 'Firmado: ' + (c.fecha_firma | date:'dd/MM/yyyy HH:mm') : ''"
            />
          </td>
          <td>
            @if (c.blockchain_status === 'ANCLADO') {
              <a [href]="blockchainLink(c.blockchain_tx, c.blockchain_network)" target="_blank" style="text-decoration:none">
                <p-tag value="ANCLADO" severity="success" icon="pi pi-link" pTooltip="Ver transacción en explorador" />
              </a>
            } @else if (c.blockchain_status === 'PENDIENTE') {
              <p-tag value="PENDIENTE" severity="warn" icon="pi pi-spin pi-spinner" pTooltip="Procesando anclaje en segundo plano" />
            } @else if (c.blockchain_status === 'FALLIDO') {
              <p-tag value="FALLIDO" severity="danger" icon="pi pi-exclamation-triangle" pTooltip="Error al anclar en blockchain" />
            } @else {
              <span class="text-muted">—</span>
            }
          </td>
          <td class="acciones-cell">
            <p-button icon="pi pi-file-pdf" ariaLabel="Descargar PDF" [text]="true" severity="secondary" size="small"
              pTooltip="Descargar PDF"
              data-testid="btn-descargar-pdf"
              [loading]="descargando() === c.id"
              (onClick)="descargarPdf(c)" />
            @if (c.verificable_url) {
              <p-button icon="pi pi-external-link" ariaLabel="Verificar en línea" [text]="true" severity="info" size="small"
                pTooltip="Verificar en línea" (onClick)="abrirVerificar(c.folio)" />
            }
            @if (esAdmin() && c.estado_firma === 'PENDIENTE') {
              <p-button icon="pi pi-sign-in" ariaLabel="Firmar ahora" [text]="true" severity="success" size="small"
                pTooltip="Firmar ahora"
                [loading]="firmando() === c.id"
                (onClick)="firmarCertificado(c)" />
            }
          </td>
        </tr>
      </ng-template>
      <ng-template pTemplate="emptymessage">
        <tr><td colspan="10" style="text-align:center;padding:2rem;color:var(--text-color-secondary)">
          Sin certificados emitidos
        </td></tr>
      </ng-template>
    </p-table>

    <!-- ── Dialog emitir ── -->
    <p-dialog
      [visible]="showEmitir()" (visibleChange)="showEmitir.set($event)"
      header="Emitir Certificado Digital"
      [modal]="true" [style]="{width:'480px'}">
      <div class="form-grid">
        <div class="form-row">
          <label>Alumno *</label>
          <p-autoComplete
            [(ngModel)]="alumnoQuery"
            [suggestions]="alumnoOpts()"
            (completeMethod)="buscarAlumnos($event.query)"
            (onSelect)="onAlumnoSelect($event.value)"
            field="label" [minLength]="2"
            placeholder="Nombre o matrícula..."
            styleClass="w-full" />
        </div>
        <div class="form-row">
          <label>Tipo *</label>
          <p-select [(ngModel)]="emitirForm.tipo_certificado"
            [options]="tiposOpts" optionLabel="label" optionValue="value"
            (onChange)="onTipoChange()"
            styleClass="w-full" ariaLabel="Tipo"/>
        </div>
        <div class="form-row" [apexDATarget]="'grado-row'">
          <label>Grado completado</label>
          <input pInputText [(ngModel)]="emitirForm.grado_completado"
            placeholder="ej. 6° Primaria" class="w-full" />
        </div>
        <div class="form-row" [apexDATarget]="'promedio-row'">
          <label>Promedio final</label>
          <input pInputText type="number" [(ngModel)]="emitirForm.promedio_final"
            placeholder="ej. 9.2" class="w-full" />
        </div>
      </div>
      <div class="form-note">
        <i class="pi pi-info-circle"></i>
        El certificado se firma automáticamente con Ed25519 si la llave está configurada.
        Se genera PDF con QR de verificación.
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" severity="secondary" [text]="true"
          (onClick)="showEmitir.set(false)" />
        <p-button label="Emitir y descargar PDF" icon="pi pi-file-pdf"
          [loading]="emitiendo()"
          (onClick)="emitirCertificado()" />
      </ng-template>
    </p-dialog>

    <!-- ── Dialog gestión llaves ── -->
    <p-dialog
      [visible]="showLlaves()" (visibleChange)="showLlaves.set($event)"
      header="Gestión de Llaves de Firma Ed25519"
      [modal]="true" [style]="{width:'560px'}">
      <div class="llave-info">
        @if (llaveActiva()) {
          <div class="llave-card llave-activa">
            <div class="llave-header">
              <i class="pi pi-key" style="color:var(--green-500)"></i>
              <strong>{{ llaveActiva()!.nombre }}</strong>
              <span class="llave-badge">ACTIVA</span>
            </div>
            <div class="llave-pub">
              <span class="llave-pub-label">Clave pública (base64):</span>
              <code class="llave-pub-val">{{ llaveActiva()!.clave_publica_b64 }}</code>
            </div>
            <div class="llave-meta">
              Activada: {{ llaveActiva()!.fecha_activacion | date:'dd/MM/yyyy' }}
            </div>
            @if (!llaveActiva()!.configurada_en_env) {
              <div class="llave-warn">
                <i class="pi pi-exclamation-triangle"></i>
                La llave privada NO está en .env — los certificados no se podrán firmar.
              </div>
            }
          </div>
        } @else {
          <div class="llave-empty">
            <i class="pi pi-exclamation-circle" style="font-size:2rem;color:var(--yellow-500)"></i>
            <p>No hay llave de firma registrada.</p>
            <p class="llave-hint">Genera un par Ed25519 y registra la llave pública.</p>
          </div>
        }
      </div>

      <div class="llave-actions">
        <p-button label="Generar nuevo par de llaves" icon="pi pi-refresh"
          severity="warn" [outlined]="true"
          [loading]="generandoLlave()"
          (onClick)="generarLlave()" />
        @if (nuevaLlave()) {
          <div class="nueva-llave-box">
            <div class="nueva-llave-aviso">
              <i class="pi pi-shield" style="color:var(--red-500)"></i>
              <strong>Copia la llave privada AHORA. No se mostrará de nuevo.</strong>
            </div>
            <div class="nueva-llave-item">
              <label>FIRMA_CLAVE_PRIVADA_HEX (para .env):</label>
              <code>{{ nuevaLlave()!.privada_hex }}</code>
            </div>
            <div class="nueva-llave-item">
              <label>Clave pública (base64):</label>
              <code>{{ nuevaLlave()!.publica_b64 }}</code>
            </div>
            <p-button label="Registrar esta llave pública" icon="pi pi-save"
              (onClick)="registrarLlave()" />
          </div>
        }
      </div>

      <ng-template pTemplate="footer">
        <p-button label="Cerrar" severity="secondary" [text]="true"
          (onClick)="showLlaves.set(false)" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .page-header { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:1.5rem; }
    .page-header h2 { margin:0; font-size:1.4rem; }
    .subtitle { color:var(--text-color-secondary); font-size:.85rem; margin-top:.2rem; }

    .kpi-strip { display:flex; gap:1rem; margin-bottom:1.5rem; flex-wrap:wrap; }
    .kpi-card { background:var(--surface-0); border:1px solid var(--surface-200); border-radius:8px;
      padding:.75rem 1.5rem; min-width:130px; text-align:center; }
    .kpi-card.kpi-verde { border-color:#86efac; background:#f0fdf4; }
    .kpi-card.kpi-amber { border-color:#fcd34d; background:#fffbeb; }
    .kpi-val { display:block; font-size:1.8rem; font-weight:700; color:var(--primary-color); }
    .kpi-verde .kpi-val { color:#166534; }
    .kpi-amber .kpi-val { color:#92400e; }
    .kpi-lbl { font-size:.75rem; color:var(--text-color-secondary); }

    .col-acciones { width:90px; text-align:center; }
    .acciones-cell { text-align:center; white-space:nowrap; }

    .form-grid { display:flex; flex-direction:column; gap:1rem; padding:.5rem 0; }
    .form-row { display:flex; flex-direction:column; gap:.4rem; }
    .form-row label { font-size:.85rem; font-weight:600; color:var(--text-color); }
    .w-full { width:100% !important; }
    .form-note { background:var(--surface-50); border:1px solid var(--surface-200); border-radius:6px;
      padding:.65rem .85rem; font-size:.8rem; color:var(--text-color-secondary); display:flex;
      align-items:flex-start; gap:.5rem; margin-top:.5rem; }

    /* Llave panel */
    .llave-info { margin-bottom:1rem; }
    .llave-card { border:1px solid var(--surface-200); border-radius:8px; padding:1rem; }
    .llave-activa { border-color:#86efac; background:#f0fdf4; }
    .llave-header { display:flex; align-items:center; gap:.5rem; margin-bottom:.75rem; }
    .llave-badge { background:#166534; color:#fff; border-radius:4px; padding:.15rem .5rem; font-size:.7rem; font-weight:700; }
    .llave-pub { margin-bottom:.5rem; }
    .llave-pub-label { font-size:.75rem; color:var(--text-color-secondary); display:block; margin-bottom:.25rem; }
    .llave-pub-val { font-size:.72rem; word-break:break-all; background:var(--surface-100); padding:.25rem .5rem; border-radius:4px; display:block; }
    .llave-meta { font-size:.78rem; color:var(--text-color-secondary); }
    .llave-warn { background:#fef3c7; border:1px solid #fcd34d; border-radius:4px; padding:.5rem .75rem; font-size:.78rem; color:#92400e; margin-top:.75rem; display:flex; align-items:center; gap:.5rem; }
    .llave-empty { text-align:center; padding:2rem; color:var(--text-color-secondary); }
    .llave-hint { font-size:.82rem; }
    .llave-actions { display:flex; flex-direction:column; gap:.75rem; }
    .nueva-llave-box { background:#fff7ed; border:1px solid #fed7aa; border-radius:8px; padding:1rem; display:flex; flex-direction:column; gap:.75rem; }
    .nueva-llave-aviso { display:flex; align-items:center; gap:.5rem; font-size:.82rem; }
    .nueva-llave-item label { font-size:.75rem; color:var(--text-color-secondary); display:block; margin-bottom:.25rem; }
    .nueva-llave-item code { font-size:.7rem; word-break:break-all; background:var(--surface-100); padding:.25rem .5rem; border-radius:4px; display:block; }
  `],
})

export class CertificadosComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private readonly api    = inject(ApiService);
  private readonly notify = inject(ApexNotificationService);
  readonly ctx            = inject(ContextService);
  private readonly da     = inject(ApexDynamicActionService);

  certificados = signal<Certificado[]>([]);
  cargando     = signal(false);
  showEmitir   = signal(false);
  showLlaves   = signal(false);
  emitiendo    = signal(false);
  firmando     = signal<string | null>(null);
  descargando  = signal<string | null>(null);
  generandoLlave = signal(false);
  llaveActiva  = signal<any>(null);
  nuevaLlave   = signal<any>(null);
  alumnoOpts   = signal<AlumnoOpt[]>([]);

  total     = signal(0);
  firmados  = signal(0);
  pendientes = signal(0);

  alumnoQuery = '';
  emitirForm = {
    estudiante_id:    '',
    tipo_certificado: 'ESTUDIOS',
    grado_completado: '',
    promedio_final:   null as number | null,
  };

  tiposOpts = [
    { label: 'Estudios',            value: 'ESTUDIOS' },
    { label: 'Buena Conducta',      value: 'CONDUCTA' },
    { label: 'Participación',       value: 'PARTICIPACION' },
    { label: 'Mérito Académico',    value: 'MERITO_ACADEMICO' },
    { label: 'Asistencia Perfecta', value: 'ASISTENCIA_PERFECTA' },
  ];

  readonly TIPO_LABELS: Record<string, string> = {
    ESTUDIOS:            'Estudios',
    CONDUCTA:            'Buena Conducta',
    PARTICIPACION:       'Participación',
    MERITO_ACADEMICO:    'Mérito Académico',
    ASISTENCIA_PERFECTA: 'Asistencia Perfecta',
  };

  ngOnInit(): void {
    this.cargar();
    if (this.esAdmin()) this.cargarLlaveActiva();
  }

  onTipoChange(): void {
    const tipo = this.emitirForm.tipo_certificado;
    if (tipo === 'ESTUDIOS' || tipo === 'MERITO_ACADEMICO') {
      this.da.broadcast({ action: 'show', targetRegionId: 'grado-row' });
      this.da.broadcast({ action: 'show', targetRegionId: 'promedio-row' });
    } else {
      this.da.broadcast({ action: 'hide', targetRegionId: 'grado-row' });
      this.da.broadcast({ action: 'hide', targetRegionId: 'promedio-row' });
    }
  }

  abrirEmitir(): void {
    this.emitirForm.estudiante_id = '';
    this.emitirForm.tipo_certificado = 'ESTUDIOS';
    this.emitirForm.grado_completado = '';
    this.emitirForm.promedio_final = null;
    this.alumnoQuery = '';
    this.showEmitir.set(true);
    setTimeout(() => this.onTipoChange(), 50);
  }

  esAdmin(): boolean {
    return (this.ctx.nivelAcceso() ?? 99) <= 1;
  }

  cargar(): void {
    this.cargando.set(true);
    this.api.get<Certificado[]>('/certificados').pipe(takeUntil(this.destroy$)).subscribe({
      next: (data) => {
        this.certificados.set(data);
        this.total.set(data.length);
        this.firmados.set(data.filter(c => c.estado_firma === 'FIRMADO').length);
        this.pendientes.set(data.filter(c => c.estado_firma === 'PENDIENTE').length);
        this.cargando.set(false);
      },
      error: () => {
        this.notify.error('Error', 'No se pudieron cargar los certificados');
        this.cargando.set(false);
      },
    });
  }

  buscarAlumnos(query: string): void {
    if (query.length < 2) return;
    this.api.get<any[]>('/portal/buscar', { q: query }).pipe(takeUntil(this.destroy$)).subscribe({
      next: (data) => {
        this.alumnoOpts.set((data ?? []).map(a => {
          const nom = [a.nombre, a.apellido_paterno, a.apellido_materno].filter(Boolean).join(' ');
          return {
            id:    a.id,
            label: `${nom} (${a.matricula ?? ''})`,
          };
        }));
      },
      error: () => {},
    });
  }

  onAlumnoSelect(opt: AlumnoOpt): void {
    this.emitirForm.estudiante_id = opt.id;
  }

  emitirCertificado(): void {
    if (!this.emitirForm.estudiante_id) {
      this.notify.warning('Datos requeridos', 'Selecciona un alumno');
      return;
    }
    const ciclo = this.ctx.ciclo();
    if (!ciclo?.id) {
      this.notify.warning('Ciclo requerido', 'Selecciona un ciclo escolar en la barra superior');
      return;
    }
    this.emitiendo.set(true);
    this.api.postBlob('/certificados/emitir', {
      ...this.emitirForm,
      ciclo_escolar_id: ciclo.id,
      promedio_final: this.emitirForm.promedio_final || undefined,
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: (blob: Blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = 'certificado.pdf'; a.click();
        URL.revokeObjectURL(url);
        this.showEmitir.set(false);
        this.emitiendo.set(false);
        this.notify.success('Certificado emitido', 'PDF descargado con QR de verificación');
        this.cargar();
      },
      error: (e) => {
        this.notify.error('Error', e.error?.detail ?? 'No se pudo emitir el certificado');
        this.emitiendo.set(false);
      },
    });
  }

  firmarCertificado(cert: Certificado): void {
    this.firmando.set(cert.id);
    this.api.post(`/certificados/${cert.id}/firmar`, {}).pipe(takeUntil(this.destroy$)).subscribe({
      next: (r: any) => {
        this.notify.success('Firmado', `Folio ${r.folio} — firma Ed25519 aplicada`);
        this.firmando.set(null);
        this.cargar();
      },
      error: (e) => {
        this.notify.error('Error', e.error?.detail ?? 'No se pudo firmar');
        this.firmando.set(null);
      },
    });
  }

  descargarPdf(cert: Certificado): void {
    this.descargando.set(cert.id);
    const ciclo = this.ctx.ciclo();
    this.api.postBlob('/certificados/emitir', {
      estudiante_id:    cert.id,
      tipo_certificado: cert.tipo_certificado,
      ciclo_escolar_id: ciclo?.id,
      grado_completado: cert.grado_completado,
      promedio_final:   cert.promedio_final,
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: (blob: Blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `certificado-${cert.folio}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
        this.descargando.set(null);
      },
      error: () => {
        this.notify.warning('Sin PDF', 'El PDF no está disponible para re-descarga en este entorno');
        this.descargando.set(null);
      },
    });
  }

  abrirVerificar(folio: string): void {
    window.open(`/verificar/${folio}`, '_blank');
  }

  cargarLlaveActiva(): void {
    this.api.get('/certificados/llave/activa').pipe(takeUntil(this.destroy$)).subscribe({
      next: (l: any) => this.llaveActiva.set(l),
      error: () => {},
    });
  }

  generarLlave(): void {
    this.generandoLlave.set(true);
    this.api.post('/certificados/llave/generar', {}).pipe(takeUntil(this.destroy$)).subscribe({
      next: (r: any) => {
        this.nuevaLlave.set(r);
        this.generandoLlave.set(false);
        this.notify.warning('Llave generada', 'Copia la llave privada AHORA al .env');
      },
      error: (e) => {
        this.notify.error('Error', e.error?.detail ?? 'Error al generar llave');
        this.generandoLlave.set(false);
      },
    });
  }

  registrarLlave(): void {
    const llave = this.nuevaLlave();
    if (!llave) return;
    this.api.post('/certificados/llave/registrar', {
      nombre: `Llave Instituto Nevadi ${new Date().getFullYear()}`,
      clave_publica_b64: llave.publica_b64,
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.notify.success('Llave registrada', 'La llave pública queda activa en la BD');
        this.nuevaLlave.set(null);
        this.cargarLlaveActiva();
      },
      error: (e) => this.notify.error('Error', e.error?.detail ?? 'Error al registrar llave'),
    });
  }

  tipoLabel(tipo: string): string {
    return this.TIPO_LABELS[tipo] ?? tipo;
  }

  estadoLabel(estado: string): string {
    return { PENDIENTE: 'Pendiente', FIRMADO: 'Firmado', REVOCADO: 'Revocado' }[estado] ?? estado;
  }

  estadoSeverity(estado: string): 'success' | 'warn' | 'danger' | 'info' {
    return { PENDIENTE: 'warn', FIRMADO: 'success', REVOCADO: 'danger' }[estado] as any ?? 'info';
  }

  blockchainLink(tx: string | null, network: string | null): string {
    if (!tx) return '#';
    if (network === 'MOCK') {
      return `/verificar/mock-tx/${tx}`;
    }
    // LAChain explorer — cuando esté configurado en producción
    // Por ahora retorna # ya que LAChain RPC aún no está activo
    if (network === 'LACCHAIN') {
      // TODO: Configurar LACCHAIN_EXPLORER_URL en entorno de producción
      return `#`; // Temporalmente deshabilitado
    }
    // Backward compatibility: certificados históricos con Polygon testnet
    if (network === 'POLYGON_AMOY') {
      return `https://amoy.polygonscan.com/tx/${tx}`;
    }
    return '#';
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
