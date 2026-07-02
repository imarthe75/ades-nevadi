import { Component, inject, signal, OnInit, effect, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { TooltipModule } from 'primeng/tooltip';
import { ApexNotificationService } from 'apex-component-library';
import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';

interface IndicadoresCiclo {
  ciclo_escolar_id: string;
  nombre_ciclo: string;
  nivel_educativo_id: string;
  nombre_nivel: string;
  fecha_inicio: string;
  fecha_fin: string;
  estado: 'ACTIVO' | 'CERRADO';
  matricula_total: number;
  total_docentes: number;
  promedio_general: number;
  tasa_aprobacion: number;
  total_bajas: number;
  total_alumnos_activos: number;
}

interface CicloOpt {
  id: string;
  nombre_ciclo: string;
  nombre_nivel?: string;
  _label?: string;
}

interface PlantelOpt {
  id: string;
  nombre_plantel: string;
}

@Component({
  selector: 'app-cierre-ciclo',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ButtonModule, DialogModule,
    SelectModule, SelectButtonModule, ProgressSpinnerModule, TooltipModule
  ],
  template: `
    <div class="page-header">
      <div>
        <h2>Cierre de Ciclo Escolar</h2>
        <p class="subtitle">Indicadores y actas oficiales de inicio/cierre · Instituto Nevadi</p>
      </div>
      @if (cargando()) {
        <p-progressSpinner styleClass="w-2rem h-2rem" strokeWidth="4" />
      }
    </div>

    @if (cicloActivo(); as ciclo) {
      <div class="card card-w-title" style="margin-bottom: 2rem;">
        <div style="display:flex; justify-content:space-between; align-items:center; border-bottom:1px solid var(--border-color); padding-bottom:1rem; margin-bottom:1.5rem;">
          <div>
            <h3 style="margin:0; font-family:'Jost',sans-serif; color:var(--text-primary);">
              Ciclo Seleccionado: <span style="color:var(--color-primary)">{{ ciclo.nombre_ciclo }}</span>
            </h3>
            <p style="margin:.2rem 0 0 0; font-size:.9rem; color:var(--text-secondary)">
              Nivel Académico: {{ nivelNombre() || '—' }}
            </p>
          </div>
          <div>
            @if (indicadores()?.estado === 'CERRADO') {
              <span class="badge badge-danger" style="background-color:#EF4444; color:white; padding:.25rem .75rem; border-radius:4px; font-weight:bold; font-size:.85rem;">
                ESTADO: CERRADO
              </span>
            } @else {
              <span class="badge badge-success" style="background-color:#10B981; color:white; padding:.25rem .75rem; border-radius:4px; font-weight:bold; font-size:.85rem;">
                ESTADO: ACTIVO
              </span>
            }
          </div>
        </div>

        <!-- Selector de alcance: Instituto completo o por plantel -->
        <div style="display:flex; align-items:center; gap:1rem; margin-bottom:1.5rem; flex-wrap:wrap;">
          <span style="font-weight:600; color:var(--text-primary); font-size:.9rem;">Ver indicadores de:</span>
          <p-selectButton
            [options]="alcanceOpciones"
            [ngModel]="alcance()"
            optionLabel="label"
            optionValue="value"
            (ngModelChange)="setAlcance($event)" />

          @if (alcance() === 'plantel') {
            <p-select
              [options]="planteles()"
              optionLabel="nombre_plantel"
              optionValue="id"
              [ngModel]="plantelId()"
              placeholder="Seleccionar plantel"
              styleClass="w-14rem"
              (ngModelChange)="setPlantel($event)" />
          }
        </div>

        @if (indicadores(); as ind) {
          <!-- KPI Cards Grid -->
          <div style="display:grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap:1.5rem; margin-bottom:2rem;">

            <div class="kpi-card" style="background:var(--surface-card); border-left:4px solid var(--color-primary); padding:1.25rem; border-radius:6px; box-shadow:0 2px 4px rgba(0,0,0,0.05)">
              <div style="font-size:.8rem; text-transform:uppercase; font-weight:bold; color:var(--text-muted)">Matrícula Total</div>
              <div style="font-size:1.8rem; font-weight:bold; margin-top:.5rem; color:var(--text-primary)">
                {{ ind.matricula_total }} <span style="font-size:1rem; font-weight:normal; color:var(--text-secondary)">alumnos</span>
              </div>
              <div style="font-size:.75rem; color:var(--text-muted); margin-top:.25rem">
                {{ alcance() === 'plantel' ? plantelNombre() : 'Todo el Instituto' }}
              </div>
            </div>

            <div class="kpi-card" style="background:var(--surface-card); border-left:4px solid var(--color-primary); padding:1.25rem; border-radius:6px; box-shadow:0 2px 4px rgba(0,0,0,0.05)">
              <div style="font-size:.8rem; text-transform:uppercase; font-weight:bold; color:var(--text-muted)">Docentes Activos</div>
              <div style="font-size:1.8rem; font-weight:bold; margin-top:.5rem; color:var(--text-primary)">
                {{ ind.total_docentes }} <span style="font-size:1rem; font-weight:normal; color:var(--text-secondary)">asignados</span>
              </div>
            </div>

            <div class="kpi-card" style="background:var(--surface-card); border-left:4px solid var(--color-primary); padding:1.25rem; border-radius:6px; box-shadow:0 2px 4px rgba(0,0,0,0.05)">
              <div style="font-size:.8rem; text-transform:uppercase; font-weight:bold; color:var(--text-muted)">Promedio General</div>
              <div style="font-size:1.8rem; font-weight:bold; margin-top:.5rem; color:var(--text-primary)">
                {{ ind.promedio_general }}
              </div>
            </div>

            <div class="kpi-card" style="background:var(--surface-card); border-left:4px solid var(--color-primary); padding:1.25rem; border-radius:6px; box-shadow:0 2px 4px rgba(0,0,0,0.05)">
              <div style="font-size:.8rem; text-transform:uppercase; font-weight:bold; color:var(--text-muted)">Tasa de Aprobación</div>
              <div style="font-size:1.8rem; font-weight:bold; margin-top:.5rem; color:var(--text-primary)">
                {{ ind.tasa_aprobacion }}%
              </div>
            </div>

          </div>

          <!-- Acciones de Cierre -->
          <div style="background-color:var(--surface-ground); padding:1.5rem; border-radius:8px; border:1px solid var(--border-color);">
            <h4 style="margin:0 0 1rem 0; font-family:'Jost',sans-serif; color:var(--text-primary)">Documentación y Cierre Formal</h4>
            <div style="display:flex; flex-wrap:wrap; gap:1rem;">
              <p-button label="Descargar Acta de Inicio" icon="pi pi-file-pdf" severity="secondary" [outlined]="true"
                        [loading]="descargandoInicio()" (onClick)="descargarActa('inicio')" />
              <p-button label="Descargar Acta de Cierre" icon="pi pi-file-pdf" severity="secondary" [outlined]="true"
                        [loading]="descargandoCierre()" (onClick)="descargarActa('cierre')" />

              @if (ind.estado !== 'CERRADO') {
                <p-button label="Ejecutar Cierre Definitivo de Calificaciones" icon="pi pi-lock" severity="danger"
                          (onClick)="abrirConfirmarCierre()" />
              } @else {
                <p-button label="Ciclo Cerrado Exitosamente" icon="pi pi-verified" severity="success" [disabled]="true" />
              }
            </div>
          </div>
        } @else {
          <div style="text-align:center; padding:2rem; color:var(--text-secondary)">
            No se pudieron recuperar los indicadores agregados de este ciclo escolar.
          </div>
        }
      </div>
    } @else {
      <div class="card card-w-title" style="text-align:center; padding:3rem; color:var(--text-secondary)">
        <i class="pi pi-calendar-times" style="font-size:3rem; color:var(--text-muted); margin-bottom:1rem;"></i>
        <h3>Selecciona un ciclo escolar</h3>
        <p>Por favor, selecciona un ciclo escolar en la barra superior para gestionar su cierre.</p>
      </div>
    }

    <!-- Dialogo de confirmación de cierre definitivo -->
    <p-dialog header="Confirmar Cierre de Ciclo Escolar" [(visible)]="showConfirmDialog"
              [modal]="true" [style]="{width: '450px'}" [draggable]="false" [resizable]="false">
      <div style="padding:1rem 0;">
        <p style="margin-top:0; color:var(--text-primary); text-align:justify;">
          Al cerrar formalmente el ciclo, se <strong>bloqueará permanentemente</strong> la edición de calificaciones, planeaciones y asistencias asociadas. Esta acción no se puede deshacer.
        </p>
        <p style="color:var(--text-secondary); text-align:justify;">
          Opcionalmente, selecciona el <strong>Ciclo Escolar Destino</strong> para realizar la reinscripción y promoción masiva automática de alumnos egresados/aprobados:
        </p>

        <div style="margin:1.5rem 0;">
          <label for="cicloDestino" style="display:block; font-weight:bold; margin-bottom:.5rem; color:var(--text-primary)">
            Ciclo de Destino (Promoción)
          </label>
          <p-select id="cicloDestino" [options]="ciclosDisponibles()" optionLabel="_label" optionValue="id"
                    [(ngModel)]="cicloDestinoId" placeholder="Seleccionar ciclo de destino (Opcional)"
                    styleClass="w-full" [showClear]="true" />
        </div>
      </div>

      <ng-template pTemplate="footer">
        <p-button label="Cancelar" icon="pi pi-times" severity="secondary" [outlined]="true"
                  (onClick)="showConfirmDialog.set(false)" />
        <p-button label="Confirmar y Cerrar Ciclo" icon="pi pi-lock" severity="danger"
                  [loading]="cerrandoCiclo()" (onClick)="confirmarCierreDefinitivo()" />
      </ng-template>
    </p-dialog>
  `
})
export class CierreCicloComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly ctx = inject(ContextService);
  private readonly notify = inject(ApexNotificationService);
  readonly cicloActivo = this.ctx.ciclo;
  readonly indicadores = signal<IndicadoresCiclo | null>(null);
  readonly ciclosDisponibles = signal<CicloOpt[]>([]);
  readonly planteles = signal<PlantelOpt[]>([]);

  readonly cargando = signal<boolean>(false);
  readonly descargandoInicio = signal<boolean>(false);
  readonly descargandoCierre = signal<boolean>(false);
  readonly cerrandoCiclo = signal<boolean>(false);
  readonly showConfirmDialog = signal<boolean>(false);

  // Signals para el selector de alcance — necesarios para que @if() reaccione
  readonly alcance = signal<'instituto' | 'plantel'>('instituto');
  readonly plantelId = signal<string | null>(null);

  cicloDestinoId: string | null = null;

  readonly alcanceOpciones = [
    { label: 'Todo el Instituto', value: 'instituto' },
    { label: 'Por Plantel',       value: 'plantel'    }
  ];

  readonly nivelNombre = computed(() =>
    this.ctx.nivel()?.nombre_nivel ?? this.indicadores()?.nombre_nivel ?? ''
  );

  readonly plantelNombre = computed(() => {
    const pid = this.plantelId();
    return this.planteles().find(p => p.id === pid)?.nombre_plantel ?? '';
  });

  constructor() {
    effect(() => {
      const ciclo = this.cicloActivo();
      if (ciclo?.id) {
        this.cargarIndicadores(ciclo.id);
      } else {
        this.indicadores.set(null);
      }
    });
  }

  ngOnInit() {
    this.cargarPlanteles();
    // Pre-seleccionar el plantel del contexto
    const ctxPlantel = this.ctx.plantel();
    if (ctxPlantel?.id) {
      this.plantelId.set(ctxPlantel.id);
    }
  }

  cargarPlanteles() {
    this.api.get<PlantelOpt[]>('/catalogs/planteles').subscribe({
      next: (data) => this.planteles.set(data),
      error: () => {}
    });
  }

  setAlcance(valor: 'instituto' | 'plantel') {
    this.alcance.set(valor);
    const ciclo = this.cicloActivo();
    if (ciclo?.id) this.cargarIndicadores(ciclo.id);
  }

  setPlantel(id: string | null) {
    this.plantelId.set(id);
    const ciclo = this.cicloActivo();
    if (ciclo?.id) this.cargarIndicadores(ciclo.id);
  }

  cargarIndicadores(cicloId: string) {
    this.cargando.set(true);
    let url = `/cierre-ciclo/${cicloId}/indicadores`;
    if (this.alcance() === 'plantel' && this.plantelId()) {
      url += `?plantel_id=${this.plantelId()}`;
    }
    this.api.get<IndicadoresCiclo>(url).subscribe({
      next: (data) => {
        this.indicadores.set(data);
        this.cargando.set(false);
      },
      error: () => {
        this.notify.error('Error', 'No se pudieron cargar los indicadores del ciclo.');
        this.cargando.set(false);
      }
    });
  }

  descargarActa(tipo: 'inicio' | 'cierre') {
    const ciclo = this.cicloActivo();
    if (!ciclo?.id) return;

    if (tipo === 'inicio') {
      this.descargandoInicio.set(true);
    } else {
      this.descargandoCierre.set(true);
    }

    this.api.postBlob(`/cierre-ciclo/${ciclo.id}/acta-${tipo}`, {}).subscribe({
      next: (blob: Blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `acta_${tipo}_${ciclo.nombre_ciclo}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
        if (tipo === 'inicio') {
          this.descargandoInicio.set(false);
        } else {
          this.descargandoCierre.set(false);
        }
        this.notify.success('Descarga exitosa', `El acta de ${tipo} ha sido descargada.`);
      },
      error: () => {
        this.notify.error('Error', `No se pudo generar el acta de ${tipo}.`);
        if (tipo === 'inicio') {
          this.descargandoInicio.set(false);
        } else {
          this.descargandoCierre.set(false);
        }
      }
    });
  }

  abrirConfirmarCierre() {
    this.cicloDestinoId = null;
    this.cargando.set(true);
    this.api.get<CicloOpt[]>('/catalogs/ciclos').subscribe({
      next: (ciclos) => {
        const cicloActual = this.cicloActivo();
        const filtrados = ciclos
          .filter(c => c.id !== cicloActual?.id)
          .map(c => ({
            ...c,
            _label: `${c.nombre_ciclo} — ${c.nombre_nivel || 'General'}`
          }));
        this.ciclosDisponibles.set(filtrados);
        this.showConfirmDialog.set(true);
        this.cargando.set(false);
      },
      error: () => {
        this.notify.error('Error', 'No se pudieron recuperar los ciclos escolares disponibles para promoción.');
        this.cargando.set(false);
      }
    });
  }

  confirmarCierreDefinitivo() {
    const ciclo = this.cicloActivo();
    if (!ciclo?.id) return;

    this.cerrandoCiclo.set(true);

    this.api.get<any>(`/cierre-ciclo/${ciclo.id}/validacion-completa`).subscribe({
      next: (validacion) => {
        if (!validacion.valido) {
          this.cerrandoCiclo.set(false);
          const msg = `Calificaciones incompletas:\n` +
            `- Grupos: ${validacion.grupos_sin_calificar}\n` +
            `- Materias: ${validacion.materias_sin_calificar}\n` +
            `- Alumnos: ${validacion.alumnos_sin_calificar}`;
          this.notify.error('No se puede cerrar', msg);
          return;
        }

        this.api.post(`/cierre-ciclo/${ciclo.id}/ejecutar`, {
          ciclo_destino_id: this.cicloDestinoId || undefined
        }).subscribe({
          next: () => {
            this.notify.success('Ciclo Cerrado', 'El ciclo escolar ha sido cerrado de forma definitiva.');
            this.showConfirmDialog.set(false);
            this.cerrandoCiclo.set(false);
            this.cargarIndicadores(ciclo.id);
          },
          error: (e: any) => {
            this.notify.error('Error al cerrar ciclo', e.error?.detail ?? 'Ocurrió un error inesperado al cerrar el ciclo.');
            this.cerrandoCiclo.set(false);
          }
        });
      },
      error: (e: any) => {
        this.cerrandoCiclo.set(false);
        this.notify.error('Error de validación', e.error?.detail ?? 'No se pudo validar el estado del ciclo.');
      }
    });
  }
}
