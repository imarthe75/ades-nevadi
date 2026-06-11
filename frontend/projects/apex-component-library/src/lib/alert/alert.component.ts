import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

export type AlertSeverity = 'success' | 'info' | 'warning' | 'error';

@Component({
  selector: 'apex-alert',
  templateUrl: './alert.component.html',
  styleUrls: ['./alert.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule]
})
export class ApexAlertComponent {
  @Input() severity: AlertSeverity = 'info';
  @Input() title?: string;
  @Input() message: string = '';
  @Input() closable: boolean = true;
  @Input() icon?: string;
  @Input() styleClass?: string;
  
  @Output() closed = new EventEmitter<void>();
  
  visible = true;
  
  onClose(): void {
    this.visible = false;
    this.closed.emit();
  }
  
  get severityClass(): string {
    return `alert-${this.severity}`;
  }
}
