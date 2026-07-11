import { Directive, ElementRef, HostListener, Input, OnInit, inject } from '@angular/core';
import { NgControl } from '@angular/forms';
import { InputFormattersService } from '../services/input-formatters.service';

/**
 * Tipo de formato/validación de caracteres aplicado a un campo.
 * `safe` (por defecto) = límite de longitud + eliminación de caracteres
 * peligrosos, sin restringir el juego de caracteres. Los demás aplican una
 * máscara estricta (solo el juego de caracteres válido para ese dato oficial).
 */
export type AdesFormatKind =
  | 'safe' | 'nombre' | 'curp' | 'rfc' | 'telefono' | 'email'
  | 'cp' | 'alfanumerico' | 'numerico' | 'texto' | 'calle' | 'dinero';

/**
 * Directiva transversal de saneamiento de entrada para TODOS los campos de
 * captura del sistema. Se auto-aplica a cualquier `input[pInputText]` del
 * componente que la importe:
 *
 *  - Sin `adesFormat`  → modo `safe`: limita la longitud (maxlength del input
 *    o {@link SAFE_MAX} por defecto) y elimina caracteres peligrosos para
 *    inyección (`< > { } \` \\`). Evita cadenas excesivamente largas.
 *  - Con `adesFormat="curp|rfc|telefono|email|cp|nombre|numerico|..."` →
 *    aplica la máscara estricta correspondiente (solo caracteres válidos para
 *    ese dato oficial) y fija el `maxlength` canónico del dato.
 *
 * Actualiza el modelo (`ngModel`/reactive) re-emitiendo el evento `input` con
 * el valor ya saneado, por lo que funciona con cualquier binding existente sin
 * cambiar la plantilla más allá de (opcionalmente) declarar `adesFormat`.
 */
@Directive({
  selector: 'input[pInputText], input[adesFormat]',
  standalone: true,
})
export class AdesFormatDirective implements OnInit {
  /** Longitud máxima por defecto en modo `safe` (alineada con varchar(255)). */
  static readonly SAFE_MAX = 255;

  @Input('adesFormat') kind: AdesFormatKind = 'safe';
  /** Sobrescribe el maxlength para tipos de longitud variable (safe/numerico/alfanumerico/texto). */
  @Input() adesMax?: number;

  private readonly fmt = inject(InputFormattersService);
  private readonly el = inject<ElementRef<HTMLInputElement>>(ElementRef);
  private readonly ngControl = inject(NgControl, { optional: true, self: true });

  ngOnInit(): void {
    const input = this.el.nativeElement;
    const max = this.maxFor();
    if (max && !input.getAttribute('maxlength')) {
      input.setAttribute('maxlength', String(max));
    }
    // Sanea el valor inicial (por si viene "sucio" de la base de datos).
    if (input.value) {
      const formatted = this.apply(input.value);
      if (formatted !== input.value) this.commit(formatted);
    }
  }

  @HostListener('input')
  onInput(): void {
    const input = this.el.nativeElement;
    const raw = input.value;
    const formatted = this.apply(raw);
    if (formatted !== raw) this.commit(formatted);
  }

  /** Escribe el valor saneado en el DOM y propaga el cambio al modelo. */
  private commit(formatted: string): void {
    const input = this.el.nativeElement;
    input.value = formatted;
    // Re-emitir 'input' hace que el ValueAccessor de Angular lea el valor ya
    // saneado y actualice ngModel/FormControl (view -> model). Es idempotente:
    // en la segunda pasada raw === formatted y no se vuelve a emitir.
    input.dispatchEvent(new Event('input', { bubbles: false }));
  }

  private apply(v: string): string {
    switch (this.kind) {
      case 'nombre':       return this.fmt.formatNombre(v);
      case 'curp':         return this.fmt.formatCURP(v);
      case 'rfc':          return this.fmt.formatRFC(v);
      case 'telefono':     return this.fmt.formatTelefono(v);
      case 'email':        return this.fmt.formatEmail(v);
      case 'cp':           return this.fmt.formatZIP(v);
      case 'numerico':     return this.fmt.formatNumeric(v, this.adesMax ?? 50);
      case 'alfanumerico': return this.fmt.formatAlphanumeric(v, this.adesMax ?? 100);
      case 'calle':        return this.fmt.formatCalle(v);
      case 'dinero':       return this.fmt.formatDinero(v);
      case 'texto':        return this.fmt.formatTexto(v, this.adesMax ?? 100);
      case 'safe':
      default:             return this.fmt.formatTexto(v, this.adesMax ?? AdesFormatDirective.SAFE_MAX);
    }
  }

  private maxFor(): number | undefined {
    switch (this.kind) {
      case 'curp':     return 18;
      case 'rfc':      return 13;
      case 'telefono': return 10;
      case 'cp':       return 5;
      case 'nombre':   return 100;
      case 'calle':    return 150;
      case 'email':    return 100;
      case 'numerico':
      case 'alfanumerico':
      case 'texto':    return this.adesMax;
      case 'safe':
      default:         return this.adesMax ?? AdesFormatDirective.SAFE_MAX;
    }
  }
}
