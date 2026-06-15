import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';

export interface ApexIconListItem {
  id?: string | number;
  icon: string;
  label: string;
  value?: string | number;
  color?: string;
  disabled?: boolean;
}

@Component({
  selector: 'apex-iconlist',
  standalone: true,
  imports: [],
  template: `
    <div class="apex-iconlist-region">
      @if (title) {
        <div class="apex-iconlist-header">
          <h4 class="apex-iconlist-header-title">{{ title }}</h4>
        </div>
      }

      @if (!items?.length) {
        <div class="apex-iconlist-empty">
          <span class="pi pi-th-large apex-iconlist-empty-icon"></span>
          <span>{{ emptyMessage }}</span>
        </div>
      } @else {
        <div class="apex-iconlist-grid">
          @for (item of items; track item.id ?? item.label) {
            <div
              class="apex-iconlist-cell"
              [class.apex-iconlist-cell--disabled]="item.disabled"
              (click)="!item.disabled && itemSelect.emit(item)"
              [attr.aria-disabled]="item.disabled || null"
              role="button"
              tabindex="{{ item.disabled ? -1 : 0 }}"
              (keydown.enter)="!item.disabled && itemSelect.emit(item)">

              <div
                class="apex-iconlist-icon-bg"
                [style.background]="item.color ? item.color + '22' : 'var(--primary-50)'">
                <i [class]="item.icon"
                   [style.color]="item.color || 'var(--primary-color)'"></i>
              </div>

              <div class="apex-iconlist-cell-content">
                <span class="apex-iconlist-cell-label">{{ item.label }}</span>
                @if (item.value !== undefined && item.value !== null) {
                  <span class="apex-iconlist-cell-value">{{ item.value }}</span>
                }
              </div>

            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .apex-iconlist-region {
      background: var(--surface-card);
      border: 1px solid var(--surface-border);
      border-radius: var(--border-radius, 6px);
      overflow: hidden;
    }
    .apex-iconlist-header {
      padding: 0.6rem 1rem;
      background: var(--surface-section);
      border-bottom: 1px solid var(--surface-border);
    }
    .apex-iconlist-header-title {
      margin: 0;
      font-size: 0.85rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--text-color);
    }
    .apex-iconlist-empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.5rem;
      padding: 2.5rem;
      color: var(--text-color-secondary);
      font-size: 0.875rem;
    }
    .apex-iconlist-empty-icon { font-size: 2rem; color: var(--surface-300); }
    .apex-iconlist-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
      gap: 0;
    }
    .apex-iconlist-cell {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.5rem;
      padding: 1.25rem 0.75rem;
      text-align: center;
      cursor: pointer;
      border-right: 1px solid var(--surface-100, #f3f4f6);
      border-bottom: 1px solid var(--surface-100, #f3f4f6);
      transition: background 0.15s;
      outline: none;
    }
    .apex-iconlist-cell:focus-visible {
      box-shadow: inset 0 0 0 2px var(--primary-color);
    }
    .apex-iconlist-cell:hover:not(.apex-iconlist-cell--disabled) {
      background: var(--surface-hover, #f9fafb);
    }
    .apex-iconlist-cell--disabled {
      opacity: 0.45;
      cursor: not-allowed;
    }
    .apex-iconlist-icon-bg {
      width: 2.75rem;
      height: 2.75rem;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .apex-iconlist-icon-bg i { font-size: 1.2rem; }
    .apex-iconlist-cell-content {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.15rem;
    }
    .apex-iconlist-cell-label {
      font-size: 0.78rem;
      font-weight: 600;
      color: var(--text-color);
      line-height: 1.3;
    }
    .apex-iconlist-cell-value {
      font-size: 0.85rem;
      font-weight: 700;
      color: var(--primary-700, #1d4ed8);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexIconListComponent {
  @Input() items: ApexIconListItem[] = [];
  @Input() title?: string;
  @Input() emptyMessage: string = 'No items.';

  @Output() itemSelect = new EventEmitter<ApexIconListItem>();
}
