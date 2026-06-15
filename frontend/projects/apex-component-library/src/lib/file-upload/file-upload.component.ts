import {
  Component, Input, Output, EventEmitter, ChangeDetectionStrategy
} from '@angular/core';
import { FileUploadModule } from 'primeng/fileupload';
import { ButtonModule } from 'primeng/button';

@Component({
  selector: 'apex-file-upload',
  standalone: true,
  imports: [FileUploadModule, ButtonModule],
  template: `
    <div class="apex-fileupload-container">
      @if (label) {
        <label class="apex-fileupload-label">{{ label }}</label>
      }

      @if (mode === 'advanced') {
        <p-fileUpload
          [name]="'file'"
          [url]="uploadUrl"
          [multiple]="multiple"
          [accept]="accept"
          [maxFileSize]="maxFileSize"
          [auto]="!!uploadUrl"
          (onUpload)="uploadComplete.emit($event)"
          (onSelect)="fileSelect.emit($event)"
          (onError)="error.emit($event)"
          chooseLabel="Choose File"
          uploadLabel="Upload"
          cancelLabel="Clear"
          styleClass="apex-p-fileupload">
          <ng-template pTemplate="content">
            <div class="apex-fileupload-dropzone">
              <i class="pi pi-cloud-upload apex-fileupload-icon"></i>
              <p class="apex-fileupload-hint">
                Drag and drop files here, or click <strong>Choose File</strong>
              </p>
              @if (accept) {
                <small class="apex-fileupload-meta">Accepted: {{ accept }}</small>
              }
              @if (maxFileSize) {
                <small class="apex-fileupload-meta">Max size: {{ formatSize(maxFileSize) }}</small>
              }
            </div>
          </ng-template>
        </p-fileUpload>
      } @else {
        <p-fileUpload
          mode="basic"
          [name]="'file'"
          [url]="uploadUrl"
          [multiple]="multiple"
          [accept]="accept"
          [maxFileSize]="maxFileSize"
          [auto]="!!uploadUrl"
          chooseLabel="{{ label || 'Choose File' }}"
          (onUpload)="uploadComplete.emit($event)"
          (onSelect)="fileSelect.emit($event)"
          (onError)="error.emit($event)"
          styleClass="apex-p-fileupload-basic">
        </p-fileUpload>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .apex-fileupload-container { width: 100%; }
    .apex-fileupload-label {
      display: block;
      font-size: 0.875rem;
      font-weight: 500;
      color: var(--text-color);
      margin-bottom: 0.5rem;
    }
    .apex-fileupload-dropzone {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 2rem 1rem;
      border: 2px dashed var(--surface-border);
      border-radius: var(--border-radius, 6px);
      background: var(--surface-ground);
      color: var(--text-color-secondary);
      text-align: center;
      transition: border-color 0.2s, background 0.2s;
    }
    .apex-fileupload-dropzone:hover {
      border-color: var(--primary-color);
      background: var(--primary-50, #eff6ff);
    }
    .apex-fileupload-icon {
      font-size: 2.5rem;
      color: var(--primary-300, #93c5fd);
      margin-bottom: 0.75rem;
    }
    .apex-fileupload-hint {
      margin: 0 0 0.35rem;
      font-size: 0.875rem;
    }
    .apex-fileupload-meta {
      font-size: 0.78rem;
      color: var(--text-color-secondary);
      display: block;
    }
    :host ::ng-deep .apex-p-fileupload .p-fileupload-buttonbar {
      background: var(--surface-section);
      border-bottom: 1px solid var(--surface-border);
      padding: 0.5rem 0.75rem;
      gap: 0.5rem;
    }
    :host ::ng-deep .apex-p-fileupload .p-fileupload-content {
      padding: 0;
      border: none;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexFileUploadComponent {
  @Input() accept: string = '';
  @Input() multiple: boolean = false;
  @Input() maxFileSize: number = 10485760; // 10 MB
  @Input() uploadUrl?: string;
  @Input() label: string = '';
  @Input() mode: 'basic' | 'advanced' = 'advanced';

  @Output() uploadComplete = new EventEmitter<any>();
  @Output() fileSelect = new EventEmitter<any>();
  @Output() error = new EventEmitter<any>();

  formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1048576) return `${(bytes / 1024).toFixed(0)} KB`;
    return `${(bytes / 1048576).toFixed(1)} MB`;
  }
}
