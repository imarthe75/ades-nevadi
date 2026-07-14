import { Component, OnDestroy, inject, OnInit, signal, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { DatePickerModule } from 'primeng/datepicker';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import { TableModule } from 'primeng/table';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';

/**
 * Crear Tarea Vinculada a Planeación Semanal
 *
 * FASE 3: Permite a profesores crear tareas que heredan automáticamente:
 * - planeacion_clase_id (de la selección)
 * - grupo_id, materia_id (de la planeación)
 * - aprendizajes_esperados[] (via trigger BEFORE INSERT)
 *
 * Flujo:
 * 1. Seleccionar grupo
 * 2. Cargar planeaciones activas del grupo
 * 3. Seleccionar una planeación → carga detalles + aprendizajes vinculados
 * 4. Completar datos de tarea (título, descripción, fecha entrega, puntaje)
 * 5. Guardar → POST /tareas/desde-planeacion
 * 6. Tarea hereda aprendizajes automáticamente
 */
@Component({
  selector: 'app-crear-tarea-desde-planeacion',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule, ReactiveFormsModule,
    ButtonModule, SelectModule, InputTextModule, TextareaModule,
    DatePickerModule, CardModule, TagModule, ToastModule, MessageModule, TableModule
  ],
  providers: [MessageService],
  template: `
    <div class="page-header">
      <div>
        <h2 class="page-title">✏️ Crear Tarea desde Planeación</h2>
        <p class="page-subtitle">La tarea heredará automáticamente los aprendizajes esperados</p>
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
        <h3>Paso 2: Detalles de la Planeación</h3>

        <div class="detalle-cards">
          <div class="detalle-item">
            <strong>Tema:</strong> {{planeacionDetalle()?.nombre_tema}}
          </div>
          <div class="detalle-item">
            <strong>Materia:</strong> {{planeacionDetalle()?.nombre_materia}}
          </div>
          <div class="detalle-item">
            <strong>Semana:</strong> T{{planeacionDetalle()?.numero_trimestre}}-S{{planeacionDetalle()?.numero_semana}}
          </div>
          <div class="detalle-item">
            <strong>Fechas:</strong> {{planeacionDetalle()?.fecha_planeada | date}} a {{planeacionDetalle()?.fecha_fin | date}}
          </div>
        </div>

        <!-- Aprendizajes vinculados -->
        @if ((planeacionDetalle()?.aprendizajes || []).length > 0) {
          <div class="aprendizajes-section mt-3">
            <h4>Aprendizajes esperados que evaluará esta tarea</h4>
            <p class="subtitle">Los siguientes aprendizajes se heredarán automáticamente a la tarea:</p>

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

    <!-- PASO 3: Datos de la Tarea -->
    @if (planeacionSeleccionada) {
      <div class="card form-section">
        <h3>Paso 3: Datos de la Tarea</h3>
        <form [formGroup]="tareaForm">
          <div class="field-grid">
            <div class="field">
              <label>Título*</label>
              <input pInputText formControlName="titulo"
                     placeholder="Ej: Ejercicios de suma y resta"/>
            </div>

            <div class="field">
              <label>Descripción*</label>
              <textarea pTextarea formControlName="descripcion"
                       placeholder="Detalles de la tarea"
                       rows="3"></textarea>
            </div>

            <div class="field">
              <label>Fecha de Entrega*</label>
              <p-datepicker formControlName="fechaEntrega" dateFormat="dd/mm/yy" [showIcon]="true"/>
            </div>

            <div class="field">
              <label>Puntaje Máximo</label>
              <input pInputText type="number" formControlName="puntajeMaximo"
                     min="0" step="0.5" placeholder="10"/>
            </div>

            <div class="field">
              <label>
                <input type="checkbox" formControlName="permiteEntregaTarde"/>
                Permite entregas tardías
              </label>
            </div>

            <div class="field">
              <label>URL Instrucciones (opcional)</label>
              <input pInputText formControlName="instruccionesUrl"
                     placeholder="http://..."/>
            </div>
          </div>

          <div class="mt-4">
            <p-button label="✅ Guardar Tarea"
                     (onClick)="guardarTarea()"
                     [loading]="guardando()"
                     [disabled]="!tareaForm.valid"
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

    .field input[type="checkbox"] {
      width: 18px;
      height: 18px;
      cursor: pointer;
      margin-right: 0.5rem;
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

    .detalle-cards {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 1rem;
      margin-top: 1rem;
    }

    .detalle-item {
      background: var(--surface-section);
      padding: 0.75rem;
      border-radius: 4px;
      border-left: 3px solid var(--primary-color);
    }

    .aprendizajes-section {
      background: var(--blue-50);
      border: 1px solid var(--blue-200);
      border-radius: 6px;
      padding: 1rem;
    }

    .subtitle {
      font-size: 0.85rem;
      color: var(--text-color-secondary);
      margin: 0.5rem 0;
    }

    .aprendizajes-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
      gap: 0.75rem;
      margin-top: 0.75rem;
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
export class CrearTareaDesdeplanneacionComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private apiService = inject(ApiService);
  private messageService = inject(MessageService);
  private fb = inject(FormBuilder);

  // Formularios
  step1Form: FormGroup;
  tareaForm: FormGroup;

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

    this.tareaForm = this.fb.group({
      titulo: ['', [Validators.required, Validators.minLength(3)]],
      descripcion: ['', Validators.required],
      fechaEntrega: [null, Validators.required],
      puntajeMaximo: [10, [Validators.required, Validators.min(0)]],
      permiteEntregaTarde: [false],
      instruccionesUrl: ['']
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
          this.messageService.add({
            severity: 'success',
            summary: 'Planeación seleccionada',
            detail: `${(res as any).cantidad_aprendizajes} aprendizajes esperados`
          });
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

  guardarTarea() {
    if (!this.tareaForm.valid || !this.planeacionSeleccionada) return;

    const body = {
      planeacion_clase_id: this.planeacionSeleccionada,
      titulo: this.tareaForm.get('titulo')!.value,
      descripcion: this.tareaForm.get('descripcion')!.value,
      fecha_entrega: this.tareaForm.get('fechaEntrega')!.value,
      puntaje_maximo: this.tareaForm.get('puntajeMaximo')!.value,
      permite_entrega_tarde: this.tareaForm.get('permiteEntregaTarde')!.value,
      instrucciones_url: this.tareaForm.get('instruccionesUrl')!.value || null
    };

    this.guardando.set(true);
    this.apiService.post('/api/v1/planeacion/tareas/desde-planeacion', body)
      .subscribe(
        res => {
          this.messageService.add({
            severity: 'success',
            summary: '✅ Tarea guardada',
            detail: `Aprendizajes esperados heredados automáticamente`
          });
          this.guardando.set(false);
          // Reset forms
          this.tareaForm.reset({ puntajeMaximo: 10, permiteEntregaTarde: false });
          this.planeacionSeleccionada = null;
          this.planeacionDetalle.set(null);
        },
        err => {
          this.messageService.add({
            severity: 'error',
            summary: '❌ Error',
            detail: err.error?.message || 'No se pudo guardar la tarea'
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
