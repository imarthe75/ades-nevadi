/**
 * IaComponent — Asistente pedagógico IA + Panel de alertas académicas.
 *
 * Dos pestañas:
 *   1. Chat con el asistente (Claude vía backend)
 *   2. Alertas académicas del grupo/plantel con botón "Escanear grupo"
 */
import { Component, OnDestroy, OnInit, inject, signal, computed, ElementRef, ViewChild, afterNextRender } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TabsModule } from 'primeng/tabs';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { SelectModule } from 'primeng/select';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import type { Grupo } from '../../core/models';
import { grupoLabel } from '../../core/models';
import { ApexNotificationService } from 'apex-component-library';

type GrupoConLabel = Grupo & { _label: string };

interface Mensaje {
  rol: 'user' | 'assistant';
  contenido: string;
  timestamp: Date;
}

interface Alerta {
  id: string;
  tipo_alerta: string;
  nivel_riesgo: string;
  descripcion: string;
  atendida: boolean;
  fccreacion: string;
}

type AlertaSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary';

const RIESGO_SEVERITY: Record<string, AlertaSeverity> = {
  BAJO: 'info', MEDIO: 'warn', ALTO: 'warn', CRITICO: 'danger',
};

@Component({
  selector: 'app-ia',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    ButtonModule, InputTextModule, TabsModule, CardModule,
    TagModule, SelectModule, ProgressSpinnerModule,
    InteractiveGridComponent,
  ],
  template: `

    <div class="page-header">
      <div>
        <h2>Asistente Pedagógico IA</h2>
        <p class="subtitle">Análisis académico y alertas de riesgo — powered by IA</p>
      </div>
    </div>

    <p-tabs>

      <!-- ── Pestaña Chat ── -->
      <p-tabpanel value="0" header="Chat con el asistente">
        <div class="chat-layout">

          <!-- Sugerencias rápidas + selector de sesión -->
          <div style="display:flex;gap:.5rem;align-items:center;flex-wrap:wrap;margin-bottom:.5rem">
            <div class="quick-chips" style="flex:1;margin-bottom:0">
              @for (s of sugerencias; track s) {
                <button class="chip" (click)="usarSugerencia(s)">{{ s }}</button>
              }
            </div>
            @if (sesionesGuardadas().length > 0) {
              <p-button label="Conversaciones" icon="pi pi-history" size="small"
                severity="secondary" [outlined]="true"
                (onClick)="showSesiones.set(!showSesiones())" />
            }
          </div>

          <!-- Panel sesiones guardadas (IA-015) -->
          @if (showSesiones() && sesionesGuardadas().length > 0) {
            <div style="background:var(--surface-50);border:1px solid var(--surface-200);border-radius:8px;padding:.75rem;margin-bottom:.75rem">
              <div style="font-size:.78rem;font-weight:600;color:var(--text-muted);margin-bottom:.5rem;text-transform:uppercase">
                Conversaciones anteriores
              </div>
              @for (ses of sesionesGuardadas(); track ses.sesion_id) {
                <div style="display:flex;gap:.5rem;align-items:center;padding:.35rem 0;border-bottom:1px solid var(--surface-100)">
                  <div style="flex:1;min-width:0">
                    <div style="font-size:.82rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">
                      {{ ses.resumen || '(sin texto)' }}
                    </div>
                    <div style="font-size:.7rem;color:var(--text-muted)">
                      {{ ses.total_mensajes }} mensajes · {{ ses.ultimo_mensaje | date:'dd/MM HH:mm' }}
                    </div>
                  </div>
                  <p-button icon="pi pi-arrow-right" size="small" [text]="true"
                    ariaLabel="Cargar esta conversación" pTooltip="Cargar esta conversación"
                    (onClick)="cargarSesion(ses.sesion_id)" />
                  <p-button icon="pi pi-trash" size="small" [text]="true" severity="danger"
                    ariaLabel="Eliminar conversación" pTooltip="Eliminar" (onClick)="eliminarSesion(ses.sesion_id)" />
                </div>
              }
            </div>
          }

          <!-- Historial de mensajes -->
          <div class="chat-messages" #messagesContainer>
            @if (mensajes().length === 0) {
              <div class="chat-empty">
                <i class="pi pi-comments" style="font-size:2.5rem;color:#CBD5E1"></i>
                <p>¿En qué puedo ayudarte hoy? Pregúntame sobre calificaciones, asistencias, estrategias pedagógicas o normativa SEP/UAEMEX.</p>
              </div>
            }
            @for (m of mensajes(); track m.timestamp) {
              <div [class]="'message-row ' + m.rol">
                <div class="message-bubble">
                  <div class="message-text" [innerHTML]="formatMensaje(m.contenido)"></div>
                  <div class="message-time">{{ m.timestamp | date:'HH:mm' }}</div>
                </div>
              </div>
            }
            @if (cargando()) {
              <div class="message-row assistant">
                <div class="message-bubble typing">
                  <span></span><span></span><span></span>
                </div>
              </div>
            }
          </div>

          <!-- Input -->
          <div class="chat-input-bar">
            <input
              pInputText
              [(ngModel)]="inputMensaje"
              placeholder="Escribe tu pregunta o petición..."
              (keyup.enter)="enviar()"
              [disabled]="cargando()"
              class="chat-input"
            />
            <p-button
              icon="pi pi-send"
              [loading]="cargando()"
              (onClick)="enviar()"
              [disabled]="!inputMensaje.trim()"
              ariaLabel="Enviar mensaje al asistente"
              pTooltip="Enviar"
            />
            <p-button
              icon="pi pi-trash"
              severity="secondary"
              [text]="true"
              (onClick)="limpiarChat()"
              ariaLabel="Iniciar una nueva conversación"
              pTooltip="Nueva conversación"
            />
          </div>
        </div>
      </p-tabpanel>

      <!-- ── Pestaña Alertas ── -->
      <p-tabpanel value="1" header="Alertas académicas">
        <div class="alertas-toolbar">
          <p-select
            [options]="grupos()"
            [(ngModel)]="selectedGrupo"
            optionLabel="_label"
            placeholder="Seleccionar grupo..."
            [showClear]="true"
          

          [filter]="true" filterPlaceholder="Buscar..."/>
          <p-button
            label="Escanear grupo"
            icon="pi pi-search"
            (onClick)="escanearGrupo()"
            [loading]="escaneando()"
            [disabled]="!selectedGrupo"
          />
          <p-button
            label="Cargar alertas"
            icon="pi pi-refresh"
            severity="secondary"
            [text]="true"
            (onClick)="cargarAlertas()"
          />
        </div>

        <app-interactive-grid
          [data]="alertasFlat()"
          [columns]="alertasColumns"
          [loading]="cargandoAlertas()"
        />
      </p-tabpanel>

      <!-- ── Pestaña Chatbot de Datos (NL→SQL) ── -->
      <p-tabpanel value="2" header="Consulta de datos">
        <div class="datos-layout">
          <p style="font-size:.83rem;color:var(--text-color-secondary);margin:0 0 1rem">
            Pregunta en lenguaje natural sobre alumnos, calificaciones, asistencias y más.
            El sistema genera y ejecuta el SQL automáticamente, respetando tu nivel de acceso.
          </p>

          <div class="quick-chips">
            @for (s of sugerenciasDatos; track s) {
              <button class="chip" (click)="usarSugerenciaDatos(s)">{{ s }}</button>
            }
          </div>

          <div class="datos-input-bar">
            <input pInputText [(ngModel)]="inputDatos"
              placeholder="¿Cuántos alumnos reprobaron matemáticas este bimestre?"
              (keyup.enter)="consultarDatos()"
              [disabled]="cargandoDatos()"
              style="flex:1" />
            <p-button icon="pi pi-search" label="Consultar"
              [loading]="cargandoDatos()" (onClick)="consultarDatos()"
              [disabled]="!inputDatos.trim()" />
          </div>

          @if (respuestaDatos()) {
            <div class="datos-respuesta">
              <p class="datos-texto">{{ respuestaDatos()!.respuesta }}</p>
              @if (respuestaDatos()!.sql_generado) {
                <details class="sql-details">
                  <summary>SQL generado</summary>
                  <pre class="sql-code">{{ respuestaDatos()!.sql_generado }}</pre>
                </details>
              }
              @if (respuestaDatos()!.datos && respuestaDatos()!.datos!.length > 0) {
                <div style="margin-top:1rem;overflow-x:auto">
                  <table class="datos-tabla">
                    <thead>
                      <tr>
                        @for (col of columnasTabla(); track col) {
                          <th>{{ col }}</th>
                        }
                      </tr>
                    </thead>
                    <tbody>
                      @for (row of respuestaDatos()!.datos!.slice(0,50); track $index) {
                        <tr>
                          @for (col of columnasTabla(); track col) {
                            <td>{{ row[col] ?? '—' }}</td>
                          }
                        </tr>
                      }
                    </tbody>
                  </table>
                  @if ((respuestaDatos()!.datos?.length ?? 0) > 50) {
                    <p style="font-size:.75rem;color:var(--text-muted);margin:.5rem 0 0">
                      Mostrando 50 de {{ respuestaDatos()!.datos!.length }} resultados
                    </p>
                  }
                </div>
              }
            </div>
          }
        </div>
      </p-tabpanel>

    </p-tabs>
  `,
  styles: [`
    /* ── Chat layout ──────────────────────────────────────────────────────── */
    .chat-layout { display: flex; flex-direction: column; height: calc(100vh - 220px); gap: 0.75rem; }

    .quick-chips { display: flex; flex-wrap: wrap; gap: 0.4rem; }
    .chip {
      padding: 0.3rem 0.8rem;
      border-radius: 20px;
      background: var(--nevadi-red-lighter);
      border: 1px solid var(--nevadi-red-light);
      color: var(--nevadi-red-dark);
      font-size: 0.78rem;
      cursor: pointer;
      transition: background .15s;
      font-family: var(--font-body);
    }
    .chip:hover { background: var(--nevadi-red-light); }

    .chat-messages {
      flex: 1; overflow-y: auto;
      display: flex; flex-direction: column; gap: 0.75rem;
      padding: 0.5rem;
      background: #F8F9FA;
      border-radius: 8px;
      border: 1px solid var(--surface-border);
    }
    .chat-empty {
      flex: 1; display: flex; flex-direction: column;
      align-items: center; justify-content: center;
      gap: 0.75rem; color: #94A3B8; text-align: center; padding: 2rem;
    }
    .message-row { display: flex; }
    .message-row.user     { justify-content: flex-end; }
    .message-row.assistant { justify-content: flex-start; }

    .message-bubble {
      max-width: 75%;
      padding: 0.6rem 0.9rem;
      border-radius: 12px;
      font-size: 0.88rem;
      line-height: 1.5;
    }
    .message-row.user .message-bubble {
      background: var(--nevadi-red);
      color: #fff;
      border-bottom-right-radius: 3px;
    }
    .message-row.assistant .message-bubble {
      background: #fff;
      border: 1px solid var(--surface-border);
      border-bottom-left-radius: 3px;
    }
    .message-text :global(p) { margin: 0 0 0.4rem; }
    .message-text :global(ul) { margin: 0.4rem 0; padding-left: 1.2rem; }
    .message-time { font-size: 0.68rem; opacity: 0.65; margin-top: 3px; text-align: right; }

    /* Typing animation */
    .typing { padding: 0.5rem 0.9rem; }
    .typing span {
      display: inline-block; width: 7px; height: 7px;
      border-radius: 50%; background: #94A3B8; margin: 0 2px;
      animation: bounce 1.2s infinite;
    }
    .typing span:nth-child(2) { animation-delay: .2s; }
    .typing span:nth-child(3) { animation-delay: .4s; }
    @keyframes bounce {
      0%, 60%, 100% { transform: translateY(0); }
      30% { transform: translateY(-6px); }
    }

    .chat-input-bar { display: flex; gap: 0.5rem; align-items: center; }
    .chat-input { flex: 1; }

    /* ── Alertas ───────────────────────────────────────────────────────────── */
    .alertas-toolbar { display: flex; gap: 0.75rem; margin-bottom: 1rem; align-items: center; flex-wrap: wrap; }
    tr.atendida { opacity: 0.5; }

    /* ── Consulta de Datos (NL→SQL) ────────────────────────────────────────── */
    .datos-layout { display: flex; flex-direction: column; gap: 1rem; }
    .datos-input-bar { display: flex; gap: .5rem; align-items: center; }
    .datos-respuesta { background: #F8F9FA; border: 1px solid var(--surface-border); border-radius: 8px; padding: 1rem; }
    .datos-texto { font-size: .9rem; line-height: 1.6; margin: 0 0 .75rem; }
    .sql-details { margin-top: .5rem; }
    .sql-details summary { font-size: .78rem; color: var(--text-color-secondary); cursor: pointer; }
    .sql-code { font-size: .75rem; background: #1e293b; color: #e2e8f0; padding: .75rem 1rem; border-radius: 6px; overflow-x: auto; white-space: pre-wrap; margin: .5rem 0 0; }
    .datos-tabla { width: 100%; border-collapse: collapse; font-size: .8rem; }
    .datos-tabla th { background: var(--surface-100); padding: .4rem .6rem; text-align: left; font-weight: 600; border-bottom: 2px solid var(--surface-300); }
    .datos-tabla td { padding: .35rem .6rem; border-bottom: 1px solid var(--surface-200); }
    .datos-tabla tr:hover td { background: var(--surface-50); }
  `],
})
export class IaComponent implements OnInit implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private readonly api = inject(ApiService);
  readonly ctx = inject(ContextService);
  private readonly notify = inject(ApexNotificationService);

  @ViewChild('messagesContainer') messagesContainer?: ElementRef<HTMLDivElement>;

  mensajes          = signal<Mensaje[]>([]);
  cargando          = signal(false);
  sesionesGuardadas = signal<any[]>([]);
  showSesiones      = signal(false);
  inputMensaje = '';
  sesionId: string = crypto.randomUUID();

  grupos   = signal<GrupoConLabel[]>([]);
  alertas  = signal<Alerta[]>([]);
  cargandoAlertas = signal(false);

  readonly alertasFlat = computed(() =>
    this.alertas().map(a => ({
      ...a,
      fecha_str:   a.fccreacion ? new Date(a.fccreacion).toLocaleDateString('es-MX') : '—',
      estado_str:  a.atendida ? 'Atendida' : 'Pendiente',
    }))
  );
  readonly alertasColumns: ColumnConfig[] = [
    { field: 'tipo_alerta',  header: 'Tipo',        width: '130px' },
    { field: 'nivel_riesgo', header: 'Riesgo',      width: '90px' },
    { field: 'descripcion',  header: 'Descripción' },
    { field: 'fecha_str',    header: 'Fecha',       width: '110px' },
    { field: 'estado_str',   header: 'Estado',      width: '90px' },
  ];
  escaneando = signal(false);
  selectedGrupo: GrupoConLabel | null = null;

  readonly sugerencias = [
    '¿Cuáles son los alumnos en riesgo de reprobar?',
    'Sugiere estrategias para mejorar la asistencia',
    'Explica los criterios de acreditación UAEMEX',
    'Genera una rúbrica para evaluación de exposición oral',
    'Redacta un comunicado de bajo rendimiento para padres',
  ];

  // ── NL→SQL (FASE 17) ─────────────────────────────────────────────────────
  inputDatos       = '';
  cargandoDatos    = signal(false);
  respuestaDatos   = signal<{ respuesta: string; sql_generado?: string; datos?: any[] } | null>(null);
  columnasTabla    = signal<string[]>([]);
  sesiónDatos      = crypto.randomUUID();

  readonly sugerenciasDatos = [
    '¿Cuántos alumnos tienen calificación menor a 6?',
    '¿Cuál es el promedio por materia en preparatoria?',
    '¿Qué alumnos tienen más del 20% de inasistencias?',
    'Listar los 10 alumnos con mejor promedio general',
    '¿Cuántos alumnos reprobaron al menos una materia?',
  ];

  ngOnInit(): void {
    const plantelId = this.ctx.plantel()?.id;
    if (plantelId) {
      this.api.get<Grupo[]>('/grupos', { plantel_id: plantelId, solo_activos: true, ciclo_vigente: true })
        .subscribe(g => this.grupos.set(g.map(x => ({ ...x, _label: grupoLabel(x) }))));
    }
    this.cargarAlertas();
    this._cargarSesiones();
  }

  private _cargarSesiones(): void {
    this.api.get<any[]>('/ai/mis-sesiones?limite=8').subscribe({
      next: (items) => this.sesionesGuardadas.set(items || []),
      error: () => {},
    });
  }

  cargarSesion(sesionId: string): void {
    this.api.get<any>(`/ai/sesion/${sesionId}`).subscribe({
      next: (data) => {
        const msgs: Mensaje[] = (data.mensajes || []).map((m: any) => ({
          rol: m.rol as 'user' | 'assistant',
          contenido: m.contenido,
          timestamp: new Date(m.timestamp),
        }));
        this.mensajes.set(msgs);
        this.sesionId = sesionId;
        this.showSesiones.set(false);
        setTimeout(() => this._scrollToBottom(), 100);
      },
      error: () => this.notify.error('No se pudo cargar la conversación'),
    });
  }

  eliminarSesion(sesionId: string): void {
    this.api.delete(`/ai/sesion/${sesionId}`).subscribe({
      next: () => {
        this.sesionesGuardadas.update(s => s.filter(x => x.sesion_id !== sesionId));
        if (this.sesionId === sesionId) this.limpiarChat();
        this.notify.success('Conversación eliminada');
      },
      error: () => this.notify.error('Error al eliminar conversación'),
    });
  }

  usarSugerencia(s: string): void {
    this.inputMensaje = s;
    this.enviar();
  }

  enviar(): void {
    const texto = this.inputMensaje.trim();
    if (!texto || this.cargando()) return;

    const userMsg: Mensaje = { rol: 'user', contenido: texto, timestamp: new Date() };
    this.mensajes.update(m => [...m, userMsg]);
    this.inputMensaje = '';
    this.cargando.set(true);

    const historial = this.mensajes().slice(-10).map(m => ({ role: m.rol, content: m.contenido }));
    const contexto: Record<string, any> = {};
    if (this.ctx.plantel()) contexto['plantel'] = this.ctx.plantel()!.nombre_plantel;
    if (this.ctx.ciclo()) contexto['ciclo'] = this.ctx.ciclo()!.nombre_ciclo;

    this.api.post<any>('/ai/chat', {
      sesion_id: this.sesionId,
      mensaje: texto,
      historial: historial.slice(0, -1),
      contexto,
    }).subscribe({
      next: (r) => {
        this.mensajes.update(m => [...m, { rol: 'assistant', contenido: r.respuesta, timestamp: new Date() }]);
        this.cargando.set(false);
        this._scrollToBottom();
      },
      error: (e) => {
        this.cargando.set(false);
        this.notify.error('Error', e.error?.detail || 'Error al contactar al asistente');
      },
    });
  }

  limpiarChat(): void {
    this.mensajes.set([]);
    this.sesionId = crypto.randomUUID();
    this.showSesiones.set(false);
    this._cargarSesiones();
  }

  formatMensaje(texto: string): string {
    return texto
      .replace(/\n\n/g, '</p><p>')
      .replace(/\n/g, '<br>')
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/^- /gm, '• ')
      .replace(/^(\d+)\. /gm, '$1. ');
  }

  cargarAlertas(): void {
    this.cargandoAlertas.set(true);
    const params: Record<string, any> = { atendida: false };
    if (this.selectedGrupo) params['grupo_id'] = this.selectedGrupo.id;
    this.api.get<Alerta[]>('/ai/alertas', params)
      .subscribe({ next: a => { this.alertas.set(a); this.cargandoAlertas.set(false); }, error: () => this.cargandoAlertas.set(false) });
  }

  escanearGrupo(): void {
    if (!this.selectedGrupo) return;
    this.escaneando.set(true);
    this.api.post<any>(`/ai/alertas/scan/${this.selectedGrupo.id}`, {}).subscribe({
      next: (r) => {
        this.escaneando.set(false);
        this.notify.success('Escaneo completado', `${r.alertas_generadas} alerta(s) generadas para ${r.alumnos_analizados} alumnos`);
        this.cargarAlertas();
      },
      error: () => this.escaneando.set(false),
    });
  }

  riesgoSeverity(nivel: string): AlertaSeverity {
    return RIESGO_SEVERITY[nivel] || 'info';
  }

  private _scrollToBottom(): void {
    setTimeout(() => {
      if (this.messagesContainer) {
        this.messagesContainer.nativeElement.scrollTop = this.messagesContainer.nativeElement.scrollHeight;
      }
    }, 50);
  }

  // ── Consulta de datos NL→SQL ──────────────────────────────────────────────

  usarSugerenciaDatos(s: string): void { this.inputDatos = s; this.consultarDatos(); }

  consultarDatos(): void {
    const q = this.inputDatos.trim();
    if (!q) return;
    this.cargandoDatos.set(true);
    this.respuestaDatos.set(null);

    this.api.post<{ respuesta: string; sql_generado?: string; datos?: any[] }>(
      '/chatbot/mensaje',
      { pregunta: q, sesion_id: this.sesiónDatos }
    ).subscribe({
      next: resp => {
        this.respuestaDatos.set(resp);
        if (resp.datos?.length) {
          this.columnasTabla.set(Object.keys(resp.datos[0]));
        }
        this.cargandoDatos.set(false);
      },
      error: (err) => {
        const msg = err?.error?.detail ?? 'Error al procesar la consulta';
        this.respuestaDatos.set({ respuesta: `⚠ ${msg}` });
        this.cargandoDatos.set(false);
      },
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
