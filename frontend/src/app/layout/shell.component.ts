/**
 * ShellComponent — Layout principal con topbar tipo APEX.
 * Contiene: logo, selector de plantel/ciclo (Application Items), nav lateral, router-outlet.
 * Colores institucionales Instituto Nevadi — primario #D02030.
 */
import { Component, inject, OnInit, OnDestroy, signal, computed, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, RouterOutlet } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ToolbarModule } from 'primeng/toolbar';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { MenuModule } from 'primeng/menu';
import { PopoverModule, Popover } from 'primeng/popover';
import { BadgeModule } from 'primeng/badge';
import { TagModule } from 'primeng/tag';

import { ContextService } from '../core/services/context.service';
import { AuthService } from '../core/services/auth.service';
import { ApiService } from '../core/services/api.service';
import { PushNotificationService } from '../core/services/push-notification.service';
import type { Plantel, CicloEscolar, NivelEducativo } from '../core/models';
import { ApexComponentsModule } from '../shared/apex-components.module';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';

// Sentinelas para "Todo el Instituto" / "Todos los niveles"
const TODO_INSTITUTO: Plantel = { id: '', nombre_plantel: '— Todo el Instituto —', escuela_id: '', is_active: true };
const TODOS_NIVELES: NivelEducativo = { id: '', nombre_nivel: 'TODOS' as any, autoridad_educativa: '', tipo_ciclo: '', num_periodos_eval: 0 };

const ROUTE_TITLES: Record<string, string> = {
  'dashboard': 'Dashboard',
  'alumnos': 'Alumnos',
  'grupos': 'Grupos',
  'gradebook': 'Calificaciones',
  'asistencias': 'Asistencias',
  'horarios': 'Horarios',
  'tareas': 'Tareas',
  'entregas': 'Entregas',
  'planeacion': 'Planeación',
  'planes-estudio': 'Planes de Estudio',
  'materias': 'Materias',
  'planteles': 'Planteles',
  'usuarios': 'Usuarios',
  'admin': 'Administración',
  'reportes': 'Reportes',
  'stats': 'Estadísticas',
  'encuestas': 'Encuestas',
  'comunicados': 'Comunicados',
  'avisos': 'Avisos',
  'badges': 'Reconocimientos',
  'foros': 'Foros',
  'learning-paths': 'Rutas de Aprendizaje',
  'mi-progreso': 'Mi Progreso',
  'padres': 'Portal de Padres',
  'medico': 'Expediente Médico',
  'condiciones-cronicas': 'Condiciones Crónicas',
  'justificaciones': 'Justificaciones',
  'movilidad': 'Movilidad',
  'disponibilidad': 'Disponibilidad',
  'licencias': 'Licencias',
  'capacitaciones': 'Capacitaciones',
  'ponderacion-config': 'Ponderación',
  'cierre-periodo': 'Cierre de Período',
  'aulas': 'Aulas',
  'certificados': 'Certificados',
  'admision': 'Admisión',
  'ia': 'Asistente IA',
  'ayuda': 'Ayuda',
  'portal': 'Portal Familias',
};

interface NavItem  { route: string; icon: string; label: string; maxNivel?: number; minNivel?: number; }
interface NavGroup { section: string; maxNivel?: number; minNivel?: number; items: NavItem[]; }

interface Notif { id: string; titulo: string; cuerpo: string; tipo: string; leido: boolean; }

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    CommonModule, RouterModule, RouterOutlet, FormsModule,
    ToolbarModule, ButtonModule, SelectModule, MenuModule,
    PopoverModule, BadgeModule, TagModule, ApexComponentsModule
  ],
  template: `
    <apex-toast-container position="top-right" />

    <!-- ── Topbar institucional ── -->
    <p-toolbar styleClass="ades-topbar">
      <ng-template pTemplate="start">
        <div class="topbar-brand">
          <div class="brand-logo">N</div>
          <div class="brand-text">
            <span class="brand-title">ADES</span>
            <span class="brand-subtitle">Instituto Nevadi</span>
          </div>
        </div>

        <div class="topbar-divider"></div>

        <!-- ── Plantel ── -->
        @if (ctx.esAdminGlobal()) {
          <p-select
            [options]="planteles()"
            [(ngModel)]="selectedPlantel"
            optionLabel="nombre_plantel"
            placeholder="Plantel..."
            styleClass="ctx-selector"
            (onChange)="onPlantelChange($event.value)"
          />
        } @else {
          <div class="ctx-label"><i class="pi pi-map-marker"></i> {{ ctx.usuario()?.nombre_plantel ?? selectedPlantel?.nombre_plantel }}</div>
        }

        <span class="ctx-sep">/</span>

        <!-- ── Nivel educativo / Escuela ── -->
        @if (ctx.tieneScopeNivel()) {
          <div class="ctx-label ctx-nivel">{{ ctx.usuario()?.nombre_nivel }}</div>
        } @else {
          <p-select
            [options]="niveles()"
            [(ngModel)]="selectedNivel"
            optionLabel="nombre_nivel"
            placeholder="Nivel..."
            styleClass="ctx-selector ctx-selector-sm"
            (onChange)="onNivelChange($event.value)"
          />
        }

        <span class="ctx-sep">/</span>

        <!-- ── Ciclo Escolar ── -->
        <p-select
          [options]="ciclos()"
          [(ngModel)]="selectedCiclo"
          optionLabel="_label"
          placeholder="Ciclo..."
          styleClass="ctx-selector ctx-selector-sm"
          (onChange)="onCicloChange($event.value)"
        />
      </ng-template>

      <ng-template pTemplate="end">
        <!-- Campanita de notificaciones -->
        <div class="notif-bell" (click)="toggleNotifPanel($event)">
          <i class="pi pi-bell topbar-icon"></i>
          @if (notifCount() > 0) {
            <span class="notif-badge">{{ notifCount() > 99 ? '99+' : notifCount() }}</span>
          }
        </div>

        <!-- Avatar usuario -->
        <div class="user-avatar-btn" (click)="toggleUserMenu($event)" pTooltip="Cuenta" tooltipPosition="bottom">
          <div class="user-avatar-circle">{{ userInitial() }}</div>
          <div class="user-info">
            <span class="user-name">{{ ctx.usuario()?.nombre_completo }}</span>
            <span class="user-rol">{{ ctx.rolLabel() }}</span>
          </div>
          <i class="pi pi-chevron-down" style="font-size:.6rem;color:rgba(255,255,255,.55);margin-left:.2rem"></i>
        </div>
      </ng-template>
    </p-toolbar>

    <!-- ── Panel notificaciones ── -->
    <p-popover #notifPanel styleClass="notif-panel">
      <div class="notif-panel-header">
        <span>Notificaciones</span>
        @if (notifCount() > 0) {
          <button class="notif-leer-todas" (click)="leerTodas()">Marcar todas leídas</button>
        }
      </div>
      @if (notificaciones().length === 0) {
        <div class="notif-empty">Sin notificaciones pendientes</div>
      }
      @for (n of notificaciones(); track n.id) {
        <div class="notif-item-wrapper" (click)="marcarLeida(n)" [style.opacity]="n.leido ? '0.6' : '1'">
          <apex-alert
            [severity]="n.tipo === 'ERROR' ? 'error' : n.tipo === 'WARNING' ? 'warning' : 'info'"
            [message]="n.cuerpo"
            [icon]="n.tipo === 'ERROR' ? 'pi pi-times-circle' : n.tipo === 'WARNING' ? 'pi pi-exclamation-triangle' : 'pi pi-info-circle'"
            [closable]="false">
          </apex-alert>
        </div>
      }
      <div class="notif-panel-footer">
        <a routerLink="/comunicados" (click)="notifPanel.hide()">Ver comunicados →</a>
      </div>
    </p-popover>

    <!-- ── Menú de usuario ── -->
    <p-popover #userMenu styleClass="user-menu-panel">
      <div class="umenu-header">
        <div class="umenu-avatar">{{ userInitial() }}</div>
        <div>
          <div class="umenu-name">{{ ctx.usuario()?.nombre_completo }}</div>
          <div class="umenu-user">{{ ctx.usuario()?.nombre_usuario }}</div>
          <div class="umenu-rol">{{ ctx.rolLabel() }}</div>
        </div>
      </div>
      <div class="umenu-divider"></div>
      <button class="umenu-item" (click)="auth.logout(); userMenu.hide()">
        <i class="pi pi-sign-out"></i> Cerrar sesión
      </button>
    </p-popover>

    <!-- ── Layout principal ── -->
    <div class="app-layout">
      <!-- Nav lateral — dinámico por rol -->
      <nav class="sidenav">
        @for (group of navGroupsVisible(); track group.section) {
          <div class="sidenav-section">{{ group.section }}</div>
          <ul>
            @for (item of group.items; track item.route) {
              <li>
                <a [routerLink]="item.route" routerLinkActive="active" [routerLinkActiveOptions]="{exact: item.route === '/dashboard'}">
                  <i [class]="'pi ' + item.icon"></i>
                  <span>{{ item.label }}</span>
                </a>
              </li>
            }
          </ul>
        }
      </nav>

      <!-- Contenido principal -->
      <main class="main-content">
        @if (breadcrumbItems.length > 0) {
          <div class="breadcrumb-container">
            <apex-breadcrumb
              [items]="breadcrumbItems"
              [routeTitles]="routeTitles"
              [home]="{ label: 'Home', routerLink: '/' }">
            </apex-breadcrumb>
          </div>
        }
        <router-outlet />
      </main>
    </div>
  `,
  styles: [`
    :host { display: block; height: 100vh; overflow: hidden; }

    /* ── Topbar ──────────────────────────────────────────────────────────── */
    :host ::ng-deep .ades-topbar.p-toolbar {
      background: var(--topbar-bg) !important;
      border-bottom: 2px solid var(--topbar-border) !important;
      border-radius: 0 !important;
      padding: 0.35rem 1.25rem !important;
      gap: 0;
    }
    :host ::ng-deep .ades-topbar .p-toolbar-start { gap: 0.5rem; align-items: center; }
    :host ::ng-deep .ades-topbar .p-toolbar-end   { gap: 0.25rem; align-items: center; }

    .topbar-brand {
      display: flex; align-items: center; gap: 0.6rem; margin-right: 0.25rem;
    }
    .brand-logo {
      width: 34px; height: 34px; border-radius: 8px;
      background: rgba(255,255,255,.18);
      border: 1px solid rgba(255,255,255,.3);
      display: flex; align-items: center; justify-content: center;
      font-weight: 800; font-size: 1rem; color: #fff;
    }
    .brand-title   { color: #fff; font-weight: 700; font-size: 1rem; line-height: 1.1; display: block; }
    .brand-subtitle { color: var(--topbar-text-muted); font-size: 0.7rem; display: block; }

    .topbar-divider { width: 1px; height: 28px; background: rgba(255,255,255,.2); margin: 0 0.75rem; }

    /* User avatar menu */
    .user-avatar-btn {
      display: flex; align-items: center; gap: .5rem; cursor: pointer;
      padding: .25rem .5rem; border-radius: 8px;
      transition: background .15s;
    }
    .user-avatar-btn:hover { background: rgba(255,255,255,.12); }
    .user-avatar-circle {
      width: 30px; height: 30px; border-radius: 50%;
      background: rgba(255,255,255,.22); border: 1.5px solid rgba(255,255,255,.4);
      display: flex; align-items: center; justify-content: center;
      font-weight: 700; font-size: .85rem; color: #fff; flex-shrink: 0;
    }
    .user-info { display: flex; flex-direction: column; align-items: flex-end; }
    .user-name { color: rgba(255,255,255,.9); font-size: 0.82rem; line-height: 1.2; }
    .user-rol  { color: var(--topbar-text-muted); font-size: 0.7rem; }

    /* User menu popover */
    :host ::ng-deep p-popover.user-menu-panel .p-popover { min-width: 220px; padding: 0; }
    .umenu-header { display: flex; align-items: center; gap: .75rem; padding: 1rem; }
    .umenu-avatar {
      width: 40px; height: 40px; border-radius: 50%;
      background: var(--primary-color); color: #fff;
      display: flex; align-items: center; justify-content: center;
      font-weight: 700; font-size: 1rem; flex-shrink: 0;
    }
    .umenu-name { font-weight: 600; font-size: .88rem; }
    .umenu-user { font-size: .75rem; color: var(--text-color-secondary); }
    .umenu-rol  { font-size: .72rem; color: var(--text-color-secondary); margin-top: .1rem; }
    .umenu-divider { height: 1px; background: var(--surface-200); margin: 0; }
    .umenu-item {
      display: flex; align-items: center; gap: .6rem;
      width: 100%; background: none; border: none; cursor: pointer;
      padding: .75rem 1rem; font-size: .87rem; color: var(--text-color);
      transition: background .12s; text-align: left;
    }
    .umenu-item:hover { background: var(--surface-50); }
    .umenu-item i { color: var(--primary-color); }

    /* Badge de escuela para usuarios con scope fijo */
    .scope-badge {
      display: flex; align-items: center; gap: 0.4rem;
      background: rgba(255,255,255,.12); border: 1px solid rgba(255,255,255,.2);
      border-radius: 6px; padding: 0.3rem 0.7rem;
      color: #fff; font-size: 0.82rem; font-weight: 500;
    }
    .scope-badge i { font-size: 0.75rem; opacity: 0.8; }
    .scope-sep  { opacity: 0.5; }
    .scope-nivel { opacity: 0.85; font-weight: 600; }

    /* Campanita */
    .notif-bell {
      position: relative; cursor: pointer; padding: .3rem .5rem;
      display: flex; align-items: center; margin-right: .2rem;
    }
    .topbar-icon { color: rgba(255,255,255,.8); font-size: 1rem; }
    .notif-bell:hover .topbar-icon { color: #fff; }
    .notif-badge {
      position: absolute; top: 0; right: 0;
      background: #ff4d4f; color: #fff;
      border-radius: 8px; font-size: 0.62rem; font-weight: 700;
      min-width: 14px; height: 14px; padding: 0 3px;
      display: flex; align-items: center; justify-content: center;
      line-height: 1;
    }

    /* Panel notificaciones */
    :host ::ng-deep p-popover.notif-panel .p-popover { width: 340px; padding: 0; }
    .notif-panel-header {
      display: flex; justify-content: space-between; align-items: center;
      padding: .75rem 1rem; border-bottom: 1px solid var(--surface-200);
      font-weight: 600; font-size: .9rem;
    }
    .notif-leer-todas {
      background: none; border: none; cursor: pointer;
      color: var(--primary-color); font-size: .78rem; padding: 0;
    }
    .notif-empty {
      padding: 1.5rem 1rem; text-align: center;
      color: var(--text-color-secondary); font-size: .85rem;
    }
    .notif-item-wrapper {
      padding: .65rem 1rem; cursor: pointer;
      border-bottom: 1px solid var(--surface-100);
      transition: opacity .12s;
    }
    .notif-item-wrapper:hover { opacity: 0.8 !important; }
    .notif-panel-footer {
      padding: .5rem 1rem; text-align: center; border-top: 1px solid var(--surface-200);
    }
    .notif-panel-footer a { font-size: .8rem; color: var(--primary-color); text-decoration: none; }
    .notif-panel-footer a:hover { text-decoration: underline; }

    /* Separador / entre contextos */
    .ctx-sep { color: rgba(255,255,255,.4); font-size: 1rem; margin: 0 0.1rem; user-select: none; }

    /* Badge fijo de plantel/nivel (usuarios con scope) */
    .ctx-label {
      color: rgba(255,255,255,.9); font-size: 0.82rem; font-weight: 500;
      padding: 0.3rem 0.5rem; white-space: nowrap;
    }
    .ctx-label i { font-size: 0.75rem; opacity: 0.7; margin-right: 0.2rem; }
    .ctx-nivel { font-weight: 700; letter-spacing: .02em; text-transform: uppercase; font-size: 0.78rem; }

    /* p-select en topbar */
    :host ::ng-deep .ctx-selector { min-width: 160px; }
    :host ::ng-deep .ctx-selector-sm { min-width: 130px; }

    /* botón de topbar */
    :host ::ng-deep .topbar-btn { color: rgba(255,255,255,.8) !important; }
    :host ::ng-deep .topbar-btn:hover { color: #fff !important; }

    /* ── Layout ──────────────────────────────────────────────────────────── */
    .app-layout { display: flex; height: calc(100vh - 50px); overflow: hidden; }

    /* ── Sidebar ─────────────────────────────────────────────────────────── */
    .sidenav {
      width: 220px; min-width: 220px;
      background: var(--sidebar-bg);
      overflow-y: auto; padding: 1rem 0 0;
    }
    .sidenav-section {
      padding: 0 1rem 0.4rem;
      font-size: 0.68rem; font-weight: 700; letter-spacing: .08em;
      text-transform: uppercase;
      color: var(--sidebar-text-muted);
    }
    .sidenav ul { list-style: none; margin: 0; padding: 0 0.5rem; }
    .sidenav li a {
      display: flex; align-items: center; gap: 0.65rem;
      padding: 0.6rem 0.85rem;
      border-radius: 6px;
      color: var(--sidebar-text);
      text-decoration: none;
      font-size: 0.88rem;
      transition: background 0.14s, color 0.14s;
      margin-bottom: 1px;
    }
    .sidenav li a i { font-size: 0.9rem; opacity: 0.8; }
    .sidenav li a:hover { background: var(--sidebar-hover); color: #fff; }
    .sidenav li a:hover i { opacity: 1; }
    .sidenav li a.active {
      background: var(--sidebar-active);
      color: var(--sidebar-active-text);
      font-weight: 600;
    }
    .sidenav li a.active i { opacity: 1; }

    /* ── Main content ────────────────────────────────────────────────────── */
    .main-content { flex: 1; overflow-y: auto; padding: 1.5rem; background: var(--surface-ground); }
  `],
})
export class ShellComponent implements OnInit, OnDestroy {
  readonly ctx  = inject(ContextService);
  readonly auth = inject(AuthService);
  private readonly api  = inject(ApiService);
  private readonly push = inject(PushNotificationService);
  private readonly router = inject(Router);

  breadcrumbItems: any[] = [];
  readonly routeTitles = ROUTE_TITLES;

  planteles = signal<Plantel[]>([]);
  niveles   = signal<NivelEducativo[]>([]);
  ciclos    = signal<CicloEscolar[]>([]);

  private readonly _selectedPlantel = signal<Plantel | null>(this.ctx.plantel());
  private readonly _selectedNivel   = signal<NivelEducativo | null>(this.ctx.nivel());
  selectedCiclo: CicloEscolar | null = this.ctx.ciclo();

  get selectedPlantel(): Plantel | null { return this._selectedPlantel(); }
  set selectedPlantel(v: Plantel | null)  { this._selectedPlantel.set(v); }
  get selectedNivel(): NivelEducativo | null { return this._selectedNivel(); }
  set selectedNivel(v: NivelEducativo | null) { this._selectedNivel.set(v); }

  readonly plantelLabel = computed(() => {
    const p = this._selectedPlantel();
    return (!p || p.id === '') ? 'Todo el Instituto' : p.nombre_plantel;
  });
  readonly nivelLabel = computed(() => {
    const n = this._selectedNivel();
    return (!n || n.id === '') ? 'Todos los niveles' : n.nombre_nivel;
  });

  private notifInterval?: ReturnType<typeof setInterval>;

  notifCount    = signal(0);
  notificaciones = signal<Notif[]>([]);
  @ViewChild('notifPanel') notifPanelRef!: Popover;
  @ViewChild('userMenu')   userMenuRef!: Popover;

  readonly userInitial = computed(() =>
    this.ctx.usuario()?.nombre_completo?.[0]?.toUpperCase() ?? 'U'
  );

  private readonly _allNavGroups: NavGroup[] = [
    { section: 'Principal', items: [
      { route: '/dashboard',  icon: 'pi-home',         label: 'Dashboard' },
    ]},
    { section: 'Académico', maxNivel: 4, items: [
      { route: '/alumnos',          icon: 'pi-users',        label: 'Alumnos' },
      { route: '/reinscripcion',    icon: 'pi-refresh',      label: 'Reinscripción',       maxNivel: 3 },
      { route: '/cierre-ciclo',     icon: 'pi-lock',         label: 'Cierre de Ciclo',     maxNivel: 2 },
      { route: '/padres-admin',     icon: 'pi-users',        label: 'Gestión de Padres',   maxNivel: 1 },
      { route: '/profesores',       icon: 'pi-id-card',      label: 'Profesores' },
      { route: '/grupos',           icon: 'pi-building',     label: 'Grupos' },
      { route: '/aulas',            icon: 'pi-map',          label: 'Aulas',               maxNivel: 3 },
      { route: '/planes-estudio',   icon: 'pi-list',         label: 'Planes de Estudio',   maxNivel: 3 },
      { route: '/calificaciones',   icon: 'pi-star',         label: 'Calificaciones' },
      { route: '/evaluaciones',     icon: 'pi-file-check',   label: 'Evaluaciones' },
      { route: '/asistencias',      icon: 'pi-check-square', label: 'Asistencias' },
      { route: '/tareas',           icon: 'pi-file-edit',    label: 'Tareas' },
      { route: '/planeacion',       icon: 'pi-list-check',   label: 'Planeación',          maxNivel: 3 },
    ]},
    { section: 'Operaciones', maxNivel: 3, items: [
      { route: '/horarios',   icon: 'pi-calendar',           label: 'Horarios' },
      { route: '/calendario', icon: 'pi-calendar-clock',    label: 'Calendario Escolar', maxNivel: 3 },
      { route: '/conducta',  icon: 'pi-exclamation-circle', label: 'Conducta' },
      { route: '/medico',               icon: 'pi-heart',           label: 'Expediente Médico' },
      { route: '/condiciones-cronicas', icon: 'pi-exclamation-triangle', label: 'Condiciones Crónicas', maxNivel: 3 },
      { route: '/justificaciones',      icon: 'pi-check-circle',    label: 'Justificaciones Faltas', maxNivel: 3 },
      { route: '/movilidad',            icon: 'pi-arrows-h',        label: 'Movilidad Estudiantil', maxNivel: 3 },
      { route: '/admision',             icon: 'pi-user-plus',       label: 'Admisión', maxNivel: 3 },
    ]},
    { section: 'Recursos Humanos', maxNivel: 2, items: [
      { route: '/licencias',           icon: 'pi-calendar-times',  label: 'Licencias y Permisos',   maxNivel: 2 },
      { route: '/capacitaciones',      icon: 'pi-graduation-cap',  label: 'Capacitaciones',          maxNivel: 2 },
      { route: '/expediente-laboral',  icon: 'pi-id-card',         label: 'Expediente Laboral',      maxNivel: 2 },
      { route: '/disponibilidad',      icon: 'pi-calendar',        label: 'Disponibilidad Docente',  maxNivel: 2 },
      { route: '/asistencia-personal', icon: 'pi-clock',           label: 'Asistencia Personal',     maxNivel: 2 },
    ]},
    { section: 'Comunicación', maxNivel: 4, items: [
      { route: '/comunicados', icon: 'pi-envelope',   label: 'Comunicados' },
      { route: '/foros',       icon: 'pi-comments',   label: 'Foros y Anuncios' },
      { route: '/encuestas',   icon: 'pi-chart-pie',  label: 'Encuestas',  maxNivel: 3 },
    ]},
    { section: 'Gradebook', maxNivel: 4, items: [
      { route: '/gradebook',         icon: 'pi-book',       label: 'Gradebook' },
      { route: '/mi-progreso',       icon: 'pi-chart-line', label: 'Mi Progreso' },
      { route: '/ponderacion-config', icon: 'pi-sliders-h', label: 'Ponderaciones', maxNivel: 3 },
    ]},
    { section: 'Recursos', maxNivel: 4, items: [
      { route: '/rubricas', icon: 'pi-table',   label: 'Rúbricas' },
      { route: '/badges',   icon: 'pi-star-fill', label: 'Insignias' },
      { route: '/portal',   icon: 'pi-id-card',  label: 'Portal Alumno' },
    ]},
    { section: 'Mi Familia', minNivel: 5, items: [
      { route: '/padres',       icon: 'pi-users',        label: 'Portal de Padres',    minNivel: 5 },
      { route: '/portal',       icon: 'pi-chart-line',   label: 'Progreso del Alumno', minNivel: 5 },
      { route: '/comunicados',  icon: 'pi-envelope',     label: 'Comunicados',         minNivel: 5 },
    ]},
    { section: 'Inteligencia', maxNivel: 3, items: [
      { route: '/bi',             icon: 'pi-chart-pie',      label: 'Dashboards BI' },
      { route: '/grade-analytics', icon: 'pi-chart-bar',     label: 'Grade Analytics' },
      { route: '/ia',             icon: 'pi-sparkles',       label: 'Asistente IA + Datos' },
      { route: '/eval-docente',   icon: 'pi-star',           label: 'Eval. Docente 360°' },
      { route: '/learning-paths', icon: 'pi-graduation-cap', label: 'Learning Paths' },
    ]},
    { section: 'Reportes', maxNivel: 3, items: [
      { route: '/reportes',         icon: 'pi-file-pdf',       label: 'Generador de Reportes' },
      { route: '/certificados',     icon: 'pi-verified',       label: 'Certificados Digitales', maxNivel: 3 },
      { route: '/expediente-doc',   icon: 'pi-folder-open',    label: 'Expediente Digital',     maxNivel: 3 },
    ]},
    { section: 'Sistema', maxNivel: 1, items: [
      { route: '/monitor', icon: 'pi-heart-fill', label: 'Monitor del Sistema' },
      { route: '/admin',   icon: 'pi-cog',        label: 'Administración' },
    ]},
    { section: 'Ayuda', items: [
      { route: '/ayuda', icon: 'pi-question-circle', label: 'Manual de Usuario' },
    ]},
  ];

  readonly navGroupsVisible = computed(() => {
    const nivel = this.ctx.nivelAcceso();
    if (nivel === 0) return this._allNavGroups;
    const itemVisible = (item: NavItem, groupMax?: number, groupMin?: number) => {
      const max = item.maxNivel ?? groupMax ?? 99;
      const min = item.minNivel ?? groupMin ?? 0;
      return nivel <= max && nivel >= min;
    };
    return this._allNavGroups
      .filter(g => {
        const max = g.maxNivel ?? 99;
        const min = g.minNivel ?? 0;
        return nivel <= max && nivel >= min;
      })
      .map(g => ({ ...g, items: g.items.filter(item => itemVisible(item, g.maxNivel, g.minNivel)) }))
      .filter(g => g.items.length > 0);
  });

  ngOnInit(): void {
    // FASE 20 — Inicializar push notifications (ntfy SSE) al cargar el shell
    this.push.init().catch(() => { /* ntfy opcional — modo degradado */ });

    // Inicializar breadcrumbs
    this.buildBreadcrumbs();
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe(() => {
      this.buildBreadcrumbs();
    });

    const usuario = this.ctx.usuario();

    this.api.get<Plantel[]>('/planteles').subscribe(p => {
      // ADMIN_GLOBAL: primera opción es "Todo el Instituto" (null plantel)
      const opciones = this.ctx.esAdminGlobal() ? [TODO_INSTITUTO, ...p] : p;
      this.planteles.set(opciones);

      if (!this.selectedPlantel) {
        if (usuario?.plantel_id) {
          // Usuario con plantel fijo: seleccionar su plantel
          const match = p.find(x => x.id === usuario.plantel_id) ?? p[0];
          this.selectedPlantel = match ?? null;
        } else if (this.ctx.esAdminGlobal()) {
          // Admin global: iniciar en "Todo el Instituto"
          this.selectedPlantel = TODO_INSTITUTO;
        } else {
          this.selectedPlantel = p[0] ?? null;
        }
        this.ctx.setPlantel(this.selectedPlantel?.id ? this.selectedPlantel : null);
      }

      if (this.selectedPlantel?.id) this.loadNiveles();
      else this.loadNivelesTodos();
    });

    this.loadNotifCount();
    this.notifInterval = setInterval(() => this.loadNotifCount(), 60_000);
  }

  ngOnDestroy(): void {
    clearInterval(this.notifInterval);
  }

  loadNotifCount(): void {
    this.api.get<{ total: number }>('/notificaciones/no-leidas-count').subscribe({
      next: (r) => this.notifCount.set(r.total),
      error: () => {},
    });
  }

  toggleUserMenu(event: Event): void {
    if (this.userMenuRef.overlayVisible) {
      this.userMenuRef.hide();
    } else {
      this.userMenuRef.show(event);
    }
  }

  toggleNotifPanel(event: Event): void {
    if (this.notifPanelRef.overlayVisible) {
      this.notifPanelRef.hide();
    } else {
      this.api.get<Notif[]>('/notificaciones/mis-notificaciones', { limit: 10 }).subscribe({
        next: (r) => { this.notificaciones.set(r); this.notifPanelRef.show(event); },
        error: () => this.notifPanelRef.show(event),
      });
    }
  }

  marcarLeida(n: Notif): void {
    if (n.leido) return;
    this.api.put(`/notificaciones/${n.id}/leer`, {}).subscribe(() => {
      this.notificaciones.update(list => list.map(x => x.id === n.id ? { ...x, leido: true } : x));
      this.notifCount.update(c => Math.max(0, c - 1));
    });
  }

  leerTodas(): void {
    this.api.put('/notificaciones/leer-todas', {}).subscribe(() => {
      this.notificaciones.update(list => list.map(x => ({ ...x, leido: true })));
      this.notifCount.set(0);
    });
  }

  notifIcon(tipo: string): string {
    const map: Record<string, string> = {
      ALERTA: 'pi-exclamation-triangle',
      INFO:   'pi-info-circle',
      AVISO:  'pi-bell',
    };
    return map[tipo] ?? 'pi-bell';
  }

  onPlantelChange(p: Plantel): void {
    const esGlobal = !p?.id;
    this.ctx.setPlantel(esGlobal ? null : p);
    this.selectedNivel = null;
    this.ctx.setNivel(null);
    this.selectedCiclo = null;
    this.ctx.setCiclo(null);
    this.ciclos.set([]);
    if (esGlobal) this.loadNivelesTodos();
    else this.loadNiveles();
  }

  onNivelChange(n: NivelEducativo): void {
    const esGlobal = !n?.id;
    this.ctx.setNivel(esGlobal ? null : n);
    this.selectedCiclo = null;
    this.ctx.setCiclo(null);
    this.loadCiclos();
  }

  onCicloChange(c: CicloEscolar): void {
    this.ctx.setCiclo(c);
  }

  private loadNivelesTodos(): void {
    // Sin plantel específico: cargar todos los niveles únicos
    this.api.get<NivelEducativo[]>('/catalogs/niveles').subscribe(ns => {
      this.niveles.set([TODOS_NIVELES, ...ns]);
      this.selectedNivel = TODOS_NIVELES;
      this.ctx.setNivel(null);
      this.loadCiclos();
    });
  }

  private loadNiveles(): void {
    const plantelId = this.selectedPlantel?.id;
    if (!plantelId) return;

    this.api.get<NivelEducativo[]>(`/planteles/${plantelId}/niveles`).subscribe(ns => {
      // Admin global puede ver "todos los niveles" también por plantel
      const opciones = this.ctx.esAdminGlobal() ? [TODOS_NIVELES, ...ns] : ns;
      this.niveles.set(opciones);

      // Resolver nivel inicial
      const usuario = this.ctx.usuario();
      let inicial: NivelEducativo | null = null;

      if (usuario?.nivel_educativo_id) {
        inicial = ns.find(x => x.id === usuario.nivel_educativo_id) ?? null;
      }
      if (!inicial && this.selectedNivel?.id) {
        inicial = ns.find(x => x.id === this.selectedNivel!.id) ?? ns[0] ?? null;
      }
      if (!inicial && ns.length) inicial = ns[0];

      if (inicial) {
        this.selectedNivel = inicial;
        this.ctx.setNivel(inicial);
        this.loadCiclos();
      }
    });
  }

  private loadCiclos(): void {
    const nivel = this.selectedNivel?.id ? this.selectedNivel.nombre_nivel : undefined;
    const params: Record<string, any> = { solo_vigentes: true };
    if (nivel) params['nivel'] = nivel;

    this.api.get<CicloEscolar[]>('/catalogs/ciclos', params).subscribe(c => {
      const labeled = c.map(x => ({
        ...x,
        _label: (!this.selectedNivel?.id && x.nombre_nivel)
          ? `${x.nombre_ciclo} — ${x.nombre_nivel}`
          : x.nombre_ciclo,
      }));
      this.ciclos.set(labeled);
      if (labeled.length) {
        const existente = this.selectedCiclo ? labeled.find(x => x.id === this.selectedCiclo!.id) : null;
        const elegido = existente ?? labeled[0];
        this.selectedCiclo = elegido;
        this.ctx.setCiclo(elegido);
      }
    });
  }

  private buildBreadcrumbs(): void {
    const urlSegments = this.router.url.split('?')[0].split('/').filter(s => s);
    this.breadcrumbItems = urlSegments.map((segment, index) => {
      const path = '/' + urlSegments.slice(0, index + 1).join('/');
      return {
        label: ROUTE_TITLES[segment] ?? this.humanize(segment),
        routerLink: index < urlSegments.length - 1 ? path : undefined,
      };
    });
  }

  private humanize(text: string): string {
    return text
      .split('-')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  }
}
