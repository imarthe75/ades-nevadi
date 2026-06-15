import {
  Component,
  Input,
  Output,
  EventEmitter,
  ChangeDetectionStrategy,
  OnInit,
  OnDestroy,
  DestroyRef,
  inject,
  ChangeDetectorRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router, NavigationEnd } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs/operators';

export interface NavMenuItem {
  label: string;
  icon?: string;
  route?: string;
  url?: string;
  target?: string;
  expanded?: boolean;
  active?: boolean;
  badge?: number;
  items?: NavMenuItem[];
}

@Component({
  selector: 'apex-navigation',
  template: `
<nav class="apex-nav"
     [class.apex-nav--collapsed]="collapsed"
     [class.apex-nav--horizontal]="mode === 'horizontal'"
     [style.width]="collapsed ? collapsedWidth : expandedWidth">

  <ul class="apex-nav-list" role="menu">
    @for (item of items; track item.label) {
      <li class="apex-nav-item"
          [class.has-submenu]="hasSubmenu(item)"
          [class.expanded]="item.expanded"
          [class.active]="item.active"
          role="none">

        <!-- Router link -->
        @if (item.route) {
          <a [routerLink]="item.route"
             routerLinkActive="active"
             class="apex-nav-link"
             role="menuitem"
             (click)="toggleSubmenu($event, item)">
            @if (item.icon) {
              <span [class]="item.icon + ' apex-nav-icon'"></span>
            }
            @if (!collapsed) {
              <span class="apex-nav-label">{{ item.label }}</span>
            }
            @if (!collapsed && resolveBadge(item) > 0) {
              <span class="apex-nav-badge">{{ resolveBadge(item) > 99 ? '99+' : resolveBadge(item) }}</span>
            }
            @if (collapsed && resolveBadge(item) > 0) {
              <span class="apex-nav-badge apex-nav-badge--dot"></span>
            }
          </a>
        }

        <!-- External URL -->
        @if (item.url && !item.route) {
          <a [href]="item.url"
             [target]="item.target || '_self'"
             class="apex-nav-link"
             role="menuitem"
             (click)="toggleSubmenu($event, item)">
            @if (item.icon) {
              <span [class]="item.icon + ' apex-nav-icon'"></span>
            }
            @if (!collapsed) {
              <span class="apex-nav-label">{{ item.label }}</span>
            }
            @if (!collapsed && resolveBadge(item) > 0) {
              <span class="apex-nav-badge">{{ resolveBadge(item) > 99 ? '99+' : resolveBadge(item) }}</span>
            }
            @if (collapsed && resolveBadge(item) > 0) {
              <span class="apex-nav-badge apex-nav-badge--dot"></span>
            }
          </a>
        }

        <!-- Toggle-only (group header) -->
        @if (!item.route && !item.url) {
          <a href="#"
             class="apex-nav-link"
             role="menuitem"
             [attr.aria-haspopup]="true"
             [attr.aria-expanded]="item.expanded"
             (click)="toggleSubmenu($event, item)">
            @if (item.icon) {
              <span [class]="item.icon + ' apex-nav-icon'"></span>
            }
            @if (!collapsed) {
              <span class="apex-nav-label">{{ item.label }}</span>
              @if (hasSubmenu(item)) {
                <span class="apex-nav-toggle pi"
                      [class.pi-angle-down]="item.expanded"
                      [class.pi-angle-right]="!item.expanded">
                </span>
              }
            }
          </a>
        }

        <!-- Submenu (depth 1) -->
        @if (hasSubmenu(item)) {
          <ul class="apex-nav-submenu"
              [style.display]="(item.expanded || mode === 'horizontal') && !collapsed ? 'block' : 'none'"
              role="menu">
            @for (sub of item.items!; track sub.label) {
              <li class="apex-nav-submenu-item"
                  [class.active]="sub.active"
                  role="none">
                @if (sub.route) {
                  <a [routerLink]="sub.route"
                     routerLinkActive="active"
                     class="apex-nav-link"
                     role="menuitem"
                     (click)="toggleSubmenu($event, sub)">
                    @if (sub.icon) {
                      <span [class]="sub.icon + ' apex-nav-icon'"></span>
                    }
                    <span class="apex-nav-label">{{ sub.label }}</span>
                    @if (resolveBadge(sub) > 0) {
                      <span class="apex-nav-badge">{{ resolveBadge(sub) > 99 ? '99+' : resolveBadge(sub) }}</span>
                    }
                  </a>
                }
                @if (sub.url && !sub.route) {
                  <a [href]="sub.url"
                     [target]="sub.target || '_self'"
                     class="apex-nav-link"
                     role="menuitem"
                     (click)="toggleSubmenu($event, sub)">
                    @if (sub.icon) {
                      <span [class]="sub.icon + ' apex-nav-icon'"></span>
                    }
                    <span class="apex-nav-label">{{ sub.label }}</span>
                    @if (resolveBadge(sub) > 0) {
                      <span class="apex-nav-badge">{{ resolveBadge(sub) > 99 ? '99+' : resolveBadge(sub) }}</span>
                    }
                  </a>
                }
              </li>
            }
          </ul>
        }

      </li>
    }
  </ul>
</nav>
  `,
  styles: [`
.apex-nav {
  background-color: #ffffff;
  transition: width 0.2s ease;
  overflow: hidden;

  ul {
    list-style: none;
    margin: 0;
    padding: 0;
  }

  .apex-nav-link {
    display: flex;
    align-items: center;
    padding: 0.75rem 1rem;
    color: #4b5563;
    text-decoration: none;
    transition: background-color 0.2s, color 0.2s;
    border-radius: 0.375rem;
    margin: 0.125rem 0.5rem;
    position: relative;

    &:hover {
      background-color: #f3f4f6;
      color: #111827;
    }

    &.active {
      background-color: #eff6ff;
      color: #0066cc;
      font-weight: 500;

      .apex-nav-icon {
        color: #0066cc;
      }
    }
  }

  .apex-nav-icon {
    font-size: 1.125rem;
    width: 1.5rem;
    text-align: center;
    margin-right: 0.75rem;
    color: #6b7280;
    flex-shrink: 0;
  }

  .apex-nav-label {
    flex: 1;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .apex-nav-toggle-icon {
    font-size: 0.875rem;
    transition: transform 0.2s;
  }

  /* Badge */
  .apex-nav-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: #ef4444;
    color: #fff;
    border-radius: 10px;
    font-size: 0.65rem;
    font-weight: 700;
    min-width: 18px;
    height: 18px;
    padding: 0 4px;
    line-height: 1;
    margin-left: auto;
    flex-shrink: 0;

    &--dot {
      width: 8px;
      min-width: 8px;
      height: 8px;
      border-radius: 50%;
      padding: 0;
      position: absolute;
      top: 6px;
      right: 6px;
    }
  }

  /* Vertical Mode */
  &:not(.apex-nav--horizontal) {
    .apex-nav-list {
      display: flex;
      flex-direction: column;
    }

    .apex-nav-submenu {
      padding-left: 2.25rem;
      margin-bottom: 0.25rem;

      .apex-nav-link {
        padding: 0.5rem 1rem;
        font-size: 0.875rem;
      }
    }

    /* Collapsed state */
    &.apex-nav--collapsed {
      .apex-nav-link {
        justify-content: center;
        padding: 0.75rem;
        margin: 0.125rem;
      }

      .apex-nav-icon {
        margin-right: 0;
        font-size: 1.25rem;
      }

      .apex-nav-submenu {
        display: none !important;
      }
    }
  }

  /* Horizontal Mode */
  &.apex-nav--horizontal {
    border-bottom: 1px solid #e5e7eb;

    .apex-nav-list {
      display: flex;
      flex-direction: row;
      flex-wrap: wrap;
    }

    .apex-nav-item {
      position: relative;

      &:hover .apex-nav-submenu {
        display: block !important;
      }
    }

    .apex-nav-submenu {
      position: absolute;
      top: 100%;
      left: 0;
      min-width: 12rem;
      background-color: #ffffff;
      box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
      border: 1px solid #e5e7eb;
      border-radius: 0.375rem;
      padding: 0.5rem 0;
      z-index: 50;

      .apex-nav-link {
        margin: 0;
        border-radius: 0;
      }
    }
  }
}
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule, RouterModule],
})
export class ApexNavigationComponent implements OnInit, OnDestroy {
  @Input() items: NavMenuItem[] = [];
  @Input() collapsed: boolean = false;
  @Input() mode: 'vertical' | 'horizontal' = 'vertical';

  /** Map of route path → badge count. Shown as a red number on the nav item. */
  @Input() badge?: Record<string, number>;

  /** CSS width when the sidebar is collapsed. */
  @Input() collapsedWidth = '60px';

  /** CSS width when the sidebar is expanded. */
  @Input() expandedWidth = '240px';

  @Output() onItemClick = new EventEmitter<{ originalEvent: Event; item: NavMenuItem }>();

  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr = inject(ChangeDetectorRef);
  private router?: Router;

  ngOnInit(): void {
    try {
      this.router = inject(Router);
    } catch {
      return;
    }

    // Mark active state immediately for the current URL
    this._updateActiveState(this.router.url);

    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((e: NavigationEnd) => {
        this._updateActiveState(e.urlAfterRedirects);
        this.cdr.markForCheck();
      });
  }

  ngOnDestroy(): void {
    // Cleanup handled by DestroyRef / takeUntilDestroyed
  }

  toggleSubmenu(event: Event, item: NavMenuItem): void {
    if (item.items && item.items.length > 0) {
      event.preventDefault();
      item.expanded = !item.expanded;
    }

    this.onItemClick.emit({ originalEvent: event, item });
  }

  hasSubmenu(item: NavMenuItem): boolean {
    return !!item.items && item.items.length > 0;
  }

  /** Returns the badge count for an item (from item.badge or the badge Input map). */
  resolveBadge(item: NavMenuItem): number {
    if (item.badge != null) return item.badge;
    if (this.badge && item.route) {
      return this.badge[item.route] ?? 0;
    }
    return 0;
  }

  private _updateActiveState(currentUrl: string): void {
    const urlPath = currentUrl.split('?')[0];

    for (const item of this.items) {
      const childActive = this.hasSubmenu(item)
        ? item.items!.some(sub => sub.route != null && urlPath.startsWith(sub.route))
        : false;

      if (this.hasSubmenu(item)) {
        for (const sub of item.items!) {
          sub.active = sub.route != null && urlPath.startsWith(sub.route);
        }
        // Expand parent when a child is active
        if (childActive) item.expanded = true;
      }

      item.active =
        (item.route != null && urlPath.startsWith(item.route)) || childActive;
    }
  }
}
