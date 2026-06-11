import { Component, ChangeDetectionStrategy, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';

/**
 * Global wrapper for PrimeNG Toast.
 * Should be placed exactly once in the root app.component.ts
 */
@Component({
  selector: 'apex-toast-container',
  standalone: true,
  imports: [CommonModule, ToastModule],
  template: `
    <p-toast 
      [position]="position" 
      [key]="key" 
      styleClass="apex-p-toast">
    </p-toast>
  `,
  styles: [`
    :host {
      display: block;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexToastContainerComponent {
  /** Position of the toast: topleft, topcenter, topright, bottomleft, bottomcenter, bottomright */
  @Input() position: 'top-left' | 'top-center' | 'top-right' | 'bottom-left' | 'bottom-center' | 'bottom-right' | 'center' = 'top-right';

  /** Key to match specific messages to this container */
  @Input() key?: string;
}
