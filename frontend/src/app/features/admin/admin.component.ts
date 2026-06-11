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
    InputTextModule, InputNumberModule, DatePickerModule, ToggleSwitchModule,
    TagModule, TooltipModule, ConfirmDialogModule,
    TabsModule, TabList, TabPanels, Tab, TabPanel,
    InteractiveGridComponent, ImportButtonComponent, SelectorGeoComponent
  ],
  providers: [ConfirmationService],
  template: `
    <p-confirmDialog />

    <div class="page-header">
      <h2>Administración</h2>
      <p class="subtitle">Gestión de usuarios, ciclos, planteles, variables del sistema y catálogos</p>
    </div>

    <p-tabs [value]="tabActivo()" (valueChange)="onTabChange($event)">
      <p-tablist>
        <p-tab value="usuarios"><i class="pi pi-users"></i> Usuarios</p-tab>
        <p-tab value="ciclos"><i class="pi pi-calendar"></i> Ciclos Escolares</p-tab>
        <p-tab value="planteles"><i class="pi pi-map-marker"></i> Planteles</p-tab>
        <p-tab value="grupos"><i class="pi pi-th-large"></i> Grupos</p-tab>
        <p-tab value="variables"><i class="pi pi-cog"></i> Variables del Sistema</p-tab>
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

        <!-- ══ GEOGRÁFICOS (SEPOMEX) ═══════════════════════════════════════ -->
        <p-tabpanel value="geo">
          <div style="max-width: 600px; margin-top: 1rem;">
            <h3>Probar Autocompletado SEPOMEX</h3>
            <p style="margin-bottom:1.5rem;color:var(--text-color-secondary)">
              Componente <code>&lt;app-selector-geo&gt;</code> integrado. Escribe un código postal de 5 dígitos (ej. 50100) y presiona Enter.
            </p>
            <app-selector-geo></app-selector-geo>
          </div>
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
              [filter]="true" filterPlaceholder="Buscar..."
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
            <p-table
              [value]="variablesPorGrupo()[grupo]"
              [loading]="loadingVariables()"
              styleClass="p-datatable-sm p-datatable-striped"
              [tableStyle]="{'margin-bottom':'1rem'}">
              <ng-template pTemplate="header">
                <tr>
                  <th style="width:220px">Variable</th>
                  <th style="width:110px">Tipo</th>
                  <th>Valor</th>
                  <th style="width:280px">Descripción</th>
                  <th style="width:60px"></th>
                </tr>
              </ng-template>
              <ng-template pTemplate="body" let-v>
                <tr [class]="v.solo_lectura ? 'var-readonly' : ''">
                  <td><code class="var-nombre">{{ v.nombre }}</code></td>
                  <td>
                    <p-tag [value]="v.tipo_valor" [severity]="tipoValorSeverity(v.tipo_valor)" />
                  </td>
                  <td>
                    @if (v.tipo_valor === 'PASSWORD') {
                      <span class="var-secret">••••••••</span>
                    } @else if (v.tipo_valor === 'BOOLEANO') {
                      <p-tag
                        [value]="v.valor === 'true' ? 'Activo' : 'Inactivo'"
                        [severity]="v.valor === 'true' ? 'success' : 'secondary'" />
                    } @else if (v.tipo_valor === 'JSON') {
                      <code class="var-json">{{ (v.valor ?? '').slice(0, 60) }}{{ (v.valor?.length ?? 0) > 60 ? '…' : '' }}</code>
                    } @else {
                      {{ v.valor }}
                    }
                  </td>
                  <td class="var-desc">{{ v.descripcion }}</td>
                  <td>
                    @if (!v.solo_lectura) {
                      <p-button icon="pi pi-pencil" [text]="true" [rounded]="true"
                        pTooltip="Editar" (onClick)="abrirEditarVariable(v)" />
                    } @else {
                      <i class="pi pi-lock var-lock" pTooltip="Solo lectura"></i>
                    }
                  </td>
                </tr>
              </ng-template>
            </p-table>
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
                <p-table
                  [value]="catSeleccionado()!.items"
                  [loading]="loadingCatalogos()"
                  styleClass="p-datatable-sm p-datatable-striped"
                  [paginator]="true" [rows]="20">
                  <ng-template pTemplate="header">
                    <tr>
                      <th style="width:60px">Orden</th>
                      <th>Valor</th>
                      <th>Descripción</th>
                      <th style="width:90px;text-align:center">Estado</th>
                      <th style="width:70px"></th>
                    </tr>
                  </ng-template>
                  <ng-template pTemplate="body" let-item>
                    <tr>
                      <td style="text-align:center">{{ item.orden }}</td>
                      <td><strong>{{ item.valor }}</strong></td>
                      <td>{{ item.descripcion }}</td>
                      <td style="text-align:center">
                        <p-tag [value]="item.is_active ? 'Activo' : 'Inactivo'"
                          [severity]="item.is_active ? 'success' : 'secondary'" />
                      </td>
                      <td>
                        <p-button icon="pi pi-pencil" [text]="true" [rounded]="true"
                          pTooltip="Editar" (onClick)="abrirEditarItem(item)" />
                      </td>
                    </tr>
                  </ng-template>
                </p-table>
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
          <div style="padding:2rem;text-align:center;color:var(--text-secondary)">
            <i class="pi pi-palette" style="font-size:2rem;display:block;margin-bottom:1rem"></i>
            Marca e identidad visual — Configurable desde Variables del Sistema (JSON_MARCA)
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
  `,
  styles: [`
    .page-header { margin-bottom:1rem; }
    .subtitle { font-size:.82rem; color:var(--text-secondary); margin:0; }
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
    :deep(.w-full) { width:100% !important; }
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

  loadingUsuarios  = signal(false);
  loadingCiclos    = signal(false);
  loadingPlanteles = signal(false);
  loadingGrupos    = signal(false);

  tabActivo       = signal('usuarios');
  cicloFiltroId   = '';

  columnasUsuarios: ColumnConfig[] = [
    { field: 'nombre_usuario',      header: 'Usuario',       sortable: true, filterable: true, width: '130px' },
    { field: 'nombre_completo',     header: 'Nombre',        sortable: true, filterable: true, width: '200px' },
    { field: 'email_institucional', header: 'Email',         sortable: true, filterable: true, width: '200px' },
    { field: 'rol',                 header: 'Rol',           sortable: true, filterable: true, width: '130px' },
    { field: 'alcance',             header: 'Alcance',       sortable: false, filterable: true, width: '150px' },
    { field: 'estado',              header: 'Estado',        sortable: true, filterable: true, width: '100px' },
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

  // ── Catálogos Dinámicos ─────────────────────────────────────────────────────
  catalogos        = signal<Catalogo[]>([]);
  catSeleccionado  = signal<Catalogo | null>(null);
  loadingCatalogos = signal(false);
  catalogoDlgVisible = signal(false);
  catalogoForm     = { codigo: '', nombre: '', descripcion: '' };
  itemDlgVisible   = signal(false);
  itemEdit: CatalogoItem | null = null;
  itemForm         = { valor: '', descripcion: '', orden: 0, is_active: true };

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
  }

  onTabChange(tab: any): void {
    if (!tab) return;
    this.tabActivo.set(tab);
    if (tab === 'variables' && this.variables().length === 0) this.cargarVariables();
    if (tab === 'catalogos' && this.catalogos().length === 0) this.cargarCatalogos();
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

  // ── Stubs pendientes ─────────────────────────────────────────────────────────
  abrirCrearUsuario(): void { /* TODO */ }
  abrirEditarUsuario(_row: any): void { /* TODO: implementar dialog de edición de usuario */ }
  abrirNuevoCiclo(): void { /* TODO */ }
  abrirEditarCiclo(c: CicloAdmin): void { /* TODO */ }
  abrirEditarPlantel(p: PlantelAdmin): void { /* TODO */ }
  abrirNuevoGrupo(): void { /* TODO */ }
  abrirEditarGrupo(g: GrupoAdmin): void { /* TODO */ }
}
