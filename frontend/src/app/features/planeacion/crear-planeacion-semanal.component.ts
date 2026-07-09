import { Component, OnDestroy, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { CheckboxModule } from 'primeng/checkbox';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { CalendarModule } from 'primeng/calendar';
import { DialogModule } from 'primeng/dialog';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { MessageModule } from 'primeng/message';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';

/**
 * Crear Planeación Semanal Integral
 *
 * FASE 2: Permite a los profesores crear planeaciones semanales vinculadas a:
 * - Temario (temas) de la materia/grado
 * - Aprendizajes esperados que cubre cada tema
 *
 * Flujo:
 * 1. Seleccionar grupo, materia, trimestre/semana, modalidad
 * 2. Cargar temario y aprendizajes disponibles
 * 3. Seleccionar temas + vincular aprendizajes a cada uno
 * 4. Guardar planeación semanal completa (crea múltiples registros internos)
 */
@Component({
  selector: 'app-crear-planeacion-semanal',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule,
    ButtonModule, SelectModule, CheckboxModule, InputTextModule, TextareaModule,
    CalendarModule, DialogModule, CardModule, TagModule, ToastModule, MessageModule
  ],
  providers: [MessageService],
  template: `
    <div class="page-header">
      <div>
        <h2 class="page-title">📋 Crear Planeación Semanal</h2>
        <p class="page-subtitle">Vincular temario y aprendizajes esperados</p>
      </div>
    </div>

    <p-toast />

    <!-- PASO 1: Seleccionar Contexto (Grupo, Materia, Semana, Modalidad) -->
    <div class="card form-section">
      <h3>Paso 1: Contexto de la Planeación</h3>
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

          <div class="field">
            <label>Materia*</label>
            <p-select [options]="materias()" formControlName="materiaId"
                     optionLabel="label" optionValue="value"
                     placeholder="Seleccionar materia..."
                     (onChange)="onMateriaChange()"
                     [filter]="true" filterPlaceholder="Buscar..."/>
          </div>

          <div class="field">
            <label>Trimestre*</label>
            <p-select [options]="trimestres" formControlName="trimestre"
                     placeholder="Seleccionar trimestre..."/>
          </div>

          <div class="field">
            <label>Semana*</label>
            <input pInputText type="number" formControlName="semana"
                   min="1" max="40" placeholder="1-40"/>
          </div>

          <div class="field">
            <label>Modalidad*</label>
            <p-select [options]="modalidades" formControlName="modalidad"
                     placeholder="Seleccionar modalidad..."/>
          </div>

          <div class="field">
            <label>Fecha Inicio*</label>
            <p-calendar formControlName="fechaInicio" dateFormat="dd/mm/yy" [showIcon]="true"/>
          </div>

          <div class="field">
            <label>Fecha Fin*</label>
            <p-calendar formControlName="fechaFin" dateFormat="dd/mm/yy" [showIcon]="true"/>
          </div>
        </div>

        <p-button label="▶ Cargar Temario y Aprendizajes"
                 (onClick)="cargarTemario()"
                 [disabled]="!step1Form.valid"
                 styleClass="mt-3"/>
      </form>
    </div>

    <!-- PASO 2: Seleccionar Temas y Vinculados a Aprendizajes -->
    @if (temarioLoaded()) {
      <div class="card form-section">
        <h3>Paso 2: Seleccionar Temas y Aprendizajes Esperados</h3>

        @if ((temas() || []).length === 0) {
          <p-message severity="warn" text="No hay temas disponibles para esta materia/grado"/>
        } @else {
          <div class="temas-container">
            @for (tema of temas(); track tema.tema_id) {
              <div class="tema-card">
                <div class="tema-header">
                  <input type="checkbox" [value]="tema.tema_id"
                         (change)="onTemaToggle($event, tema)"/>
                  <strong>{{tema.nombre_tema}}</strong>
                  <span class="tema-orden">{{tema.orden}}</span>
                </div>

                <p class="tema-desc">{{tema.descripcion}}</p>

                <!-- Aprendizajes para este tema -->
                @if (temaSelecionado() === tema.tema_id) {
                  <div class="aprendizajes-list">
                    <p class="subtitle">Aprendizajes esperados a cubrir:</p>
                    @for (ae of getAprendizajesForTema(tema); track ae.aprendizaje_id) {
                      <div class="aprendizaje-item">
                        <input type="checkbox" [value]="ae.aprendizaje_id"
                               (change)="onAprendizajeToggle($event, tema.tema_id, ae)"/>
                        <span>
                          <strong>{{ae.codigo}}</strong> — {{ae.descripcion}}
                          @if (ae.competencia_nombre) {
                            <p-tag [value]="ae.competencia_nombre" severity="info"
                                   styleClass="ml-2"/>
                          }
                        </span>
                      </div>
                    }
                  </div>
                }
              </div>
            }
          </div>
        }

        <!-- Resumen de selección -->
        <div class="resumen-card mt-4">
          <h4>📊 Resumen de Selección</h4>
          <div class="resumen-stats">
            <div class="stat">
              <strong>Temas seleccionados:</strong> {{temasSeleccionados().size}}
            </div>
            <div class="stat">
              <strong>Aprendizajes seleccionados:</strong> {{aprendizajesSeleccionados().size}}
            </div>
          </div>

          @if (temasSeleccionados().size > 0) {
            <div class="mt-3">
              <p-button label="✅ Guardar Planeación Semanal"
                       (onClick)="guardarPlaneacion()"
                       [loading]="guardando()"
                       icon="pi pi-save"/>
            </div>
          }
        </div>
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

    .temas-container {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
      gap: 1rem;
      margin-top: 1rem;
    }

    .tema-card {
      border: 1px solid var(--surface-border);
      border-radius: 6px;
      padding: 1rem;
      background: var(--surface-section);
      transition: all 0.3s ease;
    }

    .tema-card:hover {
      border-color: var(--primary-color);
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    }

    .tema-header {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      margin-bottom: 0.5rem;
    }

    .tema-header input[type="checkbox"] {
      cursor: pointer;
      width: 18px;
      height: 18px;
    }

    .tema-orden {
      background: var(--primary-color);
      color: white;
      padding: 0.2rem 0.6rem;
      border-radius: 12px;
      font-size: 0.75rem;
      font-weight: 600;
      margin-left: auto;
    }

    .tema-desc {
      font-size: 0.9rem;
      color: var(--text-color-secondary);
      margin: 0.5rem 0;
    }

    .aprendizajes-list {
      margin-top: 1rem;
      padding-top: 1rem;
      border-top: 1px solid var(--surface-border);
    }

    .aprendizajes-list .subtitle {
      font-weight: 600;
      font-size: 0.85rem;
      margin-bottom: 0.5rem;
    }

    .aprendizaje-item {
      display: flex;
      align-items: flex-start;
      gap: 0.5rem;
      margin-bottom: 0.5rem;
      font-size: 0.85rem;
    }

    .aprendizaje-item input[type="checkbox"] {
      cursor: pointer;
      margin-top: 0.25rem;
      flex-shrink: 0;
    }

    .resumen-card {
      background: var(--blue-50);
      border: 1px solid var(--blue-200);
      border-radius: 6px;
      padding: 1rem;
    }

    .resumen-stats {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 1rem;
      margin-top: 0.75rem;
    }

    .stat {
      padding: 0.75rem;
      background: white;
      border-radius: 4px;
      border-left: 3px solid var(--primary-color);
    }

    .mt-3 { margin-top: 1rem; }
    .mt-4 { margin-top: 1.5rem; }
    .ml-2 { margin-left: 0.5rem; }
  `]
})
export class CrearPlaneacionSemanalComponent implements OnInit implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private apiService = inject(ApiService);
  private contextService = inject(ContextService);
  private messageService = inject(MessageService);
  private fb = inject(FormBuilder);

  // Formulario PASO 1: Contexto
  step1Form: FormGroup;

  // Datos
  grupos = signal<any[]>([]);
  materias = signal<any[]>([]);
  temas = signal<any[]>([]);
  aprendizajes = signal<any[]>([]);

  // Estado
  temarioLoaded = signal(false);
  temaSelecionado = signal<string | null>(null);
  temasSeleccionados = signal(new Map<string, any>());
  aprendizajesSeleccionados = signal(new Set<string>());
  guardando = signal(false);

  // Opciones
  trimestres = [
    { label: 'Trimestre 1', value: 1 },
    { label: 'Trimestre 2', value: 2 },
    { label: 'Trimestre 3', value: 3 }
  ];

  modalidades = [
    { label: 'Presencial', value: 'PRESENCIAL' },
    { label: 'Virtual', value: 'VIRTUAL' },
    { label: 'Híbrida', value: 'HIBRIDA' }
  ];

  constructor() {
    this.step1Form = this.fb.group({
      grupoId: [null, Validators.required],
      materiaId: [null, Validators.required],
      trimestre: [1, Validators.required],
      semana: [null, [Validators.required, Validators.min(1), Validators.max(40)]],
      modalidad: ['PRESENCIAL', Validators.required],
      fechaInicio: [null, Validators.required],
      fechaFin: [null, Validators.required]
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

    this.apiService.get(`/api/v1/grupos/${grupoId}`).subscribe(res => {
      const grupo = res as any;
      this.apiService.get(`/api/v1/materias?grado_id=${grupo.grado_id}`).subscribe(materias => {
        this.materias.set((materias as any[]).map(m => ({
          label: m.nombre_materia,
          value: m.ref
        })));
      });
    });
  }

  onMateriaChange() {
    // Podría cargar más información
  }

  cargarTemario() {
    const { grupoId, materiaId } = this.step1Form.value;
    if (!grupoId || !materiaId) return;

    // Obtener grado del grupo
    this.apiService.get(`/api/v1/grupos/${grupoId}`).subscribe(res => {
      const grupo = res as any;
      this.apiService.get(
        `/api/v1/planeacion/temario-aprendizajes?materia_id=${materiaId}&grado_id=${grupo.grado_id}`
      ).subscribe(res => {
        const data = res as any;
        this.temas.set(data.temas || []);
        this.aprendizajes.set(data.aprendizajes || []);
        this.temarioLoaded.set(true);
        this.messageService.add({
          severity: 'success',
          summary: 'Temario cargado',
          detail: `${data.cantidad_temas} temas, ${data.cantidad_aprendizajes} aprendizajes`
        });
      });
    });
  }

  onTemaToggle(event: any, tema: any) {
    const checked = event.target.checked;
    if (checked) {
      this.temasSeleccionados().set(tema.tema_id, tema);
      this.temaSelecionado.set(tema.tema_id);
    } else {
      this.temasSeleccionados().delete(tema.tema_id);
      if (this.temaSelecionado() === tema.tema_id) {
        this.temaSelecionado.set(null);
      }
    }
  }

  onAprendizajeToggle(event: any, temaId: string, ae: any) {
    const checked = event.target.checked;
    if (checked) {
      this.aprendizajesSeleccionados().add(ae.aprendizaje_id);
      const tema = this.temasSeleccionados().get(temaId);
      if (tema) {
        if (!tema.aprendizajes_ids) tema.aprendizajes_ids = [];
        tema.aprendizajes_ids.push(ae.aprendizaje_id);
      }
    } else {
      this.aprendizajesSeleccionados().delete(ae.aprendizaje_id);
      const tema = this.temasSeleccionados().get(temaId);
      if (tema && tema.aprendizajes_ids) {
        tema.aprendizajes_ids = tema.aprendizajes_ids.filter((id: string) => id !== ae.aprendizaje_id);
      }
    }
  }

  getAprendizajesForTema(tema: any): any[] {
    return this.aprendizajes().filter(ae =>
      ae.materia_id === tema.materia_id
    );
  }

  guardarPlaneacion() {
    const { grupoId, materiaId, trimestre, semana, modalidad, fechaInicio, fechaFin } = this.step1Form.value;

    const temasSeleccionados = Array.from(this.temasSeleccionados().values()).map(t => ({
      tema_id: t.tema_id,
      aprendizajes_ids: t.aprendizajes_ids || []
    }));

    const body = {
      grupo_id: grupoId,
      materia_id: materiaId,
      trimestre,
      semana,
      modalidad,
      fecha_inicio: fechaInicio,
      fecha_fin: fechaFin,
      temas_seleccionados: temasSeleccionados
    };

    this.guardando.set(true);
    this.apiService.post('/api/v1/planeacion/semanal-integral', body).subscribe(
      res => {
        this.messageService.add({
          severity: 'success',
          summary: '✅ Planeación guardada',
          detail: `${(res as any).planeaciones_creadas} planeaciones creadas`
        });
        this.guardando.set(false);
        // Limpiar form
        this.step1Form.reset();
        this.temarioLoaded.set(false);
        this.temasSeleccionados().clear();
        this.aprendizajesSeleccionados().clear();
      },
      err => {
        this.messageService.add({
          severity: 'error',
          summary: '❌ Error',
          detail: err.error?.message || 'No se pudo guardar'
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
