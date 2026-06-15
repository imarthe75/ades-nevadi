import {
  Component, Input, Output, EventEmitter, ChangeDetectionStrategy,
  OnInit, OnDestroy, OnChanges, SimpleChanges
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';

@Component({
  selector: 'apex-search',
  standalone: true,
  imports: [FormsModule, InputTextModule, IconFieldModule, InputIconModule],
  template: `
    <div class="apex-search-wrapper">
      <p-iconField iconPosition="left" styleClass="apex-search-field">
        @if (loading) {
          <p-inputIcon styleClass="pi pi-spin pi-spinner apex-search-icon" />
        } @else {
          <p-inputIcon styleClass="pi pi-search apex-search-icon" />
        }
        <input
          pInputText
          type="text"
          [ngModel]="value"
          (ngModelChange)="onInput($event)"
          [placeholder]="placeholder"
          class="apex-search-input"
          autocomplete="off"
        />
        @if (value) {
          <span class="apex-search-clear pi pi-times" (click)="clearSearch()"></span>
        }
      </p-iconField>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .apex-search-wrapper { position: relative; width: 100%; }
    :host ::ng-deep .apex-search-field { width: 100%; }
    :host ::ng-deep .apex-search-field .p-inputtext {
      width: 100%;
      padding-left: 2.5rem;
      padding-right: 2.25rem;
      height: 2.25rem;
      font-size: 0.875rem;
      border-radius: var(--border-radius, 6px);
      border: 1px solid var(--surface-border);
      background: var(--surface-0);
      color: var(--text-color);
      transition: border-color 0.2s, box-shadow 0.2s;
    }
    :host ::ng-deep .apex-search-field .p-inputtext:focus {
      border-color: var(--primary-color);
      box-shadow: 0 0 0 2px var(--primary-100, rgba(59,130,246,0.15));
      outline: none;
    }
    .apex-search-icon { color: var(--text-color-secondary); font-size: 0.9rem; }
    .apex-search-clear {
      position: absolute;
      right: 0.65rem;
      top: 50%;
      transform: translateY(-50%);
      color: var(--text-color-secondary);
      font-size: 0.75rem;
      cursor: pointer;
      padding: 0.25rem;
      border-radius: 50%;
      transition: color 0.2s;
    }
    .apex-search-clear:hover { color: var(--text-color); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexSearchComponent implements OnInit, OnDestroy {
  @Input() placeholder: string = 'Search...';
  @Input() value: string = '';
  @Input() debounce: number = 300;
  @Input() loading: boolean = false;

  @Output() valueChange = new EventEmitter<string>();
  @Output() search = new EventEmitter<string>();

  private inputSubject = new Subject<string>();
  private destroy$ = new Subject<void>();

  ngOnInit(): void {
    this.inputSubject.pipe(
      debounceTime(this.debounce),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(val => this.search.emit(val));
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onInput(val: string): void {
    this.value = val;
    this.valueChange.emit(val);
    this.inputSubject.next(val);
  }

  clearSearch(): void {
    this.value = '';
    this.valueChange.emit('');
    this.search.emit('');
    this.inputSubject.next('');
  }
}
