import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TagModule } from 'primeng/tag';

@Component({
  selector: 'apex-badge',
  standalone: true,
  imports: [CommonModule, TagModule],
  template: `
    <p-tag 
      [value]="value" 
      [severity]="severity" 
      [icon]="icon" 
      [rounded]="rounded"
      [styleClass]="styleClass">
    </p-tag>
  `,
  styles: [`
    :host {
      display: inline-flex;
    }
    ::ng-deep .apex-badge-custom {
      font-weight: 600;
      letter-spacing: 0.5px;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexBadgeComponent {
  @Input() value: string = '';
  @Input() severity: 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' | undefined = undefined;
  @Input() icon: string | undefined = undefined;
  @Input() rounded: boolean = false;
  
  get styleClass(): string {
    return 'apex-badge-custom';
  }
}
