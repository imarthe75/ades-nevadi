import { Component, OnDestroy, inject, OnInit, signal, computed, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import { CardModule } from 'primeng/card';

import { ApiService } from '../../core/services/api.service';

/**
 * Calificar Tareas desde Planeación
 *
 * FASE 4: Permite a profesores calificar tareas con visibilidad de aprendizajes esperados.
 *
 * Flujo:
 * 1. Seleccionar grupo
 * 2. Cargar tareas pendientes de calificar
 * 3. Seleccionar una tarea → carga entregas + aprendizajes
 * 4. Calificar múltiples entregas (tabla interactiva)
 * 5. Guardar todas las calificaciones (batch)
 */
@Component({
  selector: 'app-calificar-tareas',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule,
    ButtonModule, SelectModule, TableModule, InputNumberModule, TextareaModule,
    TagModule, ToastModule, MessageModule, CardModule
  ],
  providers: [MessageService],
  template: `
    <div class="page-header">
      <div>
        <h2 class="page-title">📊 Calificar Tareas</h2>
        <p class="page-subtitle">Evalúa las entregas de tareas vinculadas a planeación</p>
      </div>
    </div>

    <p-toast />

    <!-- PASO 1: Seleccionar Grupo -->
    <div class="card form-section">
      <h3>Paso 1: Seleccionar Grupo</h3>
      <form [formGroup]="step1Form">
        <div class="field">
          <label>Grupo*</label>
          <p-select [options]="grupos()" formControlName="grupoId"
                   optionLabel="label" optionValue="value"
                   placeholder="Seleccionar grupo..."
                   (onChange)="onGrupoChange()"
                   [filter]="true" filterPlaceholder="Buscar..."/>
        </div>
      </form>
    </div>

    <!-- PASO 2: Seleccionar Tarea -->
    @if (tareasLoaded()) {
      <div class="card form-section">
        <h3>Paso 2: Seleccionar Tarea</h3>

        @if ((tareas() || []).length === 0) {
          <p-message severity="info" text="No hay tareas pendientes de calificar en este grupo"/>
        } @else {
          <p-table [value]="tareas()" [paginator]="true" [rows]="5"
                  responsiveLayout="scroll" styleClass="mt-3">
            <ng-template pTemplate="header">
              <tr>
                <th>Seleccionar</th>
                <th>Tarea</th>
                <th>Materia</th>
                <th>Fecha Entrega</th>
                <th>Entregas</th>
                <th>Sin Calificar</th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-tarea>
              <tr>
                <td>
                  <input type="radio" [value]="tarea.tarea_id"
                         [(ngModel)]="tareaSeleccionada"
                         (change)="onTareaSelect(tarea)"/>
                </td>
                <td><strong>{{tarea.nombre_tarea}}</strong></td>
                <td>{{tarea.nombre_materia}}</td>
                <td>{{tarea.fecha_entrega | date}}</td>
                <td>{{tarea.total_entregas}}</td>
                <td>
                  <p-tag [value]="tarea.entregas_sin_calificar | string"
                        severity="warning"/>
                </td>
              </tr>
            </ng-template>
          </p-table>
        }
      </div>
    }

    <!-- PASO 3: Detalles de Tarea + Aprendizajes -->
    @if (tareaDetalle()) {
      <div class="card form-section">
        <h3>Paso 3: Detalles de la Tarea</h3>

        <div class="detalle-cards">
          <div class="detalle-item">
            <strong>Título:</strong> {{tareaDetalle()?.titulo}}
          </div>
          <div class="detalle-item">
            <strong>Puntaje Máximo:</strong> {{tareaDetalle()?.puntaje_maximo}}
          </div>
          <div class="detalle-item">
            <strong>Fecha Entrega:</strong> {{tareaDetalle()?.fecha_entrega | date}}
          </div>
        </div>

        <!-- Aprendizajes esperados -->
        @if ((tareaDetalle()?.aprendizajes || []).length > 0) {
          <div class="aprendizajes-section mt-3">
            <h4>Aprendizajes Esperados</h4>
            <div class="aprendizajes-grid">
              @for (ae of tareaDetalle()?.aprendizajes; track ae.aprendizaje_id) {
                <div class="aprendizaje-badge">
                  <strong>{{ae.codigo}}</strong> — {{ae.descripcion}}
                  @if (ae.competencia_nombre) {
                    <p-tag [value]="ae.competencia_nombre" severity="info" styleClass="ml-2"/>
                  }
                </div>
              }
            </div>
          </div>
        }
      </div>
    }

    <!-- PASO 4: Tabla de Entregas para Calificar -->
    @if (tareaSeleccionada && tareaDetalle()?.entregas) {
      <div class="card form-section">
        <h3>Paso 4: Calificar Entregas</h3>

        <p-table [value]="tareaDetalle()?.entregas" [paginator]="true" [rows]="10"
                responsiveLayout="scroll" styleClass="mt-3"
                [rowsPerPageOptions]="[5, 10, 20]">
          <ng-template pTemplate="header">
            <tr>
              <th>Alumno</th>
              <th>Fecha Entrega</th>
              <th>Es Tarde</th>
              <th>Calificación</th>
              <th>Comentarios</th>
              <th>Estado</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-entrega>
            <tr>
              <td><strong>{{entrega.nombre_alumno}}</strong></td>
              <td>{{entrega.fecha_entrega_alumno | date}}</td>
              <td>
                <p-tag [value]="entrega.es_tarde ? 'Sí' : 'No'"
                      [severity]="entrega.es_tarde ? 'warning' : 'success'"/>
              </td>
              <td>
                <input pInputNumber type="number" [(ngModel)]="entrega.calificacion"
                       min="0" max="10" [step]="0.5"
                       [disabled]="entrega.calificacion_id !== null && !editando(entrega.entrega_id)"
                       placeholder="0-10" class="w-100"/>
              </td>
              <td>
                <textarea pTextarea [(ngModel)]="entrega.comentarios"
                         [disabled]="entrega.calificacion_id !== null && !editando(entrega.entrega_id)"
                         rows="2" class="w-100" placeholder="Comentarios (opcional)"/>
              </td>
              <td>
                <p-tag [value]="entrega.calificacion_id ? '✓ Calificado' : '⏳ Pendiente'"
                      [severity]="entrega.calificacion_id ? 'success' : 'info'"/>
              </td>
            </tr>
          </ng-template>
        </p-table>

        <div class="mt-4">
          <p-button label="💾 Guardar Todas las Calificaciones"
                   (onClick)="guardarCalificaciones()"
                   [loading]="guardando()"
                   icon="pi pi-save"/>
        </div>
      </div>
    }
  `,
  styles: [`
    .form-section {
      margin-top: 1.5rem;
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
      line-height: 1.4;
    }

    .w-100 {
      width: 100%;
      min-width: 100px;
    }

    .mt-3 { margin-top: 1rem; }
    .mt-4 { margin-top: 1.5rem; }
    .ml-2 { margin-left: 0.5rem; }
  `]
})
export class CalificarTareasComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private apiService = inject(ApiService);
  private messageService = inject(MessageService);
  private fb = inject(FormBuilder);

  // Formulario
  step1Form: FormGroup;

  // Datos
  grupos = signal<any[]>([]);
  tareas = signal<any[]>([]);
  tareaDetalle = signal<any>(null);

  // Estado
  tareasLoaded = signal(false);
  tareaSeleccionada: string | null = null;
  guardando = signal(false);

  // Set para rastrear qué entregas están en edición
  editandoSet = signal(new Set<string>());

  editando = (entregaId: string) => this.editandoSet().has(entregaId);

  constructor() {
    this.step1Form = this.fb.group({
      grupoId: [null, Validators.required]
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

    this.tareasLoaded.set(false);
    this.tareaSeleccionada = null;
    this.tareaDetalle.set(null);

    this.apiService.get(`/api/v1/planeacion/grupo/${grupoId}/tareas-pendientes-calificar`)
      .subscribe(
        res => {
          this.tareas.set(res as any[]);
          this.tareasLoaded.set(true);
          this.messageService.add({
            severity: 'info',
            summary: 'Tareas cargadas',
            detail: `${(res as any[]).length} tareas con entregas pendientes`
          });
        },
        err => {
          this.messageService.add({
            severity: 'error',
            summary: 'Error',
            detail: 'No se pudieron cargar las tareas'
          });
        }
      );
  }

  onTareaSelect(tarea: any) {
    this.tareaSeleccionada = tarea.tarea_id;

    this.apiService.get(`/api/v1/planeacion/tareas/${tarea.tarea_id}/detalles`)
      .subscribe(
        res => {
          this.tareaDetalle.set(res as any);
        },
        err => {
          this.messageService.add({
            severity: 'error',
            summary: 'Error',
            detail: 'No se pudo cargar los detalles de la tarea'
          });
        }
      );
  }

  guardarCalificaciones() {
    if (!this.tareaSeleccionada || !this.tareaDetalle()?.entregas) return;

    const calificaciones = (this.tareaDetalle()?.entregas || [])
      .filter((e: any) => e.calificacion !== null && e.calificacion !== undefined)
      .map((e: any) => ({
        alumno_id: e.alumno_id,
        calificacion: e.calificacion,
        comentarios: e.comentarios || ''
      }));

    if (calificaciones.length === 0) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Aviso',
        detail: 'No hay calificaciones para guardar'
      });
      return;
    }

    const body = {
      tarea_id: this.tareaSeleccionada,
      calificaciones
    };

    this.guardando.set(true);
    this.apiService.post('/api/v1/planeacion/calificaciones/tarea-batch', body)
      .subscribe(
        res => {
          this.messageService.add({
            severity: 'success',
            summary: '✅ Calificaciones guardadas',
            detail: `${(res as any).registros_guardados} de ${(res as any).total_intentos} calificaciones`
          });
          this.guardando.set(false);
          // Recargar tareas
          this.onGrupoChange();
        },
        err => {
          this.messageService.add({
            severity: 'error',
            summary: '❌ Error',
            detail: err.error?.message || 'No se pudieron guardar las calificaciones'
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
