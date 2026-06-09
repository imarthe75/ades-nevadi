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
import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TabsModule } from 'primeng/tabs';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { SelectModule } from 'primeng/select';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { DialogModule } from 'primeng/dialog';
import { DrawerModule } from 'primeng/drawer';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { ProgressBarModule } from 'primeng/progressbar';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';

interface NivelOpt   { id: string; nombre_nivel: string; }
interface GradoOpt   { id: string; nombre_grado: string; numero_grado: number; nivel_educativo_id: string; }
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
interface CicloOpt  { id: string; nombre_ciclo: string; es_vigente: boolean; }
interface Tema {
  id: string;
  materia_plan_id: string;
  numero_tema: number;
  nombre_tema: string;
  descripcion: string | null;
  horas_estimadas: number | null;
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

@Component({
  selector: 'app-planes-estudio',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TabsModule, TableModule, ButtonModule, TagModule, SelectModule,
    InputNumberModule, InputTextModule, ToggleSwitchModule,
    DialogModule, DrawerModule, ToastModule, TooltipModule,
    ProgressBarModule, MessageModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast />

    <div class="page-header">
      <div>
        <h2>Planes y Programas de Estudio</h2>
        <p class="subtitle">CBU 2024 UAEMEX · NEM 2022 SEP — {{ cicloNombre() }}</p>
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
      </p-tablist>

      <p-tabpanels>

        <!-- ════════════════ MAPA CURRICULAR ════════════════ -->
        <p-tabpanel value="mapa">
          <!-- Selector de nivel -->
          <div class="nivel-tabs">
            @for (n of niveles(); track n.id) {
              <button class="nivel-chip"
                [class.activo]="nivelActivo === n.id"
                (click)="seleccionarNivel(n)">
                <i class="pi" [class]="nivelIcon(n.nombre_nivel)"></i>
                {{ n.nombre_nivel | titlecase }}
              </button>
            }
          </div>

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
                        @if (esAdmin()) {
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
              (onChange)="filtrarMaterias()" style="width:180px" />
            @if (esAdmin()) {
              <p-button label="Nueva materia" icon="pi pi-plus" size="small"
                (onClick)="abrirNuevaMateria()" />
            }
          </div>

          <p-table [value]="materiasFiltradas()" styleClass="p-datatable-sm p-datatable-striped"
            [paginator]="true" [rows]="20" [rowsPerPageOptions]="[20,50,100]"
            [showCurrentPageReport]="true" currentPageReportTemplate="{first}-{last} de {totalRecords}">
            <ng-template pTemplate="header">
              <tr>
                <th>Materia / Campo formativo</th>
                <th style="width:110px">Clave</th>
                <th style="width:120px">Nivel</th>
                <th style="width:75px;text-align:center">Hrs/sem</th>
                <th style="width:75px;text-align:center">Grados</th>
                <th style="width:65px;text-align:center">Estado</th>
                <th style="width:80px"></th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-m>
              <tr [class.row-inactiva]="!m.is_active">
                <td>
                  <span class="mat-link" (click)="abrirDetalle(m)">{{ m.nombre_materia }}</span>
                </td>
                <td style="font-family:monospace;font-size:.78rem">{{ m.clave_materia || '—' }}</td>
                <td>
                  <p-tag [value]="nivelNombre(m.nivel_educativo_id)"
                    [severity]="nivelSeverity(m.nivel_educativo_id)" />
                </td>
                <td style="text-align:center">{{ m.horas_semana || '—' }}</td>
                <td style="text-align:center">{{ gradosAsignados(m.id) }}</td>
                <td style="text-align:center">
                  <p-tag [value]="m.is_active ? 'Activa' : 'Inactiva'"
                    [severity]="m.is_active ? 'success' : 'secondary'" />
                </td>
                <td>
                  <div style="display:flex;gap:.2rem">
                    @if (esAdmin()) {
                      <p-button icon="pi pi-pencil" [text]="true" [rounded]="true" size="small"
                        pTooltip="Editar" (onClick)="abrirEditarMateria(m)" />
                      <p-button icon="pi pi-trash" [text]="true" [rounded]="true" size="small"
                        severity="danger" pTooltip="Desactivar"
                        (onClick)="desactivarMateria(m)" />
                    }
                    <p-button icon="pi pi-info-circle" [text]="true" [rounded]="true" size="small"
                      severity="info" pTooltip="Detalle" (onClick)="abrirDetalle(m)" />
                  </div>
                </td>
              </tr>
            </ng-template>
          </p-table>
        </p-tabpanel>

        <!-- ════════════════ TEMARIO ════════════════ -->
        <p-tabpanel value="temario">
          <div class="temario-selector">
            <p-select [options]="nivelesConMaterias()" [(ngModel)]="temarioNivelId"
              optionLabel="nombre_nivel" optionValue="id"
              placeholder="Nivel..." style="width:180px"
              (onChange)="onTemarioNivelChange()" />
            <p-select [options]="materiasParaTemario()" [(ngModel)]="temarioMateriaId"
              optionLabel="nombre_materia" optionValue="id"
              placeholder="Materia..." style="width:260px" [filter]="true"
              (onChange)="onTemarioMateriaChange()" />
            <p-select [options]="gradosParaTemario()" [(ngModel)]="temarioGradoId"
              optionLabel="nombre_grado" optionValue="id"
              placeholder="Grado..." style="width:160px"
              (onChange)="cargarTemas()" />
          </div>

          @if (temarioPlanId()) {
            <div class="temario-header">
              <h4>Temario — {{ temarioTituloActual() }}</h4>
              @if (esAdmin() || ctx.nivelAcceso() === 4) {
                <p-button label="Agregar tema" icon="pi pi-plus" size="small"
                  (onClick)="abrirNuevoTema()" />
              }
            </div>

            <p-table [value]="temas()" [loading]="loadingTemas()"
              styleClass="p-datatable-sm p-datatable-striped"
              [reorderableColumns]="false">
              <ng-template pTemplate="header">
                <tr>
                  <th style="width:50px">#</th>
                  <th>Tema</th>
                  <th>Descripción</th>
                  <th style="width:80px;text-align:center">Horas</th>
                  @if (esAdmin() || ctx.nivelAcceso() === 4) {
                    <th style="width:80px"></th>
                  }
                </tr>
              </ng-template>
              <ng-template pTemplate="body" let-t>
                <tr>
                  <td style="text-align:center;font-weight:700;color:var(--primary-color)">{{ t.numero_tema }}</td>
                  <td>{{ t.nombre_tema }}</td>
                  <td style="font-size:.8rem;color:var(--text-color-secondary)">{{ t.descripcion || '—' }}</td>
                  <td style="text-align:center">{{ t.horas_estimadas || '—' }}</td>
                  @if (esAdmin() || ctx.nivelAcceso() === 4) {
                    <td>
                      <div style="display:flex;gap:.2rem">
                        <p-button icon="pi pi-pencil" [text]="true" [rounded]="true" size="small"
                          (onClick)="abrirEditarTema(t)" />
                        <p-button icon="pi pi-trash" [text]="true" [rounded]="true" size="small"
                          severity="danger" (onClick)="eliminarTema(t)" />
                      </div>
                    </td>
                  }
                </tr>
              </ng-template>
              <ng-template pTemplate="emptymessage">
                <tr><td [colSpan]="5" style="text-align:center;padding:2rem;color:var(--text-muted)">
                  Sin temas registrados para este plan. Usa "Agregar tema" para comenzar.
                </td></tr>
              </ng-template>
            </p-table>
          } @else {
            <p-message severity="info" text="Selecciona nivel, materia y grado para ver o editar el temario." />
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
            optionLabel="nombre_nivel" optionValue="id" />
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
          <label>Número</label>
          <p-inputnumber [(ngModel)]="temaEdit.numero_tema" [min]="1" [max]="50" />
          <label>Nombre del tema *</label>
          <input pInputText [(ngModel)]="temaEdit.nombre_tema" placeholder="Ej. Números naturales" />
          <label>Descripción</label>
          <input pInputText [(ngModel)]="temaEdit.descripcion" placeholder="Resumen breve" />
          <label>Horas estimadas</label>
          <p-inputnumber [(ngModel)]="temaEdit.horas_estimadas" [min]="1" [max]="40" />
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="dlgTema=false" />
          <p-button label="Guardar" icon="pi pi-save" [loading]="savingTema()" (onClick)="guardarTema()" />
        </ng-template>
      }
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

        <h4 class="det-section">Asignaciones en plan vigente</h4>
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

        <h4 class="det-section">Tipos de actividades típicas</h4>
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
    .nivel-tabs { display:flex; gap:.5rem; margin-bottom:1rem; flex-wrap:wrap; }
    .nivel-chip { padding:.35rem .9rem; border-radius:20px; border:1px solid var(--surface-300);
      background:var(--surface-0); font-size:.82rem; cursor:pointer; display:flex; align-items:center; gap:.4rem; }
    .nivel-chip.activo { background:var(--primary-color); color:#fff; border-color:var(--primary-color); }
    .nivel-chip i { font-size:.85rem; }

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
    .horas-input { width:48px; border:1px solid var(--primary-color); border-radius:4px;
      text-align:center; font-size:.82rem; padding:.15rem .2rem; background:white; }
    .mapa-leyenda { font-size:.78rem; color:var(--text-muted); padding:.5rem; display:flex; align-items:center; gap:.5rem; flex-wrap:wrap; }

    /* ── Catálogo ── */
    .cat-toolbar { display:flex; gap:.5rem; align-items:center; margin-bottom:1rem; flex-wrap:wrap; }
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
export class PlanesEstudioComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly msg = inject(MessageService);
  readonly ctx       = inject(ContextService);

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
  nivelActivo     = '';
  selectedCicloId = '';
  editCellKey     = signal('');

  // ── Diálogos / Drawer ─────────────────────────────────────────────────────
  dlgMateria      = false;
  dlgTema         = false;
  drawerVisible   = false;
  materiaEdit: any       = null;
  temaEdit: any          = null;
  materiaDetalle  = signal<Materia | null>(null);
  estadisticasDetalle = signal<Estadisticas | null>(null);

  // ── Temario selectors ────────────────────────────────────────────────────
  temarioNivelId  = '';
  temarioMateriaId = '';
  temarioGradoId  = '';
  temarioPlanId   = signal('');

  // ── Catálogo filtros ─────────────────────────────────────────────────────
  busquedaCat     = '';
  filtroNivelId   = '';

  readonly esAdmin = computed(() => this.ctx.nivelAcceso() <= 1);

  readonly gradosActuales = computed(() =>
    this.grados()
      .filter(g => g.nivel_educativo_id === this.nivelActivo)
      .sort((a, b) => a.numero_grado - b.numero_grado)
  );

  readonly materiasNivelActual = computed(() =>
    this.materias()
      .filter(m => m.nivel_educativo_id === this.nivelActivo && m.is_active)
      .sort((a, b) => a.nombre_materia.localeCompare(b.nombre_materia))
  );

  readonly materiasFiltradas = computed(() => {
    let list = this.materias();
    if (this.filtroNivelId) list = list.filter(m => m.nivel_educativo_id === this.filtroNivelId);
    if (this.busquedaCat) {
      const q = this.busquedaCat.toLowerCase();
      list = list.filter(m => m.nombre_materia.toLowerCase().includes(q) ||
                               (m.clave_materia ?? '').toLowerCase().includes(q));
    }
    return list.sort((a, b) => a.nombre_materia.localeCompare(b.nombre_materia));
  });

  readonly nivelesConMaterias = computed(() => this.niveles());

  readonly materiasParaTemario = computed(() =>
    this.materias().filter(m => !this.temarioNivelId || m.nivel_educativo_id === this.temarioNivelId)
  );

  readonly gradosParaTemario = computed(() =>
    this.grados().filter(g => {
      const mat = this.materias().find(m => m.id === this.temarioMateriaId);
      return mat ? g.nivel_educativo_id === mat.nivel_educativo_id : false;
    }).sort((a, b) => a.numero_grado - b.numero_grado)
  );

  readonly planDeMateria = computed(() =>
    this.materiaDetalle()
      ? this.plan().filter(p => p.materia_id === this.materiaDetalle()!.id)
      : []
  );

  readonly cicloNombre = computed(() =>
    this.ciclos().find(c => c.id === this.selectedCicloId)?.nombre_ciclo ?? ''
  );

  readonly temarioTituloActual = computed(() => {
    const mat = this.materias().find(m => m.id === this.temarioMateriaId);
    const gr  = this.grados().find(g => g.id === this.temarioGradoId);
    return mat && gr ? `${mat.nombre_materia} — ${gr.nombre_grado}` : '';
  });

  ngOnInit(): void {
    this.api.get<NivelOpt[]>('/catalogs/niveles').subscribe(n => {
      this.niveles.set(n);
      if (n.length) this.nivelActivo = n[0].id;
    });

    this.api.get<CicloOpt[]>('/catalogs/ciclos', { solo_vigentes: true }).subscribe(c => {
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
    this.api.get<Materia[]>('/materias', { incluir_inactivas: true }).subscribe(m => this.materias.set(m));
  }

  cargarGrados(): void {
    const plantelId = this.ctx.plantel()?.id;
    const params: any = {};
    if (plantelId) params['plantel_id'] = plantelId;
    // Usar endpoint general de grados desde catálogos
    this.api.get<GradoOpt[]>('/catalogs/grados', params).subscribe({
      next: g => this.grados.set(g),
      error: () => {
        // Fallback: endpoint de planteles si existe
        if (plantelId) {
          this.api.get<GradoOpt[]>(`/planteles/${plantelId}/grados`).subscribe(g => this.grados.set(g));
        }
      },
    });
  }

  cargarPlan(): void {
    if (!this.selectedCicloId) return;
    this.loading.set(true);
    this.api.get<MateriaPlan[]>('/planes-estudio', { ciclo_id: this.selectedCicloId }).subscribe({
      next: p => { this.plan.set(p); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  seleccionarNivel(n: NivelOpt): void {
    this.nivelActivo = n.id;
    // Si no hay grados para este nivel, intentar cargar
    if (!this.gradosActuales().length) {
      const plantelId = this.ctx.plantel()?.id;
      const params: any = { nivel_id: n.id };
      if (plantelId) params['plantel_id'] = plantelId;
      this.api.get<GradoOpt[]>('/catalogs/grados', params).subscribe(g => {
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
    if (!this.selectedCicloId) return;
    this.api.post<MateriaPlan>('/planes-estudio', {
      materia_id: materiaId, grado_id: gradoId,
      ciclo_escolar_id: this.selectedCicloId,
      horas_semana: 4, es_obligatoria: true,
    }).subscribe({
      next: p => { this.plan.update(l => [...l, p]); },
      error: e => this.msg.add({ severity:'error', summary:'Error', detail: e.error?.detail ?? 'Error al asignar' }),
    });
  }

  guardarHoras(event: Event, planId: string): void {
    const val = +(event.target as HTMLInputElement).value;
    this.editCellKey.set('');
    if (!val || val < 1) return;
    this.api.patch<MateriaPlan>(`/planes-estudio/${planId}`, { horas_semana: val }).subscribe({
      next: updated => this.plan.update(l => l.map(p => p.id === planId ? { ...p, horas_semana: updated.horas_semana } : p)),
      error: () => this.msg.add({ severity:'error', summary:'Error al guardar horas' }),
    });
  }

  quitarAsignacion(planId: string, materiaId: string, gradoId: string): void {
    this.api.delete(`/planes-estudio/${planId}`).subscribe({
      next: () => this.plan.update(l => l.filter(p => p.id !== planId)),
      error: e => this.msg.add({ severity:'error', summary:'Error', detail: e.error?.detail }),
    });
  }

  // ── Catálogo CRUD ─────────────────────────────────────────────────────────

  filtrarMaterias(): void { /* computed se actualiza automáticamente */ }

  abrirNuevaMateria(): void {
    this.materiaEdit = { nombre_materia:'', clave_materia:'', nivel_educativo_id: this.nivelActivo, horas_semana: 4, is_active: true };
    this.dlgMateria = true;
  }

  abrirEditarMateria(m: Materia): void {
    this.materiaEdit = { ...m };
    this.dlgMateria = true;
  }

  guardarMateria(): void {
    if (!this.materiaEdit?.nombre_materia || !this.materiaEdit?.nivel_educativo_id) {
      this.msg.add({ severity:'warn', summary:'Campos requeridos' });
      return;
    }
    this.saving.set(true);
    const req = this.materiaEdit.id
      ? this.api.patch(`/materias/${this.materiaEdit.id}`, this.materiaEdit)
      : this.api.post('/materias', this.materiaEdit);

    req.subscribe({
      next: (m: any) => {
        this.materias.update(list =>
          this.materiaEdit.id ? list.map(x => x.id === m.id ? m : x) : [...list, m]
        );
        this.dlgMateria = false;
        this.saving.set(false);
        this.msg.add({ severity:'success', summary:'Materia guardada' });
      },
      error: e => { this.msg.add({ severity:'error', summary:'Error', detail: e.error?.detail }); this.saving.set(false); },
    });
  }

  desactivarMateria(m: Materia): void {
    this.api.patch(`/materias/${m.id}`, { is_active: false }).subscribe({
      next: () => {
        this.materias.update(l => l.map(x => x.id === m.id ? { ...x, is_active: false } : x));
        this.msg.add({ severity:'success', summary:'Materia desactivada' });
      },
      error: () => this.msg.add({ severity:'error', summary:'Error al desactivar' }),
    });
  }

  // ── Detalle / Drawer ──────────────────────────────────────────────────────

  abrirDetalle(m: Materia): void {
    this.materiaDetalle.set(m);
    this.estadisticasDetalle.set(null);
    this.drawerVisible = true;
    this.api.get<Estadisticas>(`/materias/${m.id}/estadisticas`).subscribe(e => this.estadisticasDetalle.set(e));
  }

  actividadesTipicas(m: Materia): string[] {
    const n = this.nivelNombre(m.nivel_educativo_id);
    if (n === 'PRIMARIA') return ['Ejercicios escritos', 'Proyectos', 'Exposición oral', 'Dibujo / arte', 'Juego educativo'];
    if (n === 'SECUNDARIA') return ['Ensayo', 'Experimento', 'Debate', 'Mapa conceptual', 'Proyecto integrador'];
    return ['Portafolio', 'Investigación', 'Examen', 'Presentación', 'Práctica de laboratorio', 'Reporte'];
  }

  // ── Temario ───────────────────────────────────────────────────────────────

  onTemarioNivelChange(): void {
    this.temarioMateriaId = '';
    this.temarioGradoId  = '';
    this.temas.set([]);
    this.temarioPlanId.set('');
  }

  onTemarioMateriaChange(): void {
    this.temarioGradoId = '';
    this.temas.set([]);
    this.temarioPlanId.set('');
  }

  cargarTemas(): void {
    if (!this.temarioMateriaId || !this.temarioGradoId) return;
    const p = this.plan().find(x =>
      x.materia_id === this.temarioMateriaId &&
      x.grado_id === this.temarioGradoId &&
      x.ciclo_escolar_id === this.selectedCicloId &&
      x.is_active
    );
    if (!p) {
      this.temarioPlanId.set('');
      this.temas.set([]);
      return;
    }
    this.temarioPlanId.set(p.id);
    this.loadingTemas.set(true);
    this.api.get<Tema[]>(`/planes-estudio/${p.id}/temas`).subscribe({
      next: t => { this.temas.set(t); this.loadingTemas.set(false); },
      error: () => this.loadingTemas.set(false),
    });
  }

  abrirNuevoTema(): void {
    const siguiente = (this.temas().length > 0 ? Math.max(...this.temas().map(t => t.numero_tema)) : 0) + 1;
    this.temaEdit = { numero_tema: siguiente, nombre_tema: '', descripcion: '', horas_estimadas: 2 };
    this.dlgTema = true;
  }

  abrirEditarTema(t: Tema): void {
    this.temaEdit = { ...t };
    this.dlgTema = true;
  }

  guardarTema(): void {
    if (!this.temaEdit?.nombre_tema) {
      this.msg.add({ severity:'warn', summary:'El nombre del tema es requerido' });
      return;
    }
    this.savingTema.set(true);
    const planId = this.temarioPlanId();
    const req = this.temaEdit.id
      ? this.api.put(`/planes-estudio/${planId}/temas/${this.temaEdit.id}`, this.temaEdit)
      : this.api.post(`/planes-estudio/${planId}/temas`, this.temaEdit);

    req.subscribe({
      next: (t: any) => {
        this.temas.update(list =>
          this.temaEdit.id ? list.map(x => x.id === t.id ? t : x) : [...list, t]
        );
        this.dlgTema = false;
        this.savingTema.set(false);
        this.msg.add({ severity:'success', summary:'Tema guardado' });
      },
      error: e => { this.msg.add({ severity:'error', detail: e.error?.detail }); this.savingTema.set(false); },
    });
  }

  eliminarTema(t: Tema): void {
    const planId = this.temarioPlanId();
    this.api.delete(`/planes-estudio/${planId}/temas/${t.id}`).subscribe({
      next: () => { this.temas.update(l => l.filter(x => x.id !== t.id)); this.msg.add({ severity:'success', summary:'Tema eliminado' }); },
      error: () => this.msg.add({ severity:'error', summary:'Error al eliminar' }),
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
    return g ? `${g.numero_grado}° ${g.nombre_grado}` : gradoId.slice(0, 6);
  }
}
