import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, forwardRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FileUploadModule, FileUpload } from 'primeng/fileupload';
import { FormsModule, NG_VALUE_ACCESSOR, ControlValueAccessor } from '@angular/forms';

@Component({
  selector: 'apex-fileupload',
  standalone: true,
  imports: [CommonModule, FileUploadModule, FormsModule],
  template: `
    <div class="apex-fileupload-container">
      <label *ngIf="label" class="apex-fileupload-label" [ngClass]="{'is-required': required}">
        {{label}}
      </label>
      
      <!-- Advanced mode (Drag & Drop) -->
      <p-fileUpload *ngIf="mode === 'advanced'"
        #advancedUpload
        [name]="name"
        [url]="url"
        [multiple]="multiple"
        [accept]="accept"
        [maxFileSize]="maxFileSize"
        [auto]="auto"
        [disabled]="disabled"
        (onUpload)="onUploadComplete($event)"
        (onSelect)="onFileSelect($event)"
        (onError)="onUploadError($event)"
        (onClear)="onFileClear()"
        [chooseLabel]="chooseLabel"
        [uploadLabel]="uploadLabel"
        [cancelLabel]="cancelLabel"
        styleClass="apex-p-fileupload">
      </p-fileUpload>

      <!-- Basic mode (Simple button) -->
      <p-fileUpload *ngIf="mode === 'basic'"
        #basicUpload
        mode="basic"
        [name]="name"
        [url]="url"
        [multiple]="multiple"
        [accept]="accept"
        [maxFileSize]="maxFileSize"
        [auto]="auto"
        [disabled]="disabled"
        (onUpload)="onUploadComplete($event)"
        (onSelect)="onFileSelect($event)"
        (onError)="onUploadError($event)"
        [chooseLabel]="chooseLabel"
        styleClass="apex-p-fileupload-basic">
      </p-fileUpload>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      margin-bottom: 1rem;
    }
    .apex-fileupload-container {
      width: 100%;
    }
    .apex-fileupload-label {
      display: block;
      margin-bottom: 0.5rem;
      font-weight: 500;
      color: var(--text-color, #374151);
      font-size: 0.875rem;
    }
    .apex-fileupload-label.is-required::after {
      content: '*';
      color: var(--red-500, #ef4444);
      margin-left: 0.25rem;
    }
  `],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => ApexFileUploadComponent),
      multi: true
    }
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexFileUploadComponent implements ControlValueAccessor {
  /** Mode of the uploader */
  @Input() mode: 'advanced' | 'basic' = 'advanced';

  /** Target URL for the upload (if auto or manual upload is used) */
  @Input() url?: string;

  /** Name of the request parameter to identify the files at backend */
  @Input() name: string = 'file';

  /** Label for the component */
  @Input() label: string = '';

  /** Allow multiple file selection */
  @Input() multiple: boolean = false;

  /** Accept attribute, e.g. "image/*,.pdf" */
  @Input() accept?: string;

  /** Max file size in bytes */
  @Input() maxFileSize?: number;

  /** Automatically upload on selection */
  @Input() auto: boolean = false;

  /** Disabled state */
  @Input() disabled: boolean = false;

  /** Required field indicator */
  @Input() required: boolean = false;

  // Labels
  @Input() chooseLabel: string = 'Choose';
  @Input() uploadLabel: string = 'Upload';
  @Input() cancelLabel: string = 'Cancel';

  // Events
  @Output() fileSelect = new EventEmitter<any>();
  @Output() uploadComplete = new EventEmitter<any>();
  @Output() uploadError = new EventEmitter<any>();
  @Output() fileClear = new EventEmitter<void>();

  // CVA
  public onChange: any = () => {};
  public onTouched: any = () => {};

  public writeValue(val: any): void {
    // File upload doesn't typically accept incoming values natively unless preloading logic is added.
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

  public onFileSelect(event: any): void {
    this.fileSelect.emit(event);
    // For reactive forms, pass the files
    this.onChange(event.currentFiles);
    this.onTouched();
  }

  public onUploadComplete(event: any): void {
    this.uploadComplete.emit(event);
  }

  public onUploadError(event: any): void {
    this.uploadError.emit(event);
  }

  public onFileClear(): void {
    this.fileClear.emit();
    this.onChange(null);
  }
}
