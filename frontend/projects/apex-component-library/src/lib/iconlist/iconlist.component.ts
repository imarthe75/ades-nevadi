import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, TemplateRef, ContentChild } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface ApexIconListItem {
  id?: string | number;
  label: string;
  icon?: string;
  description?: string;
  color?: string;
  disabled?: boolean;
  data?: any;
}

@Component({
  selector: 'apex-iconlist',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="apex-iconlist-container" [ngClass]="layout">
      <div *ngFor="let item of items" 
           class="apex-iconlist-item" 
           [ngClass]="{'is-disabled': item.disabled}"
           (click)="onItemClick(item)">
        <ng-container *ngIf="customTemplate; else defaultTemplate">
          <ng-container *ngTemplateOutlet="customTemplate; context: {$implicit: item}"></ng-container>
        </ng-container>
        
        <ng-template #defaultTemplate>
          <div class="apex-iconlist-icon-wrapper" [style.background-color]="item.color || 'var(--primary-color)'">
            <i class="apex-iconlist-icon" [ngClass]="item.icon || 'pi pi-star'"></i>
          </div>
          <div class="apex-iconlist-content">
            <div class="apex-iconlist-label">{{item.label}}</div>
            <div class="apex-iconlist-desc" *ngIf="item.description">{{item.description}}</div>
          </div>
        </ng-template>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
    }
    .apex-iconlist-container {
      display: flex;
      flex-wrap: wrap;
      gap: 1rem;
    }
    .apex-iconlist-container.grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    }
    .apex-iconlist-container.list {
      flex-direction: column;
    }
    .apex-iconlist-item {
      display: flex;
      align-items: center;
      padding: 1rem;
      border-radius: var(--border-radius, 8px);
      background: var(--surface-card, #ffffff);
      border: 1px solid var(--surface-border, #e5e7eb);
      cursor: pointer;
      transition: background-color 0.2s, box-shadow 0.2s;
    }
    .apex-iconlist-item:hover:not(.is-disabled) {
      background: var(--surface-hover, #f3f4f6);
      box-shadow: 0 2px 4px rgba(0,0,0,0.05);
    }
    .apex-iconlist-item.is-disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
    .apex-iconlist-icon-wrapper {
      width: 40px;
      height: 40px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-right: 1rem;
      color: #ffffff;
      flex-shrink: 0;
    }
    .apex-iconlist-icon {
      font-size: 1.2rem;
    }
    .apex-iconlist-content {
      flex-grow: 1;
      overflow: hidden;
    }
    .apex-iconlist-label {
      font-weight: 600;
      color: var(--text-color, #1f2937);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .apex-iconlist-desc {
      font-size: 0.875rem;
      color: var(--text-color-secondary, #6b7280);
      margin-top: 0.25rem;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexIconListComponent {
  /** Array of items to display */
  @Input() items: ApexIconListItem[] = [];

  /** Layout of the icon list */
  @Input() layout: 'flex' | 'grid' | 'list' = 'flex';

  /** Custom template for items */
  @ContentChild(TemplateRef) customTemplate!: TemplateRef<any>;

  /** Event emitted when an item is clicked */
  @Output() itemClick = new EventEmitter<ApexIconListItem>();

  public onItemClick(item: ApexIconListItem): void {
    if (!item.disabled) {
      this.itemClick.emit(item);
    }
  }
}
