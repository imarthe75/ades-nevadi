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
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router, ActivatedRoute, NavigationEnd } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs/operators';

export interface BreadcrumbItem {
  label: string;
  routerLink?: string | any[];
  icon?: string;
  activeIcon?: string;
  action?: () => void;
}

@Component({
  selector: 'apex-breadcrumb',
  template: `
<nav class="apex-breadcrumb" [ngClass]="styleClass || ''" aria-label="Breadcrumb">
  <ol class="breadcrumb-list">
    <!-- Home item -->
    @if (home) {
      <li class="breadcrumb-item">
        @if (home.routerLink) {
          <a [routerLink]="home.routerLink"
             (click)="onClick(home, -1, $event)"
             class="breadcrumb-link">
            @if (home.icon) { <span [class]="home.icon"></span> }
            {{ home.label }}
          </a>
        } @else {
          <a href="#"
             (click)="onClick(home, -1, $event)"
             class="breadcrumb-link">
            @if (home.icon) { <span [class]="home.icon"></span> }
            {{ home.label }}
          </a>
        }
      </li>
    }

    <!-- Regular items -->
    @for (item of displayItems(); track item.label; let i = $index; let last = $last) {
      <li class="breadcrumb-item"
          [attr.aria-current]="last ? 'page' : null">

        @if (i > 0 || home) {
          <span [class]="separatorIcon" class="breadcrumb-separator"></span>
        }

        @if (item.routerLink && !last) {
          <a [routerLink]="item.routerLink"
             (click)="onClick(item, i, $event)"
             class="breadcrumb-link">
            @if (item.icon) { <span [class]="item.icon"></span> }
            {{ item.label }}
          </a>
        } @else if (!item.routerLink && !last) {
          <a href="#"
             (click)="onClick(item, i, $event)"
             class="breadcrumb-link">
            @if (item.icon) { <span [class]="item.icon"></span> }
            {{ item.label }}
          </a>
        } @else {
          <span class="breadcrumb-text">
            @if (item.activeIcon) { <span [class]="item.activeIcon"></span> }
            {{ item.label }}
          </span>
        }

      </li>
    }
  </ol>
</nav>
  `,
  styles: [`
.apex-breadcrumb {
  margin-bottom: 1rem;

  .breadcrumb-list {
    display: flex;
    list-style: none;
    padding: 0;
    margin: 0;
    gap: 0.25rem;
    align-items: center;
    flex-wrap: wrap;
  }

  .breadcrumb-item {
    display: flex;
    align-items: center;
    gap: 0.5rem;

    &:last-child {
      font-weight: 600;
      color: #333;
    }
  }

  .breadcrumb-link {
    color: #0066cc;
    text-decoration: none;
    display: flex;
    align-items: center;
    gap: 0.25rem;
    padding: 0.25rem 0.5rem;
    border-radius: 0.25rem;
    transition: background-color 0.2s;

    &:hover {
      background-color: rgba(0, 102, 204, 0.1);
      text-decoration: underline;
    }

    &:active {
      background-color: rgba(0, 102, 204, 0.2);
    }
  }

  .breadcrumb-text {
    display: flex;
    align-items: center;
    gap: 0.25rem;
    color: #333;
  }

  .breadcrumb-separator {
    color: #999;
    font-size: 0.8rem;
  }
}
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule, RouterModule],
})
export class ApexBreadcrumbComponent implements OnInit, OnDestroy {
  @Input() items: BreadcrumbItem[] = [];
  @Input() home?: BreadcrumbItem;
  @Input() styleClass?: string;
  @Input() separatorIcon: string = 'pi pi-chevron-right';

  /** Maps URL segment → display label, e.g. {'gradebook': 'Calificaciones'} */
  @Input() routeTitles: Record<string, string> = {};

  /**
   * When true, auto-generates breadcrumbs from the current URL using routeTitles.
   * Requires Router to be available in the injection context.
   */
  @Input() autoGenerate = false;

  @Output() itemClick = new EventEmitter<BreadcrumbItem>();

  private router?: Router;
  private activatedRoute?: ActivatedRoute;
  private destroyRef = inject(DestroyRef);

  private readonly _generatedItems = signal<BreadcrumbItem[]>([]);

  /** Returns either the auto-generated items (when autoGenerate=true) or the provided items. */
  displayItems(): BreadcrumbItem[] {
    return this.autoGenerate ? this._generatedItems() : this.items;
  }

  ngOnInit(): void {
    if (!this.autoGenerate) return;

    try {
      this.router = inject(Router);
      this.activatedRoute = inject(ActivatedRoute);
    } catch {
      // Router not available — fall back to static items
      return;
    }

    // Generate immediately for the current URL
    this._generateFromUrl(this.router.url);

    // Re-generate on every navigation
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((e: NavigationEnd) => {
        this._generateFromUrl(e.urlAfterRedirects);
      });
  }

  ngOnDestroy(): void {
    // Cleanup handled by DestroyRef / takeUntilDestroyed
  }

  onClick(item: BreadcrumbItem, index: number, event: Event): void {
    event.preventDefault();

    if (item.action) {
      item.action();
    }

    this.itemClick.emit(item);
  }

  isLast(index: number): boolean {
    return index === this.displayItems().length - 1;
  }

  private _generateFromUrl(url: string): void {
    const cleanUrl = url.split('?')[0].split('#')[0];
    const segments = cleanUrl.split('/').filter(s => s.length > 0);

    const generated: BreadcrumbItem[] = segments.map((segment, index) => {
      const path = '/' + segments.slice(0, index + 1).join('/');
      const isLast = index === segments.length - 1;
      return {
        label: this.routeTitles[segment] ?? this._humanize(segment),
        routerLink: isLast ? undefined : path,
      };
    });

    this._generatedItems.set(generated);
  }

  private _humanize(seg: string): string {
    return seg
      .split('-')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  }
}
