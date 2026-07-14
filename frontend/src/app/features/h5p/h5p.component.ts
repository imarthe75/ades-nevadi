import { Component, OnDestroy, OnInit, signal, inject, computed, ElementRef, ViewChild, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { DatePipe } from '@angular/common';
import { DomSanitizer } from '@angular/platform-browser';
import { SafeResourceUrl } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';
import { CardModule } from 'primeng/card';
import { TabsModule } from 'primeng/tabs';
import { ProgressBarModule } from 'primeng/progressbar';
import { ApexNotificationService } from 'apex-component-library';
import { ContextService } from '../../core/services/context.service';
import { AuthService } from '../../core/services/auth.service';
import { ApiService } from '../../core/services/api.service';
import { grupoLabel } from '../../core/models';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';

interface H5PTipo {
  id: string;
  clave: string;
  nombre: string;
  icono: string;
}

interface H5PContenido {
  id: string;
  titulo: string;
  descripcion: string | null;
  h5p_content_id: string;
  h5p_library: string | null;
  tipo_nombre: string | null;
  tipo_icono: string | null;
  fecha_creacion: string;
}

interface H5PResultado {
  id: string;
  titulo: string;
  h5p_library: string | null;
  intento: number;
  score_raw: number | null;
  score_max: number | null;
  score_escalado: number | null;
  completado: boolean;
  aprobado: boolean | null;
  tiempo_segundos: number | null;
  fecha_creacion: string;
}

type TabKey = 'biblioteca' | 'mis-resultados';

/**
 * Módulo de contenido educativo interactivo H5P.
 * Embebe actividades H5P servidas por el servidor Node.js en el puerto 8091.
 * Muestra biblioteca de contenidos y resultados del alumno en pestañas separadas.
 */
@Component({
  selector: 'app-h5p',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule, DatePipe,
    TableModule, ButtonModule, DialogModule, InputTextModule,
    SelectModule, TagModule, TextareaModule, TooltipModule,
    CardModule, TabsModule, ProgressBarModule,
  ],
  template: `
<div class="apex-page">
  <!-- KPI Strip -->
  <div class="apex-kpi-strip" style="margin-bottom:1rem">
    <div class="apex-kpi-card">
      <span class="apex-kpi-value">{{ contenidos().length }}</span>
      <span class="apex-kpi-label">Contenidos H5P</span>
    </div>
    <div class="apex-kpi-card">
      <span class="apex-kpi-value">{{ resultadosMios().length }}</span>
      <span class="apex-kpi-label">Mis Actividades</span>
    </div>
    <div class="apex-kpi-card">
      <span class="apex-kpi-value">{{ completadas() }}</span>
      <span class="apex-kpi-label">Completadas</span>
    </div>
    <div class="apex-kpi-card">
      <span class="apex-kpi-value">{{ promedioScore() }}%</span>
      <span class="apex-kpi-label">Promedio Score</span>
    </div>
  </div>

  <p-tabs [value]="tabActivo()">
    <!-- ── Tab Biblioteca ── -->
    <p-tabpanel value="biblioteca" header="Biblioteca de Contenidos">
      <div class="apex-toolbar">
        @if (esDocente()) {
          <p-button label="Subir Contenido H5P" icon="pi pi-upload" (onClick)="abrirSubir()" />
        }
        <span class="apex-toolbar-spacer"></span>
        <p-select [options]="tipos()" [(ngModel)]="filtroTipo"
                  optionLabel="nombre" optionValue="id" placeholder="Filtrar por tipo"
                  [showClear]="true" (onChange)="cargarContenidos()" style="min-width:200px" />
      </div>

      <p-table [value]="contenidos()" [loading]="cargando()" stripedRows
               [paginator]="true" [rows]="10">
        <ng-template pTemplate="header">
          <tr>
            <th>Tipo</th>
            <th>Título</th>
            <th>Librería H5P</th>
            <th>Creado</th>
            <th style="width:130px">Acciones</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-c>
          <tr>
            <td><i [class]="c.tipo_icono || 'pi pi-box'" style="margin-right:.5rem"></i>{{ c.tipo_nombre || '—' }}</td>
            <td><strong>{{ c.titulo }}</strong><br/><small class="text-muted">{{ c.descripcion }}</small></td>
            <td><code style="font-size:.8em">{{ c.h5p_library || '—' }}</code></td>
            <td>{{ c.fecha_creacion | date:'dd/MM/yy' }}</td>
            <td>
              <p-button icon="pi pi-play" severity="success" [text]="true" size="small"
                        ariaLabel="Abrir el contenido H5P en el reproductor"
                        pTooltip="Abrir Player" (onClick)="abrirPlayer(c)" />
              @if (esDocente()) {
                <p-button icon="pi pi-share-alt" [text]="true" size="small"
                          ariaLabel="Asignar este contenido a un grupo de estudiantes"
                          pTooltip="Asignar a grupo" (onClick)="abrirAsignar(c)" />
                <p-button icon="pi pi-trash" severity="danger" [text]="true" size="small"
                          ariaLabel="Eliminar este contenido H5P"
                          pTooltip="Eliminar" (onClick)="confirmarEliminar(c)" />
              }
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr><td colspan="5" style="text-align:center;padding:2rem;color:var(--text-muted)">
            No hay contenidos H5P. @if(esDocente()) { Sube el primer paquete .h5p. }
          </td></tr>
        </ng-template>
      </p-table>
    </p-tabpanel>

    <!-- ── Tab Mis Resultados ── -->
    <p-tabpanel value="mis-resultados" header="Mis Resultados">
      <p-table [value]="resultadosMios()" [loading]="cargandoResultados()" stripedRows
               [paginator]="true" [rows]="10">
        <ng-template pTemplate="header">
          <tr>
            <th>Actividad</th>
            <th>Intento</th>
            <th>Score</th>
            <th>Completado</th>
            <th>Aprobado</th>
            <th>Tiempo</th>
            <th>Fecha</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-r>
          <tr>
            <td><strong>{{ r.titulo }}</strong><br/><small class="text-muted">{{ r.h5p_library }}</small></td>
            <td># {{ r.intento }}</td>
            <td>
              @if (r.score_raw != null) {
                <span>{{ r.score_raw }} / {{ r.score_max }}</span>
                <p-progressbar [value]="(r.score_escalado || 0) * 100"
                               style="height:6px;margin-top:4px" />
              } @else { — }
            </td>
            <td>
              <p-tag [value]="r.completado ? 'Sí' : 'No'"
                     [severity]="r.completado ? 'success' : 'secondary'" />
            </td>
            <td>
              @if (r.aprobado != null) {
                <p-tag [value]="r.aprobado ? 'Aprobado' : 'No aprobado'"
                       [severity]="r.aprobado ? 'success' : 'danger'" />
              } @else { — }
            </td>
            <td>{{ r.tiempo_segundos ? formatTiempo(r.tiempo_segundos) : '—' }}</td>
            <td>{{ r.fecha_creacion | date:'dd/MM/yy HH:mm' }}</td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr><td colspan="7" style="text-align:center;padding:2rem;color:var(--text-muted)">
            Aún no tienes resultados de actividades H5P.
          </td></tr>
        </ng-template>
      </p-table>
    </p-tabpanel>
  </p-tabs>
</div>

<!-- Dialog — Player H5P -->
<p-dialog [(visible)]="playerVisible" [header]="playerTitulo()" [modal]="true"
          [style]="{width:'90vw', maxWidth:'1000px', height:'85vh'}">
  @if (playerUrl()) {
    <iframe [src]="playerSafeUrl()"
            style="width:100%;height:calc(85vh - 120px);border:none"
            allow="fullscreen"
            title="H5P Content Player">
    </iframe>
  }
</p-dialog>

<!-- Dialog — Subir H5P -->
<p-dialog [(visible)]="subirVisible" header="Subir Contenido H5P" [modal]="true"
          [style]="{width:'520px'}">
  <div style="display:flex;flex-direction:column;gap:1rem;padding:.5rem 0">
    <div>
      <label class="apex-label">Título *</label>
      <input pInputText [(ngModel)]="subirForm.titulo" class="w-full" placeholder="Nombre del contenido" />
    </div>
    <div>
      <label class="apex-label">Descripción</label>
      <textarea pTextarea [(ngModel)]="subirForm.descripcion" rows="2" class="w-full"
                placeholder="Descripción breve para los alumnos"></textarea>
    </div>
    <div>
      <label class="apex-label">Tipo de contenido</label>
      <p-select [options]="tipos()" [(ngModel)]="subirForm.tipo_id"
                optionLabel="nombre" optionValue="id" placeholder="Seleccionar tipo"
                [showClear]="true" class="w-full" />
    </div>
    <div>
      <label class="apex-label">Archivo .h5p *</label>
      <input type="file" accept=".h5p" (change)="onFileSelect($event)"
             style="display:block;margin-top:.25rem" />
      @if (subirArchivo) {
        <small style="color:var(--color-success)">{{ subirArchivo.name }} ({{ (subirArchivo.size/1024/1024).toFixed(1) }} MB)</small>
      }
    </div>
  </div>
  <ng-template pTemplate="footer">
    <p-button label="Cancelar" severity="secondary" (onClick)="subirVisible = false" />
    <p-button label="Subir" icon="pi pi-upload" [loading]="subiendo()" (onClick)="subirH5P()" />
  </ng-template>
</p-dialog>

<!-- Dialog — Asignar -->
<p-dialog [(visible)]="asignarVisible" header="Asignar a Grupo" [modal]="true"
          [style]="{width:'460px'}">
  <div style="display:flex;flex-direction:column;gap:1rem;padding:.5rem 0">
    <div>
      <label class="apex-label">Grupo</label>
      <p-select [options]="grupos()" [(ngModel)]="asignarForm.grupo_id"
                optionLabel="_label" optionValue="id" placeholder="Seleccionar grupo"
                class="w-full" />
    </div>
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:.75rem">
      <div>
        <label class="apex-label">Fecha desde</label>
        <input pInputText type="date" [(ngModel)]="asignarForm.fecha_desde" class="w-full" />
      </div>
      <div>
        <label class="apex-label">Fecha hasta</label>
        <input pInputText type="date" [(ngModel)]="asignarForm.fecha_hasta" class="w-full" />
      </div>
    </div>
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:.75rem">
      <div>
        <label class="apex-label">Intentos máx.</label>
        <input pInputText type="number" [(ngModel)]="asignarForm.intentos_max" class="w-full" min="1" max="10" />
      </div>
      <div>
        <label class="apex-label">Puntaje mínimo (%)</label>
        <input pInputText type="number" [(ngModel)]="asignarForm.puntaje_minimo" class="w-full" min="0" max="100" />
      </div>
    </div>
  </div>
  <ng-template pTemplate="footer">
    <p-button label="Cancelar" severity="secondary" (onClick)="asignarVisible = false" />
    <p-button label="Asignar" icon="pi pi-check" [loading]="asignando()" (onClick)="guardarAsignacion()" />
  </ng-template>
</p-dialog>
  `,
})
export class H5pComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private api = inject(ApiService);
  private notify = inject(ApexNotificationService);
  private ctx = inject(ContextService);
  private auth = inject(AuthService);
  private sanitizer = inject(DomSanitizer);

  // State
  contenidos = signal<H5PContenido[]>([]);
  tipos = signal<H5PTipo[]>([]);
  grupos = signal<any[]>([]);
  resultadosMios = signal<H5PResultado[]>([]);
  cargando = signal(false);
  cargandoResultados = signal(false);
  subiendo = signal(false);
  asignando = signal(false);
  tabActivo = signal<TabKey>('biblioteca');
  filtroTipo: string | null = null;

  // Player
  playerVisible = false;
  playerUrl = signal('');
  playerTitulo = signal('');
  playerSafeUrl = computed(() =>
    this.sanitizer.bypassSecurityTrustResourceUrl(this.playerUrl())
  );

  // Subir
  subirVisible = false;
  subirArchivo: File | null = null;
  subirForm = { titulo: '', descripcion: '', tipo_id: null as string | null };

  // Asignar
  asignarVisible = false;
  asignarContenidoId = '';
  asignarForm = { grupo_id: '', fecha_desde: '', fecha_hasta: '', intentos_max: 3, puntaje_minimo: 60 };

  // Computed KPIs
  completadas = computed(() => this.resultadosMios().filter(r => r.completado).length);
  promedioScore = computed(() => {
    const con = this.resultadosMios().filter(r => r.score_escalado != null);
    if (!con.length) return 0;
    return Math.round(con.reduce((s, r) => s + (r.score_escalado! * 100), 0) / con.length);
  });
  esDocente = computed(() => (this.ctx.nivelAcceso() ?? 5) <= 4);

  ngOnInit() {
    this.cargarTipos();
    this.cargarContenidos();
    this.cargarGrupos();
    this.cargarMisResultados();
  }

  cargarTipos() {
    this.api.get<H5PTipo[]>('/h5p/tipos').pipe(takeUntil(this.destroy$)).subscribe({ next: t => this.tipos.set(t), error: () => {} });
  }

  cargarContenidos() {
    this.cargando.set(true);
    const params: any = {};
    if (this.filtroTipo) params.tipo_id = this.filtroTipo;
    if (this.ctx.plantel()?.id) params.plantel_id = this.ctx.plantel()?.id;
    this.api.get<H5PContenido[]>('/h5p/contenidos', params).pipe(takeUntil(this.destroy$)).subscribe({
      next: c => { this.contenidos.set(c); this.cargando.set(false); },
      error: () => this.cargando.set(false),
    });
  }

  cargarGrupos() {
    this.api.get<any[]>('/grupos').pipe(takeUntil(this.destroy$)).subscribe({
      next: g => this.grupos.set(g.map((x: any) => ({ ...x, _label: grupoLabel(x) || x.nombre_grupo }))),
      error: () => {},
    });
  }

  cargarMisResultados() {
    this.cargandoResultados.set(true);
    this.api.get<H5PResultado[]>('/h5p/mis-resultados').pipe(takeUntil(this.destroy$)).subscribe({
      next: r => { this.resultadosMios.set(r); this.cargandoResultados.set(false); },
      error: () => this.cargandoResultados.set(false),
    });
  }

  abrirPlayer(c: H5PContenido) {
    this.api.get<{ player_url: string }>(`/h5p/player/${c.id}`).pipe(takeUntil(this.destroy$)).subscribe({
      next: d => {
        this.playerUrl.set(d.player_url);
        this.playerTitulo.set(c.titulo);
        this.playerVisible = true;
      },
      error: (e: any) => this.notify.error(e?.error?.detail || 'No se pudo abrir el player H5P'),
    });
  }

  abrirSubir() {
    this.subirForm = { titulo: '', descripcion: '', tipo_id: null };
    this.subirArchivo = null;
    this.subirVisible = true;
  }

  onFileSelect(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) this.subirArchivo = input.files[0];
  }

  subirH5P() {
    if (!this.subirForm.titulo || !this.subirArchivo) {
      this.notify.warning('Completa el título y selecciona un archivo .h5p');
      return;
    }
    this.subiendo.set(true);
    const fd = new FormData();
    fd.append('titulo', this.subirForm.titulo);
    fd.append('descripcion', this.subirForm.descripcion);
    if (this.subirForm.tipo_id) fd.append('tipo_id', this.subirForm.tipo_id);
    fd.append('h5p_file', this.subirArchivo);
    this.api.postForm('/h5p/subir', fd).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.notify.success('Contenido H5P subido correctamente');
        this.subirVisible = false;
        this.subiendo.set(false);
        this.cargarContenidos();
      },
      error: (e) => {
        this.notify.error(e?.error?.detail || 'Error al subir el archivo H5P');
        this.subiendo.set(false);
      },
    });
  }

  abrirAsignar(c: H5PContenido) {
    this.asignarContenidoId = c.id;
    this.asignarForm = { grupo_id: '', fecha_desde: '', fecha_hasta: '', intentos_max: 3, puntaje_minimo: 60 };
    this.asignarVisible = true;
  }

  guardarAsignacion() {
    if (!this.asignarForm.grupo_id) {
      this.notify.warning('Selecciona un grupo');
      return;
    }
    this.asignando.set(true);
    this.api.post('/h5p/asignaciones', {
      contenido_id: this.asignarContenidoId,
      grupo_id: this.asignarForm.grupo_id,
      fecha_desde: this.asignarForm.fecha_desde || null,
      fecha_hasta: this.asignarForm.fecha_hasta || null,
      intentos_max: this.asignarForm.intentos_max,
      puntaje_minimo: this.asignarForm.puntaje_minimo,
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.notify.success('Contenido asignado al grupo');
        this.asignarVisible = false;
        this.asignando.set(false);
      },
      error: () => { this.notify.error('Error al asignar'); this.asignando.set(false); },
    });
  }

  confirmarEliminar(c: H5PContenido) {
    if (!confirm(`¿Eliminar "${c.titulo}"? Se perderán todos los resultados asociados.`)) return;
    this.api.delete(`/h5p/contenidos/${c.id}`).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => { this.notify.success('Contenido eliminado'); this.cargarContenidos(); },
      error: () => this.notify.error('Error al eliminar'),
    });
  }

  formatTiempo(segundos: number): string {
    const m = Math.floor(segundos / 60);
    const s = segundos % 60;
    return m > 0 ? `${m}m ${s}s` : `${s}s`;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
