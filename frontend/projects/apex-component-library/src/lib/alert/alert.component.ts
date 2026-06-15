import {
  Component, Input, Output, EventEmitter, ChangeDetectionStrategy, signal,
  OnInit, OnDestroy
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';

export type AlertSeverity = 'success' | 'info' | 'warning' | 'error';

const ICON_MAP: Record<AlertSeverity, string> = {
  success: 'pi pi-check-circle',
  info:    'pi pi-info-circle',
  warning: 'pi pi-exclamation-triangle',
  error:   'pi pi-times-circle',
};

@Component({
  selector: 'apex-alert',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ButtonModule],
  template: `
    @if (visible()) {
      <div class="apex-alert apex-alert-{{ severity }}" role="alert">
        <i [class]="resolvedIcon" class="apex-alert-icon"></i>
        <div class="apex-alert-body">
          @if (title) { <strong class="apex-alert-title">{{ title }}</strong> }
          @if (message) { <span class="apex-alert-msg">{{ message }}</span> }
          <ng-content />
        </div>
        @if (closable) {
          <p-button icon="pi pi-times" [text]="true" severity="secondary" size="small"
            (onClick)="close()" styleClass="apex-alert-close" />
        }
        @if (autoCloseDuration && showProgress) {
          <div class="apex-alert-progress-bar" [style.animation-duration]="autoCloseDuration + 'ms'"></div>
        }
      </div>
    }
  `,
  styles: [`
    .apex-alert {
      display: flex; align-items: flex-start; gap: .75rem;
      padding: .8rem 1rem; border-radius: 6px; border-left: 4px solid;
      font-size: .875rem; margin-bottom: .75rem;
      position: relative; overflow: hidden;
    }
    .apex-alert-success { background: var(--green-50); border-color: var(--green-500); color: var(--green-800); }
    .apex-alert-info    { background: var(--blue-50);  border-color: var(--blue-500);  color: var(--blue-800);  }
    .apex-alert-warning { background: var(--yellow-50);border-color: var(--yellow-500);color: var(--yellow-800);}
    .apex-alert-error   { background: var(--red-50);   border-color: var(--red-500);   color: var(--red-800);   }
    .apex-alert-icon { font-size: 1.1rem; margin-top: .1rem; flex-shrink: 0; }
    .apex-alert-body { flex: 1; display: flex; flex-direction: column; gap: .15rem; }
    .apex-alert-title { font-weight: 600; font-size: .9rem; }
    .apex-alert-msg { line-height: 1.4; }
    :host ::ng-deep .apex-alert-close { opacity: .7; }
    :host ::ng-deep .apex-alert-close:hover { opacity: 1; }

    .apex-alert-progress-bar {
      position: absolute;
      bottom: 0;
      left: 0;
      height: 3px;
      width: 100%;
      background: currentColor;
      opacity: 0.35;
      animation: progressShrink linear forwards;
      transform-origin: left center;
    }

    @keyframes progressShrink {
      from { width: 100%; }
      to   { width: 0; }
    }
  `],
})
export class ApexAlertComponent implements OnInit, OnDestroy {
  @Input() severity: AlertSeverity = 'info';
  @Input() title?: string;
  @Input() message = '';
  @Input() closable = true;
  @Input() icon?: string;
  @Input() styleClass?: string;
  @Input() autoCloseDuration?: number;
  @Input() showProgress = false;

  @Output() closed = new EventEmitter<void>();

  visible = signal(true);

  private _timer?: ReturnType<typeof setTimeout>;

  get resolvedIcon(): string { return this.icon || ICON_MAP[this.severity]; }

  ngOnInit(): void {
    if (this.autoCloseDuration && this.autoCloseDuration > 0) {
      this._timer = setTimeout(() => this.close(), this.autoCloseDuration);
    }
  }

  ngOnDestroy(): void {
    if (this._timer !== undefined) {
      clearTimeout(this._timer);
    }
  }

  close(): void {
    this.visible.set(false);
    this.closed.emit();
  }

  show(): void {
    this.visible.set(true);
  }
}
