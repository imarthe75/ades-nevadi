import { Component, OnDestroy, inject, signal, computed, effect, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { CardModule } from 'primeng/card';
import { SkeletonModule } from 'primeng/skeleton';
import { ButtonModule } from 'primeng/button';
import { DividerModule } from 'primeng/divider';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TooltipModule } from 'primeng/tooltip';
import { TagModule } from 'primeng/tag';
import { CheckboxModule } from 'primeng/checkbox';
import { MessageModule } from 'primeng/message';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import type { ResumenPlantel, Plantel } from '../../core/models';
import { ApexNotificationService, ApexChartComponent } from 'apex-component-library';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';

interface NivelStats {
  nivel_educativo_id: string;
  nombre_nivel: string;
  grupos_activos: number;
  grados: number;
}

interface PlantelStats {
  id: string;
  nombre_plantel: string;
  clave_ct: string | null;
  total_alumnos: number;
  total_profesores: number;
  total_grupos: number;
  niveles: NivelStats[];
}

interface DistribucionNivel {
  nombre_nivel: string;
  total_alumnos: number;
  total_grupos: number;
}

interface DashboardWidgets {
  welcome: boolean;
  kpi: boolean;
  miPlantel: boolean;
  planteles: boolean;
  chart: boolean;
  quickLinks: boolean;
}

const DEFAULT_WIDGETS: DashboardWidgets = {
  welcome: true,
  kpi: true,
  miPlantel: true,
  planteles: true,
  chart: true,
  quickLinks: true
};

const NIVEL_COLOR: Record<string, string> = {
  PRIMARIA: '#22c55e', SECUNDARIA: '#3b82f6', PREPARATORIA: '#a855f7',
};
const NIVEL_ICON: Record<string, string> = {
  PRIMARIA: 'pi-star', SECUNDARIA: 'pi-book', PREPARATORIA: 'pi-graduation-cap',
};

/**
 * Dashboard principal de ADES con KPIs institucionales y accesos rápidos.
 * Muestra estadísticas por plantel (alumnos, docentes, grupos, niveles) y
 * gráfica de distribución. Los widgets son configurables y persisten en
 * localStorage. Para AdminSistema (nivelAcceso 5) muestra todos los planteles;
 * para AdminPlantel (4) muestra solo su plantel vía `ContextService`.
 */
@Component({
  selector: 'app-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, RouterModule, FormsModule, CardModule, SkeletonModule,
    ButtonModule, DividerModule, DialogModule, InputTextModule, TooltipModule, TagModule, CheckboxModule,
    MessageModule, ApexChartComponent
  ],
  template: `
    <div class="dashboard">
      <!-- Barra de control de personalización -->
      <div class="dashboard-custom-header">
        <p-button icon="pi pi-cog" [rounded]="true" [text]="true" severity="secondary"
          pTooltip="Personalizar Dashboard" (onClick)="dlgPersonalizar = true" />
      </div>

      <!-- ── Bienvenida contextual ── -->
      @if (visibleWidgets().welcome) {
        <div class="welcome-bar">
          <div class="welcome-left">
            <h2>Dashboard</h2>
            <p class="subtitle">
              @if (ctx.plantel()) {
                <i class="pi pi-map-marker"></i> {{ ctx.plantel()!.nombre_plantel }}
              } @else {
                <i class="pi pi-building"></i> Instituto Nevadi — Vista global
              }
              @if (ctx.ciclo()) {
                <span class="ciclo-chip">Ciclo {{ ctx.ciclo()!.nombre_ciclo }}</span>
              }
            </p>
          </div>
          @if (ctx.usuario()) {
            <div class="user-greeting">
              <span class="greeting-name">{{ ctx.usuario()!.nombre_completo }}</span>
              <span class="greeting-rol">{{ ctx.rolLabel() }}</span>
            </div>
          }
        </div>
      } @else {
        <!-- Mini cabecera compacta si la bienvenida está desactivada -->
        <div class="mini-welcome-bar">
          <span class="mini-title">Dashboard</span>
        </div>
      }

      <!-- ── Error KPI Cards ── -->
      @if (visibleWidgets().kpi && errorResumen()) {
        <p-message severity="error" [text]="true"
          [innerHTML]="'Error al cargar estadísticas: ' + errorResumen()"></p-message>
        <p-button label="Reintentar" icon="pi pi-refresh" size="small" (onClick)="recargarResumen()" />
      }

      <!-- ── KPI Cards ── -->
      @if (visibleWidgets().kpi) {
        <div class="kpi-cards">
          @if ((resumen()?.total_alumnos ?? 0) >= minAlumnosKpi()) {
            <div class="kpi-card" routerLink="/alumnos">
              <div class="kpi-icon-wrap alumnos"><i class="pi pi-users"></i></div>
              <div class="kpi-body">
                @if (loading()) { <p-skeleton width="60px" height="2rem" /> }
                @else { <span class="kpi-value">{{ resumen()?.total_alumnos ?? 0 | number }}</span> }
                <span class="kpi-label">Alumnos inscritos</span>
              </div>
              <i class="pi pi-chevron-right kpi-arrow"></i>
            </div>
          }

          @if ((resumen()?.total_profesores ?? 0) >= minAlumnosKpi()) {
            <div class="kpi-card" routerLink="/profesores">
              <div class="kpi-icon-wrap profesores"><i class="pi pi-id-card"></i></div>
              <div class="kpi-body">
                @if (loading()) { <p-skeleton width="60px" height="2rem" /> }
                @else { <span class="kpi-value">{{ resumen()?.total_profesores ?? 0 | number }}</span> }
                <span class="kpi-label">Profesores activos</span>
              </div>
              <i class="pi pi-chevron-right kpi-arrow"></i>
            </div>
          }

          @if ((resumen()?.total_grupos_activos ?? 0) >= minAlumnosKpi()) {
            <div class="kpi-card" routerLink="/grupos">
              <div class="kpi-icon-wrap grupos"><i class="pi pi-book"></i></div>
              <div class="kpi-body">
                @if (loading()) { <p-skeleton width="60px" height="2rem" /> }
                @else { <span class="kpi-value">{{ resumen()?.total_grupos_activos ?? 0 | number }}</span> }
                <span class="kpi-label">Grupos activos</span>
              </div>
              <i class="pi pi-chevron-right kpi-arrow"></i>
            </div>
          }

          @if ((resumen()?.total_clases_hoy ?? 0) >= minAlumnosKpi()) {
            <div class="kpi-card kpi-highlight">
              <div class="kpi-icon-wrap clases"><i class="pi pi-calendar"></i></div>
              <div class="kpi-body">
                @if (loading()) { <p-skeleton width="60px" height="2rem" /> }
                @else { <span class="kpi-value">{{ resumen()?.total_clases_hoy ?? 0 | number }}</span> }
                <span class="kpi-label">Clases hoy</span>
              </div>
            </div>
          }
        </div>
      }

      <!-- ── Mi Plantel (para usuarios con plantel seleccionado que NO son admin global) ── -->
      @if (visibleWidgets().miPlantel && !ctx.esAdminGlobal() && ctx.plantel() && resumen()) {
        <p-divider styleClass="dash-divider" />
        <h3 class="section-title">Mi Plantel</h3>
        <div class="mi-plantel-card">
          <div class="card-title-row">
            <div class="avatar">{{ ctx.plantel()!.nombre_plantel[0] }}</div>
            <div>
              <h3 class="card-nombre">{{ ctx.plantel()!.nombre_plantel }}</h3>
              @if (ctx.plantel()!.clave_ct) {
                <code class="clave-ct">{{ ctx.plantel()!.clave_ct }}</code>
              }
            </div>
          </div>
          <div class="kpi-row">
            <div class="kpi">
              <span class="kv">{{ resumen()!.total_alumnos | number }}</span>
              <span class="kl">Alumnos</span>
            </div>
            <div class="sep"></div>
            <div class="kpi">
              <span class="kv">{{ resumen()!.total_profesores }}</span>
              <span class="kl">Profesores</span>
            </div>
            <div class="sep"></div>
            <div class="kpi">
              <span class="kv">{{ resumen()!.total_grupos_activos }}</span>
              <span class="kl">Grupos activos</span>
            </div>
            <div class="sep"></div>
            <div class="kpi">
              <span class="kv">{{ resumen()!.total_clases_hoy }}</span>
              <span class="kl">Clases hoy</span>
            </div>
          </div>
        </div>
      }

      <!-- ── Error Planteles ── -->
      @if (visibleWidgets().planteles && ctx.esAdminGlobal() && errorPlanteles()) {
        <p-message severity="error" [text]="true"
          [innerHTML]="'Error al cargar planteles: ' + errorPlanteles()"></p-message>
        <p-button label="Reintentar" icon="pi pi-refresh" size="small" (onClick)="recargarPlanteles()" />
      }

      <!-- ── Planteles del Instituto (Administradores Globales — siempre visible) ── -->
      @if (visibleWidgets().planteles && ctx.esAdminGlobal()) {
        <p-divider styleClass="dash-divider" />
        <div class="section-header-flex">
          <h3 class="section-title">Planteles del Instituto</h3>
          <div class="search-filter-wrap">
            <span class="p-input-icon-left">
              <i class="pi pi-search" style="left: 0.75rem;"></i>
              <input pInputText type="text" [(ngModel)]="filtroPlanteles" placeholder="Buscar plantel..." size="small" class="filtro-input" />
            </span>
          </div>
        </div>
        @if (loadingPlanteles()) {
          <div class="cards-grid">
            @for (i of [1, 2, 3]; track i) {
              <p-skeleton height="280px" />
            }
          </div>
        } @else {
          <div class="cards-grid">
            @for (p of filteredStats(); track p.id) {
              <div class="plantel-card" [class.activo]="ctx.plantel()?.id === p.id">
                <div class="card-top">
                  <div class="card-title-row">
                    <div class="avatar">{{ p.nombre_plantel[0] }}</div>
                    <div>
                      <h3 class="card-nombre">{{ p.nombre_plantel }}</h3>
                      @if (p.clave_ct) { <code class="clave-ct">{{ p.clave_ct }}</code> }
                    </div>
                  </div>
                  @if (ctx.plantel()?.id === p.id) {
                    <p-tag value="Contexto actual" severity="success" />
                  }
                </div>

                <div class="kpi-row">
                  <div class="kpi">
                    <span class="kv">{{ p.total_alumnos | number }}</span>
                    <span class="kl">Alumnos</span>
                  </div>
                  <div class="sep"></div>
                  <div class="kpi">
                    <span class="kv">{{ p.total_profesores }}</span>
                    <span class="kl">Profesores</span>
                  </div>
                  <div class="sep"></div>
                  <div class="kpi">
                    <span class="kv">{{ p.total_grupos }}</span>
                    <span class="kl">Grupos</span>
                  </div>
                </div>

                <div class="niveles">
                  @for (n of p.niveles; track n.nivel_educativo_id) {
                    <div class="nivel-row" [style.border-left-color]="color(n.nombre_nivel)">
                      <i class="pi" [class]="icon(n.nombre_nivel)" [style.color]="color(n.nombre_nivel)"></i>
                      <span class="nn">{{ n.nombre_nivel | titlecase }}</span>
                      <span class="nd">{{ n.grados }} grados</span>
                      <span class="nd">{{ n.grupos_activos }} grupos</span>
                    </div>
                  }
                </div>

                <div class="card-btns">
                  <p-button label="Seleccionar" icon="pi pi-check" severity="primary" size="small"
                    [text]="true" (onClick)="seleccionar(p)" />
                  @if (isAdmin()) {
                    <p-button icon="pi pi-pencil" severity="warn" size="small"
                      [text]="true" pTooltip="Editar plantel" (onClick)="abrirEditar(p)" />
                  }
                </div>
              </div>
            }
          </div>
        }
      }

      <!-- ── Gráfico: distribución por nivel ── -->
      @if (visibleWidgets().chart && distribucion().length > 0) {
        <p-divider styleClass="dash-divider" />
        <h3 class="section-title">Distribución por nivel educativo</h3>
        <apex-chart
          [barData]="distribucionChartData()"
          title="Distribución por Nivel"
          subtitle="Alumnos y grupos activos"
          height="260px"
        />
      } @else if (visibleWidgets().chart && loadingChart()) {
        <p-divider styleClass="dash-divider" />
        <p-skeleton height="120px" />
      }

      <!-- ── Accesos rápidos ── -->
      @if (visibleWidgets().quickLinks) {
        <p-divider styleClass="dash-divider" />
        <h3 class="section-title">Accesos rápidos</h3>
        <div class="quick-links">
          @for (link of quickLinks; track link.route) {
            <a [routerLink]="link.route" class="quick-link">
              <i [class]="'pi ' + link.icon"></i>
              <span>{{ link.label }}</span>
            </a>
          }
        </div>
      }
    </div>

    <!-- Diálogo personalizar dashboard -->
    <p-dialog [(visible)]="dlgPersonalizar" header="Personalizar Vista de Dashboard"
      [modal]="true" [draggable]="false" [style]="{width:'400px'}">
      <div class="custom-dialog-body" style="display: flex; flex-direction: column; gap: 1rem; padding: 0.5rem 0;">
        <p class="dialog-section-desc" style="font-size: 0.85rem; color: var(--text-secondary); margin: 0;">
          Selecciona las secciones que deseas visualizar:
        </p>
        <div class="widget-list" style="display: flex; flex-direction: column; gap: 0.75rem;">
          <div class="widget-item">
            <p-checkbox [binary]="true" label="Barra de Bienvenida"
              [ngModel]="visibleWidgets().welcome"
              (ngModelChange)="toggleWidget('welcome', $event)" />
          </div>
          <div class="widget-item">
            <p-checkbox [binary]="true" label="Indicadores Clave (KPIs)"
              [ngModel]="visibleWidgets().kpi"
              (ngModelChange)="toggleWidget('kpi', $event)" />
          </div>
          <div class="widget-item">
            <p-checkbox [binary]="true" label="Mi Plantel (Resumen Contexto)"
              [ngModel]="visibleWidgets().miPlantel"
              (ngModelChange)="toggleWidget('miPlantel', $event)" />
          </div>
          @if (ctx.esAdminGlobal()) {
            <div class="widget-item">
              <p-checkbox [binary]="true" label="Planteles del Instituto"
                [ngModel]="visibleWidgets().planteles"
                (ngModelChange)="toggleWidget('planteles', $event)" />
            </div>
          }
          <div class="widget-item">
            <p-checkbox [binary]="true" label="Gráfico de Distribución"
              [ngModel]="visibleWidgets().chart"
              (ngModelChange)="toggleWidget('chart', $event)" />
          </div>
          <div class="widget-item">
            <p-checkbox [binary]="true" label="Accesos Rápidos"
              [ngModel]="visibleWidgets().quickLinks"
              (ngModelChange)="toggleWidget('quickLinks', $event)" />
          </div>
        </div>

        <p-divider styleClass="dash-divider" />
        <p class="dialog-section-desc" style="font-size: 0.85rem; color: var(--text-secondary); margin: 0;">
          Filtros de visualización por cantidad de alumnos:
        </p>
        <div class="form-grid-custom" style="display: flex; flex-direction: column; gap: 0.35rem;">
          <label style="font-size: 0.8rem; font-weight: 600; color: var(--text-secondary);">Valor Mínimo Alumnos en KPIs:</label>
          <input pInputText type="number" [ngModel]="minAlumnosKpi()" (ngModelChange)="minAlumnosKpi.set($event)" min="0" class="w-full" style="padding: 0.5rem;" />
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Restablecer" severity="secondary" [text]="true" (onClick)="restablecerWidgets()" />
        <p-button label="Cerrar" icon="pi pi-times" (onClick)="dlgPersonalizar = false" />
      </ng-template>
    </p-dialog>

    <!-- Diálogo editar plantel -->
    <p-dialog [(visible)]="dlgPlantel" header="Editar plantel"
      [modal]="true" [draggable]="false" [style]="{width:'380px'}">
      @if (plantelEdit) {
        <div class="form-grid">
          <label>Nombre *</label>
          <input pInputText [(ngModel)]="plantelEdit.nombre_plantel" />
          <label>Clave CT</label>
          <input pInputText [(ngModel)]="plantelEdit.clave_ct" placeholder="CCT12345" />
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="dlgPlantel=false" />
          <p-button label="Guardar" icon="pi pi-save" [loading]="saving()" (onClick)="guardarPlantel()" />
        </ng-template>
      }
    </p-dialog>
  `,
  styles: [`
    .dashboard { padding: 0.5rem 0; position: relative; }

    /* Custom Header Control */
    .dashboard-custom-header {
      position: absolute;
      top: 0.5rem;
      right: 0.5rem;
      z-index: 10;
    }

    /* Bienvenida */
    .welcome-bar { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1.5rem; padding-right: 3rem; }
    .welcome-bar h2 { margin: 0 0 0.2rem; color: var(--text-primary); }
    .subtitle { display: flex; align-items: center; gap: 0.4rem; color: var(--text-secondary); font-size: 0.85rem; margin: 0; }
    .subtitle i { font-size: 0.8rem; }
    .ciclo-chip { background: var(--nevadi-red-lighter); color: var(--nevadi-red-dark); font-size: 0.72rem; font-weight: 600; padding: 0.1rem 0.5rem; border-radius: 20px; margin-left: 0.25rem; }
    .user-greeting { display: flex; flex-direction: column; align-items: flex-end; gap: 0.1rem; }
    .greeting-name { font-size: 0.85rem; font-weight: 600; color: var(--text-primary); }
    .greeting-rol { font-size: 0.72rem; color: var(--text-secondary); }

    /* Mini cabecera compacta */
    .mini-welcome-bar {
      margin-bottom: 1.5rem;
      border-bottom: 1px solid var(--surface-border);
      padding-bottom: 0.5rem;
      padding-right: 3rem;
    }
    .mini-title {
      font-size: 1.25rem;
      font-weight: 700;
      color: var(--text-primary);
    }

    /* KPI cards — clicables tipo APEX */
    .kpi-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 0.75rem; }
    .kpi-card {
      display: flex; align-items: center; gap: 1rem;
      background: var(--surface-card); border: 1px solid var(--surface-border);
      border-radius: 10px; padding: 1rem 1.25rem;
      cursor: pointer; text-decoration: none;
      transition: box-shadow .15s, border-color .15s;
    }
    .kpi-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,.08); border-color: var(--nevadi-red-light); }
    .kpi-card.kpi-highlight { border-left: 3px solid var(--color-warning); }
    .kpi-icon-wrap {
      width: 44px; height: 44px; border-radius: 10px; flex-shrink: 0;
      display: flex; align-items: center; justify-content: center;
      font-size: 1.25rem;
    }
    .kpi-icon-wrap.alumnos   { background: #EFF6FF; color: var(--nevadi-slate-mid); }
    .kpi-icon-wrap.profesores{ background: #F0FDF4; color: var(--nevadi-sage-mid); }
    .kpi-icon-wrap.grupos    { background: #F5F3FF; color: #7c3aed; }
    .kpi-icon-wrap.clases    { background: #FFFBEB; color: var(--color-warning); }
    .kpi-body { flex: 1; }
    .kpi-value { display: block; font-size: 1.9rem; font-weight: 800; line-height: 1.1; color: var(--text-primary); }
    .kpi-label { font-size: 0.75rem; color: var(--text-secondary); font-weight: 500; }
    .kpi-arrow { color: var(--text-muted); font-size: 0.8rem; }

    /* Mi Plantel card (usuarios no-admin con plantel activo) */
    .mi-plantel-card {
      background: var(--surface-0); border: 1px solid var(--primary-200); border-radius: 12px;
      padding: 1.25rem; display: flex; flex-direction: column; gap: .9rem;
      box-shadow: 0 0 0 2px var(--primary-50); max-width: 480px;
    }

    /* Cards Grid (Planteles) */
    .cards-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 1.25rem; margin-top: 0.5rem; }
    .plantel-card {
      background: var(--surface-0); border: 1px solid var(--surface-200); border-radius: 12px;
      padding: 1.25rem; display: flex; flex-direction: column; gap: .9rem;
      transition: box-shadow .15s, border-color .15s;
    }
    .plantel-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,.08); border-color: var(--primary-200); }
    .plantel-card.activo { border-color: var(--primary-color); box-shadow: 0 0 0 2px var(--primary-100); }

    .card-top { display: flex; justify-content: space-between; align-items: flex-start; }
    .card-title-row { display: flex; gap: .7rem; align-items: center; }
    .avatar {
      width: 42px; height: 42px; border-radius: 10px; background: var(--primary-color); color: #fff;
      display: flex; align-items: center; justify-content: center;
      font-weight: 800; font-size: 1.1rem; flex-shrink: 0;
    }
    .card-nombre { font-size: 1rem; font-weight: 700; margin: 0; }
    .clave-ct { font-size: .72rem; color: var(--text-color-secondary); background: var(--surface-100); padding: .1rem .4rem; border-radius: 3px; }

    .kpi-row { display: flex; align-items: center; justify-content: space-around; background: var(--surface-50); border-radius: 8px; padding: .7rem; }
    .kpi { display: flex; flex-direction: column; align-items: center; }
    .kv { font-size: 1.35rem; font-weight: 700; color: var(--primary-color); line-height: 1.1; }
    .kl { font-size: .7rem; color: var(--text-color-secondary); }
    .sep { width: 1px; height: 28px; background: var(--surface-200); }

    .niveles { display: flex; flex-direction: column; gap: .35rem; }
    .nivel-row {
      display: flex; align-items: center; gap: .55rem; padding: .4rem .65rem;
      border-radius: 6px; background: var(--surface-50); border-left: 3px solid #ccc;
    }
    .nn { font-size: .83rem; font-weight: 600; flex: 1; text-transform: capitalize; }
    .nd { font-size: .72rem; color: var(--text-color-secondary); background: var(--surface-100); padding: .1rem .35rem; border-radius: 10px; }
    .nivel-row i { font-size: .82rem; }

    .card-btns { display: flex; gap: .4rem; padding-top: .25rem; border-top: 1px solid var(--surface-100); flex-wrap: wrap; }
    .form-grid { display: grid; grid-template-columns: 100px 1fr; gap: .75rem 1rem; align-items: center; padding: .5rem 0; }
    .form-grid label { font-size: .85rem; color: var(--text-color-secondary); font-weight: 600; }

    /* Section Flex Header (for search filtering) */
    .section-header-flex {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 0.75rem;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
    .filtro-input {
      font-size: 0.82rem;
      padding: 0.35rem 0.5rem 0.35rem 2rem !important;
      width: 200px;
    }

    /* Accesos rápidos */
    .dash-divider { margin: 1.25rem 0 1rem !important; }
    .section-title { margin: 0; font-size: 0.85rem; font-weight: 700; text-transform: uppercase; letter-spacing: .05em; color: var(--text-secondary); }
    .quick-links { display: flex; flex-wrap: wrap; gap: 0.5rem; }
    .quick-link {
      display: flex; align-items: center; gap: 0.4rem;
      background: var(--surface-50); border: 1px solid var(--surface-border);
      border-radius: 8px; padding: 0.45rem 0.85rem;
      color: var(--text-primary); text-decoration: none; font-size: 0.83rem;
      transition: background .12s, border-color .12s;
    }
    .quick-link:hover { background: var(--nevadi-red-lighter); border-color: var(--nevadi-red-light); color: var(--nevadi-red-dark); }
    .quick-link i { font-size: 0.82rem; color: var(--nevadi-red); }
  `],
})
export class DashboardComponent implements OnDestroy {
  private destroy$ = new Subject<void>();
  private readonly api = inject(ApiService);
  readonly ctx = inject(ContextService);
  private readonly notify = inject(ApexNotificationService);

  resumen        = signal<ResumenPlantel | null>(null);
  distribucion   = signal<DistribucionNivel[]>([]);
  loading        = signal(true);
  loadingChart   = signal(true);
  errorResumen   = signal<string | null>(null);
  errorDistribucion = signal<string | null>(null);

  // Planteles properties
  stats            = signal<PlantelStats[]>([]);
  loadingPlanteles = signal(true);
  errorPlanteles   = signal<string | null>(null);
  dlgPlantel       = false;
  plantelEdit: { id: string; nombre_plantel: string; clave_ct: string | null } | null = null;
  saving           = signal(false);

  // Personalización del dashboard
  dlgPersonalizar = false;
  visibleWidgets = signal<DashboardWidgets>({ ...DEFAULT_WIDGETS });
  minAlumnosKpi = signal<number>(0);
  filtroPlanteles = '';

  readonly quickLinks = [
    { route: '/calificaciones',  icon: 'pi-star',          label: 'Calificaciones' },
    { route: '/asistencias',     icon: 'pi-check-square',  label: 'Asistencias' },
    { route: '/tareas',          icon: 'pi-file-edit',     label: 'Tareas' },
    { route: '/comunicados',     icon: 'pi-envelope',      label: 'Comunicados' },
    { route: '/gradebook',       icon: 'pi-book',          label: 'Gradebook' },
    { route: '/grade-analytics', icon: 'pi-chart-bar',     label: 'Grade Analytics' },
    { route: '/portal',          icon: 'pi-id-card',       label: 'Portal Alumno' },
    { route: '/reportes',        icon: 'pi-file-pdf',      label: 'Reportes' },
  ];

  // Planteles filtrados computados dinámicamente
  readonly filteredStats = computed(() => {
    const query = this.filtroPlanteles.toLowerCase().trim();
    const minVal = this.minAlumnosKpi();
    return this.stats().filter(s => {
      const matchSearch = !query ||
        s.nombre_plantel.toLowerCase().includes(query) ||
        (s.clave_ct && s.clave_ct.toLowerCase().includes(query));
      const matchMinAlumnos = s.total_alumnos >= minVal;
      return matchSearch && matchMinAlumnos;
    });
  });

  constructor() {
    // Cargar personalización guardada
    try {
      const saved = localStorage.getItem('ades_dashboard_widgets');
      if (saved) {
        this.visibleWidgets.set({ ...DEFAULT_WIDGETS, ...JSON.parse(saved) });
      }
      const savedMinKpi = localStorage.getItem('ades_dashboard_min_kpi');
      if (savedMinKpi) {
        this.minAlumnosKpi.set(parseInt(savedMinKpi, 10) || 0);
      }
    } catch (e) {
      console.warn('Error al cargar preferencias de dashboard desde localStorage:', e);
    }

    effect(() => {
      const plantelId = this.ctx.plantel()?.id;
      const params = plantelId ? { plantel_id: plantelId } : {};

      this.loading.set(true);
      this.errorResumen.set(null);
      this.api.get<ResumenPlantel>('/stats/resumen', params).pipe(takeUntil(this.destroy$)).subscribe({
        next: (r) => {
          this.resumen.set(r);
          this.loading.set(false);
          this.errorResumen.set(null);
        },
        error: (err) => {
          this.loading.set(false);
          const errorMsg = err?.error?.detail ?? 'No se pudieron cargar las estadísticas';
          this.errorResumen.set(errorMsg);
          this.notify.error('Error', errorMsg);
        },
      });

      this.loadingChart.set(true);
      this.errorDistribucion.set(null);
      this.api.get<DistribucionNivel[]>('/stats/distribucion', params).pipe(takeUntil(this.destroy$)).subscribe({
        next: (d) => {
          this.distribucion.set(d);
          this.loadingChart.set(false);
          this.errorDistribucion.set(null);
        },
        error: (err) => {
          this.distribucion.set([]);
          this.loadingChart.set(false);
          const errorMsg = err?.error?.detail ?? 'No se pudo cargar la distribución';
          this.errorDistribucion.set(errorMsg);
        },
      });
    });

    // Load planteles stats if user is global admin
    if (this.ctx.esAdminGlobal()) {
      this.loadingPlanteles.set(true);
      this.errorPlanteles.set(null);
      this.api.get<PlantelStats[]>('/planteles/stats').pipe(takeUntil(this.destroy$)).subscribe({
        next: s => {
          this.stats.set(s);
          this.loadingPlanteles.set(false);
          this.errorPlanteles.set(null);
        },
        error: (err) => {
          this.loadingPlanteles.set(false);
          const errorMsg = err?.error?.detail ?? 'No se pudieron cargar los planteles';
          this.errorPlanteles.set(errorMsg);
          this.notify.error('Error', errorMsg);
        },
      });
    }
  }

  readonly maxAlumnos = computed(() => Math.max(1, ...this.distribucion().map(d => d.total_alumnos)));
  readonly maxGrupos  = computed(() => Math.max(1, ...this.distribucion().map(d => d.total_grupos)));

  readonly distribucionChartData = computed(() => ({
    labels: this.distribucion().map(d => d.nombre_nivel),
    datasets: [
      {
        label: 'Alumnos',
        data: this.distribucion().map(d => d.total_alumnos),
        color: '#3b82f6',
      },
      {
        label: 'Grupos',
        data: this.distribucion().map(d => d.total_grupos),
        color: '#10b981',
      },
    ],
  }));

  barPct(value: number, max: number): number {
    return max > 0 ? Math.round((value / max) * 100) : 0;
  }

  isAdmin(): boolean { return this.ctx.nivelAcceso() <= 1; }

  abrirEditar(p: PlantelStats): void {
    this.plantelEdit = { id: p.id, nombre_plantel: p.nombre_plantel, clave_ct: p.clave_ct };
    this.dlgPlantel = true;
  }

  guardarPlantel(): void {
    if (!this.plantelEdit || !this.plantelEdit.nombre_plantel.trim()) return;
    this.saving.set(true);
    this.api.patch(`/admin/planteles/${this.plantelEdit.id}`, {
      nombre_plantel: this.plantelEdit.nombre_plantel,
      clave_ct: this.plantelEdit.clave_ct,
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.stats.update(list => list.map(s =>
          s.id === this.plantelEdit!.id
            ? { ...s, nombre_plantel: this.plantelEdit!.nombre_plantel, clave_ct: this.plantelEdit!.clave_ct }
            : s
        ));
        this.notify.success('Guardado', 'Plantel actualizado');
        this.dlgPlantel = false;
        this.saving.set(false);
      },
      error: e => {
        this.notify.error('Error', e.error?.detail ?? 'Error al guardar');
        this.saving.set(false);
      },
    });
  }

  seleccionar(p: PlantelStats): void {
    this.ctx.setPlantel({
      id: p.id, nombre_plantel: p.nombre_plantel,
      clave_ct: p.clave_ct ?? undefined,
      escuela_id: '', is_active: true,
    } as Plantel);
  }

  toggleWidget(key: keyof DashboardWidgets, val: boolean): void {
    this.visibleWidgets.update(w => {
      const updated = { ...w, [key]: val };
      try {
        localStorage.setItem('ades_dashboard_widgets', JSON.stringify(updated));
      } catch (e) {
        console.error('Error guardando widget a localStorage:', e);
      }
      return updated;
    });
  }

  restablecerWidgets(): void {
    this.visibleWidgets.set({ ...DEFAULT_WIDGETS });
    this.minAlumnosKpi.set(0);
    try {
      localStorage.removeItem('ades_dashboard_widgets');
      localStorage.removeItem('ades_dashboard_min_kpi');
    } catch (e) {}
    this.notify.info('Restablecido', 'Preferencias de visualización restablecidas');
  }

  recargarResumen(): void {
    const plantelId = this.ctx.plantel()?.id;
    const params = plantelId ? { plantel_id: plantelId } : {};
    this.loading.set(true);
    this.errorResumen.set(null);
    this.api.get<ResumenPlantel>('/stats/resumen', params).pipe(takeUntil(this.destroy$)).subscribe({
      next: (r) => {
        this.resumen.set(r);
        this.loading.set(false);
        this.notify.success('Éxito', 'Datos recargados');
      },
      error: (err) => {
        this.loading.set(false);
        const errorMsg = err?.error?.detail ?? 'Error al recargar';
        this.errorResumen.set(errorMsg);
        this.notify.error('Error', errorMsg);
      },
    });
  }

  recargarPlanteles(): void {
    this.loadingPlanteles.set(true);
    this.errorPlanteles.set(null);
    this.api.get<PlantelStats[]>('/planteles/stats').pipe(takeUntil(this.destroy$)).subscribe({
      next: s => {
        this.stats.set(s);
        this.loadingPlanteles.set(false);
        this.notify.success('Éxito', 'Planteles recargados');
      },
      error: (err) => {
        this.loadingPlanteles.set(false);
        const errorMsg = err?.error?.detail ?? 'Error al recargar';
        this.errorPlanteles.set(errorMsg);
        this.notify.error('Error', errorMsg);
      },
    });
  }

  color(n: string): string { return NIVEL_COLOR[n] ?? '#94a3b8'; }
  icon(n: string):  string { return NIVEL_ICON[n]  ?? 'pi-school'; }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
