/**
 * PushNotificationService — FASE 20 (ntfy).
 *
 * Conecta el navegador al stream SSE de ntfy para recibir notificaciones push
 * en tiempo real, sin necesidad de service worker.
 *
 * Flujo:
 *   1. Al hacer login → init() → GET /push/suscripcion
 *   2. Abre EventSource a ntfy/{topic}/sse
 *   3. Cada mensaje dispara un Subject que el componente de notificaciones observa
 *   4. Muestra notificación nativa del browser si el permiso está concedido
 */
import { Injectable, inject } from '@angular/core';
import { Subject } from 'rxjs';
import { ApiService } from './api.service';

export interface PushEvent {
  id: string;
  event: string;
  topic: string;
  title: string;
  message: string;
  priority: number;
  time: number;
  tags?: string[];
  click?: string;
}

@Injectable({ providedIn: 'root' })
export class PushNotificationService {
  private readonly api = inject(ApiService);

  private _eventSource: EventSource | null = null;
  private _topic = '';

  readonly events$ = new Subject<PushEvent>();
  readonly connected$ = new Subject<boolean>();

  async init(ntfyBaseUrl?: string): Promise<void> {
    try {
      const sub = await this.api.get<{
        topic: string;
        url_sse: string;
        token_requerido: boolean;
      }>('/push/suscripcion').toPromise();

      if (!sub) return;
      this._topic = sub.topic;

      // Solicitar permiso para notificaciones nativas
      if ('Notification' in window && Notification.permission === 'default') {
        await Notification.requestPermission();
      }

      this._conectar(sub.url_sse);
    } catch {
      // ntfy no disponible — modo degradado (solo campanita in-app)
    }
  }

  private _conectar(url: string): void {
    this._desconectar();

    const es = new EventSource(url);
    this._eventSource = es;

    es.onmessage = (ev) => {
      try {
        const data: PushEvent = JSON.parse(ev.data);
        if (data.event === 'keepalive') return;
        this.events$.next(data);
        this._mostrarNativa(data);
      } catch { /* ignorar mensajes malformados */ }
    };

    es.onopen = () => this.connected$.next(true);
    es.onerror = () => {
      this.connected$.next(false);
      // Reconectar automáticamente cada 30s si hay error
      setTimeout(() => {
        if (this._eventSource === es) this._conectar(url);
      }, 30_000);
    };
  }

  private _mostrarNativa(ev: PushEvent): void {
    if (!('Notification' in window) || Notification.permission !== 'granted') return;
    const n = new Notification(ev.title || 'ADES', {
      body: ev.message,
      icon: '/assets/nevadi-images/favicon.ico',
      tag: ev.id,
    });
    if (ev.click) n.onclick = () => { window.open(ev.click, '_blank'); n.close(); };
  }

  private _desconectar(): void {
    if (this._eventSource) {
      this._eventSource.close();
      this._eventSource = null;
      this.connected$.next(false);
    }
  }

  destroy(): void { this._desconectar(); }
}
