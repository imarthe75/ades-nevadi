/**
 * IaComponent — Asistente pedagógico IA + Panel de alertas académicas.
 *
 * Dos pestañas:
 *   1. Chat con el asistente (Claude vía backend)
 *   2. Alertas académicas del grupo/plantel con botón "Escanear grupo"
 */
import { Component, OnInit, inject, signal, ElementRef, ViewChild, afterNextRender } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TabsModule } from 'primeng/tabs';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { SelectModule } from 'primeng/select';
import { ToastModule } from 'primeng/toast';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import type { Grupo } from '../../core/models';

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
    ButtonModule, InputTextModule, TabsModule, CardModule, TableModule,
    TagModule, SelectModule, ToastModule, ProgressSpinnerModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast />

    <div class="page-header">
      <div>
        <h2>Asistente Pedagógico IA</h2>
        <p class="subtitle">Análisis académico y alertas de riesgo — powered by Claude</p>
      </div>
    </div>

    <p-tabs>

      <!-- ── Pestaña Chat ── -->
      <p-tabpanel value="0" header="Chat con el asistente">
        <div class="chat-layout">

          <!-- Sugerencias rápidas -->
          <div class="quick-chips">
            @for (s of sugerencias; track s) {
              <button class="chip" (click)="usarSugerencia(s)">{{ s }}</button>
            }
          </div>

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
              pTooltip="Enviar"
            />
            <p-button
              icon="pi pi-trash"
              severity="secondary"
              [text]="true"
              (onClick)="limpiarChat()"
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
            optionLabel="nombre_grupo"
            placeholder="Seleccionar grupo..."
            [showClear]="true"
          />
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

        <p-table
          [value]="alertas()"
          [rows]="20"
          [paginator]="true"
          styleClass="p-datatable-sm p-datatable-striped"
          [loading]="cargandoAlertas()"
        >
          <ng-template pTemplate="header">
            <tr>
              <th style="width:130px">Tipo</th>
              <th style="width:90px;text-align:center">Riesgo</th>
              <th>Descripción</th>
              <th style="width:100px">Fecha</th>
              <th style="width:90px;text-align:center">Estado</th>
            </tr>
          </ng-template>

          <ng-template pTemplate="body" let-a>
            <tr [class.atendida]="a.atendida">
              <td><small>{{ a.tipo_alerta | titlecase }}</small></td>
              <td style="text-align:center">
                <p-tag [value]="a.nivel_riesgo" [severity]="riesgoSeverity(a.nivel_riesgo)" />
              </td>
              <td style="font-size:0.82rem">{{ a.descripcion }}</td>
              <td style="font-size:0.8rem">{{ a.fccreacion | date:'dd/MM/yy HH:mm' }}</td>
              <td style="text-align:center">
                @if (a.atendida) {
                  <p-tag value="Atendida" severity="success" />
                } @else {
                  <p-button label="Atender" [size]="'small'" severity="warn" [text]="true" />
                }
              </td>
            </tr>
          </ng-template>

          <ng-template pTemplate="emptymessage">
            <tr><td [colSpan]="5" style="text-align:center;padding:2rem;color:#94A3B8">
              Sin alertas activas. Usa "Escanear grupo" para detectar riesgos.
            </td></tr>
          </ng-template>
        </p-table>
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
  `],
})
export class IaComponent implements OnInit {
  private readonly api = inject(ApiService);
  readonly ctx = inject(ContextService);
  private readonly msg = inject(MessageService);

  @ViewChild('messagesContainer') messagesContainer?: ElementRef<HTMLDivElement>;

  mensajes   = signal<Mensaje[]>([]);
  cargando   = signal(false);
  inputMensaje = '';
  sesionId   = crypto.randomUUID();

  grupos   = signal<Grupo[]>([]);
  alertas  = signal<Alerta[]>([]);
  cargandoAlertas = signal(false);
  escaneando = signal(false);
  selectedGrupo: Grupo | null = null;

  readonly sugerencias = [
    '¿Cuáles son los alumnos en riesgo de reprobar?',
    'Sugiere estrategias para mejorar la asistencia',
    'Explica los criterios de acreditación UAEMEX',
    'Genera una rúbrica para evaluación de exposición oral',
    'Redacta un comunicado de bajo rendimiento para padres',
  ];

  ngOnInit(): void {
    const plantelId = this.ctx.plantel()?.id;
    if (plantelId) {
      this.api.get<Grupo[]>('/grupos', { plantel_id: plantelId, solo_activos: true, ciclo_vigente: true })
        .subscribe(g => this.grupos.set(g));
    }
    this.cargarAlertas();
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
        this.msg.add({ severity: 'error', summary: 'Error', detail: e.error?.detail || 'Error al contactar al asistente' });
      },
    });
  }

  limpiarChat(): void {
    this.mensajes.set([]);
    this.sesionId = crypto.randomUUID();
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
        this.msg.add({ severity: 'success', summary: 'Escaneo completado', detail: `${r.alertas_generadas} alerta(s) generadas para ${r.alumnos_analizados} alumnos` });
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
}
