import { Component, OnDestroy, inject, signal, OnInit, computed, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { ApiService } from '../../core/services/api.service';
import { ApexNotificationService } from 'apex-component-library';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';

interface NivelOpt  { id: string; nombre_nivel: string; escala_maxima: number; }
interface EsquemaRow { id: string; nombre: string; nombre_nivel: string; materia_id: string | null;
                        nombre_materia: string | null; vigente_desde: string; activo: boolean;
                        es_nee: boolean; items: ItemRow[]; }
interface ItemRow    { tipo_item: string; nombre_personalizado: string | null; peso_porcentaje: number; orden_display: number; }

/**
 * Configuración de ponderación de evaluaciones para el gradebook.
 * Permite definir el peso porcentual de cada tipo de ítem evaluativo
 * (tareas, exámenes, proyectos, participación) por materia y período.
 * Accesible desde nivelAcceso 0 (Admin Global) hasta 4 (Docente); el docente
 * solo puede crear/editar su propio esquema, el resto de roles gestiona el
 * esquema institucional o el de su plantel (ver EsquemasPonderacionController).
 */
@Component({
  selector: 'app-ponderacion-config',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonModule, SelectModule,
            InputTextModule, InputNumberModule, TagModule, DialogModule,
            InteractiveGridComponent],
  template: `
<div class="page-header">
  <div class="page-title"><i class="pi pi-sliders-h"></i> Configurar Ponderaciones</div>
  <div class="page-actions">
    <button pButton icon="pi pi-plus" label="Nuevo esquema" (click)="abrirNuevo()"></button>
  </div>
</div>

<div class="filter-bar">
  <p-select [options]="niveles()" optionLabel="nombre_nivel" optionValue="id"
            placeholder="Filtrar por nivel" [(ngModel)]="nivelFiltro"
            (onChange)="cargarEsquemas()" [showClear]="true" 
 [filter]="true" filterPlaceholder="Buscar..."/>
</div>

<app-interactive-grid
  [data]="esquemasFlat()"
  [columns]="esquemaColumns"
  [loading]="cargando()"
  [showDelete]="true"
  (rowSelected)="abrirEditar($event)"
  (rowDeleted)="desactivarEsquema($event.id)"
/>

<!-- Panel de ítems del esquema seleccionado -->
@if (esquemaSeleccionado()) {
  <div class="items-panel" style="margin-top:1rem; background:#f9fafb; border:1px solid var(--surface-200); border-radius:8px; padding:12px 16px;">
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
      <strong>Ítems de ponderación — {{ esquemaSeleccionado()!.nombre }}</strong>
      <button pButton icon="pi pi-times" text size="small" (click)="esquemaSeleccionado.set(null)"></button>
    </div>
    <div class="items-grid">
      @for (it of esquemaSeleccionado()!.items; track it.tipo_item) {
        <div class="item-pill">
          <span class="item-tipo">{{ it.nombre_personalizado ?? it.tipo_item }}</span>
          <span class="item-peso" [style.background]="pesoColor(it.peso_porcentaje)">
            {{ it.peso_porcentaje }}%
          </span>
        </div>
      }
      <div class="item-pill item-total"
           [class.suma-ok]="sumaItems(esquemaSeleccionado()!) === 100"
           [class.suma-error]="sumaItems(esquemaSeleccionado()!) !== 100">
        Total: {{ sumaItems(esquemaSeleccionado()!) }}%
      </div>
    </div>
  </div>
}

<!-- Dialog crear/editar -->
<p-dialog [(visible)]="dialogVisible" [header]="editandoId ? 'Editar esquema' : 'Nuevo esquema'"
          [modal]="true" [style]="{width:'600px'}">
  <div class="grid grid-cols-2 gap-3">
    <div class="field col-span-2">
      <label>Nombre *</label>
      <input pInputText [(ngModel)]="form.nombre" class="w-full" />
    </div>
    <div class="field">
      <label>Nivel educativo *</label>
      <p-select [options]="niveles()" optionLabel="nombre_nivel" optionValue="id"
                [(ngModel)]="form.nivel_educativo_id" styleClass="w-full" 
 [filter]="true" filterPlaceholder="Buscar..."/>
    </div>
    <div class="field">
      <label>Vigente desde</label>
      <input pInputText type="date" [(ngModel)]="form.vigente_desde" class="w-full" />
    </div>
    <div class="field col-span-2 flex items-center gap-2 mt-2">
      <input type="checkbox" id="es_nee" [(ngModel)]="form.es_nee" style="width: 20px; height: 20px; cursor: pointer;" />
      <label for="es_nee" style="cursor: pointer; font-weight: bold; margin-bottom: 0; display: inline-flex; align-items: center;">Aplica como adecuación curricular NEE (Necesidades Especiales)</label>
    </div>
  </div>

  <!-- Ítems -->
  <div class="mt-3">
    <div class="flex items-center justify-between mb-2">
      <strong>Ítems de ponderación</strong>
      <div [class]="sumaForm() === 100 ? 'text-success' : 'text-danger'" style="font-size:0.9rem">
        Suma: {{ sumaForm() }}% {{ sumaForm() === 100 ? '✓' : '≠ 100%' }}
      </div>
    </div>
    @for (item of form.items; track $index; let i = $index) {
      <div class="item-row flex gap-2 mb-2 items-center">
        <p-select [options]="tiposItem" optionLabel="label" optionValue="value"
                  [(ngModel)]="item.tipo_item" styleClass="flex-1" 
 [filter]="true" filterPlaceholder="Buscar..."/>
        <input pInputText [(ngModel)]="item.nombre_personalizado" placeholder="Nombre personalizado"
               style="flex:1;max-width:160px" />
        <p-inputNumber [(ngModel)]="item.peso_porcentaje" suffix="%" [min]="1" [max]="100"
                       [useGrouping]="false" inputStyleClass="w-16" />
        <button pButton icon="pi pi-trash" text severity="danger" size="small"
                (click)="quitarItem(i)" [disabled]="form.items.length <= 1"></button>
      </div>
    }
    <button pButton icon="pi pi-plus" label="Agregar ítem" text size="small"
            (click)="agregarItem()"></button>
  </div>

  <ng-template pTemplate="footer">
    <button pButton label="Cancelar" severity="secondary" (click)="dialogVisible = false"></button>
    <button pButton [label]="editandoId ? 'Guardar cambios' : 'Crear esquema'"
            (click)="guardar()" [disabled]="sumaForm() !== 100"></button>
  </ng-template>
</p-dialog>
  `,
  styles: [`
    .filter-bar { margin-bottom:16px; }
    .items-grid  { display:flex; flex-wrap:wrap; gap:8px; align-items:center; }
    .item-pill   { display:flex; align-items:center; gap:6px; background:#fff;
                   border:1px solid #e5e7eb; border-radius:20px; padding:4px 10px; }
    .item-tipo   { font-size:0.85rem; }
    .item-peso   { font-size:0.8rem; font-weight:700; color:#fff; border-radius:12px; padding:2px 8px; }
    .item-total  { font-weight:700; }
    .suma-ok     { color:#15803d; }
    .suma-error  { color:#dc2626; }
    .item-row    { align-items:center; }
  `],
})
export class PonderacionConfigComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private api = inject(ApiService);
  private readonly notify = inject(ApexNotificationService);

  niveles  = signal<NivelOpt[]>([]);
  esquemas = signal<EsquemaRow[]>([]);
  cargando = signal(false);
  nivelFiltro: string | null = null;
  dialogVisible = false;
  editandoId: string | null = null;
  esquemaSeleccionado = signal<EsquemaRow | null>(null);

  readonly esquemaColumns: ColumnConfig[] = [
    { field: 'nombre',         header: 'Nombre',             sortable: true, filterable: true },
    { field: 'nombre_nivel',   header: 'Nivel',              sortable: true, filterable: true, width: '140px' },
    { field: 'materiaDisplay', header: 'Materia específica', sortable: true, filterable: true },
    { field: 'neeLabel',       header: 'Tipo Esquema',       sortable: true, filterable: true, width: '120px' },
    { field: 'vigenteDisplay', header: 'Vigente desde',      sortable: true, filterable: false, width: '120px' },
    { field: 'activoLabel',    header: 'Estado',             sortable: true, filterable: true,  width: '90px' },
  ];

  readonly esquemasFlat = computed(() =>
    this.esquemas().map(esc => ({
      ...esc,
      materiaDisplay: esc.nombre_materia ?? '— (aplica a todo el nivel)',
      vigenteDisplay: esc.vigente_desde ? new Date(esc.vigente_desde).toLocaleDateString('es-MX') : '',
      activoLabel: esc.activo ? 'Activo' : 'Inactivo',
      neeLabel: esc.es_nee ? 'NEE (Diferenciado)' : 'General',
    }))
  );

  tiposItem = [
    { label: 'Examen',        value: 'examen' },
    { label: 'Tarea',         value: 'tarea' },
    { label: 'Proyecto',      value: 'proyecto' },
    { label: 'Asistencia',    value: 'asistencia' },
    { label: 'Comportamiento',value: 'comportamiento' },
    { label: 'Participación', value: 'participacion' },
    { label: 'Laboratorio',   value: 'laboratorio' },
    { label: 'Otro',          value: 'otro' },
  ];

  form: { nombre: string; nivel_educativo_id: string; vigente_desde: string;
          es_nee: boolean; items: ItemRow[] } = this.emptyForm();

  ngOnInit() {
    this.api.get('/catalogs/niveles').subscribe((r: any) => this.niveles.set(r ?? []));
    this.cargarEsquemas();
  }

  cargarEsquemas() {
    this.cargando.set(true);
    let url = '/esquemas-ponderacion';
    if (this.nivelFiltro) url += `?nivel_educativo_id=${this.nivelFiltro}`;
    this.api.get(url).subscribe({ next: (r: any) => { this.esquemas.set(r); this.cargando.set(false); },
      error: () => this.cargando.set(false) });
  }

  abrirNuevo() {
    this.editandoId = null;
    this.form = this.emptyForm();
    this.dialogVisible = true;
  }

  abrirEditar(esc: EsquemaRow) {
    // Find the original EsquemaRow with full items data
    const original = this.esquemas().find(e => e.id === esc.id) ?? esc;
    this.esquemaSeleccionado.set(original);
    this.editandoId = original.id;
    this.form = {
      nombre: original.nombre,
      nivel_educativo_id: '',  // will be loaded from row
      vigente_desde: original.vigente_desde,
      es_nee: original.es_nee ?? false,
      items: original.items.map(i => ({ ...i })),
    };
    // Obtener el nivel_educativo_id del esquema
    this.api.get(`/esquemas-ponderacion?nivel_educativo_id=*`).subscribe(); // noop — usamos lo que ya tenemos
    this.dialogVisible = true;
  }

  emptyForm() {
    return {
      nombre: '', nivel_educativo_id: '', vigente_desde: new Date().toISOString().slice(0, 10),
      es_nee: false,
      items: [{ tipo_item: 'examen', nombre_personalizado: '', peso_porcentaje: 70, orden_display: 1 },
              { tipo_item: 'tarea',  nombre_personalizado: '', peso_porcentaje: 20, orden_display: 2 },
              { tipo_item: 'asistencia', nombre_personalizado: '', peso_porcentaje: 10, orden_display: 3 }],
    };
  }

  agregarItem() {
    this.form.items.push({ tipo_item: 'otro', nombre_personalizado: '', peso_porcentaje: 0, orden_display: this.form.items.length + 1 });
  }

  quitarItem(i: number) { this.form.items.splice(i, 1); }

  sumaForm(): number { return this.form.items.reduce((acc, i) => acc + (i.peso_porcentaje || 0), 0); }
  sumaItems(esc: EsquemaRow): number { return (esc.items ?? []).reduce((acc, i) => acc + Number(i.peso_porcentaje), 0); }

  guardar() {
    const payload = {
      nombre: this.form.nombre,
      nivel_educativo_id: this.form.nivel_educativo_id,
      vigente_desde: this.form.vigente_desde,
      es_nee: this.form.es_nee ?? false,
      items: this.form.items.map((it, idx) => ({ ...it, orden_display: idx + 1 })),
    };
    const obs = this.editandoId
      ? this.api.put(`/esquemas-ponderacion/${this.editandoId}`, payload)
      : this.api.post('/esquemas-ponderacion', payload);
    obs.subscribe(() => {
      this.notify.success('Guardado');
      this.dialogVisible = false;
      this.cargarEsquemas();
    });
  }

  desactivarEsquema(id: string) {
    this.api.delete(`/esquemas-ponderacion/${id}`).subscribe(() => {
      this.notify.info('Desactivado');
      this.cargarEsquemas();
    });
  }

  pesoColor(peso: number): string {
    if (peso >= 50) return '#D02030';
    if (peso >= 20) return '#0369a1';
    return '#6b7280';
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
