import { Component, Input, Output, EventEmitter, OnDestroy, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DragDropModule, CdkDragDrop } from '@angular/cdk/drag-drop';
import { TooltipModule } from 'primeng/tooltip';

export interface HorarioGridEntry {
  id: string;
  dia_semana: number;
  hora_inicio: string;
  hora_fin: string;
  fijado?: boolean;
  nombre_materia: string | null;
  materia_id: string | null;
  nombre_grupo: string | null;
  grupo_id: string | null;
  nombre_profesor: string | null;
  profesor_id: string | null;
  nombre_aula: string | null;
  aula_id: string | null;
}

@Component({
  selector: 'app-horario-grid',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule, DragDropModule, TooltipModule],
  template: `
    @if (franjas.length === 0) {
      <div class="empty-state">
        <i class="pi pi-calendar-plus" style="font-size: 3rem"></i>
        <p>No hay franjas horarias configuradas para este contexto.</p>
      </div>
    } @else {
      <div class="horario-grid" cdkDropListGroup>
        <!-- Cabecera días -->
        <div class="grid-header">
          <div class="hora-header">Hora</div>
          @for (dia of dias; track dia.num) {
            <div class="dia-header">{{ dia.label }}</div>
          }
        </div>

        <!-- Franjas horarias -->
        @for (franja of franjas; track franja) {
          <div class="grid-row">
            <div class="hora-col">{{ franja }}</div>
            @for (dia of dias; track dia.num) {
              <div class="dia-cell" cdkDropList [cdkDropListData]="{dia: dia.num, franja: franja}" (cdkDropListDropped)="onDrop($event)"
                   [class.cell-nodisp]="estadoDisp(dia.num, franja) === 'NO_DISPONIBLE'"
                   [class.cell-cond]="estadoDisp(dia.num, franja) === 'CONDICIONAL'"
                   [pTooltip]="estadoDisp(dia.num, franja) === 'NO_DISPONIBLE' ? 'Docente no disponible' : (estadoDisp(dia.num, franja) === 'CONDICIONAL' ? 'Docente disponible con condiciones' : '')"
                   [attr.aria-label]="dia.label + ' ' + franja + (estadoDisp(dia.num, franja) === 'NO_DISPONIBLE' ? ', docente no disponible' : (estadoDisp(dia.num, franja) === 'CONDICIONAL' ? ', docente condicional' : ''))">
                @for (e of entradasPor(dia.num, franja); track e.id) {
                  <div class="clase-chip" cdkDrag [cdkDragData]="e" [style.background]="colorMateria(e.nombre_materia)"
                       (click)="claseClick.emit(e)" pTooltip="Click para editar" style="cursor:pointer"
                       role="button" tabindex="0"
                       [attr.aria-label]="'Editar clase ' + e.nombre_materia + (modo === 'grupo' ? ', profesor ' + e.nombre_profesor : ', grupo ' + e.nombre_grupo) + ', ' + (e.hora_inicio | slice:0:5) + ' a ' + (e.hora_fin | slice:0:5)"
                       (keydown.enter)="claseClick.emit(e)" (keydown.space)="$event.preventDefault(); claseClick.emit(e)">
                    <strong>{{ e.nombre_materia }}</strong>
                    @if (modo === 'grupo') {
                      <span>{{ e.nombre_profesor }}</span>
                    } @else {
                      <span>{{ e.nombre_grupo }}</span>
                    }
                    @if (e.nombre_aula) { <span class="aula-tag">{{ e.nombre_aula }}</span> }
                    <span class="hora-tag">{{ e.hora_inicio | slice:0:5 }}–{{ e.hora_fin | slice:0:5 }}</span>
                    @if (e.fijado) { <i class="pi pi-lock" style="font-size: 0.7rem; color: #b91c1c" pTooltip="Fijada"></i> }
                  </div>
                }
              </div>
            }
          </div>
        }
      </div>
    }
  `,
  styles: [`
    .empty-state { display: flex; flex-direction: column; align-items: center; gap: 0.75rem; padding: 4rem; color: var(--text-muted); }
    
    .horario-grid { overflow-x: auto; }
    .grid-header, .grid-row {
      display: grid;
      grid-template-columns: 70px repeat(5, 1fr);
      gap: 2px;
    }
    .grid-header { margin-bottom: 2px; }
    .dia-header {
      background: var(--nevadi-red);
      color: #fff;
      text-align: center;
      padding: 0.4rem;
      font-family: var(--font-display);
      font-weight: 700;
      font-size: 0.82rem;
      border-radius: 4px;
    }
    .hora-header, .hora-col {
      background: var(--surface-200);
      text-align: center;
      padding: 0.4rem;
      font-size: 0.8rem;
      font-weight: 600;
      border-radius: 4px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .dia-cell {
      background: var(--surface-100);
      border-radius: 4px;
      min-height: 70px;
      padding: 2px;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    /* Overlay de disponibilidad docente: rojo rayado = no disponible, amarillo = condicional */
    .dia-cell.cell-nodisp {
      background: repeating-linear-gradient(45deg, #fee2e2, #fee2e2 6px, #fecaca 6px, #fecaca 12px);
    }
    .dia-cell.cell-cond { background: #fef9c3; }
    .clase-chip {
      border-radius: 4px;
      padding: 0.3rem 0.5rem;
      font-size: 0.78rem;
      display: flex; flex-direction: column;
      border-left: 3px solid var(--nevadi-red);
    }
    .clase-chip:hover { filter: brightness(.95); }
    .clase-chip strong { font-family: var(--font-display); font-size: 0.8rem; }
    .clase-chip span { color: var(--text-secondary); font-size: 0.72rem; }
    .aula-tag { font-weight: 600; color: var(--nevadi-red-dark) !important; }
    .hora-tag { font-size: 0.68rem !important; color: var(--text-muted) !important; }
    .grid-row { margin-bottom: 2px; }
    
    .cdk-drag-preview {
      box-sizing: border-box;
      border-radius: 4px;
      box-shadow: 0 5px 5px -3px rgba(0, 0, 0, 0.2),
                  0 8px 10px 1px rgba(0, 0, 0, 0.14),
                  0 3px 14px 2px rgba(0, 0, 0, 0.12);
    }
    .cdk-drag-placeholder {
      opacity: 0.3;
    }
    .cdk-drag-animating {
      transition: transform 250ms cubic-bezier(0, 0, 0.2, 1);
    }
  `]
})
export class HorarioGridComponent implements OnDestroy {
  @Input() dias: { num: number, label: string }[] = [];
  @Input() franjas: string[] = [];
  @Input() entradas: HorarioGridEntry[] = [];
  @Input() modo: 'grupo' | 'profesor' = 'grupo';
  /**
   * Overlay de disponibilidad docente. Clave `${dia}-${franja}` (franja = "HH:mm")
   * → 'NO_DISPONIBLE' | 'CONDICIONAL'. Cuando se muestra el horario de un docente,
   * sombrea las celdas donde el docente marcó indisponibilidad, para verlo de un
   * vistazo sin salir a otra pantalla.
   */
  @Input() disponibilidad: Record<string, string> = {};

  @Output() claseDrop = new EventEmitter<CdkDragDrop<any>>();
  @Output() claseClick = new EventEmitter<HorarioGridEntry>();

  entradasPor(dia: number, franja: string): HorarioGridEntry[] {
    return this.entradas.filter(e => e.dia_semana === dia && e.hora_inicio.startsWith(franja));
  }

  /** Estado de disponibilidad del docente en una celda (día × franja). */
  estadoDisp(dia: number, franja: string): string {
    return this.disponibilidad[`${dia}-${franja}`] ?? '';
  }

  colorMateria(nombre: string | null): string {
    if (!nombre) return 'var(--surface-hover)';
    const colorHash = nombre.split('').reduce((acc, char) => char.charCodeAt(0) + ((acc << 5) - acc), 0);
    const h = Math.abs(colorHash) % 360;
    return `hsl(${h}, 60%, 90%)`;
  }

  onDrop(event: CdkDragDrop<any>) {
    this.claseDrop.emit(event);
  }

  ngOnDestroy(): void {}
}
