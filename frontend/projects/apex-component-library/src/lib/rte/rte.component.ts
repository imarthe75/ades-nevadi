import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EditorModule } from 'primeng/editor';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'apex-rte',
  standalone: true,
  imports: [CommonModule, EditorModule, FormsModule],
  template: `
    <div class="apex-rte-wrapper">
      <label *ngIf="label" class="apex-item-label">{{label}}</label>
      <p-editor 
        [(ngModel)]="value" 
        (onTextChange)="onTextChange.emit($event)"
        [readonly]="readonly"
        [style]="editorStyle"
        styleClass="apex-p-editor">
        <p-header *ngIf="customToolbar">
          <ng-content select="[toolbar]"></ng-content>
        </p-header>
      </p-editor>
    </div>
  `,
  styles: [`
    .apex-rte-wrapper {
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
    ::ng-deep .apex-p-editor .p-editor-toolbar {
      border-top-left-radius: var(--border-radius);
      border-top-right-radius: var(--border-radius);
      background: var(--surface-card);
    }
    ::ng-deep .apex-p-editor .p-editor-content {
      border-bottom-left-radius: var(--border-radius);
      border-bottom-right-radius: var(--border-radius);
      background: var(--surface-ground);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexRTEComponent {
  @Input() value: string = '';
  @Input() label?: string;
  @Input() readonly: boolean = false;
  @Input() editorStyle: any = { height: '320px' };
  @Input() customToolbar: boolean = false;

  @Output() onTextChange = new EventEmitter<any>();
}
