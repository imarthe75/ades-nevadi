import { Component, OnDestroy, OnInit, inject, signal, computed, effect, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { TooltipModule } from 'primeng/tooltip';
import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ApexNotificationService } from 'apex-component-library';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';

interface CicloOpt   { id: string; nombre_ciclo: string; }
interface PlantelOpt { id: string; nombre_plantel: string; }

interface EventoForm {
  ciclo_escolar_id: string;
  fecha_evento: string;
  nombre_evento: string;
  tipo_evento: string;
  aplica_todos_planteles: boolean;
  plantel_id: string | null;
}

/**
 * Calendario escolar institucional con eventos por ciclo y plantel.
 * Permite registrar y visualizar eventos académicos, administrativos y
 * festivos. Los eventos pueden aplicar a todos los planteles o a uno específico.
 * Usa `InteractiveGridComponent` para edición inline. Integra `ContextService`
 * para respetar el ciclo activo. Requiere nivelAcceso ≥ 4 para mutaciones.
 */
@Component({
  selector: 'app-calendario',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule,
    ButtonModule, SelectModule, TagModule,
    DialogModule, TooltipModule,
    InteractiveGridComponent,
  ],
  template: `
<div class="page-header">
  <div class="page-title"><i class="pi pi-calendar"></i> Calendario Escolar</div>
  <div class="page-actions">
    <button pButton icon="pi pi-plus" label="Nuevo evento"
            (click)="abrirNuevo()"></button>
  </div>
</div>

<div class="filter-bar">
  <p-select [options]="ciclos()" optionLabel="nombre_ciclo" optionValue="id"
            placeholder="Ciclo escolar" [(ngModel)]="cicloId"
            (onChange)="cargar()" [filter]="true" filterPlaceholder="Buscar..." styleClass="w-220" />
  <p-select [options]="tipoOpts" optionLabel="label" optionValue="value"
            placeholder="Todos los tipos" [(ngModel)]="tipoFiltro"
            (onChange)="cargar()" [showClear]="true" styleClass="w-200" />
</div>

<!-- Resumen por tipo -->
@if (resumen().length) {
  <div class="resumen-row">
    @for (item of resumen(); track item.tipo) {
      <div class="resumen-chip">
        <p-tag [value]="item.tipo" [severity]="tipoSeverity(item.tipo)" />
        <span class="resumen-count">{{ item.count }}</span>
      </div>
    }
  </div>
}

<app-interactive-grid
  [data]="eventos()"
  [columns]="columnas"
  [loading]="cargando()"
  (rowSelected)="abrirEditar($event)"
/>

<!-- Dialog crear/editar -->
<p-dialog [(visible)]="dialogVisible" [header]="esNuevo ? 'Nuevo evento' : 'Editar evento'"
          [modal]="true" [style]="{width:'520px'}">
  <div class="grid-form">
    <div class="field">
      <label for="ciclo-ev">Ciclo escolar *</label>
      <p-select id="ciclo-ev" [options]="ciclos()" optionLabel="nombre_ciclo" optionValue="id"
                [(ngModel)]="form.ciclo_escolar_id" styleClass="w-full"
                [filter]="true" filterPlaceholder="Buscar..." />
    </div>
    <div class="field">
      <label for="tipo-ev">Tipo *</label>
      <p-select id="tipo-ev" [options]="tipoOpts" optionLabel="label" optionValue="value"
                [(ngModel)]="form.tipo_evento" styleClass="w-full" />
    </div>
    <div class="field">
      <label for="fecha-ev">Fecha *</label>
      <input id="fecha-ev" type="date" pInputText [(ngModel)]="form.fecha_evento" class="w-full" />
    </div>
    <div class="field col-span-2">
      <label for="nombre-ev">Nombre del evento *</label>
      <input id="nombre-ev" pInputText [(ngModel)]="form.nombre_evento"
             placeholder="Ej: Día del maestro" class="w-full" />
    </div>
    <div class="field col-span-2">
      <label class="checkbox-label">
        <input type="checkbox" [(ngModel)]="form.aplica_todos_planteles" />
        Aplica a todos los planteles
      </label>
    </div>
    @if (!form.aplica_todos_planteles) {
      <div class="field col-span-2">
        <label for="plantel-ev">Plantel específico</label>
        <p-select id="plantel-ev" [options]="planteles()" optionLabel="nombre_plantel" optionValue="id"
                  [(ngModel)]="form.plantel_id" styleClass="w-full"
                  [filter]="true" filterPlaceholder="Buscar..." />
      </div>
    }
  </div>
  <ng-template pTemplate="footer">
    @if (!esNuevo) {
      <button pButton label="Eliminar" severity="danger" icon="pi pi-trash"
              class="mr-auto" (click)="eliminar()"></button>
    }
    <button pButton label="Cancelar" severity="secondary" (click)="dialogVisible = false"></button>
    <button pButton [label]="esNuevo ? 'Crear' : 'Guardar'" icon="pi pi-save"
            [loading]="guardando()" (click)="guardar()"></button>
  </ng-template>
</p-dialog>
  `,
  styles: [`
    .filter-bar { display:flex; gap:8px; flex-wrap:wrap; margin-bottom:16px; }
    .w-220 { width:220px; } .w-200 { width:200px; } .w-full { width:100%; }
    .resumen-row { display:flex; gap:8px; flex-wrap:wrap; margin-bottom:16px; }
    .resumen-chip { display:flex; align-items:center; gap:6px;
                    background:var(--p-surface-50,#f8f9fa);
                    border:1px solid var(--p-surface-border);
                    border-radius:20px; padding:4px 10px; }
    .resumen-count { font-weight:700; font-size:.9rem; }
    .grid-form { display:grid; grid-template-columns:1fr 1fr; gap:12px; }
    .field { display:flex; flex-direction:column; gap:4px; }
    .field label { font-weight:600; font-size:.85rem; }
    .col-span-2 { grid-column: span 2; }
    .checkbox-label { display:flex; align-items:center; gap:8px; cursor:pointer; font-weight:600; }
    .mr-auto { margin-right:auto; }
  `],
})
export class CalendarioComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private api    = inject(ApiService);
  private ctx    = inject(ContextService);
  private notify = inject(ApexNotificationService);

  ciclos   = signal<CicloOpt[]>([]);
  planteles = signal<PlantelOpt[]>([]);
  eventos  = signal<any[]>([]);

  cargando  = signal(false);
  guardando = signal(false);

  cicloId:     string | null = null;
  tipoFiltro:  string | null = null;
  plantelFiltro: string | null = null;

  dialogVisible = false;
  esNuevo       = true;
  eventoId:     string | null = null;

  form: EventoForm = this._formVacio();

  tipoOpts = [
    { label: 'Día festivo',       value: 'DIA_FESTIVO'    },
    { label: 'Vacaciones',        value: 'VACACIONES'      },
    { label: 'Inicio de clases',  value: 'INICIO_CLASES'  },
    { label: 'Fin de clases',     value: 'FIN_CLASES'     },
    { label: 'Consejo técnico',   value: 'CONSEJO_TECNICO' },
    { label: 'Suspensión',        value: 'SUSPENSION'      },
  ];

  readonly columnas: ColumnConfig[] = [
    { field: 'fecha_evento',   header: 'Fecha',       sortable: true,  filterable: false, width: '110px' },
    { field: 'nombre_evento',  header: 'Evento',      sortable: true,  filterable: true  },
    { field: 'tipo_str',       header: 'Tipo',        sortable: true,  filterable: true,  width: '150px' },
    { field: 'alcance_str',    header: 'Alcance',     sortable: true,  filterable: true,  width: '160px' },
    { field: 'nombre_ciclo',   header: 'Ciclo',       sortable: true,  filterable: true,  width: '120px' },
  ];

  readonly resumen = computed(() => {
    const map: Record<string, number> = {};
    for (const e of this.eventos()) {
      map[e.tipo_evento] = (map[e.tipo_evento] ?? 0) + 1;
    }
    return Object.entries(map).map(([tipo, count]) => ({ tipo, count }))
      .sort((a, b) => b.count - a.count);
  });

  constructor() {
    effect(() => {
      this.plantelFiltro = this.ctx.plantel()?.id ?? null;
      this.cargar();
    });
  }

  ngOnInit() {
    this.cargarCiclos();
    this.cargarPlanteles();
  }

  cargarCiclos() {
    this.api.get('/catalogs/ciclos').pipe(takeUntil(this.destroy$)).subscribe((r: any) => {
      this.ciclos.set(r ?? []);
      const vigente = (r ?? []).find((c: any) => c.es_vigente);
      if (vigente && !this.cicloId) {
        this.cicloId = vigente.id;
        this.cargar();
      }
    });
  }

  cargarPlanteles() {
    this.api.get('/planteles').pipe(takeUntil(this.destroy$)).subscribe((r: any) =>
      this.planteles.set(r.data ?? r ?? []));
  }

  cargar() {
    if (!this.cicloId) return;
    this.cargando.set(true);
    let url = `/calendario?ciclo_escolar_id=${this.cicloId}`;
    if (this.tipoFiltro)    url += `&tipo_evento=${this.tipoFiltro}`;
    if (this.plantelFiltro) url += `&plantel_id=${this.plantelFiltro}`;

    this.api.get(url).pipe(takeUntil(this.destroy$)).subscribe({
      next: (r: any) => {
        const tipoLabels: Record<string, string> = {
          DIA_FESTIVO: 'Día festivo', VACACIONES: 'Vacaciones',
          INICIO_CLASES: 'Inicio de clases', FIN_CLASES: 'Fin de clases',
          CONSEJO_TECNICO: 'Consejo técnico', SUSPENSION: 'Suspensión',
        };
        const flat = (r ?? []).map((ev: any) => ({
          ...ev,
          tipo_str:    tipoLabels[ev.tipo_evento] ?? ev.tipo_evento,
          alcance_str: ev.aplica_todos_planteles
            ? 'Todos los planteles'
            : (ev.nombre_plantel ?? 'Plantel específico'),
        }));
        this.eventos.set(flat);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  abrirNuevo() {
    this.esNuevo   = true;
    this.eventoId  = null;
    this.form      = this._formVacio();
    if (this.cicloId) this.form.ciclo_escolar_id = this.cicloId;
    this.dialogVisible = true;
  }

  abrirEditar(row: any) {
    this.esNuevo  = false;
    this.eventoId = row.id;
    this.form = {
      ciclo_escolar_id:        row.ciclo_escolar_id,
      fecha_evento:            row.fecha_evento,
      nombre_evento:           row.nombre_evento,
      tipo_evento:             row.tipo_evento,
      aplica_todos_planteles:  row.aplica_todos_planteles,
      plantel_id:              row.plantel_id ?? null,
    };
    this.dialogVisible = true;
  }

  guardar() {
    if (!this.form.ciclo_escolar_id || !this.form.fecha_evento || !this.form.nombre_evento || !this.form.tipo_evento) {
      this.notify.warning('Campos requeridos', 'Completa ciclo, fecha, nombre y tipo');
      return;
    }
    this.guardando.set(true);
    const payload = {
      ...this.form,
      plantel_id: this.form.aplica_todos_planteles ? null : this.form.plantel_id,
    };
    const req = this.esNuevo
      ? this.api.post('/calendario', payload)
      : this.api.patch(`/calendario/${this.eventoId}`, payload);

    req.pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.guardando.set(false);
        this.notify.success(
          this.esNuevo ? 'Evento creado' : 'Evento actualizado',
          this.form.nombre_evento
        );
        this.dialogVisible = false;
        this.cargar();
      },
      error: () => this.guardando.set(false),
    });
  }

  eliminar() {
    if (!this.eventoId) return;
    this.api.delete(`/calendario/${this.eventoId}`).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.notify.success('Eliminado', this.form.nombre_evento);
        this.dialogVisible = false;
        this.cargar();
      },
    });
  }

  tipoSeverity(tipo: string): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
    const map: Record<string, any> = {
      DIA_FESTIVO: 'warn', VACACIONES: 'success',
      INICIO_CLASES: 'info', FIN_CLASES: 'info',
      CONSEJO_TECNICO: 'secondary', SUSPENSION: 'danger',
    };
    return map[tipo] ?? 'secondary';
  }

  private _formVacio(): EventoForm {
    return {
      ciclo_escolar_id: '', fecha_evento: '', nombre_evento: '',
      tipo_evento: 'DIA_FESTIVO', aplica_todos_planteles: true, plantel_id: null,
    };
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
