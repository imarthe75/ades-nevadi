import { Component, OnDestroy, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { DividerModule } from 'primeng/divider';
import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ApexNotificationService } from 'apex-component-library';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';

interface AlumnoOpt { id: string; matricula: string; persona: { nombre: string; apellido_paterno: string; }; }

interface Inscripcion {
  id: string;
  materia_id: string;
  nombre_materia: string;
  clave_materia: string;
  alumno: string;
  matricula: string;
  fecha_inscripcion: string;
}

interface Materia {
  id: string;
  nombre_materia: string;
  clave_materia: string;
  tipo_materia: string;
  horas_semana: number;
  nombre_nivel: string;
}

const TIPO_LABELS: Record<string, string> = {
  NEVADI_ENRIQUECIMIENTO: 'Enriquecimiento',
  NEVADI_ESPECIALIZADA:   'Especializada',
  NEVADI_FORMATIVA:       'Formativa',
};

/**
 * Módulo de gestión de materias optativas.
 * Permite a coordinadores configurar la oferta de optativas por período y
 * a alumnos de preparatoria seleccionar las materias dentro del CBU UAEMEX.
 */
@Component({
  selector: 'app-optativas',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ButtonModule, SelectModule, AutoCompleteModule,
    TagModule, TooltipModule, DividerModule, InteractiveGridComponent,
  ],
  template: `
    <div class="apex-page">
      <div class="apex-page-header">
        <div>
          <h2 class="apex-title">Materias Optativas</h2>
          <p class="apex-subtitle">Inscripción y control de materias optativas Nevadi — PE-012</p>
        </div>
      </div>

      <!-- Selector de alumno -->
      <div class="student-bar">
        <label class="student-label">Alumno:</label>
        <p-autoComplete
          [(ngModel)]="alumnoSeleccionadoObj"
          [suggestions]="estudiantesSugg()"
          (completeMethod)="buscarEstudiantes($event)"
          optionLabel="nombre_completo"
          [forceSelection]="true"
          placeholder="Buscar alumno por nombre o matrícula..."
          [showClear]="true"
          [dropdown]="true"
          (onSelect)="onAlumnoSelect($event.value)"
          (onClear)="onAlumnoClear()"
          [style]="{width:'360px'}" />
        @if (ctx.ciclo()) {
          <span class="ciclo-badge">Ciclo: {{ ctx.ciclo()!.nombre_ciclo ?? ctx.ciclo()!.id }}</span>
        }
      </div>

      @if (!alumnoSeleccionado) {
        <div class="empty-hint">
          <i class="pi pi-info-circle"></i>
          Selecciona un alumno para gestionar sus optativas. Asegúrate de tener seleccionado el nivel educativo en el contexto.
        </div>
      }

      @if (alumnoSeleccionado) {

        <!-- ═══ INSCRITAS ═══ -->
        <div class="section-header">
          <div>
            <h3 class="section-title">Optativas Inscritas</h3>
            <span class="count-badge">{{ inscritas().length }} materia(s)</span>
          </div>
        </div>

        <app-interactive-grid
          [data]="inscritasFlat()"
          [columns]="inscritasColumns"
          [loading]="cargandoInscritas()"
          [showDelete]="true"
          (rowDeleted)="darDeBaja($event)"
        />

        <p-divider />

        <!-- ═══ CATÁLOGO DISPONIBLE ═══ -->
        <div class="section-header">
          <div>
            <h3 class="section-title">Catálogo de Optativas Disponibles</h3>
            <span class="count-badge secondary">{{ disponibles().length }} disponible(s)</span>
          </div>
          @if (!ctx.nivel()) {
            <span class="hint-text"><i class="pi pi-exclamation-triangle"></i> Selecciona un nivel educativo en el contexto para filtrar el catálogo</span>
          }
        </div>

        <app-interactive-grid
          [data]="disponiblesFlat()"
          [columns]="catalogoColumns"
          [loading]="cargandoCatalogo()"
          [showDelete]="false"
          (rowSelected)="inscribir($event)"
        />
        <p class="hint-row-click"><i class="pi pi-hand-pointer"></i> Haz clic en una materia del catálogo para inscribirla.</p>

      }
    </div>
  `,
  styles: [`
    .apex-page { padding: 1rem; }
    .apex-page-header { margin-bottom: .75rem; }
    .apex-title { margin: 0; font-size: 1.25rem; font-weight: 600; }
    .apex-subtitle { font-size: .8rem; color: var(--text-color-secondary); margin: .1rem 0 0; }
    .student-bar { display: flex; align-items: center; gap: .75rem; padding: .5rem .75rem; background: var(--surface-50); border: 1px solid var(--surface-200); border-radius: 8px; margin-bottom: 1rem; flex-wrap: wrap; }
    .student-label { font-size: .85rem; font-weight: 600; color: var(--text-color-secondary); flex-shrink: 0; }
    .ciclo-badge { font-size: .8rem; color: var(--primary-color); background: var(--primary-50, #eff6ff); padding: .15rem .6rem; border-radius: 10px; border: 1px solid var(--primary-200, #bfdbfe); }
    .empty-hint { padding: 2rem; text-align: center; color: var(--text-color-secondary); font-size: .9rem; background: var(--surface-50); border-radius: 8px; margin: 1rem 0; }
    .section-header { display: flex; align-items: center; justify-content: space-between; margin: 1rem 0 .5rem; flex-wrap: wrap; gap: .5rem; }
    .section-title { margin: 0; font-size: 1rem; font-weight: 600; }
    .count-badge { display: inline-block; font-size: .78rem; background: var(--primary-100, #dbeafe); color: var(--primary-700, #1d4ed8); padding: .15rem .6rem; border-radius: 10px; font-weight: 600; }
    .count-badge.secondary { background: var(--surface-100); color: var(--text-color-secondary); }
    .hint-text { font-size: .8rem; color: var(--orange-600); }
    .hint-row-click { font-size: .78rem; color: var(--text-color-secondary); margin: .25rem 0 1rem; }
  `],
})
export class OptativasComponent implements OnInit implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private readonly api    = inject(ApiService);
  private readonly notify = inject(ApexNotificationService);
  readonly ctx            = inject(ContextService);

  estudiantesSugg    = signal<any[]>([]);
  alumnoSeleccionadoObj: any = null;
  alumnoSeleccionado: string | null = null;

  inscritas          = signal<Inscripcion[]>([]);
  catalogo           = signal<Materia[]>([]);
  cargandoInscritas  = signal(false);
  cargandoCatalogo   = signal(false);
  guardando          = signal(false);

  // Exclude materias already enrolled
  readonly inscritasIds = computed(() => new Set(this.inscritas().map(i => i.materia_id ?? '')));

  readonly disponibles = computed(() =>
    this.catalogo().filter(m => !this.isInscrita(m.id))
  );

  readonly inscritasFlat = computed(() =>
    this.inscritas().map(i => ({
      ...i,
      fecha_str: i.fecha_inscripcion ? new Date(i.fecha_inscripcion).toLocaleDateString('es-MX') : '—',
    }))
  );

  readonly disponiblesFlat = computed(() =>
    this.disponibles().map(m => ({
      ...m,
      tipo_label: TIPO_LABELS[m.tipo_materia] ?? m.tipo_materia,
    }))
  );

  readonly inscritasColumns: ColumnConfig[] = [
    { field: 'nombre_materia', header: 'Materia' },
    { field: 'clave_materia',  header: 'Clave',    width: '110px' },
    { field: 'fecha_str',      header: 'Inscripción', width: '120px' },
  ];

  readonly catalogoColumns: ColumnConfig[] = [
    { field: 'nombre_materia', header: 'Materia' },
    { field: 'clave_materia',  header: 'Clave',       width: '110px' },
    { field: 'tipo_label',     header: 'Tipo',        width: '140px' },
    { field: 'horas_semana',   header: 'Hrs/sem',     width: '80px' },
    { field: 'nombre_nivel',   header: 'Nivel',       width: '120px' },
  ];

  ngOnInit(): void {
    this.cargarCatalogo();
  }

  buscarEstudiantes(event: { query: string }): void {
    if (!event.query || event.query.trim().length < 2) {
      this.estudiantesSugg.set([]);
      return;
    }
    this.api.get<any[]>('/portal/buscar', { q: event.query }).subscribe({
      next: (res) => {
        this.estudiantesSugg.set((res ?? []).map((a: any) => ({
          id: a.id,
          matricula: a.matricula,
          nombre_completo: [a.nombre, a.apellido_paterno, a.apellido_materno]
            .filter(Boolean).join(' ') + (a.matricula ? ` — ${a.matricula}` : ''),
        })));
      },
      error: () => {
        this.estudiantesSugg.set([]);
      }
    });
  }

  onAlumnoSelect(alumno: any): void {
    this.alumnoSeleccionado = alumno?.id ?? null;
    this.alumnoSeleccionadoObj = alumno;
    this.onAlumnoChange();
  }

  onAlumnoClear(): void {
    this.alumnoSeleccionado = null;
    this.alumnoSeleccionadoObj = null;
    this.onAlumnoChange();
  }

  cargarCatalogo(): void {
    this.cargandoCatalogo.set(true);
    const nivelId = this.ctx.nivel()?.id;
    this.api.get<Materia[]>('/materias', {
      tipo: 'NEVADI',
      ...(nivelId ? { nivel_educativo_id: nivelId } : {}),
    }).subscribe({
      next: d => { this.catalogo.set(Array.isArray(d) ? d : []); this.cargandoCatalogo.set(false); },
      error: () => { this.cargandoCatalogo.set(false); },
    });
  }

  onAlumnoChange(): void {
    if (this.alumnoSeleccionado) {
      this.cargarInscritas();
    } else {
      this.inscritas.set([]);
    }
  }

  cargarInscritas(): void {
    if (!this.alumnoSeleccionado) return;
    this.cargandoInscritas.set(true);
    const cicloId = this.ctx.ciclo()?.id;
    this.api.get<Inscripcion[]>('/procesos/optativas', {
      estudiante_id: this.alumnoSeleccionado,
      ...(cicloId ? { ciclo_id: cicloId } : {}),
    }).subscribe({
      next: d => { this.inscritas.set(Array.isArray(d) ? d : []); this.cargandoInscritas.set(false); },
      error: () => { this.cargandoInscritas.set(false); this.notify.error('Error', 'No se pudieron cargar las optativas inscritas'); },
    });
  }

  private isInscrita(materiaId: string): boolean {
    return this.inscritas().some(i => i.materia_id === materiaId);
  }

  inscribir(row: Materia): void {
    if (!this.alumnoSeleccionado) {
      this.notify.warning('Alumno requerido', 'Selecciona un alumno primero');
      return;
    }
    if (this.isInscrita(row.id)) {
      this.notify.warning('Ya inscrito', 'El alumno ya está inscrito en esta materia');
      return;
    }
    const cicloId = this.ctx.ciclo()?.id;
    if (!cicloId) {
      this.notify.warning('Ciclo requerido', 'Selecciona un ciclo escolar en el contexto');
      return;
    }
    this.guardando.set(true);
    const body = {
      estudiante_id: this.alumnoSeleccionado,
      materia_id: row.id,
      ciclo_escolar_id: cicloId,
    };
    this.api.post('/procesos/optativas', body).subscribe({
      next: () => {
        this.guardando.set(false);
        this.notify.success('Inscripción registrada', `${row.nombre_materia} agregada correctamente`);
        this.cargarInscritas();
      },
      error: (e: any) => {
        this.guardando.set(false);
        this.notify.error('Error', e?.error?.message ?? e?.error?.detail ?? 'No se pudo registrar la inscripción');
      },
    });
  }

  darDeBaja(row: any): void {
    this.api.delete(`/procesos/optativas/${row.id}`).subscribe({
      next: () => {
        this.notify.success('Baja registrada', `${row.nombre_materia} eliminada de las optativas`);
        this.cargarInscritas();
      },
      error: (e: any) => {
        this.notify.error('Error', e?.error?.message ?? 'No se pudo dar de baja la optativa');
      },
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
