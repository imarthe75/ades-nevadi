import {
  Component, input, inject, signal, computed, OnDestroy, ChangeDetectionStrategy, ChangeDetectorRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject, takeUntil } from 'rxjs';
import { ApiService } from '../../../core/services/api.service';
import { ButtonModule }    from 'primeng/button';
import { DialogModule }    from 'primeng/dialog';
import { TableModule }     from 'primeng/table';
import { TagModule }       from 'primeng/tag';
import { TooltipModule }   from 'primeng/tooltip';
import { ProgressBarModule } from 'primeng/progressbar';
import { environment }    from '../../../../environments/environment';
import { ContextService } from '../../../core/services/context.service';

export interface ImportResult {
  entidad: string;
  total: number;
  exitosos: number;
  errores: number;
  detalle_errores: Array<{ fila: number; dato: string; error: string }>;
}

@Component({
  selector: 'app-import-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    CommonModule, ButtonModule, DialogModule,
    TableModule, TagModule, TooltipModule, ProgressBarModule,
  ],
  template: `
    <!-- Botones de acción — solo visible para coordinador y superiores (nivel_acceso <= 3) -->
    @if (puedeImportar()) {
    <div class="import-actions">
      <p-button
        icon="pi pi-upload"
        label="Importar CSV/Excel"
        [outlined]="true"
        size="small"
        severity="secondary"
        (onClick)="fileInput.click()"
        [loading]="subiendo()"
        pTooltip="Importar registros desde archivo CSV o Excel (.xlsx)"
        tooltipPosition="top"
      />
      <p-button
        icon="pi pi-download"
        label="Plantilla"
        [text]="true"
        size="small"
        severity="secondary"
        (onClick)="descargarPlantilla()"
        pTooltip="Descargar plantilla CSV de ejemplo"
        tooltipPosition="top"
      />
    </div>
    }

    <!-- Input oculto -->
    <input
      #fileInput
      type="file"
      [accept]="accept"
      style="display:none"
      (change)="onFileSelected($event)"
    />

    <!-- Dialog de resultado -->
    <p-dialog
      [(visible)]="showResult"
      [header]="headerDialog()"
      [modal]="true"
      [style]="{width:'620px'}"
      [closeOnEscape]="true"
    >
      @if (resultado()) {
        <div class="result-summary">
          <div class="stat-card stat-total">
            <span class="stat-num">{{ resultado()!.total }}</span>
            <span class="stat-label">Total filas</span>
          </div>
          <div class="stat-card stat-ok">
            <span class="stat-num">{{ resultado()!.exitosos }}</span>
            <span class="stat-label">Importados</span>
          </div>
          <div class="stat-card" [class.stat-error]="resultado()!.errores > 0" [class.stat-zero]="resultado()!.errores === 0">
            <span class="stat-num">{{ resultado()!.errores }}</span>
            <span class="stat-label">Errores</span>
          </div>
        </div>

        @if (resultado()!.detalle_errores.length > 0) {
          <p class="error-title">Detalle de errores:</p>
          <p-table
            [value]="resultado()!.detalle_errores"
            [scrollable]="true" scrollHeight="260px"
            styleClass="p-datatable-sm p-datatable-striped"
          >
            <ng-template pTemplate="header">
              <tr>
                <th style="width:60px">Fila</th>
                <th style="width:180px">Identificador</th>
                <th>Error</th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-e>
              <tr>
                <td class="text-center">{{ e.fila }}</td>
                <td class="monospace">{{ e.dato }}</td>
                <td class="error-msg">{{ e.error }}</td>
              </tr>
            </ng-template>
          </p-table>
        }
      }

      <ng-template pTemplate="footer">
        <p-button label="Cerrar" icon="pi pi-times" [text]="true" (onClick)="showResult = false" />
        @if (resultado()?.exitosos ?? 0 > 0) {
          <p-button label="Actualizar lista" icon="pi pi-refresh" (onClick)="onRefresh()" />
        }
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .import-actions { display: flex; gap: .25rem; align-items: center; }

    .result-summary {
      display: flex; gap: 1rem; margin-bottom: 1.25rem;
    }
    .stat-card {
      flex: 1; text-align: center; padding: .75rem 1rem;
      border-radius: 8px; border: 1px solid var(--surface-border);
      display: flex; flex-direction: column; gap: .15rem;
    }
    .stat-num   { font-size: 1.8rem; font-weight: 700; line-height: 1; }
    .stat-label { font-size: .75rem; color: var(--text-color-secondary); }
    .stat-total { background: var(--surface-100); }
    .stat-ok    { background: #ecfdf5; border-color: #6ee7b7; }
    .stat-ok .stat-num { color: #059669; }
    .stat-error { background: #fef2f2; border-color: #fca5a5; }
    .stat-error .stat-num { color: #dc2626; }
    .stat-zero  { background: var(--surface-100); }
    .stat-zero .stat-num { color: var(--text-color-secondary); }

    .error-title { font-weight: 600; font-size: .85rem; margin: 0 0 .5rem; color: #dc2626; }
    .error-msg   { font-size: .8rem; color: #dc2626; }
    .monospace   { font-family: monospace; font-size: .8rem; }
  `],
})
export class ImportButtonComponent implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly ctx  = inject(ContextService);
  private readonly cdr  = inject(ChangeDetectorRef);
  private readonly destroy$ = new Subject<void>();

  /** Entidad a importar: 'alumnos' | 'profesores' | 'materias' | 'grupos' */
  entidad = input.required<string>();

  /** Callback invocado cuando hay registros exitosos, para refrescar la lista padre */
  onSuccess = input<() => void>(() => {});

  /** Solo coordinador (nivel 3) o superior puede importar */
  readonly puedeImportar = computed(() => this.ctx.nivelAcceso() <= 3);

  accept = '.csv,.xlsx,.xls';

  subiendo  = signal(false);
  resultado = signal<ImportResult | null>(null);
  showResult = false;

  headerDialog = computed(() => {
    const r = this.resultado();
    if (!r) return 'Resultado de importación';
    const ok = r.exitosos === r.total;
    return ok
      ? `✓ Importación completa — ${r.exitosos} registros`
      : `Importación finalizada — ${r.exitosos} de ${r.total} registros`;
  });

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file  = input.files?.[0];
    if (!file) return;
    input.value = '';  // reset para permitir re-subir el mismo archivo
    this.upload(file);
  }

  private upload(file: File): void {
    const form = new FormData();
    form.append('file', file);

    this.subiendo.set(true);
    this.api.postForm<ImportResult>(
      `/imports/${this.entidad()}`,
      form,
    ).pipe(takeUntil(this.destroy$)).subscribe({
      next: (res) => {
        this.subiendo.set(false);
        this.resultado.set(res);
        this.showResult = true;
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.subiendo.set(false);
        const msg = err?.error?.detail ?? 'Error desconocido al importar';
        this.resultado.set({
          entidad: this.entidad(),
          total: 0, exitosos: 0, errores: 1,
          detalle_errores: [{ fila: 0, dato: '—', error: String(msg) }],
        });
        this.showResult = true;
        this.cdr.markForCheck();
      },
    });
  }

  descargarPlantilla(): void {
    this.api.getBlob(
      `/imports/plantillas/${this.entidad()}`
    ).pipe(takeUntil(this.destroy$)).subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a   = document.createElement('a');
      a.href    = url;
      a.download = `plantilla_${this.entidad()}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  onRefresh(): void {
    this.showResult = false;
    this.onSuccess()();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
