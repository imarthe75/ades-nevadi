import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'success' | 'text';
export type ButtonSize = 'small' | 'medium' | 'large';

@Component({
  selector: 'apex-button',
  templateUrl: './button.component.html',
  styleUrls: ['./button.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule]
})
export class ApexButtonComponent {
  @Input() label: string = '';
  @Input() icon?: string;
  @Input() iconPos: 'left' | 'right' = 'left';
  @Input() variant: ButtonVariant = 'primary';
  @Input() size: ButtonSize = 'medium';
  @Input() disabled: boolean = false;
  @Input() fullWidth: boolean = false;
  @Input() type: 'button' | 'submit' | 'reset' = 'button';
  @Input() styleClass?: string;

  @Output() onClick = new EventEmitter<Event>();

  get buttonClasses(): string {
    const classes = [
      'apex-btn',
      `apex-btn-${this.variant}`,
      `apex-btn-${this.size}`
    ];
    
    if (this.fullWidth) classes.push('apex-btn-full');
    if (this.styleClass) classes.push(this.styleClass);
    if (this.icon && !this.label) classes.push('apex-btn-icon-only');
    
    return classes.join(' ');
  }

  handleClick(event: Event): void {
    if (!this.disabled) {
      this.onClick.emit(event);
    }
  }
}
