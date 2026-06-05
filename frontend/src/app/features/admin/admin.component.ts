/**
 * FASE 12 — Módulo de Administración.
 * Tabs: Usuarios | Ciclos Escolares | Planteles | Grupos
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
    ImportButtonComponent,
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
        <!-- ══ USUARIOS ══════════════════════════════════════════════════════ -->
        <p-tabpanel value="usuarios">
          <div class="tab-toolbar">
            <input pInputText type="text" placeholder="Buscar usuario..."
              [(ngModel)]="buscarUsuario"
              (input)="dtUsuarios.filterGlobal($any($event.target).value,'contains')"
              style="width:260px" />
            <div style="margin-left:auto;display:flex;gap:.5rem;align-items:center">
              <span style="font-size:.8rem;color:#64748b">Importar:</span>
              <app-import-button entidad="alumnos" [onSuccess]="recargarUsuarios" />
              <app-import-button entidad="profesores" [onSuccess]="recargarUsuarios" />
              <p-button label="Nuevo usuario" icon="pi pi-plus" size="small"
                (onClick)="abrirCrearUsuario()" />
            </div>
          </div>

          <p-table #dtUsuarios [value]="usuarios()" [loading]="loadingUsuarios()"
            [globalFilterFields]="['nombre_usuario','nombre_completo','rol','nombre_plantel']"
            [paginator]="true" [rows]="20"
            styleClass="p-datatable-sm p-datatable-striped">
            <ng-template pTemplate="header">
              <tr>
                <th pSortableColumn="nombre_usuario">Usuario <p-sortIcon field="nombre_usuario"/></th>
                <th pSortableColumn="nombre_completo">Nombre</th>
                <th pSortableColumn="rol" style="width:160px">Rol</th>
                <th style="width:160px">Alcance</th>
                <th style="width:90px;text-align:center">Estado</th>
                <th style="width:80px"></th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-u>
              <tr>
                <td><code>{{ u.nombre_usuario }}</code></td>
                <td>{{ u.nombre_completo }}</td>
                <td>
                  <p-tag [value]="u.rol" [severity]="rolSeverity(u.nivel_acceso)" />
                </td>
                <td style="font-size:0.8rem;color:#64748b">
                  @if (u.nombre_plantel) {
                    {{ u.nombre_plantel }}@if (u.nombre_nivel) { · {{ u.nombre_nivel }} }
                  } @else { <em>Global</em> }
                </td>
                <td style="text-align:center">
                  <p-tag [value]="u.is_active ? 'Activo' : 'Inactivo'"
                    [severity]="u.is_active ? 'success' : 'secondary'" />
                </td>
                <td>
                  <p-button icon="pi pi-pencil" [text]="true" [rounded]="true"
                    pTooltip="Editar" (onClick)="abrirEditarUsuario(u)" />
                </td>
              </tr>
            </ng-template>
            <ng-template pTemplate="emptymessage">
              <tr><td colspan="6" style="text-align:center;padding:2rem;color:#94a3b8">Sin usuarios</td></tr>
            </ng-template>
          </p-table>
        </p-tabpanel>

        <!-- ══ CICLOS ══════════════════════════════════════════════════════════ -->
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

        <!-- ══ PLANTELES ══════════════════════════════════════════════════════ -->
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

        <!-- ══ GRUPOS ══════════════════════════════════════════════════════════ -->
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

        <!-- ══ MARCA / IDENTIDAD INSTITUCIONAL ══════════════════════════════ -->
        <p-tabpanel value="marca">
          <div class="tab-toolbar">
            <span style="font-size:.9rem;font-weight:600">Configuración de identidad visual y datos institucionales</span>
            <p-button label="Guardar identidad" icon="pi pi-save" size="small"
              [loading]="savingMarca()" (onClick)="guardarMarca()" styleClass="ml-auto" />
          </div>

          <div class="marca-grid">
            <!-- Datos básicos -->
            <div class="marca-section">
              <h4 class="marca-sec-title">Datos institucionales</h4>
              <div class="form-grid">
                <label>Nombre institución</label>
                <input pInputText [(ngModel)]="marca['NOMBRE_INSTITUCION']" placeholder="Instituto Nevadi" />
                <label>Nombre del sistema</label>
                <input pInputText [(ngModel)]="marca['NOMBRE_SISTEMA']" placeholder="ADES" />
                <label>Slogan</label>
                <input pInputText [(ngModel)]="marca['SLOGAN']" />
                <label>Texto pie de página</label>
                <input pInputText [(ngModel)]="marca['FOOTER_TEXTO']" />
                <label>Meta descripción</label>
                <input pInputText [(ngModel)]="marca['META_DESCRIPCION']" />
              </div>
            </div>

            <!-- Colores -->
            <div class="marca-section">
              <h4 class="marca-sec-title">Colores de marca</h4>
              <div class="form-grid">
                <label>Color primario</label>
                <div class="color-row">
                  <input type="color" [(ngModel)]="marca['COLOR_PRIMARIO']" class="color-picker" />
                  <input pInputText [(ngModel)]="marca['COLOR_PRIMARIO']" maxlength="7" style="width:100px;font-family:monospace" />
                  <div class="color-preview" [style.background]="marca['COLOR_PRIMARIO']"></div>
                </div>
                <label>Color secundario</label>
                <div class="color-row">
                  <input type="color" [(ngModel)]="marca['COLOR_SECUNDARIO']" class="color-picker" />
                  <input pInputText [(ngModel)]="marca['COLOR_SECUNDARIO']" maxlength="7" style="width:100px;font-family:monospace" />
                  <div class="color-preview" [style.background]="marca['COLOR_SECUNDARIO']"></div>
                </div>
                <label>Color acento</label>
                <div class="color-row">
                  <input type="color" [(ngModel)]="marca['COLOR_ACENTO']" class="color-picker" />
                  <input pInputText [(ngModel)]="marca['COLOR_ACENTO']" maxlength="7" style="width:100px;font-family:monospace" />
                  <div class="color-preview" [style.background]="marca['COLOR_ACENTO']"></div>
                </div>
              </div>
            </div>

            <!-- Archivos -->
            <div class="marca-section">
              <h4 class="marca-sec-title">Archivos (rutas relativas o URLs)</h4>
              <div class="form-grid">
                <label>Logo (URL)</label>
                <input pInputText [(ngModel)]="marca['LOGO_URL']" placeholder="nevadi-logo.jpg" />
                <label>Favicon (URL)</label>
                <input pInputText [(ngModel)]="marca['FAVICON_URL']" placeholder="favicon.png" />
              </div>
              <p class="marca-nota">
                Los archivos deben subirse al servidor y colocarse en la carpeta <code>/public/</code> del frontend.
                Las URLs se aplican al recargar la página.
              </p>
            </div>

            <!-- Vista previa -->
            <div class="marca-section">
              <h4 class="marca-sec-title">Vista previa del topbar</h4>
              <div class="topbar-preview" [style.background]="marca['COLOR_PRIMARIO']">
                <div class="tp-logo">{{ (marca['NOMBRE_SISTEMA'] || 'ADES')[0] }}</div>
                <div class="tp-text">
                  <span class="tp-title">{{ marca['NOMBRE_SISTEMA'] || 'ADES' }}</span>
                  <span class="tp-sub">{{ marca['NOMBRE_INSTITUCION'] || 'Institución' }}</span>
                </div>
              </div>
              @if (marca['SLOGAN']) {
                <p class="slogan-preview">{{ marca['SLOGAN'] }}</p>
              }
            </div>
          </div>
        </p-tabpanel>

        <!-- ══ AUDITORÍA ═══════════════════════════════════════════════════ -->
        <p-tabpanel value="auditoria">
          <div class="tab-toolbar">
            <span style="font-size:.9rem;font-weight:600">Registro de auditoría — últimas 200 mutaciones</span>
            <p-button icon="pi pi-refresh" [text]="true" severity="secondary" size="small"
              (onClick)="cargarAuditoria()" pTooltip="Actualizar" />
          </div>

          <p-table [value]="auditLogs()" [loading]="loadingAuditoria()"
            styleClass="p-datatable-sm p-datatable-striped" [rows]="50" [paginator]="true"
            [showCurrentPageReport]="true" currentPageReportTemplate="{first}-{last} de {totalRecords}">
            <ng-template pTemplate="header">
              <tr>
                <th style="width:140px">Fecha</th>
                <th style="width:80px">Método</th>
                <th>Endpoint</th>
                <th style="width:90px">Entidad</th>
                <th style="width:80px">Código</th>
                <th style="width:60px">ms</th>
                <th>Usuario</th>
                <th style="width:120px">IP</th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-row>
              <tr>
                <td style="font-size:.75rem">{{ row.fccreacion | date:'dd/MM/yy HH:mm' }}</td>
                <td>
                  <p-tag [value]="row.accion"
                    [severity]="row.accion==='DELETE'?'danger':row.accion==='INSERT'?'success':'warn'" />
                </td>
                <td style="font-size:.75rem;color:var(--text-color-secondary)">{{ row.endpoint }}</td>
                <td style="font-size:.8rem"><code>{{ row.entidad }}</code></td>
                <td>
                  <p-tag [value]="row.codigo_respuesta"
                    [severity]="row.codigo_respuesta < 300 ? 'success' : row.codigo_respuesta < 500 ? 'warn' : 'danger'" />
                </td>
                <td style="font-size:.75rem">{{ row.duracion_ms }}</td>
                <td style="font-size:.8rem">{{ row.nombre_usuario ?? '—' }}</td>
                <td style="font-size:.75rem;color:var(--text-color-secondary)">{{ row.ip_origen ?? '—' }}</td>
              </tr>
            </ng-template>
            <ng-template pTemplate="emptymessage">
              <tr><td [colSpan]="8" style="text-align:center;padding:2rem;color:#94a3b8">
                Sin registros de auditoría aún — las mutaciones se registrarán automáticamente
              </td></tr>
            </ng-template>
          </p-table>
        </p-tabpanel>
      </p-tabpanels>
    </p-tabs>

    <!-- ══ DIÁLOGO CREAR USUARIO ═══════════════════════════════════════════ -->
    <p-dialog [(visible)]="dlgCrearUsuario" header="Nuevo usuario"
      [modal]="true" [draggable]="false" [style]="{width:'500px'}">
      <div class="form-grid">
        <label>Nombre(s) *</label>
        <input pInputText [(ngModel)]="nuevoUsuario.nombre" />
        <label>Apellido paterno *</label>
        <input pInputText [(ngModel)]="nuevoUsuario.apellido_paterno" />
        <label>Apellido materno</label>
        <input pInputText [(ngModel)]="nuevoUsuario.apellido_materno" />
        <label>CURP *</label>
        <input pInputText [(ngModel)]="nuevoUsuario.curp" maxlength="18"
          style="font-family:monospace;text-transform:uppercase" />
        <label>Género</label>
        <p-select [options]="[{l:'Masculino',v:'M'},{l:'Femenino',v:'F'}]"
          [(ngModel)]="nuevoUsuario.genero" optionLabel="l" optionValue="v" [showClear]="true" />
        <label>Rol *</label>
        <p-select [options]="roles()" [(ngModel)]="nuevoUsuario.rol_id"
          optionLabel="nombre_rol" optionValue="id" placeholder="Seleccionar rol" />
        <label>Plantel</label>
        <p-select [options]="planteles()" [(ngModel)]="nuevoUsuario.plantel_id"
          optionLabel="nombre_plantel" optionValue="id"
          placeholder="Global (sin plantel)" [showClear]="true" />
        <label>Nivel educativo</label>
        <p-select [options]="nivelesAdmin()" [(ngModel)]="nuevoUsuario.nivel_educativo_id"
          optionLabel="nombre_nivel" optionValue="id"
          placeholder="Todos los niveles" [showClear]="true"
          [disabled]="!nuevoUsuario.plantel_id" />
        <label>Email institucional</label>
        <input pInputText [(ngModel)]="nuevoUsuario.email_institucional"
          type="email" placeholder="Autogenerado si se deja vacío" />
      </div>
      <p class="dlg-nota">
        El usuario podrá iniciar sesión mediante el SSO del instituto (Authentik).
        La contraseña se gestiona desde el portal de Authentik.
      </p>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="dlgCrearUsuario=false" />
        <p-button label="Crear usuario" icon="pi pi-plus" [loading]="savingUsuario()"
          (onClick)="crearUsuario()" />
      </ng-template>
    </p-dialog>

    <!-- ══ DIÁLOGO EDITAR USUARIO ══════════════════════════════════════════ -->
    <p-dialog [(visible)]="dlgUsuario" [header]="'Editar: ' + (usuarioEdit?.nombre_usuario ?? '')"
      [modal]="true" [draggable]="false" [style]="{width:'480px'}">
      @if (usuarioEdit) {
        <div class="form-grid">
          <label>Rol</label>
          <p-select [options]="roles()" [(ngModel)]="usuarioEdit.rol_id"
            optionLabel="nombre_rol" optionValue="id" placeholder="Seleccionar rol" />

          <label>Plantel</label>
          <p-select [options]="[{id: null, nombre_plantel: '— Global —'}, ...planteles()]"
            [(ngModel)]="usuarioEdit.plantel_id"
            optionLabel="nombre_plantel" optionValue="id" placeholder="Sin restricción" />

          <label>Escuela (nivel)</label>
          <p-select [options]="[{id: null, nombre_nivel: '— Todos —'}, ...nivelesOpt()]"
            [(ngModel)]="usuarioEdit.nivel_educativo_id"
            optionLabel="nombre_nivel" optionValue="id" placeholder="Sin restricción" />

          <label>Estado</label>
          <p-toggleswitch [(ngModel)]="usuarioEdit.is_active" />
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="dlgUsuario=false" />
          <p-button label="Guardar" icon="pi pi-save" [loading]="saving()" (onClick)="guardarUsuario()" />
        </ng-template>
      }
    </p-dialog>

    <!-- ══ DIÁLOGO CICLO ════════════════════════════════════════════════════ -->
    <p-dialog [(visible)]="dlgCiclo" [header]="cicloEdit?.id ? 'Editar ciclo' : 'Nuevo ciclo'"
      [modal]="true" [draggable]="false" [style]="{width:'440px'}">
      @if (cicloEdit) {
        <div class="form-grid">
          <label>Nombre</label>
          <input pInputText [(ngModel)]="cicloEdit.nombre_ciclo" placeholder="2026-2027" />

          <label>Nivel educativo</label>
          <p-select [options]="nivelesOpt()" [(ngModel)]="cicloEdit.nivel_educativo_id"
            optionLabel="nombre_nivel" optionValue="id"
            [disabled]="!!cicloEdit.id" />

          <label>Inicio</label>
          <p-datepicker [(ngModel)]="cicloEdit.fecha_inicio" dateFormat="dd/mm/yy" />

          <label>Fin</label>
          <p-datepicker [(ngModel)]="cicloEdit.fecha_fin" dateFormat="dd/mm/yy" />

          <label>Tipo</label>
          <p-select [options]="['ANUAL','SEMESTRAL','TRIMESTRAL']"
            [(ngModel)]="cicloEdit.tipo_ciclo" />

          <label>¿Vigente?</label>
          <p-toggleswitch [(ngModel)]="cicloEdit.es_vigente" />
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="dlgCiclo=false" />
          <p-button label="Guardar" icon="pi pi-save" [loading]="saving()" (onClick)="guardarCiclo()" />
        </ng-template>
      }
    </p-dialog>

    <!-- ══ DIÁLOGO PLANTEL ══════════════════════════════════════════════════ -->
    <p-dialog [(visible)]="dlgPlantel" header="Editar plantel"
      [modal]="true" [draggable]="false" [style]="{width:'400px'}">
      @if (plantelEdit) {
        <div class="form-grid">
          <label>Nombre</label>
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

    <!-- ══ DIÁLOGO GRUPO ════════════════════════════════════════════════════ -->
    <p-dialog [(visible)]="dlgGrupo" [header]="grupoEdit?.id ? 'Editar grupo' : 'Nuevo grupo'"
      [modal]="true" [draggable]="false" [style]="{width:'420px'}">
      @if (grupoEdit) {
        <div class="form-grid">
          <label>Nombre</label>
          <input pInputText [(ngModel)]="grupoEdit.nombre_grupo" maxlength="10" placeholder="A" />
          <label>Capacidad</label>
          <input pInputText type="number" [(ngModel)]="grupoEdit.capacidad_maxima" min="1" max="60" />
          <label>Turno</label>
          <p-select [options]="['MATUTINO','VESPERTINO','NOCTURNO']"
            [(ngModel)]="grupoEdit.turno" />
          @if (grupoEdit.id) {
            <label>Estado</label>
            <p-toggleswitch [(ngModel)]="grupoEdit.is_active" />
          }
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="dlgGrupo=false" />
          <p-button label="Guardar" icon="pi pi-save" [loading]="saving()" (onClick)="guardarGrupo()" />
        </ng-template>
      }
    </p-dialog>
  `,
  styles: [`
    .page-header { margin-bottom: 1rem; }
    .page-header h2 { margin: 0; }
    .subtitle { font-size: 0.82rem; color: var(--text-color-secondary); margin: 0; }
    .tab-toolbar { display: flex; align-items: center; gap: 0.75rem; margin-bottom: 1rem; }
    .form-grid { display: grid; grid-template-columns: 130px 1fr; gap: 0.75rem 1rem; align-items: center; padding: 0.5rem 0; }
    .form-grid label { font-size: 0.85rem; color: var(--text-color-secondary); font-weight: 600; }
    .nivel-chip {
      display: inline-block; font-size: 0.68rem; font-weight: 700; text-transform: uppercase;
      letter-spacing: .04em; padding: 0.1rem 0.4rem; border-radius: 3px;
      background: var(--primary-100); color: var(--primary-700); margin-right: 0.4rem;
    }
    .ocupacion { font-weight: 600; font-size: 0.85rem; color: var(--green-700); }
    .ocupacion.lleno { color: var(--red-600); }
    .dlg-nota { font-size: .78rem; color: var(--text-color-secondary); margin: .75rem 0 0; border-top: 1px solid var(--surface-200); padding-top: .6rem; }
    :host ::ng-deep .ctx-selector { min-width: 200px; }
    /* ── Marca / Identidad ─────────────────────────── */
    .marca-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1.25rem; }
    .marca-section { background: var(--surface-50); border: 1px solid var(--surface-200); border-radius: 10px; padding: 1rem; }
    .marca-sec-title { font-size: .8rem; font-weight: 700; text-transform: uppercase; letter-spacing: .05em; color: var(--primary-color); margin: 0 0 .75rem; }
    .color-row { display: flex; align-items: center; gap: .5rem; }
    .color-picker { width: 38px; height: 32px; border: 1px solid var(--surface-300); border-radius: 6px; cursor: pointer; padding: 2px; }
    .color-preview { width: 28px; height: 28px; border-radius: 4px; border: 1px solid var(--surface-300); flex-shrink: 0; }
    .marca-nota { font-size: .75rem; color: var(--text-color-secondary); margin: .75rem 0 0; }
    .marca-nota code { background: var(--surface-100); padding: .1rem .3rem; border-radius: 3px; }
    .topbar-preview { display: flex; align-items: center; gap: .75rem; padding: .75rem 1rem; border-radius: 8px; }
    .tp-logo { width: 36px; height: 36px; border-radius: 8px; background: rgba(255,255,255,.25); display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 1.1rem; color: #fff; }
    .tp-title { display: block; font-size: .9rem; font-weight: 700; color: #fff; line-height: 1.2; }
    .tp-sub   { display: block; font-size: .7rem; color: rgba(255,255,255,.8); }
    .slogan-preview { font-size: .8rem; font-style: italic; color: var(--text-color-secondary); margin: .5rem 0 0; text-align: center; }
  `],
})
export class AdminComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly ctx = inject(ContextService);
  private readonly msg = inject(MessageService);

  tabActivo = signal('usuarios');

  // ── Data signals ──────────────────────────────────────────────────────────
  usuarios       = signal<UsuarioAdmin[]>([]);
  ciclos         = signal<CicloAdmin[]>([]);
  planteles      = signal<PlantelAdmin[]>([]);
  grupos         = signal<GrupoAdmin[]>([]);
  nivelesOpt     = signal<NivelOpt[]>([]);
  roles          = signal<RolOpt[]>([]);
  ciclosFiltro   = signal<CicloAdmin[]>([]);

  loadingUsuarios  = signal(false);
  loadingCiclos    = signal(false);
  loadingPlanteles = signal(false);
  loadingGrupos    = signal(false);
  saving           = signal(false);

  buscarUsuario = '';
  cicloFiltroId: string | null = null;

  // ── Dialogs ───────────────────────────────────────────────────────────────
  dlgUsuario       = false;
  dlgCiclo         = false;
  dlgPlantel       = false;
  dlgGrupo         = false;
  dlgCrearUsuario  = false;

  usuarioEdit: any  = null;
  cicloEdit: any    = null;
  plantelEdit: any  = null;
  grupoEdit: any    = null;
  nuevoUsuario: any = {};
  savingUsuario     = signal(false);

  nivelesAdmin      = signal<NivelOpt[]>([]);
  savingMarca       = signal(false);
  auditLogs         = signal<any[]>([]);
  loadingAuditoria  = signal(false);
  marca: Record<string, string> = {};

  readonly recargarUsuarios = () => this.cargarUsuarios();

  ngOnInit(): void {
    this.cargarCatalogos();
    this.cargarUsuarios();
    this.cargarCiclos();
    this.cargarPlanteles();
    this.cargarGrupos();
  }

  onTabChange(valor: any): void {
    if (!valor) return;
    this.tabActivo.set(valor.toString());
    if (valor === 'grupos') this.cargarGrupos();
    if (valor === 'marca') this.cargarMarca();
    if (valor === 'auditoria') this.cargarAuditoria();
  }

  cargarAuditoria(): void {
    this.loadingAuditoria.set(true);
    this.api.get<any[]>('/auditoria', { limite: 200 }).subscribe({
      next: rows => { this.auditLogs.set(rows); this.loadingAuditoria.set(false); },
      error: () => this.loadingAuditoria.set(false),
    });
  }

  private cargarCatalogos(): void {
    this.api.get<NivelOpt[]>('/catalogs/niveles').subscribe(n => {
      this.nivelesOpt.set(n);
      this.nivelesAdmin.set(n);
    });
    this.api.get<RolOpt[]>('/catalogs/roles').subscribe(r => this.roles.set(r));
  }

  cargarMarca(): void {
    this.api.get<any[]>('/admin/marca').subscribe({
      next: items => {
        this.marca = {};
        items.forEach(i => {
          this.marca[i.tipo_elemento] = i.color_hex || i.texto_elemento || i.url_archivo || '';
        });
      },
      error: () => {},
    });
  }

  guardarMarca(): void {
    this.savingMarca.set(true);
    const payload = Object.entries(this.marca).map(([tipo, valor]) => ({
      tipo_elemento: tipo,
      valor,
    }));
    this.api.put('/admin/marca', payload).subscribe({
      next: () => {
        this.msg.add({ severity: 'success', summary: 'Guardado', detail: 'Identidad institucional actualizada' });
        this.savingMarca.set(false);
      },
      error: e => {
        this.msg.add({ severity: 'error', summary: 'Error', detail: e.error?.detail ?? 'Error al guardar' });
        this.savingMarca.set(false);
      },
    });
  }

  abrirCrearUsuario(): void {
    this.nuevoUsuario = {
      nombre: '', apellido_paterno: '', apellido_materno: '',
      curp: '', genero: null, rol_id: null,
      plantel_id: null, nivel_educativo_id: null, email_institucional: '',
    };
    this.dlgCrearUsuario = true;
  }

  crearUsuario(): void {
    if (!this.nuevoUsuario.nombre || !this.nuevoUsuario.apellido_paterno ||
        !this.nuevoUsuario.curp || !this.nuevoUsuario.rol_id) {
      this.msg.add({ severity: 'warn', summary: 'Campos requeridos',
        detail: 'Nombre, apellido paterno, CURP y rol son obligatorios' });
      return;
    }
    this.savingUsuario.set(true);
    const payload = {
      ...this.nuevoUsuario,
      curp: this.nuevoUsuario.curp.toUpperCase().trim(),
      email_institucional: this.nuevoUsuario.email_institucional || null,
    };
    this.api.post('/admin/usuarios', payload).subscribe({
      next: (u: any) => {
        this.usuarios.update(list => [u, ...list]);
        this.dlgCrearUsuario = false;
        this.savingUsuario.set(false);
        this.msg.add({ severity: 'success', summary: 'Usuario creado',
          detail: `${u.nombre_completo} — usuario: ${u.nombre_usuario}` });
      },
      error: e => {
        this.savingUsuario.set(false);
        this.msg.add({ severity: 'error', summary: 'Error',
          detail: e.error?.detail ?? 'No se pudo crear el usuario' });
      },
    });
  }

  cargarUsuarios(): void {
    this.loadingUsuarios.set(true);
    this.api.get<UsuarioAdmin[]>('/admin/usuarios').subscribe({
      next: u => { this.usuarios.set(u); this.loadingUsuarios.set(false); },
      error: () => this.loadingUsuarios.set(false),
    });
  }

  cargarCiclos(): void {
    this.loadingCiclos.set(true);
    this.api.get<CicloAdmin[]>('/admin/ciclos').subscribe({
      next: c => {
        this.ciclos.set(c);
        this.ciclosFiltro.set(c);
        this.loadingCiclos.set(false);
      },
      error: () => this.loadingCiclos.set(false),
    });
  }

  cargarPlanteles(): void {
    this.loadingPlanteles.set(true);
    this.api.get<PlantelAdmin[]>('/planteles').subscribe({
      next: p => { this.planteles.set(p); this.loadingPlanteles.set(false); },
      error: () => this.loadingPlanteles.set(false),
    });
  }

  cargarGrupos(): void {
    this.loadingGrupos.set(true);
    const params: Record<string, string> = {};
    if (this.cicloFiltroId) params['ciclo_id'] = this.cicloFiltroId;
    this.api.get<GrupoAdmin[]>('/admin/grupos', params).subscribe({
      next: g => { this.grupos.set(g); this.loadingGrupos.set(false); },
      error: () => this.loadingGrupos.set(false),
    });
  }

  rolSeverity(nivel: number): 'success' | 'warn' | 'info' | 'secondary' {
    if (nivel === 0) return 'success';
    if (nivel === 1) return 'warn';
    if (nivel <= 3)  return 'info';
    return 'secondary';
  }

  // ── Usuarios ───────────────────────────────────────────────────────────────
  abrirEditarUsuario(u: UsuarioAdmin): void {
    const rol = this.roles().find(r => r.nombre_rol === u.rol);
    this.usuarioEdit = {
      id: u.id,
      nombre_usuario: u.nombre_usuario,
      rol_id: rol?.id ?? null,
      plantel_id: u.plantel_id,
      nivel_educativo_id: u.nivel_educativo_id,
      is_active: u.is_active,
    };
    this.dlgUsuario = true;
  }

  guardarUsuario(): void {
    if (!this.usuarioEdit) return;
    this.saving.set(true);
    const payload: any = {};
    if (this.usuarioEdit.rol_id)           payload.rol_id              = this.usuarioEdit.rol_id;
    if ('plantel_id' in this.usuarioEdit)  payload.plantel_id          = this.usuarioEdit.plantel_id;
    if ('nivel_educativo_id' in this.usuarioEdit) payload.nivel_educativo_id = this.usuarioEdit.nivel_educativo_id;
    payload.is_active = this.usuarioEdit.is_active;

    this.api.patch(`/admin/usuarios/${this.usuarioEdit.id}`, payload).subscribe({
      next: () => {
        this.msg.add({ severity: 'success', summary: 'Guardado', detail: 'Usuario actualizado' });
        this.dlgUsuario = false;
        this.saving.set(false);
        this.cargarUsuarios();
      },
      error: e => {
        this.msg.add({ severity: 'error', summary: 'Error', detail: e.error?.detail ?? 'Error al guardar' });
        this.saving.set(false);
      },
    });
  }

  // ── Ciclos ─────────────────────────────────────────────────────────────────
  abrirNuevoCiclo(): void {
    this.cicloEdit = { nombre_ciclo: '', nivel_educativo_id: null, fecha_inicio: null, fecha_fin: null, tipo_ciclo: 'ANUAL', es_vigente: false };
    this.dlgCiclo = true;
  }

  abrirEditarCiclo(c: CicloAdmin): void {
    this.cicloEdit = {
      id: c.id,
      nombre_ciclo: c.nombre_ciclo,
      nivel_educativo_id: c.nivel_educativo_id,
      fecha_inicio: new Date(c.fecha_inicio),
      fecha_fin:    new Date(c.fecha_fin),
      tipo_ciclo:   c.tipo_ciclo,
      es_vigente:   c.es_vigente,
    };
    this.dlgCiclo = true;
  }

  guardarCiclo(): void {
    if (!this.cicloEdit) return;
    this.saving.set(true);
    const payload = {
      nombre_ciclo: this.cicloEdit.nombre_ciclo,
      nivel_educativo_id: this.cicloEdit.nivel_educativo_id,
      fecha_inicio: this.formatDate(this.cicloEdit.fecha_inicio),
      fecha_fin:    this.formatDate(this.cicloEdit.fecha_fin),
      tipo_ciclo:   this.cicloEdit.tipo_ciclo,
      es_vigente:   this.cicloEdit.es_vigente,
    };

    const req = this.cicloEdit.id
      ? this.api.patch(`/admin/ciclos/${this.cicloEdit.id}`, payload)
      : this.api.post('/admin/ciclos', payload);

    req.subscribe({
      next: () => {
        this.msg.add({ severity: 'success', summary: 'Guardado', detail: 'Ciclo guardado' });
        this.dlgCiclo = false;
        this.saving.set(false);
        this.cargarCiclos();
      },
      error: e => {
        this.msg.add({ severity: 'error', summary: 'Error', detail: e.error?.detail ?? 'Error' });
        this.saving.set(false);
      },
    });
  }

  // ── Planteles ──────────────────────────────────────────────────────────────
  abrirEditarPlantel(p: PlantelAdmin): void {
    this.plantelEdit = { id: p.id, nombre_plantel: p.nombre_plantel, clave_ct: p.clave_ct };
    this.dlgPlantel = true;
  }

  guardarPlantel(): void {
    if (!this.plantelEdit) return;
    this.saving.set(true);
    this.api.patch(`/admin/planteles/${this.plantelEdit.id}`, {
      nombre_plantel: this.plantelEdit.nombre_plantel,
      clave_ct: this.plantelEdit.clave_ct,
    }).subscribe({
      next: () => {
        this.msg.add({ severity: 'success', summary: 'Guardado', detail: 'Plantel actualizado' });
        this.dlgPlantel = false;
        this.saving.set(false);
        this.cargarPlanteles();
      },
      error: e => {
        this.msg.add({ severity: 'error', summary: 'Error', detail: e.error?.detail ?? 'Error' });
        this.saving.set(false);
      },
    });
  }

  // ── Grupos ─────────────────────────────────────────────────────────────────
  abrirNuevoGrupo(): void {
    this.grupoEdit = { nombre_grupo: '', capacidad_maxima: 30, turno: 'MATUTINO' };
    this.dlgGrupo = true;
  }

  abrirEditarGrupo(g: GrupoAdmin): void {
    this.grupoEdit = { id: g.id, nombre_grupo: g.nombre_grupo, capacidad_maxima: g.capacidad_maxima, turno: g.turno, is_active: g.is_active };
    this.dlgGrupo = true;
  }

  guardarGrupo(): void {
    if (!this.grupoEdit) return;
    this.saving.set(true);
    const req = this.grupoEdit.id
      ? this.api.patch(`/admin/grupos/${this.grupoEdit.id}`, {
          nombre_grupo: this.grupoEdit.nombre_grupo,
          capacidad_maxima: this.grupoEdit.capacidad_maxima,
          turno: this.grupoEdit.turno,
          is_active: this.grupoEdit.is_active,
        })
      : this.api.post('/admin/grupos', this.grupoEdit);

    req.subscribe({
      next: () => {
        this.msg.add({ severity: 'success', summary: 'Guardado', detail: 'Grupo guardado' });
        this.dlgGrupo = false;
        this.saving.set(false);
        this.cargarGrupos();
      },
      error: e => {
        this.msg.add({ severity: 'error', summary: 'Error', detail: e.error?.detail ?? 'Error' });
        this.saving.set(false);
      },
    });
  }

  private formatDate(d: Date | null): string | null {
    if (!d) return null;
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
  }
}
