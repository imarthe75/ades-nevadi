import { Component, OnDestroy, inject, OnInit, signal, computed, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';
import { InputNumberModule } from 'primeng/inputnumber';
import { CheckboxModule } from 'primeng/checkbox';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { DividerModule } from 'primeng/divider';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService, ExportColumn } from '../../core/services/export.service';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';

type TagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' | undefined;

interface Encuesta {
  id: string;
  titulo: string;
  descripcion: string | null;
  tipo: string;
  audiencia: string;
  nombre_plantel: string | null;
  fecha_inicio: string | null;
  fecha_fin: string | null;
  anonima: boolean;
  activa: boolean;
  total_preguntas: number;
  total_respuestas: number;
}

interface Pregunta {
  id: string;
  texto: string;
  tipo_pregunta: string;
  opciones: string[] | null;
  orden: number;
  obligatoria: boolean;
}

interface ResultadoPregunta {
  pregunta_id: string;
  texto: string;
  tipo_pregunta: string;
  orden: number;
  // ESCALA_5
  promedio?: number;
  moda?: number;
  n1?: number; n2?: number; n3?: number; n4?: number; n5?: number;
  total?: number;
  // OPCION_MULTIPLE
  distribucion?: { opcion: string; cantidad: number; porcentaje: number }[];
  // BOOLEANO
  si?: number; no?: number; pct_si?: number; pct_no?: number;
  // TEXTO_LIBRE
  respuestas?: string[];
}

interface Resultados {
  encuesta_id: string;
  total_sesiones: number;
  preguntas: ResultadoPregunta[];
}

const TIPO_SEV: Record<string, TagSeverity> = {
  SATISFACCION:       'success',
  DIAGNOSTICO:        'info',
  CLIMA_ESCOLAR:      'warn',
  EVALUACION_DOCENTE: 'secondary',
  SALIDA:             'danger',
  PERSONALIZADA:      'contrast',
};

const TIPOS_ENC = [
  { label: 'Satisfacción',        value: 'SATISFACCION' },
  { label: 'Diagnóstico',         value: 'DIAGNOSTICO' },
  { label: 'Clima escolar',       value: 'CLIMA_ESCOLAR' },
  { label: 'Evaluación docente',  value: 'EVALUACION_DOCENTE' },
  { label: 'Encuesta de salida',  value: 'SALIDA' },
  { label: 'Personalizada',       value: 'PERSONALIZADA' },
];

const AUDIENCIAS = [
  { label: 'Alumnos',          value: 'ALUMNO' },
  { label: 'Padres de familia', value: 'PADRE' },
  { label: 'Docentes',         value: 'DOCENTE' },
  { label: 'Todos',            value: 'TODOS' },
];

const TIPOS_PREG = [
  { label: 'Escala 1-5 (estrellas)', value: 'ESCALA_5' },
  { label: 'Opción múltiple',        value: 'OPCION_MULTIPLE' },
  { label: 'Texto libre',            value: 'TEXTO_LIBRE' },
  { label: 'Sí / No',               value: 'BOOLEANO' },
];

/**
 * Plataforma de encuestas institucionales dirigidas a alumnos, padres y docentes.
 * Soporta preguntas de tipo escala 1-5, opción múltiple, texto libre y sí/no.
 * Muestra resultados agregados por pregunta (promedio, distribución, respuestas
 * libres). Las encuestas anónimas no persisten el `persona_id` del respondente.
 * Administradores (nivelAcceso ≥ 4) crean y activan encuestas; nivelAcceso ≥ 3
 * puede responder encuestas activas dirigidas a su audiencia.
 */
@Component({
  selector: 'app-encuestas',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule,
    ButtonModule, TagModule, DialogModule, SelectModule,
    InputTextModule, TextareaModule, TooltipModule, InputNumberModule,
    CheckboxModule, ToggleSwitchModule, DividerModule,
    InteractiveGridComponent,
  ],
  template: `
    <div class="page-header">
      <div>
        <h2 class="page-title">Encuestas y Sondeos</h2>
        <p class="page-subtitle">Satisfacción, diagnóstico, clima escolar y más</p>
      </div>
      <p-button icon="pi pi-plus" label="Nueva encuesta" (onClick)="abrirNueva()" />
    </div>

    <div class="layout-split">

      <!-- ── Panel izquierdo: lista ── -->
      <div class="panel-list">
        <app-interactive-grid
          [data]="encuestasFlat()"
          [columns]="encuestaColumns"
          [loading]="loading()"
          [showDelete]="false"
          (rowSelected)="onSeleccion($event)"
        />
      </div>

      <!-- ── Panel derecho: detalle ── -->
      <div class="panel-detail">
        @if (!selEncuesta) {
          <div class="empty-panel">
            <i class="pi pi-clipboard" style="font-size:3rem; color:var(--surface-300)"></i>
            <p>Selecciona una encuesta para editarla o ver sus resultados</p>
          </div>
        } @else {
          <!-- Header detalle -->
          <div class="detail-header">
            <div class="detail-title-row">
              <h3>{{ selEncuesta.titulo }}</h3>
              <div style="display:flex; gap:.35rem; align-items:center">
                <span style="font-size:.8rem; color:var(--text-color-secondary)">Activa</span>
                <p-toggleswitch [(ngModel)]="selEncuesta.activa" (onChange)="toggleActiva()" />
                <p-button icon="pi pi-trash" severity="danger" [text]="true" size="small"
                          ariaLabel="Eliminar encuesta" pTooltip="Eliminar encuesta" (onClick)="eliminarEncuesta()" />
              </div>
            </div>
            <div class="detail-tags">
              <p-tag [value]="selEncuesta.tipo" [severity]="tipoSev(selEncuesta.tipo)" />
              <p-tag [value]="selEncuesta.audiencia" severity="secondary" />
              @if (selEncuesta.anonima) { <span class="tag-anon">Anónima</span> }
              <span class="resp-total-chip">
                <i class="pi pi-users"></i> {{ selEncuesta.total_respuestas }} respuestas
              </span>
            </div>
          </div>

          <!-- Tabs internos -->
          <div class="tabs">
            @for (t of tabs; track t.key) {
              <button class="tab-btn" [class.active]="activeTab === t.key"
                      (click)="activeTab = t.key; onTabChange()">
                <i [class]="'pi ' + t.icon"></i> {{ t.label }}
              </button>
            }
          </div>

          <!-- TAB: Diseñador de preguntas -->
          @if (activeTab === 'diseno') {
            <div class="preguntas-list">
              @for (p of preguntas(); track p.id; let i = $index) {
                <div class="pregunta-row">
                  <span class="preg-num">{{ i + 1 }}</span>
                  <div class="preg-body">
                    <div class="preg-texto">{{ p.texto }}</div>
                    <div class="preg-meta">
                      <p-tag [value]="tipoPregLabel(p.tipo_pregunta)" severity="secondary" />
                      @if (!p.obligatoria) { <span class="tag-opcional">Opcional</span> }
                      @if (p.opciones) {
                        <span class="tag-opciones">{{ p.opciones.join(' · ') }}</span>
                      }
                    </div>
                  </div>
                  <p-button icon="pi pi-trash" size="small" severity="danger" [text]="true"
                            ariaLabel="Eliminar pregunta" (onClick)="eliminarPregunta(p)" />
                </div>
              }
            </div>
            <p-button icon="pi pi-plus" label="Agregar pregunta"
                      severity="secondary" [outlined]="true"
                      styleClass="w-full mt-1"
                      (onClick)="abrirPregunta()" />
          }

          <!-- TAB: Resultados -->
          @if (activeTab === 'resultados') {
            @if (loadingResultados()) {
              <div class="empty-msg" style="padding:2rem">Cargando resultados…</div>
            } @else if (!resultados()) {
              <div class="empty-msg" style="padding:2rem">
                Aún no hay respuestas para esta encuesta.
              </div>
            } @else {
              <div class="resultados-header">
                <i class="pi pi-users"></i>
                <strong>{{ resultados()!.total_sesiones }}</strong> respuestas totales
              </div>
              @for (r of resultados()!.preguntas; track r.pregunta_id; let i = $index) {
                <div class="resultado-card">
                  <div class="res-pregunta">{{ i + 1 }}. {{ r.texto }}</div>

                  <!-- ESCALA_5 -->
                  @if (r.tipo_pregunta === 'ESCALA_5' && r.promedio != null) {
                    <div class="escala-resultado">
                      <div class="escala-promedio">
                        <span class="prom-numero">{{ r.promedio | number:'1.1-1' }}</span>
                        <span class="prom-label">/ 5</span>
                        <div class="estrellas">
                          @for (s of [1,2,3,4,5]; track s) {
                            <i [class]="'pi pi-star' + (s <= (r.moda ?? 0) ? '-fill' : '')"
                               [style.color]="s <= (r.moda ?? 0) ? 'var(--yellow-400)' : 'var(--surface-300)'"></i>
                          }
                        </div>
                      </div>
                      <div class="escala-bars">
                        @for (nivel of [5,4,3,2,1]; track nivel) {
                          <div class="esc-bar-row">
                            <span class="esc-bar-label">{{ nivel }}★</span>
                            <div class="esc-bar-track">
                              <div class="esc-bar-fill"
                                   [style.width]="barWidth(nivel, r) + '%'"
                                   [style.background]="escalaColor(nivel)"></div>
                            </div>
                            <span class="esc-bar-count">{{ getN(nivel, r) }}</span>
                          </div>
                        }
                      </div>
                    </div>
                  }

                  <!-- OPCION_MULTIPLE -->
                  @if (r.tipo_pregunta === 'OPCION_MULTIPLE' && r.distribucion) {
                    <div class="opcion-bars">
                      @for (d of r.distribucion; track d.opcion) {
                        <div class="op-bar-row">
                          <span class="op-label">{{ d.opcion }}</span>
                          <div class="op-track">
                            <div class="op-fill" [style.width]="d.porcentaje + '%'"></div>
                          </div>
                          <span class="op-count">{{ d.cantidad }} <span class="op-pct">({{ d.porcentaje | number:'1.0-1' }}%)</span></span>
                        </div>
                      }
                    </div>
                  }

                  <!-- BOOLEANO -->
                  @if (r.tipo_pregunta === 'BOOLEANO' && r.total != null) {
                    <div class="bool-resultado">
                      <div class="bool-box bool-si">
                        <span class="bool-num">{{ r.si ?? 0 }}</span>
                        <span class="bool-label">SÍ</span>
                        <span class="bool-pct">{{ r.pct_si | number:'1.0-1' }}%</span>
                      </div>
                      <div class="bool-bar-track">
                        <div class="bool-bar-si" [style.width]="(r.pct_si ?? 0) + '%'"></div>
                        <div class="bool-bar-no" [style.width]="(r.pct_no ?? 0) + '%'"></div>
                      </div>
                      <div class="bool-box bool-no">
                        <span class="bool-num">{{ r.no ?? 0 }}</span>
                        <span class="bool-label">NO</span>
                        <span class="bool-pct">{{ r.pct_no | number:'1.0-1' }}%</span>
                      </div>
                    </div>
                  }

                  <!-- TEXTO_LIBRE -->
                  @if (r.tipo_pregunta === 'TEXTO_LIBRE') {
                    <div class="texto-libre-list">
                      @if (!r.respuestas?.length) {
                        <span class="text-muted">Sin respuestas aún.</span>
                      }
                      @for (txt of r.respuestas ?? []; track $index) {
                        <div class="texto-quote">
                          <i class="pi pi-comment"></i> {{ txt }}
                        </div>
                      }
                    </div>
                  }
                </div>
              }
            }
          }

          <!-- TAB: Responder -->
          @if (activeTab === 'responder') {
            @if (respuestaEnviada()) {
              <div class="enviada-msg">
                <i class="pi pi-check-circle"></i>
                <p>¡Gracias! Tu respuesta ha sido registrada.</p>
                <p-button label="Enviar otra respuesta" severity="secondary" [text]="true"
                          (onClick)="respuestaEnviada.set(false); initRespuestas()" />
              </div>
            } @else {
              <div class="responder-form">
                @for (p of preguntas(); track p.id; let i = $index) {
                  <div class="resp-pregunta">
                    <div class="resp-texto">
                      {{ i + 1 }}. {{ p.texto }}
                      @if (p.obligatoria) { <span class="oblig">*</span> }
                    </div>

                    @if (p.tipo_pregunta === 'ESCALA_5') {
                      <div class="estrellas-input">
                        @for (s of [1,2,3,4,5]; track s) {
                          <button class="estrella-btn"
                                  [class.activa]="s <= (respForm[p.id]?.valor ?? 0)"
                                  (click)="setEscala(p.id, s)">
                            <i class="pi pi-star-fill"></i>
                          </button>
                        }
                        @if (respForm[p.id]?.valor) {
                          <span class="escala-label">{{ respForm[p.id].valor }}/5</span>
                        }
                      </div>
                    }

                    @if (p.tipo_pregunta === 'OPCION_MULTIPLE' && p.opciones) {
                      <div class="opciones-grid">
                        @for (op of p.opciones; track op) {
                          <button class="opcion-btn"
                                  [class.seleccionada]="respForm[p.id]?.opcion === op"
                                  (click)="setOpcion(p.id, op)">
                            {{ op }}
                          </button>
                        }
                      </div>
                    }

                    @if (p.tipo_pregunta === 'BOOLEANO') {
                      <div class="bool-input">
                        <button class="bool-btn si" [class.sel]="respForm[p.id]?.opcion === 'Sí'"
                                (click)="setOpcion(p.id, 'Sí')">
                          <i class="pi pi-check"></i> Sí
                        </button>
                        <button class="bool-btn no" [class.sel]="respForm[p.id]?.opcion === 'No'"
                                (click)="setOpcion(p.id, 'No')">
                          <i class="pi pi-times"></i> No
                        </button>
                      </div>
                    }

                    @if (p.tipo_pregunta === 'TEXTO_LIBRE') {
                      <textarea class="texto-input"
                                [(ngModel)]="respForm[p.id].texto"
                                rows="3"
                                placeholder="Escribe tu respuesta..."></textarea>
                    }
                  </div>
                }
                <p-button label="Enviar respuestas" icon="pi pi-send"
                          styleClass="w-full"
                          (onClick)="enviarRespuestas()" [loading]="saving()" />
              </div>
            }
          }
        }
      </div>
    </div>

    <!-- Dialog nueva encuesta -->
    <p-dialog header="Nueva encuesta" [(visible)]="showNueva" [modal]="true"
              [style]="{width:'520px'}">
      <div class="form-grid">
        <div class="form-field full">
          <label>Título *</label>
          <input pInputText [(ngModel)]="formNueva.titulo" style="width:100%"
                 placeholder="Ej. Encuesta de satisfacción 2026-II" />
        </div>
        <div class="form-field full">
          <label>Descripción</label>
          <textarea pTextarea [(ngModel)]="formNueva.descripcion"
                    rows="2" style="width:100%"></textarea>
        </div>
        <div class="form-field">
          <label>Tipo</label>
          <p-select [options]="tipos" [(ngModel)]="formNueva.tipo"
                    optionLabel="label" optionValue="value" styleClass="w-full" 
 [filter]="true" filterPlaceholder="Buscar..."/>
        </div>
        <div class="form-field">
          <label>Audiencia</label>
          <p-select [options]="audiencias" [(ngModel)]="formNueva.audiencia"
                    optionLabel="label" optionValue="value" styleClass="w-full" 
 [filter]="true" filterPlaceholder="Buscar..."/>
        </div>
        <div class="form-field">
          <label>Fecha inicio</label>
          <input pInputText type="date" [(ngModel)]="formNueva.fecha_inicio" style="width:100%" />
        </div>
        <div class="form-field">
          <label>Fecha fin</label>
          <input pInputText type="date" [(ngModel)]="formNueva.fecha_fin" style="width:100%" />
        </div>
        <div class="form-field full" style="display:flex; align-items:center; gap:.6rem;">
          <p-checkbox [(ngModel)]="formNueva.anonima" [binary]="true" inputId="anon" />
          <label for="anon">Encuesta anónima (no se registra quién responde)</label>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="showNueva = false" />
        <p-button label="Crear" icon="pi pi-check" (onClick)="crearEncuesta()" [loading]="saving()" />
      </ng-template>
    </p-dialog>

    <!-- Dialog nueva pregunta -->
    <p-dialog header="Agregar pregunta" [(visible)]="showPregunta" [modal]="true"
              [style]="{width:'500px'}">
      <div class="form-grid">
        <div class="form-field full">
          <label>Texto de la pregunta *</label>
          <textarea pTextarea [(ngModel)]="formPreg.texto"
                    rows="2" style="width:100%"
                    placeholder="¿Cómo calificarías...?"></textarea>
        </div>
        <div class="form-field">
          <label>Tipo de respuesta</label>
          <p-select [options]="tiposPreg" [(ngModel)]="formPreg.tipo_pregunta"
                    optionLabel="label" optionValue="value" styleClass="w-full"
                    (onChange)="onTipoPregChange()" 
 [filter]="true" filterPlaceholder="Buscar..."/>
        </div>
        <div class="form-field">
          <label>Orden</label>
          <p-inputNumber [(ngModel)]="formPreg.orden" [min]="1" inputStyleClass="w-full" />
        </div>
        @if (formPreg.tipo_pregunta === 'OPCION_MULTIPLE') {
          <div class="form-field full">
            <label>Opciones (una por línea)</label>
            <textarea pTextarea [(ngModel)]="formPreg.opcionesTexto"
                      rows="4" style="width:100%"
                      placeholder="Muy satisfecho&#10;Satisfecho&#10;Regular&#10;Insatisfecho"></textarea>
          </div>
        }
        <div class="form-field full" style="display:flex; align-items:center; gap:.6rem;">
          <p-checkbox [(ngModel)]="formPreg.obligatoria" [binary]="true" inputId="oblig2" />
          <label for="oblig2">Obligatoria</label>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="showPregunta = false" />
        <p-button label="Agregar" icon="pi pi-plus" (onClick)="guardarPregunta()" [loading]="saving()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .page-header   { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:1.25rem; }
    .page-title    { font-size:1.25rem; font-weight:700; margin:0 0 .2rem; }
    .page-subtitle { color:var(--text-color-secondary); font-size:.82rem; margin:0; }

    /* Layout */
    .layout-split { display:grid; grid-template-columns:300px 1fr; gap:1.25rem; }
    @media (max-width:800px) { .layout-split { grid-template-columns:1fr; } }

    /* Lista */
    :host ::ng-deep .row-selected td { background: var(--primary-50, #fff0f1) !important; }
    .enc-title  { font-weight:600; font-size:.88rem; margin-bottom:.3rem; }
    .enc-meta   { display:flex; gap:.35rem; align-items:center; flex-wrap:wrap; }
    .tag-anon   { background:var(--purple-100); color:var(--purple-700); border-radius:10px;
      font-size:.7rem; font-weight:600; padding:0 .45rem; }
    .resp-count { font-size:1.2rem; font-weight:700; color:var(--primary-color); text-align:center; }

    /* Detalle */
    .panel-detail { display:flex; flex-direction:column; }
    .empty-panel  { height:300px; display:flex; flex-direction:column; align-items:center;
      justify-content:center; gap:1rem; color:var(--text-color-secondary); }
    .detail-header { padding:.75rem 0; margin-bottom:.75rem; border-bottom:1px solid var(--surface-200); }
    .detail-title-row { display:flex; justify-content:space-between; align-items:center; margin-bottom:.4rem; }
    .detail-title-row h3 { font-size:1.05rem; font-weight:700; margin:0; }
    .detail-tags { display:flex; gap:.4rem; flex-wrap:wrap; align-items:center; }
    .resp-total-chip { background:var(--surface-200); border-radius:12px; font-size:.75rem;
      padding:0 .6rem; height:22px; display:inline-flex; align-items:center; gap:.3rem; }

    /* Tabs */
    .tabs { display:flex; gap:.25rem; margin-bottom:1rem; border-bottom:2px solid var(--surface-200); }
    .tab-btn { background:none; border:none; padding:.4rem .85rem; cursor:pointer; border-radius:4px 4px 0 0;
      font-size:.83rem; color:var(--text-color-secondary); display:flex; align-items:center; gap:.3rem;
      transition:background .12s; }
    .tab-btn:hover  { background:var(--surface-100); }
    .tab-btn.active { color:var(--primary-color); font-weight:600; border-bottom:2px solid var(--primary-color); }

    /* Preguntas diseñador */
    .preguntas-list { display:flex; flex-direction:column; gap:.5rem; margin-bottom:.75rem; }
    .pregunta-row { display:flex; align-items:flex-start; gap:.6rem; background:var(--surface-50);
      border:1px solid var(--surface-200); border-radius:6px; padding:.6rem .75rem; }
    .preg-num  { background:var(--primary-color); color:#fff; border-radius:50%;
      width:22px; height:22px; display:flex; align-items:center; justify-content:center;
      font-size:.72rem; font-weight:700; flex-shrink:0; margin-top:.1rem; }
    .preg-body { flex:1; }
    .preg-texto { font-size:.88rem; font-weight:600; margin-bottom:.3rem; }
    .preg-meta  { display:flex; gap:.35rem; flex-wrap:wrap; align-items:center; }
    .tag-opcional { background:var(--surface-200); border-radius:8px; font-size:.68rem; padding:0 .4rem; }
    .tag-opciones { font-size:.68rem; color:var(--text-color-secondary); }

    /* Resultados */
    .resultados-header { display:flex; align-items:center; gap:.4rem; font-size:.88rem;
      margin-bottom:1rem; color:var(--text-color-secondary); }
    .resultado-card { background:var(--surface-card); border:1px solid var(--surface-200);
      border-radius:8px; padding:.85rem 1rem; margin-bottom:.75rem; }
    .res-pregunta { font-size:.9rem; font-weight:700; margin-bottom:.75rem; color:var(--text-color); }

    /* ESCALA_5 resultados */
    .escala-resultado { display:flex; gap:2rem; align-items:center; }
    .escala-promedio  { text-align:center; }
    .prom-numero { font-size:2.5rem; font-weight:800; color:var(--primary-color); }
    .prom-label  { font-size:1rem; color:var(--text-color-secondary); }
    .estrellas   { display:flex; gap:.15rem; justify-content:center; margin-top:.2rem; }
    .estrellas i { font-size:.9rem; }
    .escala-bars { flex:1; display:flex; flex-direction:column; gap:.35rem; }
    .esc-bar-row { display:grid; grid-template-columns:28px 1fr 24px; align-items:center; gap:.4rem; }
    .esc-bar-label { font-size:.72rem; color:var(--text-color-secondary); }
    .esc-bar-track { background:var(--surface-200); border-radius:4px; height:14px; overflow:hidden; }
    .esc-bar-fill  { height:100%; border-radius:4px; transition:width .5s ease; min-width:2px; }
    .esc-bar-count { font-size:.72rem; color:var(--text-color-secondary); text-align:right; }

    /* OPCION_MULTIPLE resultados */
    .opcion-bars { display:flex; flex-direction:column; gap:.45rem; }
    .op-bar-row  { display:grid; grid-template-columns:140px 1fr 90px; align-items:center; gap:.5rem; }
    .op-label    { font-size:.82rem; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
    .op-track    { background:var(--surface-200); border-radius:4px; height:18px; overflow:hidden; }
    .op-fill     { height:100%; border-radius:4px; background:var(--primary-color); transition:width .5s; }
    .op-count    { font-size:.8rem; font-weight:600; }
    .op-pct      { color:var(--text-color-secondary); font-weight:400; }

    /* BOOLEANO resultados */
    .bool-resultado { display:flex; align-items:center; gap:1rem; }
    .bool-box    { text-align:center; min-width:60px; }
    .bool-num    { font-size:1.8rem; font-weight:800; display:block; }
    .bool-label  { font-size:.7rem; font-weight:700; text-transform:uppercase; display:block; letter-spacing:.05em; }
    .bool-pct    { font-size:.75rem; display:block; }
    .bool-si     { color:var(--green-600); }
    .bool-no     { color:var(--red-600); }
    .bool-bar-track { flex:1; height:20px; border-radius:10px; overflow:hidden; display:flex; }
    .bool-bar-si { background:var(--green-400); transition:width .5s; }
    .bool-bar-no { background:var(--red-400); transition:width .5s; }

    /* TEXTO_LIBRE resultados */
    .texto-libre-list { display:flex; flex-direction:column; gap:.4rem; max-height:250px; overflow-y:auto; }
    .texto-quote { background:var(--surface-100); border-left:3px solid var(--primary-color);
      border-radius:0 6px 6px 0; padding:.4rem .65rem; font-size:.83rem; display:flex; gap:.4rem; }
    .texto-quote i { color:var(--primary-color); flex-shrink:0; margin-top:.1rem; }

    /* Formulario responder */
    .responder-form { display:flex; flex-direction:column; gap:1rem; }
    .resp-pregunta  { padding:.75rem 0; border-bottom:1px solid var(--surface-200); }
    .resp-texto     { font-size:.9rem; font-weight:600; margin-bottom:.65rem; }
    .oblig          { color:var(--red-500); margin-left:.2rem; }

    .estrellas-input { display:flex; align-items:center; gap:.4rem; }
    .estrella-btn   { background:none; border:none; cursor:pointer; padding:.1rem; }
    .estrella-btn i { font-size:1.6rem; color:var(--surface-300); transition:color .1s; }
    .estrella-btn.activa i { color:var(--yellow-400); }
    .escala-label   { font-size:.82rem; color:var(--text-color-secondary); margin-left:.4rem; }

    .opciones-grid  { display:flex; flex-wrap:wrap; gap:.4rem; }
    .opcion-btn     { background:var(--surface-100); border:1px solid var(--surface-300);
      border-radius:20px; padding:.35rem .85rem; cursor:pointer; font-size:.85rem;
      transition:background .12s, border-color .12s; }
    .opcion-btn:hover       { background:var(--surface-200); }
    .opcion-btn.seleccionada { background:var(--primary-color); color:#fff; border-color:var(--primary-color); }

    .bool-input { display:flex; gap:.75rem; }
    .bool-btn   { display:flex; align-items:center; gap:.4rem; padding:.5rem 1.5rem;
      border-radius:6px; cursor:pointer; font-size:.88rem; font-weight:600;
      border:2px solid transparent; transition:all .12s; }
    .bool-btn.si       { background:var(--green-50);  border-color:var(--green-300); color:var(--green-700); }
    .bool-btn.no       { background:var(--red-50);    border-color:var(--red-300);   color:var(--red-700); }
    .bool-btn.si.sel   { background:var(--green-400); border-color:var(--green-500); color:#fff; }
    .bool-btn.no.sel   { background:var(--red-400);   border-color:var(--red-500);   color:#fff; }

    .texto-input { width:100%; padding:.5rem .75rem; border:1px solid var(--surface-300);
      border-radius:6px; font-size:.88rem; font-family:inherit; resize:vertical; }
    .texto-input:focus { outline:none; border-color:var(--primary-color); }

    .enviada-msg { display:flex; flex-direction:column; align-items:center; justify-content:center;
      gap:1rem; padding:3rem; text-align:center; }
    .enviada-msg i { font-size:3rem; color:var(--green-500); }
    .enviada-msg p { font-size:1rem; color:var(--text-color-secondary); margin:0; }

    /* Forms */
    .form-grid  { display:grid; grid-template-columns:1fr 1fr; gap:.75rem; }
    .form-field { display:flex; flex-direction:column; gap:.3rem; font-size:.875rem; }
    .form-field.full { grid-column:1/-1; }
    label       { font-weight:500; font-size:.82rem; }

    .w-full     { width:100% !important; }
    .mt-1       { margin-top:.4rem; }
    .text-muted { color:var(--text-color-secondary); font-size:.82rem; }
    .empty-msg  { text-align:center; padding:2rem; color:var(--text-color-secondary); }
  `],
})
export class EncuestasComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private readonly api      = inject(ApiService);
  readonly ctx              = inject(ContextService);
  private readonly exporter = inject(ExportService);

  encuestas   = signal<Encuesta[]>([]);

  readonly encuestaColumns: ColumnConfig[] = [
    { field: 'titulo',         header: 'Encuesta',    sortable: true, filterable: true },
    { field: 'tipoLabel',      header: 'Tipo',        sortable: true, filterable: true, width: '110px' },
    { field: 'activaLabel',    header: 'Estado',      sortable: true, filterable: true, width: '80px' },
    { field: 'total_respuestas', header: 'Respuestas', sortable: true, filterable: false, width: '90px' },
  ];

  readonly encuestasFlat = computed(() =>
    this.encuestas().map(e => ({
      ...e,
      tipoLabel: e.tipo,
      activaLabel: e.activa ? 'Activa' : 'Cerrada',
    }))
  );
  preguntas   = signal<Pregunta[]>([]);
  resultados  = signal<Resultados | null>(null);

  loading           = signal(false);
  loadingResultados = signal(false);
  saving            = signal(false);
  respuestaEnviada  = signal(false);

  selEncuesta: Encuesta | null = null;
  activeTab = 'diseno';
  showNueva   = false;
  showPregunta = false;

  respForm: Record<string, { valor?: number; opcion?: string; texto?: string }> = {};

  formNueva = this.emptyFormNueva();
  formPreg  = this.emptyFormPreg();

  tabs = [
    { key: 'diseno',     label: 'Preguntas',  icon: 'pi-list' },
    { key: 'resultados', label: 'Resultados', icon: 'pi-chart-pie' },
    { key: 'responder',  label: 'Responder',  icon: 'pi-pencil' },
  ];

  tipos     = TIPOS_ENC;
  audiencias = AUDIENCIAS;
  tiposPreg  = TIPOS_PREG;

  ngOnInit(): void { this.cargar(); }

  cargar(): void {
    this.loading.set(true);
    const plantel = this.ctx.plantel();
    const params = plantel ? { plantel_id: plantel.id } : {};
    this.api.get<Encuesta[]>('/encuestas', params).subscribe({
      next: (r) => { this.encuestas.set(r); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  onSeleccion(e: any): void {
    this.selEncuesta = e;
    this.activeTab = 'diseno';
    this.resultados.set(null);
    this.cargarPreguntas();
  }

  cargarPreguntas(): void {
    if (!this.selEncuesta) return;
    this.api.get<any>(`/encuestas/${this.selEncuesta.id}`).subscribe(d => {
      this.preguntas.set(d.preguntas ?? []);
      this.initRespuestas();
    });
  }

  onTabChange(): void {
    if (this.activeTab === 'resultados' && !this.resultados() && this.selEncuesta) {
      this.cargarResultados();
    }
  }

  cargarResultados(): void {
    if (!this.selEncuesta) return;
    this.loadingResultados.set(true);
    this.api.get<Resultados>(`/encuestas/${this.selEncuesta.id}/resultados`).subscribe({
      next: (r) => { this.resultados.set(r); this.loadingResultados.set(false); },
      error: () => this.loadingResultados.set(false),
    });
  }

  initRespuestas(): void {
    this.respForm = {};
    this.preguntas().forEach(p => { this.respForm[p.id] = {}; });
  }

  // ── Nueva encuesta ──
  abrirNueva(): void { this.formNueva = this.emptyFormNueva(); this.showNueva = true; }

  crearEncuesta(): void {
    if (!this.formNueva.titulo) return;
    this.saving.set(true);
    const plantel = this.ctx.plantel();
    this.api.post<Encuesta>('/encuestas', {
      ...this.formNueva,
      plantel_id: plantel?.id ?? null,
      fecha_inicio: this.formNueva.fecha_inicio || null,
      fecha_fin:    this.formNueva.fecha_fin    || null,
    }).subscribe({
      next: (r) => {
        this.showNueva = false;
        this.saving.set(false);
        this.cargar();
      },
      error: () => this.saving.set(false),
    });
  }

  toggleActiva(): void {
    if (!this.selEncuesta) return;
    this.api.patch(`/encuestas/${this.selEncuesta.id}/toggle-activa`, {}).subscribe(r => {
      this.cargar();
    });
  }

  eliminarEncuesta(): void {
    if (!this.selEncuesta) return;
    this.api.delete(`/encuestas/${this.selEncuesta.id}`).subscribe(() => {
      this.selEncuesta = null;
      this.preguntas.set([]);
      this.cargar();
    });
  }

  // ── Preguntas ──
  abrirPregunta(): void {
    this.formPreg = this.emptyFormPreg();
    this.formPreg.orden = (this.preguntas().length || 0) + 1;
    this.showPregunta = true;
  }

  onTipoPregChange(): void {
    if (this.formPreg.tipo_pregunta !== 'OPCION_MULTIPLE') {
      this.formPreg.opcionesTexto = '';
    }
  }

  guardarPregunta(): void {
    if (!this.selEncuesta || !this.formPreg.texto) return;
    this.saving.set(true);
    const opciones = this.formPreg.tipo_pregunta === 'OPCION_MULTIPLE'
      ? this.formPreg.opcionesTexto.split('\n').map(s => s.trim()).filter(Boolean)
      : null;

    this.api.post(`/encuestas/${this.selEncuesta.id}/preguntas`, {
      texto:         this.formPreg.texto,
      tipo_pregunta: this.formPreg.tipo_pregunta,
      opciones,
      orden:        this.formPreg.orden,
      obligatoria:  this.formPreg.obligatoria,
    }).subscribe({
      next: () => { this.showPregunta = false; this.saving.set(false); this.cargarPreguntas(); },
      error: () => this.saving.set(false),
    });
  }

  eliminarPregunta(p: Pregunta): void {
    if (!this.selEncuesta) return;
    this.api.delete(`/encuestas/${this.selEncuesta.id}/preguntas/${p.id}`).subscribe(() => {
      this.cargarPreguntas();
    });
  }

  // ── Responder ──
  setEscala(pregId: string, val: number): void {
    if (!this.respForm[pregId]) this.respForm[pregId] = {};
    this.respForm[pregId].valor = val;
  }
  setOpcion(pregId: string, op: string): void {
    if (!this.respForm[pregId]) this.respForm[pregId] = {};
    this.respForm[pregId].opcion = op;
  }

  enviarRespuestas(): void {
    if (!this.selEncuesta) return;
    this.saving.set(true);
    const respuestas = this.preguntas().map(p => {
      const r = this.respForm[p.id] ?? {};
      return {
        pregunta_id:         p.id,
        valor_numerico:      r.valor ?? null,
        opcion_seleccionada: r.opcion ?? null,
        texto_respuesta:     r.texto  ?? null,
      };
    }).filter(r => r.valor_numerico != null || r.opcion_seleccionada || r.texto_respuesta);

    this.api.post(`/encuestas/${this.selEncuesta.id}/responder`, { respuestas }).subscribe({
      next: () => { this.saving.set(false); this.respuestaEnviada.set(true); this.resultados.set(null); },
      error: () => this.saving.set(false),
    });
  }

  // ── Helpers visualización resultados ──
  tipoSev(tipo: string): TagSeverity { return TIPO_SEV[tipo] ?? 'secondary'; }
  tipoPregLabel(t: string): string { return TIPOS_PREG.find(p => p.value === t)?.label ?? t; }

  getN(nivel: number, r: ResultadoPregunta): number {
    return (r as any)[`n${nivel}`] ?? 0;
  }

  barWidth(nivel: number, r: ResultadoPregunta): number {
    const total = r.total ?? 0;
    if (!total) return 0;
    return Math.round(this.getN(nivel, r) / total * 100);
  }

  escalaColor(nivel: number): string {
    return ['var(--red-400)', 'var(--orange-400)', 'var(--yellow-400)', 'var(--teal-400)', 'var(--green-500)'][nivel - 1];
  }

  private emptyFormNueva() {
    return { titulo: '', descripcion: '', tipo: 'SATISFACCION', audiencia: 'ALUMNO',
             fecha_inicio: '', fecha_fin: '', anonima: false };
  }
  private emptyFormPreg() {
    return { texto: '', tipo_pregunta: 'ESCALA_5', opcionesTexto: '', orden: 1, obligatoria: true };
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
