import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, forwardRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SliderModule } from 'primeng/slider';
import { FormsModule, NG_VALUE_ACCESSOR, ControlValueAccessor } from '@angular/forms';

@Component({
  selector: 'apex-slider',
  standalone: true,
  imports: [CommonModule, SliderModule, FormsModule],
  template: `
    <div class="apex-slider-container">
      <div class="apex-slider-header" *ngIf="label">
        <label [for]="inputId">{{label}}</label>
        <span class="apex-slider-value" *ngIf="showValue">
          {{range ? (value[0] + ' - ' + value[1]) : value}}
        </span>
      </div>
      <p-slider 
        [(ngModel)]="value" 
        [min]="min" 
        [max]="max" 
        [step]="step" 
        [range]="range"
        [orientation]="orientation"
        [disabled]="disabled"
        (onChange)="onSliderChange($event)"
        [styleClass]="'apex-p-slider'">
      </p-slider>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      margin-bottom: 1rem;
    }
    .apex-slider-container {
      width: 100%;
    }
    .apex-slider-header {
      display: flex;
      justify-content: space-between;
      margin-bottom: 0.5rem;
      font-size: 0.875rem;
      color: var(--text-color, #374151);
      font-weight: 500;
    }
    .apex-slider-value {
      color: var(--primary-color, #3b82f6);
      font-weight: 600;
    }
  `],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => ApexSliderComponent),
      multi: true
    }
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexSliderComponent implements ControlValueAccessor {
  /** Internal value */
  public value: any = 0;

  /** Label for the slider */
  @Input() label: string = '';

  /** Show current value text */
  @Input() showValue: boolean = true;

  /** Minimum value */
  @Input() min: number = 0;

  /** Maximum value */
  @Input() max: number = 100;

  /** Step size */
  @Input() step: number = 1;

  /** Whether the slider is a range slider */
  @Input() range: boolean = false;

  /** Orientation of the slider: horizontal or vertical */
  @Input() orientation: 'horizontal' | 'vertical' = 'horizontal';

  /** Disabled state */
  @Input() disabled: boolean = false;

  /** HTML id */
  @Input() inputId: string = `apex-slider-${Math.random().toString(36).substr(2, 9)}`;

  /** Event emitted on value change */
  @Output() valueChange = new EventEmitter<any>();

  // ControlValueAccessor functions
  public onChange: any = () => {};
  public onTouched: any = () => {};

  public writeValue(val: any): void {
    if (val !== undefined && val !== null) {
      this.value = val;
    } else {
      this.value = this.range ? [this.min, this.max] : this.min;
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

  public onSliderChange(event: any): void {
    this.valueChange.emit(event.value);
    this.onChange(event.value);
    this.onTouched();
  }
}
