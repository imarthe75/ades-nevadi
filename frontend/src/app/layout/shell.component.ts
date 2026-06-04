/**
 * ShellComponent — Layout principal con topbar tipo APEX.
 * Contiene: logo, selector de plantel/ciclo (Application Items), nav lateral, router-outlet.
 * Colores institucionales Instituto Nevadi — primario #D02030.
 */
import { Component, inject, OnInit, signal, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, RouterOutlet } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ToolbarModule } from 'primeng/toolbar';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { MenuModule } from 'primeng/menu';
import { ToastModule } from 'primeng/toast';
import { PopoverModule, Popover } from 'primeng/popover';
import { BadgeModule } from 'primeng/badge';
import { MessageService } from 'primeng/api';

import { ContextService } from '../core/services/context.service';
import { AuthService } from '../core/services/auth.service';
import { ApiService } from '../core/services/api.service';
import type { Plantel, CicloEscolar } from '../core/models';

interface Notif { id: string; titulo: string; cuerpo: string; tipo: string; leido: boolean; }

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    CommonModule, RouterModule, RouterOutlet, FormsModule,
    ToolbarModule, ButtonModule, SelectModule, MenuModule, ToastModule,
    PopoverModule, BadgeModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast position="top-right" />

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

        <!-- Selector de Plantel (Application Item APEX-style) -->
        <p-select
          [options]="planteles()"
          [(ngModel)]="selectedPlantel"
          optionLabel="nombre_plantel"
          placeholder="Plantel..."
          styleClass="ctx-selector"
          (onChange)="onPlantelChange($event.value)"
        />

        <!-- Selector de Ciclo -->
        <p-select
          [options]="ciclos()"
          [(ngModel)]="selectedCiclo"
          optionLabel="nombre_ciclo"
          placeholder="Ciclo escolar..."
          styleClass="ctx-selector"
          (onChange)="onCicloChange($event.value)"
        />
      </ng-template>

      <ng-template pTemplate="end">
        <span class="user-name">{{ ctx.usuario()?.nombre_completo }}</span>

        <!-- Campanita de notificaciones -->
        <div class="notif-bell" (click)="toggleNotifPanel($event)">
          <i class="pi pi-bell topbar-icon"></i>
          @if (notifCount() > 0) {
            <span class="notif-badge">{{ notifCount() > 99 ? '99+' : notifCount() }}</span>
          }
        </div>

        <p-button
          icon="pi pi-sign-out"
          [text]="true"
          [plain]="true"
          pTooltip="Cerrar sesión"
          (onClick)="auth.logout()"
          styleClass="topbar-btn"
        />
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
        <div class="notif-item" [class.notif-no-leida]="!n.leido" (click)="marcarLeida(n)">
          <i [class]="'pi ' + notifIcon(n.tipo)"></i>
          <div class="notif-text">
            <div class="notif-titulo">{{ n.titulo }}</div>
            <div class="notif-cuerpo">{{ n.cuerpo }}</div>
          </div>
        </div>
      }
      <div class="notif-panel-footer">
        <a routerLink="/comunicados" (click)="notifPanel.hide()">Ver comunicados →</a>
      </div>
    </p-popover>

    <!-- ── Layout principal ── -->
    <div class="app-layout">
      <!-- Nav lateral -->
      <nav class="sidenav">
        @for (group of navGroups; track group.section) {
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

    .user-name { color: var(--topbar-text-muted); font-size: 0.82rem; margin-right: 0.5rem; }

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
    .notif-item {
      display: flex; gap: .75rem; padding: .65rem 1rem; cursor: pointer;
      border-bottom: 1px solid var(--surface-100);
      transition: background .12s;
    }
    .notif-item:hover { background: var(--surface-50); }
    .notif-no-leida   { background: var(--blue-50); }
    .notif-no-leida:hover { background: var(--blue-100); }
    .notif-item i     { color: var(--primary-color); margin-top: .15rem; font-size: .9rem; flex-shrink: 0; }
    .notif-titulo     { font-weight: 600; font-size: .82rem; margin-bottom: .1rem; }
    .notif-cuerpo     { font-size: .75rem; color: var(--text-color-secondary); }
    .notif-panel-footer {
      padding: .5rem 1rem; text-align: center; border-top: 1px solid var(--surface-200);
    }
    .notif-panel-footer a { font-size: .8rem; color: var(--primary-color); text-decoration: none; }
    .notif-panel-footer a:hover { text-decoration: underline; }

    /* p-select en topbar */
    :host ::ng-deep .ctx-selector { min-width: 160px; }

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
export class ShellComponent implements OnInit {
  readonly ctx  = inject(ContextService);
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);

  planteles = signal<Plantel[]>([]);
  ciclos    = signal<CicloEscolar[]>([]);
  selectedPlantel: Plantel | null = this.ctx.plantel();
  selectedCiclo: CicloEscolar | null = this.ctx.ciclo();

  notifCount    = signal(0);
  notificaciones = signal<Notif[]>([]);
  @ViewChild('notifPanel') notifPanelRef!: Popover;

  readonly navGroups: { section: string; items: { route: string; icon: string; label: string }[] }[] = [
    {
      section: 'Principal',
      items: [
        { route: '/dashboard',  icon: 'pi-home',  label: 'Dashboard' },
        { route: '/planteles',  icon: 'pi-map-marker', label: 'Planteles' },
      ],
    },
    {
      section: 'Académico',
      items: [
        { route: '/alumnos',        icon: 'pi-users',        label: 'Alumnos' },
        { route: '/profesores',     icon: 'pi-id-card',      label: 'Profesores' },
        { route: '/grupos',         icon: 'pi-building',     label: 'Grupos' },
        { route: '/calificaciones', icon: 'pi-star',         label: 'Calificaciones' },
        { route: '/evaluaciones',   icon: 'pi-file-check',   label: 'Evaluaciones' },
        { route: '/asistencias',    icon: 'pi-check-square', label: 'Asistencias' },
        { route: '/tareas',         icon: 'pi-file-edit',    label: 'Tareas' },
        { route: '/planeacion',     icon: 'pi-list-check',   label: 'Planeación' },
      ],
    },
    {
      section: 'Operaciones',
      items: [
        { route: '/horarios', icon: 'pi-calendar',            label: 'Horarios' },
        { route: '/conducta', icon: 'pi-exclamation-circle',  label: 'Conducta' },
        { route: '/medico',   icon: 'pi-heart',               label: 'Expediente Médico' },
      ],
    },
    {
      section: 'Comunicación',
      items: [
        { route: '/comunicados', icon: 'pi-envelope',  label: 'Comunicados' },
        { route: '/encuestas',   icon: 'pi-chart-pie', label: 'Encuestas' },
      ],
    },
    {
      section: 'Gradebook',
      items: [
        { route: '/gradebook',          icon: 'pi-book',       label: 'Gradebook' },
        { route: '/mi-progreso',        icon: 'pi-chart-line', label: 'Mi Progreso' },
        { route: '/ponderacion-config', icon: 'pi-sliders-h',  label: 'Ponderaciones' },
      ],
    },
    {
      section: 'Recursos',
      items: [
        { route: '/rubricas', icon: 'pi-table',     label: 'Rúbricas' },
        { route: '/badges',   icon: 'pi-star-fill', label: 'Insignias' },
        { route: '/portal',   icon: 'pi-id-card',   label: 'Portal Alumno' },
      ],
    },
    {
      section: 'Inteligencia',
      items: [
        { route: '/grade-analytics', icon: 'pi-chart-bar',      label: 'Grade Analytics' },
        { route: '/eval-docente',    icon: 'pi-star',           label: 'Eval. Docente 360°' },
        { route: '/learning-paths',  icon: 'pi-graduation-cap', label: 'Learning Paths' },
        { route: '/ia',              icon: 'pi-sparkles',       label: 'Asistente IA' },
      ],
    },
  ];

  ngOnInit(): void {
    this.api.get<Plantel[]>('/planteles').subscribe(p => {
      this.planteles.set(p);
      if (!this.selectedPlantel && p.length) {
        this.selectedPlantel = p[0];
        this.ctx.setPlantel(p[0]);
        this.loadCiclos();
      }
    });
    if (this.selectedPlantel) this.loadCiclos();
    this.loadNotifCount();
    setInterval(() => this.loadNotifCount(), 60_000);   // refresca cada minuto
  }

  loadNotifCount(): void {
    this.api.get<{ total: number }>('/notificaciones/no-leidas-count').subscribe({
      next: (r) => this.notifCount.set(r.total),
      error: () => {},
    });
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
    this.ctx.setPlantel(p);
    this.loadCiclos();
  }

  onCicloChange(c: CicloEscolar): void {
    this.ctx.setCiclo(c);
  }

  private loadCiclos(): void {
    this.api.get<CicloEscolar[]>('/ciclos', { solo_vigentes: true }).subscribe(c => {
      this.ciclos.set(c);
      if (!this.selectedCiclo && c.length) {
        this.selectedCiclo = c[0];
        this.ctx.setCiclo(c[0]);
      }
    });
  }
}
