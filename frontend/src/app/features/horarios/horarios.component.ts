/**
 * HorariosComponent — Vista semanal + CRUD de horarios escolares.
 *
 * Vista tipo grid 5×N (Lun-Vie × franjas horarias).
 * Permite ver el horario de un grupo O de un docente, y crear/editar/eliminar entradas.
 * Exporta el XML para aSc TimeTables.
 */
import { Component, OnInit, OnDestroy, inject, signal, computed, effect, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TooltipModule } from 'primeng/tooltip';
import { TextareaModule } from 'primeng/textarea';
import { DragDropModule, CdkDragDrop } from '@angular/cdk/drag-drop';
import { Subject, takeUntil } from 'rxjs';
import { HorarioGridComponent, HorarioGridEntry } from '../../shared/components/horario-grid/horario-grid.component';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import type { Grupo, Profesor } from '../../core/models';
import { grupoLabel } from '../../core/models';
import { ApexNotificationService } from 'apex-component-library';

type GrupoConLabel = Grupo & { _label: string };

interface HorarioEntry {
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

interface MateriaOpt { id: string; nombre_materia: string; }
interface AulaOpt { id: string; nombre: string; codigo: string | null; }
interface HorarioReporteMateria {
  materia: string;
  requerido: number | null;
  obtenido: number;
  cumple: boolean;
}
interface HorarioReporteGrupo {
  grupo: string;
  grado: number | null;
  totalHoras: number;
  materias: HorarioReporteMateria[];
}
interface HorarioReporteCheck {
  criterio: string;
  ok: boolean;
  detalle: string;
}
interface HorarioReportePrimaria {
  estado_solver: string;
  score_text: string;
  grupos: HorarioReporteGrupo[];
  conflictos_docentes: { docente: string; conflictos: string[] }[];
  checks: HorarioReporteCheck[];
  resumen: string;
}
interface SolverCorrida {
  id: string;
  plantel_id: string | null;
  ciclo_escolar_id: string | null;
  estado: string | null;
  score_text: string | null;
  score_analysis_json: string | null;
  tiempo_solving_ms: number | null;
  version: string | null;
  generado_por: string | null;
  resultado_excel_url: string | null;
  fecha_creacion: string | null;
  fecha_modificacion: string | null;
  horarios_generados: number | null;
}
interface SolverDetail extends SolverCorrida {
  horarios?: HorarioEntry[];
}

const DIAS = ['', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes'];
const HORAS_INICIO = [
  '07:00', '07:30', '08:00', '08:30', '09:00', '09:30', '10:00', '10:30',
  '11:00', '11:30', '12:00', '12:30', '13:00', '13:30', '14:00', '14:30',
  '15:00', '15:30', '16:00', '16:30', '17:00', '17:30', '18:00', '18:30',
];
const DURACIONES = [
  { label: '30 min', value: 30 }, { label: '45 min', value: 45 },
  { label: '50 min', value: 50 }, { label: '1 hora', value: 60 },
  { label: '1.5 horas', value: 90 }, { label: '2 horas', value: 120 },
];
const COLORS: Record<string, string> = {
  'Matemáticas': '#FEE2E2', 'Español': '#E0F2FE', 'Ciencias': '#D1FAE5',
  'Historia': '#FEF3C7', 'Inglés': '#EDE9FE', 'Física': '#FCE7F3',
  'Química': '#ECFDF5', 'default': '#F1F5F9',
};

const MATERIAS_ESPECIALISTAS = [
  'inglés', 'socioemocional', 'educación física', 'desarrollo comunitario', 'computación',
];
const MATERIAS_TITULAR = [
  'lecto', 'español', 'matemáticas', 'conocimiento', 'artes', 'fábrica de lectura', 'ortografía',
  'historia', 'formación', 'entidad', 'geografía', 'proyectos',
];
const MATERIAS_REPORTE = [
  'lecto', 'español', 'matemáticas', 'inglés', 'socioemocional', 'desarrollo comunitario',
  'educación física', 'computación', 'artes', 'formación', 'conocimiento', 'proyectos',
  'historia', 'geografía', 'fábrica de lectura', 'ortografía', 'entidad',
];

const HORARIO_GOLDEN_REQUERIDO: Record<string, Record<string, number>> = {
  '1': { 'Lecto': 7, 'Español': 1, 'Matemáticas': 7, 'Inglés': 4, 'Socioemocional': 1, 'Desarrollo Comunitario': 1, 'Educación Física': 2, 'Computación': 1, 'Artes': 1, 'Formación': 1, 'Conocimiento': 1, 'Proyectos': 2, 'Fábrica de lectura': 1, 'Ortografía': 1 },
  '2': { 'Lecto': 5, 'Español': 3, 'Matemáticas': 7, 'Inglés': 4, 'Socioemocional': 1, 'Desarrollo Comunitario': 1, 'Educación Física': 2, 'Computación': 1, 'Artes': 1, 'Formación': 1, 'Conocimiento': 1, 'Proyectos': 2, 'Fábrica de lectura': 1, 'Ortografía': 1 },
  '3': { 'Español': 6, 'Matemáticas': 7, 'Inglés': 4, 'Socioemocional': 1, 'Desarrollo Comunitario': 1, 'Educación Física': 2, 'Computación': 1, 'Artes': 1, 'Formación': 1, 'Entidad': 1, 'Conocimiento': 2, 'Proyectos': 2, 'Fábrica de lectura': 1, 'Ortografía': 1 },
  '4': { 'Español': 5, 'Matemáticas': 6, 'Inglés': 4, 'Socioemocional': 1, 'Desarrollo Comunitario': 1, 'Educación Física': 2, 'Computación': 1, 'Artes': 1, 'Formación': 1, 'Conocimiento': 2, 'Proyectos': 3, 'Historia': 1, 'Geografía': 1, 'Fábrica de lectura': 1, 'Ortografía': 1 },
  '5': { 'Español': 5, 'Matemáticas': 6, 'Inglés': 4, 'Socioemocional': 1, 'Desarrollo Comunitario': 1, 'Educación Física': 2, 'Computación': 1, 'Artes': 1, 'Formación': 1, 'Conocimiento': 2, 'Proyectos': 3, 'Historia': 1, 'Geografía': 1, 'Fábrica de lectura': 1, 'Ortografía': 1 },
  '6': { 'Español': 5, 'Matemáticas': 6, 'Inglés': 4, 'Socioemocional': 1, 'Desarrollo Comunitario': 1, 'Educación Física': 2, 'Computación': 1, 'Artes': 1, 'Formación': 1, 'Conocimiento': 2, 'Proyectos': 3, 'Historia': 1, 'Geografía': 1, 'Fábrica de lectura': 1, 'Ortografía': 1 },
};

@Component({
  selector: 'app-horarios',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    ButtonModule, SelectModule, TagModule, DialogModule,
    InputTextModule, SelectButtonModule, TooltipModule,
    DragDropModule, HorarioGridComponent
  ],
  template: `
    <!-- ── Diálogo crear/editar entrada ── -->
    <p-dialog
      [visible]="showDialog()"
      (visibleChange)="showDialog.set($event)"
      [header]="editEntry ? 'Editar entrada de horario' : 'Nueva entrada de horario'"
      [modal]="true" [style]="{width:'480px'}" [draggable]="false">

      <div class="dlg-grid">
        <div class="dlg-row">
          <div>
            <label class="dlg-lbl">Día *</label>
            <p-select [options]="diasOpts" [(ngModel)]="form.dia_semana"
              optionLabel="label" optionValue="value" style="width:100%" placeholder="Día…" />
          </div>
          <div>
            <label class="dlg-lbl">Hora inicio *</label>
            <p-select [options]="horasOpts" [(ngModel)]="form.hora_inicio"
              style="width:100%" placeholder="HH:MM" />
          </div>
        </div>
        <div class="dlg-row">
          <div>
            <label class="dlg-lbl">Duración *</label>
            <p-select [options]="duracionOpts" [(ngModel)]="form.duracion"
              optionLabel="label" optionValue="value" style="width:100%" placeholder="Duración…" />
          </div>
          <div>
            <label class="dlg-lbl">Materia *</label>
            <p-select [options]="materias()" [(ngModel)]="form.materia_id"
              optionLabel="nombre_materia" optionValue="id"
              style="width:100%" [filter]="true" filterPlaceholder="Buscar…" placeholder="Materia…" />
          </div>
        </div>
        <div class="dlg-row">
          @if (modo === 'grupo') {
            <div>
              <label class="dlg-lbl">Docente</label>
              <p-select [options]="profesores()" [(ngModel)]="form.profesor_id"
                optionLabel="_label" optionValue="id"
                style="width:100%" [filter]="true" filterPlaceholder="Buscar…" placeholder="Docente…" [showClear]="true" />
            </div>
          } @else {
            <div>
              <label class="dlg-lbl">Grupo</label>
              <p-select [options]="gruposOpts()" [(ngModel)]="form.grupo_id"
                optionLabel="_label" optionValue="id"
                style="width:100%" [filter]="true" filterPlaceholder="Buscar…" placeholder="Grupo…" [showClear]="true" />
            </div>
          }
          <div>
            <label class="dlg-lbl">Aula</label>
            <p-select [options]="aulas()" [(ngModel)]="form.aula_id"
              optionLabel="_label" optionValue="id"
              style="width:100%" [filter]="true" filterPlaceholder="Buscar…" placeholder="Aula…" [showClear]="true" />
          </div>
        </div>
        <div class="dlg-row">
          <div style="grid-column: 1 / -1; display:flex; align-items:center; gap:.5rem; margin-top:.5rem">
            <input type="checkbox" id="fijadoCheck" [(ngModel)]="form.fijado" />
            <label for="fijadoCheck" style="font-size:0.8rem; cursor:pointer">
              Fijar este bloque (El motor automático no lo moverá)
            </label>
          </div>
        </div>
      </div>

      <ng-template pTemplate="footer">
        @if (editEntry) {
          <p-button label="Eliminar" icon="pi pi-trash" severity="danger" [text]="true"
            (onClick)="eliminar()" [loading]="guardando()" />
        }
        <p-button label="Cancelar" severity="secondary" [text]="true"
          (onClick)="showDialog.set(false)" />
        <p-button [label]="editEntry ? 'Guardar cambios' : 'Agregar al horario'"
          icon="pi pi-check" (onClick)="guardar()" [loading]="guardando()" />
      </ng-template>
    </p-dialog>


    <!-- ── Diálogo Asistente IA de Reglas ── -->
    <p-dialog
      [visible]="mostrarReglasIA()"
      (visibleChange)="mostrarReglasIA.set($event)"
      header="Asistente IA para Reglas de Horario"
      [modal]="true" [style]="{width:'600px'}" [draggable]="false">
      <div style="display:flex; flex-direction:column; gap: 1rem; margin-top: 1rem;">
        <p>Escribe la regla en lenguaje natural y la IA se encargará de traducirla.</p>
        <textarea pTextarea [(ngModel)]="reglaFraseIA" rows="3" placeholder="Ej: Educación Física no se imparte los viernes para el grupo 2B" style="width:100%"></textarea>
        <div style="display:flex; justify-content:flex-end;">
          <p-button label="Interpretar regla" icon="pi pi-sparkles" (onClick)="parsearReglaIA()" [loading]="parseandoRegla()" [disabled]="!reglaFraseIA()"></p-button>
        </div>
        
        @if (reglaInterpretada()) {
          <div style="background: var(--surface-ground); padding: 1rem; border-radius: 6px; border: 1px solid var(--surface-border);">
            <strong style="display:block; margin-bottom: 0.5rem; color: var(--primary-color);">Previsualización de regla:</strong>
            <pre style="margin:0; white-space: pre-wrap; font-size: 0.9rem;">{{ reglaInterpretada() | json }}</pre>
          </div>
        }
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="mostrarReglasIA.set(false)" />
        <p-button label="Guardar Regla" icon="pi pi-check" (onClick)="guardarReglaIA()" [loading]="guardandoRegla()" [disabled]="!reglaInterpretada()" />
      </ng-template>
    </p-dialog>

    <div class="page-header">
      <div>
        <h2>Horarios</h2>
        <p class="subtitle">Vista semanal — {{ modoLabel() }}</p>
      </div>
      <div style="display:flex;gap:.5rem;align-items:center">
        <p-button
          label="Exportar XML aSc"
          icon="pi pi-download"
          severity="secondary"
          [text]="true"
          (onClick)="exportarASC()"
          [loading]="exportandoAsc()"
          [disabled]="!ctx.ciclo()"
          pTooltip="Exportar horarios al formato XML de aSc TimeTables"
        />
        @if (ctx.nivelAcceso() <= 3) {
          <label class="asc-reemplazar" pTooltip="Si está activo, reemplaza los horarios vigentes del ciclo antes de importar">
            <input type="checkbox" [(ngModel)]="reemplazarAsc" />
            <span>Reemplazar al importar</span>
          </label>
          <p-button
            label="Importar XML aSc"
            icon="pi pi-upload"
            severity="secondary"
            [text]="true"
            (onClick)="ascInput.click()"
            [loading]="importandoAsc()"
            [disabled]="!ctx.ciclo()"
            pTooltip="Importar la solución resuelta desde aSc TimeTables"
          />
          <input #ascInput type="file" accept=".xml" style="display:none"
                 (change)="onArchivoAscSeleccionado($event)" />
        }
        @if (selectedGrupo || selectedProfesor) {
          <p-button
            label="Nueva entrada"
            icon="pi pi-plus"
            (onClick)="abrirNuevo()"
          />
        }
      </div>
    </div>

    <div class="solver-panel">
      <div class="solver-head">
        <div>
          <h3>Timefold Solver</h3>
          <p>
            Corridas del optimizador para el plantel y ciclo actual. Desde aquí puedes lanzar una corrida,
            ver corridas recientes y revisar score, análisis y horarios generados.
          </p>
        </div>
        <div class="solver-actions">
          <p-button
            label="Refrescar corridas"
            icon="pi pi-refresh"
            severity="secondary"
            [text]="true"
            (onClick)="cargarCorridas()"
            [loading]="cargandoCorridas()"
            [disabled]="!puedeUsarSolver()"
          />
          <p-button
            label="Reglas IA"
            icon="pi pi-sparkles"
            severity="info"
            (onClick)="mostrarReglasIA.set(true)"
          />
          <p-button
            label="Ejecutar solver"
            icon="pi pi-play"
            (onClick)="iniciarSolver()"
            [loading]="ejecutandoSolver()"
            [disabled]="!puedeUsarSolver() || !ctx.plantel() || !ctx.ciclo()"
          />
        </div>
      </div>

      @if (!puedeUsarSolver()) {
        <div class="solver-empty">Selecciona un contexto con permisos de coordinación para ejecutar el solver.</div>
      } @else {
        <div class="solver-grid">
          <section class="solver-card">
            <div class="solver-card-head">
              <strong>Corridas recientes</strong>
              <span>{{ corridasSolver().length }}</span>
            </div>
            @if (corridasSolver().length === 0) {
              <div class="solver-empty small">No hay corridas registradas para este plantel.</div>
            } @else {
              <div class="solver-list">
                @for (corrida of corridasSolver(); track corrida.id) {
                  <button class="solver-list-item" type="button" (click)="seleccionarCorrida(corrida.id)">
                    <div>
                      <strong>{{ corrida.estado || 'SIN ESTADO' }}</strong>
                      <span>{{ corrida.score_text || 'sin score' }}</span>
                    </div>
                    <small>{{ corrida.fecha_creacion | slice:0:19 }}</small>
                  </button>
                }
              </div>
            }
          </section>

          <section class="solver-card solver-detail">
            <div class="solver-card-head">
              <strong>Detalle de corrida</strong>
              <div style="display:flex;gap:.5rem;align-items:center;flex-wrap:wrap;justify-content:flex-end">
                @if (esCorridaActiva(corridaDetalle())) {
                  <p-tag severity="warning" value="En vivo"></p-tag>
                }
                <span>{{ corridaDetalle()?.estado || 'sin selección' }}</span>
                <p-button
                  label="Abrir Excel"
                  icon="pi pi-external-link"
                  severity="secondary"
                  [text]="true"
                  [disabled]="!resultadoExcelDisponible()"
                  (onClick)="abrirResultadoExcel()"
                />
                <p-button
                  label="Fijar selección"
                  icon="pi pi-lock"
                  severity="secondary"
                  [text]="true"
                  [loading]="mutandoCorrida()"
                  [disabled]="horariosSeleccionados.size === 0 || !corridaDetalle()"
                  (onClick)="fijarSeleccionados()"
                />
                <p-button
                  label="Regenerar no fijados"
                  icon="pi pi-refresh"
                  [text]="true"
                  [loading]="mutandoCorrida()"
                  [disabled]="!corridaDetalle()"
                  (onClick)="regenerarSeleccionados()"
                />
              </div>
            </div>
            @if (!corridaDetalle()) {
              <div class="solver-empty small">Selecciona una corrida para ver score, análisis y horarios generados.</div>
            } @else {
              <div class="solver-summary">
                <div><label>Score</label><strong>{{ corridaDetalle()?.score_text || 'N/D' }}</strong></div>
                <div><label>Tiempo</label><strong>{{ corridaDetalle()?.tiempo_solving_ms ?? 'N/D' }} ms</strong></div>
                <div><label>Generados</label><strong>{{ corridaDetalle()?.horarios_generados ?? 0 }}</strong></div>
                <div><label>Versión</label><strong>{{ corridaDetalle()?.version || 'N/D' }}</strong></div>
              </div>

              <div class="solver-json-block">
                <label>Análisis de Restricciones</label>
                @if (corridaAnalysis()) {
                  <div class="reporte-checks" style="margin-top: 0.5rem">
                    @for (constraint of corridaAnalysis()?.constraints || []; track constraint.name) {
                      @if (constraint.score !== '0hard/0soft') {
                        <div class="reporte-check">
                          <div>
                            <strong>{{ constraint.name }}</strong>
                            <p>Penalización: {{ constraint.score }}</p>
                          </div>
                          <span [class.ok]="!constraint.score.includes('-')">
                            {{ constraint.score.includes('-') ? '❌' : '⚠️' }}
                          </span>
                        </div>
                      }
                    }
                    @if ((corridaAnalysis()?.constraints?.length || 0) === 0) {
                      <div class="solver-empty small">No hay violaciones de restricciones.</div>
                    }
                  </div>
                } @else {
                  <textarea pTextarea rows="8" readonly [ngModel]="corridaAnalysisText()"></textarea>
                }
              </div>

              @if ((corridaDetalle()?.horarios?.length ?? 0) > 0) {
                <div class="solver-table-wrap">
                  <table class="solver-table">
                    <thead>
                      <tr>
                        <th>Fijar</th>
                        <th>Día</th>
                        <th>Hora</th>
                        <th>Grupo</th>
                        <th>Materia</th>
                        <th>Docente</th>
                        <th>Aula</th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (row of corridaDetalle()?.horarios || []; track row.id) {
                        <tr>
                          <td>
                            <input
                              type="checkbox"
                              [checked]="esHorarioSeleccionado(row.id) || !!row.fijado"
                              [disabled]="!!row.fijado"
                              (change)="alternarHorarioSeleccionado(row.id, $any($event.target).checked)"
                            />
                          </td>
                          <td>{{ labelDia(row.dia_semana) }}</td>
                          <td>{{ row.hora_inicio | slice:0:5 }}–{{ row.hora_fin | slice:0:5 }}</td>
                          <td>{{ row.nombre_grupo }}</td>
                          <td>{{ row.nombre_materia }}</td>
                          <td>{{ row.nombre_profesor }}</td>
                          <td>{{ row.nombre_aula }}</td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              }
            }
          </section>
        </div>
      }
    </div>

    <div class="solver-panel">
      <div class="solver-head">
        <div>
          <h3>Análisis del Motor (Timefold)</h3>
          <p>
            Calcula las infracciones a las reglas del motor basándose en el horario actualmente activo.
          </p>
        </div>
        <div class="solver-actions">
          <p-button
            label="Verificar Reglas"
            icon="pi pi-shield"
            (onClick)="verificarHorarioBackend()"
            [loading]="cargandoReporte()"
            [disabled]="!ctx.plantel() || !ctx.ciclo()"
          />
        </div>
      </div>

      @if (!reporteVerificacion()) {
        <div class="solver-empty">Pulsa "Verificar Reglas" para analizar el horario actual con Timefold.</div>
      } @else {
        <div class="reporte-grid">
          <section class="solver-card" style="grid-column: 1 / -1">
            <div class="solver-card-head"><strong>Desglose de Restricciones</strong></div>
            <div class="reporte-table-wrap">
              <table class="solver-table">
                <thead>
                  <tr>
                    <th>Regla (Constraint)</th>
                    <th>Penalización Parcial</th>
                    <th>Incidencias (Matches)</th>
                  </tr>
                </thead>
                <tbody>
                  @for (c of reporteVerificacion()?.constraints; track c.name) {
                    <tr>
                      <td>{{ c.name }}</td>
                      <td style="color: var(--pink-500); font-weight:600">{{ c.score }}</td>
                      <td>{{ c.matchesCount }}</td>
                    </tr>
                  }
                  @if (!reporteVerificacion()?.constraints?.length) {
                    <tr>
                      <td colspan="3" style="text-align:center; padding:2rem; color:var(--text-secondary)">
                        No hay penalizaciones. ¡El horario es 100% óptimo!
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </section>
        </div>
      }
    </div>

    <!-- Controles — la cascada plantel→nivel→grado→grupo vive en el topbar -->
    <div class="filter-bar">
      <p-selectButton
        [options]="modoOpts"
        [(ngModel)]="modo"
        optionLabel="label"
        optionValue="value"
        (onChange)="onModoChange()"
      />

      @if (modo === 'profesor' && !esDocenteSelf()) {
        <p-select
          [options]="profesores()"
          [(ngModel)]="selectedProfesor"
          optionLabel="_label"
          placeholder="Seleccionar docente..."
          (onChange)="cargar()"
          [showClear]="true"
          [filter]="true" filterPlaceholder="Buscar..."/>
      }
      @if (modo === 'profesor' && esDocenteSelf()) {
        <span class="mi-horario-badge"><i class="pi pi-user"></i> Mi Horario</span>
      }
    </div>

    <!-- ── Grid semanal ── -->
    @if ((modo === 'grupo' && !selectedGrupoId) || (modo === 'profesor' && !esDocenteSelf() && !selectedProfesor)) {
      <div class="empty-state">
        <i class="pi pi-calendar" style="font-size:2.5rem;color:#CBD5E1"></i>
        <p>Selecciona un {{ modo === 'grupo' ? 'grupo' : 'docente' }} para ver el horario.</p>
      </div>
    } @else if (entradas().length === 0) {
      <div class="empty-state">
        <i class="pi pi-calendar-plus" style="font-size:2.5rem;color:#CBD5E1"></i>
        <p>No hay entradas de horario. Usa "Nueva entrada" para comenzar.</p>
      </div>
      <app-horario-grid
        [dias]="dias"
        [franjas]="franjas()"
        [entradas]="entradas()"
        [modo]="modo"
        (claseDrop)="onClaseDrop($event)"
        (claseClick)="abrirEditar($event)">
      </app-horario-grid>
    }
  `,
  styles: [`
    .filter-bar { display: flex; gap: 0.75rem; align-items: center; margin-bottom: 1.25rem; flex-wrap: wrap; }
    .mi-horario-badge { display:inline-flex; align-items:center; gap:.4rem; padding:.4rem .75rem; border-radius:6px; background:var(--p-primary-50); color:var(--p-primary-700); font-size:.85rem; font-weight:500; }
    .page-header { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:1.25rem; }
    .page-header h2 { margin:0; }
    .subtitle { margin:0; font-size:.82rem; color:var(--p-text-color-secondary); }
    .empty-state { display: flex; flex-direction: column; align-items: center; gap: 0.75rem; padding: 4rem; color: #94A3B8; }

    /* Diálogo */
    .dlg-grid { display:flex; flex-direction:column; gap:.75rem; padding:.5rem 0; }
    .dlg-row  { display:grid; grid-template-columns:1fr 1fr; gap:.75rem; }
    .dlg-lbl  { display:block; font-size:.8rem; font-weight:600; margin-bottom:.3rem; color:var(--p-text-color); }
    .asc-reemplazar { display:flex; align-items:center; gap:.35rem; font-size:.8rem; color:var(--p-text-muted-color); cursor:pointer; user-select:none; }
    .asc-reemplazar input { cursor:pointer; }

    .solver-panel { margin-bottom: 1.5rem; padding: 1rem; border: 1px solid var(--surface-border); border-radius: 12px; background: linear-gradient(180deg, var(--surface-card), var(--surface-50)); }
    .solver-head { display:flex; justify-content:space-between; gap:1rem; align-items:flex-start; margin-bottom:1rem; }
    .solver-head h3 { margin:0 0 .25rem 0; font-family: var(--font-display); }
    .solver-head p { margin:0; color: var(--p-text-color-secondary); font-size:.85rem; max-width: 70ch; }
    .solver-actions { display:flex; gap:.5rem; flex-wrap:wrap; justify-content:flex-end; }
    .solver-grid { display:grid; grid-template-columns: 320px 1fr; gap:1rem; }
    .solver-card { background: var(--surface-card); border: 1px solid var(--surface-border); border-radius: 10px; padding: .9rem; min-height: 180px; }
    .solver-card-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:.75rem; }
    .solver-card-head span { color: var(--p-text-color-secondary); font-size:.82rem; }
    .solver-empty { color: var(--p-text-color-secondary); padding:.75rem; border: 1px dashed var(--surface-border); border-radius: 8px; background: var(--surface-hover); }
    .solver-empty.small { padding:.5rem; font-size:.82rem; }
    .solver-list { display:flex; flex-direction:column; gap:.5rem; }
    .solver-list-item { width:100%; border:1px solid var(--surface-border); background:var(--surface-overlay); border-radius:8px; padding:.7rem .75rem; display:flex; justify-content:space-between; align-items:center; cursor:pointer; text-align:left; }
    .solver-list-item:hover { border-color: var(--nevadi-red); }
    .solver-list-item strong { display:block; }
    .solver-list-item span, .solver-list-item small { display:block; color: var(--p-text-color-secondary); font-size:.78rem; }
    .solver-summary { display:grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap:.75rem; margin-bottom: .9rem; }
    .solver-summary label { display:block; font-size:.72rem; color: var(--p-text-color-secondary); margin-bottom:.15rem; }
    .solver-summary strong { font-size:.92rem; }
    .solver-json-block { display:flex; flex-direction:column; gap:.35rem; margin-bottom:.9rem; }
    .solver-json-block label { font-size:.8rem; font-weight:600; color: var(--p-text-color); }
    .solver-json-block textarea { width:100%; min-height:160px; resize:vertical; }
    .solver-table-wrap { overflow:auto; border:1px solid var(--surface-border); border-radius:8px; }
    .solver-table { width:100%; border-collapse:collapse; font-size:.82rem; }
    .solver-table th, .solver-table td { padding:.55rem .6rem; border-bottom:1px solid var(--surface-border); text-align:left; vertical-align:top; }
    .solver-table th { position:sticky; top:0; background: var(--surface-100); z-index:1; }
    .reporte-summary {
      display:grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap:.75rem;
      margin-bottom: 1rem;
    }
    .reporte-summary div { background: var(--surface-card); border:1px solid var(--surface-border); border-radius:8px; padding:.7rem .8rem; }
    .reporte-summary label { display:block; font-size:.72rem; color: var(--p-text-color-secondary); margin-bottom:.2rem; }
    .reporte-summary strong { font-size:.92rem; }
    .reporte-grid { display:grid; grid-template-columns: 1.4fr .9fr; gap:1rem; }
    .reporte-table-wrap { overflow:auto; border:1px solid var(--surface-border); border-radius:8px; }
    .reporte-checks, .reporte-conflicts { display:flex; flex-direction:column; gap:.6rem; }
    .reporte-check, .reporte-conflict {
      display:flex;
      justify-content:space-between;
      gap:1rem;
      padding:.7rem .8rem;
      border:1px solid var(--surface-border);
      border-radius:8px;
      background:var(--surface-card);
      align-items:flex-start;
    }
    .reporte-check strong, .reporte-conflict strong { display:block; margin-bottom:.2rem; }
    .reporte-check p, .reporte-conflict p { margin:0; color: var(--p-text-color-secondary); font-size:.8rem; }
    .reporte-check span.ok { font-size:1rem; font-weight:700; color:#166534; }
    .reporte-check span:not(.ok) { font-size:1rem; font-weight:700; color:#b91c1c; }
    @media (max-width: 1100px) {
      .solver-grid { grid-template-columns: 1fr; }
      .solver-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
      .reporte-summary { grid-template-columns: 1fr; }
      .reporte-grid { grid-template-columns: 1fr; }
      .solver-head { flex-direction:column; }
    }
  `],
})
export class HorariosComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();
  private readonly api = inject(ApiService);
  readonly ctx = inject(ContextService);
  private readonly notify = inject(ApexNotificationService);

  readonly dias = DIAS.slice(1).map((label, i) => ({ num: i + 1, label }));
  readonly diasOpts = DIAS.slice(1).map((label, i) => ({ label, value: i + 1 }));
  readonly horasOpts = HORAS_INICIO;
  readonly duracionOpts = DURACIONES;

  readonly modoOpts = [
    { label: 'Por grupo', value: 'grupo' },
    { label: 'Por docente', value: 'profesor' },
  ];

  modo: 'grupo' | 'profesor' = 'grupo';
  selectedProfesor: any | null = null;
  /** ID del grupo activo; en modo=grupo viene del contexto global (topbar). */
  selectedGrupoId: string | null = null;

  gruposOpts = signal<GrupoConLabel[]>([]);
  profesores = signal<any[]>([]);
  materias = signal<MateriaOpt[]>([]);
  aulas = signal<AulaOpt[]>([]);
  entradas = signal<HorarioEntry[]>([]);
  corridasSolver = signal<SolverCorrida[]>([]);
  corridaDetalle = signal<SolverDetail | null>(null);
  cargandoCorridas = signal(false);
  ejecutandoSolver = signal(false);
  mutandoCorrida = signal(false);
  reporteVerificacion = signal<any>(null);
  cargandoReporte = signal(false);
  mostrarReglasIA = signal(false);
  reglaFraseIA = signal('');
  reglaInterpretada = signal<any>(null);
  parseandoRegla = signal(false);
  guardandoRegla = signal(false);
  private corridasPollHandle: ReturnType<typeof setInterval> | null = null;
  private horariosSeleccionados = new Set<string>();

  /** Grupo activo leído del contexto global (para modo=grupo). */
  get selectedGrupo(): GrupoConLabel | null {
    const g = this.ctx.grupo();
    if (!g?.id) return null;
    return { ...g, _label: grupoLabel(g) } as GrupoConLabel;
  }

  showDialog = signal(false);
  guardando = signal(false);
  editEntry: HorarioEntry | null = null;

  // aSc TimeTables
  exportandoAsc = signal(false);
  importandoAsc = signal(false);
  reemplazarAsc = false;

  form: {
    dia_semana: number | null;
    hora_inicio: string;
    duracion: number;
    materia_id: string | null;
    profesor_id: string | null;
    grupo_id: string | null;
    aula_id: string | null;
    fijado: boolean;
  } = this.resetForm();

  modoLabel = computed(() => this.modo === 'grupo' ? 'Grupo' : 'Docente');

  /** El docente ve "Mi Horario" self-service — sin buscarse en el selector de profesores. */
  readonly esDocenteSelf = computed(() => this.ctx.usuario()?.rol === 'DOCENTE');

  readonly franjas = computed(() => {
    const horas = [...new Set(this.entradas().map(e => e.hora_inicio))];
    return horas.sort();
  });

  readonly profeLabel = (p: any) => `${p.nombre ?? ''} ${p.apellido_paterno ?? ''}`.trim() || p.numero_empleado;
  readonly aulaLabel = (a: AulaOpt) => a.codigo ? `${a.codigo} — ${a.nombre}` : a.nombre;

  constructor() {
    // Recargar catálogos cuando cambia el plantel en el contexto global.
    effect(() => {
      const plantel = this.ctx.plantel();
      if (plantel?.id) {
        this.api.get<{ data: any[]; total: number }>('/profesores', { plantel_id: plantel.id })
          .pipe(takeUntil(this.destroy$)).subscribe(resp => this.profesores.set(
            resp.data.map((x: any) => ({ ...x, _label: `${x.nombre ?? ''} ${x.apellido_paterno ?? ''}`.trim() || x.numero_empleado }))
          ));
        this.api.get<AulaOpt[]>('/aulas', { plantel_id: plantel.id })
          .pipe(takeUntil(this.destroy$)).subscribe(a => this.aulas.set(
            a.map(x => ({ ...x, _label: x.codigo ? `${x.codigo} — ${x.nombre}` : x.nombre }))
          ));
      } else {
        this.profesores.set([]);
        this.aulas.set([]);
      }
    });

    // Recargar horario cuando cambia el grupo en el contexto global (modo=grupo).
    effect(() => {
      const grupo = this.ctx.grupo();
      this.selectedGrupoId = grupo?.id ?? null;
      this.entradas.set([]);
      this.materias.set([]);
      if (this.modo === 'grupo' && grupo?.id) {
        this.onGrupoChange();
      }
    });
  }

  ngOnInit(): void {
    // Catálogos cargados reactivamente desde effects.
    this.cargarCorridas();
    // Docente: entra directo a su propio horario, sin buscarse en el selector.
    if (this.esDocenteSelf()) {
      this.modo = 'profesor';
      this.cargarMiHorario();
    }
  }

  ngOnDestroy(): void {
    this.detenerPollingCorrida();
    this.destroy$.next();
    this.destroy$.complete();
  }

  onModoChange(): void {
    this.selectedGrupoId = null;
    this.selectedProfesor = null;
    this.entradas.set([]);
    if (this.modo === 'profesor' && this.esDocenteSelf()) {
      this.cargarMiHorario();
    }
  }

  onGrupoChange(): void {
    this.entradas.set([]);
    this.materias.set([]);
    if (!this.selectedGrupoId) return;
    this.cargar();

    const grupo = this.selectedGrupo;
    if (!grupo) return;
    const gId = grupo.grado_id;
    this.api.get<any[]>('/planes-estudio', { grado_id: gId })
      .pipe(takeUntil(this.destroy$)).subscribe(p => this.materias.set(p.map((x: any) => x.materia).filter(Boolean)));
  }

  puedeUsarSolver(): boolean {
    return Boolean(this.ctx.plantel()?.id && this.ctx.ciclo()?.id && this.ctx.nivelAcceso() <= 3);
  }

  labelDia(dia: number | null | undefined): string {
    return dia ? (this.dias.find(x => x.num === dia)?.label ?? `Día ${dia}`) : 'N/D';
  }

  corridaAnalysisText(): string {
    const detalle = this.corridaDetalle();
    if (!detalle?.score_analysis_json) return 'Sin análisis disponible.';
    try {
      const parsed = JSON.parse(detalle.score_analysis_json);
      return JSON.stringify(parsed, null, 2);
    } catch {
      return detalle.score_analysis_json;
    }
  }

  corridaAnalysis(): any {
    const detalle = this.corridaDetalle();
    if (!detalle?.score_analysis_json) return null;
    try {
      return JSON.parse(detalle.score_analysis_json);
    } catch {
      return null;
    }
  }

  resultadoExcelDisponible(): boolean {
    return Boolean(this.corridaDetalle()?.resultado_excel_url);
  }

  abrirResultadoExcel(): void {
    const url = this.corridaDetalle()?.resultado_excel_url;
    if (!url) return;
    window.open(url, '_blank', 'noopener');
  }

  esCorridaActiva(corrida: SolverCorrida | SolverDetail | null | undefined): boolean {
    const estado = String(corrida?.estado ?? '').toUpperCase();
    return estado.includes('RUNNING') || estado.includes('EN_PROCESO') || estado.includes('PROCESS') || estado.includes('PENDING');
  }

  iniciarPollingCorrida(corridaId: string): void {
    this.detenerPollingCorrida();
    this.corridasPollHandle = setInterval(() => {
      this.api.get<SolverDetail>(`/horarios/solver/corridas/${corridaId}`).pipe(takeUntil(this.destroy$)).subscribe({
        next: corrida => {
          this.corridaDetalle.set(corrida);
          this.sincronizarSeleccionDesdeDetalle(corrida);
          this.corridasSolver.update(corridas => corridas.map(c => c.id === corrida.id ? { ...c, ...corrida } : c));
          if (!this.esCorridaActiva(corrida)) {
            this.detenerPollingCorrida();
          }
        },
        error: () => {
          this.detenerPollingCorrida();
        },
      });
    }, 8000);
  }

  detenerPollingCorrida(): void {
    if (this.corridasPollHandle) {
      clearInterval(this.corridasPollHandle);
      this.corridasPollHandle = null;
    }
  }

  cargarCorridas(): void {
    if (!this.puedeUsarSolver()) {
      this.corridasSolver.set([]);
      this.corridaDetalle.set(null);
      return;
    }
    const plantelId = this.ctx.plantel()?.id;
    const cicloId = this.ctx.ciclo()?.id;
    if (!plantelId || !cicloId) {
      this.corridasSolver.set([]);
      this.corridaDetalle.set(null);
      return;
    }
    this.cargandoCorridas.set(true);
    this.api.get<SolverCorrida[]>('/horarios/solver/corridas', { plantel_id: plantelId, ciclo_id: cicloId }).pipe(takeUntil(this.destroy$)).subscribe({
      next: corridas => {
        this.corridasSolver.set(corridas);
        this.cargandoCorridas.set(false);
        if (!this.corridaDetalle() && corridas.length > 0) {
          this.seleccionarCorrida(corridas[0].id);
        }
      },
      error: () => {
        this.corridasSolver.set([]);
        this.corridaDetalle.set(null);
        this.cargandoCorridas.set(false);
        this.notify.error('Solver', 'No se pudieron cargar las corridas del plantel.');
      },
    });
  }

  verificarHorarioBackend(): void {
    const plantelId = this.ctx.plantel()?.id;
    const cicloId = this.ctx.ciclo()?.id;
    if (!plantelId || !cicloId) {
      this.notify.warning('Contexto requerido', 'Selecciona plantel y ciclo escolar para verificar el horario.');
      return;
    }
    this.cargandoReporte.set(true);
    this.api.get<any[]>('/horarios', { plantel_id: plantelId, ciclo_id: cicloId }).pipe(takeUntil(this.destroy$)).subscribe({
      next: horarios => {
        const payload = this.construirPayloadSolver(horarios ?? [], plantelId, cicloId);
        this.api.post<any>('/horarios/solver/verificar', payload).pipe(takeUntil(this.destroy$)).subscribe({
          next: analysis => {
            this.reporteVerificacion.set(analysis);
            this.cargandoReporte.set(false);
          },
          error: (err) => {
            this.cargandoReporte.set(false);
            this.notify.error('Verificación', err?.error?.detail || 'No se pudo verificar el horario con Timefold.');
          }
        });
      },
      error: () => {
        this.cargandoReporte.set(false);
        this.notify.error('Verificación', 'No se pudo cargar el horario del plantel para verificarlo.');
      },
    });
  }
  seleccionarCorrida(id: string): void {
    this.api.get<SolverDetail>(`/horarios/solver/corridas/${id}`).pipe(takeUntil(this.destroy$)).subscribe({
      next: corrida => {
        this.corridaDetalle.set(corrida);
        this.sincronizarSeleccionDesdeDetalle(corrida);
        if (this.esCorridaActiva(corrida)) {
          this.iniciarPollingCorrida(corrida.id);
        } else {
          this.detenerPollingCorrida();
        }
      },
      error: () => this.notify.error('Solver', 'No se pudo cargar el detalle de la corrida.'),
    });
  }


  parsearReglaIA(): void {
    const frase = this.reglaFraseIA();
    if (!frase) return;
    this.parseandoRegla.set(true);
    this.api.post<any>('/ai/horarios/reglas/parse', { frase }).pipe(takeUntil(this.destroy$)).subscribe({
      next: (res) => {
        this.parseandoRegla.set(false);
        this.reglaInterpretada.set(res.parsed);
        this.notify.success('IA', 'Regla interpretada correctamente.');
      },
      error: (err) => {
        this.parseandoRegla.set(false);
        this.notify.error('IA Error', err?.error?.detail ?? 'No se pudo interpretar la regla.');
      }
    });
  }

  guardarReglaIA(): void {
    const parsed = this.reglaInterpretada();
    const plantelId = this.ctx.plantel()?.id;
    const cicloId = this.ctx.ciclo()?.id;
    if (!parsed || !plantelId || !cicloId) return;
    
    const payload = {
      plantelId,
      cicloEscolarId: cicloId,
      tipo: parsed.tipo,
      params: parsed.params,
      peso: parsed.peso === 'HARD' ? 100 : 10,
      dura: parsed.peso === 'HARD',
      activa: true
    };
    
    this.guardandoRegla.set(true);
    // Nota: Llama al API de Spring Boot (/api/v1/horarios/reglas)
    this.api.post<any>('/horarios/reglas', payload).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.guardandoRegla.set(false);
        this.mostrarReglasIA.set(false);
        this.reglaFraseIA.set('');
        this.reglaInterpretada.set(null);
        this.notify.success('Reglas', 'Regla guardada en la base de datos.');
      },
      error: () => {
        this.guardandoRegla.set(false);
        this.notify.error('Error', 'No se pudo guardar la regla.');
      }
    });
  }

  iniciarSolver(): void {
    const plantelId = this.ctx.plantel()?.id;
    const cicloId = this.ctx.ciclo()?.id;
    if (!plantelId || !cicloId) {
      this.notify.warning('Contexto requerido', 'Selecciona plantel y ciclo escolar antes de ejecutar el solver.');
      return;
    }
    this.ejecutandoSolver.set(true);
    this.api.get<any[]>('/horarios', { plantel_id: plantelId, ciclo_id: cicloId }).pipe(takeUntil(this.destroy$)).subscribe({
      next: horarios => {
        const payload = this.construirPayloadSolver(horarios ?? [], plantelId, cicloId);
        this.api.post<SolverCorrida>('/horarios/solver/corridas', payload).pipe(takeUntil(this.destroy$)).subscribe({
          next: corrida => {
            this.ejecutandoSolver.set(false);
            this.notify.success('Solver', `Corrida ${corrida.id} creada con estado ${corrida.estado ?? 'N/D'}.`);
            this.cargarCorridas();
            this.seleccionarCorrida(corrida.id);
            if (this.esCorridaActiva(corrida)) {
              this.iniciarPollingCorrida(corrida.id);
            }
          },
          error: (err: any) => {
            this.ejecutandoSolver.set(false);
            this.notify.error('Solver', err?.error?.detail ?? 'No se pudo iniciar la corrida.');
          },
        });
      },
      error: () => {
        this.ejecutandoSolver.set(false);
        this.notify.error('Solver', 'No se pudieron leer los horarios vigentes del plantel.');
      },
    });
  }

  esHorarioSeleccionado(id: string): boolean {
    return this.horariosSeleccionados.has(id);
  }

  alternarHorarioSeleccionado(id: string, seleccionado: boolean): void {
    if (seleccionado) {
      this.horariosSeleccionados.add(id);
    } else {
      this.horariosSeleccionados.delete(id);
    }
  }

  sincronizarSeleccionDesdeDetalle(corrida: SolverDetail | null): void {
    this.horariosSeleccionados.clear();
    for (const horario of corrida?.horarios ?? []) {
      if (horario.fijado) {
        this.horariosSeleccionados.add(horario.id);
      }
    }
  }

  fijarSeleccionados(): void {
    const corridaId = this.corridaDetalle()?.id;
    if (!corridaId || this.horariosSeleccionados.size === 0) return;
    this.mutandoCorrida.set(true);
    this.api.post<SolverDetail>(`/horarios/solver/corridas/${corridaId}/lock`, { horarioIds: Array.from(this.horariosSeleccionados) }).pipe(takeUntil(this.destroy$)).subscribe({
      next: corrida => {
        this.mutandoCorrida.set(false);
        this.notify.success('Solver', 'Horarios fijados correctamente.');
        this.corridaDetalle.set(corrida);
        this.sincronizarSeleccionDesdeDetalle(corrida);
        this.cargarCorridas();
      },
      error: (err: any) => {
        this.mutandoCorrida.set(false);
        this.notify.error('Solver', err?.error?.detail ?? 'No se pudieron fijar los horarios seleccionados.');
      },
    });
  }

  regenerarSeleccionados(): void {
    const corridaId = this.corridaDetalle()?.id;
    if (!corridaId) return;
    this.mutandoCorrida.set(true);
    this.api.post<SolverCorrida>(`/horarios/solver/corridas/${corridaId}/regenerar`, { horarioIds: Array.from(this.horariosSeleccionados) }).pipe(takeUntil(this.destroy$)).subscribe({
      next: corrida => {
        this.mutandoCorrida.set(false);
        this.notify.success('Solver', `Nueva corrida ${corrida.id} generada.`);
        this.cargarCorridas();
        this.seleccionarCorrida(corrida.id);
      },
      error: (err: any) => {
        this.mutandoCorrida.set(false);
        this.notify.error('Solver', err?.error?.detail ?? 'No se pudo regenerar la corrida.');
      },
    });
  }

  private construirPayloadSolver(horarios: any[], plantelId: string, cicloId: string): Record<string, any> {
    const leccionesMap = new Map<string, Record<string, any>>();
    for (const row of horarios) {
      const diaSemana = Number(row.dia_semana);
      const horaInicio = String(row.hora_inicio ?? '').slice(0, 5);
      const horaFin = String(row.hora_fin ?? '').slice(0, 5);

      const leccionKey = String(row.id ?? `${row.grupo_id}|${row.materia_id}|${row.profesor_id}|${row.aula_id}`);
      if (!leccionesMap.has(leccionKey)) {
        leccionesMap.set(leccionKey, {
          id: row.id ?? null,
          grupo_id: row.grupo_id,
          materia_id: row.materia_id,
          profesor_id: row.profesor_id,
          aula_id: row.aula_id ?? null,
          ciclo_escolar_id: row.ciclo_escolar_id ?? cicloId,
          fijado: Boolean(row.fijado),
          timeslot: {
            dia_semana: diaSemana,
            hora_inicio: horaInicio,
            hora_fin: horaFin,
            turno: row.turno ?? null,
          },
        });
      }
    }

    return {
      plantel_id: plantelId,
      ciclo_escolar_id: cicloId,
      lecciones: Array.from(leccionesMap.values()),
    };
  }

  cargar(): void {
    const cicloId = this.ctx.ciclo()?.id;
    if (this.modo === 'grupo' && this.selectedGrupoId) {
      this.api.get<any>(`/horarios/grupo/${this.selectedGrupoId}`, cicloId ? { ciclo_id: cicloId } : undefined)
        .pipe(takeUntil(this.destroy$)).subscribe(r => this.entradas.set(r.entradas || r || []));
    } else if (this.modo === 'profesor' && this.esDocenteSelf()) {
      this.cargarMiHorario();
    } else if (this.modo === 'profesor' && this.selectedProfesor) {
      this.api.get<any>(`/horarios/profesor/${this.selectedProfesor.id}`, cicloId ? { ciclo_id: cicloId } : undefined)
        .pipe(takeUntil(this.destroy$)).subscribe(r => this.entradas.set(r.entradas || r || []));
    }
  }

  /** Self-service: carga el horario del docente autenticado, sin selector de profesor. */
  cargarMiHorario(): void {
    const cicloId = this.ctx.ciclo()?.id;
    this.api.get<any>('/horarios/mi-horario', cicloId ? { ciclo_id: cicloId } : undefined)
      .pipe(takeUntil(this.destroy$)).subscribe({
        next: r => this.entradas.set(r.entradas || r || []),
        error: () => this.notify.error('Mi Horario', 'No se encontró un registro de profesor asociado a tu cuenta'),
      });
  }

  entradasPor(dia: number, hora: string): HorarioEntry[] {
    return this.entradas().filter(e => e.dia_semana === dia && e.hora_inicio === hora);
  }

  colorMateria(nombre: string | null): string {
    if (!nombre) return 'var(--surface-hover)';
    const colorHash = nombre.split('').reduce((acc, char) => char.charCodeAt(0) + ((acc << 5) - acc), 0);
    const h = Math.abs(colorHash) % 360;
    return `hsl(${h}, 60%, 90%)`;
  }

  onClaseDrop(event: CdkDragDrop<any>) {
    if (event.previousContainer === event.container) {
      // Movimiento dentro de la misma celda, no hacer nada o reordenar si aplica
      return;
    }
    const item: HorarioEntry = event.item.data;
    const destino = event.container.data as {dia: number, franja: string};
    
    // Si la clase está fijada, no permitir moverla (validación extra)
    if (item.fijado) {
      this.notify.error('No se puede mover', 'La clase está fijada por el motor.');
      return;
    }
    
    // Aquí implementaremos la actualización en la API
    // Por ahora, actualicemos el estado localmente para feedback inmediato
    const oldDia = item.dia_semana;
    const oldHora = item.hora_inicio;
    
    item.dia_semana = destino.dia;
    item.hora_inicio = destino.franja;
    // Calcular hora de fin basado en la duración original
    const oldIni = new Date(`1970-01-01T${oldHora}`);
    const oldFin = new Date(`1970-01-01T${item.hora_fin}`);
    const diffMs = oldFin.getTime() - oldIni.getTime();
    
    const newIni = new Date(`1970-01-01T${destino.franja}:00`);
    const newFin = new Date(newIni.getTime() + diffMs);
    item.hora_fin = newFin.toTimeString().slice(0, 5) + ':00';
    
    // Si la actualización en BD falla, revertiríamos esto.
    // Lógica para enviar a la API
    const payload = {
      dia_semana: item.dia_semana,
      hora_inicio: item.hora_inicio,
      hora_fin: item.hora_fin
    };
    
    this.api.put(`/horarios/${item.id}`, payload).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.notify.success('Clase movida', 'El horario ha sido actualizado.');
        // Trigger de regeneración del signal
        this.entradas.set([...this.entradas()]); 
      },
      error: (err) => {
        this.notify.error('Error al mover', err?.error?.message || 'Hubo un problema de validación de traslape');
        // Revertir
        item.dia_semana = oldDia;
        item.hora_inicio = oldHora;
        item.hora_fin = oldFin.toTimeString().slice(0, 5) + ':00';
        this.entradas.set([...this.entradas()]);
      }
    });
  }

  abrirNuevo(): void {
    this.editEntry = null;
    this.form = {
      ...this.resetForm(),
      grupo_id: this.modo === 'grupo' ? (this.selectedGrupo as any)?.id ?? null : null,
      profesor_id: this.modo === 'profesor' ? this.selectedProfesor?.id ?? null : null,
    };
    this.showDialog.set(true);
  }

  abrirEditar(e: HorarioEntry): void {
    this.editEntry = e;
    const [h, m] = e.hora_inicio.split(':').map(Number);
    const [hFin, mFin] = e.hora_fin.split(':').map(Number);
    this.form = {
      dia_semana: e.dia_semana,
      hora_inicio: `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`,
      duracion: (hFin * 60 + mFin) - (h * 60 + m),
      materia_id: e.materia_id,
      profesor_id: e.profesor_id,
      grupo_id: e.grupo_id,
      aula_id: e.aula_id,
      fijado: e.fijado || false
    };
    this.showDialog.set(true);
  }

  guardar(): void {
    if (!this.form.dia_semana || !this.form.hora_inicio || !this.form.materia_id) {
      this.notify.warning('Validación', 'Completa los campos obligatorios: Día, Hora inicio y Materia.');
      return;
    }
    this.guardando.set(true);

    const horaFin = this.calcHoraFin(this.form.hora_inicio, this.form.duracion);
    const cicloId = this.ctx.ciclo()?.id;

    const body: Record<string, any> = {
      dia_semana: this.form.dia_semana,
      hora_inicio: this.form.hora_inicio,
      hora_fin: horaFin,
      materia_id: this.form.materia_id,
      profesor_id: this.form.profesor_id || null,
      aula_id: this.form.aula_id || null,
      ciclo_escolar_id: cicloId || null,
      origen: 'MANUAL',
      fijado: this.form.fijado ?? false,
    };

    if (this.modo === 'grupo') {
      body['grupo_id'] = (this.selectedGrupo as any)?.id;
    } else {
      body['grupo_id'] = this.form.grupo_id || null;
    }

    const req = this.editEntry
      ? this.api.patch(`/horarios/${this.editEntry.id}`, body)
      : this.api.post('/horarios', body);

    req.pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.notify.success(this.editEntry ? 'Entrada actualizada.' : 'Entrada agregada al horario.');
        this.guardando.set(false);
        this.showDialog.set(false);
        this.cargar();
      },
      error: (err: any) => {
        this.notify.error(err?.error?.detail ?? 'Error al guardar la entrada.');
        this.guardando.set(false);
      },
    });
  }

  eliminar(): void {
    if (!this.editEntry) return;
    if (!confirm('¿Eliminar esta entrada del horario?')) return;
    this.guardando.set(true);
    this.api.delete(`/horarios/${this.editEntry.id}`).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.notify.success('Entrada eliminada.');
        this.guardando.set(false);
        this.showDialog.set(false);
        this.cargar();
      },
      error: () => {
        this.notify.error('Error al eliminar.');
        this.guardando.set(false);
      },
    });
  }

  exportarASC(): void {
    const cicloId = this.ctx.ciclo()?.id;
    const plantelId = this.ctx.plantel()?.id;
    if (!cicloId) return;
    const params: Record<string, string> = {};
    if (plantelId) params['plantel_id'] = plantelId;
    this.exportandoAsc.set(true);
    this.api.getBlob(`/horarios/exportar-asc/${cicloId}`, params).pipe(takeUntil(this.destroy$)).subscribe({
      next: blob => {
        this.exportandoAsc.set(false);
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'horarios_asc.xml';
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.exportandoAsc.set(false);
        this.notify.error('Error', 'No se pudo exportar el XML aSc');
      },
    });
  }

  onArchivoAscSeleccionado(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    input.value = '';   // permite re-subir el mismo archivo
    const cicloId = this.ctx.ciclo()?.id;
    if (!cicloId) {
      this.notify.warning('Ciclo requerido', 'Selecciona un ciclo escolar en el contexto antes de importar');
      return;
    }
    const plantelId = this.ctx.plantel()?.id;
    const form = new FormData();
    form.append('file', file);
    if (plantelId) form.append('plantel_id', plantelId);
    form.append('reemplazar', String(this.reemplazarAsc));

    this.importandoAsc.set(true);
    this.api.upload<any>(`/horarios/importar-asc/${cicloId}`, form).pipe(takeUntil(this.destroy$)).subscribe({
      next: r => {
        this.importandoAsc.set(false);
        const detalle = r.errores > 0 ? ` (${r.errores} con error)` : '';
        const reemplazo = r.eliminados > 0 ? `, ${r.eliminados} reemplazadas` : '';
        this.notify.success('Importación aSc',
          `${r.exitosos} de ${r.total} clases importadas${reemplazo}${detalle}`);
        this.cargar();
      },
      error: e => {
        this.importandoAsc.set(false);
        this.notify.error('Error', e.error?.detail ?? e.error?.message ?? 'No se pudo importar el XML aSc');
      },
    });
  }

  private resetForm() {
    return {
      dia_semana: null as number | null,
      hora_inicio: '08:00',
      duracion: 60,
      materia_id: null as string | null,
      profesor_id: null as string | null,
      grupo_id: this.selectedGrupoId || this.ctx.grupo()?.id || null,
      aula_id: null as string | null,
      fijado: false
    };
  }

  private calcHoraFin(inicio: string, duracionMin: number): string {
    const [h, m] = inicio.split(':').map(Number);
    const total = h * 60 + m + duracionMin;
    return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
  }
}
