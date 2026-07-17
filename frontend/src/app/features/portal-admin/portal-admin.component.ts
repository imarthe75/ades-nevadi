import {
  Component, inject, OnInit, OnDestroy, signal, computed, ViewChild, ElementRef, effect, ChangeDetectionStrategy
} from '@angular/core';
import { Subject, takeUntil } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule }        from 'primeng/button';
import { TagModule }           from 'primeng/tag';
import { DialogModule }        from 'primeng/dialog';
import { InputTextModule }     from 'primeng/inputtext';
import { TextareaModule }      from 'primeng/textarea';
import { SelectModule }        from 'primeng/select';
import { CheckboxModule }      from 'primeng/checkbox';
import { TooltipModule }       from 'primeng/tooltip';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService } from 'primeng/api';
import { FileUploadModule }    from 'primeng/fileupload';

import { ApiService }       from '../../core/services/api.service';
import { ContextService }   from '../../core/services/context.service';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import { compressImageIfNeeded } from '../../core/utils/file-compressor';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';
import { ApexNotificationService } from 'apex-component-library';

type TagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' | undefined;

interface Convocatoria {
  id: string;
  categoria: string;
  tipo: string;
  titulo: string;
  fecha_inicio_postulacion: string | null;
  fecha_cierre_postulacion: string | null;
  cupo_maximo: number | null;
  cupo_actual: number;
  is_published: boolean;
  is_active: boolean;
  nombre_plantel: string | null;
  total_postulaciones: number;
  pendientes_revision: number;
  imagen_url: string | null;
}

interface Requisito {
  nombre: string;
  descripcion: string;
  esObligatorio: boolean;
  tiposMimePermitidos: string[];
  tamanoMaximoMb: number;
  orden: number;
}

interface Catalogo {
  planteles: { id: string; nombre_plantel: string }[];
  niveles:   { id: string; nombre_nivel: string }[];
  categorias: string[];
  tipos: string[];
}

/**
 * Panel de administración del portal de comunicación institucional.
 * Permite gestionar categorías, publicaciones y avisos visibles en el portal
 * de alumnos y padres. Requiere nivelAcceso 4 (AdminPlantel) o superior.
 */
@Component({
  selector: 'app-portal-admin',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  providers: [ConfirmationService],
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule,
    ButtonModule, TagModule, DialogModule, InputTextModule, TextareaModule,
    SelectModule, CheckboxModule, TooltipModule, ConfirmDialogModule, FileUploadModule,
    InteractiveGridComponent,
  ],
  template: `
<div class="page-header">
  <div class="page-title">
    <i class="pi pi-megaphone" style="color:var(--nevadi-red)"></i>
    <h1>Gestión de Convocatorias</h1>
  </div>
  <div class="page-actions">
    <p-button label="Nueva convocatoria" icon="pi pi-plus" (onClick)="abrirNueva()" />
  </div>
</div>

<!-- ── KPI strip ─────────────────────────────────────────────────────────── -->
<div class="kpi-strip">
  <div class="kpi-card"><span class="kpi-val">{{ stats().activas }}</span><span class="kpi-lbl">Publicadas activas</span></div>
  <div class="kpi-card"><span class="kpi-val">{{ stats().postulaciones }}</span><span class="kpi-lbl">Postulaciones totales</span></div>
  <div class="kpi-card warn"><span class="kpi-val">{{ stats().pendientes }}</span><span class="kpi-lbl">Pendientes revisión</span></div>
  <div class="kpi-card"><span class="kpi-val">{{ stats().usuarios }}</span><span class="kpi-lbl">Usuarios registrados</span></div>
  <div class="kpi-card {{ stats().arco > 0 ? 'warn' : '' }}">
    <span class="kpi-val">{{ stats().arco }}</span><span class="kpi-lbl">Solicitudes ARCO</span>
  </div>
</div>

<!-- ── Filtros ───────────────────────────────────────────────────────────── -->
<div class="filter-bar">
  <p-select [options]="opcionesTipo()" [(ngModel)]="filtroTipo" placeholder="Tipo"
    optionLabel="label" optionValue="value" [showClear]="true"
    (onChange)="cargar()" style="min-width:180px" ariaLabel="Tipo" />
  <p-select [options]="opcionesEstado" [(ngModel)]="filtroPublicado" placeholder="Estado"
    optionLabel="label" optionValue="value" [showClear]="true"
    (onChange)="cargar()" style="min-width:160px" ariaLabel="Estado" />
  <p-button icon="pi pi-refresh" severity="secondary" [text]="true" ariaLabel="Actualizar lista" (onClick)="cargar()" pTooltip="Actualizar" />
</div>

<!-- ── Grid ─────────────────────────────────────────────────────────────── -->
<app-interactive-grid
  [data]="convocatorias()"
  [columns]="columnas"
  [loading]="cargando()"
  [searchable]="true"
  searchPlaceholder="Buscar convocatoria..."
  (rowSelect)="seleccionar($event)" />

<!-- ── Dialog crear/editar ──────────────────────────────────────────────── -->
<p-dialog [(visible)]="dialogVisible" [header]="editando()?.id ? 'Editar convocatoria' : 'Nueva convocatoria'"
  [modal]="true" [style]="{width:'860px'}" [closable]="true" (onHide)="cerrarDialog()">

  <div class="form-grid" *ngIf="form()?.titulo !== undefined">
    <!-- Col 1 -->
    <div class="form-col">
      <div class="field">
        <label for="pa-conv-titulo">Título *</label>
        <input pInputText id="pa-conv-titulo" [(ngModel)]="form().titulo" placeholder="Ej. Convocatoria de Inscripción 2026-2027"/>
      </div>
      <div class="field">
        <label>Tipo *</label>
        <p-select [options]="opcionesTipoForm" [(ngModel)]="form().tipo"
          optionLabel="label" optionValue="value" placeholder="Selecciona tipo" ariaLabel="Tipo"/>
      </div>
      <div class="field">
        <label>Plantel</label>
        <p-select [options]="opcionesPlantel()" [(ngModel)]="form().plantelId"
          optionLabel="label" optionValue="value" placeholder="Todos los planteles" [showClear]="true" ariaLabel="Plantel"/>
      </div>
      <div class="field-row">
        <div class="field">
          <label for="pa-conv-fecha-inicio">Inicio postulaciones</label>
          <input pInputText id="pa-conv-fecha-inicio" type="datetime-local" [(ngModel)]="form().fechaInicio"/>
        </div>
        <div class="field">
          <label for="pa-conv-fecha-cierre">Cierre postulaciones</label>
          <input pInputText id="pa-conv-fecha-cierre" type="datetime-local" [(ngModel)]="form().fechaCierre"/>
        </div>
      </div>
      <div class="field">
        <label for="pa-conv-cupo">Cupo máximo</label>
        <input pInputText id="pa-conv-cupo" type="number" [(ngModel)]="form().cupoMaximo" placeholder="Sin límite"/>
      </div>
    </div>

    <!-- Col 2 -->
    <div class="form-col">
      <div class="field">
        <label>Descripción</label>
        <p-textarea [(ngModel)]="form().descripcion" rows="4" autoResize="true"
          placeholder="Descripción pública de la convocatoria..." />
      </div>
      <div class="field">
        <label>Requisitos generales</label>
        <p-textarea [(ngModel)]="form().requisitosGenerales" rows="3" autoResize="true"
          placeholder="Requisitos generales para postularse..." />
      </div>

      <!-- Imagen -->
      <div class="field">
        <label>Imagen de portada</label>
        <div class="imagen-preview" *ngIf="form().imagenUrl">
          <img [src]="form().imagenUrl" alt="Portada" style="max-height:120px; border-radius:6px; border:1px solid #ddd" loading="lazy" />
          <p-button icon="pi pi-trash" severity="danger" [text]="true" size="small"
            ariaLabel="Quitar imagen" (onClick)="form().imagenUrl = null" pTooltip="Quitar imagen" />
        </div>
        <div class="imagen-upload" *ngIf="!form().imagenUrl || editando()?.id">
          <input #fileInput type="file" accept="image/jpeg,image/png,image/webp" style="display:none"
            (change)="onImagenSeleccionada($event)" />
          <p-button [label]="editando()?.id ? 'Cambiar imagen' : 'Subir imagen'"
            icon="pi pi-image" ariaLabel="Subir imagen" severity="secondary" size="small"
            (onClick)="fileInput.click()" />
          <span class="field-hint">JPG, PNG o WebP · máx 5 MB</span>
        </div>
      </div>
    </div>
  </div>

  <!-- Requisitos de documentos -->
  <div class="section-title">Requisitos de documentos</div>
  <div class="requisitos-list">
    <div class="requisito-row" *ngFor="let r of form().requisitos; let i = index">
      <span class="req-num">{{ i + 1 }}</span>
      <input pInputText [(ngModel)]="r.nombre" placeholder="Nombre del documento" style="flex:1" aria-label="Nombre del documento"/>
      <p-checkbox [(ngModel)]="r.esObligatorio" [binary]="true" label="Obligatorio" />
      <p-button icon="pi pi-trash" severity="danger" [text]="true" size="small" ariaLabel="Eliminar requisito" (onClick)="eliminarRequisito(i)" />
    </div>
    <p-button label="+ Agregar requisito" icon="pi pi-plus" severity="secondary" [text]="true"
      size="small" (onClick)="agregarRequisito()" />
  </div>

  <ng-template pTemplate="footer">
    <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="cerrarDialog()" />
    <p-button [label]="guardando() ? 'Guardando...' : 'Guardar'"
      icon="pi pi-check" ariaLabel="Guardar" [loading]="guardando()" (onClick)="guardar()" />
  </ng-template>
</p-dialog>

<!-- ── Dialog postulaciones ─────────────────────────────────────────────── -->
<p-dialog [(visible)]="postulacionesVisible" [header]="'Postulaciones — ' + convSeleccionada()?.titulo"
  [modal]="true" [style]="{width:'900px'}">
  <app-interactive-grid
    [data]="postulaciones()"
    [columns]="columnasPost"
    [loading]="cargandoPost()"
    [searchable]="true"
    searchPlaceholder="Buscar postulante..." />
</p-dialog>

<p-confirmDialog />
  `,
  styles: [`
    .kpi-strip { display:flex; gap:12px; margin-bottom:20px; flex-wrap:wrap; }
    .kpi-card  { background:#fff; border:1px solid #e2e8f0; border-radius:8px; padding:14px 20px;
                 min-width:140px; flex:1; }
    .kpi-card.warn { border-color:#f59e0b; }
    .kpi-val   { display:block; font-size:2rem; font-weight:700; color:#1e293b; }
    .kpi-lbl   { font-size:0.75rem; color:var(--text-secondary); }
    .filter-bar { display:flex; gap:10px; margin-bottom:16px; align-items:center; flex-wrap:wrap; }
    .form-grid  { display:grid; grid-template-columns:1fr 1fr; gap:20px; }
    .form-col   { display:flex; flex-direction:column; gap:12px; }
    .field      { display:flex; flex-direction:column; gap:4px; }
    .field label{ font-size:0.85rem; font-weight:600; color:#374151; }
    .field-row  { display:grid; grid-template-columns:1fr 1fr; gap:10px; }
    .field-hint { font-size:0.75rem; color:#9ca3af; margin-top:4px; }
    .imagen-preview { display:flex; align-items:center; gap:8px; }
    .imagen-upload  { display:flex; align-items:center; gap:8px; }
    .section-title  { font-weight:700; font-size:0.9rem; color:#374151; border-top:1px solid #e5e7eb;
                      padding-top:12px; margin-top:12px; margin-bottom:10px; }
    .requisitos-list{ display:flex; flex-direction:column; gap:6px; }
    .requisito-row  { display:flex; align-items:center; gap:8px; background:#f8fafc;
                      padding:6px 10px; border-radius:6px; }
    .req-num { font-size:0.75rem; color:#6b7280; min-width:18px; }
    input[pInputText], p-select { width:100%; }
    p-textarea { width:100%; }
  `]
})
export class PortalAdminComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();

  private api = inject(ApiService);
  private ctx = inject(ContextService);
  private confirmSvc = inject(ConfirmationService);
  private notify = inject(ApexNotificationService);

  convocatorias  = signal<Convocatoria[]>([]);
  postulaciones  = signal<any[]>([]);
  catalogo       = signal<Catalogo>({ planteles: [], niveles: [], categorias: [], tipos: [] });
  estadisticas   = signal<any>({});
  cargando       = signal(false);
  cargandoPost   = signal(false);
  guardando      = signal(false);
  dialogVisible  = false;
  postulacionesVisible = false;

  editando    = signal<Convocatoria | null>(null);
  form        = signal<any>({});
  convSeleccionada = signal<Convocatoria | null>(null);

  filtroTipo       = '';
  filtroPlantelId  = '';
  filtroPublicado  = '';

  stats = computed(() => {
    const e = this.estadisticas();
    return {
      activas:       e.convocatorias_activas      ?? 0,
      postulaciones: e.total_postulaciones         ?? 0,
      pendientes:    e.pendientes_revision         ?? 0,
      usuarios:      e.usuarios_registrados        ?? 0,
      arco:          e.solicitudes_arco_pendientes ?? 0,
    };
  });

  opcionesEstado = [
    { label: 'Publicada', value: 'true' },
    { label: 'Borrador',  value: 'false' },
  ];

  opcionesTipoForm = [
    { label: 'Inscripción',        value: 'INSCRIPCION' },
    { label: 'Reinscripción',      value: 'REINSCRIPCION' },
    { label: 'Beca',               value: 'BECA' },
    { label: 'Intercambio',        value: 'INTERCAMBIO' },
    { label: 'Extracurricular',    value: 'EXTRACURRICULAR' },
    { label: 'Vacante docente',    value: 'VACANTE_DOCENTE' },
    { label: 'Vacante administrativa', value: 'VACANTE_ADMINISTRATIVA' },
  ];

  opcionesTipo = computed(() =>
    [{ label: 'Todos los tipos', value: '' }, ...this.opcionesTipoForm]
  );

  opcionesPlantel = computed(() => [
    { label: 'Todos los planteles', value: '' },
    ...this.catalogo().planteles.map(p => ({ label: p.nombre_plantel, value: p.id })),
  ]);

  columnas: ColumnConfig[] = [
    { field: 'titulo',         header: 'Título',         sortable: true, width: '280px' },
    { field: 'tipo',           header: 'Tipo',           sortable: true, width: '140px',
      template: (row: any) => `<span class="p-tag p-tag-secondary">${row.tipo?.replace('_', ' ')}</span>` },
    { field: 'nombre_plantel', header: 'Plantel',        sortable: true, width: '160px' },
    { field: 'fecha_cierre_postulacion', header: 'Cierre', width: '130px',
      template: (row: any) => row.fecha_cierre_postulacion
        ? new Date(row.fecha_cierre_postulacion).toLocaleDateString('es-MX') : '—' },
    { field: 'total_postulaciones', header: 'Postulaciones', width: '110px', align: 'center' },
    { field: 'pendientes_revision', header: 'Pendientes', width: '100px', align: 'center',
      template: (row: any) => row.pendientes_revision > 0
        ? `<span class="p-tag p-tag-warn">${row.pendientes_revision}</span>` : '0' },
    { field: 'is_published', header: 'Estado', width: '100px', align: 'center',
      template: (row: any) => row.is_published
        ? `<span class="p-tag p-tag-success">Publicada</span>`
        : `<span class="p-tag p-tag-secondary">Borrador</span>` },
    { field: 'acciones', header: 'Acciones', width: '160px', align: 'center',
      template: (row: any) => `
        <button class="btn-icon" data-action="editar" data-id="${row.id}" title="Editar">✏️</button>
        <button class="btn-icon" data-action="publicar" data-id="${row.id}"
          title="${row.is_published ? 'Despublicar' : 'Publicar'}">${row.is_published ? '🔴' : '🟢'}</button>
        <button class="btn-icon" data-action="postulaciones" data-id="${row.id}" title="Ver postulaciones">📋</button>
        <button class="btn-icon" data-action="archivar" data-id="${row.id}" title="Archivar">🗄️</button>` },
  ];

  columnasPost: ColumnConfig[] = [
    { field: 'nombre_completo', header: 'Postulante',  sortable: true, width: '200px' },
    { field: 'email',           header: 'Email',       sortable: true, width: '200px' },
    { field: 'folio',           header: 'Folio',       width: '120px' },
    { field: 'estado',          header: 'Estado',      width: '120px',
      template: (row: any) => `<span class="p-tag p-tag-${this.severidadEstado(row.estado)}">${row.estado}</span>` },
    { field: 'fecha_envio',     header: 'Enviada',     width: '130px',
      template: (row: any) => row.fecha_envio ? new Date(row.fecha_envio).toLocaleDateString('es-MX') : '—' },
    { field: 'total_documentos', header: 'Docs', width: '70px', align: 'center' },
  ];

  constructor() {
    effect(() => {
      this.filtroPlantelId = this.ctx.plantel()?.id ?? '';
      this.cargar();
    });
  }

  ngOnInit() {
    this.cargarCatalogo();
    this.cargarEstadisticas();
  }

  cargar() {
    this.cargando.set(true);
    const params: any = { limit: 100 };
    if (this.filtroTipo)      params['tipo']        = this.filtroTipo;
    if (this.filtroPlantelId) params['plantel_id']  = this.filtroPlantelId;
    this.api.get<Convocatoria[]>('/portal/admin/convocatorias', params)
      .pipe(takeUntil(this.destroy$))
      .subscribe({ next: d => { this.convocatorias.set(d ?? []); this.cargando.set(false); },
                  error: ()  => this.cargando.set(false) });
  }

  cargarCatalogo() {
    this.api.getAbs<Catalogo>('/api/portal/catalogo')
      .pipe(takeUntil(this.destroy$))
      .subscribe({ next: d => this.catalogo.set(d ?? { planteles:[], niveles:[], categorias:[], tipos:[] }) });
  }

  cargarEstadisticas() {
    this.api.get<any>('/portal/admin/estadisticas')
      .pipe(takeUntil(this.destroy$))
      .subscribe({ next: d => this.estadisticas.set(d ?? {}) });
  }

  seleccionar(row: any) {
    // Acciones manejadas por data-action en template
    const action = (window.event as any)?.target?.dataset?.action;
    const id     = (window.event as any)?.target?.dataset?.id;
    if (!action) { this.abrirEditar(row as Convocatoria); return; }
    switch (action) {
      case 'editar':       this.abrirEditar(row); break;
      case 'publicar':     this.togglePublicar(row); break;
      case 'postulaciones': this.verPostulaciones(row); break;
      case 'archivar':     this.archivar(row); break;
    }
  }

  abrirNueva() {
    this.editando.set(null);
    this.form.set({
      titulo: '', tipo: 'INSCRIPCION', plantelId: null, descripcion: '',
      requisitosGenerales: '', fechaInicio: '', fechaCierre: '',
      cupoMaximo: null, imagenUrl: null, requisitos: []
    });
    this.dialogVisible = true;
  }

  abrirEditar(c: Convocatoria) {
    this.editando.set(c);
    this.form.set({
      titulo: c.titulo, tipo: c.tipo,
      plantelId: (c as any).plantel_id ?? null,
      descripcion: (c as any).descripcion ?? '',
      requisitosGenerales: (c as any).requisitos_generales ?? '',
      fechaInicio: c.fecha_inicio_postulacion?.substring(0, 16) ?? '',
      fechaCierre: c.fecha_cierre_postulacion?.substring(0, 16) ?? '',
      cupoMaximo: c.cupo_maximo,
      imagenUrl: c.imagen_url,
      requisitos: []
    });
    this.cargarRequisitosEdicion(c.id);
    this.dialogVisible = true;
  }

  cerrarDialog() { this.dialogVisible = false; this.editando.set(null); }

  cargarRequisitosEdicion(id: string) {
    this.api.get<any[]>(`/portal/admin/convocatorias/${id}/requisitos`)
      .pipe(takeUntil(this.destroy$))
      .subscribe({ next: rows => {
        const reqs = (rows ?? []).map((r: any) => ({
          nombre: r.nombre ?? '',
          descripcion: r.descripcion ?? '',
          esObligatorio: r.es_obligatorio ?? true,
          tiposMimePermitidos: r.tipos_mime_permitidos ?? ['application/pdf'],
          tamanoMaximoMb: r.tamano_maximo_mb ?? 5,
          orden: r.orden ?? 0,
        }));
        this.form.update(f => ({ ...f, requisitos: reqs }));
      }});
  }

  guardar() {
    const f = this.form();
    if (!f.titulo || !f.tipo) return;
    this.guardando.set(true);

    const body = {
      titulo: f.titulo, tipo: f.tipo, plantelId: f.plantelId || null,
      descripcion: f.descripcion, requisitosGenerales: f.requisitosGenerales,
      fechaInicioPostulacion: f.fechaInicio || null,
      fechaCierrePostulacion: f.fechaCierre || null,
      cupoMaximo: f.cupoMaximo || null, imagenUrl: f.imagenUrl,
      requisitos: f.requisitos
    };

    const id = this.editando()?.id;
    const req = id
      ? this.api.put(`/portal/admin/convocatorias/${id}`, body)
      : this.api.post<any>('/portal/admin/convocatorias', body);

    req.pipe(takeUntil(this.destroy$)).subscribe({
      next: () => { this.guardando.set(false); this.cerrarDialog(); this.cargar(); this.cargarEstadisticas(); },
      error: (e: any) => {
        this.guardando.set(false);
        this.notify.error('Error', e?.error?.detail ?? 'No se pudo guardar la convocatoria');
      }
    });
  }

  async onImagenSeleccionada(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/') && file.size > 2 * 1024 * 1024) {
      alert('Las imágenes de portada no pueden superar los 2 MB.');
      return;
    }

    const processedFile = await compressImageIfNeeded(file);
    const id = this.editando()?.id;
    if (!id) {
      // Para nueva convocatoria, preview local y subir después del save
      const reader = new FileReader();
      reader.onload = e => this.form.update(f => ({ ...f, imagenUrl: e.target?.result as string }));
      reader.readAsDataURL(processedFile);
      return;
    }
    const fd = new FormData();
    fd.append('imagen', processedFile);
    this.api.postForm<any>(`/portal/admin/convocatorias/${id}/imagen`, fd)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: r => {
          this.form.update(f => ({ ...f, imagenUrl: r.imagen_url }));
          this.cargar();
        },
        error: (e: any) => this.notify.error('Error', e?.error?.detail ?? 'No se pudo subir la imagen'),
      });
  }

  togglePublicar(c: Convocatoria) {
    this.api.post<any>(`/portal/admin/convocatorias/${c.id}/publicar`, {})
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => this.cargar(),
        error: (e: any) => this.notify.error('Error', e?.error?.detail ?? 'No se pudo publicar/despublicar la convocatoria'),
      });
  }

  archivar(c: Convocatoria) {
    this.confirmSvc.confirm({
      message: `¿Archivar "${c.titulo}"? Dejará de estar disponible.`,
      header: 'Confirmar archivo', icon: 'pi pi-archive',
      accept: () => {
        this.api.delete(`/portal/admin/convocatorias/${c.id}`)
          .pipe(takeUntil(this.destroy$))
          .subscribe({
            next: () => { this.cargar(); this.cargarEstadisticas(); },
            error: (e: any) => this.notify.error('Error', e?.error?.detail ?? 'No se pudo archivar la convocatoria'),
          });
      }
    });
  }

  verPostulaciones(c: Convocatoria) {
    this.convSeleccionada.set(c);
    this.cargandoPost.set(true);
    this.postulacionesVisible = true;
    this.api.get<any[]>('/portal/admin/postulaciones', { convocatoria_id: c.id, limit: 200 })
      .pipe(takeUntil(this.destroy$))
      .subscribe({ next: d => { this.postulaciones.set(d ?? []); this.cargandoPost.set(false); },
                  error: (e: any) => { this.cargandoPost.set(false); this.notify.error('Error', e?.error?.detail ?? 'No se pudieron cargar las postulaciones'); } });
  }

  agregarRequisito() {
    this.form.update(f => ({
      ...f, requisitos: [...(f.requisitos ?? []),
        { nombre: '', descripcion: '', esObligatorio: true,
          tiposMimePermitidos: ['application/pdf'], tamanoMaximoMb: 5, orden: f.requisitos.length }]
    }));
  }

  eliminarRequisito(i: number) {
    this.form.update(f => ({ ...f, requisitos: f.requisitos.filter((_: any, idx: number) => idx !== i) }));
  }

  severidadEstado(e: string): string {
    return ({ ENVIADA:'info', REVISION:'warn', PRESELECCIONADA:'info',
              ACEPTADA:'success', RECHAZADA:'danger', LISTA:'secondary' } as any)[e] ?? 'secondary';
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
