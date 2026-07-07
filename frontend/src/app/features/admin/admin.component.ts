/**
 * FASE 12 & FASE 24 & FASE 26-A — Administración + Interactive Grid + Variables/Catálogos
 * Tabs: Usuarios | Ciclos | Planteles | Grupos | Variables del Sistema | Catálogos | Marca | Auditoría
 * Solo accesible con roleGuard(1) — ADMIN_GLOBAL y ADMIN_PLANTEL.
 */
import { Component, OnInit, inject, signal, computed, effect } from '@angular/core';
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
import { TextareaModule } from 'primeng/textarea';
import { InputNumberModule } from 'primeng/inputnumber';
import { DatePickerModule } from 'primeng/datepicker';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import { ImportButtonComponent } from '../../shared/components/import-button/import-button.component';
import { SelectorGeoComponent } from '../../shared/components/selector-geo/selector-geo.component';
import { ApexNotificationService } from 'apex-component-library';
import { SkeletonModule } from 'primeng/skeleton';
import { MessageModule } from 'primeng/message';

interface UsuarioAdmin {
  id: string; nombre_usuario: string; email_institucional: string;
  nombre_completo: string; rol: string; nivel_acceso: number;
  plantel_id: string | null; nivel_educativo_id: string | null;
  nombre_plantel: string | null; nombre_nivel: string | null; is_active: boolean;
}
interface CicloAdmin {
  id: string; nombre_ciclo: string; nivel_educativo_id: string; nombre_nivel?: string | null;
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
interface VariableSistema {
  id: string;
  nombre: string;
  tipo_valor: 'TEXTO' | 'BOOLEANO' | 'JSON' | 'NUMERO' | 'FECHA' | 'HORA' | 'PASSWORD';
  valor: string | null;
  descripcion: string | null;
  encriptado: boolean;
  solo_lectura: boolean;
  grupo: string | null;
  is_active: boolean;
  row_version: number;
  _valorEdit?: string;
  _valorBool?: boolean;
  _valorNum?: number;
  _valorDate?: Date;
}
interface CatalogoItem {
  id: string; catalogo_id: string; valor: string;
  descripcion: string | null; orden: number; is_active: boolean; row_version: number;
}
interface Catalogo {
  id: string; codigo: string; nombre: string;
  descripcion: string | null; is_active: boolean; row_version: number;
  items: CatalogoItem[];
}

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TableModule, ButtonModule, DialogModule, SelectModule,
    InputTextModule, TextareaModule, InputNumberModule, DatePickerModule, ToggleSwitchModule,
    TagModule, TooltipModule, ConfirmDialogModule,
    TabsModule, TabList, TabPanels, Tab, TabPanel, SkeletonModule, MessageModule,
    InteractiveGridComponent, ImportButtonComponent, SelectorGeoComponent
  ],
  providers: [ConfirmationService],
  template: `
    <p-confirmDialog />

    <div class="page-header">
      <h2>Administración</h2>
      <p class="subtitle">Gestión de usuarios, ciclos, planteles, variables del sistema y catálogos</p>
    </div>

    <!-- Barra de contexto: indica qué plantel está activo y cómo afecta las tabs -->
    @if (ctx.plantel()) {
      <div class="scope-bar">
        <i class="pi pi-map-marker" style="color:var(--primary-color)"></i>
        <span>Filtrando por <strong>{{ ctx.plantel()!.nombre_plantel }}</strong></span>
        <span class="scope-tag scope-scoped">Usuarios y Grupos filtrados</span>
        <span class="scope-tag scope-global">Roles, Ciclos, Catálogos y demás configuraciones son globales al sistema</span>
      </div>
    } @else {
      <div class="scope-bar scope-bar--global">
        <i class="pi pi-globe" style="color:var(--text-color-secondary)"></i>
        <span style="color:var(--text-color-secondary);font-size:.8rem">
          Vista global — todos los planteles. Selecciona un plantel en la barra superior para filtrar Usuarios y Grupos.
        </span>
      </div>
    }

    <p-tabs [value]="tabActivo()" (valueChange)="onTabChange($event)">
      <p-tablist>
        <p-tab value="usuarios"><i class="pi pi-users"></i> Usuarios</p-tab>
        <p-tab value="roles"><i class="pi pi-id-card"></i> Roles</p-tab>
        <p-tab value="menus"><i class="pi pi-bars"></i> Menús</p-tab>
        <p-tab value="permisos"><i class="pi pi-lock"></i> Permisos</p-tab>
        <p-tab value="ciclos"><i class="pi pi-calendar"></i> Ciclos Escolares</p-tab>
        <p-tab value="planteles"><i class="pi pi-map-marker"></i> Planteles</p-tab>
        <p-tab value="grupos"><i class="pi pi-th-large"></i> Grupos</p-tab>
        <p-tab value="variables"><i class="pi pi-cog"></i> Variables del Sistema</p-tab>
        <p-tab value="reglas"><i class="pi pi-graduation-cap"></i> Reglas de Promoción</p-tab>
        <p-tab value="franjas"><i class="pi pi-clock"></i> Franjas Horarias</p-tab>
        <p-tab value="eval-cualitativa"><i class="pi pi-star"></i> Eval. Cualitativa</p-tab>
        <p-tab value="catalogos"><i class="pi pi-list"></i> Catálogos</p-tab>
        <p-tab value="geo"><i class="pi pi-map"></i> Geográficos</p-tab>
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

        <!-- ══ ROLES ══════════════════════════════════════════════════════ -->
        <p-tabpanel value="roles">
          <div class="tab-toolbar">
            <span class="tab-title">Roles del sistema</span>
            <small style="color:var(--text-color-secondary)">Haz clic en un rol para editar su descripción y nivel de acceso.</small>
          </div>
          <app-interactive-grid
            [data]="roles()"
            [columns]="columnasRoles"
            [loading]="loadingRoles()"
            (rowSelected)="abrirEditarRol($event)"
          />
          <p-dialog [visible]="rolDlgVisible()" (visibleChange)="rolDlgVisible.set($event)"
            header="Editar Rol" [modal]="true" [draggable]="false" [style]="{width:'460px'}">
            @if (rolEdit()) {
              <div style="display:flex;flex-direction:column;gap:.75rem">
                <div class="field"><span class="dlg-lbl">Nombre</span>
                  <strong>{{ rolEdit()!.nombre_rol }}</strong></div>
                <div class="field" style="display:flex;flex-direction:column;gap:.35rem">
                  <label class="dlg-lbl">Nivel de acceso</label>
                  <p-select [(ngModel)]="rolEditForm.nivel_acceso"
                    [options]="nivelesAccesoOpts"
                    optionLabel="label" optionValue="value" />
                </div>
                <div style="background:var(--surface-50);border-radius:6px;padding:.6rem .8rem;font-size:.75rem">
                  <strong style="display:block;margin-bottom:.3rem;color:var(--text-color-secondary)">Referencia de niveles</strong>
                  <div style="display:grid;grid-template-columns:auto 1fr;gap:.15rem .6rem">
                    <span class="nivel-badge" style="width:20px;height:20px;font-size:.7rem">0</span><span>ADMIN_GLOBAL — acceso total al sistema</span>
                    <span class="nivel-badge" style="width:20px;height:20px;font-size:.7rem">1</span><span>ADMIN_PLANTEL — administración de un plantel</span>
                    <span class="nivel-badge" style="width:20px;height:20px;font-size:.7rem">2</span><span>Director / Subdirector / Coordinador</span>
                    <span class="nivel-badge" style="width:20px;height:20px;font-size:.7rem">3</span><span>Coordinador Académico / Orientador / Secretaria</span>
                    <span class="nivel-badge" style="width:20px;height:20px;font-size:.7rem">4</span><span>Docente / Apoyo académico y administrativo</span>
                    <span class="nivel-badge" style="width:20px;height:20px;font-size:.7rem">5</span><span>Padre de familia / Alumno (solo lectura)</span>
                  </div>
                </div>
                <div class="field" style="display:flex;flex-direction:column;gap:.35rem">
                  <label class="dlg-lbl">Descripción</label>
                  <textarea pInputTextarea [(ngModel)]="rolEditForm.descripcion" rows="3"
                    style="width:100%;resize:vertical"></textarea>
                </div>
              </div>
              <ng-template pTemplate="footer">
                <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="rolDlgVisible.set(false)" />
                <p-button label="Guardar" icon="pi pi-save" [loading]="guardandoRol()" (onClick)="guardarRol()" />
              </ng-template>
            }
          </p-dialog>
        </p-tabpanel>

        <!-- ══ MENÚS ═══════════════════════════════════════════════════════ -->
        <p-tabpanel value="menus">
          <div class="tab-toolbar">
            <span class="tab-title">Configuración de menús del sidebar</span>
            <small style="color:var(--text-color-secondary)">
              Nivel máximo: 0=solo Admin Global, 4=Docentes, 99=Todos. Nivel mínimo: 5=solo Padres/Alumnos.
            </small>
          </div>
          <app-interactive-grid
            [data]="menus()"
            [columns]="columnasMenus"
            [loading]="loadingMenus()"
            (rowSelected)="abrirEditarMenu($event)"
          />
          <p-dialog [visible]="menuDlgVisible()" (visibleChange)="menuDlgVisible.set($event)"
            header="Editar Menú" [modal]="true" [draggable]="false" [style]="{width:'440px'}">
            @if (menuEdit()) {
              <div style="display:flex;flex-direction:column;gap:.75rem">
                <div class="field"><span class="dlg-lbl">Clave</span>
                  <code>{{ menuEdit()!.clave }}</code></div>
                <div class="field" style="display:flex;flex-direction:column;gap:.35rem">
                  <label class="dlg-lbl">Etiqueta</label>
                  <input pInputText [(ngModel)]="menuEditForm.label" />
                </div>
                <div class="field" style="display:flex;flex-direction:column;gap:.35rem">
                  <label class="dlg-lbl">Nivel máximo (0–99)</label>
                  <p-inputNumber [(ngModel)]="menuEditForm.nivel_maximo" [min]="0" [max]="99" [showButtons]="true" />
                  <small>0 = solo Admin Global · 1 = Admin Plantel · 2 = Director · 3 = Coord · 4 = Docente · 99 = Todos</small>
                </div>
                <div class="field" style="display:flex;flex-direction:column;gap:.35rem">
                  <label class="dlg-lbl">Nivel mínimo (0–99)</label>
                  <p-inputNumber [(ngModel)]="menuEditForm.nivel_minimo" [min]="0" [max]="99" [showButtons]="true" />
                  <small>0 = todos · 5 = solo Padres/Alumnos</small>
                </div>
                <div class="field" style="display:flex;align-items:center;gap:.5rem">
                  <p-toggleswitch [(ngModel)]="menuEditForm.activo" inputId="menu-activo" />
                  <label for="menu-activo">Menú activo (visible en sidebar)</label>
                </div>
              </div>
              <ng-template pTemplate="footer">
                <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="menuDlgVisible.set(false)" />
                <p-button label="Guardar" icon="pi pi-save" [loading]="guardandoMenu()" (onClick)="guardarMenu()" />
              </ng-template>
            }
          </p-dialog>
        </p-tabpanel>

        <!-- ══ PERMISOS POR ROL ═══════════════════════════════════════════ -->
        <p-tabpanel value="permisos">
          <div class="tab-toolbar">
            <span class="tab-title">Permisos por rol y módulo</span>
            <div style="display:flex;gap:.5rem;align-items:center">
              <label class="dlg-lbl">Filtrar por rol:</label>
              <p-select [options]="roles()" [(ngModel)]="permisosRolFiltro"
                optionLabel="nombre_rol" optionValue="id"
                placeholder="Todos los roles" [showClear]="true"
                (onChange)="cargarPermisos()"
                style="min-width:200px" />
            </div>
          </div>

          <!-- Matriz permisos estilo APEX -->
          <div class="permisos-matrix">
            <table class="pm-table">
              <thead>
                <tr>
                  <th style="width:22%">Módulo</th>
                  <th style="width:18%">Rol</th>
                  <th class="pm-check">Ver</th>
                  <th class="pm-check">Editar</th>
                  <th class="pm-check">Crear</th>
                  <th class="pm-check">Eliminar</th>
                  <th style="width:80px"></th>
                </tr>
              </thead>
              <tbody>
                @if (loadingPermisos()) {
                  <tr><td colspan="7" style="text-align:center;padding:2rem">Cargando...</td></tr>
                } @else if (permisos().length === 0) {
                  <tr><td colspan="7" style="text-align:center;padding:2rem;color:var(--text-color-secondary)">
                    Selecciona un rol para ver sus permisos configurados.<br>
                    <small>Los módulos sin configuración usan el acceso por nivel_acceso del menú.</small>
                  </td></tr>
                }
                @for (p of permisos(); track p.id) {
                  <tr>
                    <td><code style="font-size:.8rem">{{ p.menu_clave }}</code><br>
                      <small>{{ p.label }}</small></td>
                    <td><span class="nivel-badge">{{ p.nombre_rol }}</span></td>
                    <td class="pm-check">
                      <p-toggleswitch [(ngModel)]="p.puede_ver" (onChange)="guardarPermiso(p)" />
                    </td>
                    <td class="pm-check">
                      <p-toggleswitch [(ngModel)]="p.puede_editar" (onChange)="guardarPermiso(p)" />
                    </td>
                    <td class="pm-check">
                      <p-toggleswitch [(ngModel)]="p.puede_crear" (onChange)="guardarPermiso(p)" />
                    </td>
                    <td class="pm-check">
                      <p-toggleswitch [(ngModel)]="p.puede_eliminar" (onChange)="guardarPermiso(p)" />
                    </td>
                    <td><p-button icon="pi pi-save" size="small" [text]="true"
                      [loading]="p._saving" (onClick)="guardarPermiso(p)" /></td>
                  </tr>
                }
              </tbody>
            </table>

            <div class="pm-add">
              <p-button label="Agregar permiso" icon="pi pi-plus" size="small" severity="secondary"
                [disabled]="!permisosRolFiltro"
                (onClick)="abrirNuevoPermiso()" />
            </div>
          </div>

          <!-- Dialog nuevo permiso -->
          <p-dialog [visible]="nuevoPrmDlgVisible()" (visibleChange)="nuevoPrmDlgVisible.set($event)"
            header="Agregar permiso de rol" [modal]="true" [draggable]="false" [style]="{width:'420px'}">
            <div style="display:flex;flex-direction:column;gap:.75rem">
              <div class="field" style="display:flex;flex-direction:column;gap:.35rem">
                <label class="dlg-lbl">Módulo (clave del menú)</label>
                <p-select [options]="menus()" [(ngModel)]="nuevoPrmForm.menu_clave"
                  optionLabel="clave" optionValue="clave"
                  [filter]="true" filterPlaceholder="Buscar módulo..." appendTo="body"/>
              </div>
              <div style="display:flex;gap:1rem;flex-wrap:wrap">
                <label style="display:flex;align-items:center;gap:.4rem">
                  <p-toggleswitch [(ngModel)]="nuevoPrmForm.puede_ver" /> Ver
                </label>
                <label style="display:flex;align-items:center;gap:.4rem">
                  <p-toggleswitch [(ngModel)]="nuevoPrmForm.puede_editar" /> Editar
                </label>
                <label style="display:flex;align-items:center;gap:.4rem">
                  <p-toggleswitch [(ngModel)]="nuevoPrmForm.puede_crear" /> Crear
                </label>
                <label style="display:flex;align-items:center;gap:.4rem">
                  <p-toggleswitch [(ngModel)]="nuevoPrmForm.puede_eliminar" /> Eliminar
                </label>
              </div>
            </div>
            <ng-template pTemplate="footer">
              <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="nuevoPrmDlgVisible.set(false)" />
              <p-button label="Guardar" icon="pi pi-save" [loading]="guardandoPermiso()" (onClick)="guardarNuevoPermiso()" />
            </ng-template>
          </p-dialog>
        </p-tabpanel>

        <!-- ══ GEOGRÁFICOS (SEPOMEX) ═══════════════════════════════════════ -->
        <p-tabpanel value="geo">
          <div style="max-width: 680px; margin-top: 1rem; display: flex; flex-direction: column; gap: 1.75rem;">

            <!-- Estadísticas del catálogo -->
            <div>
              <h3 style="margin:0 0 .75rem">Catálogo geográfico cargado</h3>
              @if (loadingSepomexStats()) {
                <p style="font-size:.85rem;color:var(--text-color-secondary)">
                  <i class="pi pi-spin pi-spinner" style="margin-right:.4rem"></i>Cargando estadísticas…
                </p>
              } @else if (sepomexStats()) {
                <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:.75rem">
                  @for (stat of [
                    { label: 'Estados',           value: sepomexStats()!.estados,    icon: 'pi-map' },
                    { label: 'Municipios',         value: sepomexStats()!.municipios, icon: 'pi-building' },
                    { label: 'Colonias / Asent.', value: sepomexStats()!.colonias,   icon: 'pi-home' },
                    { label: 'CPs únicos',         value: sepomexStats()!.cps_unicos, icon: 'pi-tag' }
                  ]; track stat.label) {
                    <div style="background:var(--surface-card);border:1px solid var(--surface-border);border-radius:8px;padding:.75rem 1rem;display:flex;flex-direction:column;gap:.2rem">
                      <div style="display:flex;align-items:center;gap:.5rem;color:var(--text-color-secondary);font-size:.78rem">
                        <i class="pi {{ stat.icon }}" style="font-size:.85rem"></i>
                        {{ stat.label }}
                      </div>
                      <span style="font-size:1.4rem;font-weight:700;color:var(--primary-color)">
                        {{ stat.value | number }}
                      </span>
                    </div>
                  }
                </div>
              } @else {
                <p style="font-size:.85rem;color:var(--text-color-secondary)">Sin datos — sincroniza el catálogo primero.</p>
              }
            </div>

            <!-- Sincronización SEPOMEX -->
            <div class="sync-card">
              <div class="sync-header">
                <div>
                  <h3 style="margin:0 0 .25rem">Catálogo SEPOMEX</h3>
                  <p style="margin:0;font-size:.82rem;color:var(--text-color-secondary)">
                    Descarga la versión más reciente de Correos de México e importa estados, municipios y códigos postales.
                  </p>
                </div>
                <div style="flex-shrink:0">
                <p-button label="Sincronizar" icon="pi pi-refresh"
                  severity="primary"
                  [loading]="syncSepomexLoading()"
                  [disabled]="syncSepomexLoading() || syncSepomexEstado() === 'STARTED' || syncSepomexEstado() === 'PENDING'"
                  pTooltip="Descarga e importa el catálogo desde correosdemexico.gob.mx"
                  (onClick)="iniciarSyncSepomex()" />
              </div>
              </div>

              @if (syncSepomexTaskId()) {
                <div class="sync-estado" [class]="'sync-' + (syncSepomexEstado() || 'PENDING').toLowerCase()">
                  <i class="pi"
                    [class.pi-spin]="syncSepomexEstado() === 'STARTED' || syncSepomexEstado() === 'PENDING'"
                    [class.pi-spinner]="syncSepomexEstado() === 'STARTED' || syncSepomexEstado() === 'PENDING'"
                    [class.pi-check-circle]="syncSepomexEstado() === 'SUCCESS'"
                    [class.pi-times-circle]="syncSepomexEstado() === 'FAILURE'">
                  </i>
                  <span>
                    @switch (syncSepomexEstado()) {
                      @case ('PENDING') { Encolado — esperando worker disponible… }
                      @case ('STARTED') { Descargando e importando catálogo SEPOMEX… }
                      @case ('SUCCESS') { Sincronización completada. {{ syncSepomexResultado() }} }
                      @case ('FAILURE') { Error: {{ syncSepomexError() }} }
                      @default { Estado: {{ syncSepomexEstado() }} }
                    }
                  </span>
                </div>
              }
            </div>

            <!-- Verificar autocompletado -->
            <div>
              <h3 style="margin:0 0 .5rem">Probar Autocompletado</h3>
              <p style="margin:0 0 1rem;font-size:.82rem;color:var(--text-color-secondary)">
                Escribe un código postal de 5 dígitos (ej. 50100) y presiona Enter.
              </p>
              <app-selector-geo></app-selector-geo>
            </div>
          </div>
        </p-tabpanel>

        <!-- ══ CICLOS ══════════════════════════════════════════════════════ -->
        <p-tabpanel value="ciclos">
          <div class="tab-toolbar">
            <p-button label="Nuevo ciclo" icon="pi pi-plus"
              severity="primary" (onClick)="abrirNuevoCiclo()" />
          </div>
          <app-interactive-grid
            [data]="ciclos()"
            [columns]="columnasCiclos"
            [loading]="loadingCiclos()"
            (rowSelected)="abrirEditarCiclo($event)"
          />
        </p-tabpanel>

        <!-- ══ PLANTELES ═════════════════════════════════════════════════════ -->
        <p-tabpanel value="planteles">
          <app-interactive-grid
            [data]="planteles()"
            [columns]="columnasPlanteles"
            [loading]="loadingPlanteles()"
            (rowSelected)="abrirEditarPlantel($event)"
          />
        </p-tabpanel>

        <!-- ══ GRUPOS ════════════════════════════════════════════════════════ -->
        <p-tabpanel value="grupos">
          <div class="tab-toolbar">
            <p-select [options]="ciclosFiltro()" [(ngModel)]="cicloFiltroId"
              optionLabel="nombre_ciclo" optionValue="id"
              placeholder="Filtrar por ciclo" [showClear]="true"
              [filter]="true" filterPlaceholder="Buscar..."
              (onChange)="cargarGrupos()" styleClass="ctx-selector" />
            <p-button label="Nuevo grupo" icon="pi pi-plus"
              severity="primary" (onClick)="abrirNuevoGrupo()" />
          </div>
          <app-interactive-grid
            [data]="grupos()"
            [columns]="columnasGrupos"
            [loading]="loadingGrupos()"
            (rowSelected)="abrirEditarGrupo($event)"
          />
        </p-tabpanel>

        <!-- ══ VARIABLES DEL SISTEMA ════════════════════════════════════════ -->
        <p-tabpanel value="variables">
          <div class="tab-toolbar">
            <p-select
              [options]="gruposFiltroOpts()"
              [(ngModel)]="grupoSeleccionado"
              placeholder="Todos los grupos"
              [showClear]="true"
              [filter]="true" filterPlaceholder="Buscar grupo..."
              (onChange)="cargarVariables()"
              styleClass="ctx-selector" />
            <p-button label="Nueva variable" icon="pi pi-plus" size="small"
              (onClick)="abrirNuevaVariable()" />
          </div>

          @for (grupo of gruposActuales(); track grupo) {
            <div class="var-grupo-header">
              <i class="pi pi-cog" style="margin-right:.4rem"></i>{{ grupo }}
            </div>
            <app-interactive-grid
              [data]="variablesPorGrupoFlat()[grupo] ?? []"
              [columns]="variablesColumns"
              [loading]="loadingVariables()"
              (rowSelected)="abrirEditarVariable($event)"
            />
          }

          @if (variables().length === 0 && !loadingVariables()) {
            <div class="empty-msg">No hay variables del sistema registradas.</div>
          }
        </p-tabpanel>

        <!-- ══ CATÁLOGOS DINÁMICOS ══════════════════════════════════════════ -->
        <p-tabpanel value="catalogos">
          <div style="display:grid;grid-template-columns:290px 1fr;gap:1rem;min-height:500px">

            <!-- Panel izquierdo: lista de catálogos -->
            <div class="cat-panel">
              <div style="padding:.5rem;border-bottom:1px solid var(--surface-border)">
                <p-button label="Nuevo catálogo" icon="pi pi-plus" size="small"
                  styleClass="w-full" (onClick)="abrirNuevoCatalogo()" />
              </div>
              @for (cat of catalogos(); track cat.id) {
                <div class="cat-item" [class.cat-activo]="catSeleccionado()?.id === cat.id"
                  (click)="seleccionarCatalogo(cat)">
                  <div>
                    <code class="cat-codigo">{{ cat.codigo }}</code>
                    <div class="cat-nombre">{{ cat.nombre }}</div>
                  </div>
                  <p-tag [value]="cat.items.length + ''" severity="secondary"
                    pTooltip="{{ cat.items.length }} items" />
                </div>
              }
              @if (catalogos().length === 0 && !loadingCatalogos()) {
                <div class="empty-msg" style="padding:1rem">Sin catálogos</div>
              }
            </div>

            <!-- Panel derecho: items del catálogo seleccionado -->
            <div>
              @if (catSeleccionado()) {
                <div class="tab-toolbar">
                  <div>
                    <strong>{{ catSeleccionado()!.nombre }}</strong>
                    <code style="margin-left:.5rem;font-size:.8rem;color:var(--text-secondary)">
                      {{ catSeleccionado()!.codigo }}
                    </code>
                  </div>
                  <p-button label="Agregar item" icon="pi pi-plus" size="small"
                    (onClick)="abrirNuevoItem()" />
                </div>
                <app-interactive-grid
                  [data]="catItemsFlat()"
                  [columns]="catItemsColumns"
                  [loading]="loadingCatalogos()"
                  (rowSelected)="abrirEditarItem($event)"
                />
              } @else {
                <div class="empty-msg" style="height:100%;display:flex;align-items:center;justify-content:center">
                  <div style="text-align:center">
                    <i class="pi pi-box" style="font-size:2rem;display:block;margin-bottom:.5rem;color:var(--text-secondary)"></i>
                    Selecciona un catálogo para ver sus valores
                  </div>
                </div>
              }
            </div>
          </div>
        </p-tabpanel>

        <!-- ══ MARCA / IDENTIDAD ═════════════════════════════════════════════ -->
        <p-tabpanel value="marca">
          <div class="marca-container" style="max-width:600px; margin: 1.5rem auto; display:flex; flex-direction:column; gap:1.25rem; background:var(--surface-card); padding:2rem; border-radius:10px; border:1px solid var(--surface-border)">
            <div style="display:flex; align-items:center; gap:.75rem; border-bottom:1px solid var(--surface-border); padding-bottom:1rem">
              <i class="pi pi-palette" style="font-size:1.75rem; color:var(--primary-color)"></i>
              <div>
                <h3 style="margin:0">Identidad Institucional</h3>
                <span style="font-size:.8rem; color:var(--text-secondary)">Personaliza el logotipo, colores y textos institucionales.</span>
              </div>
            </div>

            @if (loadingMarca()) {
              <div style="display:flex; flex-direction:column; gap:1rem">
                <p-skeleton height="2.5rem" />
                <p-skeleton height="2.5rem" />
                <p-skeleton height="2.5rem" />
                <p-skeleton height="2.5rem" />
              </div>
            } @else {
              <div class="form-grid1" style="display:flex; flex-direction:column; gap:1rem">
                <div class="field" style="display:flex; flex-direction:column; gap:.35rem">
                  <label class="dlg-lbl">Nombre de la Institución</label>
                  <input pInputText [(ngModel)]="marcaForm.NOMBRE_INSTITUCION" placeholder="Ej. Instituto Nevadi" />
                </div>
                <div class="field" style="display:flex; flex-direction:column; gap:.35rem">
                  <label class="dlg-lbl">Eslogan / Slogan</label>
                  <input pInputText [(ngModel)]="marcaForm.ESLOGAN" placeholder="Ej. Calidad y Futuro" />
                </div>
                <div class="field" style="display:flex; flex-direction:column; gap:.35rem">
                  <label class="dlg-lbl">URL del Logotipo</label>
                  <input pInputText [(ngModel)]="marcaForm.LOGO_URL" placeholder="https://..." />
                </div>
                <div class="field" style="display:flex; flex-direction:column; gap:.35rem">
                  <label class="dlg-lbl">URL del Favicon</label>
                  <input pInputText [(ngModel)]="marcaForm.FAVICON_URL" placeholder="https://..." />
                </div>
                <div class="field" style="display:flex; flex-direction:column; gap:.35rem">
                  <label class="dlg-lbl">Color Primario (Hex)</label>
                  <div style="display:flex; gap:.5rem; align-items:center">
                    <input type="color" [(ngModel)]="marcaForm.COLOR_PRIMARIO" style="width:42px; height:42px; padding:0; border-radius:6px; border:1px solid #ddd; cursor:pointer" />
                    <input pInputText [(ngModel)]="marcaForm.COLOR_PRIMARIO" placeholder="#D02030" style="flex:1" />
                  </div>
                </div>
              </div>

              <div style="display:flex; justify-content:flex-end; gap:.5rem; margin-top:1rem; border-top:1px solid var(--surface-border); padding-top:1rem">
                <p-button label="Guardar Marca" icon="pi pi-save" [loading]="guardandoMarca()" (onClick)="guardarMarca()" />
              </div>
            }
          </div>
        </p-tabpanel>

        <!-- ══ REGLAS DE PROMOCIÓN ═══════════════════════════════════════════ -->
        <p-tabpanel value="reglas">
          <div class="tab-toolbar" style="margin-bottom:1rem">
            <h3 style="margin:0">Reglas de Promoción por Nivel Educativo</h3>
            <p-button label="Evaluar Promoción" icon="pi pi-play-circle" severity="contrast"
              [loading]="evaluandoPromocion()"
              (onClick)="evaluarPromocion()"
              pTooltip="Marca cada alumno ACTIVO como PROMOVIDO o REPROBADO según estas reglas. Ejecutar ANTES de cerrar el ciclo." />
          </div>

          @if (loadingReglas()) {
            <div style="text-align:center;padding:2rem"><i class="pi pi-spin pi-spinner" style="font-size:1.5rem"></i></div>
          } @else {
            <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:1.5rem">
              @for (nivel of nivelesReglas(); track nivel.id) {
                <div class="regla-card">
                  <div class="regla-card-header">
                    <span class="regla-titulo">{{ nivel.nombre_nivel }}</span>
                    <small class="regla-sub">{{ nivel.autoridad_educativa }} · Escala 0 – {{ nivel.escala_maxima }}</small>
                  </div>

                  <div class="regla-fields">
                    <div class="regla-field">
                      <label>Calificación mínima aprobatoria</label>
                      <input pInputText type="number" step="0.5" min="0" [max]="nivel.escala_maxima"
                        [(ngModel)]="nivel.minimo_aprobatorio" style="width:100px" />
                    </div>
                    <div class="regla-field">
                      <label>Máx. materias reprobadas</label>
                      <input pInputText type="number" step="1" min="0" max="10"
                        [(ngModel)]="nivel.max_materias_reprobadas" style="width:80px" />
                    </div>
                    <div class="regla-field">
                      <label>Asistencia mínima requerida (%)</label>
                      <input pInputText type="number" step="1" min="0" max="100"
                        [(ngModel)]="nivel.min_asistencia_pct" style="width:90px" />
                    </div>
                    <div class="regla-field">
                      <label>Máx. años de recursamiento</label>
                      <input pInputText type="number" step="1" min="0" max="5"
                        [(ngModel)]="nivel.max_anios_reprobados" style="width:80px" />
                    </div>
                    <div class="regla-field regla-field--toggle">
                      <label>Permite recursamiento</label>
                      <p-toggleswitch [(ngModel)]="nivel.permite_recursamiento" />
                    </div>
                    <div class="regla-field regla-field--toggle">
                      <label>Tiene examen extraordinario</label>
                      <p-toggleswitch [(ngModel)]="nivel.tiene_examen_extra" />
                    </div>
                  </div>

                  <div style="text-align:right;margin-top:.75rem">
                    <p-button label="Guardar" icon="pi pi-save" size="small"
                      [loading]="guardandoRegla()"
                      (onClick)="actualizarRegla(nivel)" />
                  </div>
                </div>
              }
            </div>
          }
        </p-tabpanel>

        <!-- ══ FRANJAS HORARIAS ════════════════════════════════════════════════ -->
        <p-tabpanel value="franjas">
          <div class="tab-toolbar" style="margin-bottom:1rem; display:flex; justify-content:space-between; align-items:center;">
            <div>
              <h3 style="margin:0">Franjas Horarias del Motor</h3>
              <p style="margin:0; font-size:0.9rem; color:var(--text-color-secondary)">
                Define los bloques de tiempo que el motor usará para asignar clases, por nivel educativo o plantel.
              </p>
            </div>
            <p-button label="Nueva Franja" icon="pi pi-plus" (onClick)="abrirFranjaDialog()" />
          </div>

          <p-table [value]="franjas()" [loading]="loadingFranjas()" [tableStyle]="{'min-width': '50rem'}">
            <ng-template pTemplate="header">
              <tr>
                <th>Día</th>
                <th>Turno</th>
                <th>Hora Inicio</th>
                <th>Hora Fin</th>
                <th>Nivel</th>
                <th>Plantel</th>
                <th>Activo</th>
                <th style="width:5rem"></th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-f>
              <tr>
                <td>{{ f.dia_semana }}</td>
                <td>{{ f.turno }}</td>
                <td>{{ String(f.hora_inicio || '').slice(0,5) }}</td>
                <td>{{ String(f.hora_fin || '').slice(0,5) }}</td>
                <td>{{ f.nivel_educativo_id ? (nivelesMap()[f.nivel_educativo_id] ?? f.nivel_educativo_id) : 'Todos' }}</td>
                <td>{{ f.plantel_id ? 'Específico' : 'Todos' }}</td>
                <td>
                  <p-tag [severity]="f.is_active ? 'success' : 'danger'" [value]="f.is_active ? 'SÍ' : 'NO'"></p-tag>
                </td>
                <td>
                  <p-button icon="pi pi-pencil" [text]="true" (onClick)="abrirFranjaDialog(f)" />
                  <p-button icon="pi pi-trash" [text]="true" severity="danger" (onClick)="eliminarFranja(f)" />
                </td>
              </tr>
            </ng-template>
            <ng-template pTemplate="emptymessage">
              <tr><td colspan="8">No hay franjas configuradas.</td></tr>
            </ng-template>
          </p-table>

          <p-dialog
            [visible]="showFranjaDialog()"
            (visibleChange)="showFranjaDialog.set($event)"
            [header]="editFranjaEntry() ? 'Editar Franja' : 'Nueva Franja'"
            [modal]="true" [style]="{width:'400px'}">
            
            <div style="display:flex; flex-direction:column; gap:1rem; padding-top:1rem">
              <div class="field">
                <label>Día de la semana (1-7)</label>
                <p-select [options]="[{l:'Lunes',v:1},{l:'Martes',v:2},{l:'Miércoles',v:3},{l:'Jueves',v:4},{l:'Viernes',v:5}]" 
                  optionLabel="l" optionValue="v" [(ngModel)]="franjaForm.dia_semana" appendTo="body" styleClass="w-full"></p-select>
              </div>
              <div class="field" style="display:flex; gap:1rem">
                <div style="flex:1">
                  <label>Hora Inicio</label>
                  <input pInputText type="time" [(ngModel)]="franjaForm.hora_inicio" class="w-full" />
                </div>
                <div style="flex:1">
                  <label>Hora Fin</label>
                  <input pInputText type="time" [(ngModel)]="franjaForm.hora_fin" class="w-full" />
                </div>
              </div>
              <div class="field">
                <label>Turno</label>
                <input pInputText type="text" [(ngModel)]="franjaForm.turno" class="w-full" />
              </div>
              <div class="field">
                <label>Nivel Educativo</label>
                <p-select [options]="niveles()" optionLabel="nombre_nivel" optionValue="id"
                  [(ngModel)]="franjaForm.nivel_educativo_id" appendTo="body" styleClass="w-full"
                  [showClear]="true" placeholder="Todos los niveles"></p-select>
              </div>
              <div class="field" style="display:flex; align-items:center; gap:0.5rem; margin-top:0.5rem">
                <p-toggleswitch [(ngModel)]="franjaForm.is_active" inputId="fAct"></p-toggleswitch>
                <label for="fAct" style="margin:0">Activo</label>
              </div>
            </div>

            <ng-template pTemplate="footer">
              <p-button label="Cancelar" [text]="true" severity="secondary" (onClick)="showFranjaDialog.set(false)" />
              <p-button label="Guardar" icon="pi pi-check" (onClick)="guardarFranja()" [loading]="guardandoFranja()" />
            </ng-template>
          </p-dialog>
        </p-tabpanel>

        <!-- ══ AUDITORÍA ═════════════════════════════════════════════════════ -->
        <!-- ══ EVALUACIÓN CUALITATIVA NEM ═════════════════════════════════════ -->
        <p-tabpanel value="eval-cualitativa">
          @if (loadingCual()) {
            <div style="display:flex;gap:.5rem;flex-direction:column;padding:1rem">
              <p-skeleton height="2.5rem" /><p-skeleton height="2.5rem" /><p-skeleton height="2.5rem" />
            </div>
          } @else {
            <!-- Sección 1: Config switches -->
            <div style="margin-bottom:1.5rem">
              <h3 style="margin:0 0 .75rem;font-size:1rem;font-weight:600;color:var(--text-primary)">
                <i class="pi pi-cog" style="margin-right:.4rem"></i>Configuración
              </h3>
              <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:1rem">

                <div class="cual-config-card">
                  <label class="cual-config-label">Grados con evaluación cualitativa</label>
                  <small class="cual-config-hint">Grados de primaria (números separados por coma)</small>
                  <input pInputText
                    [(ngModel)]="cualGradosStr"
                    placeholder="ej: 1, 2"
                    style="margin-top:.5rem;width:100%" />
                  <p-button label="Guardar" size="small" styleClass="mt-2"
                    (onClick)="guardarConfigCual('EVAL_CUAL_GRADOS_PRIMARIA', parseCualGrados())" />
                </div>

                <div class="cual-config-card">
                  <label class="cual-config-label">Mostrar equivalencia numérica</label>
                  <small class="cual-config-hint">Muestra el número equivalente junto al descriptor en libreta y boleta</small>
                  <div style="margin-top:.75rem;display:flex;align-items:center;gap:.5rem">
                    <p-toggleSwitch [(ngModel)]="cualMostrarEquiv"
                      (onChange)="guardarConfigCual('EVAL_CUAL_MOSTRAR_EQUIVALENCIA', cualMostrarEquiv)" />
                    <span>{{ cualMostrarEquiv ? 'Sí' : 'No' }}</span>
                  </div>
                </div>

                <div class="cual-config-card">
                  <label class="cual-config-label">Aplicar a todas las materias</label>
                  <small class="cual-config-hint">Si está desactivado, solo aplica a materias con campo formativo configurado</small>
                  <div style="margin-top:.75rem;display:flex;align-items:center;gap:.5rem">
                    <p-toggleSwitch [(ngModel)]="cualTodasMaterias"
                      (onChange)="guardarConfigCual('EVAL_CUAL_APLICAR_TODAS_MATERIAS', cualTodasMaterias)" />
                    <span>{{ cualTodasMaterias ? 'Sí' : 'No' }}</span>
                  </div>
                </div>

              </div>
            </div>

            <!-- Sección 2: Escala de descriptores -->
            <div>
              <h3 style="margin:0 0 .75rem;font-size:1rem;font-weight:600;color:var(--text-primary)">
                <i class="pi pi-list" style="margin-right:.4rem"></i>Escala de Descriptores NEM — Primaria
              </h3>
              @for (escala of cualEscalas(); track escala.id) {
                <div style="margin-bottom:1rem">
                  <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:.5rem">
                    <span style="font-weight:600;font-size:.9rem">{{ escala.nombre }}</span>
                    <p-tag [value]="escala.nivel_educativo" severity="info" />
                  </div>
                  <p-table [value]="escala._descriptores ?? []" styleClass="p-datatable-sm p-datatable-gridlines"
                    [editMode]="'cell'">
                    <ng-template pTemplate="header">
                      <tr>
                        <th style="width:50px">Nivel</th>
                        <th style="width:160px">Etiqueta</th>
                        <th>Descripción</th>
                        <th style="width:80px">Mín</th>
                        <th style="width:80px">Máx</th>
                        <th style="width:90px">Equiv. Num.</th>
                      </tr>
                    </ng-template>
                    <ng-template pTemplate="body" let-d>
                      <tr>
                        <td>
                          <span class="nivel-badge" [style.background]="d.color">{{ d.nivel }}</span>
                        </td>
                        <td [pEditableColumn]="d.label" pEditableColumnField="label">
                          <p-cellEditor>
                            <ng-template pTemplate="input">
                              <input pInputText [(ngModel)]="d.label" style="width:100%"
                                (blur)="marcarEscalaCambio(escala)" />
                            </ng-template>
                            <ng-template pTemplate="output">{{ d.label }}</ng-template>
                          </p-cellEditor>
                        </td>
                        <td [pEditableColumn]="d.descripcion" pEditableColumnField="descripcion">
                          <p-cellEditor>
                            <ng-template pTemplate="input">
                              <input pInputText [(ngModel)]="d.descripcion" style="width:100%"
                                (blur)="marcarEscalaCambio(escala)" />
                            </ng-template>
                            <ng-template pTemplate="output">
                              <span style="font-size:.8rem">{{ d.descripcion }}</span>
                            </ng-template>
                          </p-cellEditor>
                        </td>
                        <td [pEditableColumn]="d.min" pEditableColumnField="min" style="text-align:center">
                          <p-cellEditor>
                            <ng-template pTemplate="input">
                              <p-inputNumber [(ngModel)]="d.min" [min]="0" [max]="10" [maxFractionDigits]="1"
                                [inputStyle]="{width:'60px'}" (onBlur)="marcarEscalaCambio(escala)" />
                            </ng-template>
                            <ng-template pTemplate="output">{{ d.min }}</ng-template>
                          </p-cellEditor>
                        </td>
                        <td [pEditableColumn]="d.max" pEditableColumnField="max" style="text-align:center">
                          <p-cellEditor>
                            <ng-template pTemplate="input">
                              <p-inputNumber [(ngModel)]="d.max" [min]="0" [max]="10" [maxFractionDigits]="1"
                                [inputStyle]="{width:'60px'}" (onBlur)="marcarEscalaCambio(escala)" />
                            </ng-template>
                            <ng-template pTemplate="output">{{ d.max }}</ng-template>
                          </p-cellEditor>
                        </td>
                        <td [pEditableColumn]="d.equiv_num" pEditableColumnField="equiv_num" style="text-align:center">
                          <p-cellEditor>
                            <ng-template pTemplate="input">
                              <p-inputNumber [(ngModel)]="d.equiv_num" [min]="0" [max]="10" [maxFractionDigits]="1"
                                [inputStyle]="{width:'60px'}" (onBlur)="marcarEscalaCambio(escala)" />
                            </ng-template>
                            <ng-template pTemplate="output">
                              <strong>{{ d.equiv_num }}</strong>
                            </ng-template>
                          </p-cellEditor>
                        </td>
                      </tr>
                    </ng-template>
                  </p-table>
                  @if (escala._modificado) {
                    <div style="margin-top:.5rem;display:flex;gap:.5rem">
                      <p-button label="Guardar descriptores" icon="pi pi-save" size="small"
                        (onClick)="guardarEscala(escala)" [loading]="guardandoCual()" />
                      <p-button label="Cancelar" severity="secondary" size="small" [text]="true"
                        (onClick)="cargarConfigCualitativa()" />
                    </div>
                  }
                </div>
              }
            </div>
          }
        </p-tabpanel>

        <p-tabpanel value="auditoria">
          <div style="padding:2rem;text-align:center;color:var(--text-secondary)">
            <i class="pi pi-shield" style="font-size:2rem;display:block;margin-bottom:1rem"></i>
            Logs de auditoría — Por implementar en FASE 13
          </div>
        </p-tabpanel>
      </p-tabpanels>
    </p-tabs>

    <!-- Dialog editar variable -->
    <p-dialog [visible]="variableDlgVisible()" (visibleChange)="variableDlgVisible.set($event)"
      [header]="variableEdit ? variableEdit.nombre : 'Variable del Sistema'"
      [modal]="true" [style]="{width:'540px'}" [closable]="true">
      @if (variableEdit) {
        <div style="display:flex;flex-direction:column;gap:1rem">
          @if (variableEdit.descripcion) {
            <p style="margin:0;font-size:.85rem;color:var(--text-secondary)">{{ variableEdit.descripcion }}</p>
          }

          @switch (variableEdit.tipo_valor) {
            @case ('TEXTO') {
              <input pInputText [(ngModel)]="variableEdit._valorEdit" style="width:100%" />
            }
            @case ('PASSWORD') {
              <input type="password" pInputText [(ngModel)]="variableEdit._valorEdit"
                placeholder="Ingrese nuevo valor secreto" style="width:100%" />
              <small style="color:var(--text-secondary)">El valor actual está enmascarado por seguridad.</small>
            }
            @case ('BOOLEANO') {
              <div style="display:flex;align-items:center;gap:.75rem">
                <p-toggleswitch
                  [(ngModel)]="variableEdit._valorBool"
                  (onChange)="onToggleBool($event)" />
                <span>{{ variableEdit._valorEdit === 'true' ? 'Activo' : 'Inactivo' }}</span>
              </div>
            }
            @case ('NUMERO') {
              <p-inputNumber
                [(ngModel)]="variableEdit._valorNum"
                (onInput)="variableEdit._valorEdit = $event.value?.toString() ?? ''"
                styleClass="w-full" />
            }
            @case ('JSON') {
              <textarea pInputText [(ngModel)]="variableEdit._valorEdit"
                rows="8" style="width:100%;font-family:monospace;font-size:.8rem"
                placeholder='{"clave": "valor"}'></textarea>
              <small [style.color]="esJsonValido(variableEdit._valorEdit ?? '') ? 'var(--green-600)' : 'var(--red-600)'">
                {{ esJsonValido(variableEdit._valorEdit ?? '') ? '✓ JSON válido' : '✗ JSON inválido' }}
              </small>
            }
            @case ('FECHA') {
              <p-datepicker [(ngModel)]="variableEdit._valorDate"
                (onSelect)="onVariableDateSelect($event)"
                dateFormat="dd/mm/yy" styleClass="w-full" />
            }
            @case ('HORA') {
              <p-datepicker [(ngModel)]="variableEdit._valorDate"
                (onSelect)="onVariableDateSelect($event)"
                [timeOnly]="true" hourFormat="24" styleClass="w-full" />
            }
          }

          <div style="display:flex;gap:.5rem;justify-content:flex-end">
            <p-button label="Cancelar" severity="secondary" [text]="true"
              (onClick)="variableDlgVisible.set(false)" />
            <p-button label="Guardar" icon="pi pi-save"
              [disabled]="variableEdit.tipo_valor === 'JSON' && !esJsonValido(variableEdit._valorEdit ?? '')"
              [loading]="guardandoVariable()"
              (onClick)="guardarVariable()" />
          </div>
        </div>
      }
    </p-dialog>

    <!-- Dialog nuevo catálogo -->
    <p-dialog [visible]="catalogoDlgVisible()" (visibleChange)="catalogoDlgVisible.set($event)" header="Nuevo Catálogo"
      [modal]="true" [style]="{width:'420px'}">
      <div style="display:flex;flex-direction:column;gap:1rem">
        <div>
          <label class="dlg-lbl">Código único *</label>
          <input pInputText [(ngModel)]="catalogoForm.codigo"
            placeholder="CAT_NOMBRE_EN_MAYUSCULAS" style="width:100%" />
        </div>
        <div>
          <label class="dlg-lbl">Nombre legible *</label>
          <input pInputText [(ngModel)]="catalogoForm.nombre" style="width:100%" />
        </div>
        <div>
          <label class="dlg-lbl">Descripción</label>
          <input pInputText [(ngModel)]="catalogoForm.descripcion" style="width:100%" />
        </div>
        <div style="display:flex;gap:.5rem;justify-content:flex-end">
          <p-button label="Cancelar" severity="secondary" [text]="true"
            (onClick)="catalogoDlgVisible.set(false)" />
          <p-button label="Crear" icon="pi pi-plus" (onClick)="crearCatalogo()" />
        </div>
      </div>
    </p-dialog>

    <!-- Dialog nuevo/editar item -->
    <p-dialog [visible]="itemDlgVisible()" (visibleChange)="itemDlgVisible.set($event)"
      [header]="itemEdit ? 'Editar Item' : 'Agregar Item'"
      [modal]="true" [style]="{width:'420px'}">
      <div style="display:flex;flex-direction:column;gap:1rem">
        <div>
          <label class="dlg-lbl">Valor *</label>
          <input pInputText [(ngModel)]="itemForm.valor" style="width:100%" />
        </div>
        <div>
          <label class="dlg-lbl">Descripción</label>
          <input pInputText [(ngModel)]="itemForm.descripcion" style="width:100%" />
        </div>
        <div>
          <label class="dlg-lbl">Orden</label>
          <p-inputNumber [(ngModel)]="itemForm.orden" styleClass="w-full" />
        </div>
        @if (itemEdit) {
          <div style="display:flex;align-items:center;gap:.75rem">
            <p-toggleswitch [(ngModel)]="itemForm.is_active" />
            <span>{{ itemForm.is_active ? 'Activo' : 'Inactivo' }}</span>
          </div>
        }
        <div style="display:flex;gap:.5rem;justify-content:flex-end">
          <p-button label="Cancelar" severity="secondary" [text]="true"
            (onClick)="itemDlgVisible.set(false)" />
          <p-button [label]="itemEdit ? 'Guardar' : 'Agregar'" icon="pi pi-check"
            (onClick)="guardarItem()" />
        </div>
      </div>
    </p-dialog>

    <!-- Dialog editar usuario -->
    <p-dialog [visible]="usuarioDlgVisible()" (visibleChange)="usuarioDlgVisible.set($event)"
      header="Editar Usuario" [modal]="true" [draggable]="false" [style]="{width:'400px'}">
      @if (usuarioEdit()) {
        <div class="form-grid1" style="display:flex; flex-direction:column; gap:.75rem">
          <div class="field" style="display:flex; flex-direction:column; gap:.35rem">
            <span class="dlg-lbl">Nombre</span>
            <strong>{{ usuarioEdit()!.nombre_completo }}</strong>
          </div>
          <div class="field" style="display:flex; flex-direction:column; gap:.35rem">
            <span class="dlg-lbl">Usuario</span>
            <strong>{{ usuarioEdit()!.nombre_usuario }}</strong>
          </div>
          <div class="field" style="display:flex; flex-direction:column; gap:.35rem">
            <label class="dlg-lbl">Rol</label>
            <p-select [options]="roles()" [(ngModel)]="usuarioEditForm.rol_id"
                      optionLabel="nombre_rol" optionValue="id" placeholder="Seleccionar Rol"
                      [filter]="true" filterPlaceholder="Buscar..." appendTo="body"/>
          </div>
          <div class="field" style="display:flex; flex-direction:column; gap:.35rem">
            <label class="dlg-lbl">Plantel (Alcance)</label>
            <p-select [options]="planteles()" [(ngModel)]="usuarioEditForm.plantel_id"
                      optionLabel="nombre_plantel" optionValue="id" placeholder="Global"
                      [showClear]="true" [filter]="true" filterPlaceholder="Buscar..." appendTo="body"/>
          </div>
          <div class="field" style="display:flex; flex-direction:column; gap:.35rem">
            <label class="dlg-lbl">Estado</label>
            <div style="display:flex; gap:.5rem; align-items:center">
              <input type="checkbox" [(ngModel)]="usuarioEditForm.is_active" id="usr-active" />
              <label for="usr-active" style="font-size:.85rem">Usuario Activo</label>
            </div>
          </div>
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="usuarioDlgVisible.set(false)" />
          <p-button label="Guardar" icon="pi pi-save" [loading]="guardandoUsuario()" (onClick)="guardarUsuario()" />
        </ng-template>
      }
    </p-dialog>

    <!-- Dialog crear usuario (formulario completo) -->
    <p-dialog [visible]="crearUsrDlgVisible()" (visibleChange)="crearUsrDlgVisible.set($event)"
      header="Crear Usuario" [modal]="true" [draggable]="false" [style]="{width:'520px'}">
      <div style="display:flex;flex-direction:column;gap:.75rem">
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:.6rem">
          <div class="field" style="display:flex;flex-direction:column;gap:.35rem">
            <label class="dlg-lbl">Nombre(s) *</label>
            <input pInputText [(ngModel)]="nuevoUsrForm.nombre" placeholder="Ej. María" />
          </div>
          <div class="field" style="display:flex;flex-direction:column;gap:.35rem">
            <label class="dlg-lbl">Apellido paterno *</label>
            <input pInputText [(ngModel)]="nuevoUsrForm.apellido_paterno" />
          </div>
          <div class="field" style="display:flex;flex-direction:column;gap:.35rem">
            <label class="dlg-lbl">Apellido materno</label>
            <input pInputText [(ngModel)]="nuevoUsrForm.apellido_materno" />
          </div>
          <div class="field" style="display:flex;flex-direction:column;gap:.35rem">
            <label class="dlg-lbl">CURP *</label>
            <input pInputText [(ngModel)]="nuevoUsrForm.curp" maxlength="18"
              style="text-transform:uppercase" placeholder="18 caracteres" />
          </div>
          <div class="field" style="display:flex;flex-direction:column;gap:.35rem">
            <label class="dlg-lbl">Email institucional</label>
            <input pInputText [(ngModel)]="nuevoUsrForm.email_institucional" type="email"
              placeholder="opcional — se genera automático" />
          </div>
          <div class="field" style="display:flex;flex-direction:column;gap:.35rem">
            <label class="dlg-lbl">Género</label>
            <p-select [options]="[{l:'Hombre',v:'M'},{l:'Mujer',v:'F'},{l:'No especificado',v:'NB'}]"
              [(ngModel)]="nuevoUsrForm.genero" optionLabel="l" optionValue="v" appendTo="body"/>
          </div>
          <div class="field" style="display:flex;flex-direction:column;gap:.35rem">
            <label class="dlg-lbl">Fecha de nacimiento</label>
            <input pInputText type="date" [(ngModel)]="nuevoUsrForm.fecha_nacimiento" />
          </div>
        </div>
        <div class="field" style="display:flex;flex-direction:column;gap:.35rem">
          <label class="dlg-lbl">Rol *</label>
          <p-select [options]="roles()" [(ngModel)]="nuevoUsrForm.rol_id"
            optionLabel="nombre_rol" optionValue="id" placeholder="Seleccionar Rol"
            [filter]="true" filterPlaceholder="Buscar..." appendTo="body"/>
        </div>
        <div class="field" style="display:flex;flex-direction:column;gap:.35rem">
          <label class="dlg-lbl">Plantel (alcance)</label>
          <p-select [options]="planteles()" [(ngModel)]="nuevoUsrForm.plantel_id"
            optionLabel="nombre_plantel" optionValue="id" placeholder="Global (sin plantel fijo)"
            [showClear]="true" [filter]="true" filterPlaceholder="Buscar..." appendTo="body"/>
        </div>
        <p-message severity="info" icon="pi pi-info-circle"
          text="El usuario se creará en ADES. Para que pueda iniciar sesión también necesita cuenta en Authentik (SSO)." />
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="crearUsrDlgVisible.set(false)" />
        <p-button label="Crear Usuario" icon="pi pi-user-plus" [loading]="guardandoNuevoUsr()" (onClick)="crearUsuario()" />
      </ng-template>
    </p-dialog>

    <!-- Dialog Ciclo -->
    <p-dialog [visible]="dlgCicloVisible()" (visibleChange)="dlgCicloVisible.set($event)"
      [header]="cicloEdit()?.id ? 'Editar Ciclo Escolar' : 'Nuevo Ciclo Escolar'"
      [modal]="true" [draggable]="false" [style]="{width:'480px'}">
      @if (cicloEdit()) {
        <div style="display:flex; flex-direction:column; gap:.75rem; padding:.25rem 0">
          <div style="display:flex; flex-direction:column; gap:.35rem">
            <label class="dlg-lbl">Nombre del ciclo *</label>
            <input pInputText [(ngModel)]="cicloEdit()!.nombre_ciclo" placeholder="Ej. 2026-2027" />
          </div>
          <div style="display:flex; flex-direction:column; gap:.35rem">
            <label class="dlg-lbl">Nivel educativo *</label>
            <p-select [options]="niveles()" [(ngModel)]="cicloEdit()!.nivel_educativo_id"
              optionLabel="nombre_nivel" optionValue="id" placeholder="Seleccionar nivel"
              [filter]="true" filterPlaceholder="Buscar..." appendTo="body"/>
          </div>
          <div style="display:grid; grid-template-columns:1fr 1fr; gap:.75rem">
            <div style="display:flex; flex-direction:column; gap:.35rem">
              <label class="dlg-lbl">Fecha inicio *</label>
              <input pInputText type="date" [(ngModel)]="cicloEdit()!.fecha_inicio" />
            </div>
            <div style="display:flex; flex-direction:column; gap:.35rem">
              <label class="dlg-lbl">Fecha fin *</label>
              <input pInputText type="date" [(ngModel)]="cicloEdit()!.fecha_fin" />
            </div>
          </div>
          <div style="display:grid; grid-template-columns:1fr 1fr; gap:.75rem">
            <div style="display:flex; flex-direction:column; gap:.35rem">
              <label class="dlg-lbl">Tipo de ciclo</label>
              <p-select [options]="['ANUAL','SEMESTRAL','CUATRIMESTRAL','TRIMESTRAL']"
                [(ngModel)]="cicloEdit()!.tipo_ciclo" />
            </div>
            <div style="display:flex; flex-direction:column; gap:.35rem; justify-content:flex-end">
              <div style="display:flex; gap:.5rem; align-items:center">
                <input type="checkbox" [(ngModel)]="cicloEdit()!.es_vigente" id="cic-vig" />
                <label for="cic-vig" style="font-size:.85rem">Ciclo Vigente</label>
              </div>
            </div>
          </div>
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="dlgCicloVisible.set(false)" />
          <p-button label="Guardar" icon="pi pi-save" [loading]="guardandoCiclo()" (onClick)="guardarCiclo()" />
        </ng-template>
      }
    </p-dialog>

    <!-- Dialog Plantel -->
    <p-dialog [visible]="dlgPlantelVisible()" (visibleChange)="dlgPlantelVisible.set($event)"
      header="Editar Plantel" [modal]="true" [draggable]="false" [style]="{width:'400px'}">
      @if (plantelEdit()) {
        <div style="display:flex; flex-direction:column; gap:.75rem; padding:.25rem 0">
          <div style="display:flex; flex-direction:column; gap:.35rem">
            <label class="dlg-lbl">Nombre del plantel *</label>
            <input pInputText [(ngModel)]="plantelEdit()!.nombre_plantel" />
          </div>
          <div style="display:flex; flex-direction:column; gap:.35rem">
            <label class="dlg-lbl">Clave CT</label>
            <input pInputText [(ngModel)]="plantelEdit()!.clave_ct" placeholder="Ej. 15EBH0001R" />
          </div>
          <div style="display:flex; gap:.5rem; align-items:center">
            <input type="checkbox" [(ngModel)]="plantelEdit()!.is_active" id="plt-active" />
            <label for="plt-active" style="font-size:.85rem">Plantel Activo</label>
          </div>
          <div style="border-top:1px solid var(--surface-200); padding-top:.6rem; margin-top:.25rem">
            <label class="dlg-lbl" style="display:block;margin-bottom:.4rem">
              Claves oficiales por nivel (CCT SEP / incorporación UAEMEX)
            </label>
            @for (c of clavesPlantel(); track c.id) {
              <div style="display:flex; gap:.5rem; align-items:center; margin-bottom:.4rem">
                <span style="font-size:.78rem; width:110px; color:var(--text-color-secondary)">{{ c.nombre_nivel }}</span>
                <input pInputText [(ngModel)]="c.clave" [placeholder]="c.tipo_clave === 'CCT_SEP' ? 'Ej. 15PPR0000X' : 'Clave incorporación UAEMEX'" style="flex:1" />
                <p-button icon="pi pi-save" [text]="true" size="small" (onClick)="guardarClavePlantel(c)" />
              </div>
            } @empty {
              <p class="dlg-note">Sin claves registradas para este plantel.</p>
            }
          </div>
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="dlgPlantelVisible.set(false)" />
          <p-button label="Guardar" icon="pi pi-save" [loading]="guardandoPlantel()" (onClick)="guardarPlantel()" />
        </ng-template>
      }
    </p-dialog>

    <!-- Dialog Grupo (admin) -->
    <p-dialog [visible]="dlgGrupoAdminVisible()" (visibleChange)="dlgGrupoAdminVisible.set($event)"
      [header]="grupoAdminEdit()?.id ? 'Editar Grupo' : 'Nuevo Grupo'"
      [modal]="true" [draggable]="false" [style]="{width:'460px'}">
      @if (grupoAdminEdit()) {
        <div style="display:flex; flex-direction:column; gap:.75rem; padding:.25rem 0">
          <div style="display:grid; grid-template-columns:1fr 1fr; gap:.75rem">
            <div style="display:flex; flex-direction:column; gap:.35rem">
              <label class="dlg-lbl">Nombre del grupo *</label>
              <input pInputText [(ngModel)]="grupoAdminEdit()!.nombre_grupo" maxlength="10" placeholder="A" />
            </div>
            <div style="display:flex; flex-direction:column; gap:.35rem">
              <label class="dlg-lbl">Capacidad *</label>
              <input pInputText type="number" [(ngModel)]="grupoAdminEdit()!.capacidad_maxima" min="1" max="60" />
            </div>
          </div>
          <div style="display:flex; flex-direction:column; gap:.35rem">
            <label class="dlg-lbl">Turno *</label>
            <p-select [options]="['MATUTINO','VESPERTINO','NOCTURNO']" [(ngModel)]="grupoAdminEdit()!.turno" />
          </div>
          @if (!grupoAdminEdit()!.id) {
            <div style="display:flex; flex-direction:column; gap:.35rem">
              <label class="dlg-lbl">Ciclo Escolar *</label>
              <p-select [options]="ciclos()" [(ngModel)]="grupoAdminEdit()!.ciclo_escolar_id"
                optionLabel="nombre_ciclo" optionValue="id" placeholder="Seleccionar ciclo"
                [filter]="true" filterPlaceholder="Buscar..." />
            </div>
            <div style="display:flex; flex-direction:column; gap:.35rem">
              <label class="dlg-lbl">Grado *</label>
              <p-select [options]="gradosFiltrados()" [(ngModel)]="grupoAdminEdit()!.grado_id"
                optionLabel="label" optionValue="id" placeholder="Seleccionar grado"
                [filter]="true" filterPlaceholder="Buscar..." />
            </div>
          }
          @if (grupoAdminEdit()!.id) {
            <div style="display:flex; gap:.5rem; align-items:center">
              <input type="checkbox" [(ngModel)]="grupoAdminEdit()!.is_active" id="grp-active" />
              <label for="grp-active" style="font-size:.85rem">Grupo Activo</label>
            </div>
          }
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="dlgGrupoAdminVisible.set(false)" />
          <p-button label="Guardar" icon="pi pi-save" [loading]="guardandoGrupo()" (onClick)="guardarGrupoAdmin()" />
        </ng-template>
      }
    </p-dialog>
  `,
  styles: [`
    .page-header { margin-bottom:.5rem; }
    .subtitle { font-size:.82rem; color:var(--text-secondary); margin:0; }
    .scope-bar { display:flex;align-items:center;gap:.6rem;padding:.45rem .75rem;
      background:var(--primary-50,#fef2f3);border:1px solid var(--primary-100,#fde0e2);
      border-radius:6px;margin-bottom:.75rem;font-size:.8rem;flex-wrap:wrap }
    .scope-bar--global { background:var(--surface-50);border-color:var(--surface-200) }
    .scope-tag { padding:.1rem .45rem;border-radius:4px;font-size:.73rem;font-weight:600 }
    .scope-scoped { background:var(--primary-100);color:var(--primary-700) }
    .scope-global  { background:var(--surface-200);color:var(--text-secondary) }
    .tab-toolbar { display:flex; gap:.5rem; align-items:center; margin-bottom:1rem; justify-content:space-between; }
    .ctx-selector { width:200px; }
    .nivel-chip { background:var(--primary-100); color:var(--primary-700); padding:.1rem .35rem;
      border-radius:3px; font-size:.75rem; font-weight:600; margin-right:.3rem; }
    .ocupacion { font-weight:600; color:var(--green-600); }
    .ocupacion.lleno { color:var(--red-600); }
    /* Variables */
    .var-grupo-header { background:var(--surface-100); padding:.4rem .75rem; font-weight:600;
      font-size:.8rem; color:var(--text-secondary); border-radius:4px; margin:.75rem 0 .25rem; display:flex; align-items:center; }
    .var-nombre { font-size:.78rem; font-weight:600; color:var(--primary-700); }
    .var-secret { font-family:monospace; color:var(--text-muted); letter-spacing:2px; }
    .var-json { font-size:.75rem; color:var(--text-secondary); font-family:monospace; }
    .var-desc { font-size:.8rem; color:var(--text-secondary); }
    .var-readonly { opacity:.75; background:var(--surface-50) !important; }
    .var-lock { color:var(--text-muted); font-size:.9rem; }
    /* Catálogos */
    .cat-panel { border:1px solid var(--surface-border); border-radius:6px; overflow:hidden; }
    .cat-item { display:flex; justify-content:space-between; align-items:center;
      padding:.6rem .75rem; border-bottom:1px solid var(--surface-border);
      cursor:pointer; transition:background .15s; }
    .cat-item:hover { background:var(--surface-hover); }
    .cat-activo { background:var(--primary-50) !important; border-left:3px solid var(--primary-color); }
    .cat-codigo { font-size:.75rem; font-weight:700; color:var(--primary-700); display:block; }
    .cat-nombre { font-size:.85rem; color:var(--text-color); }
    /* Shared */
    .empty-msg { color:var(--text-secondary); font-size:.88rem; padding:1rem; text-align:center; }
    .dlg-lbl { display:block; font-size:.85rem; margin-bottom:.25rem; color:var(--text-secondary); }
    .dlg-note { font-size:.78rem; color:var(--text-color-secondary); margin:0; }
    :deep(.w-full) { width:100% !important; }
    /* Reglas de Promoción */
    .regla-card { background:var(--surface-card,#fff); border:1px solid var(--surface-border,#e2e8f0);
      border-radius:.5rem; padding:1.25rem; box-shadow:0 1px 3px rgba(0,0,0,.06); }
    .regla-card-header { margin-bottom:1rem; }
    .regla-titulo { font-size:1rem; font-weight:700; color:var(--text-primary,#1e293b); display:block; }
    .regla-sub { font-size:.78rem; color:var(--text-secondary,#64748b); }
    .regla-fields { display:flex; flex-direction:column; gap:.65rem; }
    .regla-field { display:flex; justify-content:space-between; align-items:center; gap:.5rem; }
    .regla-field label { font-size:.82rem; color:var(--text-secondary,#64748b); }
    .regla-field--toggle { flex-direction:row; }
    /* Evaluación Cualitativa */
    .cual-config-card { background:var(--surface-card,#fff); border:1px solid var(--surface-border);
      border-radius:6px; padding:1rem; display:flex; flex-direction:column; }
    .cual-config-label { font-size:.85rem; font-weight:600; color:var(--text-primary); margin-bottom:.2rem; }
    .cual-config-hint { font-size:.78rem; color:var(--text-secondary); }
    .nivel-badge { display:inline-flex; align-items:center; justify-content:center;
      width:28px; height:28px; border-radius:50%; color:#fff; font-weight:700; font-size:.85rem; }
    /* Permisos matrix */
    .permisos-matrix { overflow-x:auto; }
    .pm-table { width:100%; border-collapse:collapse; font-size:.85rem; }
    .pm-table th { background:var(--surface-100); padding:.5rem .75rem; text-align:left;
      font-weight:600; font-size:.78rem; color:var(--text-secondary); border-bottom:2px solid var(--surface-border); }
    .pm-table td { padding:.45rem .75rem; border-bottom:1px solid var(--surface-border); vertical-align:middle; }
    .pm-table tr:hover td { background:var(--surface-hover); }
    .pm-check { text-align:center; width:70px; }
    .pm-add { margin-top:1rem; }
    .tab-title { font-weight:600; font-size:.95rem; }
    .sync-card { background:var(--surface-0);border:1px solid var(--surface-200);border-radius:10px;padding:1.25rem }
    .sync-header { display:flex;align-items:flex-start;justify-content:space-between;gap:1rem;flex-wrap:wrap }
    .sync-estado { display:flex;align-items:center;gap:.6rem;margin-top:.9rem;padding:.6rem .9rem;border-radius:8px;font-size:.83rem }
    .sync-pending,.sync-started { background:#fef9c3;color:#78350f }
    .sync-success { background:#dcfce7;color:#14532d }
    .sync-failure { background:#fee2e2;color:#7f1d1d }
  `],
})
export class AdminComponent implements OnInit {
  private readonly api   = inject(ApiService);
  readonly ctx           = inject(ContextService);
  private readonly notify = inject(ApexNotificationService);
  private readonly conf  = inject(ConfirmationService);

  // ── Estado general ──────────────────────────────────────────────────────────
  usuarios       = signal<UsuarioAdmin[]>([]);
  usuariosDatos  = signal<any[]>([]);
  ciclos         = signal<CicloAdmin[]>([]);
  planteles      = signal<PlantelAdmin[]>([]);
  grupos         = signal<GrupoAdmin[]>([]);
  roles          = signal<RolOpt[]>([]);
  niveles        = signal<NivelOpt[]>([]);
  ciclosFiltro   = signal<CicloAdmin[]>([]);

  // ── Signals para dialogs de Ciclo / Plantel / Grupo ─────────────────────────
  dlgCicloVisible      = signal(false);
  cicloEdit            = signal<CicloAdmin | null>(null);
  guardandoCiclo       = signal(false);
  dlgPlantelVisible    = signal(false);
  plantelEdit          = signal<PlantelAdmin | null>(null);
  clavesPlantel        = signal<any[]>([]);
  guardandoPlantel     = signal(false);
  dlgGrupoAdminVisible = signal(false);
  grupoAdminEdit       = signal<GrupoAdmin | null>(null);
  guardandoGrupo       = signal(false);
  grados               = signal<{ id: string; label: string; nivel_id?: string }[]>([]);

  gradosFiltrados = computed(() => {
    const grupoForm = this.grupoAdminEdit();
    if (!grupoForm?.ciclo_escolar_id) return this.grados();
    const cicloSeleccionado = this.ciclos().find(c => c.id === grupoForm.ciclo_escolar_id);
    if (!cicloSeleccionado?.nivel_educativo_id) return this.grados();
    return this.grados().filter(g =>
      !g.nivel_id || g.nivel_id === cicloSeleccionado.nivel_educativo_id
    );
  });

  loadingUsuarios  = signal(false);
  loadingCiclos    = signal(false);
  loadingPlanteles = signal(false);
  loadingGrupos    = signal(false);

  franjas = signal<any[]>([]);
  loadingFranjas = signal(false);
  showFranjaDialog = signal(false);
  guardandoFranja = signal(false);
  editFranjaEntry = signal<any>(null);
  franjaForm: any = {};
  nivelesMap = computed(() => {
    const m: Record<string, string> = {};
    this.niveles().forEach(n => m[n.id] = n.nombre_nivel);
    return m;
  });
  String = String;

  // ── SEPOMEX sync ─────────────────────────────────────────────────────────────
  syncSepomexLoading  = signal(false);
  syncSepomexTaskId   = signal<string | null>(null);
  syncSepomexEstado   = signal<string | null>(null);
  syncSepomexResultado = signal<string>('');
  syncSepomexError    = signal<string>('');
  private _syncPollInterval: ReturnType<typeof setInterval> | null = null;

  // ── SEPOMEX stats ─────────────────────────────────────────────────────────────
  sepomexStats = signal<{ estados: number; municipios: number; colonias: number; cps_unicos: number } | null>(null);
  loadingSepomexStats = signal(false);

  tabActivo       = signal('usuarios');
  cicloFiltroId   = '';

  // ── Roles ───────────────────────────────────────────────────────────────────
  loadingRoles     = signal(false);
  rolDlgVisible    = signal(false);
  rolEdit          = signal<any | null>(null);
  guardandoRol     = signal(false);
  rolEditForm      = { descripcion: '', nivel_acceso: 4 };

  readonly nivelesAccesoOpts = [
    { value: 0, label: '0 — Admin Global (acceso total)' },
    { value: 1, label: '1 — Admin Plantel' },
    { value: 2, label: '2 — Director / Coordinador' },
    { value: 3, label: '3 — Coord. Académico / Secretaria' },
    { value: 4, label: '4 — Docente / Apoyo' },
    { value: 5, label: '5 — Padre / Alumno (solo lectura)' },
  ];

  columnasRoles: ColumnConfig[] = [
    { field: 'nombre_rol',   header: 'Rol',        sortable: true,  filterable: true, width: '160px' },
    { field: 'nivel_acceso', header: 'Nivel',       sortable: true,  filterable: false, width: '80px' },
    { field: 'descripcion',  header: 'Descripción', sortable: false, filterable: true },
  ];

  // ── Menús ───────────────────────────────────────────────────────────────────
  menus            = signal<any[]>([]);
  loadingMenus     = signal(false);
  menuDlgVisible   = signal(false);
  menuEdit         = signal<any | null>(null);
  guardandoMenu    = signal(false);
  menuEditForm     = { label: '', nivel_maximo: 99, nivel_minimo: 0, activo: true };

  columnasMenus: ColumnConfig[] = [
    { field: 'clave',        header: 'Clave',    sortable: true,  filterable: true, width: '150px' },
    { field: 'seccion',      header: 'Sección',  sortable: true,  filterable: true, width: '130px' },
    { field: 'label',        header: 'Etiqueta', sortable: true,  filterable: true },
    { field: 'nivel_maximo', header: 'Máx',      sortable: true,  filterable: false, width: '70px' },
    { field: 'nivel_minimo', header: 'Mín',      sortable: true,  filterable: false, width: '70px' },
    { field: 'activo',       header: 'Activo',   sortable: false, filterable: false, width: '80px' },
  ];

  // ── Permisos por rol ────────────────────────────────────────────────────────
  permisos         = signal<any[]>([]);
  loadingPermisos  = signal(false);
  permisosRolFiltro: string | null = null;
  nuevoPrmDlgVisible = signal(false);
  guardandoPermiso = signal(false);
  nuevoPrmForm     = { menu_clave: '', puede_ver: true, puede_editar: true, puede_crear: true, puede_eliminar: false };

  // ── Crear usuario (formulario completo) ──────────────────────────────────────
  crearUsrDlgVisible = signal(false);
  guardandoNuevoUsr  = signal(false);
  nuevoUsrForm = {
    nombre: '', apellido_paterno: '', apellido_materno: '',
    curp: '', email_institucional: '', genero: 'M',
    fecha_nacimiento: '', rol_id: '', plantel_id: null as string | null,
  };

  columnasUsuarios: ColumnConfig[] = [
    { field: 'nombre_usuario',      header: 'Usuario',       sortable: true, filterable: true, width: '130px' },
    { field: 'nombre_completo',     header: 'Nombre',        sortable: true, filterable: true, width: '200px' },
    { field: 'email_institucional', header: 'Email',         sortable: true, filterable: true, width: '200px' },
    { field: 'rol',                 header: 'Rol',           sortable: true, filterable: true, width: '130px' },
    { field: 'alcance',             header: 'Alcance',       sortable: false, filterable: true, width: '150px' },
    { field: 'estado',              header: 'Estado',        sortable: true, filterable: true, width: '100px' },
  ];

  columnasCiclos: ColumnConfig[] = [
    { field: 'nombre_ciclo',     header: 'Ciclo',   sortable: true,  filterable: true,  width: '200px' },
    { field: 'nombre_nivel',     header: 'Nivel',   sortable: true,  filterable: true,  width: '160px' },
    { field: 'fecha_inicio_str', header: 'Inicio',  sortable: false, filterable: false, width: '120px' },
    { field: 'fecha_fin_str',    header: 'Fin',     sortable: false, filterable: false, width: '120px' },
    { field: 'tipo_ciclo',       header: 'Tipo',    sortable: true,  filterable: true,  width: '110px' },
    { field: 'vigente_str',      header: 'Vigente', sortable: true,  filterable: true,  width: '100px' },
  ];

  columnasPlanteles: ColumnConfig[] = [
    { field: 'nombre_plantel', header: 'Plantel',    sortable: true, filterable: true  },
    { field: 'clave_ct',       header: 'Clave CT',   sortable: true, filterable: true,  width: '150px' },
    { field: 'estado_str',     header: 'Estado',     sortable: true, filterable: true,  width: '100px' },
  ];

  columnasGrupos: ColumnConfig[] = [
    { field: 'nivel_grado',    header: 'Nivel / Grado', sortable: true, filterable: true,  width: '180px' },
    { field: 'nombre_grupo',   header: 'Grupo',         sortable: true, filterable: true,  width: '80px' },
    { field: 'ocupacion_str',  header: 'Ocupación',     sortable: false, filterable: false, width: '110px' },
    { field: 'turno',          header: 'Turno',         sortable: true, filterable: true,  width: '120px' },
    { field: 'estado_str',     header: 'Estado',        sortable: true, filterable: true,  width: '100px' },
  ];

  readonly recargarUsuarios = () => this.cargarUsuarios();

  // ── Variables del Sistema ───────────────────────────────────────────────────
  variables          = signal<VariableSistema[]>([]);
  loadingVariables   = signal(false);
  guardandoVariable  = signal(false);
  grupoSeleccionado: string | null = null;
  variableDlgVisible = signal(false);
  variableEdit: VariableSistema | null = null;

  gruposFiltroOpts = computed(() => {
    const grupos = [...new Set(this.variables().map(v => v.grupo).filter(Boolean))] as string[];
    return grupos.sort();
  });

  gruposActuales = computed(() => {
    const vars = this.variables();
    if (!vars.length) return [];
    if (this.grupoSeleccionado) return [this.grupoSeleccionado];
    return [...new Set(vars.map(v => v.grupo).filter(Boolean))] as string[];
  });

  variablesPorGrupo = computed(() => {
    const map: Record<string, VariableSistema[]> = {};
    for (const v of this.variables()) {
      const g = v.grupo ?? 'SIN GRUPO';
      if (!map[g]) map[g] = [];
      map[g].push(v);
    }
    return map;
  });

  variablesPorGrupoFlat = computed(() => {
    const src = this.variablesPorGrupo();
    const result: Record<string, any[]> = {};
    for (const g of Object.keys(src)) {
      result[g] = src[g].map(v => ({
        ...v,
        valor_str: v.tipo_valor === 'PASSWORD' ? '••••••••'
          : v.tipo_valor === 'JSON' ? ((v.valor ?? '').slice(0, 60) + ((v.valor?.length ?? 0) > 60 ? '…' : ''))
          : (v.valor ?? '—'),
      }));
    }
    return result;
  });

  readonly variablesColumns: ColumnConfig[] = [
    { field: 'nombre',      header: 'Variable',     width: '220px', sortable: true },
    { field: 'tipo_valor',  header: 'Tipo',         width: '110px' },
    { field: 'valor_str',   header: 'Valor' },
    { field: 'descripcion', header: 'Descripción',  width: '280px' },
  ];

  readonly catItemsFlat = computed(() =>
    (this.catSeleccionado()?.items ?? []).map(item => ({
      ...item,
      estado_str: item.is_active ? 'Activo' : 'Inactivo',
    }))
  );
  readonly catItemsColumns: ColumnConfig[] = [
    { field: 'orden',       header: 'Orden',        width: '60px', sortable: true },
    { field: 'valor',       header: 'Valor',        sortable: true },
    { field: 'descripcion', header: 'Descripción' },
    { field: 'estado_str',  header: 'Estado',       width: '90px' },
  ];

  // ── Catálogos Dinámicos ─────────────────────────────────────────────────────
  catalogos        = signal<Catalogo[]>([]);
  catSeleccionado  = signal<Catalogo | null>(null);
  loadingCatalogos = signal(false);
  catalogoDlgVisible = signal(false);
  catalogoForm     = { codigo: '', nombre: '', descripcion: '' };
  itemDlgVisible   = signal(false);
  itemEdit: CatalogoItem | null = null;
  itemForm         = { valor: '', descripcion: '', orden: 0, is_active: true };

  // ── Evaluación Cualitativa ──────────────────────────────────────────────────
  cualEscalas      = signal<any[]>([]);
  loadingCual      = signal(false);
  guardandoCual    = signal(false);
  cualGradosStr    = '1, 2';
  cualMostrarEquiv = true;
  cualTodasMaterias = true;

  cargarConfigCualitativa(): void {
    this.loadingCual.set(true);
    Promise.all([
      this.api.get<any[]>('/admin/config/escalas-cualitativas').toPromise(),
      this.api.get<any[]>('/admin/config', { grupo: 'evaluacion_cualitativa' }).toPromise(),
    ]).then(([escalas, configs]) => {
      // Parsear descriptores
      const parsed = (escalas ?? []).map((e: any) => ({
        ...e,
        _descriptores: typeof e.valores_json === 'string'
          ? JSON.parse(e.valores_json) : (e.valores_json ?? []),
        _modificado: false,
      }));
      this.cualEscalas.set(parsed);
      // Aplicar config
      for (const c of (configs ?? [])) {
        const v = typeof c.valor === 'string' ? JSON.parse(c.valor) : c.valor;
        if (c.clave === 'EVAL_CUAL_GRADOS_PRIMARIA')
          this.cualGradosStr = Array.isArray(v) ? v.join(', ') : String(v);
        if (c.clave === 'EVAL_CUAL_MOSTRAR_EQUIVALENCIA') this.cualMostrarEquiv = !!v;
        if (c.clave === 'EVAL_CUAL_APLICAR_TODAS_MATERIAS') this.cualTodasMaterias = !!v;
      }
      this.loadingCual.set(false);
    }).catch(() => this.loadingCual.set(false));
  }

  parseCualGrados(): number[] {
    return this.cualGradosStr.split(',').map(s => parseInt(s.trim(), 10)).filter(n => !isNaN(n));
  }

  guardarConfigCual(clave: string, valor: any): void {
    this.api.patch(`/admin/config/${clave}`, { valor }).subscribe({
      next: () => this.notify.success('Guardado', `${clave} actualizado`),
      error: (e) => this.notify.error('Error', e.error?.message ?? 'No se pudo guardar'),
    });
  }

  marcarEscalaCambio(escala: any): void {
    escala._modificado = true;
  }

  guardarEscala(escala: any): void {
    this.guardandoCual.set(true);
    const payload = {
      nombre: escala.nombre,
      descripcion: escala.descripcion,
      is_active: escala.is_active,
      valores_json: escala._descriptores,
    };
    this.api.put(`/admin/config/escalas-cualitativas/${escala.id}`, payload).subscribe({
      next: () => {
        escala._modificado = false;
        this.guardandoCual.set(false);
        this.notify.success('Guardado', 'Escala de descriptores actualizada');
      },
      error: (e) => {
        this.guardandoCual.set(false);
        this.notify.error('Error', e.error?.message ?? 'No se pudo guardar la escala');
      },
    });
  }

  // ── Reglas de Promoción ─────────────────────────────────────────────────────
  nivelesReglas        = signal<any[]>([]);
  loadingReglas        = signal(false);
  guardandoRegla       = signal(false);
  evaluandoPromocion   = signal(false);

  constructor() {
    effect(() => {
      // Reactivo al cambio de plantel/ciclo en la barra superior
      const _plantel = this.ctx.plantel();
      const _ciclo   = this.ctx.ciclo();
      this.cargarUsuarios();
      this.cargarGrupos();
    });
  }

  ngOnInit(): void {
    this.cargarCiclos();
    this.cargarPlanteles();
    this.cargarRoles();
    this.cargarNiveles();
    this.cargarGrados();
  }

  onTabChange(tab: any): void {
    if (!tab) return;
    this.tabActivo.set(tab);
    if (tab === 'variables'        && this.variables().length === 0)     this.cargarVariables();
    if (tab === 'reglas'           && this.nivelesReglas().length === 0) this.cargarReglasPromocion();
    if (tab === 'catalogos'        && this.catalogos().length === 0)     this.cargarCatalogos();
    if (tab === 'eval-cualitativa' && this.cualEscalas().length === 0)   this.cargarConfigCualitativa();
    if (tab === 'franjas'          && this.franjas().length === 0)       this.cargarFranjas();
    if (tab === 'marca')                                                  this.cargarMarca();
    if (tab === 'roles'            && this.roles().length === 0)         this.cargarRoles();
    if (tab === 'menus'            && this.menus().length === 0)         this.cargarMenusAdmin();
    if (tab === 'permisos')                                               this.cargarPermisos();
    if (tab === 'geo'              && !this.sepomexStats())              this.cargarSepomexStats();
  }

  cargarSepomexStats(): void {
    this.loadingSepomexStats.set(true);
    this.api.get<{ estados: number; municipios: number; colonias: number; cps_unicos: number }>(
      '/admin/sepomex/stats'
    ).subscribe({
      next: s => { this.sepomexStats.set(s); this.loadingSepomexStats.set(false); },
      error: () => this.loadingSepomexStats.set(false),
    });
  }

  // ── Usuarios ────────────────────────────────────────────────────────────────

  cargarUsuarios(): void {
    this.loadingUsuarios.set(true);
    const params: Record<string, string> = {};
    const plantelId = this.ctx.plantel()?.id;
    if (plantelId) params['plantel_id'] = plantelId;
    this.api.get<UsuarioAdmin[]>('/admin/usuarios', params).subscribe({
      next: (u) => {
        this.usuarios.set(u);
        this.usuariosDatos.set(u.map(usr => ({
          id: usr.id,
          nombre_usuario: usr.nombre_usuario,
          nombre_completo: usr.nombre_completo,
          email_institucional: usr.email_institucional,
          rol: usr.rol,
          alcance: usr.nombre_plantel
            ? `${usr.nombre_plantel}${usr.nombre_nivel ? ` · ${usr.nombre_nivel}` : ''}`
            : '(Global)',
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
    this.api.get<CicloAdmin[]>('/admin/ciclos').subscribe({
      next: (c) => {
        const flat = c.map(x => ({
          ...x,
          fecha_inicio_str: (x as any).fecha_inicio?.substring(0, 10) ?? '—',
          fecha_fin_str:    (x as any).fecha_fin?.substring(0, 10) ?? '—',
          vigente_str:      (x as any).es_vigente ? 'Vigente' : '—',
        }));
        this.ciclos.set(flat as any);
        this.ciclosFiltro.set(c);
        this.loadingCiclos.set(false);
      },
      error: () => this.loadingCiclos.set(false),
    });
  }

  cargarPlanteles(): void {
    this.loadingPlanteles.set(true);
    this.api.get<PlantelAdmin[]>('/planteles').subscribe({
      next: (p) => {
        const flat = p.map(x => ({
          ...x,
          estado_str: (x as any).is_active ? 'Activo' : 'Inactivo',
        }));
        this.planteles.set(flat as any);
        this.loadingPlanteles.set(false);
      },
      error: () => this.loadingPlanteles.set(false),
    });
  }

  cargarGrupos(): void {
    this.loadingGrupos.set(true);
    const params: Record<string, string> = {};
    const plantelId = this.ctx.plantel()?.id;
    if (plantelId) params['plantel_id'] = plantelId;
    if (this.cicloFiltroId) params['ciclo_id'] = this.cicloFiltroId;
    this.api.get<GrupoAdmin[]>('/admin/grupos', params).subscribe({
      next: (g) => {
        const flat = g.map(x => ({
          ...x,
          nivel_grado:   `${(x as any).nombre_nivel ?? ''} ${(x as any).nombre_grado ?? ''}`.trim(),
          ocupacion_str: `${(x as any).inscritos ?? 0} / ${(x as any).capacidad_maxima ?? '—'}`,
          estado_str:    (x as any).is_active ? 'Activo' : 'Inactivo',
        }));
        this.grupos.set(flat as any);
        this.loadingGrupos.set(false);
      },
      error: () => this.loadingGrupos.set(false),
    });
  }

  cargarRoles(): void {
    this.loadingRoles.set(true);
    this.api.get<any[]>('/admin/roles').subscribe({
      next: (r) => { this.roles.set(r as any); this.loadingRoles.set(false); },
      error: () => {
        this.api.get<RolOpt[]>('/catalogs/roles').subscribe(r => { this.roles.set(r); this.loadingRoles.set(false); });
      },
    });
  }

  cargarNiveles(): void {
    this.api.get<NivelOpt[]>('/catalogs/niveles').subscribe(n => this.niveles.set(n));
  }

  cargarGrados(): void {
    this.api.get<any[]>('/catalogs/grados?todos_planteles=true').subscribe({
      next: gs => this.grados.set(gs.map(g => ({
        id: g.id,
        label: `${g.nombre_nivel ?? ''} ${g.nombre_grado ?? g.numero_grado ?? ''}`.trim(),
        nivel_id: g.nivel_educativo_id
      }))),
      error: () => {},
    });
  }

  // ── Variables del Sistema ───────────────────────────────────────────────────

  cargarVariables(): void {
    this.loadingVariables.set(true);
    const params: Record<string, string> = {};
    if (this.grupoSeleccionado) params['grupo'] = this.grupoSeleccionado;
    this.api.get<VariableSistema[]>('/config/variables', params).subscribe({
      next: vars => {
        this.variables.set(vars.map(v => ({
          ...v,
          _valorEdit: v.valor ?? '',
          _valorBool: v.valor === 'true',
          _valorNum: v.tipo_valor === 'NUMERO' ? Number(v.valor) : undefined,
        })));
        this.loadingVariables.set(false);
      },
      error: () => this.loadingVariables.set(false),
    });
  }

  abrirEditarVariable(v: VariableSistema): void {
    if (v.solo_lectura) return;
    this.variableEdit = {
      ...v,
      _valorEdit: v.valor ?? '',
      _valorBool: v.valor === 'true',
      _valorNum: v.tipo_valor === 'NUMERO' ? Number(v.valor) : undefined,
    };
    this.variableDlgVisible.set(true);
  }

  abrirNuevaVariable(): void {
    this.notify.info('Próximamente', 'Crear variables desde la UI estará disponible en la siguiente iteración');
  }

  onToggleBool(event: any): void {
    if (this.variableEdit) {
      this.variableEdit._valorEdit = event.checked ? 'true' : 'false';
    }
  }

  onVariableDateSelect(event: any): void {
    if (this.variableEdit && event instanceof Date) {
      this.variableEdit._valorEdit = event.toISOString();
    }
  }

  esJsonValido(txt: string): boolean {
    if (!txt) return false;
    try { JSON.parse(txt); return true; } catch { return false; }
  }

  guardarVariable(): void {
    if (!this.variableEdit) return;

    const esCritica = ['SISTEMA', 'ACADEMICO'].includes(this.variableEdit.grupo ?? '');
    if (esCritica && !this.variableEdit.solo_lectura) {
      this.conf.confirm({
        message: 'Estás modificando una variable crítica del sistema. Un valor incorrecto podría afectar la operación. ¿Deseas continuar?',
        header: 'Confirmación de Seguridad',
        icon: 'pi pi-exclamation-triangle',
        accept: () => this.ejecutarGuardarVariable()
      });
    } else {
      this.ejecutarGuardarVariable();
    }
  }

  private ejecutarGuardarVariable(): void {
    if (!this.variableEdit) return;
    
    let valorAGuardar = this.variableEdit._valorEdit;
    
    // Evitar sobreescribir con vacío si es un secreto no modificado
    if (this.variableEdit.tipo_valor === 'PASSWORD' && (!valorAGuardar || valorAGuardar.trim() === '')) {
      this.variableDlgVisible.set(false);
      return;
    }

    const payload = { valor: valorAGuardar, row_version: this.variableEdit.row_version };
    this.guardandoVariable.set(true);
    this.api.patch(`/config/variables/${this.variableEdit.nombre}`, payload).subscribe({
      next: () => {
        this.variableDlgVisible.set(false);
        this.guardandoVariable.set(false);
        this.notify.success('Variable actualizada');
        this.cargarVariables();
      },
      error: (e) => {
        this.guardandoVariable.set(false);
        this.notify.error('Error', e.error?.detail ?? 'No se pudo guardar');
      },
    });
  }

  tipoValorSeverity(tipo: string): 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' {
    const map: Record<string, 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast'> = {
      TEXTO: 'secondary', BOOLEANO: 'success', JSON: 'info',
      NUMERO: 'warn', FECHA: 'secondary', HORA: 'secondary', PASSWORD: 'danger',
    };
    return map[tipo] ?? 'secondary';
  }

  // ── Catálogos Dinámicos ─────────────────────────────────────────────────────

  cargarCatalogos(): void {
    this.loadingCatalogos.set(true);
    this.api.get<Catalogo[]>('/catalogos').subscribe({
      next: c => { this.catalogos.set(c); this.loadingCatalogos.set(false); },
      error: () => this.loadingCatalogos.set(false),
    });
  }

  seleccionarCatalogo(cat: Catalogo): void {
    this.catSeleccionado.set(cat);
  }

  abrirNuevoCatalogo(): void {
    this.catalogoForm = { codigo: '', nombre: '', descripcion: '' };
    this.catalogoDlgVisible.set(true);
  }

  crearCatalogo(): void {
    if (!this.catalogoForm.codigo || !this.catalogoForm.nombre) {
      this.notify.warning('Campos requeridos', 'Código y nombre son obligatorios');
      return;
    }
    this.api.post<Catalogo>('/catalogos', this.catalogoForm).subscribe({
      next: cat => {
        this.catalogoDlgVisible.set(false);
        this.notify.success('Catálogo creado');
        this.cargarCatalogos();
        this.catSeleccionado.set(cat);
      },
      error: (e) => this.notify.error('Error', e.error?.detail),
    });
  }

  abrirNuevoItem(): void {
    this.itemEdit = null;
    this.itemForm = { valor: '', descripcion: '', orden: 0, is_active: true };
    this.itemDlgVisible.set(true);
  }

  abrirEditarItem(item: CatalogoItem): void {
    this.itemEdit = item;
    this.itemForm = { valor: item.valor, descripcion: item.descripcion ?? '', orden: item.orden, is_active: item.is_active };
    this.itemDlgVisible.set(true);
  }

  guardarItem(): void {
    const cat = this.catSeleccionado();
    if (!cat) return;
    if (!this.itemForm.valor) {
      this.notify.warning('Valor requerido');
      return;
    }
    if (this.itemEdit) {
      const payload = { ...this.itemForm, row_version: this.itemEdit.row_version };
      this.api.patch(`/catalogos/items/${this.itemEdit.id}`, payload).subscribe({
        next: () => {
          this.itemDlgVisible.set(false);
          this.notify.success('Item actualizado');
          this.recargarCatSeleccionado(cat.id);
        },
        error: (e) => this.notify.error('Error', e.error?.detail),
      });
    } else {
      this.api.post<CatalogoItem>(`/catalogos/${cat.id}/items`, this.itemForm).subscribe({
        next: () => {
          this.itemDlgVisible.set(false);
          this.notify.success('Item agregado');
          this.recargarCatSeleccionado(cat.id);
        },
        error: (e) => this.notify.error('Error', e.error?.detail),
      });
    }
  }

  private recargarCatSeleccionado(catId: string): void {
    this.api.get<Catalogo>(`/catalogos/${catId}`).subscribe({
      next: c => {
        this.catSeleccionado.set(c);
        this.catalogos.update(list => list.map(x => x.id === c.id ? c : x));
      },
    });
  }

  // ── Ciclos ─────────────────────────────────────────────────────────────────

  abrirNuevoCiclo(): void {
    this.cicloEdit.set({ id: '', nombre_ciclo: '', nivel_educativo_id: '', fecha_inicio: '', fecha_fin: '', tipo_ciclo: 'ANUAL', es_vigente: false });
    this.dlgCicloVisible.set(true);
  }

  abrirEditarCiclo(row: any): void {
    const c: CicloAdmin = row._original ?? row;
    this.cicloEdit.set({ ...c });
    this.dlgCicloVisible.set(true);
  }

  guardarCiclo(): void {
    const c = this.cicloEdit();
    if (!c) return;
    if (!c.nombre_ciclo || !c.nivel_educativo_id || !c.fecha_inicio || !c.fecha_fin) {
      this.notify.warning('Campos requeridos', 'Nombre, nivel, fechas son obligatorios');
      return;
    }
    this.guardandoCiclo.set(true);
    const req$ = c.id
      ? this.api.patch(`/admin/ciclos/${c.id}`, c)
      : this.api.post('/admin/ciclos', c);
    req$.subscribe({
      next: () => {
        this.dlgCicloVisible.set(false);
        this.guardandoCiclo.set(false);
        this.notify.success('Ciclo guardado');
        this.cargarCiclos();
      },
      error: e => { this.guardandoCiclo.set(false); this.notify.error('Error', e.error?.detail ?? 'No se pudo guardar'); },
    });
  }

  // ── Planteles ──────────────────────────────────────────────────────────────

  abrirEditarPlantel(row: any): void {
    const p: PlantelAdmin = row._original ?? row;
    this.plantelEdit.set({ ...p });
    this.dlgPlantelVisible.set(true);
    this.cargarClavesPlantel(p.id);
  }

  cargarClavesPlantel(plantelId: string): void {
    this.clavesPlantel.set([]);
    this.api.get<any[]>(`/planteles/${plantelId}/claves`).subscribe({
      next: c => this.clavesPlantel.set(c),
      error: () => this.clavesPlantel.set([]),
    });
  }

  guardarClavePlantel(c: any): void {
    const p = this.plantelEdit();
    if (!p?.id) return;
    this.api.patch(`/planteles/${p.id}/claves/${c.nivel_educativo_id}`, {
      tipo_clave: c.tipo_clave, clave: c.clave, observaciones: c.observaciones,
    }).subscribe({
      next: () => this.notify.success('Clave actualizada', c.nombre_nivel),
      error: e => this.notify.error('Error', e.error?.detail ?? 'No se pudo guardar la clave'),
    });
  }

  guardarPlantel(): void {
    const p = this.plantelEdit();
    if (!p || !p.id) return;
    if (!p.nombre_plantel) {
      this.notify.warning('El nombre del plantel es obligatorio');
      return;
    }
    this.guardandoPlantel.set(true);
    this.api.patch(`/admin/planteles/${p.id}`, { nombre_plantel: p.nombre_plantel, clave_ct: p.clave_ct, is_active: p.is_active }).subscribe({
      next: () => {
        this.dlgPlantelVisible.set(false);
        this.guardandoPlantel.set(false);
        this.notify.success('Plantel actualizado');
        this.cargarPlanteles();
      },
      error: e => { this.guardandoPlantel.set(false); this.notify.error('Error', e.error?.detail ?? 'No se pudo guardar'); },
    });
  }

  // ── Grupos ─────────────────────────────────────────────────────────────────

  abrirNuevoGrupo(): void {
    this.grupoAdminEdit.set({ id: '', nombre_grupo: '', nombre_nivel: null, nombre_grado: null, grado_id: '', ciclo_escolar_id: '', capacidad_maxima: 35, inscritos: 0, turno: 'MATUTINO', is_active: true });
    this.dlgGrupoAdminVisible.set(true);
  }

  abrirEditarGrupo(row: any): void {
    const g: GrupoAdmin = row._original ?? row;
    this.grupoAdminEdit.set({ ...g });
    this.dlgGrupoAdminVisible.set(true);
  }

  guardarGrupoAdmin(): void {
    const g = this.grupoAdminEdit();
    if (!g) return;
    if (!g.nombre_grupo || !g.turno) {
      this.notify.warning('Nombre de grupo y turno son obligatorios');
      return;
    }
    if (!g.id && (!g.ciclo_escolar_id || !g.grado_id)) {
      this.notify.warning('Ciclo y grado son obligatorios al crear un grupo');
      return;
    }
    this.guardandoGrupo.set(true);
    const payload = { nombre_grupo: g.nombre_grupo, capacidad_maxima: g.capacidad_maxima, turno: g.turno,
      ciclo_escolar_id: g.ciclo_escolar_id || undefined, grado_id: g.grado_id || undefined, is_active: g.is_active };
    const req$ = g.id
      ? this.api.patch(`/admin/grupos/${g.id}`, payload)
      : this.api.post('/admin/grupos', payload);
    req$.subscribe({
      next: () => {
        this.dlgGrupoAdminVisible.set(false);
        this.guardandoGrupo.set(false);
        this.notify.success('Grupo guardado');
        this.cargarGrupos();
      },
      error: e => { this.guardandoGrupo.set(false); this.notify.error('Error', e.error?.detail ?? 'No se pudo guardar'); },
    });
  }

  // ── Crear usuario ─────────────────────────────────────────────────────────

  abrirCrearUsuario(): void {
    this.nuevoUsrForm = {
      nombre: '', apellido_paterno: '', apellido_materno: '',
      curp: '', email_institucional: '', genero: 'M',
      fecha_nacimiento: '', rol_id: '', plantel_id: null,
    };
    if (this.roles().length === 0) this.cargarRoles();
    this.crearUsrDlgVisible.set(true);
  }

  crearUsuario(): void {
    const f = this.nuevoUsrForm;
    if (!f.nombre || !f.apellido_paterno || !f.curp || !f.rol_id) {
      this.notify.warning('Datos incompletos', 'Nombre, apellido paterno, CURP y Rol son requeridos');
      return;
    }
    if (f.curp.length !== 18) {
      this.notify.warning('CURP inválida', 'La CURP debe tener 18 caracteres');
      return;
    }
    this.guardandoNuevoUsr.set(true);
    this.api.post('/admin/usuarios', {
      nombre: f.nombre, apellidoPaterno: f.apellido_paterno,
      apellidoMaterno: f.apellido_materno || null,
      curp: f.curp.toUpperCase(), emailInstitucional: f.email_institucional || null,
      genero: f.genero, fechaNacimiento: f.fecha_nacimiento || null,
      rolId: f.rol_id, plantelId: f.plantel_id || null,
    }).subscribe({
      next: (res: any) => {
        this.notify.success('Usuario creado',
          `Usuario: ${res.nombre_usuario} — Email: ${res.email_institucional}`);
        this.crearUsrDlgVisible.set(false);
        this.guardandoNuevoUsr.set(false);
        this.cargarUsuarios();
      },
      error: (e) => {
        this.notify.error('Error', e.error?.detail || e.error?.message || 'Error al crear usuario');
        this.guardandoNuevoUsr.set(false);
      },
    });
  }

  // ── Roles ──────────────────────────────────────────────────────────────────

  abrirEditarRol(row: any): void {
    const r = row._original ?? row;
    this.rolEdit.set(r);
    this.rolEditForm = { descripcion: r.descripcion ?? '', nivel_acceso: r.nivel_acceso ?? 4 };
    this.rolDlgVisible.set(true);
  }

  guardarRol(): void {
    const r = this.rolEdit();
    if (!r) return;
    this.guardandoRol.set(true);
    this.api.patch(`/admin/roles/${r.id}`, {
      descripcion: this.rolEditForm.descripcion,
      nivelAcceso: this.rolEditForm.nivel_acceso,
    }).subscribe({
      next: () => {
        this.notify.success('Éxito', 'Descripción del rol actualizada');
        this.rolDlgVisible.set(false);
        this.guardandoRol.set(false);
        this.cargarRoles();
      },
      error: (e) => { this.notify.error('Error', e.error?.detail ?? 'Error al guardar'); this.guardandoRol.set(false); },
    });
  }

  // ── Menús ──────────────────────────────────────────────────────────────────

  cargarMenusAdmin(): void {
    this.loadingMenus.set(true);
    this.api.get<any[]>('/admin/menus').subscribe({
      next: (m) => { this.menus.set(m); this.loadingMenus.set(false); },
      error: () => this.loadingMenus.set(false),
    });
  }

  abrirEditarMenu(row: any): void {
    const m = row._original ?? row;
    this.menuEdit.set(m);
    this.menuEditForm = {
      label: m.label ?? '',
      nivel_maximo: m.nivel_maximo ?? 99,
      nivel_minimo: m.nivel_minimo ?? 0,
      activo: m.activo !== false,
    };
    this.menuDlgVisible.set(true);
  }

  guardarMenu(): void {
    const m = this.menuEdit();
    if (!m) return;
    this.guardandoMenu.set(true);
    this.api.patch(`/admin/menus/${m.clave}`, {
      label: this.menuEditForm.label,
      nivelMaximo: this.menuEditForm.nivel_maximo,
      nivelMinimo: this.menuEditForm.nivel_minimo,
      activo: this.menuEditForm.activo,
    }).subscribe({
      next: () => {
        this.notify.success('Éxito', 'Menú actualizado');
        this.menuDlgVisible.set(false);
        this.guardandoMenu.set(false);
        this.cargarMenusAdmin();
      },
      error: (e) => { this.notify.error('Error', e.error?.detail ?? 'Error al guardar'); this.guardandoMenu.set(false); },
    });
  }

  // ── Permisos por rol ────────────────────────────────────────────────────────

  cargarPermisos(): void {
    this.loadingPermisos.set(true);
    const params: Record<string, string> = {};
    if (this.permisosRolFiltro) params['rol_id'] = this.permisosRolFiltro;
    this.api.get<any[]>('/admin/permisos-rol', params).subscribe({
      next: (p) => {
        this.permisos.set(p.map(x => ({ ...x, _saving: false })));
        this.loadingPermisos.set(false);
      },
      error: () => this.loadingPermisos.set(false),
    });
  }

  guardarPermiso(p: any): void {
    p._saving = true;
    this.api.put('/admin/permisos-rol', {
      rolId: p.rol_id, menuClave: p.menu_clave,
      puedeVer: p.puede_ver, puedeEditar: p.puede_editar,
      puedeCrear: p.puede_crear, puedeEliminar: p.puede_eliminar,
    }).subscribe({
      next: () => { p._saving = false; this.notify.success('Guardado', `Permiso actualizado: ${p.menu_clave}`); },
      error: (e) => { p._saving = false; this.notify.error('Error', e.error?.detail ?? 'Error'); },
    });
  }

  abrirNuevoPermiso(): void {
    this.nuevoPrmForm = { menu_clave: '', puede_ver: true, puede_editar: true, puede_crear: true, puede_eliminar: false };
    if (this.menus().length === 0) this.cargarMenusAdmin();
    this.nuevoPrmDlgVisible.set(true);
  }

  guardarNuevoPermiso(): void {
    if (!this.permisosRolFiltro || !this.nuevoPrmForm.menu_clave) {
      this.notify.warning('Faltan datos', 'Selecciona un rol y un módulo');
      return;
    }
    this.guardandoPermiso.set(true);
    this.api.put('/admin/permisos-rol', {
      rolId: this.permisosRolFiltro,
      menuClave: this.nuevoPrmForm.menu_clave,
      puedeVer: this.nuevoPrmForm.puede_ver,
      puedeEditar: this.nuevoPrmForm.puede_editar,
      puedeCrear: this.nuevoPrmForm.puede_crear,
      puedeEliminar: this.nuevoPrmForm.puede_eliminar,
    }).subscribe({
      next: () => {
        this.notify.success('Guardado', 'Permiso creado/actualizado');
        this.nuevoPrmDlgVisible.set(false);
        this.guardandoPermiso.set(false);
        this.cargarPermisos();
      },
      error: (e) => { this.notify.error('Error', e.error?.detail ?? 'Error'); this.guardandoPermiso.set(false); },
    });
  }

  // ── Editar Usuario ──
  usuarioDlgVisible = signal(false);
  usuarioEdit       = signal<UsuarioAdmin | null>(null);
  guardandoUsuario  = signal(false);
  usuarioEditForm   = {
    rol_id: '',
    plantel_id: null as string | null,
    is_active: true,
  };

  abrirEditarUsuario(row: any): void {
    const usr: UsuarioAdmin = row._original ?? row;
    this.usuarioEdit.set(usr);
    
    // Buscar rol por nombre
    const matchingRol = this.roles().find(r => r.nombre_rol === usr.rol);
    
    this.usuarioEditForm = {
      rol_id: matchingRol ? matchingRol.id : '',
      plantel_id: usr.plantel_id,
      is_active: usr.is_active,
    };
    this.usuarioDlgVisible.set(true);
  }

  guardarUsuario(): void {
    const usr = this.usuarioEdit();
    if (!usr) return;
    this.guardandoUsuario.set(true);
    
    const payload = {
      rol_id: this.usuarioEditForm.rol_id,
      plantel_id: this.usuarioEditForm.plantel_id,
      is_active: this.usuarioEditForm.is_active,
    };
    
    this.api.patch(`/admin/usuarios/${usr.id}`, payload).subscribe({
      next: () => {
        this.notify.success('Éxito', 'Usuario actualizado correctamente');
        this.usuarioDlgVisible.set(false);
        this.guardandoUsuario.set(false);
        this.cargarUsuarios();
      },
      error: (e) => {
        this.notify.error('Error', e.error?.detail || 'Error al actualizar usuario');
        this.guardandoUsuario.set(false);
      }
    });
  }

  // ── Marca / Identidad ──
  loadingMarca = signal(false);
  guardandoMarca = signal(false);
  marcaForm = {
    NOMBRE_INSTITUCION: '',
    ESLOGAN: '',
    LOGO_URL: '',
    FAVICON_URL: '',
    COLOR_PRIMARIO: '#D02030',
  };

  cargarMarca(): void {
    this.loadingMarca.set(true);
    this.api.get<any[]>('/admin/marca').subscribe({
      next: (items) => {
        this.marcaForm = {
          NOMBRE_INSTITUCION: '',
          ESLOGAN: '',
          LOGO_URL: '',
          FAVICON_URL: '',
          COLOR_PRIMARIO: '#D02030',
        };
        for (const item of items) {
          const key = item.tipo_elemento as keyof typeof this.marcaForm;
          if (key === 'COLOR_PRIMARIO') {
            this.marcaForm[key] = item.color_hex || '#D02030';
          } else if (key === 'LOGO_URL' || key === 'FAVICON_URL') {
            this.marcaForm[key] = item.url_archivo || '';
          } else if (key in this.marcaForm) {
            this.marcaForm[key] = item.texto_elemento || '';
          }
        }
        this.loadingMarca.set(false);
      },
      error: () => this.loadingMarca.set(false),
    });
  }

  guardarMarca(): void {
    if (this.ctx.nivelAcceso() > 0) {
      this.notify.error('Acceso Denegado', 'Solo ADMIN_GLOBAL puede modificar la marca institucional.');
      return;
    }
    this.guardandoMarca.set(true);
    const payload = [
      { tipo_elemento: 'NOMBRE_INSTITUCION', valor: this.marcaForm.NOMBRE_INSTITUCION },
      { tipo_elemento: 'ESLOGAN', valor: this.marcaForm.ESLOGAN },
      { tipo_elemento: 'LOGO_URL', valor: this.marcaForm.LOGO_URL },
      { tipo_elemento: 'FAVICON_URL', valor: this.marcaForm.FAVICON_URL },
      { tipo_elemento: 'COLOR_PRIMARIO', valor: this.marcaForm.COLOR_PRIMARIO },
    ];
    this.api.put('/admin/marca', payload).subscribe({
      next: () => {
        this.notify.success('Éxito', 'Identidad institucional actualizada');
        this.guardandoMarca.set(false);
        this.cargarMarca();
      },
      error: (e) => {
        this.notify.error('Error', e.error?.detail || 'Error al guardar la marca');
        this.guardandoMarca.set(false);
      },
    });
  }

  // ── Reglas de Promoción ─────────────────────────────────────────────────────

  cargarReglasPromocion(): void {
    this.loadingReglas.set(true);
    this.api.get<any[]>('/admin/reglas-promocion').subscribe({
      next: (niveles) => {
        this.nivelesReglas.set(niveles);
        this.loadingReglas.set(false);
      },
      error: () => this.loadingReglas.set(false),
    });
  }

  actualizarRegla(nivel: any): void {
    this.guardandoRegla.set(true);
    const payload = {
      minimo_aprobatorio:      nivel.minimo_aprobatorio,
      max_materias_reprobadas: nivel.max_materias_reprobadas,
      min_asistencia_pct:      nivel.min_asistencia_pct,
      permite_recursamiento:   nivel.permite_recursamiento,
      max_anios_reprobados:    nivel.max_anios_reprobados,
      tiene_examen_extra:      nivel.tiene_examen_extra,
    };
    this.api.patch<any>(`/admin/reglas-promocion/${nivel.id}`, payload).subscribe({
      next: () => {
        this.notify.success('Guardado', `Reglas de ${nivel.nombre_nivel} actualizadas`);
        this.guardandoRegla.set(false);
      },
      error: (e) => {
        this.notify.error('Error', e.error?.message ?? 'No se pudieron guardar las reglas');
        this.guardandoRegla.set(false);
      },
    });
  }

  evaluarPromocion(): void {
    const ciclo = this.ctx.ciclo();
    if (!ciclo?.id) {
      this.notify.warning('Sin ciclo seleccionado', 'Selecciona un ciclo escolar en la barra superior');
      return;
    }
    this.evaluandoPromocion.set(true);
    const payload: Record<string, string> = { ciclo_id: ciclo.id };
    const plantel = this.ctx.plantel();
    if (plantel?.id) payload['plantel_id'] = plantel.id;

    this.api.post<any>('/admin/reglas-promocion/evaluar', payload).subscribe({
      next: (res) => {
        const r = res.resultado ? JSON.parse(res.resultado) : res;
        this.notify.success(
          'Evaluación completada',
          `Promovidos: ${r.promovidos} · Reprobados: ${r.reprobados} · Sin notas: ${r.sin_calificacion}`
        );
        this.evaluandoPromocion.set(false);
      },
      error: (e) => {
        this.notify.error('Error', e.error?.message ?? 'No se pudo evaluar la promoción');
        this.evaluandoPromocion.set(false);
      },
    });
  }

  // ── SEPOMEX sync ─────────────────────────────────────────────────────────────

  nivelAccesoDesc(nivel: number): string {
    const map: Record<number, string> = {
      0: 'Admin Global', 1: 'Admin Plantel', 2: 'Director/Coordinador',
      3: 'Coord. Académico/Orientador', 4: 'Docente/Apoyo', 5: 'Padre/Alumno',
    };
    return map[nivel] ?? `Nivel ${nivel}`;
  }

  iniciarSyncSepomex(): void {
    this.syncSepomexLoading.set(true);
    this.syncSepomexTaskId.set(null);
    this.syncSepomexEstado.set(null);
    this.syncSepomexResultado.set('');
    this.syncSepomexError.set('');
    if (this._syncPollInterval) { clearInterval(this._syncPollInterval); this._syncPollInterval = null; }

    this.api.post<any>('/admin/sepomex/sync', {}).subscribe({
      next: (res) => {
        this.syncSepomexLoading.set(false);
        this.syncSepomexTaskId.set(res.task_id);
        this.syncSepomexEstado.set(res.estado ?? 'PENDING');
        this._iniciarPollSepomex(res.task_id);
      },
      error: (e) => {
        this.syncSepomexLoading.set(false);
        this.notify.error('Error al encolar sincronización', e.error?.detail ?? e.message ?? 'Error desconocido');
      },
    });
  }

  private _iniciarPollSepomex(taskId: string): void {
    this._syncPollInterval = setInterval(() => {
      this.api.get<any>(`/admin/sepomex/sync/${taskId}`).subscribe({
        next: (res) => {
          this.syncSepomexEstado.set(res.estado);
          if (res.estado === 'SUCCESS') {
            clearInterval(this._syncPollInterval!);
            this._syncPollInterval = null;
            const r = res.resultado ?? {};
            this.syncSepomexResultado.set(
              typeof r === 'object'
                ? `${r.estados_insertados ?? 0} estados · ${r.municipios_insertados ?? 0} municipios · ${r.codigos_insertados ?? 0} CPs importados.`
                : String(r)
            );
            this.notify.success('SEPOMEX sincronizado', 'El catálogo se actualizó correctamente.');
          } else if (res.estado === 'FAILURE') {
            clearInterval(this._syncPollInterval!);
            this._syncPollInterval = null;
            this.syncSepomexError.set(res.error ?? 'Error desconocido en el worker.');
            this.notify.error('Error en sincronización SEPOMEX', this.syncSepomexError());
          }
        },
      });
    }, 3000);
  }

  cargarFranjas(): void {
    this.loadingFranjas.set(true);
    if (this.niveles().length === 0) {
      this.cargarNiveles();
    }

    this.api.get<any[]>('/horario-franjas').subscribe({
      next: res => {
        this.franjas.set(res || []);
        this.loadingFranjas.set(false);
      },
      error: () => this.loadingFranjas.set(false)
    });
  }

  abrirFranjaDialog(f?: any): void {
    if (f) {
      this.editFranjaEntry.set(f);
      this.franjaForm = { ...f, hora_inicio: String(f.hora_inicio||'').slice(0,5), hora_fin: String(f.hora_fin||'').slice(0,5) };
    } else {
      this.editFranjaEntry.set(null);
      this.franjaForm = { dia_semana: 1, turno: 'MATUTINO', is_active: true };
    }
    this.showFranjaDialog.set(true);
  }

  guardarFranja(): void {
    if (!this.franjaForm.dia_semana || !this.franjaForm.hora_inicio || !this.franjaForm.hora_fin) {
      this.notify.warning('Faltan datos', 'Completa día y horas.');
      return;
    }
    this.guardandoFranja.set(true);
    const body = { ...this.franjaForm };
    const req = this.editFranjaEntry() 
      ? this.api.put(`/horario-franjas/${this.editFranjaEntry().id}`, body)
      : this.api.post(`/horario-franjas`, body);
    
    req.subscribe({
      next: () => {
        this.notify.success('Franja guardada correctamente.');
        this.guardandoFranja.set(false);
        this.showFranjaDialog.set(false);
        this.cargarFranjas();
      },
      error: () => {
        this.notify.error('Error', 'No se pudo guardar la franja.');
        this.guardandoFranja.set(false);
      }
    });
  }

  eliminarFranja(f: any): void {
    if (!confirm('¿Eliminar esta franja?')) return;
    this.api.delete(`/horario-franjas/${f.id}`).subscribe({
      next: () => {
        this.notify.success('Franja eliminada.');
        this.cargarFranjas();
      }
    });
  }
}
