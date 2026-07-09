import { Component, OnDestroy, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { CalendarModule } from 'primeng/calendar';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import { TableModule } from 'primeng/table';

import { ApiService } from '../../core/services/api.service';

/**
 * Crear Examen Vinculado a Planeación Semanal
 *
 * FASE 3: Permite crear exámenes/evaluaciones que heredan automáticamente:
 * - planeacion_clase_id
 * - grupo_id (de la planeación)
 * - aprendizajes_esperados[] (via trigger)
 *
 * Similar a CrearTareaDesdeplanneacionComponent pero para ades_evaluaciones.
 */
@Component({
  selector: 'app-crear-examen-desde-planeacion',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule,
    ButtonModule, SelectModule, InputTextModule, TextareaModule,
    CalendarModule, CardModule, TagModule, ToastModule, MessageModule, TableModule
  ],
  providers: [MessageService],
  template: `
    <div class="page-header">
      <div>
        <h2 class="page-title">📝 Crear Examen desde Planeación</h2>
        <p class="page-subtitle">El examen heredará automáticamente los aprendizajes esperados</p>
      </div>
    </div>

    <p-toast />

    <!-- PASO 1: Seleccionar Grupo y Planeación -->
    <div class="card form-section">
      <h3>Paso 1: Seleccionar Planeación</h3>
      <form [formGroup]="step1Form">
        <div class="field-grid">
          <div class="field">
            <label>Grupo*</label>
            <p-select [options]="grupos()" formControlName="grupoId"
                     optionLabel="label" optionValue="value"
                     placeholder="Seleccionar grupo..."
                     (onChange)="onGrupoChange()"
                     [filter]="true" filterPlaceholder="Buscar..."/>
          </div>
        </div>

        <!-- Tabla de planeaciones disponibles -->
        @if (planeacionesLoaded()) {
          <div class="mt-3">
            <h4>Planeaciones activas</h4>
            @if ((planeaciones() || []).length === 0) {
              <p-message severity="info" text="No hay planeaciones activas en este grupo"/>
            } @else {
              <p-table [value]="planeaciones()" [paginator]="true" [rows]="5"
                      responsiveLayout="scroll" styleClass="mt-3">
                <ng-template pTemplate="header">
                  <tr>
                    <th>Seleccionar</th>
                    <th>Tema</th>
                    <th>Materia</th>
                    <th>Semana</th>
                    <th>Modalidad</th>
                    <th>Aprendizajes</th>
                  </tr>
                </ng-template>
                <ng-template pTemplate="body" let-planeacion>
                  <tr>
                    <td>
                      <input type="radio" [value]="planeacion.planeacion_id"
                             [(ngModel)]="planeacionSeleccionada"
                             (change)="onPlaneacionSelect(planeacion)"/>
                    </td>
                    <td><strong>{{planeacion.nombre_tema}}</strong></td>
                    <td>{{planeacion.nombre_materia}}</td>
                    <td>
                      <span class="badge">T{{planeacion.numero_trimestre}}-S{{planeacion.numero_semana}}</span>
                    </td>
                    <td>
                      <p-tag [value]="planeacion.modalidad"
                            [severity]="getModalidadSeverity(planeacion.modalidad)"/>
                    </td>
                    <td>
                      <span class="badge badge-info">{{planeacion.cantidad_aprendizajes}}</span>
                    </td>
                  </tr>
                </ng-template>
              </p-table>
            }
          </div>
        }
      </form>
    </div>

    <!-- PASO 2: Detalles de la Planeación Seleccionada -->
    @if (planeacionDetalle()) {
      <div class="card form-section">
        <h3>Paso 2: Aprendizajes a Evaluar</h3>

        <!-- Aprendizajes vinculados -->
        @if ((planeacionDetalle()?.aprendizajes || []).length > 0) {
          <div class="aprendizajes-section">
            <p class="subtitle">El examen evaluará los siguientes aprendizajes esperados:</p>

            <div class="aprendizajes-grid">
              @for (ae of planeacionDetalle()?.aprendizajes; track ae.aprendizaje_id) {
                <div class="aprendizaje-badge">
                  <div class="badge-header">
                    <strong>{{ae.codigo}}</strong>
                    @if (ae.competencia_nombre) {
                      <p-tag [value]="ae.competencia_nombre" severity="info" styleClass="ml-2"/>
                    }
                  </div>
                  <p class="badge-desc">{{ae.descripcion}}</p>
                </div>
              }
            </div>
          </div>
        }
      </div>
    }

    <!-- PASO 3: Datos del Examen -->
    @if (planeacionSeleccionada) {
      <div class="card form-section">
        <h3>Paso 3: Datos del Examen</h3>
        <form [formGroup]="examenForm">
          <div class="field-grid">
            <div class="field">
              <label>Nombre del Examen*</label>
              <input pInputText formControlName="nombreEvaluacion"
                     placeholder="Ej: Examen Período 1 - Tema 1-5"/>
            </div>

            <div class="field">
              <label>Descripción*</label>
              <textarea pTextarea formControlName="descripcion"
                       placeholder="Describe el tipo de examen y criterios de evaluación"
                       rows="3"/>
            </div>

            <div class="field">
              <label>Fecha del Examen*</label>
              <p-calendar formControlName="fecha" dateFormat="dd/mm/yy" [showIcon]="true"/>
            </div>

            <div class="field">
              <label>Puntaje Máximo</label>
              <input pInputText type="number" formControlName="puntajeMaximo"
                     min="0" step="0.5" placeholder="10"/>
            </div>
          </div>

          <div class="mt-4">
            <p-button label="✅ Guardar Examen"
                     (onClick)="guardarExamen()"
                     [loading]="guardando()"
                     [disabled]="!examenForm.valid"
                     icon="pi pi-save"/>
          </div>
        </form>
      </div>
    }
  `,
  styles: [`
    .form-section {
      margin-top: 1.5rem;
    }

    .field-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 1rem;
    }

    .field {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }

    .field label {
      font-weight: 600;
      font-size: 0.9rem;
    }

    .badge {
      display: inline-block;
      background: var(--primary-color);
      color: white;
      padding: 0.25rem 0.75rem;
      border-radius: 12px;
      font-size: 0.8rem;
      font-weight: 600;
    }

    .badge-info {
      background: var(--info-color);
    }

    .aprendizajes-section {
      background: var(--blue-50);
      border: 1px solid var(--blue-200);
      border-radius: 6px;
      padding: 1rem;
    }

    .subtitle {
      font-size: 0.9rem;
      color: var(--text-color-secondary);
      margin: 0 0 1rem 0;
    }

    .aprendizajes-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
      gap: 0.75rem;
    }

    .aprendizaje-badge {
      background: white;
      border: 1px solid var(--blue-200);
      border-radius: 4px;
      padding: 0.75rem;
      font-size: 0.85rem;
    }

    .badge-header {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin-bottom: 0.5rem;
    }

    .badge-desc {
      color: var(--text-color-secondary);
      margin: 0;
    }

    .mt-3 { margin-top: 1rem; }
    .mt-4 { margin-top: 1.5rem; }
    .ml-2 { margin-left: 0.5rem; }
  `]
})
export class CrearExamenDesdeplanneacionComponent implements OnInit implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private apiService = inject(ApiService);
  private messageService = inject(MessageService);
  private fb = inject(FormBuilder);

  // Formularios
  step1Form: FormGroup;
  examenForm: FormGroup;

  // Datos
  grupos = signal<any[]>([]);
  planeaciones = signal<any[]>([]);
  planeacionDetalle = signal<any>(null);

  // Estado
  planeacionesLoaded = signal(false);
  planeacionSeleccionada: string | null = null;
  guardando = signal(false);

  constructor() {
    this.step1Form = this.fb.group({
      grupoId: [null, Validators.required]
    });

    this.examenForm = this.fb.group({
      nombreEvaluacion: ['', [Validators.required, Validators.minLength(3)]],
      descripcion: ['', Validators.required],
      fecha: [null, Validators.required],
      puntajeMaximo: [10, [Validators.required, Validators.min(0)]]
    });
  }

  ngOnInit() {
    this.loadGrupos();
  }

  loadGrupos() {
    this.apiService.get('/api/v1/grupos').subscribe(res => {
      this.grupos.set((res as any[]).map(g => ({
        label: g.nombre_grupo,
        value: g.ref
      })));
    });
  }

  onGrupoChange() {
    const grupoId = this.step1Form.get('grupoId')?.value;
    if (!grupoId) return;

    this.planeacionesLoaded.set(false);
    this.planeacionSeleccionada = null;
    this.planeacionDetalle.set(null);

    this.apiService.get(`/api/v1/planeacion/grupo/${grupoId}/planeaciones-activas`)
      .subscribe(
        res => {
          this.planeaciones.set(res as any[]);
          this.planeacionesLoaded.set(true);
          this.messageService.add({
            severity: 'info',
            summary: 'Planeaciones cargadas',
            detail: `${(res as any[]).length} planeaciones disponibles`
          });
        },
        err => {
          this.messageService.add({
            severity: 'error',
            summary: 'Error',
            detail: 'No se pudieron cargar las planeaciones'
          });
        }
      );
  }

  onPlaneacionSelect(planeacion: any) {
    this.planeacionSeleccionada = planeacion.planeacion_id;

    this.apiService.get(`/api/v1/planeacion/${planeacion.planeacion_id}/detalles`)
      .subscribe(
        res => {
          this.planeacionDetalle.set(res as any);
        },
        err => {
          this.messageService.add({
            severity: 'error',
            summary: 'Error',
            detail: 'No se pudo cargar los detalles de la planeación'
          });
        }
      );
  }

  getModalidadSeverity(modalidad: string): string {
    switch (modalidad) {
      case 'PRESENCIAL': return 'success';
      case 'VIRTUAL': return 'info';
      case 'HIBRIDA': return 'warning';
      default: return 'secondary';
    }
  }

  guardarExamen() {
    if (!this.examenForm.valid || !this.planeacionSeleccionada) return;

    const body = {
      planeacion_clase_id: this.planeacionSeleccionada,
      nombre_evaluacion: this.examenForm.get('nombreEvaluacion')!.value,
      descripcion: this.examenForm.get('descripcion')!.value,
      fecha: this.examenForm.get('fecha')!.value,
      puntaje_maximo: this.examenForm.get('puntajeMaximo')!.value
    };

    this.guardando.set(true);
    this.apiService.post('/api/v1/planeacion/examenes/desde-planeacion', body)
      .subscribe(
        res => {
          this.messageService.add({
            severity: 'success',
            summary: '✅ Examen guardado',
            detail: `Aprendizajes esperados heredados automáticamente`
          });
          this.guardando.set(false);
          // Reset forms
          this.examenForm.reset({ puntajeMaximo: 10 });
          this.planeacionSeleccionada = null;
          this.planeacionDetalle.set(null);
        },
        err => {
          this.messageService.add({
            severity: 'error',
            summary: '❌ Error',
            detail: err.error?.message || 'No se pudo guardar el examen'
          });
          this.guardando.set(false);
        }
      );
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
