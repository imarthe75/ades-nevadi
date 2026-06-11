/**
 * HorariosComponent — Vista semanal de horarios escolares.
 *
 * Vista tipo grid 5×N (Lun-Vie × franjas horarias).
 * Permite ver el horario de un grupo O de un docente.
 * Exporta el XML para aSc TimeTables.
 */
import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { ToolbarModule } from 'primeng/toolbar';
import { SelectButtonModule } from 'primeng/selectbutton';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import type { Grupo, Profesor } from '../../core/models';
import { ApexNotificationService } from 'apex-component-library';

interface HorarioEntry {
  id: string;
  dia_semana: number;
  hora_inicio: string;
  hora_fin: string;
  nombre_materia: string | null;
  nombre_grupo: string | null;
  nombre_profesor: string | null;
  nombre_aula: string | null;
}

const DIAS = ['', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes'];
const COLORS: Record<string, string> = {
  'Matemáticas': '#FEE2E2', 'Español': '#E0F2FE', 'Ciencias': '#D1FAE5',
  'Historia': '#FEF3C7', 'Inglés': '#EDE9FE', 'Física': '#FCE7F3',
  'Química': '#ECFDF5', 'default': '#F1F5F9',
};

@Component({
  selector: 'app-horarios',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TableModule, ButtonModule, SelectModule, TagModule, ToolbarModule, SelectButtonModule,
  ],
  template: `

    <div class="page-header">
      <div>
        <h2>Horarios</h2>
        <p class="subtitle">Vista semanal — {{ modoLabel() }}</p>
      </div>
    </div>

    <!-- ── Controles ── -->
    <div class="filter-bar">
      <p-selectButton
        [options]="modoOpts"
        [(ngModel)]="modo"
        optionLabel="label"
        optionValue="value"
        (onChange)="onModoChange()"
      />

      @if (modo === 'grupo') {
        <p-select
          [options]="grupos()"
          [(ngModel)]="selectedGrupo"
          optionLabel="nombre_grupo"
          placeholder="Seleccionar grupo..."
          (onChange)="cargar()"
          [showClear]="true"
        

        [filter]="true" filterPlaceholder="Buscar..."/>
      } @else {
        <p-select
          [options]="profesores()"
          [(ngModel)]="selectedProfesor"
          optionLabel="numero_empleado"
          placeholder="Seleccionar docente..."
          (onChange)="cargar()"
          [showClear]="true"
        

        [filter]="true" filterPlaceholder="Buscar..."/>
      }

      <p-button
        label="Exportar XML aSc"
        icon="pi pi-download"
        severity="secondary"
        [text]="true"
        (onClick)="exportarASC()"
        [disabled]="!ctx.ciclo()"
      />
    </div>

    <!-- ── Grid semanal ── -->
    @if (entradas().length > 0) {
      <div class="horario-grid">
        <!-- Cabecera días -->
        <div class="grid-header">
          <div class="hora-col"></div>
          @for (dia of dias; track dia.num) {
            <div class="dia-header">{{ dia.label }}</div>
          }
        </div>

        <!-- Franjas horarias -->
        @for (franja of franjas(); track franja) {
          <div class="grid-row">
            <div class="hora-col">{{ franja }}</div>
            @for (dia of dias; track dia.num) {
              <div class="dia-cell">
                @for (e of entradasPor(dia.num, franja); track e.id) {
                  <div class="clase-chip" [style.background]="colorMateria(e.nombre_materia)">
                    <strong>{{ e.nombre_materia }}</strong>
                    @if (modo === 'grupo') {
                      <span>{{ e.nombre_profesor }}</span>
                    } @else {
                      <span>{{ e.nombre_grupo }}</span>
                    }
                    @if (e.nombre_aula) { <span class="aula-tag">{{ e.nombre_aula }}</span> }
                    <span class="hora-tag">{{ e.hora_inicio | slice:0:5 }}–{{ e.hora_fin | slice:0:5 }}</span>
                  </div>
                }
              </div>
            }
          </div>
        }
      </div>
    } @else {
      <div class="empty-state">
        <i class="pi pi-calendar" style="font-size:2.5rem; color:#CBD5E1"></i>
        <p>Selecciona un {{ modo === 'grupo' ? 'grupo' : 'docente' }} para ver el horario.</p>
      </div>
    }
  `,
  styles: [`
    .filter-bar { display: flex; gap: 0.75rem; align-items: center; margin-bottom: 1.25rem; flex-wrap: wrap; }
    .empty-state { display: flex; flex-direction: column; align-items: center; gap: 0.75rem; padding: 4rem; color: #94A3B8; }

    /* Grid */
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
    .hora-col {
      font-size: 0.75rem;
      color: var(--text-secondary);
      padding: 0.3rem 0.4rem;
      display: flex; align-items: flex-start;
      font-family: var(--font-body);
    }
    .dia-cell {
      min-height: 52px;
      background: var(--surface-hover);
      border-radius: 4px;
      padding: 2px;
      display: flex; flex-direction: column; gap: 2px;
    }
    .clase-chip {
      border-radius: 4px;
      padding: 0.3rem 0.5rem;
      font-size: 0.78rem;
      display: flex; flex-direction: column;
      border-left: 3px solid var(--nevadi-red);
    }
    .clase-chip strong { font-family: var(--font-display); font-size: 0.8rem; }
    .clase-chip span { color: var(--text-secondary); font-size: 0.72rem; }
    .aula-tag { font-weight: 600; color: var(--nevadi-red-dark) !important; }
    .hora-tag { font-size: 0.68rem !important; color: var(--text-muted) !important; }
    .grid-row { margin-bottom: 2px; }
  `],
})
export class HorariosComponent implements OnInit {
  private readonly api = inject(ApiService);
  readonly ctx = inject(ContextService);
  private readonly notify = inject(ApexNotificationService);

  readonly dias = DIAS.slice(1).map((label, i) => ({ num: i + 1, label }));
  readonly modoOpts = [
    { label: 'Por grupo', value: 'grupo' },
    { label: 'Por docente', value: 'profesor' },
  ];

  modo: 'grupo' | 'profesor' = 'grupo';
  selectedGrupo: Grupo | null = null;
  selectedProfesor: Profesor | null = null;

  grupos    = signal<Grupo[]>([]);
  profesores = signal<Profesor[]>([]);
  entradas  = signal<HorarioEntry[]>([]);

  modoLabel = computed(() => this.modo === 'grupo' ? 'Grupo' : 'Docente');

  franjas = computed(() => {
    const horas = [...new Set(this.entradas().map(e => e.hora_inicio))];
    return horas.sort();
  });

  ngOnInit(): void {
    const plantelId = this.ctx.plantel()?.id;
    if (plantelId) {
      this.api.get<Grupo[]>('/grupos', { plantel_id: plantelId, solo_activos: true, ciclo_vigente: true })
        .subscribe(g => this.grupos.set(g));
      this.api.get<Profesor[]>('/profesores', { plantel_id: plantelId })
        .subscribe(p => this.profesores.set(p));
    }
  }

  onModoChange(): void {
    this.selectedGrupo = null;
    this.selectedProfesor = null;
    this.entradas.set([]);
  }

  cargar(): void {
    if (this.modo === 'grupo' && this.selectedGrupo) {
      this.api.get<any>(`/horarios/grupo/${this.selectedGrupo.id}`)
        .subscribe(r => this.entradas.set(r.entradas || []));
    } else if (this.modo === 'profesor' && this.selectedProfesor) {
      this.api.get<any>(`/horarios/profesor/${this.selectedProfesor.id}`)
        .subscribe(r => this.entradas.set(r.entradas || []));
    }
  }

  entradasPor(dia: number, hora: string): HorarioEntry[] {
    return this.entradas().filter(e => e.dia_semana === dia && e.hora_inicio === hora);
  }

  colorMateria(nombre: string | null): string {
    if (!nombre) return COLORS['default'];
    const key = Object.keys(COLORS).find(k => nombre.includes(k));
    return key ? COLORS[key] : COLORS['default'];
  }

  exportarASC(): void {
    const cicloId = this.ctx.ciclo()?.id;
    const plantelId = this.ctx.plantel()?.id;
    if (!cicloId) return;
    const params = plantelId ? `?plantel_id=${plantelId}` : '';
    window.open(`/api/v1/horarios/exportar-asc/${cicloId}${params}`, '_blank');
  }
}
