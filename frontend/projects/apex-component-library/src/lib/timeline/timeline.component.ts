import { Component, Input, ChangeDetectionStrategy, TemplateRef, ContentChild } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { TimelineModule } from 'primeng/timeline';

@Component({
  selector: 'apex-timeline',
  standalone: true,
  imports: [TimelineModule, NgTemplateOutlet],
  template: `
    <div class="apex-timeline-wrapper">
      @if (title) {
        <div class="apex-timeline-header">
          <h4 class="apex-timeline-title">{{ title }}</h4>
        </div>
      }
      <p-timeline
        [value]="value"
        [align]="align"
        [layout]="layout"
        styleClass="apex-p-timeline">

        <ng-template pTemplate="marker" let-event>
          @if (markerTemplate) {
            <ng-container *ngTemplateOutlet="markerTemplate; context: { $implicit: event }" />
          } @else {
            <span
              class="apex-timeline-marker"
              [style.background]="markerColor(event)">
              @if (event.icon) {
                <i [class]="event.icon"></i>
              }
            </span>
          }
        </ng-template>

        <ng-template pTemplate="content" let-event>
          @if (contentTemplate) {
            <ng-container *ngTemplateOutlet="contentTemplate; context: { $implicit: event }" />
          } @else {
            <div class="apex-timeline-card">
              <div class="apex-timeline-card-title">{{ event.status || event.title }}</div>
              @if (event.date) {
                <div class="apex-timeline-card-date">
                  @if (showRelativeTime) {
                    <span class="apex-timeline-relative" [title]="event.date">{{ relativeTime(event.date) }}</span>
                    <span class="apex-timeline-absolute"> · {{ event.date }}</span>
                  } @else {
                    {{ event.date }}
                  }
                </div>
              }
              @if (showActor && event.actor) {
                <div class="apex-timeline-card-actor">{{ event.actor }}</div>
              }
              @if (event.description) {
                <p class="apex-timeline-card-desc">{{ event.description }}</p>
              }
            </div>
          }
        </ng-template>

      </p-timeline>
    </div>
  `,
  styles: [`
    .apex-timeline-wrapper { width: 100%; }
    .apex-timeline-header {
      padding: 0.6rem 1rem;
      border-bottom: 1px solid var(--surface-border);
      background: var(--surface-section);
      margin-bottom: 1rem;
      border-radius: var(--border-radius, 6px) var(--border-radius, 6px) 0 0;
    }
    .apex-timeline-title {
      margin: 0;
      font-size: 0.85rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--text-color);
    }
    .apex-timeline-marker {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 2rem;
      height: 2rem;
      border-radius: 50%;
      color: #fff;
      box-shadow: 0 2px 6px rgba(0,0,0,0.12);
      flex-shrink: 0;
    }
    .apex-timeline-marker i { font-size: 0.85rem; }
    .apex-timeline-card {
      background: var(--surface-card);
      border: 1px solid var(--surface-border);
      border-radius: var(--border-radius, 6px);
      padding: 0.75rem 1rem;
      margin-bottom: 0.75rem;
    }
    .apex-timeline-card-title {
      font-weight: 600;
      font-size: 0.875rem;
      color: var(--text-color);
      margin-bottom: 0.15rem;
    }
    .apex-timeline-card-date {
      font-size: 0.78rem;
      color: var(--text-color-secondary);
      margin-bottom: 0.35rem;
    }
    .apex-timeline-relative {
      font-weight: 500;
    }
    .apex-timeline-absolute {
      color: var(--text-color-secondary);
      font-size: 0.75rem;
    }
    .apex-timeline-card-actor {
      font-size: 0.78rem;
      color: var(--text-color-secondary);
      font-style: italic;
      margin-bottom: 0.25rem;
    }
    .apex-timeline-card-desc {
      margin: 0;
      font-size: 0.825rem;
      color: var(--text-color-secondary);
      line-height: 1.5;
    }
    :host ::ng-deep .apex-p-timeline.p-timeline-vertical
      .p-timeline-event-opposite { flex: 0; padding: 0; min-width: 0; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexTimelineComponent {
  @Input() value: any[] = [];
  @Input() title?: string;
  @Input() align: 'left' | 'right' | 'alternate' | 'top' | 'bottom' = 'left';
  @Input() layout: 'vertical' | 'horizontal' = 'vertical';
  @Input() showActor = true;
  @Input() showRelativeTime = true;

  @ContentChild('marker') markerTemplate?: TemplateRef<any>;
  @ContentChild('content') contentTemplate?: TemplateRef<any>;

  relativeTime(dateStr: string | Date): string {
    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const rtf = new Intl.RelativeTimeFormat('es-MX', { numeric: 'auto' });
    if (diffMins < 1) return 'ahora';
    if (diffMins < 60) return rtf.format(-diffMins, 'minute');
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return rtf.format(-diffHours, 'hour');
    const diffDays = Math.floor(diffHours / 24);
    if (diffDays < 30) return rtf.format(-diffDays, 'day');
    return date.toLocaleDateString('es-MX');
  }

  markerColor(event: any): string {
    switch (event.severity) {
      case 'success': return 'var(--green-500)';
      case 'info':    return 'var(--blue-500)';
      case 'warning': return 'var(--yellow-500)';
      case 'error':
      case 'danger':  return 'var(--red-500)';
      default:        return event.color || 'var(--primary-color)';
    }
  }
}
