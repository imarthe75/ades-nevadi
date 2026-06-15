import {
  Component, Input, Output, EventEmitter, ChangeDetectionStrategy,
  forwardRef, OnInit, OnDestroy
} from '@angular/core';
import { FormsModule, NG_VALUE_ACCESSOR, ControlValueAccessor } from '@angular/forms';
import { AutoCompleteModule, AutoCompleteCompleteEvent } from 'primeng/autocomplete';

export interface ApexLOVItem {
  label: string;
  value: any;
  description?: string;
}

@Component({
  selector: 'apex-popuplov',
  standalone: true,
  imports: [FormsModule, AutoCompleteModule],
  template: `
    <div class="apex-lov-wrapper">
      @if (placeholder && !label) { }
      @if (label) {
        <label class="apex-lov-label">{{ label }}</label>
      }
      <p-autoComplete
        [(ngModel)]="internalValue"
        [suggestions]="filteredItems"
        (completeMethod)="onComplete($event)"
        (onSelect)="handleSelect($event)"
        (onClear)="handleClear()"
        optionLabel="label"
        [dropdown]="true"
        [multiple]="multiple"
        [placeholder]="placeholder"
        [disabled]="disabled"
        [showClear]="true"
        styleClass="apex-p-lov"
        [style]="{ width: '100%' }">

        <ng-template let-item pTemplate="item">
          <div class="apex-lov-item">
            <span class="apex-lov-item-label">{{ item.label }}</span>
            @if (item.description) {
              <small class="apex-lov-item-desc">{{ item.description }}</small>
            }
          </div>
        </ng-template>

      </p-autoComplete>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .apex-lov-wrapper {
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
      width: 100%;
    }
    .apex-lov-label {
      font-size: 0.875rem;
      font-weight: 500;
      color: var(--text-color);
    }
    :host ::ng-deep .apex-p-lov {
      width: 100%;
    }
    :host ::ng-deep .apex-p-lov .p-autocomplete-input {
      width: 100%;
      border-radius: var(--border-radius, 6px);
    }
    :host ::ng-deep .apex-p-lov .p-autocomplete-dropdown {
      border-radius: 0 var(--border-radius, 6px) var(--border-radius, 6px) 0;
    }
    .apex-lov-item {
      display: flex;
      flex-direction: column;
      padding: 0.15rem 0;
    }
    .apex-lov-item-label {
      font-size: 0.875rem;
      font-weight: 500;
      color: var(--text-color);
    }
    .apex-lov-item-desc {
      font-size: 0.75rem;
      color: var(--text-color-secondary);
      margin-top: 0.1rem;
    }
    :host ::ng-deep .p-autocomplete-panel .p-autocomplete-item:hover {
      background: var(--primary-50, #eff6ff);
    }
  `],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => ApexPopupLOVComponent),
    multi: true
  }],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexPopupLOVComponent implements ControlValueAccessor {
  @Input() items: ApexLOVItem[] = [];
  @Input() value: any = null;
  @Input() placeholder: string = 'Type to search...';
  @Input() multiple: boolean = false;
  @Input() loading: boolean = false;
  @Input() disabled: boolean = false;
  @Input() label?: string;

  @Output() valueChange = new EventEmitter<any>();
  @Output() search = new EventEmitter<string>();

  internalValue: any = null;
  filteredItems: ApexLOVItem[] = [];

  onComplete(event: AutoCompleteCompleteEvent): void {
    const query = event.query?.toLowerCase() ?? '';
    this.filteredItems = this.items.filter(i =>
      i.label.toLowerCase().includes(query) ||
      (i.description?.toLowerCase().includes(query) ?? false)
    );
    this.search.emit(event.query);
  }

  handleSelect(event: any): void {
    const selected = event.value ?? event;
    this.internalValue = selected;
    const emitVal = this.multiple
      ? (Array.isArray(selected) ? selected.map(i => i.value) : [selected.value])
      : selected?.value;
    this.valueChange.emit(emitVal);
    this._onChange(emitVal);
    this._onTouched();
  }

  handleClear(): void {
    this.internalValue = null;
    this.valueChange.emit(null);
    this._onChange(null);
    this._onTouched();
  }

  private _onChange: any = () => {};
  private _onTouched: any = () => {};

  writeValue(val: any): void {
    if (val && this.items?.length) {
      this.internalValue = this.multiple
        ? this.items.filter(i => (Array.isArray(val) ? val : [val]).includes(i.value))
        : this.items.find(i => i.value === val) ?? null;
    } else {
      this.internalValue = null;
    }
  }
  registerOnChange(fn: any): void { this._onChange = fn; }
  registerOnTouched(fn: any): void { this._onTouched = fn; }
  setDisabledState(isDisabled: boolean): void { this.disabled = isDisabled; }
}
