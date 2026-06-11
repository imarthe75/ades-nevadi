import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, forwardRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { InputNumberModule } from 'primeng/inputnumber';
import { FormsModule, NG_VALUE_ACCESSOR, ControlValueAccessor } from '@angular/forms';

@Component({
  selector: 'apex-spinner',
  standalone: true,
  imports: [CommonModule, InputNumberModule, FormsModule],
  template: `
    <div class="apex-spinner-container">
      <label *ngIf="label" [for]="inputId" class="apex-spinner-label" [ngClass]="{'is-required': required}">
        {{label}}
      </label>
      <p-inputNumber 
        [(ngModel)]="value" 
        [inputId]="inputId"
        [showButtons]="true"
        [min]="min" 
        [max]="max" 
        [step]="step"
        [disabled]="disabled"
        buttonLayout="stacked"
        spinnerMode="horizontal"
        incrementButtonIcon="pi pi-angle-up"
        decrementButtonIcon="pi pi-angle-down"
        [minFractionDigits]="minFractionDigits"
        [maxFractionDigits]="maxFractionDigits"
        (onInput)="onSpinnerInput($event)"
        styleClass="apex-p-spinner"
        [placeholder]="placeholder">
      </p-inputNumber>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      margin-bottom: 1rem;
    }
    .apex-spinner-container {
      display: flex;
      flex-direction: column;
      width: 100%;
    }
    .apex-spinner-label {
      margin-bottom: 0.5rem;
      font-weight: 500;
      color: var(--text-color, #374151);
      font-size: 0.875rem;
    }
    .apex-spinner-label.is-required::after {
      content: '*';
      color: var(--red-500, #ef4444);
      margin-left: 0.25rem;
    }
    ::ng-deep .apex-p-spinner {
      width: 100%;
    }
    ::ng-deep .apex-p-spinner .p-inputnumber-input {
      width: 100%;
    }
  `],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => ApexSpinnerComponent),
      multi: true
    }
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexSpinnerComponent implements ControlValueAccessor {
  /** Internal value */
  public value: number | null = null;

  /** Label for the input */
  @Input() label: string = '';

  /** Minimum value */
  @Input() min!: number;

  /** Maximum value */
  @Input() max!: number;

  /** Step size */
  @Input() step: number = 1;

  /** Minimum number of fraction digits */
  @Input() minFractionDigits: number = 0;

  /** Maximum number of fraction digits */
  @Input() maxFractionDigits: number = 0;

  /** Disabled state */
  @Input() disabled: boolean = false;

  /** Required field indicator */
  @Input() required: boolean = false;

  /** Placeholder text */
  @Input() placeholder: string = '';

  /** HTML id */
  @Input() inputId: string = `apex-spinner-${Math.random().toString(36).substr(2, 9)}`;

  /** Event emitted on value change */
  @Output() valueChange = new EventEmitter<number | null>();

  // ControlValueAccessor functions
  public onChange: any = () => {};
  public onTouched: any = () => {};

  public writeValue(val: any): void {
    if (val !== undefined) {
      this.value = val;
    }
  }

  public registerOnChange(fn: any): void {
    this.onChange = fn;
  }

  public registerOnTouched(fn: any): void {
    this.onTouched = fn;
  }

  public setDisabledState?(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  public onSpinnerInput(event: any): void {
    this.valueChange.emit(event.value);
    this.onChange(event.value);
    this.onTouched();
  }
}
