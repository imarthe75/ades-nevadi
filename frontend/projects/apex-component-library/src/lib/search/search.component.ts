import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, ViewChild, ElementRef, forwardRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';

@Component({
  selector: 'apex-search',
  templateUrl: './search.component.html',
  styleUrls: ['./search.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule, FormsModule],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => ApexSearchComponent),
      multi: true
    }
  ]
})
export class ApexSearchComponent implements ControlValueAccessor {
  @Input() placeholder: string = 'Search...';
  @Input() disabled: boolean = false;
  @Input() delay: number = 300;
  @Input() size: 'small' | 'medium' | 'large' = 'medium';
  @Input() styleClass?: string;
  @Input() showClearIcon: boolean = true;
  
  @Output() onSearch = new EventEmitter<string>();
  @Output() onClear = new EventEmitter<void>();
  
  @ViewChild('searchInput') searchInput!: ElementRef<HTMLInputElement>;

  value: string = '';
  isFocused: boolean = false;
  private debounceTimer: any;
  
  onChange = (val: string) => {};
  onTouched = () => {};

  writeValue(value: any): void {
    this.value = value || '';
  }

  registerOnChange(fn: any): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: any): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  handleInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.value = value;
    this.onChange(value);
    
    if (this.debounceTimer) {
      clearTimeout(this.debounceTimer);
    }
    
    this.debounceTimer = setTimeout(() => {
      this.onSearch.emit(this.value);
    }, this.delay);
  }

  handleFocus(): void {
    this.isFocused = true;
    this.onTouched();
  }

  handleBlur(): void {
    this.isFocused = false;
  }

  clearSearch(): void {
    if (this.disabled) return;
    
    this.value = '';
    this.onChange(this.value);
    this.onSearch.emit(this.value);
    this.onClear.emit();
    
    // Maintain focus on input after clearing
    setTimeout(() => {
      if (this.searchInput) {
        this.searchInput.nativeElement.focus();
      }
    });
  }
}
