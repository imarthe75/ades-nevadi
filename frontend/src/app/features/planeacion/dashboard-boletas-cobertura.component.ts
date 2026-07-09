import { Component, OnDestroy, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { CardModule } from 'primeng/card';
import { ToastModule } from 'primeng/toast';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import { ChartModule } from 'primeng/chart';

import { ApiService } from '../../core/services/api.service';

/**
 * Dashboard: Boletas y Cobertura Curricular
 *
 * FASE 4: Visualiza:
 * 1. Boleta calculada de un alumno (notas por competencia)
 * 2. Cobertura curricular del grupo (% aprendizajes planeados vs evaluados)
 * 3. Desglose por competencia
 */
@Component({
  selector: 'app-dashboard-boletas-cobertura',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    ButtonModule, SelectModule, TableModule, TagModule, CardModule,
    ToastModule, MessageModule, ChartModule
  ],
  providers: [MessageService],
  template: `
    <div class="page-header">
      <div>
        <h2 class="page-title">📈 Boletas y Cobertura Curricular</h2>
        <p class="page-subtitle">Visualiza calificaciones y estadísticas de aprendizajes</p>
      </div>
    </div>

    <p-toast />

    <!-- SECCIÓN 1: Ver Boleta de un Alumno -->
    <div class="card form-section">
      <h3>Boleta de Alumno</h3>

      <div class="field-grid">
        <div class="field">
          <label>Grupo*</label>
          <p-select [options]="grupos()" [(ngModel)]="selGrupoId"
                   optionLabel="label" optionValue="value"
                   placeholder="Seleccionar grupo..."
                   (onChange)="onGrupoChange()"
                   [filter]="true" filterPlaceholder="Buscar..."/>
        </div>

        <div class="field">
          <label>Alumno*</label>
          <p-select [options]="alumnos()" [(ngModel)]="selAlumnoId"
                   optionLabel="label" optionValue="value"
                   placeholder="Seleccionar alumno..."
                   [filter]="true" filterPlaceholder="Buscar..."/>
        </div>

        <div class="field">
          <label>Trimestre*</label>
          <p-select [options]="trimestres" [(ngModel)]="selTrimestre"
                   placeholder="Seleccionar trimestre..."/>
        </div>

        <div class="field">
          <p-button label="📊 Cargar Boleta"
                   (onClick)="cargarBoleta()"
                   [disabled]="!selGrupoId || !selAlumnoId || !selTrimestre"
                   icon="pi pi-search"/>
        </div>
      </div>

      <!-- Boleta Resultado -->
      @if (boleta()) {
        <div class="boleta-card mt-3">
          <div class="boleta-header">
            <h4>Boleta T{{selTrimestre}}</h4>
            <div class="nota-final">
              <strong>Nota Final:</strong>
              <span class="nota-value">{{boleta()?.nota_final | number:'1.1-1'}}</span>
            </div>
          </div>

          <!-- Calificaciones por competencia -->
          @if ((boleta()?.calificaciones_por_competencia || []).length > 0) {
            <div class="competencias-grid mt-3">
              @for (comp of boleta()?.calificaciones_por_competencia; track comp.competencia_id) {
                <div class="competencia-card">
                  <h5>{{comp.competencia_nombre}}</h5>
                  <div class="promedio-competencia">
                    {{comp.promedio | number:'1.1-1'}}/10
                  </div>
                  <p class="codigo">{{comp.competencia_codigo}}</p>
                </div>
              }
            </div>
          }

          <!-- Trazabilidad -->
          @if (boleta()?.trazabilidad) {
            <div class="trazabilidad mt-3">
              <h5>Trazabilidad</h5>
              <ul class="trazabilidad-list">
                <li><strong>Planeaciones del período:</strong> {{boleta()?.trazabilidad?.planeaciones_del_periodo}}</li>
                <li><strong>Aprendizajes evaluados:</strong> {{boleta()?.trazabilidad?.aprendizajes_evaluados}}</li>
                <li><strong>Generada:</strong> {{boleta()?.trazabilidad?.timestamp | date:'short'}}</li>
              </ul>
            </div>
          }
        </div>
      }
    </div>

    <!-- SECCIÓN 2: Cobertura Curricular del Grupo -->
    <div class="card form-section">
      <h3>Cobertura Curricular del Grupo</h3>

      <div class="field-grid">
        <div class="field">
          <label>Grupo*</label>
          <p-select [options]="grupos()" [(ngModel)]="selGrupoCobertura"
                   optionLabel="label" optionValue="value"
                   placeholder="Seleccionar grupo..."
                   (onChange)="onGrupoCoberturaChange()"
                   [filter]="true" filterPlaceholder="Buscar..."/>
        </div>

        <div class="field">
          <label>Trimestre*</label>
          <p-select [options]="trimestres" [(ngModel)]="selTrimestreCobertura"
                   placeholder="Seleccionar trimestre..."
                   (onChange)="cargarCobertura()"/>
        </div>

        <div class="field">
          <p-button label="📊 Cargar Cobertura"
                   (onClick)="cargarCobertura()"
                   [disabled]="!selGrupoCobertura || !selTrimestreCobertura"
                   icon="pi pi-search"/>
        </div>
      </div>

      <!-- Estadísticas Generales -->
      @if (cobertura()) {
        <div class="stats-grid mt-3">
          <div class="stat-card">
            <div class="stat-label">Planeaciones</div>
            <div class="stat-value">{{cobertura()?.planeaciones_total}}</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">Aprendizajes Planificados</div>
            <div class="stat-value">{{cobertura()?.aprendizajes_planificados}}</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">Aprendizajes Evaluados</div>
            <div class="stat-value">{{cobertura()?.aprendizajes_evaluados}}</div>
          </div>
          <div class="stat-card" [style.background]="getColorCobertura()">
            <div class="stat-label">% Cobertura</div>
            <div class="stat-value">{{cobertura()?.porcentaje_cobertura}}%</div>
          </div>
        </div>

        <!-- Desglose por Competencia -->
        @if ((coberturaCompetencia() || []).length > 0) {
          <div class="mt-3">
            <h4>Cobertura por Competencia</h4>
            <p-table [value]="coberturaCompetencia()" [paginator]="true" [rows]="5"
                    responsiveLayout="scroll" styleClass="mt-3">
              <ng-template pTemplate="header">
                <tr>
                  <th>Competencia</th>
                  <th>Materia</th>
                  <th>Planificados</th>
                  <th>Evaluados</th>
                  <th>% Cobertura</th>
                </tr>
              </ng-template>
              <ng-template pTemplate="body" let-comp>
                <tr>
                  <td>
                    <strong>{{comp.competencia_nombre}}</strong>
                    <p class="codigo">{{comp.competencia_codigo}}</p>
                  </td>
                  <td>{{comp.nombre_materia}}</td>
                  <td>{{comp.aprendizajes_planificados}}</td>
                  <td>{{comp.aprendizajes_evaluados}}</td>
                  <td>
                    <p-tag [value]="comp.porcentaje_cobertura + '%'"
                          [severity]="getTagSeverity(comp.porcentaje_cobertura)"/>
                  </td>
                </tr>
              </ng-template>
            </p-table>
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .form-section {
      margin-top: 1.5rem;
    }

    .field-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
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

    .boleta-card {
      background: var(--surface-section);
      border: 1px solid var(--surface-border);
      border-radius: 6px;
      padding: 1.5rem;
    }

    .boleta-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1rem;
      padding-bottom: 1rem;
      border-bottom: 2px solid var(--primary-color);
    }

    .nota-final {
      display: flex;
      align-items: center;
      gap: 1rem;
    }

    .nota-value {
      background: var(--primary-color);
      color: white;
      padding: 0.5rem 1rem;
      border-radius: 4px;
      font-size: 1.5rem;
      font-weight: bold;
      min-width: 80px;
      text-align: center;
    }

    .competencias-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
      gap: 1rem;
    }

    .competencia-card {
      background: white;
      border: 1px solid var(--primary-color);
      border-radius: 6px;
      padding: 1rem;
      text-align: center;
    }

    .competencia-card h5 {
      margin: 0 0 0.5rem 0;
      font-size: 0.85rem;
      line-height: 1.3;
    }

    .promedio-competencia {
      font-size: 1.5rem;
      font-weight: bold;
      color: var(--primary-color);
      margin: 0.5rem 0;
    }

    .codigo {
      font-size: 0.75rem;
      color: var(--text-color-secondary);
      margin: 0;
    }

    .trazabilidad {
      background: var(--blue-50);
      border-left: 3px solid var(--primary-color);
      padding: 1rem;
      border-radius: 4px;
    }

    .trazabilidad-list {
      margin: 0;
      padding-left: 1.5rem;
      list-style: disc;
    }

    .trazabilidad-list li {
      margin: 0.5rem 0;
      font-size: 0.9rem;
    }

    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
      gap: 1rem;
    }

    .stat-card {
      background: linear-gradient(135deg, var(--primary-color), var(--primary-600));
      color: white;
      padding: 1.5rem;
      border-radius: 6px;
      text-align: center;
    }

    .stat-label {
      font-size: 0.85rem;
      opacity: 0.9;
      margin-bottom: 0.5rem;
    }

    .stat-value {
      font-size: 2rem;
      font-weight: bold;
    }

    .mt-3 { margin-top: 1rem; }

    .codigo {
      font-size: 0.75rem;
      color: var(--text-color-secondary);
    }
  `]
})
export class DashboardBolecasYCoberturaComponent implements OnInit implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private apiService = inject(ApiService);
  private messageService = inject(MessageService);

  // Datos
  grupos = signal<any[]>([]);
  alumnos = signal<any[]>([]);
  boleta = signal<any>(null);
  cobertura = signal<any>(null);
  coberturaCompetencia = signal<any[]>([]);

  // Selectores
  selGrupoId: string | null = null;
  selAlumnoId: string | null = null;
  selTrimestre = 1;

  selGrupoCobertura: string | null = null;
  selTrimestreCobertura = 1;

  // Opciones
  trimestres = [
    { label: 'Trimestre 1', value: 1 },
    { label: 'Trimestre 2', value: 2 },
    { label: 'Trimestre 3', value: 3 }
  ];

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
    const grupoId = this.selGrupoId;
    if (!grupoId) return;

    this.apiService.get(`/api/v1/grupos/${grupoId}`).subscribe(res => {
      const grupo = res as any;
      this.apiService.get(`/api/v1/estudiantes?grupo_id=${grupoId}`).subscribe(alumnos => {
        this.alumnos.set((alumnos as any[]).map(a => ({
          label: a.nombre_alumno,
          value: a.ref
        })));
      });
    });
  }

  onGrupoCoberturaChange() {
    this.cargarCobertura();
  }

  cargarBoleta() {
    if (!this.selGrupoId || !this.selAlumnoId || !this.selTrimestre) return;

    this.apiService.get(
      `/api/v1/planeacion/boleta/${this.selAlumnoId}/${this.selGrupoId}/${this.selTrimestre}`
    ).subscribe(
      res => {
        this.boleta.set(res as any);
        this.messageService.add({
          severity: 'success',
          summary: 'Boleta cargada',
          detail: `Nota final: ${(res as any).nota_final}`
        });
      },
      err => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'No se pudo cargar la boleta'
        });
      }
    );
  }

  cargarCobertura() {
    if (!this.selGrupoCobertura || !this.selTrimestreCobertura) return;

    this.apiService.get(
      `/api/v1/planeacion/cobertura/${this.selGrupoCobertura}/${this.selTrimestreCobertura}`
    ).subscribe(
      res => {
        this.cobertura.set(res as any);
      },
      err => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'No se pudo cargar la cobertura'
        });
      }
    );

    this.apiService.get(
      `/api/v1/planeacion/cobertura-por-competencia/${this.selGrupoCobertura}/${this.selTrimestreCobertura}`
    ).subscribe(
      res => {
        this.coberturaCompetencia.set(res as any[]);
      }
    );
  }

  getColorCobertura(): string {
    const pct = this.cobertura()?.porcentaje_cobertura || 0;
    if (pct >= 80) return 'linear-gradient(135deg, #28a745, #20c997)';
    if (pct >= 60) return 'linear-gradient(135deg, #ffc107, #ff9800)';
    return 'linear-gradient(135deg, #dc3545, #e74c3c)';
  }

  getTagSeverity(pct: number): string {
    if (pct >= 80) return 'success';
    if (pct >= 60) return 'warning';
    return 'danger';
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
