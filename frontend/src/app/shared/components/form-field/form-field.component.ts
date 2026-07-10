import { Component, Input, Output, EventEmitter, OnInit, OnDestroy, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, FormControl, ReactiveFormsModule } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { Subject, takeUntil } from 'rxjs';
import { InputFormattersService } from '../../services/input-formatters.service';

/**
 * Componente reutilizable para campos de formulario con validación integrada
 * Proporciona:
 * - Formateo automático de entrada (máscaras)
 * - Mostrar/ocultar caracteres restantes
 * - Texto de ayuda contextual
 * - Mensajes de error automáticos
 * - Estilos consistentes con ADES/APEX
 */
@Component({
  selector: 'app-form-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, InputTextModule],
  template: `
    <div class="form-field-wrapper" [class.has-error]="control.invalid && control.touched">
      <!-- Label with required indicator -->
      <label class="field-label">
        {{ label }}
        <span class="required-indicator" *ngIf="required">*</span>
      </label>

      <!-- Input field with formatter -->
      <input
        pInputText
        [formControl]="control"
        [type]="type"
        [placeholder]="placeholder"
        [maxlength]="maxLength"
        (input)="onInput($event)"
        [class.field-input-error]="control.invalid && control.touched"
        class="field-input"
      />

      <!-- Help text -->
      <small class="help-text" *ngIf="helpText">
        ℹ️ {{ helpText }}
      </small>

      <!-- Character counter -->
      <small class="char-count" *ngIf="maxLength">
        {{ (control.value || '').length }} / {{ maxLength }} caracteres
      </small>

      <!-- Error messages -->
      <small class="error-message" *ngIf="control.invalid && control.touched">
        {{ getErrorMessage() }}
      </small>
    </div>
  `,
  styles: [`
    .form-field-wrapper {
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
      margin-bottom: 1.2rem;
    }

    .form-field-wrapper.has-error {
      /* Add subtle red tint on error */
      background-color: rgba(231, 76, 60, 0.02);
      padding: 0.8rem;
      border-radius: 4px;
    }

    .field-label {
      display: block;
      font-weight: 600;
      font-size: 0.95rem;
      color: #2c3e50;
      margin-bottom: 0.2rem;
    }

    .required-indicator {
      color: #e74c3c;
      font-weight: bold;
      margin-left: 0.25rem;
    }

    .field-input {
      width: 100%;
      padding: 0.6rem 0.8rem;
      border: 1px solid #d0d7e0;
      border-radius: 4px;
      font-size: 0.95rem;
      font-family: inherit;
      transition: all 0.2s ease;
    }

    .field-input:focus {
      outline: none;
      border-color: #3498db;
      box-shadow: 0 0 0 2px rgba(52, 152, 219, 0.1);
    }

    .field-input-error {
      border-color: #e74c3c;
      background-color: rgba(231, 76, 60, 0.05);
    }

    .field-input-error:focus {
      box-shadow: 0 0 0 2px rgba(231, 76, 60, 0.1);
    }

    .help-text {
      display: block;
      font-size: 0.85rem;
      color: #666;
      line-height: 1.4;
      margin-top: 0.2rem;
    }

    .char-count {
      display: block;
      font-size: 0.8rem;
      color: #999;
      text-align: right;
      margin-top: 0.2rem;
      font-weight: 500;
    }

    .error-message {
      display: block;
      font-size: 0.85rem;
      color: #e74c3c;
      font-weight: 500;
      margin-top: 0.3rem;
      line-height: 1.4;
    }

    /* APEX-style focus state */
    :host ::ng-deep .field-input:focus-visible {
      outline: 2px solid #3498db;
      outline-offset: 2px;
    }
  `]
})
export class FormFieldComponent implements OnInit, OnDestroy {
  @Input() control!: FormControl;
  @Input() label = '';
  @Input() placeholder = '';
  @Input() type = 'text';
  @Input() maxLength: number | null = null;
  @Input() helpText = '';
  @Input() required = false;
  @Input() formatter: ((v: string) => string) | null = null;

  @Output() valueChange = new EventEmitter<string>();

  private readonly destroy$ = new Subject<void>();

  constructor(private inputFormatters: InputFormattersService, private cdr: ChangeDetectorRef) {}

  ngOnInit() {
    if (!this.control) {
      console.warn('FormFieldComponent: control is required');
      return;
    }
    this.control.statusChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.cdr.markForCheck());
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Maneja entrada de usuario: aplica formateo si es necesario
   */
  onInput(event: any) {
    let value = event.target.value;

    if (this.formatter) {
      value = this.formatter(value);
      this.control.setValue(value, { emitEvent: false });
    }

    this.valueChange.emit(value);
  }

  /**
   * Genera mensaje de error específico basado en validadores fallidos
   */
  getErrorMessage(): string {
    const errors = this.control.errors;
    if (!errors) return '';

    // Mostrar primer error encontrado
    if (errors['required']) {
      return `${this.label} es requerido`;
    }
    if (errors['maxlength']) {
      return `Máximo ${errors['requiredLength']} caracteres (tienes ${errors['actualLength']})`;
    }
    if (errors['minlength']) {
      return `Mínimo ${errors['requiredLength']} caracteres (tienes ${errors['actualLength']})`;
    }
    if (errors['exactLength']) {
      return `Debe tener exactamente ${errors['requiredLength']} caracteres`;
    }
    if (errors['email']) {
      return 'Email no válido';
    }
    if (errors['pattern']) {
      return 'Formato no válido';
    }
    if (errors['isCURP']) {
      return 'CURP inválida (formato: AAAA999999HAAAAA01)';
    }
    if (errors['isRFC']) {
      return 'RFC inválido (formato: AAAA999999AAA)';
    }
    if (errors['isMoneyFormat']) {
      return 'Formato de dinero inválido (ej: 1234.56)';
    }
    if (errors['isPositiveInteger']) {
      return 'Solo se permiten números enteros positivos';
    }
    if (errors['isMexicanPhoneNumber']) {
      return 'Teléfono debe tener 10 dígitos';
    }
    if (errors['isMexicanZipCode']) {
      return 'Código postal debe tener 5 dígitos';
    }
    if (errors['isSafeAlphanumeric']) {
      return 'Contiene caracteres no permitidos';
    }
    if (errors['maxChars']) {
      return `Máximo ${errors['maxLength']} caracteres permitidos`;
    }
    if (errors['containsNoSpaces']) {
      return 'No se permiten espacios en este campo';
    }
    if (errors['isAlphanumeric']) {
      return 'Solo se permiten letras y números';
    }
    if (errors['isNumeric']) {
      return 'Solo se permiten números';
    }

    // Genérico
    return 'Valor no válido';
  }
}
