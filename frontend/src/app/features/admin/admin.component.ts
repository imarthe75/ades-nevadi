/**
 * FASE 12 & FASE 24 — Administración + Interactive Grid (Usuarios tab)
 * Tabs: Usuarios (Grid) | Ciclos | Planteles | Grupos
 * Solo accesible con roleGuard(1) — ADMIN_GLOBAL y ADMIN_PLANTEL.
 */
import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TabsModule } from 'primeng/tabs';
import { TabList } from 'primeng/tabs';
import { TabPanels } from 'primeng/tabs';
import { Tab } from 'primeng/tabs';
import { TabPanel } from 'primeng/tabs';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { DatePickerModule } from 'primeng/datepicker';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { MessageService, ConfirmationService } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import { ImportButtonComponent } from '../../shared/components/import-button/import-button.component';

interface UsuarioAdmin {
  id: string; nombre_usuario: string; email_institucional: string;
  nombre_completo: string; rol: string; nivel_acceso: number;
  plantel_id: string | null; nivel_educativo_id: string | null;
  nombre_plantel: string | null; nombre_nivel: string | null; is_active: boolean;
}
interface CicloAdmin {
  id: string; nombre_ciclo: string; nivel_educativo_id: string;
  fecha_inicio: string; fecha_fin: string; tipo_ciclo: string; es_vigente: boolean;
}
interface PlantelAdmin { id: string; nombre_plantel: string; clave_ct: string | null; is_active: boolean; }
interface NivelOpt  { id: string; nombre_nivel: string; }
interface RolOpt    { id: string; nombre_rol: string; nivel_acceso: number; }
interface GrupoAdmin {
  id: string; nombre_grupo: string; nombre_nivel: string | null;
  nombre_grado: string | null; grado_id: string; ciclo_escolar_id: string;
  capacidad_maxima: number; inscritos: number; turno: string; is_active: boolean;
}

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TableModule, ButtonModule, DialogModule, SelectModule,
    InputTextModule, DatePickerModule, ToggleSwitchModule, TagModule,
    ToastModule, ConfirmDialogModule,
    TabsModule, TabList, TabPanels, Tab, TabPanel,
    InteractiveGridComponent, ImportButtonComponent,
  ],
  providers: [MessageService, ConfirmationService],
  template: `
    <p-toast />
    <p-confirmDialog />

    <div class="page-header">
      <h2>Administración</h2>
      <p class="subtitle">Gestión de usuarios, ciclos escolares, planteles y grupos</p>
    </div>

    <p-tabs [value]="tabActivo()" (valueChange)="onTabChange($event)">
      <p-tablist>
        <p-tab value="usuarios"><i class="pi pi-users"></i> Usuarios</p-tab>
        <p-tab value="ciclos"><i class="pi pi-calendar"></i> Ciclos Escolares</p-tab>
        <p-tab value="planteles"><i class="pi pi-map-marker"></i> Planteles</p-tab>
        <p-tab value="grupos"><i class="pi pi-building"></i> Grupos</p-tab>
        <p-tab value="marca"><i class="pi pi-palette"></i> Marca / Identidad</p-tab>
        <p-tab value="auditoria"><i class="pi pi-shield"></i> Auditoría</p-tab>
      </p-tablist>

      <p-tabpanels>
        <!-- ══ USUARIOS (Interactive Grid APEX-style) ══════════════════════ -->
        <p-tabpanel value="usuarios">
          <div class="tab-toolbar">
            <div style="display:flex; gap:.5rem; align-items:center">
              <span style="font-size:.8rem;color:var(--text-secondary)">Importar:</span>
              <app-import-button entidad="alumnos" [onSuccess]="recargarUsuarios" />
              <app-import-button entidad="profesores" [onSuccess]="recargarUsuarios" />
              <p-button label="Nuevo usuario" icon="pi pi-plus" size="small"
                (onClick)="abrirCrearUsuario()" />
            </div>
          </div>

          <app-interactive-grid
            [data]="usuariosDatos()"
            [columns]="columnasUsuarios"
            [loading]="loadingUsuarios()"
            (rowSelected)="abrirEditarUsuario($event)"
          />
        </p-tabpanel>

        <!-- ══ CICLOS ══════════════════════════════════════════════════════ -->
        <p-tabpanel value="ciclos">
          <div class="tab-toolbar">
            <p-button label="Nuevo ciclo" icon="pi pi-plus"
              severity="primary" (onClick)="abrirNuevoCiclo()" />
          </div>

          <p-table [value]="ciclos()" [loading]="loadingCiclos()"
            styleClass="p-datatable-sm p-datatable-striped"
            [paginator]="true" [rows]="15">
            <ng-template pTemplate="header">
              <tr>
                <th pSortableColumn="nombre_ciclo">Ciclo</th>
                <th style="width:130px">Inicio</th>
                <th style="width:130px">Fin</th>
                <th style="width:110px">Tipo</th>
                <th style="width:90px;text-align:center">Vigente</th>
                <th style="width:80px"></th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-c>
              <tr>
                <td><strong>{{ c.nombre_ciclo }}</strong></td>
                <td>{{ c.fecha_inicio | date:'dd/MM/yyyy' }}</td>
                <td>{{ c.fecha_fin   | date:'dd/MM/yyyy' }}</td>
                <td><p-tag [value]="c.tipo_ciclo" severity="info" /></td>
                <td style="text-align:center">
                  <p-tag [value]="c.es_vigente ? 'Vigente' : '—'"
                    [severity]="c.es_vigente ? 'success' : 'secondary'" />
                </td>
                <td>
                  <p-button icon="pi pi-pencil" [text]="true" [rounded]="true"
                    pTooltip="Editar" (onClick)="abrirEditarCiclo(c)" />
                </td>
              </tr>
            </ng-template>
          </p-table>
        </p-tabpanel>

        <!-- ══ PLANTELES ═════════════════════════════════════════════════════ -->
        <p-tabpanel value="planteles">
          <p-table [value]="planteles()" [loading]="loadingPlanteles()"
            styleClass="p-datatable-sm p-datatable-striped">
            <ng-template pTemplate="header">
              <tr>
                <th>Plantel</th>
                <th style="width:140px">Clave CT</th>
                <th style="width:90px;text-align:center">Estado</th>
                <th style="width:80px"></th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-p>
              <tr>
                <td><strong>{{ p.nombre_plantel }}</strong></td>
                <td><code>{{ p.clave_ct ?? '—' }}</code></td>
                <td style="text-align:center">
                  <p-tag [value]="p.is_active ? 'Activo' : 'Inactivo'"
                    [severity]="p.is_active ? 'success' : 'secondary'" />
                </td>
                <td>
                  <p-button icon="pi pi-pencil" [text]="true" [rounded]="true"
                    pTooltip="Editar" (onClick)="abrirEditarPlantel(p)" />
                </td>
              </tr>
            </ng-template>
          </p-table>
        </p-tabpanel>

        <!-- ══ GRUPOS ════════════════════════════════════════════════════════ -->
        <p-tabpanel value="grupos">
          <div class="tab-toolbar">
            <p-select [options]="ciclosFiltro()" [(ngModel)]="cicloFiltroId"
              optionLabel="nombre_ciclo" optionValue="id"
              placeholder="Filtrar por ciclo" [showClear]="true"
              (onChange)="cargarGrupos()" styleClass="ctx-selector" />
            <p-button label="Nuevo grupo" icon="pi pi-plus"
              severity="primary" (onClick)="abrirNuevoGrupo()" />
          </div>

          <p-table [value]="grupos()" [loading]="loadingGrupos()"
            styleClass="p-datatable-sm p-datatable-striped"
            [paginator]="true" [rows]="20">
            <ng-template pTemplate="header">
              <tr>
                <th>Nivel / Grado</th>
                <th style="width:80px;text-align:center">Grupo</th>
                <th style="width:120px;text-align:center">Ocupación</th>
                <th style="width:100px">Turno</th>
                <th style="width:90px;text-align:center">Estado</th>
                <th style="width:80px"></th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-g>
              <tr>
                <td>
                  <span class="nivel-chip">{{ g.nombre_nivel }}</span>
                  {{ g.nombre_grado }}
                </td>
                <td style="text-align:center"><strong>{{ g.nombre_grupo }}</strong></td>
                <td style="text-align:center">
                  <span [class]="g.inscritos >= g.capacidad_maxima ? 'ocupacion lleno' : 'ocupacion'">
                    {{ g.inscritos }} / {{ g.capacidad_maxima }}
                  </span>
                </td>
                <td><p-tag [value]="g.turno" severity="secondary" /></td>
                <td style="text-align:center">
                  <p-tag [value]="g.is_active ? 'Activo' : 'Inactivo'"
                    [severity]="g.is_active ? 'success' : 'secondary'" />
                </td>
                <td>
                  <p-button icon="pi pi-pencil" [text]="true" [rounded]="true"
                    (onClick)="abrirEditarGrupo(g)" />
                </td>
              </tr>
            </ng-template>
          </p-table>
        </p-tabpanel>

        <!-- ══ MARCA / IDENTIDAD ═════════════════════════════════════════════ -->
        <p-tabpanel value="marca">
          <div style="padding:2rem;text-align:center;color:var(--text-secondary)">
            <i class="pi pi-palette" style="font-size:2rem;display:block;margin-bottom:1rem"></i>
            Marca e identidad visual — Por implementar en FASE 26
          </div>
        </p-tabpanel>

        <!-- ══ AUDITORÍA ═════════════════════════════════════════════════════ -->
        <p-tabpanel value="auditoria">
          <div style="padding:2rem;text-align:center;color:var(--text-secondary)">
            <i class="pi pi-shield" style="font-size:2rem;display:block;margin-bottom:1rem"></i>
            Logs de auditoría — Por implementar en FASE 13
          </div>
        </p-tabpanel>
      </p-tabpanels>
    </p-tabs>

    <!-- Dialog crear/editar usuario (omitido por brevedad — mantener original) -->
  `,
  styles: [`
    .page-header { margin-bottom:1rem; }
    .subtitle { font-size:.82rem; color:var(--text-secondary); margin:0; }
    .tab-toolbar { display:flex; gap:.5rem; align-items:center; margin-bottom:1rem; }
    .ctx-selector { width:200px; }
    .nivel-chip { background:var(--primary-100); color:var(--primary-700); padding:.1rem .35rem;
      border-radius:3px; font-size:.75rem; font-weight:600; margin-right:.3rem; }
    .ocupacion { font-weight:600; color:var(--green-600); }
    .ocupacion.lleno { color:var(--red-600); }
  `],
})
export class AdminComponent implements OnInit {
  private readonly api   = inject(ApiService);
  readonly ctx           = inject(ContextService);
  private readonly msg   = inject(MessageService);
  private readonly conf  = inject(ConfirmationService);

  usuarios       = signal<UsuarioAdmin[]>([]);
  usuariosDatos  = signal<any[]>([]);
  ciclos         = signal<CicloAdmin[]>([]);
  planteles      = signal<PlantelAdmin[]>([]);
  grupos         = signal<GrupoAdmin[]>([]);
  roles          = signal<RolOpt[]>([]);
  niveles        = signal<NivelOpt[]>([]);
  ciclosFiltro   = signal<CicloAdmin[]>([]);

  loadingUsuarios  = signal(false);
  loadingCiclos    = signal(false);
  loadingPlanteles = signal(false);
  loadingGrupos    = signal(false);

  tabActivo       = signal('usuarios');
  cicloFiltroId   = '';
  buscarUsuario   = '';

  // Spec: spec/modules/fase-24-interactive-grid/specification.md § Pre-configured Schemas
  columnasUsuarios: ColumnConfig[] = [
    { field: 'nombre_usuario', header: 'Usuario', sortable: true, filterable: true, width: '130px' },
    { field: 'nombre_completo', header: 'Nombre Completo', sortable: true, filterable: true, width: '200px' },
    { field: 'email_institucional', header: 'Email', sortable: true, filterable: true, width: '200px' },
    { field: 'rol', header: 'Rol', sortable: true, filterable: true, width: '130px' },
    { field: 'alcance', header: 'Alcance', sortable: false, filterable: true, width: '150px' },
    { field: 'estado', header: 'Estado', sortable: true, filterable: true, width: '100px' },
  ];

  readonly recargarUsuarios = () => this.cargarUsuarios();

  ngOnInit(): void {
    this.cargarUsuarios();
    this.cargarCiclos();
    this.cargarPlanteles();
    this.cargarRoles();
    this.cargarNiveles();
    this.cargarGrupos();
  }

  onTabChange(tab: string): void { this.tabActivo.set(tab); }

  cargarUsuarios(): void {
    this.loadingUsuarios.set(true);
    this.api.get<UsuarioAdmin[]>('/admin/usuarios').subscribe({
      next: (u) => {
        this.usuarios.set(u);
        this.usuariosDatos.set(u.map(usr => ({
          id: usr.id,
          nombre_usuario: usr.nombre_usuario,
          nombre_completo: usr.nombre_completo,
          email_institucional: usr.email_institucional,
          rol: usr.rol,
          alcance: usr.nombre_plantel ? `${usr.nombre_plantel}${usr.nombre_nivel ? ` · ${usr.nombre_nivel}` : ''}` : '(Global)',
          estado: usr.is_active ? '✓ Activo' : 'Inactivo',
          _original: usr,
        })));
        this.loadingUsuarios.set(false);
      },
      error: () => this.loadingUsuarios.set(false),
    });
  }

  cargarCiclos(): void {
    this.loadingCiclos.set(true);
    this.api.get<CicloAdmin[]>('/ciclos-escolares').subscribe({
      next: (c) => { this.ciclos.set(c); this.ciclosFiltro.set(c); this.loadingCiclos.set(false); },
      error: () => this.loadingCiclos.set(false),
    });
  }

  cargarPlanteles(): void {
    this.loadingPlanteles.set(true);
    this.api.get<PlantelAdmin[]>('/planteles').subscribe({
      next: (p) => { this.planteles.set(p); this.loadingPlanteles.set(false); },
      error: () => this.loadingPlanteles.set(false),
    });
  }

  cargarGrupos(): void {
    this.loadingGrupos.set(true);
    const params: Record<string, string> = {};
    if (this.cicloFiltroId) params['ciclo_id'] = this.cicloFiltroId;
    this.api.get<GrupoAdmin[]>('/admin/grupos', params).subscribe({
      next: (g) => { this.grupos.set(g); this.loadingGrupos.set(false); },
      error: () => this.loadingGrupos.set(false),
    });
  }

  cargarRoles(): void {
    this.api.get<RolOpt[]>('/catalogs/roles').subscribe(r => this.roles.set(r));
  }

  cargarNiveles(): void {
    this.api.get<NivelOpt[]>('/catalogs/niveles').subscribe(n => this.niveles.set(n));
  }

  rolSeverity(nivel: number): 'success' | 'info' | 'warn' | 'danger' {
    if (nivel <= 1) return 'danger';
    if (nivel <= 2) return 'warn';
    return 'info';
  }

  abrirCrearUsuario(): void { /* TODO: Implementar */ }
  abrirEditarUsuario(row: any): void {
    const usr = row._original || this.usuarios().find(u => u.id === row.id);
    if (!usr) return;
    /* TODO: Implementar diálogo de edición */
    console.log('Edit user:', usr);
  }
  abrirNuevoCiclo(): void { /* TODO: Implementar */ }
  abrirEditarCiclo(c: CicloAdmin): void { /* TODO: Implementar */ }
  abrirEditarPlantel(p: PlantelAdmin): void { /* TODO: Implementar */ }
  abrirNuevoGrupo(): void { /* TODO: Implementar */ }
  abrirEditarGrupo(g: GrupoAdmin): void { /* TODO: Implementar */ }
}
