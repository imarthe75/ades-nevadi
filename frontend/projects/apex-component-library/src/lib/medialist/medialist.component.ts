import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';

export interface ApexMediaListItem {
  id?: string | number;
  title: string;
  subtitle?: string;
  description?: string;
  imageUrl?: string;
  avatar?: string;  // icon class, e.g. 'pi pi-user'
  avatarColor?: string;
  badge?: string;
  badgeSeverity?: 'success' | 'info' | 'warning' | 'danger' | 'secondary';
}

@Component({
  selector: 'apex-medialist',
  standalone: true,
  imports: [],
  template: `
    <div class="apex-medialist-region">
      @if (title) {
        <div class="apex-medialist-header">
          <h4 class="apex-medialist-header-title">{{ title }}</h4>
        </div>
      }

      @if (!items?.length) {
        <div class="apex-medialist-empty">
          <span class="pi pi-images apex-medialist-empty-icon"></span>
          <span>{{ emptyMessage }}</span>
        </div>
      } @else {
        <ul class="apex-medialist-list" role="list">
          @for (item of items; track item.id ?? item.title) {
            <li class="apex-medialist-item" role="listitem" (click)="itemSelect.emit(item)">

              <!-- Media: image or avatar -->
              <div class="apex-medialist-media">
                @if (item.imageUrl) {
                  <img
                    [src]="item.imageUrl"
                    [alt]="item.title"
                    class="apex-medialist-image" />
                } @else {
                  <div
                    class="apex-medialist-avatar"
                    [style.background]="item.avatarColor || 'var(--primary-100)'">
                    @if (item.avatar) {
                      <i [class]="item.avatar"
                         [style.color]="item.avatarColor ? '#fff' : 'var(--primary-600)'"></i>
                    } @else {
                      <i class="pi pi-user"
                         style="color: var(--primary-600)"></i>
                    }
                  </div>
                }
              </div>

              <!-- Content -->
              <div class="apex-medialist-content">
                <div class="apex-medialist-top">
                  <span class="apex-medialist-title">{{ item.title }}</span>
                  @if (item.badge) {
                    <span class="apex-medialist-badge apex-ml-badge--{{ item.badgeSeverity || 'info' }}">
                      {{ item.badge }}
                    </span>
                  }
                </div>
                @if (item.subtitle) {
                  <span class="apex-medialist-subtitle">{{ item.subtitle }}</span>
                }
                @if (item.description) {
                  <p class="apex-medialist-desc">{{ item.description }}</p>
                }
              </div>

            </li>
          }
        </ul>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .apex-medialist-region {
      background: var(--surface-card);
      border: 1px solid var(--surface-border);
      border-radius: var(--border-radius, 6px);
      overflow: hidden;
    }
    .apex-medialist-header {
      padding: 0.6rem 1rem;
      background: var(--surface-section);
      border-bottom: 1px solid var(--surface-border);
    }
    .apex-medialist-header-title {
      margin: 0;
      font-size: 0.85rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--text-color);
    }
    .apex-medialist-empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.5rem;
      padding: 2.5rem;
      color: var(--text-color-secondary);
      font-size: 0.875rem;
    }
    .apex-medialist-empty-icon { font-size: 2rem; color: var(--surface-300); }
    .apex-medialist-list {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .apex-medialist-item {
      display: flex;
      align-items: center;
      gap: 1rem;
      padding: 0.85rem 1rem;
      border-bottom: 1px solid var(--surface-100, #f3f4f6);
      cursor: pointer;
      transition: background 0.15s;
    }
    .apex-medialist-item:last-child { border-bottom: none; }
    .apex-medialist-item:hover { background: var(--surface-hover, #f9fafb); }
    .apex-medialist-media { flex-shrink: 0; }
    .apex-medialist-image {
      width: 48px;
      height: 48px;
      object-fit: cover;
      border-radius: 8px;
      border: 1px solid var(--surface-border);
    }
    .apex-medialist-avatar {
      width: 48px;
      height: 48px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.25rem;
    }
    .apex-medialist-content { flex: 1; min-width: 0; }
    .apex-medialist-top {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      flex-wrap: wrap;
    }
    .apex-medialist-title {
      font-size: 0.875rem;
      font-weight: 600;
      color: var(--text-color);
    }
    .apex-medialist-subtitle {
      display: block;
      font-size: 0.78rem;
      color: var(--text-color-secondary);
      margin-top: 0.1rem;
    }
    .apex-medialist-desc {
      margin: 0.2rem 0 0;
      font-size: 0.8rem;
      color: var(--text-color-secondary);
      line-height: 1.4;
    }
    .apex-medialist-badge {
      display: inline-block;
      padding: 0.1rem 0.5rem;
      border-radius: 20px;
      font-size: 0.68rem;
      font-weight: 700;
      text-transform: uppercase;
      white-space: nowrap;
    }
    .apex-ml-badge--success   { background: #dcfce7; color: #15803d; }
    .apex-ml-badge--info      { background: #dbeafe; color: #1d4ed8; }
    .apex-ml-badge--warning   { background: #fef3c7; color: #b45309; }
    .apex-ml-badge--danger    { background: #fee2e2; color: #b91c1c; }
    .apex-ml-badge--secondary { background: var(--surface-200); color: var(--text-color-secondary); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexMediaListComponent {
  @Input() items: ApexMediaListItem[] = [];
  @Input() title?: string;
  @Input() emptyMessage: string = 'No records found.';

  @Output() itemSelect = new EventEmitter<ApexMediaListItem>();
}
