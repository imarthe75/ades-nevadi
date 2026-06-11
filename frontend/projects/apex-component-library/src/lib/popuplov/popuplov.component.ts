import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, TemplateRef, ContentChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'apex-popuplov',
  standalone: true,
  imports: [CommonModule, AutoCompleteModule, FormsModule],
  template: `
    <div class="apex-popuplov-wrapper">
      <label *ngIf="label" class="apex-item-label">{{label}}</label>
      <p-autoComplete 
        [(ngModel)]="value" 
        [suggestions]="suggestions" 
        (completeMethod)="onSearch.emit($event)" 
        (onSelect)="onSelect.emit($event)"
        (onClear)="onClear.emit()"
        [optionLabel]="field" 
        [dropdown]="dropdown" 
        [multiple]="multiple"
        [placeholder]="placeholder"
        [disabled]="disabled"
        [showClear]="showClear"
        styleClass="apex-p-autocomplete">
        
        <ng-template let-item pTemplate="item">
          <ng-container *ngIf="itemTemplate; else defaultItem">
            <ng-container *ngTemplateOutlet="itemTemplate; context: {$implicit: item}"></ng-container>
          </ng-container>
          <ng-template #defaultItem>
            <div>{{field ? item[field] : item}}</div>
          </ng-template>
        </ng-template>
      </p-autoComplete>
    </div>
  `,
  styles: [`
    .apex-popuplov-wrapper {
      display: flex;
      flex-direction: column;
      width: 100%;
      margin-bottom: 1rem;
    }
    .apex-item-label {
      font-weight: 500;
      margin-bottom: 0.5rem;
      color: var(--text-color);
    }
    ::ng-deep .apex-p-autocomplete {
      width: 100%;
    }
    ::ng-deep .apex-p-autocomplete .p-autocomplete-input {
      width: 100%;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexPopupLOVComponent {
  @Input() value: any;
  @Input() suggestions: any[] = [];
  @Input() label?: string;
  @Input() field: string = '';
  @Input() dropdown: boolean = true;
  @Input() multiple: boolean = false;
  @Input() placeholder: string = 'Select a value...';
  @Input() disabled: boolean = false;
  @Input() showClear: boolean = true;

  @Output() onSearch = new EventEmitter<{originalEvent: Event, query: string}>();
  @Output() onSelect = new EventEmitter<any>();
  @Output() onClear = new EventEmitter<void>();

  @ContentChild('item') itemTemplate?: TemplateRef<any>;
}
