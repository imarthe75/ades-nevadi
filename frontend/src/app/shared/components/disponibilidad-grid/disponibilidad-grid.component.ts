import { Component, Input, OnInit, OnChanges, OnDestroy, SimpleChanges, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ApiService } from '../../../core/services/api.service';
import { ContextService } from '../../../core/services/context.service';
import { ButtonModule } from 'primeng/button';
import { MessageService } from 'primeng/api';

export interface Franja {
  id: string;
  dia_semana: number;
  hora_inicio: string;
  hora_fin: string;
}

export interface Indisponibilidad {
  id?: string;
  // Jackson serializa/deserializa esta entidad (HorarioIndisponibilidad) con
  // spring.jackson.property-naming-strategy: SNAKE_CASE (ver fix HexagonalConfig
  // 2026-07-14) — los nombres de campo deben coincidir con el JSON real (snake_case),
  // tanto al leer la respuesta de GET /horario-indisponibilidad como al construir
  // el payload de POST.
  profesor_id: string;
  ciclo_escolar_id: string;
  franja_id: string;
  tipo: 'DISPONIBLE' | 'CONDICIONAL' | 'NO_DISPONIBLE';
}

@Component({
  selector: 'app-disponibilidad-grid',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonModule],
  template: `
    <div class="disp-container">
      <div class="disp-header">
        <div>
          <h4 style="margin:0 0 0.5rem 0">Matriz de Disponibilidad</h4>
          <p style="margin:0; font-size: 0.85rem; color: var(--text-color-secondary)">
            Haz clic en las celdas para cambiar el estado.
          </p>
        </div>
        <div style="display:flex; gap: 1rem; align-items:center;">
          <div class="leyenda">
            <span class="box disp-verde"></span> Disponible
          </div>
          <div class="leyenda">
            <span class="box disp-amarillo"></span> Condicional
          </div>
          <div class="leyenda">
            <span class="box disp-rojo"></span> No Disponible
          </div>
        </div>
      </div>

      @if (cargando()) {
        <div style="text-align:center; padding: 2rem;">
          <i class="pi pi-spin pi-spinner" style="font-size: 2rem"></i>
        </div>
      } @else if (!ctx.ciclo()?.id) {
        <div class="disp-empty">
          Selecciona un ciclo escolar en la barra superior.
        </div>
      } @else if (franjas().length === 0) {
        <div class="disp-empty">
          No hay franjas horarias configuradas para este nivel/plantel.
        </div>
      } @else {
        <div class="disp-grid-wrapper">
          <table class="disp-table">
            <thead>
              <tr>
                <th style="width: 120px;">Hora</th>
                @for (d of diasSemana; track d.num) {
                  <th>{{ d.label }}</th>
                }
              </tr>
            </thead>
            <tbody>
              @for (h of horasUnicas(); track h) {
                <tr>
                  <td class="hora-col">{{ h }}</td>
                  @for (d of diasSemana; track d.num) {
                    <td class="celda-estado"
                        [ngClass]="getClaseEstado(d.num, h)"
                        (click)="toggleEstado(d.num, h)">
                      {{ getEtiquetaEstado(d.num, h) }}
                    </td>
                  }
                </tr>
              }
            </tbody>
          </table>
        </div>

        <div style="display:flex; justify-content:flex-end; margin-top: 1rem;">
          <p-button label="Guardar Matriz" icon="pi pi-save" [loading]="guardando()" (onClick)="guardar()" />
        </div>
      }
    </div>
  `,
  styles: [`
    .disp-container { display: flex; flex-direction: column; gap: 1rem; }
    .disp-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem; margin-bottom: 0.5rem; }
    .leyenda { display: flex; align-items: center; gap: 0.35rem; font-size: 0.8rem; color: var(--text-color-secondary); }
    .leyenda .box { width: 16px; height: 16px; border-radius: 4px; border: 1px solid rgba(0,0,0,0.1); }
    .disp-empty { padding: 2rem; text-align: center; color: var(--text-color-secondary); background: var(--surface-100); border-radius: 8px; }
    .disp-grid-wrapper { overflow-x: auto; border: 1px solid var(--surface-border); border-radius: 8px; }
    .disp-table { width: 100%; border-collapse: collapse; text-align: center; font-size: 0.85rem; }
    .disp-table th { background: var(--surface-200); padding: 0.75rem; border-bottom: 2px solid var(--surface-border); font-weight: 600; }
    .disp-table td { padding: 0; border: 1px solid var(--surface-border); height: 45px; }
    .hora-col { background: var(--surface-50); font-weight: 600; color: var(--text-color-secondary); }
    .celda-estado { cursor: pointer; transition: all 0.2s; user-select: none; font-size: 0.75rem; font-weight: 600; }
    .celda-estado:hover { filter: brightness(0.95); }
    .disp-verde { background-color: #dcfce7; color: #166534; }
    .disp-amarillo { background-color: #fef9c3; color: #854d0e; }
    .disp-rojo { background-color: #fee2e2; color: #991b1b; }
    .disp-gris { background-color: var(--surface-100); color: var(--text-color-secondary); cursor: not-allowed; }
  `]
})
export class DisponibilidadGridComponent implements OnInit, OnChanges, OnDestroy {
  private readonly api = inject(ApiService);
  readonly ctx = inject(ContextService);
  private readonly msg = inject(MessageService);
  private readonly destroy$ = new Subject<void>();

  @Input() profesorId!: string;

  diasSemana = [
    { num: 1, label: 'Lunes' },
    { num: 2, label: 'Martes' },
    { num: 3, label: 'Miércoles' },
    { num: 4, label: 'Jueves' },
    { num: 5, label: 'Viernes' }
  ];

  franjas = signal<Franja[]>([]);
  horasUnicas = computed(() => {
    const horas = new Set(this.franjas().map(f => f.hora_inicio.substring(0, 5)));
    return Array.from(horas).sort();
  });

  // Mapa: franjaId -> tipo
  estadoMap = signal<Map<string, 'DISPONIBLE' | 'CONDICIONAL' | 'NO_DISPONIBLE'>>(new Map());
  cargando = signal(false);
  guardando = signal(false);

  ngOnInit() {
    this.cargarDatos();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['profesorId'] && !changes['profesorId'].isFirstChange()) {
      this.cargarDatos();
    }
  }

  cargarDatos() {
    const cicloId = this.ctx.ciclo()?.id;
    if (!cicloId || !this.profesorId) return;

    this.cargando.set(true);

    // 1. Cargar Franjas del ciclo y plantel/nivel activo
    const params: any = { cicloId };
    if (this.ctx.plantel()?.id) params.plantelId = this.ctx.plantel()?.id;
    if (this.ctx.nivel()?.id) params.nivelEducativoId = this.ctx.nivel()?.id;

    this.api.get<Franja[]>('/horario-franjas', params)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (fList) => {
          this.franjas.set(fList);

          // 2. Cargar Matriz actual del profesor
          this.api.get<Indisponibilidad[]>('/horario-indisponibilidad', { profesorId: this.profesorId, cicloEscolarId: cicloId })
            .pipe(takeUntil(this.destroy$))
            .subscribe({
              next: (iList) => {
                const map = new Map<string, 'DISPONIBLE' | 'CONDICIONAL' | 'NO_DISPONIBLE'>();
                // Por defecto, si hay franja, está DISPONIBLE
                fList.forEach(f => map.set(f.id, 'DISPONIBLE'));
                // Sobrescribir con lo guardado
                iList.forEach(ind => map.set(ind.franja_id, ind.tipo));
                this.estadoMap.set(map);
                this.cargando.set(false);
              },
              error: () => this.cargando.set(false)
            });
        },
        error: () => this.cargando.set(false)
      });
  }

  getFranja(dia: number, hora: string): Franja | undefined {
    return this.franjas().find(f => f.dia_semana === dia && f.hora_inicio.startsWith(hora));
  }

  getClaseEstado(dia: number, hora: string): string {
    const f = this.getFranja(dia, hora);
    if (!f) return 'disp-gris';
    const st = this.estadoMap().get(f.id);
    if (st === 'DISPONIBLE') return 'disp-verde';
    if (st === 'CONDICIONAL') return 'disp-amarillo';
    if (st === 'NO_DISPONIBLE') return 'disp-rojo';
    return 'disp-verde';
  }

  getEtiquetaEstado(dia: number, hora: string): string {
    const f = this.getFranja(dia, hora);
    if (!f) return '-';
    const st = this.estadoMap().get(f.id);
    if (st === 'DISPONIBLE') return 'DISP';
    if (st === 'CONDICIONAL') return 'COND';
    if (st === 'NO_DISPONIBLE') return 'NO';
    return 'DISP';
  }

  toggleEstado(dia: number, hora: string) {
    const f = this.getFranja(dia, hora);
    if (!f) return;
    
    const current = this.estadoMap().get(f.id) || 'DISPONIBLE';
    let next: 'DISPONIBLE' | 'CONDICIONAL' | 'NO_DISPONIBLE' = 'DISPONIBLE';
    
    if (current === 'DISPONIBLE') next = 'CONDICIONAL';
    else if (current === 'CONDICIONAL') next = 'NO_DISPONIBLE';
    else if (current === 'NO_DISPONIBLE') next = 'DISPONIBLE';

    const newMap = new Map(this.estadoMap());
    newMap.set(f.id, next);
    this.estadoMap.set(newMap);
  }

  guardar() {
    const cicloId = this.ctx.ciclo()?.id;
    if (!cicloId || !this.profesorId) return;

    this.guardando.set(true);

    const payload: Indisponibilidad[] = [];
    this.estadoMap().forEach((tipo, franjaId) => {
      // Solo enviamos los que no son DISPONIBLE para ahorrar espacio, 
      // o todos si el backend hace replace. El backend hace replace completo.
      // Enviaremos todos los modificados. A nivel base de datos es mejor enviar todo
      // para que el backend borre y reinserte.
      payload.push({
        profesor_id: this.profesorId,
        ciclo_escolar_id: cicloId,
        franja_id: franjaId,
        tipo: tipo
      });
    });

    this.api.post(`/horario-indisponibilidad?profesorId=${this.profesorId}&cicloEscolarId=${cicloId}`, payload)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.msg.add({ severity: 'success', summary: 'Éxito', detail: 'Matriz guardada correctamente' });
          this.guardando.set(false);
        },
        error: () => {
          this.msg.add({ severity: 'error', summary: 'Error', detail: 'No se pudo guardar la matriz' });
          this.guardando.set(false);
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
