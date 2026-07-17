import { Component, OnDestroy, OnInit, signal, inject, computed, ChangeDetectionStrategy} from '@angular/core';
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
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';
import { CardModule } from 'primeng/card';
import { TabsModule } from 'primeng/tabs';
import { DatePickerModule } from 'primeng/datepicker';
import { MessageService } from 'primeng/api';
import { ApexNotificationService } from 'apex-component-library';
import { ContextService } from '../../core/services/context.service';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';

type TagSev = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

interface Foro {
  id: string;
  nombre: string;
  descripcion: string | null;
  tipo: string;
  es_moderado: boolean;
  nombre_grupo: string | null;
  nombre_plantel: string | null;
  nombre_materia: string | null;
  total_mensajes: number;
}

interface MensajeForo {
  id: string;
  asunto: string;
  contenido: string;
  adjunto_url: string | null;
  fecha_creacion: string;
  usuario_creacion: string;
  respuestas: number;
  estado: string;
}

interface RespuestaForo {
  id: string;
  contenido: string;
  adjunto_url: string | null;
  fecha_creacion: string;
  usuario_creacion: string;
}

interface Anuncio {
  id: string;
  titulo: string;
  contenido: string;
  es_urgente: boolean;
  nivel_educativo: string | null;
  fecha_inicio: string;
  fecha_fin: string | null;
  fecha_creacion: string;
}

/**
 * Módulo de foros de discusión académica entre docentes, alumnos y coordinadores.
 * Soporta hilos por grupo/materia y moderación por nivel de acceso.
 */
@Component({
  selector: 'app-foros',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule, TableModule, ButtonModule, DialogModule,
    InputTextModule, SelectModule, TagModule, ToastModule, TextareaModule,
    TooltipModule, CardModule, TabsModule, DatePickerModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast />
    <div class="apex-page">
      <div class="apex-toolbar">
        <h2 class="apex-title">Foros y Anuncios</h2>
        <div class="apex-toolbar-actions">
          <p-button label="Nuevo Anuncio" icon="pi pi-megaphone" size="small" severity="warn"
            (onClick)="abrirNuevoAnuncio()" *ngIf="esCoordinador()" />
          <p-button label="Nuevo Foro" icon="pi pi-comments" size="small"
            (onClick)="abrirNuevoForo()" *ngIf="esCoordinador()" />
        </div>
      </div>

      <!-- Anuncios urgentes -->
      @if (anunciosUrgentes().length > 0) {
        <div class="anuncios-urgentes">
          @for (a of anunciosUrgentes(); track a.id) {
            <div class="anuncio-urgente">
              <i class="pi pi-exclamation-triangle"></i>
              <strong>{{ a.titulo }}</strong> — {{ a.contenido | slice:0:120 }}
            </div>
          }
        </div>
      }

      <!-- Anuncios normales -->
      <div class="section-header">
        <span class="section-title">Tablón de Anuncios</span>
        <span class="text-sm text-gray-500">{{ anuncios().length }} anuncio(s) vigente(s)</span>
      </div>
      <p-table [value]="anuncios()" [loading]="cargandoAnuncios()" styleClass="p-datatable-sm"
        [paginator]="anuncios().length > 10" [rows]="10">
        <ng-template pTemplate="header">
          <tr>
            <th style="width:60px"></th>
            <th>Título</th>
            <th>Nivel</th>
            <th>Vigencia</th>
            <th>Publicado</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-a>
          <tr>
            <td>
              @if (a.es_urgente) {
                <p-tag value="URGENTE" severity="danger" />
              }
            </td>
            <td class="font-medium">{{ a.titulo }}</td>
            <td>{{ a.nivel_educativo || 'Todos' }}</td>
            <td class="text-sm">{{ a.fecha_inicio | date:'dd/MM/yy' }} — {{ a.fecha_fin ? (a.fecha_fin | date:'dd/MM/yy') : 'Sin fecha fin' }}</td>
            <td class="text-sm text-gray-500">{{ a.fecha_creacion | date:'dd/MM/yyyy HH:mm' }}</td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr><td colspan="5" class="text-center py-3 text-gray-500">Sin anuncios vigentes.</td></tr>
        </ng-template>
      </p-table>

      <!-- Foros con Tabs de Filtro -->
      <div class="section-header mt-4">
        <span class="section-title">Foros de Discusión</span>
      </div>

      <p-tabs [value]="filtroTipo()" (valueChange)="cambiarFiltro($event)" styleClass="mb-3">
        <p-tablist>
          <p-tab value="TODOS">Todos</p-tab>
          <p-tab value="GENERAL">General / Plantel</p-tab>
          <p-tab value="MATERIA">Materias</p-tab>
          <p-tab value="TUTORES">Padres & Tutores</p-tab>
        </p-tablist>
      </p-tabs>

      <div class="foros-grid">
        @for (foro of forosFiltrados(); track foro.id) {
          <div class="foro-card" (click)="abrirForo(foro)">
            <div class="foro-card-header">
              <p-tag [value]="foro.tipo" [severity]="tipoSev(foro.tipo)" />
              @if (foro.es_moderado) { <p-tag value="Moderado" severity="warn" /> }
            </div>
            <h3 class="foro-card-title">{{ foro.nombre }}</h3>
            <p class="foro-card-desc">{{ foro.descripcion || 'Sin descripción' }}</p>
            <div class="foro-card-footer">
              <span class="text-sm text-gray-500">
                <i class="pi pi-comments"></i> {{ foro.total_mensajes }} mensaje(s)
              </span>
              <span class="text-sm text-gray-500">
                {{ foro.nombre_materia || foro.nombre_grupo || foro.nombre_plantel || 'General' }}
              </span>
            </div>
          </div>
        }
        @if (forosFiltrados().length === 0 && !cargandoForos()) {
          <div style="grid-column: 1 / -1; text-align: center; padding: 2rem; color: var(--text-secondary)">
            Sin foros disponibles en esta categoría.
          </div>
        }
      </div>
    </div>

    <!-- Dialog: Mensajes del foro -->
    <p-dialog [header]="foroActivo()?.nombre ?? 'Foro'" [(visible)]="dlgMensajes"
      [modal]="true" [style]="{width:'680px'}" [draggable]="false">
      <div class="mensajes-container">
        @for (m of mensajes(); track m.id) {
          <div class="mensaje-item">
            <div class="mensaje-header">
              <strong>{{ m.asunto }}</strong>
              <div class="flex items-center gap-2">
                <span class="text-xs text-gray-400">{{ m.usuario_creacion }} · {{ m.fecha_creacion | date:'dd/MM/yy HH:mm' }}</span>
                @if (m.estado === 'PENDIENTE') {
                  <p-tag value="Moderación Pendiente" severity="warn" />
                }
              </div>
            </div>
            <p class="mensaje-body">{{ m.contenido }}</p>
            <div class="mensaje-footer">
              <span class="text-xs text-gray-500"><i class="pi pi-reply"></i> {{ m.respuestas }} respuesta(s)</span>
              <div class="flex gap-2">
                <p-button label="Respuestas" icon="pi pi-comments" size="small" [text]="true"
                  (onClick)="verRespuestas(m)" />
                <p-button label="Responder" icon="pi pi-reply" size="small" [text]="true"
                  (onClick)="abrirRespuesta(m)" />
                <p-button label="Aprobar" icon="pi pi-check" size="small" severity="success" [text]="true"
                  [loading]="moderandoId() === m.id"
                  *ngIf="esCoordinador() && m.estado === 'PENDIENTE'" (onClick)="moderar(m.id, 'PUBLICADO')" />
                <p-button label="Ocultar" icon="pi pi-eye-slash" size="small" severity="danger" [text]="true"
                  [loading]="moderandoId() === m.id"
                  *ngIf="esCoordinador() && m.estado === 'PUBLICADO'" (onClick)="moderar(m.id, 'RECHAZADO')" />
              </div>
            </div>
          </div>
        }
        @if (mensajes().length === 0) {
          <p class="text-center py-4 text-gray-400">Sin mensajes publicados en este foro.</p>
        }
      </div>
      <ng-template pTemplate="footer">
        <div style="display:flex;gap:.5rem;width:100%">
          <input pInputText [(ngModel)]="nuevoMsgAsunto" placeholder="Asunto…" style="flex:1" aria-label="Asunto"/>
          <p-button icon="pi pi-plus" label="Nuevo mensaje" size="small" (onClick)="dlgNuevoMensaje=true" />
        </div>
      </ng-template>
    </p-dialog>

    <!-- Dialog: Respuestas Encadenadas -->
    <p-dialog [header]="'Respuestas a: ' + mensajeActivo()?.asunto" [(visible)]="dlgRespuestas"
      [modal]="true" [style]="{width:'540px'}" [draggable]="false">
      <div class="respuestas-list">
        @for (r of respuestas(); track r.id) {
          <div class="respuesta-item">
            <div class="resp-header">
              <strong class="text-xs">{{ r.usuario_creacion }}</strong>
              <span class="text-xxs text-gray-400">{{ r.fecha_creacion | date:'dd/MM/yy HH:mm' }}</span>
            </div>
            <p class="resp-body">{{ r.contenido }}</p>
          </div>
        }
        @if (respuestas().length === 0) {
          <p class="text-center py-4 text-gray-400">Sin respuestas aún. ¡Sé el primero en responder!</p>
        }
      </div>
      <div class="respuesta-composer">
        <textarea pTextarea [(ngModel)]="nuevaRespuestaTexto" placeholder="Escribe tu respuesta..." rows="2" style="width:100%" aria-label="Escribe tu respuesta"></textarea>
        <div style="text-align: right; margin-top: 0.5rem;">
          <p-button label="Responder" icon="pi pi-send" size="small" [loading]="guardando()" (onClick)="enviarRespuesta()" />
        </div>
      </div>
    </p-dialog>

    <!-- Dialog: Nuevo mensaje -->
    <p-dialog header="Nuevo Mensaje" [(visible)]="dlgNuevoMensaje"
      [modal]="true" [style]="{width:'480px'}" [draggable]="false">
      <div class="grid grid-cols-1 gap-3 p-3">
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium" for="foro-msg-asunto">Asunto *</label>
          <input pInputText id="foro-msg-asunto" [(ngModel)]="nuevoMsgAsunto"/>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium" for="foro-msg-contenido">Contenido *</label>
          <textarea pTextarea id="foro-msg-contenido" [(ngModel)]="nuevoMsgContenido" rows="5" style="width:100%"></textarea>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dlgNuevoMensaje=false" />
        <p-button label="Publicar" icon="pi pi-send" [loading]="guardando()" (onClick)="publicarMensaje()" />
      </ng-template>
    </p-dialog>

    <!-- Dialog: Nuevo Foro -->
    <p-dialog header="Crear Nuevo Foro de Discusión" [(visible)]="dlgForo"
      [modal]="true" [style]="{width:'480px'}" [draggable]="false">
      <div class="grid grid-cols-1 gap-3 p-3">
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium" for="foro-nombre">Nombre del Foro *</label>
          <input pInputText id="foro-nombre" [(ngModel)]="foroForm.nombre"/>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium" for="foro-desc">Descripción</label>
          <textarea pTextarea id="foro-desc" [(ngModel)]="foroForm.descripcion" rows="3" style="width:100%"></textarea>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Tipo *</label>
          <p-select [options]="tiposForo" [(ngModel)]="foroForm.tipo" placeholder="Selecciona tipo..." ariaLabel="Tipo"/>
        </div>
        
        <div class="flex flex-col gap-1" *ngIf="foroForm.tipo === 'MATERIA'">
          <label class="text-sm font-medium">Materia *</label>
          <p-select [options]="materias()" optionLabel="nombre_materia" optionValue="id" [(ngModel)]="foroForm.materia_id" placeholder="Selecciona materia..." ariaLabel="Materia"/>
        </div>

        <div class="flex items-center gap-2">
          <input type="checkbox" [(ngModel)]="foroForm.es_moderado" id="es_moderado" />
          <label for="es_moderado" class="text-sm">Foro Moderado (mensajes requieren aprobación)</label>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dlgForo=false" />
        <p-button label="Crear Foro" icon="pi pi-plus" [loading]="guardando()" (onClick)="crearForo()" />
      </ng-template>
    </p-dialog>

    <!-- Dialog: Nuevo Anuncio -->
    <p-dialog header="Publicar Anuncio" [(visible)]="dlgAnuncio"
      [modal]="true" [style]="{width:'520px'}" [draggable]="false">
      <div class="grid grid-cols-1 gap-3 p-3">
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium" for="foro-anuncio-titulo">Título *</label>
          <input pInputText id="foro-anuncio-titulo" [(ngModel)]="anuncioForm.titulo"/>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium" for="foro-anuncio-contenido">Contenido *</label>
          <textarea pTextarea id="foro-anuncio-contenido" [(ngModel)]="anuncioForm.contenido" rows="4" style="width:100%"></textarea>
        </div>
        <div class="flex flex-col gap-2">
          <label class="text-sm font-medium">Fecha Inicio *</label>
          <p-datepicker [(ngModel)]="anuncioForm.fechaInicio" dateFormat="dd/mm/yy" [showIcon]="true"
                        placeholder="DD/MM/AAAA" [style]="{width:'100%'}" [inputStyle]="{width:'100%'}" ariaLabel="Fecha Inicio"/>
        </div>
        <div class="flex items-center gap-2">
          <input type="checkbox" [(ngModel)]="anuncioForm.esUrgente" id="urgente" />
          <label for="urgente" class="text-sm">Anuncio urgente</label>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dlgAnuncio=false" />
        <p-button label="Publicar" icon="pi pi-megaphone" [loading]="guardando()" (onClick)="publicarAnuncio()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .apex-page { padding: 1rem; }
    .apex-toolbar { display:flex; align-items:center; justify-content:space-between; margin-bottom:.75rem; flex-wrap:wrap; gap:.5rem; }
    .apex-title { margin:0; font-size:1.25rem; font-weight:600; }
    .apex-toolbar-actions { display:flex; gap:.5rem; align-items:center; }
    .anuncios-urgentes { margin-bottom:1rem; }
    .anuncio-urgente { background:var(--red-50); border:1px solid var(--red-300); border-radius:6px; padding:.5rem .75rem; margin-bottom:.5rem; display:flex; gap:.5rem; align-items:center; color:var(--red-800); }
    .section-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:.5rem; padding-bottom:.25rem; border-bottom:1px solid var(--surface-200); }
    .section-title { font-size:.95rem; font-weight:600; }
    .foros-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(260px,1fr)); gap:1rem; margin-top:.5rem; }
    .foro-card { background:var(--surface-50); border:1px solid var(--surface-200); border-radius:8px; padding:1rem; cursor:pointer; transition:border-color .2s; }
    .foro-card:hover { border-color:var(--primary-400); }
    .foro-card-header { display:flex; gap:.5rem; margin-bottom:.5rem; flex-wrap:wrap; }
    .foro-card-title { margin:.4rem 0 .25rem; font-size:.95rem; font-weight:600; }
    .foro-card-desc { font-size:.8rem; color:var(--text-color-secondary); margin:0 0 .75rem; }
    .foro-card-footer { display:flex; justify-content:space-between; align-items:center; }
    
    .mensajes-container { max-height:400px; overflow-y:auto; display:flex; flex-direction:column; gap:.75rem; padding:.5rem 0; }
    .mensaje-item { border:1px solid var(--surface-200); border-radius:6px; padding:.75rem; }
    .mensaje-header { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:.4rem; flex-wrap:wrap; gap:.25rem; }
    .mensaje-body { font-size:.875rem; margin:.25rem 0 .5rem; color:var(--text-color); }
    .mensaje-footer { display:flex; justify-content:space-between; align-items:center; }

    .respuestas-list { max-height:240px; overflow-y:auto; display:flex; flex-direction:column; gap:0.5rem; margin-bottom:1rem; border-bottom:1px solid var(--surface-200); padding-bottom:1rem; }
    .respuesta-item { border:1px solid var(--surface-100); background:var(--surface-50); border-radius:6px; padding:0.5rem; }
    .resp-header { display:flex; justify-content:space-between; margin-bottom:0.25rem; }
    .resp-body { font-size:0.8rem; }
    .text-xxs { font-size: 0.7rem; }
  `],
})
export class ForosComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private http = inject(ApiService);
  private notify = inject(ApexNotificationService);
  readonly ctx = inject(ContextService);

  foros    = signal<Foro[]>([]);
  anuncios = signal<Anuncio[]>([]);
  mensajes = signal<MensajeForo[]>([]);
  respuestas = signal<RespuestaForo[]>([]);
  materias = signal<any[]>([]);
  
  foroActivo = signal<Foro | null>(null);
  mensajeActivo = signal<MensajeForo | null>(null);
  filtroTipo = signal<string>('TODOS');
  
  cargandoForos = signal(false);
  cargandoAnuncios = signal(false);
  guardando = signal(false);
  moderandoId = signal<string | null>(null);

  dlgMensajes = false;
  dlgNuevoMensaje = false;
  dlgAnuncio = false;
  dlgForo = false;
  dlgRespuestas = false;

  nuevoMsgAsunto = '';
  nuevoMsgContenido = '';
  nuevaRespuestaTexto = '';

  foroForm = { nombre: '', descripcion: '', tipo: 'GENERAL', materia_id: null as string | null, es_moderado: false };
  anuncioForm: { titulo: string; contenido: string; fechaInicio: Date | null; esUrgente: boolean } =
    { titulo: '', contenido: '', fechaInicio: null, esUrgente: false };

  readonly tiposForo = ['GENERAL', 'PLANTEL', 'GRUPO', 'DOCENTES', 'MATERIA', 'TUTORES'];

  // computed signal to filter the active list
  forosFiltrados = computed(() => {
    const filter = this.filtroTipo();
    if (filter === 'TODOS') return this.foros();
    if (filter === 'GENERAL') return this.foros().filter(f => f.tipo === 'GENERAL' || f.tipo === 'PLANTEL');
    return this.foros().filter(f => f.tipo === filter);
  });

  ngOnInit() {
    this.cargarForos();
    this.cargarAnuncios();
    this.cargarMaterias();
  }

  esCoordinador(): boolean {
    const acc = this.ctx.nivelAcceso();
    return acc !== null && acc <= 3;
  }

  cambiarFiltro(tipo: string | number | undefined) {
    if (tipo !== undefined) {
      this.filtroTipo.set(String(tipo));
    }
  }

  cargarForos() {
    this.cargandoForos.set(true);
    this.http.get<Foro[]>('/foros').pipe(takeUntil(this.destroy$)).subscribe({
      next: d => { this.foros.set(d); this.cargandoForos.set(false); },
      error: () => { this.cargandoForos.set(false); this.notify.error('Error al cargar foros'); },
    });
  }

  cargarAnuncios() {
    this.cargandoAnuncios.set(true);
    this.http.get<Anuncio[]>('/foros/anuncios', { solo_vigentes: true }).pipe(takeUntil(this.destroy$)).subscribe({
      next: d => { this.anuncios.set(d); this.cargandoAnuncios.set(false); },
      error: () => { this.cargandoAnuncios.set(false); },
    });
  }

  cargarMaterias() {
    const pl = this.ctx.plantel();
    if (pl) {
      this.http.get<any[]>('/materias', { plantel_id: pl.id }).pipe(takeUntil(this.destroy$)).subscribe(m => this.materias.set(m));
    }
  }

  abrirForo(foro: Foro) {
    this.foroActivo.set(foro);
    this.mensajes.set([]);
    this.dlgMensajes = true;
    this.http.get<MensajeForo[]>(`/foros/${foro.id}/mensajes`).pipe(takeUntil(this.destroy$)).subscribe({
      next: d => this.mensajes.set(d),
      error: () => this.notify.error('Error al cargar mensajes'),
    });
  }

  abrirNuevoForo() {
    this.foroForm = { nombre: '', descripcion: '', tipo: 'GENERAL', materia_id: null, es_moderado: false };
    this.dlgForo = true;
  }

  crearForo() {
    if (!this.foroForm.nombre || !this.foroForm.tipo) {
      this.notify.warning('Nombre y tipo son obligatorios');
      return;
    }
    this.guardando.set(true);
    const pl = this.ctx.plantel();
    const payload = {
      ...this.foroForm,
      plantel_id: pl ? pl.id : null
    };
    this.http.post('/foros', payload).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.guardando.set(false);
        this.dlgForo = false;
        this.notify.success('Foro creado exitosamente');
        this.cargarForos();
      },
      error: e => { this.guardando.set(false); this.notify.error(e.error?.detail ?? 'Error al crear foro'); }
    });
  }

  abrirNuevoAnuncio() {
    this.anuncioForm = { titulo: '', contenido: '', fechaInicio: new Date(), esUrgente: false };
    this.dlgAnuncio = true;
  }

  verRespuestas(m: MensajeForo) {
    this.mensajeActivo.set(m);
    this.respuestas.set([]);
    this.nuevaRespuestaTexto = '';
    this.dlgRespuestas = true;
    this.cargarRespuestas(m.id);
  }

  cargarRespuestas(msgId: string) {
    const foro = this.foroActivo();
    if (!foro) return;
    this.http.get<RespuestaForo[]>(`/foros/${foro.id}/mensajes/${msgId}/respuestas`).pipe(takeUntil(this.destroy$)).subscribe({
      next: r => this.respuestas.set(r),
      error: () => this.notify.error('Error al cargar respuestas')
    });
  }

  abrirRespuesta(m: MensajeForo) {
    this.mensajeActivo.set(m);
    this.nuevaRespuestaTexto = '';
    this.dlgRespuestas = true;
    this.cargarRespuestas(m.id);
  }

  enviarRespuesta() {
    const msg = this.mensajeActivo();
    const foro = this.foroActivo();
    if (!msg || !foro || !this.nuevaRespuestaTexto.trim()) return;
    this.guardando.set(true);
    this.http.post(`/foros/${foro.id}/mensajes/${msg.id}/responder`, {
      contenido: this.nuevaRespuestaTexto
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.guardando.set(false);
        this.nuevaRespuestaTexto = '';
        this.notify.success('Respuesta publicada');
        this.cargarRespuestas(msg.id);
        // refresh message reply count
        this.abrirForo(foro);
      },
      error: () => this.guardando.set(false)
    });
  }

  moderar(mensajeId: string, estado: string) {
    this.moderandoId.set(mensajeId);
    this.http.patch(`/foros/mensajes/${mensajeId}/moderar?estado=${encodeURIComponent(estado)}`, {}).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.moderandoId.set(null);
        this.notify.success(`Mensaje moderado: ${estado}`);
        if (this.foroActivo()) this.abrirForo(this.foroActivo()!);
      },
      error: () => { this.moderandoId.set(null); this.notify.error('Error al moderar mensaje'); }
    });
  }

  publicarMensaje() {
    if (!this.foroActivo() || !this.nuevoMsgAsunto || !this.nuevoMsgContenido) {
      this.notify.warning('Asunto y contenido son obligatorios');
      return;
    }
    this.guardando.set(true);
    this.http.post(`/foros/${this.foroActivo()!.id}/mensajes`, {
      asunto: this.nuevoMsgAsunto,
      contenido: this.nuevoMsgContenido,
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.guardando.set(false); this.dlgNuevoMensaje = false;
        this.notify.success('Mensaje publicado');
        this.nuevoMsgAsunto = '';
        this.nuevoMsgContenido = '';
        this.abrirForo(this.foroActivo()!);
      },
      error: e => { this.guardando.set(false); this.notify.error(e.error?.detail ?? 'Error'); },
    });
  }

  publicarAnuncio() {
    if (!this.anuncioForm.titulo || !this.anuncioForm.contenido || !this.anuncioForm.fechaInicio) {
      this.notify.warning('Título, contenido y fecha son obligatorios');
      return;
    }
    this.guardando.set(true);
    const pl = this.ctx.plantel();
    this.http.post('/foros/anuncios', {
      titulo: this.anuncioForm.titulo,
      contenido: this.anuncioForm.contenido,
      fecha_inicio: this.anuncioForm.fechaInicio!.toISOString().substring(0, 10),
      es_urgente: this.anuncioForm.esUrgente,
      plantel_id: pl ? pl.id : null
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => { this.guardando.set(false); this.dlgAnuncio = false; this.notify.success('Anuncio publicado'); this.cargarAnuncios(); },
      error: e => { this.guardando.set(false); this.notify.error(e.error?.detail ?? 'Error'); },
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  anunciosUrgentes(): Anuncio[] {
    return this.anuncios().filter(a => a.es_urgente);
  }

  tipoSev(tipo: string): TagSev {
    const m: Record<string, TagSev> = { GRUPO: 'info', PLANTEL: 'success', GENERAL: 'secondary', DOCENTES: 'warn', MATERIA: 'info', TUTORES: 'contrast' };
    return m[tipo] ?? 'secondary';
  }
}

