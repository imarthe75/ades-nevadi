import { Component, OnDestroy, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject, signal, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { TagModule } from 'primeng/tag';
import { StepperModule } from 'primeng/stepper';
import { ProgressBarModule } from 'primeng/progressbar';
import { TextareaModule } from 'primeng/textarea';
import { ApiService } from '../../core/services/api.service';
import { ApexNotificationService } from 'apex-component-library';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';

interface Faltante { alumno: string; materia: string; }

interface ValidacionResult {
  puede_cerrar: boolean;
  alumnos_faltantes: number;
  materias_incompletas: number;
  detalles: {
    total_esperadas: number;
    con_calificacion: number;
    ya_cerradas: number;
    faltantes: number;
    alumnos_sin_cal: Faltante[] | null;
  };
}

/**
 * Diálogo de confirmación y ejecución del cierre de período en el gradebook.
 * Operación irreversible: congela calificaciones del período y dispara el cálculo
 * de promedios finales. Requiere nivelAcceso 4 (AdminPlantel) o superior.
 */
@Component({
  selector: 'app-cierre-periodo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    DialogModule, ButtonModule, TagModule,
    StepperModule, ProgressBarModule, TextareaModule,
    InteractiveGridComponent,
  ],
  template: `
<p-dialog [(visible)]="visible" (visibleChange)="visibleChange.emit($event)"
          header="Cierre Formal de Período" [modal]="true"
          [style]="{width:'680px'}" [closable]="!cerrando()">

  <!-- PASO 1: Resumen de lo que se va a cerrar -->
  @if (paso() === 1) {
    <div class="wizard-step">
      <div class="step-header">
        <i class="pi pi-info-circle text-blue-500" style="font-size:2rem"></i>
        <div>
          <h3>¿Cerrar este período?</h3>
          <p class="text-muted">Este proceso bloqueará la edición de todas las calificaciones del período seleccionado.</p>
        </div>
      </div>
      <div class="info-card mt-3">
        <div class="info-row">
          <span class="info-label">Período</span>
          <strong>{{ periodoNombre }}</strong>
        </div>
        <div class="info-row">
          <span class="info-label">Grupo</span>
          <strong>{{ grupoNombre }}</strong>
        </div>
      </div>
      <p class="text-muted mt-3" style="font-size:.85rem">
        <i class="pi pi-lock mr-1"></i>
        Una vez cerrado, solo ADMIN puede reabrir manualmente en la base de datos.
        El sistema generará un snapshot inmutable de las calificaciones.
      </p>
    </div>
    <ng-template pTemplate="footer">
      <button pButton label="Cancelar" severity="secondary" (click)="cerrar()"></button>
      <button pButton label="Validar calificaciones" icon="pi pi-check-circle"
              (click)="irPaso2()" [loading]="validando()"></button>
    </ng-template>
  }

  <!-- PASO 2: Resultado de validación -->
  @if (paso() === 2) {
    <div class="wizard-step">
      @if (validacion()?.puede_cerrar) {
        <div class="step-header success">
          <i class="pi pi-check-circle text-green-500" style="font-size:2rem"></i>
          <div>
            <h3>¡Validación exitosa!</h3>
            <p class="text-muted">Todas las calificaciones están completas.</p>
          </div>
        </div>
        <div class="info-card mt-3">
          <div class="info-row">
            <span class="info-label">Total de registros</span>
            <strong>{{ validacion()?.detalles?.total_esperadas }}</strong>
          </div>
          <div class="info-row">
            <span class="info-label">Con calificación</span>
            <strong class="text-success">{{ validacion()?.detalles?.con_calificacion }}</strong>
          </div>
          <div class="info-row">
            <span class="info-label">Pendientes</span>
            <strong>0</strong>
          </div>
        </div>
      } @else {
        <div class="step-header error">
          <i class="pi pi-times-circle text-red-500" style="font-size:2rem"></i>
          <div>
            <h3>Faltan calificaciones</h3>
            <p class="text-muted">No se puede cerrar hasta completar todos los registros.</p>
          </div>
        </div>
        <div class="info-card mt-3 border-red">
          <div class="info-row">
            <span class="info-label">Sin calificación</span>
            <strong class="text-danger">{{ validacion()?.alumnos_faltantes }}</strong>
          </div>
          <div class="info-row">
            <span class="info-label">Materias incompletas</span>
            <strong class="text-danger">{{ validacion()?.materias_incompletas }}</strong>
          </div>
        </div>
        @if (faltantes().length) {
          <app-interactive-grid
            [data]="faltantes()"
            [columns]="faltantesColumns"
          />
        }
      }
    </div>
    <ng-template pTemplate="footer">
      <button pButton label="Atrás" severity="secondary" icon="pi pi-arrow-left" (click)="paso.set(1)"></button>
      @if (validacion()?.puede_cerrar) {
        <button pButton label="Revisar resumen" icon="pi pi-arrow-right" iconPos="right"
                (click)="paso.set(3)"></button>
      } @else {
        <button pButton label="Cerrar" severity="secondary" (click)="cerrar()"></button>
      }
    </ng-template>
  }

  <!-- PASO 3: Resumen final antes de confirmar -->
  @if (paso() === 3) {
    <div class="wizard-step">
      <div class="step-header">
        <i class="pi pi-file-check text-orange-500" style="font-size:2rem"></i>
        <div>
          <h3>Resumen del cierre</h3>
          <p class="text-muted">Revisa los datos antes de confirmar.</p>
        </div>
      </div>
      <div class="info-card mt-3">
        <div class="info-row">
          <span class="info-label">Período a cerrar</span>
          <strong>{{ periodoNombre }}</strong>
        </div>
        <div class="info-row">
          <span class="info-label">Grupo</span>
          <strong>{{ grupoNombre }}</strong>
        </div>
        <div class="info-row">
          <span class="info-label">Calificaciones a bloquear</span>
          <strong>{{ validacion()?.detalles?.total_esperadas }}</strong>
        </div>
        <div class="info-row">
          <span class="info-label">Snapshot histórico</span>
          <p-tag value="Se generará" severity="info" />
        </div>
        <div class="info-row">
          <span class="info-label">Estado resultante</span>
          <p-tag value="CERRADO — sin edición" severity="warn" />
        </div>
      </div>
      <div class="field mt-3">
        <label for="notas-cierre" style="font-weight:600;display:block;margin-bottom:4px">
          Notas del cierre (opcional)
        </label>
        <textarea id="notas-cierre" pTextarea [(ngModel)]="notas" rows="3"
                  placeholder="Ej: Cierre de primer bimestre. Todas las materias revisadas."
                  style="width:100%;resize:vertical"></textarea>
      </div>
    </div>
    <ng-template pTemplate="footer">
      <button pButton label="Atrás" severity="secondary" icon="pi pi-arrow-left" (click)="paso.set(2)"></button>
      <button pButton label="Ir a confirmación" icon="pi pi-arrow-right" iconPos="right"
              (click)="paso.set(4)"></button>
    </ng-template>
  }

  <!-- PASO 4: Doble confirmación -->
  @if (paso() === 4) {
    <div class="wizard-step">
      <div class="step-header error">
        <i class="pi pi-exclamation-triangle text-red-500" style="font-size:2rem"></i>
        <div>
          <h3>Confirmación final</h3>
          <p class="text-muted">Esta acción es <strong>irreversible</strong>. Las calificaciones quedarán bloqueadas.</p>
        </div>
      </div>
      <div class="confirm-box mt-3">
        <p>Para confirmar, escribe <strong>CERRAR</strong> en el campo:</p>
        <input class="confirm-input" [(ngModel)]="confirmText"
               placeholder="Escribe CERRAR" autocomplete="off" />
      </div>
    </div>
    <ng-template pTemplate="footer">
      <button pButton label="Cancelar" severity="secondary" (click)="cerrar()" [disabled]="cerrando()"></button>
      <button pButton label="Cerrar período definitivamente" icon="pi pi-lock"
              severity="danger"
              [disabled]="confirmText !== 'CERRAR'"
              [loading]="cerrando()"
              (click)="ejecutarCierre()"></button>
    </ng-template>
  }

</p-dialog>
  `,
  styles: [`
    .wizard-step { padding: 8px 0; min-height: 200px; }
    .step-header { display: flex; gap: 16px; align-items: flex-start; }
    .step-header h3 { margin: 0 0 4px; font-family: 'Jost', sans-serif; }
    .step-header p  { margin: 0; font-size: .9rem; }
    .info-card {
      background: var(--p-surface-50, #f8f9fa);
      border: 1px solid var(--p-surface-border);
      border-radius: 8px;
      padding: 12px 16px;
    }
    .info-card.border-red { border-color: #fca5a5; background: #fff5f5; }
    .info-row { display: flex; justify-content: space-between; padding: 6px 0;
                border-bottom: 1px solid var(--p-surface-border); }
    .info-row:last-child { border-bottom: none; }
    .info-label { color: var(--p-text-muted-color); font-size: .9rem; }
    .text-success { color: #15803d; }
    .text-danger  { color: #dc2626; }
    .text-muted   { color: var(--p-text-muted-color); }
    .confirm-box  { background: #fff5f5; border: 1px solid #fca5a5; border-radius: 8px;
                    padding: 16px; text-align: center; }
    .confirm-input {
      margin-top: 12px; width: 200px; text-align: center;
      padding: 8px 12px; border: 2px solid #dc2626; border-radius: 6px;
      font-size: 1.1rem; font-weight: 600; letter-spacing: .05em;
    }
    .mr-1 { margin-right: 4px; }
    .mt-3 { margin-top: 12px; }
  `],
})
export class CierrePeriodoComponent implements OnChanges, OnDestroy {
  private destroy$ = new Subject<void>();
  @Input() visible = false;
  @Input() periodoId: string | null = null;
  @Input() periodoNombre = '';
  @Input() grupoId: string | null = null;
  @Input() grupoNombre = '';

  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() periodoCerrado = new EventEmitter<void>();

  private api    = inject(ApiService);
  private notify = inject(ApexNotificationService);

  readonly faltantesColumns: ColumnConfig[] = [
    { field: 'alumno',  header: 'Alumno' },
    { field: 'materia', header: 'Materia sin calificar' },
  ];

  paso        = signal(1);
  validando   = signal(false);
  cerrando    = signal(false);
  validacion  = signal<ValidacionResult | null>(null);
  faltantes   = signal<Faltante[]>([]);

  notas        = '';
  confirmText  = '';

  ngOnChanges(changes: SimpleChanges) {
    if (changes['visible']?.currentValue === true) {
      this.resetear();
    }
  }

  private resetear() {
    this.paso.set(1);
    this.validacion.set(null);
    this.faltantes.set([]);
    this.notas = '';
    this.confirmText = '';
  }

  irPaso2() {
    if (!this.periodoId || !this.grupoId) return;
    this.validando.set(true);
    this.api.get(
      `/evaluaciones/periodos/${this.periodoId}/validar-cierre?grupo_id=${this.grupoId}`
    ).pipe(takeUntil(this.destroy$)).subscribe({
      next: (r: any) => {
        this.validacion.set(r);
        this.faltantes.set(r.detalles?.alumnos_sin_cal ?? []);
        this.validando.set(false);
        this.paso.set(2);
      },
      error: () => {
        this.notify.error('Error', 'No se pudo validar el período');
        this.validando.set(false);
      },
    });
  }

  ejecutarCierre() {
    if (!this.periodoId || !this.grupoId || this.confirmText !== 'CERRAR') return;
    this.cerrando.set(true);
    this.api.post(`/evaluaciones/periodos/${this.periodoId}/cerrar`, {
      grupo_id: this.grupoId,
      notas: this.notas || null,
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: (r: any) => {
        this.cerrando.set(false);
        this.notify.success(
          'Período cerrado',
          `${r.calificaciones_cerradas} calificaciones bloqueadas correctamente.`
        );
        this.visibleChange.emit(false);
        this.periodoCerrado.emit();
      },
      error: (err: any) => {
        this.cerrando.set(false);
        const msg = err?.error?.detail?.detail ?? err?.error?.detail ?? 'Error al cerrar período';
        this.notify.error('Error', typeof msg === 'string' ? msg : JSON.stringify(msg));
      },
    });
  }

  cerrar() {
    if (this.cerrando()) return;
    this.visibleChange.emit(false);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
