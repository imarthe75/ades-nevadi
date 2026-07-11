import { Component, OnInit, OnDestroy, signal, computed, inject, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { TabsModule, TabList, Tab, TabPanels, TabPanel } from 'primeng/tabs';
import { MessageService } from 'primeng/api';
import { ApexNotificationService } from 'apex-component-library';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';

interface Libro {
  id: string;
  titulo: string;
  autor: string | null;
  isbn: string | null;
  editorial: string | null;
  anio_publicacion: number | null;
  categoria: string;
  ubicacion: string | null;
  plantel_id: string | null;
  plantel_nombre: string | null;
  ejemplares_total: number;
  ejemplares_disponibles: number;
}

interface Prestamo {
  id: string;
  libro_id: string;
  libro_titulo: string;
  persona_id: string;
  persona_nombre: string;
  numero_control: string | null;
  fecha_prestamo: string;
  fecha_devolucion_esperada: string;
  fecha_devolucion_real: string | null;
  estatus: string;
  observaciones: string | null;
  vencido: boolean;
}

const CATEGORIAS = [
  { label: 'Literatura',  value: 'LITERATURA' },
  { label: 'Ciencia',     value: 'CIENCIA' },
  { label: 'Historia',    value: 'HISTORIA' },
  { label: 'Matemáticas', value: 'MATEMATICAS' },
  { label: 'Arte',        value: 'ARTE' },
  { label: 'Tecnología',  value: 'TECNOLOGIA' },
  { label: 'Infantil',    value: 'INFANTIL' },
  { label: 'Consulta',    value: 'CONSULTA' },
  { label: 'Texto',       value: 'TEXTO' },
  { label: 'Otro',        value: 'OTRO' },
];

/**
 * Gestión del acervo bibliográfico y préstamos del Instituto Nevadi.
 * Administra el catálogo de libros (título, autor, ISBN, categoría, ejemplares)
 * y el ciclo de vida de préstamos: activo → devuelto / vencido. Controla el
 * decremento atómico de ejemplares disponibles vía BFF. Organizado en pestañas
 * Libros / Préstamos con `InteractiveGridComponent`. Requiere nivelAcceso ≥ 4
 * para altas y bajas; nivelAcceso ≥ 3 puede consultar disponibilidad.
 */
@Component({
  selector: 'app-biblioteca',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule, ButtonModule, DialogModule, InputTextModule,
    InputNumberModule, SelectModule, ToastModule, TooltipModule,
    TabsModule, TabList, Tab, TabPanels, TabPanel,
    InteractiveGridComponent,
  ],
  providers: [MessageService],
  template: `
    <p-toast />
    <div class="apex-page">
      <div class="apex-toolbar">
        <h2 class="apex-title">Biblioteca</h2>
      </div>

      <p-tabs [(value)]="tab">
        <p-tablist>
          <p-tab value="acervo">Acervo</p-tab>
          <p-tab value="prestamos">Préstamos</p-tab>
        </p-tablist>

        <p-tabpanels>
          <!-- ── ACERVO ─────────────────────────────────────────────── -->
          <p-tabpanel value="acervo">
            <div class="apex-toolbar-actions">
              <input pInputText [(ngModel)]="busqueda" placeholder="Título, autor o ISBN…"
                style="width:260px" (keyup.enter)="cargarLibros()" />
              <p-select [options]="categorias" optionLabel="label" optionValue="value"
                [(ngModel)]="filtroCategoria" placeholder="Categoría" [showClear]="true"
                (onChange)="cargarLibros()" style="width:170px" />
              <p-button label="Buscar" icon="pi pi-search" size="small" (onClick)="cargarLibros()" />
              <p-button label="Nuevo libro" icon="pi pi-plus" size="small" (onClick)="abrirNuevoLibro()" />
            </div>

            <app-interactive-grid
              [data]="librosFlat()"
              [columns]="librosColumns"
              [loading]="cargandoLibros()"
              [showDelete]="true"
              (rowSelected)="abrirEdicionLibro($event)"
              (rowDeleted)="eliminarLibro($event)"
            />
          </p-tabpanel>

          <!-- ── PRÉSTAMOS ──────────────────────────────────────────── -->
          <p-tabpanel value="prestamos">
            <div class="apex-toolbar-actions">
              <p-select [options]="estatusFiltro" optionLabel="label" optionValue="value"
                [(ngModel)]="filtroEstatus" placeholder="Estatus" [showClear]="true"
                (onChange)="cargarPrestamos()" style="width:170px" />
              <input pInputText [(ngModel)]="filtroPersonaId" placeholder="UUID persona…"
                style="width:240px" (keyup.enter)="cargarPrestamos()" />
              <p-button label="Buscar" icon="pi pi-search" size="small" (onClick)="cargarPrestamos()" />
              <p-button label="Nuevo préstamo" icon="pi pi-book" size="small" (onClick)="abrirNuevoPrestamo()" />
            </div>

            <app-interactive-grid
              [data]="prestamosFlat()"
              [columns]="prestamosColumns"
              [loading]="cargandoPrestamos()"
              (rowSelected)="abrirDevolucion($event)"
            />
          </p-tabpanel>
        </p-tabpanels>
      </p-tabs>
    </div>

    <!-- Dialog: Crear/Editar libro -->
    <p-dialog [header]="editandoLibroId ? 'Editar Libro' : 'Nuevo Libro'"
      [(visible)]="dialogLibro" [modal]="true" [style]="{width:'620px'}" [draggable]="false">
      <div class="grid grid-cols-2 gap-3 p-3">
        <div class="col-span-2 flex flex-col gap-1">
          <label class="text-sm font-medium">Título *</label>
          <input pInputText [(ngModel)]="formLibro.titulo" placeholder="Título del libro" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Autor</label>
          <input pInputText [(ngModel)]="formLibro.autor" placeholder="Autor" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Categoría *</label>
          <p-select [options]="categorias" optionLabel="label" optionValue="value"
            [(ngModel)]="formLibro.categoria" placeholder="Seleccionar…" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">ISBN</label>
          <input pInputText [(ngModel)]="formLibro.isbn" placeholder="978-..." />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Editorial</label>
          <input pInputText [(ngModel)]="formLibro.editorial" placeholder="Editorial" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Año</label>
          <p-inputNumber [(ngModel)]="formLibro.anio_publicacion" [useGrouping]="false"
            placeholder="2024" [min]="1400" [max]="2200" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Ubicación (estante)</label>
          <input pInputText [(ngModel)]="formLibro.ubicacion" placeholder="Ej: Estante A-3" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Ejemplares (total)</label>
          <p-inputNumber [(ngModel)]="formLibro.ejemplares_total" [min]="0" [showButtons]="true" />
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dialogLibro=false" />
        <p-button [label]="editandoLibroId ? 'Guardar cambios' : 'Registrar libro'"
          icon="pi pi-save" [loading]="guardando()" (onClick)="guardarLibro()" />
      </ng-template>
    </p-dialog>

    <!-- Dialog: Nuevo préstamo -->
    <p-dialog header="Nuevo Préstamo" [(visible)]="dialogPrestamo" [modal]="true"
      [style]="{width:'560px'}" [draggable]="false">
      <div class="grid grid-cols-2 gap-3 p-3">
        <div class="col-span-2 flex flex-col gap-1">
          <label class="text-sm font-medium">Libro *</label>
          <p-select [options]="librosDisponibles()" optionLabel="label" optionValue="value"
            [(ngModel)]="formPrestamo.libro_id" [filter]="true" placeholder="Seleccionar libro disponible…" />
        </div>
        <div class="col-span-2 flex flex-col gap-1">
          <label class="text-sm font-medium">UUID Persona (alumno o personal) *</label>
          <input pInputText [(ngModel)]="formPrestamo.persona_id" placeholder="uuid de la persona" />
        </div>
        <div class="col-span-2 flex flex-col gap-1">
          <label class="text-sm font-medium">Fecha devolución esperada *</label>
          <input pInputText type="date" [(ngModel)]="formPrestamo.fecha_devolucion_esperada" />
        </div>
        <div class="col-span-2 flex flex-col gap-1">
          <label class="text-sm font-medium">Observaciones</label>
          <input pInputText [(ngModel)]="formPrestamo.observaciones" placeholder="Opcional" />
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dialogPrestamo=false" />
        <p-button label="Registrar préstamo" icon="pi pi-book" [loading]="guardando()" (onClick)="guardarPrestamo()" />
      </ng-template>
    </p-dialog>

    <!-- Dialog: Devolución -->
    <p-dialog header="Devolución / Cierre de préstamo" [(visible)]="dialogDevolucion"
      [modal]="true" [style]="{width:'520px'}" [draggable]="false">
      @if (prestamoSel) {
        <div class="p-3 flex flex-col gap-2">
          <div><strong>Libro:</strong> {{ prestamoSel.libro_titulo }}</div>
          <div><strong>Persona:</strong> {{ prestamoSel.persona_nombre }}</div>
          <div><strong>Prestado:</strong> {{ prestamoSel.fecha_prestamo }} ·
            <strong>Devolver:</strong> {{ prestamoSel.fecha_devolucion_esperada }}</div>
          <div><strong>Estatus:</strong> {{ prestamoSel.estatus }}
            @if (prestamoSel.vencido) { <span style="color:var(--red-600)">(VENCIDO)</span> }
          </div>
          @if (prestamoSel.estatus === 'PRESTADO') {
            <div class="flex flex-col gap-1 mt-2">
              <label class="text-sm font-medium">Cierre</label>
              <p-select [options]="cierreOpts" optionLabel="label" optionValue="value"
                [(ngModel)]="cierreEstatus" />
            </div>
          } @else {
            <div class="text-gray-500 mt-2">Este préstamo ya está cerrado.</div>
          }
        </div>
      }
      <ng-template pTemplate="footer">
        <p-button label="Cerrar" [text]="true" (onClick)="dialogDevolucion=false" />
        @if (prestamoSel?.estatus === 'PRESTADO') {
          <p-button label="Registrar cierre" icon="pi pi-check" [loading]="guardando()"
            (onClick)="confirmarDevolucion()" />
        }
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .apex-page { padding: 1rem; }
    .apex-toolbar { display:flex; align-items:center; justify-content:space-between; margin-bottom:.5rem; }
    .apex-title { margin:0; font-size:1.25rem; font-weight:600; }
    .apex-toolbar-actions { display:flex; gap:.5rem; align-items:center; flex-wrap:wrap; margin:.75rem 0; }
  `],
})
export class BibliotecaComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private api = inject(ApiService);
  private notify = inject(ApexNotificationService);

  tab = 'acervo';
  categorias = CATEGORIAS;
  estatusFiltro = [
    { label: 'Prestado',   value: 'PRESTADO' },
    { label: 'Devuelto',   value: 'DEVUELTO' },
    { label: 'Extraviado', value: 'EXTRAVIADO' },
  ];

  readonly librosColumns: ColumnConfig[] = [
    { field: 'titulo',        header: 'Título' },
    { field: 'autor_str',     header: 'Autor',      width: '170px' },
    { field: 'categoria',     header: 'Categoría',  width: '120px' },
    { field: 'ubicacion_str', header: 'Ubicación',  width: '130px' },
    { field: 'disp_str',      header: 'Disponibles', width: '110px', align: 'center' },
  ];

  readonly prestamosColumns: ColumnConfig[] = [
    { field: 'libro_titulo',   header: 'Libro' },
    { field: 'persona_str',    header: 'Persona',     width: '200px' },
    { field: 'fecha_prestamo', header: 'Prestado',    width: '110px' },
    { field: 'fecha_esp_str',  header: 'Devolver',    width: '130px' },
    { field: 'estatus_str',    header: 'Estatus',     width: '120px' },
  ];

  libros           = signal<Libro[]>([]);
  prestamos        = signal<Prestamo[]>([]);
  cargandoLibros   = signal(false);
  cargandoPrestamos = signal(false);
  guardando        = signal(false);

  readonly librosFlat = computed(() =>
    this.libros().map(l => ({
      ...l,
      autor_str:     l.autor ?? '—',
      ubicacion_str: l.ubicacion ?? '—',
      disp_str:      `${l.ejemplares_disponibles} / ${l.ejemplares_total}`,
    }))
  );

  readonly librosDisponibles = computed(() =>
    this.libros()
      .filter(l => l.ejemplares_disponibles > 0)
      .map(l => ({ label: `${l.titulo} (${l.ejemplares_disponibles} disp.)`, value: l.id }))
  );

  readonly prestamosFlat = computed(() =>
    this.prestamos().map(p => ({
      ...p,
      persona_str:   p.numero_control ? `${p.persona_nombre} (${p.numero_control})` : p.persona_nombre,
      fecha_esp_str: p.vencido ? `${p.fecha_devolucion_esperada} ⚠` : p.fecha_devolucion_esperada,
      estatus_str:   p.estatus === 'PRESTADO' && p.vencido ? 'VENCIDO' : p.estatus,
    }))
  );

  busqueda = '';
  filtroCategoria: string | null = null;
  filtroEstatus: string | null = null;
  filtroPersonaId = '';

  dialogLibro = false;
  dialogPrestamo = false;
  dialogDevolucion = false;
  editandoLibroId: string | null = null;
  prestamoSel: Prestamo | null = null;
  cierreEstatus = 'DEVUELTO';
  cierreOpts = [
    { label: 'Devuelto (regresa al acervo)', value: 'DEVUELTO' },
    { label: 'Extraviado (se pierde la copia)', value: 'EXTRAVIADO' },
  ];

  formLibro = {
    titulo: '', autor: '', isbn: '', editorial: '',
    anio_publicacion: null as number | null, categoria: '', ubicacion: '',
    ejemplares_total: 1,
  };

  formPrestamo = {
    libro_id: '', persona_id: '', fecha_devolucion_esperada: '', observaciones: '',
  };

  ngOnInit() { this.cargarLibros(); }

  // ── Acervo ───────────────────────────────────────────────────────────────
  cargarLibros() {
    this.cargandoLibros.set(true);
    const params: any = {};
    if (this.busqueda) params.q = this.busqueda;
    if (this.filtroCategoria) params.categoria = this.filtroCategoria;
    this.api.get<Libro[]>('/biblioteca/libros', params).subscribe({
      next: d => { this.libros.set(d); this.cargandoLibros.set(false); },
      error: () => { this.cargandoLibros.set(false); this.notify.error('Error al cargar el acervo'); },
    });
  }

  abrirNuevoLibro() {
    this.editandoLibroId = null;
    this.formLibro = { titulo: '', autor: '', isbn: '', editorial: '',
      anio_publicacion: null, categoria: '', ubicacion: '', ejemplares_total: 1 };
    this.dialogLibro = true;
  }

  abrirEdicionLibro(l: Libro) {
    this.editandoLibroId = l.id;
    this.formLibro = {
      titulo: l.titulo, autor: l.autor ?? '', isbn: l.isbn ?? '', editorial: l.editorial ?? '',
      anio_publicacion: l.anio_publicacion, categoria: l.categoria,
      ubicacion: l.ubicacion ?? '', ejemplares_total: l.ejemplares_total,
    };
    this.dialogLibro = true;
  }

  guardarLibro() {
    if (!this.formLibro.titulo || !this.formLibro.categoria) {
      this.notify.warning('Título y categoría son obligatorios');
      return;
    }
    this.guardando.set(true);
    const req$ = this.editandoLibroId
      ? this.api.patch(`/biblioteca/libros/${this.editandoLibroId}`, this.formLibro)
      : this.api.post('/biblioteca/libros', this.formLibro);
    req$.subscribe({
      next: () => { this.guardando.set(false); this.dialogLibro = false; this.notify.success('Libro guardado'); this.cargarLibros(); },
      error: e => { this.guardando.set(false); this.notify.error(e.error?.message ?? e.error?.detail ?? 'Error al guardar'); },
    });
  }

  eliminarLibro(l: Libro) {
    if (!confirm(`¿Eliminar "${l.titulo}" del acervo?`)) return;
    this.api.delete(`/biblioteca/libros/${l.id}`).subscribe({
      next: () => { this.notify.success('Libro eliminado'); this.cargarLibros(); },
      error: e => this.notify.error(e.error?.message ?? e.error?.detail ?? 'Error'),
    });
  }

  // ── Préstamos ────────────────────────────────────────────────────────────
  cargarPrestamos() {
    this.cargandoPrestamos.set(true);
    const params: any = {};
    if (this.filtroEstatus) params.estatus = this.filtroEstatus;
    if (this.filtroPersonaId) params.persona_id = this.filtroPersonaId;
    this.api.get<Prestamo[]>('/biblioteca/prestamos', params).subscribe({
      next: d => { this.prestamos.set(d); this.cargandoPrestamos.set(false); },
      error: () => { this.cargandoPrestamos.set(false); this.notify.error('Error al cargar préstamos'); },
    });
  }

  abrirNuevoPrestamo() {
    if (this.libros().length === 0) this.cargarLibros();
    this.formPrestamo = { libro_id: '', persona_id: '', fecha_devolucion_esperada: '', observaciones: '' };
    this.dialogPrestamo = true;
  }

  guardarPrestamo() {
    if (!this.formPrestamo.libro_id || !this.formPrestamo.persona_id || !this.formPrestamo.fecha_devolucion_esperada) {
      this.notify.warning('Libro, persona y fecha de devolución son obligatorios');
      return;
    }
    this.guardando.set(true);
    this.api.post('/biblioteca/prestamos', this.formPrestamo).subscribe({
      next: () => {
        this.guardando.set(false); this.dialogPrestamo = false;
        this.notify.success('Préstamo registrado'); this.cargarLibros(); this.cargarPrestamos();
      },
      error: e => { this.guardando.set(false); this.notify.error(e.error?.message ?? e.error?.detail ?? 'Error al prestar'); },
    });
  }

  abrirDevolucion(p: Prestamo) {
    this.prestamoSel = p;
    this.cierreEstatus = 'DEVUELTO';
    this.dialogDevolucion = true;
  }

  confirmarDevolucion() {
    if (!this.prestamoSel) return;
    this.guardando.set(true);
    this.api.post(`/biblioteca/prestamos/${this.prestamoSel.id}/devolver`,
      { estatus_final: this.cierreEstatus }).subscribe({
      next: () => {
        this.guardando.set(false); this.dialogDevolucion = false;
        this.notify.success('Préstamo cerrado'); this.cargarLibros(); this.cargarPrestamos();
      },
      error: e => { this.guardando.set(false); this.notify.error(e.error?.message ?? e.error?.detail ?? 'Error'); },
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
