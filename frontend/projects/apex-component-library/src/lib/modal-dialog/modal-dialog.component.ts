import {
  Component, Input, Output, EventEmitter, ContentChild, TemplateRef,
  ChangeDetectionStrategy
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';

export type DialogSize = 'sm' | 'md' | 'lg' | 'xl' | 'full';

const SIZE_MAP: Record<DialogSize, string> = {
  sm:   '420px',
  md:   '560px',
  lg:   '780px',
  xl:   '1000px',
  full: '95vw',
};

@Component({
  selector: 'apex-modal-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, DialogModule, ButtonModule],
  template: `
    <p-dialog
      [visible]="visible"
      (visibleChange)="visibleChange.emit($event)"
      [header]="title"
      [modal]="true"
      [closable]="closable"
      [draggable]="draggable"
      [resizable]="resizable"
      [maximizable]="maximizable"
      [closeOnEscape]="closeOnEscape"
      [style]="{ width: width || sizeWidth }"
      [styleClass]="'apex-dialog ' + (styleClass || '')"
      (onShow)="onShow.emit()"
      (onHide)="onHide.emit()">

      <ng-content />

      @if (footerTemplate) {
        <ng-template pTemplate="footer">
          <ng-container [ngTemplateOutlet]="footerTemplate" />
        </ng-template>
      }
    </p-dialog>
  `,
  styles: [`
    :host ::ng-deep .apex-dialog .p-dialog-header {
      background: var(--surface-50);
      border-bottom: 1px solid var(--surface-200);
      padding: .75rem 1.25rem;
      font-weight: 600;
    }
    :host ::ng-deep .apex-dialog .p-dialog-content {
      padding: 1.25rem;
    }
    :host ::ng-deep .apex-dialog .p-dialog-footer {
      padding: .75rem 1.25rem;
      border-top: 1px solid var(--surface-200);
      display: flex;
      justify-content: flex-end;
      gap: .5rem;
    }
  `],
})
export class ApexModalDialogComponent {
  @Input() visible = false;
  @Input() title = '';
  @Input() size: DialogSize = 'md';
  @Input() width?: string;
  @Input() closable = true;
  @Input() draggable = true;
  @Input() resizable = false;
  @Input() maximizable = false;
  @Input() closeOnEscape = true;
  @Input() styleClass?: string;

  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() onShow = new EventEmitter<void>();
  @Output() onHide = new EventEmitter<void>();

  @ContentChild('footer') footerTemplate?: TemplateRef<any>;

  get sizeWidth(): string { return SIZE_MAP[this.size]; }
}
