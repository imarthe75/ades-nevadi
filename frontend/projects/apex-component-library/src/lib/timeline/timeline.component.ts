import { Component, Input, ChangeDetectionStrategy, TemplateRef, ContentChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TimelineModule } from 'primeng/timeline';

@Component({
  selector: 'apex-timeline',
  standalone: true,
  imports: [CommonModule, TimelineModule],
  template: `
    <div class="apex-timeline-wrapper">
      <h3 *ngIf="title" class="apex-timeline-title">{{title}}</h3>
      <p-timeline 
        [value]="value" 
        [align]="align" 
        [layout]="layout" 
        styleClass="apex-p-timeline">
        
        <ng-template pTemplate="marker" let-event>
          <ng-container *ngIf="markerTemplate; else defaultMarker">
            <ng-container *ngTemplateOutlet="markerTemplate; context: {$implicit: event}"></ng-container>
          </ng-container>
          <ng-template #defaultMarker>
            <span 
              class="apex-timeline-marker-default" 
              [style.backgroundColor]="event.color || 'var(--primary-color)'">
              <i *ngIf="event.icon" [class]="event.icon"></i>
            </span>
          </ng-template>
        </ng-template>

        <ng-template pTemplate="content" let-event>
          <ng-container *ngIf="contentTemplate; else defaultContent">
            <ng-container *ngTemplateOutlet="contentTemplate; context: {$implicit: event}"></ng-container>
          </ng-container>
          <ng-template #defaultContent>
            <div class="apex-timeline-content-default p-card">
              <div class="apex-timeline-event-title">{{event.status || event.title}}</div>
              <div class="apex-timeline-event-date">{{event.date}}</div>
              <p *ngIf="event.description" class="apex-timeline-event-desc">{{event.description}}</p>
            </div>
          </ng-template>
        </ng-template>
      </p-timeline>
    </div>
  `,
  styles: [`
    .apex-timeline-wrapper {
      width: 100%;
      margin-bottom: 1rem;
    }
    .apex-timeline-title {
      font-size: 1.1rem;
      font-weight: 600;
      margin-bottom: 1rem;
      color: var(--text-color);
    }
    .apex-timeline-marker-default {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 2rem;
      height: 2rem;
      border-radius: 50%;
      color: #ffffff;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }
    .apex-timeline-content-default {
      padding: 1rem;
      border-radius: var(--border-radius);
      margin-bottom: 1rem;
      background: var(--surface-card);
      border: 1px solid var(--surface-border);
    }
    .apex-timeline-event-title {
      font-weight: 600;
      font-size: 1rem;
      color: var(--text-color);
      margin-bottom: 0.25rem;
    }
    .apex-timeline-event-date {
      font-size: 0.85rem;
      color: var(--text-color-secondary);
      margin-bottom: 0.5rem;
    }
    .apex-timeline-event-desc {
      margin: 0;
      font-size: 0.95rem;
      line-height: 1.5;
      color: var(--text-color);
    }
    ::ng-deep .apex-p-timeline.p-timeline-vertical .p-timeline-event-opposite {
      flex: 0;
      padding: 0;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexTimelineComponent {
  @Input() value: any[] = [];
  @Input() title?: string;
  @Input() align: 'left' | 'right' | 'alternate' | 'top' | 'bottom' = 'left';
  @Input() layout: 'vertical' | 'horizontal' = 'vertical';

  @ContentChild('marker') markerTemplate?: TemplateRef<any>;
  @ContentChild('content') contentTemplate?: TemplateRef<any>;
}
