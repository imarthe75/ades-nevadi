import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, TemplateRef, ContentChild } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface ApexMediaListItem {
  id?: string | number;
  title: string;
  description?: string;
  image?: string;
  avatarIcon?: string;
  avatarColor?: string;
  badge?: string;
  badgeSeverity?: 'success' | 'info' | 'warning' | 'danger';
  data?: any;
}

@Component({
  selector: 'apex-medialist',
  standalone: true,
  imports: [CommonModule],
  template: `
    <ul class="apex-medialist">
      <li *ngFor="let item of items" class="apex-medialist-item" (click)="onItemClick(item)">
        <ng-container *ngIf="customTemplate; else defaultTemplate">
          <ng-container *ngTemplateOutlet="customTemplate; context: {$implicit: item}"></ng-container>
        </ng-container>
        
        <ng-template #defaultTemplate>
          <div class="apex-medialist-media">
            <img *ngIf="item.image" [src]="item.image" [alt]="item.title" class="apex-medialist-image" />
            <div *ngIf="!item.image && item.avatarIcon" 
                 class="apex-medialist-avatar" 
                 [style.background-color]="item.avatarColor || 'var(--primary-color)'">
              <i [ngClass]="item.avatarIcon"></i>
            </div>
          </div>
          <div class="apex-medialist-content">
            <div class="apex-medialist-header">
              <h4 class="apex-medialist-title">{{item.title}}</h4>
              <span *ngIf="item.badge" class="apex-medialist-badge" [ngClass]="'badge-' + (item.badgeSeverity || 'info')">
                {{item.badge}}
              </span>
            </div>
            <p *ngIf="item.description" class="apex-medialist-desc">{{item.description}}</p>
          </div>
        </ng-template>
      </li>
    </ul>
  `,
  styles: [`
    :host {
      display: block;
    }
    .apex-medialist {
      list-style-type: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }
    .apex-medialist-item {
      display: flex;
      padding: 1rem;
      border-radius: var(--border-radius, 8px);
      background: var(--surface-card, #ffffff);
      border: 1px solid var(--surface-border, #e5e7eb);
      cursor: pointer;
      transition: background-color 0.2s;
    }
    .apex-medialist-item:hover {
      background: var(--surface-hover, #f3f4f6);
    }
    .apex-medialist-media {
      margin-right: 1rem;
      flex-shrink: 0;
    }
    .apex-medialist-image {
      width: 64px;
      height: 64px;
      object-fit: cover;
      border-radius: 8px;
    }
    .apex-medialist-avatar {
      width: 64px;
      height: 64px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #ffffff;
      font-size: 1.5rem;
    }
    .apex-medialist-content {
      flex-grow: 1;
      display: flex;
      flex-direction: column;
      justify-content: center;
    }
    .apex-medialist-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 0.25rem;
    }
    .apex-medialist-title {
      margin: 0;
      font-size: 1.125rem;
      font-weight: 600;
      color: var(--text-color, #1f2937);
    }
    .apex-medialist-desc {
      margin: 0;
      font-size: 0.875rem;
      color: var(--text-color-secondary, #6b7280);
      line-height: 1.4;
    }
    .apex-medialist-badge {
      padding: 0.25rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 700;
      text-transform: uppercase;
    }
    .badge-info { background: #e0f2fe; color: #0284c7; }
    .badge-success { background: #dcfce3; color: #16a34a; }
    .badge-warning { background: #fef3c7; color: #d97706; }
    .badge-danger { background: #fee2e2; color: #dc2626; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexMediaListComponent {
  /** Array of media items */
  @Input() items: ApexMediaListItem[] = [];

  /** Custom template for items */
  @ContentChild(TemplateRef) customTemplate!: TemplateRef<any>;

  /** Event emitted when an item is clicked */
  @Output() itemClick = new EventEmitter<ApexMediaListItem>();

  public onItemClick(item: ApexMediaListItem): void {
    this.itemClick.emit(item);
  }
}
