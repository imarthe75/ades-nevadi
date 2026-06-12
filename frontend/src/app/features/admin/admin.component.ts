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
import { SkeletonModule } from 'primeng/skeleton';

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
    TabsModule, TabList, TabPanels, Tab, TabPanel, SkeletonModule,
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
                      [filter]="true" filterPlaceholder="Buscar..."/>
          </div>
          <div class="field" style="display:flex; flex-direction:column; gap:.35rem">
            <label class="dlg-lbl">Plantel (Alcance)</label>
            <p-select [options]="planteles()" [(ngModel)]="usuarioEditForm.plantel_id"
                      optionLabel="nombre_plantel" optionValue="id" placeholder="Global"
                      [showClear]="true" [filter]="true" filterPlaceholder="Buscar..."/>
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
              [filter]="true" filterPlaceholder="Buscar..." />
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
              <p-select [options]="grados()" [(ngModel)]="grupoAdminEdit()!.grado_id"
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

  // ── Signals para dialogs de Ciclo / Plantel / Grupo ─────────────────────────
  dlgCicloVisible      = signal(false);
  cicloEdit            = signal<CicloAdmin | null>(null);
  guardandoCiclo       = signal(false);
  dlgPlantelVisible    = signal(false);
  plantelEdit          = signal<PlantelAdmin | null>(null);
  guardandoPlantel     = signal(false);
  dlgGrupoAdminVisible = signal(false);
  grupoAdminEdit       = signal<GrupoAdmin | null>(null);
  guardandoGrupo       = signal(false);
  grados               = signal<{ id: string; label: string }[]>([]);

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

  columnasCiclos: ColumnConfig[] = [
    { field: 'nombre_ciclo',     header: 'Ciclo',   sortable: true,  filterable: true,  width: '200px' },
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
    this.cargarGrados();
  }

  onTabChange(tab: any): void {
    if (!tab) return;
    this.tabActivo.set(tab);
    if (tab === 'variables' && this.variables().length === 0) this.cargarVariables();
    if (tab === 'catalogos' && this.catalogos().length === 0) this.cargarCatalogos();
    if (tab === 'marca') this.cargarMarca();
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
    this.api.get<RolOpt[]>('/catalogs/roles').subscribe(r => this.roles.set(r));
  }

  cargarNiveles(): void {
    this.api.get<NivelOpt[]>('/catalogs/niveles').subscribe(n => this.niveles.set(n));
  }

  cargarGrados(): void {
    this.api.get<any[]>('/catalogs/grados').subscribe({
      next: gs => this.grados.set(gs.map(g => ({ id: g.id, label: `${g.nombre_nivel ?? ''} ${g.nombre_grado ?? g.numero_grado ?? ''}`.trim() }))),
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

  // ── Crear usuario (abre el mismo dialog de edición en modo nuevo) ────────────

  abrirCrearUsuario(): void {
    this.notify.info('Próximamente', 'La creación de usuarios desde la UI estará disponible en la siguiente iteración. Use Authentik para crear el usuario primero.');
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
}
