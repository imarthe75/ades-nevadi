import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';

export interface ApexListItemAction {
  icon: string;
  label: string;
  action: (item: ApexListItem) => void;
}

export interface ApexListItem {
  id: string | number;
  title: string;
  subtitle?: string;
  description?: string;
  icon?: string;
  iconColor?: string;
  badge?: string;
  badgeSeverity?: 'success' | 'info' | 'warning' | 'danger' | 'secondary';
  actions?: ApexListItemAction[];
  [key: string]: any;
}

@Component({
  selector: 'apex-list',
  standalone: true,
  imports: [],
  template: `
    <div class="apex-list-region">
      @if (title) {
        <div class="apex-list-header">
          <h4 class="apex-list-header-title">{{ title }}</h4>
          @if (subtitle) {
            <span class="apex-list-header-sub">{{ subtitle }}</span>
          }
        </div>
      }

      @if (!items?.length) {
        <div class="apex-list-empty">
          <span class="pi pi-list apex-list-empty-icon"></span>
          <span>{{ emptyMessage }}</span>
        </div>
      } @else {
        <ul class="apex-list-body" role="list">
          @for (item of items; track item.id) {
            <li
              class="apex-list-item"
              [class.apex-list-item--clickable]="selectable"
              (click)="selectable && itemSelect.emit(item)"
              role="listitem">

              <!-- Icon / Avatar column -->
              <div class="apex-list-item-media">
                @if (item.icon) {
                  <span
                    class="apex-list-item-icon"
                    [style.background]="item.iconColor || 'var(--primary-100)'">
                    <i [class]="item.icon"
                       [style.color]="item.iconColor ? '#fff' : 'var(--primary-600)'"></i>
                  </span>
                }
              </div>

              <!-- Content column -->
              <div class="apex-list-item-content">
                <div class="apex-list-item-top">
                  <span class="apex-list-item-title">{{ item.title }}</span>
                  @if (item.badge) {
                    <span class="apex-list-badge apex-list-badge--{{ item.badgeSeverity || 'info' }}">
                      {{ item.badge }}
                    </span>
                  }
                </div>
                @if (item.subtitle) {
                  <span class="apex-list-item-subtitle">{{ item.subtitle }}</span>
                }
                @if (item.description) {
                  <p class="apex-list-item-desc">{{ item.description }}</p>
                }
              </div>

              <!-- Actions column -->
              @if (item.actions?.length) {
                <div class="apex-list-item-actions" (click)="$event.stopPropagation()">
                  @for (action of item.actions!; track action.label) {
                    <button
                      type="button"
                      class="apex-list-action-btn"
                      [title]="action.label"
                      (click)="action.action(item)">
                      <i [class]="action.icon"></i>
                    </button>
                  }
                </div>
              }
            </li>
          }
        </ul>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .apex-list-region {
      background: var(--surface-card);
      border: 1px solid var(--surface-border);
      border-radius: var(--border-radius, 6px);
      overflow: hidden;
    }
    .apex-list-header {
      display: flex;
      align-items: baseline;
      gap: 0.75rem;
      padding: 0.6rem 1rem;
      background: var(--surface-section);
      border-bottom: 1px solid var(--surface-border);
    }
    .apex-list-header-title {
      margin: 0;
      font-size: 0.85rem;
      font-weight: 700;
      color: var(--text-color);
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .apex-list-header-sub {
      font-size: 0.8rem;
      color: var(--text-color-secondary);
    }
    .apex-list-empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.5rem;
      padding: 2.5rem 1rem;
      color: var(--text-color-secondary);
      font-size: 0.875rem;
    }
    .apex-list-empty-icon { font-size: 1.75rem; color: var(--surface-300); }
    .apex-list-body {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .apex-list-item {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.75rem 1rem;
      border-bottom: 1px solid var(--surface-100, #f3f4f6);
      transition: background 0.15s;
    }
    .apex-list-item:last-child { border-bottom: none; }
    .apex-list-item--clickable { cursor: pointer; }
    .apex-list-item--clickable:hover { background: var(--surface-hover, #f9fafb); }
    .apex-list-item-media { flex-shrink: 0; }
    .apex-list-item-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 2.25rem;
      height: 2.25rem;
      border-radius: 8px;
    }
    .apex-list-item-icon i { font-size: 1rem; }
    .apex-list-item-content { flex: 1; min-width: 0; }
    .apex-list-item-top {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      flex-wrap: wrap;
    }
    .apex-list-item-title {
      font-size: 0.875rem;
      font-weight: 600;
      color: var(--text-color);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .apex-list-item-subtitle {
      display: block;
      font-size: 0.78rem;
      color: var(--text-color-secondary);
      margin-top: 0.1rem;
    }
    .apex-list-item-desc {
      margin: 0.25rem 0 0;
      font-size: 0.8rem;
      color: var(--text-color-secondary);
      line-height: 1.4;
    }
    .apex-list-badge {
      display: inline-block;
      padding: 0.1rem 0.55rem;
      border-radius: 20px;
      font-size: 0.68rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.03em;
      white-space: nowrap;
    }
    .apex-list-badge--success   { background: #dcfce7; color: #15803d; }
    .apex-list-badge--info      { background: #dbeafe; color: #1d4ed8; }
    .apex-list-badge--warning   { background: #fef3c7; color: #b45309; }
    .apex-list-badge--danger    { background: #fee2e2; color: #b91c1c; }
    .apex-list-badge--secondary { background: var(--surface-200); color: var(--text-color-secondary); }
    .apex-list-item-actions {
      display: flex;
      gap: 0.25rem;
      flex-shrink: 0;
    }
    .apex-list-action-btn {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 1.75rem;
      height: 1.75rem;
      border: none;
      background: transparent;
      border-radius: 4px;
      color: var(--text-color-secondary);
      cursor: pointer;
      transition: background 0.15s, color 0.15s;
      font-size: 0.8rem;
    }
    .apex-list-action-btn:hover {
      background: var(--surface-200);
      color: var(--text-color);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexListComponent {
  @Input() items: ApexListItem[] = [];
  @Input() title?: string;
  @Input() subtitle?: string;
  @Input() selectable: boolean = true;
  @Input() emptyMessage: string = 'No records found.';

  @Output() itemSelect = new EventEmitter<ApexListItem>();
}
