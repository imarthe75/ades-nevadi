/**
 * PlanesEstudioComponent — FASE 19 — Módulo de Planes y Programas completo.
 *
 * Tabs:
 *   1. Mapa Curricular     — grid grados × materias con horas inline-editables
 *   2. Catálogo            — CRUD de materias con detalle de rúbricas y estadísticas
 *   3. Temas / Temario     — gestión del temario por plan (materia × grado × ciclo)
 *   4. Estadísticas        — cobertura y uso del plan
 *
 * Principios: Oracle APEX Interactive Report + Moodle Course Outline.
 */
import { Component, OnDestroy, OnInit, inject, signal, computed, effect, ChangeDetectionStrategy} from '@angular/core';
import { forkJoin } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { TabsModule } from 'primeng/tabs';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { TextareaModule } from 'primeng/textarea';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { DialogModule } from 'primeng/dialog';
import { DrawerModule } from 'primeng/drawer';
import { TooltipModule } from 'primeng/tooltip';
import { ProgressBarModule } from 'primeng/progressbar';
import { MessageModule } from 'primeng/message';
import { ConfirmationService } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ApexNotificationService } from 'apex-component-library';
import { ImportButtonComponent } from '../../shared/components/import-button/import-button.component';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';

interface NivelOpt   { id: string; nombre_nivel: string; }
interface GradoOpt   { id: string; nombre_grado: string; numero_grado: number; nivel_educativo_id: string; plantel_id: string; plantel_nombre: string | null; }
interface Materia {
  id: string;
  nombre_materia: string;
  clave_materia: string | null;
  nivel_educativo_id: string;
  horas_semana: number | null;
  is_active: boolean;
  es_inglés?: boolean;
}
interface MateriaPlan {
  id: string;
  materia_id: string;
  grado_id: string;
  ciclo_escolar_id: string;
  horas_semana: number | null;
  es_obligatoria: boolean;
  is_active: boolean;
}
interface CicloOpt  {
  id: string; nombre_ciclo: string; es_vigente: boolean;
  // El endpoint /catalogs/ciclos devuelve la entidad JPA con nivel_educativo anidado
  // (lo consume así también el topbar — ver ContextCatalogService) — no aplanar aquí.
  nivel_educativo?: { id: string; nombre_nivel: string; autoridad_educativa: string };
}
interface Tema {
  id: string;
  materia_id: string;
  grado_id: string | null;
  ciclo_escolar_id: string | null;
  nombre_tema: string;
  descripcion: string | null;
  orden: number;
  periodo_sugerido: number | null;
}
interface Estadisticas {
  materia_id: string;
  nombre_materia: string;
  grados_asignados: number;
  total_tareas: number;
  total_calificaciones: number;
  total_rubricas: number;
  promedio_calificaciones: number | null;
}

const NIVEL_ORDER = ['PRIMARIA', 'SECUNDARIA', 'PREPARATORIA'];

@Component({
  selector: 'app-planes-estudio',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule,
    TabsModule, ButtonModule, TagModule, SelectModule, MultiSelectModule, TextareaModule,
    InputNumberModule, InputTextModule, ToggleSwitchModule,
    DialogModule, DrawerModule, TooltipModule,
    ProgressBarModule, MessageModule, ImportButtonComponent,
    InteractiveGridComponent, ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  template: `
    <p-confirmDialog />
    <div class="page-header">
      <div>
        <h2>Planes y Programas de Estudio</h2>
        <p class="subtitle">
          {{ cicloNombre() }}
          @if (sistemaEducativo()) {
            <p-tag [value]="sistemaEducativo()"
                   [severity]="sistemaEducativo() === 'SEP' ? 'info' : 'success'"
                   [style]="{'margin-left': '1rem'}"></p-tag>
          }
        </p>
      </div>
      <div style="display:flex;gap:.5rem;align-items:center">
        <p-select [options]="ciclos()" [(ngModel)]="selectedCicloId"
          optionLabel="nombre_ciclo" optionValue="id"
          placeholder="Ciclo..." styleClass="ctx-selector"
          (onChange)="onCicloChange()" />
      </div>
    </div>

    <p-tabs [(value)]="tabActivo">
      <p-tablist>
        <p-tab value="mapa"><i class="pi pi-table"></i> Mapa Curricular</p-tab>
        <p-tab value="catalogo"><i class="pi pi-list"></i> Catálogo</p-tab>
        <p-tab value="temario"><i class="pi pi-book"></i> Temario</p-tab>
        <p-tab value="nee"><i class="pi pi-heart"></i> Planes NEE</p-tab>
      </p-tablist>

      <p-tabpanels>

        <!-- ════════════════ MAPA CURRICULAR ════════════════ -->
        <p-tabpanel value="mapa">
          <!-- Selector de nivel -->
          <div class="nivel-tabs">
            @for (n of niveles(); track n.id) {
              <button class="nivel-chip"
                [class.activo]="nivelActivo() === n.id"
                (click)="seleccionarNivel(n)">
                <i class="pi" [class]="nivelIcon(n.nombre_nivel)"></i>
                {{ n.nombre_nivel | titlecase }}
              </button>
            }
          </div>

          @if (nivelActivoNombre()) {
            <div class="mapa-nivel-label">
              <i class="pi pi-graduation-cap"></i> {{ nivelActivoNombre() | titlecase }}
            </div>
          }

          @if (loading()) {
            <div style="padding:3rem;text-align:center;color:var(--text-muted)">Cargando mapa curricular…</div>
          } @else if (gradosActuales().length === 0) {
            <p-message severity="info" text="No hay grados configurados para este nivel." />
          } @else {
            <div class="mapa-scroll">
              <!-- Encabezado de grados -->
              <div class="mapa-grid" [style.grid-template-columns]="'260px ' + gradosActuales().map(() => '90px').join(' ')">
                <div class="mapa-head-label">Materia / Campo formativo</div>
                @for (g of gradosActuales(); track g.id) {
                  <div class="mapa-head-grado">
                    {{ g.numero_grado }}°<br>
                    <span style="font-size:.68rem;font-weight:400">{{ g.nombre_grado }}</span>
                    @if (g.plantel_nombre) {
                      <span style="font-size:.62rem;font-weight:600;color:var(--orange-600);display:block;margin-top:1px">{{ g.plantel_nombre }}</span>
                    }
                  </div>
                }
              </div>

              <!-- Filas por materia -->
              @for (m of materiasNivelActual(); track m.id) {
                <div class="mapa-grid mapa-row-data"
                  [class.inactiva]="!m.is_active"
                  [style.grid-template-columns]="'260px ' + gradosActuales().map(() => '90px').join(' ')">
                  <div class="mapa-mat-label" (click)="abrirDetalle(m)" pTooltip="Ver detalle" style="cursor:pointer">
                    <span class="mat-nombre">{{ m.nombre_materia }}</span>
                    @if (m.clave_materia) {
                      <code class="mat-clave">{{ m.clave_materia }}</code>
                    }
                  </div>

                  @for (g of gradosActuales(); track g.id) {
                    <div class="mapa-cell">
                      @if (getPlan(m.id, g.id); as p) {
                        <!-- Celda con asignación -->
                        @if (editCellKey() === m.id + '|' + g.id) {
                          <input class="horas-input"
                            type="number" min="1" max="20"
                            [value]="p.horas_semana ?? 0"
                            (blur)="guardarHoras($event, p.id)"
                            (keyup.enter)="guardarHoras($event, p.id)"
                            (keyup.escape)="editCellKey.set('')"
                            #horasInput autofocus />
                        } @else {
                          <div class="horas-badge"
                            [class.optativa]="!p.es_obligatoria"
                            [pTooltip]="p.es_obligatoria ? 'Obligatoria' : 'Optativa'"
                            (click)="esAdmin() ? editCellKey.set(m.id + '|' + g.id) : null"
                            [style.cursor]="esAdmin() ? 'pointer' : 'default'">
                            {{ p.horas_semana ?? '?' }}h
                          </div>
                        }
                        @if (p.estado_publicacion && p.estado_publicacion !== 'PUBLICADO') {
                          <span class="estado-badge" [pTooltip]="'v' + (p.version ?? 1)">{{ p.estado_publicacion }}</span>
                        }
                        @if (esAdmin()) {
                          @if (p.estado_publicacion === 'ARCHIVADO') {
                            <button class="cell-del" pTooltip="Publicar de nuevo"
                              (click)="publicarPlan(p.id)">↺</button>
                          } @else {
                            <button class="cell-del" pTooltip="Archivar (histórico)"
                              (click)="archivarPlan(p.id)">🗄</button>
                          }
                          <button class="cell-del" pTooltip="Quitar asignación"
                            (click)="quitarAsignacion(p.id, m.id, g.id)">×</button>
                        }
                      } @else {
                        <!-- Celda sin asignación -->
                        @if (esAdmin()) {
                          <button class="cell-add" pTooltip="Asignar materia a este grado"
                            (click)="asignarMateria(m.id, g.id)">+</button>
                        } @else {
                          <span class="no-asig">—</span>
                        }
                      }
                    </div>
                  }
                </div>
              }

              <!-- Totales por grado -->
              <div class="mapa-grid mapa-total-row"
                [style.grid-template-columns]="'260px ' + gradosActuales().map(() => '90px').join(' ')">
                <div class="mapa-mat-label"><strong>Total horas/semana</strong></div>
                @for (g of gradosActuales(); track g.id) {
                  <div class="mapa-cell">
                    <strong>{{ totalHoras(g.id) }}h</strong>
                  </div>
                }
              </div>
            </div>

            <div class="mapa-leyenda">
              <span class="horas-badge" style="font-size:.72rem">5h</span> Obligatoria &nbsp;
              <span class="horas-badge optativa" style="font-size:.72rem">3h</span> Optativa
              @if (esAdmin()) {
                &nbsp; · &nbsp; <span style="color:var(--text-secondary);font-size:.78rem">Clic en horas para editar · + para asignar · × para quitar</span>
              }
            </div>
          }
        </p-tabpanel>

        <!-- ════════════════ CATÁLOGO ════════════════ -->
        <p-tabpanel value="catalogo">
          <div class="cat-toolbar">
            <input pInputText placeholder="Buscar materia..." [(ngModel)]="busquedaCat"
              style="width:260px" (input)="filtrarMaterias()" />
            <p-select [options]="niveles()" [(ngModel)]="filtroNivelId"
              optionLabel="nombre_nivel" optionValue="id"
              placeholder="Todos los niveles" [showClear]="true"
              (onChange)="onFiltroNivelChange()" style="width:180px"
              [filter]="true" filterPlaceholder="Buscar..."/>
            <p-select [options]="gradosFiltradosPorNivel()" [(ngModel)]="filtroGradoId"
              optionLabel="nombre_grado" optionValue="id"
              placeholder="Todos los grados" [showClear]="true"
              (onChange)="filtrarMaterias()" style="width:180px"
              [filter]="true" filterPlaceholder="Buscar..."/>
            @if (esAdmin()) {
              <app-import-button entidad="materias" [onSuccess]="cargarMaterias.bind(this)" />
              <p-button label="Nueva materia" icon="pi pi-plus" size="small"
                (onClick)="abrirNuevaMateria()" />
            }
          </div>

          <app-interactive-grid
            [data]="materiasFiltradas()"
            [columns]="materiasColumns"
            (rowSelected)="abrirDetalle($event)"
          />
        </p-tabpanel>

        <!-- ════════════════ PLANES NEE (AC-014) ════════════════ -->
        <p-tabpanel value="nee">
          <div class="nee-toolbar">
            <p class="dlg-note" style="margin:0">
              Planes alternativos/reducidos para el grupo seleccionado en la barra superior
              @if (ctx.grupo()?.nombre_grupo) { — <strong>{{ ctx.grupo()?.nombre_grupo }}</strong> }
            </p>
            @if (esAdmin() && ctx.grupo()?.id) {
              <p-button label="Nuevo plan NEE" icon="pi pi-plus" size="small" (onClick)="abrirNuevoPlanNee()" />
            }
          </div>

          @if (!ctx.grupo()?.id) {
            <p-message severity="info" text="Selecciona un grupo en la barra superior para ver/crear planes NEE." />
          } @else if (planesNee().length === 0) {
            <p-message severity="info" text="Sin planes NEE registrados para este grupo." />
          } @else {
            @for (pn of planesNee(); track pn.id) {
              <div class="nee-card">
                <div style="flex:1">
                  <strong>{{ pn.motivo }}</strong>
                  <div style="font-size:.78rem;color:var(--text-color-secondary)">
                    Registrado {{ pn.fecha_creacion | date:'short' }}
                  </div>
                </div>
                @if (esAdmin()) {
                  <p-button icon="pi pi-trash" [text]="true" severity="danger" size="small"
                    (onClick)="eliminarPlanNee(pn.id)" />
                }
              </div>
            }
          }
        </p-tabpanel>

        <!-- ════════════════ TEMARIO ════════════════ -->
        <p-tabpanel value="temario">
          <div class="temario-selector">
            <p-select [options]="nivelesConMaterias()" [(ngModel)]="temarioNivelId"
              optionLabel="nombre_nivel" optionValue="id"
              placeholder="Nivel..." style="width:180px"
              (onChange)="onTemarioNivelChange()"
              [filter]="true" filterPlaceholder="Buscar..."/>
            <p-select [options]="gradosParaTemario()" [(ngModel)]="temarioGradoId"
              optionLabel="nombre_grado" optionValue="id"
              placeholder="Grado..." style="width:160px"
              (onChange)="onTemarioGradoChange()"
              [filter]="true" filterPlaceholder="Buscar..."/>
            <p-select [options]="materiasParaTemario()" [(ngModel)]="temarioMateriaId"
              optionLabel="nombre_materia" optionValue="id"
              placeholder="Materia..." style="width:260px" [filter]="true"
              (onChange)="onTemarioMateriaChange()" />
          </div>

          @if (temarioPlanId()) {
            <div class="temario-header">
              <h3>Temario — {{ temarioTituloActual() }}</h3>
              @if (esAdmin() || ctx.nivelAcceso() === 4) {
                <p-button label="Agregar tema" icon="pi pi-plus" size="small"
                  (onClick)="abrirNuevoTema()" />
              }
            </div>

            <app-interactive-grid
              [data]="temasFlat()"
              [columns]="temasColumns"
              [loading]="loadingTemas()"
              (rowSelected)="abrirEditarTema($event)"
            />
          } @else {
            <p-message severity="info" text="Selecciona nivel, grado y materia para ver o editar el temario." />
          }
        </p-tabpanel>

      </p-tabpanels>
    </p-tabs>

    <!-- ════ DIÁLOGO MATERIA ════ -->
    <p-dialog [(visible)]="dlgMateria" [header]="materiaEdit?.id ? 'Editar materia' : 'Nueva materia'"
      [modal]="true" [style]="{width:'440px'}">
      @if (materiaEdit) {
        <div class="form-grid">
          <label>Nombre *</label>
          <input pInputText [(ngModel)]="materiaEdit.nombre_materia" placeholder="Nombre de la materia" />
          <label>Clave</label>
          <input pInputText [(ngModel)]="materiaEdit.clave_materia"
            style="font-family:monospace;text-transform:uppercase" placeholder="CBU-MAT-01" />
          <label>Nivel *</label>
          <p-select [options]="niveles()" [(ngModel)]="materiaEdit.nivel_educativo_id"
            optionLabel="nombre_nivel" optionValue="id"
 [filter]="true" filterPlaceholder="Buscar..."/>
          <label>Tipo *</label>
          <p-select [options]="tiposMateria" [(ngModel)]="materiaEdit.tipo_materia"
            optionLabel="label" optionValue="value" placeholder="Selecciona tipo..." />
          <label>Campo formativo</label>
          <p-select [options]="camposFormativos" [(ngModel)]="materiaEdit.campo_formativo"
            optionLabel="label" optionValue="value" placeholder="(solo NEM primaria)" [showClear]="true" />
          <label>Horas/semana</label>
          <p-inputnumber [(ngModel)]="materiaEdit.horas_semana" [min]="1" [max]="30" />
          <label>Activa</label>
          <p-toggleswitch [(ngModel)]="materiaEdit.is_active" />
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="dlgMateria=false" />
          <p-button label="Guardar" icon="pi pi-save" [loading]="saving()" (onClick)="guardarMateria()" />
        </ng-template>
      }
    </p-dialog>

    <!-- ════ DIÁLOGO TEMA ════ -->
    <p-dialog [(visible)]="dlgTema" [header]="temaEdit?.id ? 'Editar tema' : 'Nuevo tema'"
      [modal]="true" [style]="{width:'480px'}">
      @if (temaEdit) {
        <div class="form-grid">
          <label>Orden</label>
          <p-inputnumber [(ngModel)]="temaEdit.orden" [min]="1" [max]="99" />
          <label>Nombre del tema *</label>
          <input pInputText [(ngModel)]="temaEdit.nombre_tema" placeholder="Ej. Números naturales" />
          <label>Descripción</label>
          <input pInputText [(ngModel)]="temaEdit.descripcion" placeholder="Resumen breve" />
          <label>Periodo sugerido</label>
          <p-inputnumber [(ngModel)]="temaEdit.periodo_sugerido" [min]="1" [max]="6" placeholder="1-6" />
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="dlgTema=false" />
          <p-button label="Guardar" icon="pi pi-save" [loading]="savingTema()" (onClick)="guardarTema()" />
        </ng-template>
      }
    </p-dialog>

    <!-- ════ DIALOG PLAN NEE (AC-014) ════ -->
    <p-dialog [(visible)]="dlgPlanNee" header="Nuevo plan alternativo/reducido (NEE)"
      [modal]="true" [style]="{width:'480px'}">
      <div class="form-grid">
        <label>Motivo *</label>
        <textarea pTextarea [(ngModel)]="planNeeForm.motivo" rows="3"
          placeholder="Ej. Alumno con discapacidad intelectual, plan reducido de 6 materias"></textarea>
        <label>Materias del plan alternativo *</label>
        <p-multiselect [options]="materiasNivelActual()" [(ngModel)]="planNeeForm.materiaIds"
          optionLabel="nombre_materia" optionValue="id" display="chip"
          placeholder="Selecciona las materias que sí aplican" style="width:100%" />
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="dlgPlanNee=false" />
        <p-button label="Guardar" icon="pi pi-save" [loading]="savingPlanNee()" (onClick)="guardarPlanNee()" />
      </ng-template>
    </p-dialog>

    <!-- ════ DRAWER DETALLE MATERIA ════ -->
    <p-drawer [(visible)]="drawerVisible" header="Detalle de materia"
      position="right" [style]="{width:'440px'}">
      @if (materiaDetalle()) {
        <div class="detalle-header">
          <h3>{{ materiaDetalle()!.nombre_materia }}</h3>
          @if (materiaDetalle()!.clave_materia) {
            <code class="det-clave">{{ materiaDetalle()!.clave_materia }}</code>
          }
          <p-tag [value]="nivelNombre(materiaDetalle()!.nivel_educativo_id)"
            [severity]="nivelSeverity(materiaDetalle()!.nivel_educativo_id)" />
        </div>

        @if (estadisticasDetalle()) {
          <div class="stats-grid">
            <div class="stat-card">
              <span class="stat-n">{{ estadisticasDetalle()!.grados_asignados }}</span>
              <span class="stat-l">Grados</span>
            </div>
            <div class="stat-card">
              <span class="stat-n">{{ estadisticasDetalle()!.total_tareas }}</span>
              <span class="stat-l">Tareas</span>
            </div>
            <div class="stat-card">
              <span class="stat-n">{{ estadisticasDetalle()!.total_calificaciones }}</span>
              <span class="stat-l">Calificaciones</span>
            </div>
            <div class="stat-card">
              <span class="stat-n">{{ estadisticasDetalle()!.promedio_calificaciones ?? '—' }}</span>
              <span class="stat-l">Promedio</span>
            </div>
          </div>
        }

        <h3 class="det-section">Asignaciones en plan vigente</h3>
        @if (planDeMateria().length > 0) {
          @for (p of planDeMateria(); track p.id) {
            <div class="asig-row">
              <span>{{ gradoNombre(p.grado_id) }}</span>
              <span>{{ p.horas_semana }}h/sem</span>
              <p-tag [value]="p.es_obligatoria ? 'Obligatoria' : 'Optativa'"
                [severity]="p.es_obligatoria ? 'success' : 'warn'" />
            </div>
          }
        } @else {
          <p style="color:var(--text-muted);font-size:.83rem">Sin asignaciones en el ciclo vigente.</p>
        }

        <h3 class="det-section">Tipos de actividades típicas</h3>
        <div class="actividades-list">
          @for (a of actividadesTipicas(materiaDetalle()!); track a) {
            <span class="act-chip">{{ a }}</span>
          }
        </div>
      }
    </p-drawer>
  `,
  styles: [`
    .page-header { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:1rem; }
    .page-header h2 { margin:0; }
    .subtitle { font-size:.82rem; color:var(--text-color-secondary); margin:0; }
    :host ::ng-deep .ctx-selector { min-width:160px }

    /* ── Nivel chips ── */
    .nivel-tabs { display:flex; gap:.5rem; margin-bottom:.5rem; flex-wrap:wrap; }
    .nivel-chip { padding:.35rem .9rem; border-radius:20px; border:1px solid var(--surface-300);
      background:var(--surface-0); font-size:.82rem; cursor:pointer; display:flex; align-items:center; gap:.4rem; }
    .nivel-chip.activo { background:var(--primary-color); color:#fff; border-color:var(--primary-color); }
    .nivel-chip i { font-size:.85rem; }
    .mapa-nivel-label { font-size:.78rem; font-weight:600; color:var(--primary-color);
      margin-bottom:.75rem; display:flex; align-items:center; gap:.35rem; }

    /* ── Mapa curricular ── */
    .mapa-scroll { overflow-x:auto; margin-bottom:.75rem; }
    .mapa-grid { display:grid; border-bottom:1px solid var(--surface-200); }
    .mapa-head-label, .mapa-head-grado { padding:.5rem .4rem; font-size:.75rem; font-weight:700;
      text-transform:uppercase; letter-spacing:.04em; color:var(--text-color-secondary);
      background:var(--surface-100); text-align:center; }
    .mapa-head-label { text-align:left; padding-left:.75rem; }
    .mapa-row-data:hover { background:var(--surface-50); }
    .mapa-row-data.inactiva { opacity:.4; }
    .mapa-mat-label { padding:.45rem .75rem; font-size:.83rem; display:flex; flex-direction:column; gap:.1rem; }
    .mat-nombre { font-weight:500; }
    .mat-clave { font-size:.68rem; color:var(--text-secondary); background:var(--surface-100); padding:.05rem .3rem; border-radius:3px; width:fit-content; }
    .mapa-cell { text-align:center; padding:.3rem .1rem; position:relative; display:flex; align-items:center; justify-content:center; gap:.2rem; min-height:34px; }
    .horas-badge { display:inline-block; background:var(--primary-100); color:var(--primary-700);
      font-size:.75rem; font-weight:700; padding:.15rem .45rem; border-radius:10px; }
    .horas-badge.optativa { background:var(--orange-100); color:var(--orange-700); }
    .no-asig { color:#d1d5db; font-size:.8rem; }
    .mapa-total-row { background:var(--surface-100); font-size:.83rem; }
    .mapa-total-row .mapa-cell { font-weight:700; }
    .cell-add { background:none; border:1px dashed var(--primary-300); color:var(--primary-400);
      border-radius:50%; width:22px; height:22px; font-size:.9rem; cursor:pointer; line-height:1;
      display:flex; align-items:center; justify-content:center; }
    .cell-add:hover { background:var(--primary-50); border-color:var(--primary-color); color:var(--primary-color); }
    .cell-del { background:none; border:none; color:#fca5a5; cursor:pointer; font-size:.9rem; padding:0 2px;
      opacity:0; transition:opacity .15s; }
    .mapa-cell:hover .cell-del { opacity:1; }
    .estado-badge { font-size:.58rem; font-weight:700; text-transform:uppercase; background:var(--yellow-100);
      color:var(--yellow-800); border-radius:3px; padding:1px 3px; margin-left:2px; }
    .horas-input { width:48px; border:1px solid var(--primary-color); border-radius:4px;
      text-align:center; font-size:.82rem; padding:.15rem .2rem; background:white; }
    .mapa-leyenda { font-size:.78rem; color:var(--text-muted); padding:.5rem; display:flex; align-items:center; gap:.5rem; flex-wrap:wrap; }

    /* ── Catálogo ── */
    .cat-toolbar { display:flex; gap:.5rem; align-items:center; margin-bottom:1rem; flex-wrap:wrap; }
    .nee-toolbar { display:flex; gap:.5rem; align-items:center; justify-content:space-between; margin-bottom:1rem; flex-wrap:wrap; }
    .nee-card { display:flex; align-items:center; gap:.5rem; padding:.6rem .75rem; border:1px solid var(--surface-200);
      border-radius:6px; margin-bottom:.5rem; }
    .dlg-note { font-size:.78rem; color:var(--text-color-secondary); }
    .mat-link { cursor:pointer; font-weight:500; }
    .mat-link:hover { color:var(--primary-color); text-decoration:underline; }
    .row-inactiva { opacity:.5; }

    /* ── Temario ── */
    .temario-selector { display:flex; gap:.5rem; margin-bottom:1rem; align-items:center; flex-wrap:wrap; }
    .temario-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:.75rem; }
    .temario-header h4 { margin:0; font-size:.9rem; font-weight:700; }

    /* ── Formularios ── */
    .form-grid { display:grid; grid-template-columns:130px 1fr; gap:.6rem 1rem; align-items:center; }
    .form-grid label { font-size:.83rem; color:var(--text-color-secondary); }

    /* ── Drawer detalle ── */
    .detalle-header { display:flex; flex-direction:column; gap:.4rem; margin-bottom:1.25rem; }
    .detalle-header h3 { margin:0; font-size:1rem; }
    .det-clave { font-size:.75rem; background:var(--surface-100); padding:.1rem .4rem; border-radius:4px; width:fit-content; }
    .det-section { font-size:.78rem; text-transform:uppercase; letter-spacing:.05em;
      color:var(--primary-color); margin:1.25rem 0 .6rem; border-top:1px solid var(--surface-200); padding-top:.75rem; }
    .stats-grid { display:grid; grid-template-columns:repeat(4,1fr); gap:.5rem; margin-bottom:.5rem; }
    .stat-card { background:var(--surface-50); border-radius:8px; padding:.6rem .4rem; text-align:center; border:1px solid var(--surface-200); }
    .stat-n { display:block; font-size:1.3rem; font-weight:700; color:var(--primary-color); }
    .stat-l { display:block; font-size:.68rem; color:var(--text-color-secondary); }
    .asig-row { display:flex; align-items:center; gap:.75rem; padding:.35rem 0; border-bottom:1px solid var(--surface-100); font-size:.83rem; }
    .asig-row span:first-child { flex:1; }
    .actividades-list { display:flex; flex-wrap:wrap; gap:.4rem; }
    .act-chip { background:var(--surface-100); border:1px solid var(--surface-300); border-radius:12px;
      font-size:.75rem; padding:.2rem .6rem; color:var(--text-color-secondary); }
  `],
})
export class PlanesEstudioComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private readonly api = inject(ApiService);
  private readonly notify = inject(ApexNotificationService);
  readonly ctx       = inject(ContextService);
  private readonly confirm = inject(ConfirmationService);

  // ── Datos base ─────────────────────────────────────────────────────────────
  niveles        = signal<NivelOpt[]>([]);
  grados         = signal<GradoOpt[]>([]);
  materias       = signal<Materia[]>([]);
  plan           = signal<MateriaPlan[]>([]);
  ciclos         = signal<CicloOpt[]>([]);
  temas          = signal<Tema[]>([]);

  // ── Estado UI ─────────────────────────────────────────────────────────────
  loading         = signal(false);
  loadingTemas    = signal(false);
  saving          = signal(false);
  savingTema      = signal(false);
  tabActivo       = 'mapa';
  nivelActivo     = signal('');
  selectedCicloId = '';
  editCellKey     = signal('');

  // ── Diálogos / Drawer ─────────────────────────────────────────────────────
  dlgMateria      = false;
  dlgTema         = false;
  drawerVisible   = false;
  materiaEdit: any       = null;
  temaEdit: any          = null;
  /** Debe coincidir con CrearMateriaUseCase.TIPOS_MATERIA_VALIDOS (chk_tipo_materia en BD). */
  readonly tiposMateria = [
    { label: 'Oficial SEP Primaria', value: 'OFICIAL_SEP_PRIMARIA' },
    { label: 'Oficial SEP Secundaria', value: 'OFICIAL_SEP_SECUNDARIA' },
    { label: 'Oficial UAEMEX Preparatoria', value: 'OFICIAL_UAEMEX_PREP' },
    { label: 'Nevadi Formativa', value: 'NEVADI_FORMATIVA' },
    { label: 'Nevadi Enriquecimiento', value: 'NEVADI_ENRIQUECIMIENTO' },
    { label: 'Nevadi Especializada', value: 'NEVADI_ESPECIALIZADA' },
  ];
  /** Debe coincidir con CrearMateriaUseCase.CAMPOS_FORMATIVOS_VALIDOS (ck_materias_campo_formativo en BD). */
  readonly camposFormativos = [
    { label: 'Lenguajes', value: 'LENGUAJES' },
    { label: 'Saberes y Pensamiento Científico', value: 'SABERES_PENSAMIENTO_CIENTIFICO' },
    { label: 'Ética, Naturaleza y Sociedades', value: 'ETICA_NATURALEZA_SOCIEDADES' },
    { label: 'De lo Humano y lo Comunitario', value: 'HUMANO_COMUNITARIO' },
  ];
  materiaDetalle  = signal<Materia | null>(null);
  estadisticasDetalle = signal<Estadisticas | null>(null);

  // ── Planes NEE (AC-014) ────────────────────────────────────────────────────
  planesNee       = signal<any[]>([]);
  dlgPlanNee      = false;
  savingPlanNee   = signal(false);
  planNeeForm: { motivo: string; materiaIds: string[] } = { motivo: '', materiaIds: [] };

  // ── Temario selectors (backing signals + getter/setter for [(ngModel)]) ─────
  private readonly _temarioNivelId   = signal('');
  get temarioNivelId()               { return this._temarioNivelId(); }
  set temarioNivelId(v: string)      { this._temarioNivelId.set(v); }

  private readonly _temarioGradoId   = signal('');
  get temarioGradoId()               { return this._temarioGradoId(); }
  set temarioGradoId(v: string)      { this._temarioGradoId.set(v); }

  private readonly _temarioMateriaId = signal('');
  get temarioMateriaId()             { return this._temarioMateriaId(); }
  set temarioMateriaId(v: string)    { this._temarioMateriaId.set(v); }

  temarioPlanId   = signal('');

  // ── Catálogo filtros ─────────────────────────────────────────────────────
  busquedaCat     = '';
  filtroNivelId   = '';
  filtroGradoId   = '';

  readonly esAdmin = computed(() => this.ctx.nivelAcceso() <= 1);

  readonly materiasColumns: ColumnConfig[] = [
    { field: 'nombre_materia',        header: 'Materia / Campo formativo', sortable: true },
    { field: 'clave_materia',         header: 'Clave',       width: '110px' },
    { field: '_nivel_nombre',         header: 'Nivel',       width: '120px' },
    { field: '_grados_asignados_str', header: 'Grados asignados', width: '220px' },
    { field: 'horas_semana',          header: 'Hrs/sem',     width: '75px' },
    { field: 'is_active',             header: 'Estado',      width: '80px' },
  ];

  readonly temasFlat = computed(() =>
    this.temas().map(t => ({
      ...t,
      descripcion_str:   t.descripcion ?? '—',
      periodo_str:       t.periodo_sugerido ? `${t.periodo_sugerido}P` : '—',
    }))
  );
  readonly temasColumns: ColumnConfig[] = [
    { field: 'orden',           header: 'Orden',    width: '60px', sortable: true },
    { field: 'nombre_tema',     header: 'Tema',     sortable: true },
    { field: 'descripcion_str', header: 'Descripción' },
    { field: 'periodo_str',     header: 'Periodo',  width: '80px' },
  ];

  readonly gradosActuales = computed(() =>
    this.grados()
      .filter(g => g.nivel_educativo_id === this.nivelActivo())
      .sort((a, b) => a.numero_grado - b.numero_grado)
  );

  readonly materiasNivelActual = computed(() =>
    this.materias()
      .filter(m => m.nivel_educativo_id === this.nivelActivo() && m.is_active)
      .sort((a, b) => a.nombre_materia.localeCompare(b.nombre_materia))
  );

  readonly gradosFiltradosPorNivel = computed(() => {
    const nivelId = this.filtroNivelId;
    if (!nivelId) return this.grados();
    return this.grados().filter(g => g.nivel_educativo_id === nivelId);
  });

  readonly materiasFiltradas = computed(() => {
    let list = this.materias();
    if (this.filtroNivelId) list = list.filter(m => m.nivel_educativo_id === this.filtroNivelId);
    if (this.filtroGradoId) {
      const materiaIdsEnGrado = new Set(
        this.plan().filter(p => p.grado_id === this.filtroGradoId && p.is_active).map(p => p.materia_id)
      );
      list = list.filter(m => materiaIdsEnGrado.has(m.id));
    }
    if (this.busquedaCat) {
      const q = this.busquedaCat.toLowerCase();
      list = list.filter(m => m.nombre_materia.toLowerCase().includes(q) ||
                               (m.clave_materia ?? '').toLowerCase().includes(q));
    }
    return list
      .sort((a, b) => a.nombre_materia.localeCompare(b.nombre_materia))
      .map(m => {
        const assignedGrades = this.plan()
          .filter(p => p.materia_id === m.id && p.is_active)
          .map(p => {
            const gr = this.grados().find(g => g.id === p.grado_id);
            return gr ? gr.nombre_grado : '';
          })
          .filter(Boolean);
        return {
          ...m,
          _nivel_nombre: this.nivelNombre(m.nivel_educativo_id),
          _grados_asignados_str: assignedGrades.join(', ') || 'Sin asignar'
        };
      });
  });

  readonly nivelesConMaterias = computed(() => this.niveles());

  // Cascade: Nivel → Grado → Materia
  // Grado options with plantel suffix to differentiate same-number grados across planteles
  readonly gradosParaTemario = computed(() =>
    this.grados()
      .filter(g => !this._temarioNivelId() || g.nivel_educativo_id === this._temarioNivelId())
      .sort((a, b) => a.numero_grado - b.numero_grado || (a.plantel_nombre ?? '').localeCompare(b.plantel_nombre ?? ''))
      .map(g => ({
        ...g,
        nombre_grado: g.plantel_nombre ? `${g.nombre_grado} — ${g.plantel_nombre}` : g.nombre_grado,
      }))
  );

  readonly materiasParaTemario = computed(() => {
    const gradoId = this._temarioGradoId();
    if (gradoId) {
      // Only show materias that have an active plan entry for this specific grado+ciclo
      const materiaIdsEnPlan = new Set(
        this.plan().filter(p => p.grado_id === gradoId && p.is_active).map(p => p.materia_id)
      );
      return this.materias()
        .filter(m => materiaIdsEnPlan.has(m.id))
        .sort((a, b) => a.nombre_materia.localeCompare(b.nombre_materia));
    }
    const nivelId = this._temarioNivelId();
    return nivelId
      ? this.materias().filter(m => m.nivel_educativo_id === nivelId).sort((a, b) => a.nombre_materia.localeCompare(b.nombre_materia))
      : [];
  });

  readonly nivelActivoNombre = computed(() =>
    this.niveles().find(n => n.id === this.nivelActivo())?.nombre_nivel ?? ''
  );

  readonly planDeMateria = computed(() =>
    this.materiaDetalle()
      ? this.plan().filter(p => p.materia_id === this.materiaDetalle()!.id)
      : []
  );

  readonly cicloNombre = computed(() =>
    this.ciclos().find(c => c.id === this.selectedCicloId)?.nombre_ciclo ?? ''
  );

  readonly sistemaEducativo = computed(() =>
    this.ciclos().find(c => c.id === this.selectedCicloId)?.nivel_educativo?.autoridad_educativa ?? ''
  );

  /** Cada nivel educativo tiene su PROPIO ciclo vigente (Primaria/Secundaria comparten
   *  nombre "2026-2027" pero son ciclos distintos; Preparatoria usa "26B") — nunca asumir
   *  que el ciclo seleccionado globalmente aplica a todos los niveles. */
  cicloIdParaNivel(nivelId: string): string | undefined {
    return this.ciclos().find(c => c.nivel_educativo?.id === nivelId)?.id;
  }

  readonly temarioTituloActual = computed(() => {
    const mat = this.materias().find(m => m.id === this._temarioMateriaId());
    const gr  = this.grados().find(g => g.id === this._temarioGradoId());
    return mat && gr ? `${mat.nombre_materia} — ${gr.nombre_grado}` : '';
  });

  constructor() {
    effect(() => {
      const p = this.ctx.plantel();
      const c = this.ctx.ciclo();
      this.cargarTodo();
    });
    effect(() => {
      const grupoId = this.ctx.grupo()?.id;
      if (grupoId) this.cargarPlanesNee(grupoId);
      else this.planesNee.set([]);
    });
  }

  // ── Planes NEE (AC-014) ────────────────────────────────────────────────────

  cargarPlanesNee(grupoId: string): void {
    this.api.get<any[]>('/planes-estudio/alternativos', { grupo_id: grupoId }).pipe(takeUntil(this.destroy$)).subscribe({
      next: l => this.planesNee.set(l),
      error: () => this.planesNee.set([]),
    });
  }

  abrirNuevoPlanNee(): void {
    this.planNeeForm = { motivo: '', materiaIds: [] };
    this.dlgPlanNee = true;
  }

  guardarPlanNee(): void {
    const grupoId = this.ctx.grupo()?.id;
    if (!grupoId) return;
    if (!this.planNeeForm.motivo.trim() || this.planNeeForm.materiaIds.length === 0) {
      this.notify.warning('Motivo y al menos una materia son obligatorios');
      return;
    }
    this.savingPlanNee.set(true);
    this.api.post('/planes-estudio/alternativos', {
      grupo_id: grupoId,
      motivo: this.planNeeForm.motivo.trim(),
      materias: this.planNeeForm.materiaIds.map(id => ({ materia_id: id })),
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.savingPlanNee.set(false);
        this.dlgPlanNee = false;
        this.notify.success('Plan NEE creado');
        this.cargarPlanesNee(grupoId);
      },
      error: e => {
        this.savingPlanNee.set(false);
        this.notify.error('Error', e.error?.detail ?? 'No se pudo crear el plan');
      },
    });
  }

  eliminarPlanNee(id: string): void {
    this.confirm.confirm({
      message: '¿Eliminar este plan NEE (Necesidades Educativas Especiales)?',
      header: 'Confirmar eliminación',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        const grupoId = this.ctx.grupo()?.id;
        this.api.delete(`/planes-estudio/alternativos/${id}`).pipe(takeUntil(this.destroy$)).subscribe({
          next: () => { if (grupoId) this.cargarPlanesNee(grupoId); },
          error: e => this.notify.error('Error', e.error?.detail),
        });
      },
    });
  }

  onFiltroNivelChange(): void {
    this.filtroGradoId = '';
    this.filtrarMaterias();
  }

  ngOnInit(): void {
    this.api.get<NivelOpt[]>('/catalogs/niveles').pipe(takeUntil(this.destroy$)).subscribe(n => {
      const sorted = [...n].sort((a, b) => {
        const ai = NIVEL_ORDER.indexOf(a.nombre_nivel.toUpperCase());
        const bi = NIVEL_ORDER.indexOf(b.nombre_nivel.toUpperCase());
        return (ai === -1 ? 99 : ai) - (bi === -1 ? 99 : bi);
      });
      this.niveles.set(sorted);
      if (sorted.length) this.nivelActivo.set(sorted[0].id);
    });

    this.api.get<CicloOpt[]>('/catalogs/ciclos', { solo_vigentes: true }).pipe(takeUntil(this.destroy$)).subscribe(c => {
      this.ciclos.set(c);
      if (c.length) {
        const vigente = c.find(x => x.es_vigente) ?? c[0];
        this.selectedCicloId = vigente.id;
        this.cargarTodo();
      }
    });
  }

  // ── Carga principal ───────────────────────────────────────────────────────

  cargarTodo(): void {
    this.cargarMaterias();
    this.cargarGrados();
    this.cargarPlan();
  }

  onCicloChange(): void { this.cargarPlan(); }

  cargarMaterias(): void {
    this.api.get<Materia[]>('/materias', { incluir_inactivas: true }).pipe(takeUntil(this.destroy$)).subscribe(m => this.materias.set(m));
  }

  cargarGrados(): void {
    // Planes de Estudio siempre necesita ver los grados de TODOS los planteles
    // (Mapa Curricular los distingue por columna con plantel_nombre) — no filtrar
    // por el plantel del topbar, y pedir explícitamente sin deduplicar.
    this.api.get<GradoOpt[]>('/catalogs/grados', { todos_planteles: true }).pipe(takeUntil(this.destroy$)).subscribe({
      next: g => this.grados.set(g),
      error: () => {},
    });
  }

  cargarPlan(): void {
    // Primaria/Secundaria/Preparatoria tienen cada una su PROPIO ciclo vigente
    // (Primaria y Secundaria comparten el nombre "2026-2027" pero son ciclos
    // distintos; Preparatoria usa "26B"). El Catálogo mezcla materias de los 3
    // niveles a la vez, así que hay que cargar el plan de TODOS los ciclos
    // vigentes y fusionarlos — filtrar por un solo ciclo_id dejaba "sin asignar"
    // cualquier materia que no perteneciera al primer ciclo cargado.
    const ciclosVigentes = this.ciclos().length ? this.ciclos() : [];
    if (!ciclosVigentes.length) return;
    this.loading.set(true);
    const requests = ciclosVigentes.map(c =>
      this.api.get<MateriaPlan[]>('/planes-estudio', { ciclo_id: c.id })
    );
    forkJoin(requests).pipe(takeUntil(this.destroy$)).subscribe({
      next: results => { this.plan.set(results.flat()); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  seleccionarNivel(n: NivelOpt): void {
    this.nivelActivo.set(n.id);
    if (!this.gradosActuales().length) {
      this.api.get<GradoOpt[]>('/catalogs/grados', { nivel_id: n.id, todos_planteles: true }).pipe(takeUntil(this.destroy$)).subscribe(g => {
        this.grados.update(cur => [...cur.filter(x => x.nivel_educativo_id !== n.id), ...g]);
      });
    }
  }

  // ── Helpers del mapa ─────────────────────────────────────────────────────

  getPlan(materiaId: string, gradoId: string): MateriaPlan | undefined {
    return this.plan().find(p => p.materia_id === materiaId && p.grado_id === gradoId && p.is_active);
  }

  totalHoras(gradoId: string): number {
    return this.plan()
      .filter(p => p.grado_id === gradoId && p.is_active)
      .reduce((s, p) => s + (p.horas_semana ?? 0), 0);
  }

  gradosAsignados(materiaId: string): number {
    return this.plan().filter(p => p.materia_id === materiaId && p.is_active).length;
  }

  // ── Asignaciones inline ───────────────────────────────────────────────────

  asignarMateria(materiaId: string, gradoId: string): void {
    // El "+" vive en el mapa curricular del nivel activo — usar SU ciclo vigente,
    // no el selector global (que puede corresponder a otro nivel).
    const cicloId = this.cicloIdParaNivel(this.nivelActivo());
    if (!cicloId) { this.notify.error('No hay ciclo vigente configurado para este nivel'); return; }
    this.api.post<MateriaPlan>('/planes-estudio', {
      materia_id: materiaId, grado_id: gradoId,
      ciclo_escolar_id: cicloId,
      horas_semana: 4, es_obligatoria: true,
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: p => { this.plan.update(l => [...l, p]); },
      error: e => this.notify.error('Error', e.error?.detail ?? 'Error al asignar'),
    });
  }

  guardarHoras(event: Event, planId: string): void {
    const val = +(event.target as HTMLInputElement).value;
    this.editCellKey.set('');
    if (!val || val < 1) return;
    this.api.patch<MateriaPlan>(`/planes-estudio/${planId}`, { horas_semana: val }).pipe(takeUntil(this.destroy$)).subscribe({
      next: updated => this.plan.update(l => l.map(p => p.id === planId ? { ...p, horas_semana: updated.horas_semana } : p)),
      error: () => this.notify.error('Error al guardar horas'),
    });
  }

  quitarAsignacion(planId: string, materiaId: string, gradoId: string): void {
    const nombreMateria = this.materias().find(m => m.id === materiaId)?.nombre_materia ?? 'esta materia';
    this.confirm.confirm({
      message: `¿Quitar la asignación de "${nombreMateria}" de este grado?`,
      header: 'Confirmar eliminación',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.api.delete(`/planes-estudio/${planId}`).pipe(takeUntil(this.destroy$)).subscribe({
          next: () => this.plan.update(l => l.filter(p => p.id !== planId)),
          error: e => this.notify.error('Error', e.error?.detail),
        });
      },
    });
  }

  /** AC-015: publicar/archivar una versión del plan de estudio (materia+grado+ciclo). */
  publicarPlan(planId: string): void {
    this.api.patch(`/planes-estudio/${planId}/publicar`, {}).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.plan.update(l => l.map(p => p.id === planId ? { ...p, estado_publicacion: 'PUBLICADO' } : p));
        this.notify.success('Plan publicado');
      },
      error: e => this.notify.error('Error', e.error?.detail),
    });
  }

  archivarPlan(planId: string): void {
    this.api.patch(`/planes-estudio/${planId}/archivar`, {}).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.plan.update(l => l.map(p => p.id === planId ? { ...p, estado_publicacion: 'ARCHIVADO' } : p));
        this.notify.success('Plan archivado');
      },
      error: e => this.notify.error('Error', e.error?.detail),
    });
  }

  // ── Catálogo CRUD ─────────────────────────────────────────────────────────

  filtrarMaterias(): void { /* computed se actualiza automáticamente */ }

  abrirNuevaMateria(): void {
    this.materiaEdit = { nombre_materia:'', clave_materia:'', nivel_educativo_id: this.nivelActivo(), tipo_materia: '', campo_formativo: null, horas_semana: 4, is_active: true };
    this.dlgMateria = true;
  }

  abrirEditarMateria(m: Materia): void {
    this.materiaEdit = { ...m };
    this.dlgMateria = true;
  }

  guardarMateria(): void {
    if (!this.materiaEdit?.nombre_materia || !this.materiaEdit?.nivel_educativo_id || !this.materiaEdit?.tipo_materia) {
      this.notify.warning('Campos requeridos');
      return;
    }
    this.saving.set(true);
    const req = this.materiaEdit.id
      ? this.api.patch(`/materias/${this.materiaEdit.id}`, this.materiaEdit)
      : this.api.post('/materias', this.materiaEdit);

    req.pipe(takeUntil(this.destroy$)).subscribe({
      next: (m: any) => {
        this.materias.update(list =>
          this.materiaEdit.id ? list.map(x => x.id === m.id ? m : x) : [...list, m]
        );
        this.dlgMateria = false;
        this.saving.set(false);
        this.notify.success('Materia guardada');
      },
      error: e => { this.notify.error('Error', e.error?.detail); this.saving.set(false); },
    });
  }

  desactivarMateria(m: Materia): void {
    this.api.patch(`/materias/${m.id}`, { is_active: false }).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.materias.update(l => l.map(x => x.id === m.id ? { ...x, is_active: false } : x));
        this.notify.success('Materia desactivada');
      },
      error: () => this.notify.error('Error al desactivar'),
    });
  }

  // ── Detalle / Drawer ──────────────────────────────────────────────────────

  abrirDetalle(m: Materia): void {
    this.materiaDetalle.set(m);
    this.estadisticasDetalle.set(null);
    this.drawerVisible = true;
    this.api.get<Estadisticas>(`/materias/${m.id}/estadisticas`).pipe(takeUntil(this.destroy$)).subscribe(e => this.estadisticasDetalle.set(e));
  }

  actividadesTipicas(m: Materia): string[] {
    const n = this.nivelNombre(m.nivel_educativo_id);
    if (n === 'PRIMARIA') return ['Ejercicios escritos', 'Proyectos', 'Exposición oral', 'Dibujo / arte', 'Juego educativo'];
    if (n === 'SECUNDARIA') return ['Ensayo', 'Experimento', 'Debate', 'Mapa conceptual', 'Proyecto integrador'];
    return ['Portafolio', 'Investigación', 'Examen', 'Presentación', 'Práctica de laboratorio', 'Reporte'];
  }

  // ── Temario ───────────────────────────────────────────────────────────────

  onTemarioNivelChange(): void {
    this._temarioGradoId.set('');
    this._temarioMateriaId.set('');
    this.temas.set([]);
    this.temarioPlanId.set('');
  }

  onTemarioGradoChange(): void {
    this._temarioMateriaId.set('');
    this.temas.set([]);
    this.temarioPlanId.set('');
  }

  onTemarioMateriaChange(): void {
    this.temas.set([]);
    this.temarioPlanId.set('');
    if (this._temarioMateriaId() && this._temarioGradoId()) this.cargarTemas();
  }

  cargarTemas(): void {
    if (!this._temarioMateriaId() || !this._temarioGradoId()) return;
    // No filtrar por selectedCicloId: plan() ya trae los 3 ciclos vigentes fusionados
    // (uno por nivel) y la combinación materia_id+grado_id ya es única por nivel.
    const p = this.plan().find(x =>
      x.materia_id === this._temarioMateriaId() &&
      x.grado_id === this._temarioGradoId() &&
      x.is_active
    );
    if (!p) {
      this.temarioPlanId.set('');
      this.temas.set([]);
      return;
    }
    this.temarioPlanId.set(p.id);
    this.loadingTemas.set(true);
    this.api.get<Tema[]>(`/planes-estudio/${p.id}/temas`).pipe(takeUntil(this.destroy$)).subscribe({
      next: t => { this.temas.set(t); this.loadingTemas.set(false); },
      error: () => this.loadingTemas.set(false),
    });
  }

  abrirNuevoTema(): void {
    const siguienteOrden = this.temas().length > 0
      ? Math.max(...this.temas().map(t => t.orden)) + 1
      : 1;
    this.temaEdit = { orden: siguienteOrden, nombre_tema: '', descripcion: '', periodo_sugerido: null };
    this.dlgTema = true;
  }

  abrirEditarTema(t: Tema): void {
    this.temaEdit = { ...t };
    this.dlgTema = true;
  }

  guardarTema(): void {
    if (!this.temaEdit?.nombre_tema) {
      this.notify.warning('El nombre del tema es requerido');
      return;
    }
    this.savingTema.set(true);
    const planId = this.temarioPlanId();
    const req = this.temaEdit.id
      ? this.api.put(`/planes-estudio/${planId}/temas/${this.temaEdit.id}`, this.temaEdit)
      : this.api.post(`/planes-estudio/${planId}/temas`, this.temaEdit);

    req.pipe(takeUntil(this.destroy$)).subscribe({
      next: (t: any) => {
        this.temas.update(list =>
          this.temaEdit.id ? list.map(x => x.id === t.id ? t : x) : [...list, t]
        );
        this.dlgTema = false;
        this.savingTema.set(false);
        this.notify.success('Tema guardado');
      },
      error: e => { this.notify.error('Error', e.error?.detail); this.savingTema.set(false); },
    });
  }

  eliminarTema(t: Tema): void {
    this.confirm.confirm({
      message: `¿Eliminar el tema "${t.nombre_tema}"?`,
      header: 'Confirmar eliminación',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        const planId = this.temarioPlanId();
        this.api.delete(`/planes-estudio/${planId}/temas/${t.id}`).pipe(takeUntil(this.destroy$)).subscribe({
          next: () => { this.temas.update(l => l.filter(x => x.id !== t.id)); this.notify.success('Tema eliminado'); },
          error: () => this.notify.error('Error al eliminar'),
        });
      },
    });
  }

  // ── Helpers de label/severity ─────────────────────────────────────────────

  nivelNombre(nid: string): string {
    return this.niveles().find(n => n.id === nid)?.nombre_nivel ?? '—';
  }

  nivelSeverity(nid: string): 'success' | 'info' | 'warn' | 'secondary' {
    const n = this.nivelNombre(nid);
    if (n === 'PRIMARIA') return 'success';
    if (n === 'SECUNDARIA') return 'info';
    return 'warn';
  }

  nivelIcon(nombre: string): string {
    const map: Record<string, string> = { PRIMARIA:'pi-star', SECUNDARIA:'pi-book', PREPARATORIA:'pi-graduation-cap' };
    return map[nombre] ?? 'pi-school';
  }

  gradoNombre(gradoId: string): string {
    const g = this.grados().find(x => x.id === gradoId);
    return g ? `${g.numero_grado}° ${g.nombre_grado}` : '—';
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
