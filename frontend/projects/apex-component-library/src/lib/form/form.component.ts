import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, ContentChildren, QueryList, AfterContentInit } from '@angular/core';
import { CommonModule } from '@angular/common';

export type FormLayout = 'vertical' | 'horizontal' | 'inline';

@Component({
  selector: 'apex-form',
  templateUrl: './form.component.html',
  styleUrls: ['./form.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule]
})
export class ApexFormComponent {
  @Input() layout: FormLayout = 'vertical';
  @Input() title?: string;
  @Input() styleClass?: string;
  @Output() onSubmit = new EventEmitter<Event>();

  handleSubmit(event: Event): void {
    event.preventDefault();
    this.onSubmit.emit(event);
  }
}

@Component({
  selector: 'apex-form-item',
  template: `
    <div class="apex-form-item" [ngClass]="{'has-error': hasError}">
      <label *ngIf="label" [attr.for]="forId" class="apex-form-label">
        {{ label }}
        <span *ngIf="required" class="apex-required-asterisk">*</span>
      </label>
      <div class="apex-form-control-wrapper">
        <ng-content></ng-content>
        <div *ngIf="helpText && !hasError" class="apex-form-help-text">{{ helpText }}</div>
        <div *ngIf="hasError && errorText" class="apex-form-error-text">{{ errorText }}</div>
      </div>
    </div>
  `,
  standalone: true,
  imports: [CommonModule]
})
export class ApexFormItemComponent {
  @Input() label?: string;
  @Input() forId?: string;
  @Input() required: boolean = false;
  @Input() helpText?: string;
  @Input() errorText?: string;
  @Input() hasError: boolean = false;
}
