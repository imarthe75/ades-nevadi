/**
 * HelpButton — Botón "?" contextual que muestra un popover con ayuda del módulo.
 * Uso: <app-help-button modulo="calificaciones" />
 */
import { Component, Input, OnDestroy, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { PopoverModule } from 'primeng/popover';

export interface HelpContent { titulo: string; pasos: string[]; nota?: string; }

const AYUDA: Record<string, HelpContent> = {
  dashboard: {
    titulo: 'Dashboard',
    pasos: [
      'El dashboard muestra los KPIs del plantel y escuela seleccionados en el topbar.',
      'Cambia el plantel / escuela / ciclo en la barra superior para ver datos diferentes.',
      'Los contadores se actualizan automáticamente al cambiar el contexto.',
    ],
    nota: 'Solo verás datos de los planteles y escuelas a los que tienes acceso según tu rol.',
  },
  alumnos: {
    titulo: 'Gestión de Alumnos',
    pasos: [
      'Busca alumnos por nombre, apellido o CURP en la barra de búsqueda.',
      'Usa los botones CSV/Excel para exportar el listado completo.',
      'El botón "Importar" permite cargar alumnos en masa desde un archivo CSV o Excel.',
      'Para ver la plantilla de importación, haz clic en "Plantilla".',
    ],
    nota: 'Los alumnos mostrados corresponden al plantel y escuela del contexto actual.',
  },
  profesores: {
    titulo: 'Gestión de Profesores',
    pasos: [
      'Lista todos los profesores activos del plantel.',
      'Usa búsqueda por nombre, apellido o número de empleado.',
      'La importación masiva acepta CSV y Excel con los campos: nombre, apellido, CURP, número de empleado.',
    ],
  },
  grupos: {
    titulo: 'Grupos Académicos',
    pasos: [
      'Muestra todos los grupos activos del ciclo vigente.',
      'La columna "Ocupación" indica cuántos alumnos están inscritos sobre el total permitido.',
      'Los grupos en rojo están al máximo de capacidad.',
    ],
    nota: 'Para crear o editar grupos, ve a Administración → Grupos.',
  },
  calificaciones: {
    titulo: 'Libreta de Calificaciones',
    pasos: [
      '1. Selecciona el grupo en el primer dropdown.',
      '2. Selecciona la materia en el segundo dropdown.',
      '3. Haz clic en una celda para editar la calificación (0–10).',
      '4. Las celdas editadas se marcan en amarillo — usa "Guardar cambios" para confirmar.',
      '5. "Exportar CSV" descarga la libreta completa.',
    ],
    nota: 'El promedio se calcula automáticamente. Acredita con ≥ 6.',
  },
  gradebook: {
    titulo: 'Gradebook Curricular',
    pasos: [
      'Selecciona el grupo y materia para ver las actividades del periodo.',
      'Cada actividad tiene un peso configurado en el esquema de ponderación.',
      'Ingresa calificaciones de actividades — el promedio ponderado se calcula en tiempo real.',
      'Usa "Cerrar período" cuando todas las calificaciones estén completas.',
    ],
  },
  asistencias: {
    titulo: 'Registro de Asistencias',
    pasos: [
      'Selecciona el grupo y la fecha.',
      'Marca "Presente", "Ausente" o "Justificado" para cada alumno.',
      'Guarda los cambios con el botón al final de la lista.',
    ],
    nota: 'Solo se puede registrar asistencia para el día actual y los últimos 7 días.',
  },
  admin: {
    titulo: 'Módulo de Administración',
    pasos: [
      'Usuarios: cambia el rol, alcance (plantel/escuela) y estado de cualquier usuario.',
      'Ciclos: crea y gestiona ciclos escolares por nivel educativo. Solo puede haber un ciclo vigente por nivel.',
      'Planteles: edita nombre y clave CT de los planteles.',
      'Grupos: crea nuevos grupos y edita capacidad, turno y estado.',
    ],
    nota: 'Solo disponible para ADMIN_GLOBAL y ADMIN_PLANTEL.',
  },
  horarios: {
    titulo: 'Horarios',
    pasos: ['Visualiza el horario semanal por grupo o por profesor.', 'Selecciona grupo/profesor en el filtro superior.'],
  },
  planeacion: {
    titulo: 'Planeación Didáctica',
    pasos: ['Crea y gestiona planes de clase por materia y grupo.', 'Los planes pueden vincular recursos y actividades del gradebook.'],
  },
};

@Component({
  selector: 'app-help-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule, RouterLink, ButtonModule, PopoverModule],
  template: `
    <p-button
      icon="pi pi-question-circle"
      [rounded]="true"
      [text]="true"
      severity="secondary"
      pTooltip="Ayuda"
      (onClick)="panel.toggle($event)"
      styleClass="help-btn"
    />
    <p-popover #panel>
      <div class="help-panel" style="max-width:340px">
        @if (contenido) {
          <div class="help-titulo">
            <i class="pi pi-question-circle"></i>
            {{ contenido.titulo }}
          </div>
          <ul class="help-pasos">
            @for (paso of contenido.pasos; track paso) {
              <li>{{ paso }}</li>
            }
          </ul>
          @if (contenido.nota) {
            <div class="help-nota">
              <i class="pi pi-info-circle"></i> {{ contenido.nota }}
            </div>
          }
        } @else {
          <p style="color:#64748b;font-size:.85rem">No hay ayuda disponible para este módulo.</p>
        }
        <div class="help-footer">
          <a routerLink="/ayuda" style="font-size:.78rem;color:var(--primary-color)">Ver manual completo →</a>
        </div>
      </div>
    </p-popover>
  `,
  styles: [`
    :host ::ng-deep .help-btn { opacity: 0.6; }
    :host ::ng-deep .help-btn:hover { opacity: 1; }
    .help-titulo { font-weight: 700; font-size: .9rem; margin-bottom: .6rem; display: flex; gap: .4rem; align-items: center; color: var(--primary-color); }
    .help-pasos { padding-left: 1.1rem; margin: 0 0 .5rem; font-size: .83rem; line-height: 1.6; color: #334155; }
    .help-nota { font-size: .78rem; color: #64748b; background: #f1f5f9; border-radius: 4px; padding: .4rem .6rem; margin-top: .4rem; display: flex; gap: .3rem; }
    .help-footer { margin-top: .6rem; padding-top: .4rem; border-top: 1px solid #e2e8f0; text-align: right; }
  `],
})
export class HelpButtonComponent implements OnDestroy {
  @Input() modulo = '';

  get contenido(): HelpContent | null {
    return AYUDA[this.modulo] ?? null;
  }

  ngOnDestroy(): void {}
}
