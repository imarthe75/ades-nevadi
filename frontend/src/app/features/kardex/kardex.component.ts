import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { ApexNotificationService } from 'apex-component-library';
import { ContextService } from '../../core/services/context.service';
import { ExportService, ExportColumn } from '../../core/services/export.service';

interface GrupoRaw {
  id: string; nombre_grupo: string;
  semestre: string; numero_grado: number;
  nivel: string; plantel: string; plantel_id: string; ciclo: string;
}
interface AlumnoOpt { value: string; label: string; }
interface MateriaKardex {
  materia: string; clave: string | null;
  ordinario: number | null; extraordinario: number | null;
  definitiva: number | null; acreditada: boolean; inasistencias: number;
}
interface Kardex {
  alumno: {
    alumno: string; matricula: string; curp: string;
    semestre: string; grupo: string; plantel: string; ciclo: string;
  } | null;
  escala: string;
  materias: MateriaKardex[];
  promedio_general: number | null;
  materias_acreditadas: number;
  materias_reprobadas: number;
  total_materias: number;
}

/**
 * Kardex académico para alumnos de Preparatoria UAEMEX (nivel CBU).
 * Muestra historial de calificaciones con escala 0-10, mínimo aprobatorio 6.0,
 * y distingue entre calificación ordinaria, extraordinaria y definitiva según RGEMS.
 * Incluye botón para descargar la boleta PDF generada por FastAPI vía proxy BFF.
 */
@Component({
  selector: 'app-kardex',
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonModule, SelectModule, ToastModule],
  providers: [MessageService],
  template: `
    <p-toast />
    <div class="apex-page">
      <div class="apex-toolbar">
        <h2 class="apex-title">Kardex / Historial Académico — UAEMEX</h2>
        <div class="apex-toolbar-actions">

          <!-- Plantel -->
          <p-select [options]="plantelesOpts()" optionLabel="label" optionValue="value"
            placeholder="Plantel…" [(ngModel)]="plantelSel"
            [disabled]="isPlantelDisabled()"
            (ngModelChange)="onPlantelChange()" style="min-width:160px" />

          <!-- Semestre -->
          <p-select [options]="semestresOpts()" optionLabel="label" optionValue="value"
            placeholder="Semestre…" [(ngModel)]="semestreSel"
            (ngModelChange)="onSemestreChange()"
            [disabled]="!plantelSel" style="min-width:140px" />

          <!-- Grupo -->
          <p-select [options]="gruposOpts()" optionLabel="label" optionValue="value"
            placeholder="Grupo…" [(ngModel)]="grupoSel"
            (ngModelChange)="onGrupoChange($event)"
            [disabled]="!semestreSel" style="min-width:110px" />

          <!-- Alumno -->
          <p-select [options]="alumnosOpts()" optionLabel="label" optionValue="value"
            placeholder="Alumno…" [(ngModel)]="alumnoSel"
            [disabled]="!grupoSel" [filter]="true" filterBy="label"
            style="min-width:240px" />

          <p-button label="Consultar" icon="pi pi-search" size="small" (onClick)="cargar()"
            [loading]="cargando()" [disabled]="!alumnoSel" />

          @if (data()?.alumno) {
            <p-button label="Excel" icon="pi pi-file-excel" size="small" severity="success"
              [outlined]="true" (onClick)="exportar()" />
            <p-button label="Constancia PDF" icon="pi pi-file-pdf" size="small" severity="danger"
              [outlined]="true" [loading]="descargando()" (onClick)="descargarPdf()" />
          }
        </div>
      </div>

      @if (data()?.alumno; as a) {
        <div class="ficha">
          <div><span class="lbl">Alumno</span><span class="val">{{ a.alumno }}</span></div>
          <div><span class="lbl">Matrícula</span><span class="val">{{ a.matricula }}</span></div>
          <div><span class="lbl">CURP</span><span class="val">{{ a.curp }}</span></div>
          <div><span class="lbl">Semestre / Grupo</span><span class="val">{{ a.semestre }} · {{ a.grupo }}</span></div>
          <div><span class="lbl">Plantel</span><span class="val">{{ a.plantel }}</span></div>
          <div><span class="lbl">Ciclo</span><span class="val">{{ a.ciclo }}</span></div>
        </div>

        <table class="kardex">
          <thead>
            <tr>
              <th class="left">Asignatura</th>
              <th>Clave</th>
              <th>Ordinario</th>
              <th>Extraord.</th>
              <th>Definitiva</th>
              <th>Estatus</th>
              <th>Inasist.</th>
            </tr>
          </thead>
          <tbody>
            @for (m of data()!.materias; track m.materia) {
              <tr>
                <td class="left">{{ m.materia }}</td>
                <td>{{ m.clave || '—' }}</td>
                <td>{{ fmt(m.ordinario) }}</td>
                <td>{{ fmt(m.extraordinario) }}</td>
                <td class="def" [class.rep]="!m.acreditada">{{ fmt(m.definitiva) }}</td>
                <td [class.ok]="m.acreditada" [class.rep]="!m.acreditada">
                  {{ m.acreditada ? 'Acreditada' : 'No acreditada' }}
                </td>
                <td>{{ m.inasistencias }}</td>
              </tr>
            }
          </tbody>
          <tfoot>
            <tr>
              <td class="left" colspan="4">
                Promedio general: <strong>{{ fmt(data()!.promedio_general) }}</strong>
              </td>
              <td colspan="3" class="right">
                Acreditadas: <strong>{{ data()!.materias_acreditadas }}</strong> /
                {{ data()!.total_materias }}
                @if (data()!.materias_reprobadas) {
                  · <span class="rep">Reprobadas: {{ data()!.materias_reprobadas }}</span>
                }
              </td>
            </tr>
          </tfoot>
        </table>

        <p class="nota">{{ data()!.escala }}. Definitiva = ordinario si ≥ 6.0; en caso
          contrario, la calificación de examen extraordinario.</p>

      } @else if (!cargando()) {
        <p class="text-center text-gray-500 py-6">
          Selecciona plantel, semestre, grupo y alumno para consultar el kardex.
        </p>
      }
    </div>
  `,
  styles: [`
    .apex-page { padding: 1rem; }
    .apex-toolbar { display:flex; align-items:flex-start; justify-content:space-between; margin-bottom:1rem; flex-wrap:wrap; gap:.5rem; }
    .apex-title { margin:0; font-size:1.25rem; font-weight:600; }
    .apex-toolbar-actions { display:flex; gap:.5rem; align-items:center; flex-wrap:wrap; }
    .ficha { display:grid; grid-template-columns:repeat(3,1fr); gap:.5rem 1rem; background:var(--surface-100);
             border:1px solid var(--surface-300); border-radius:6px; padding:.8rem 1rem; margin-bottom:1rem; }
    .ficha .lbl { display:block; font-size:.65rem; text-transform:uppercase; color:var(--text-color-secondary); letter-spacing:.04em; }
    .ficha .val { font-weight:600; font-size:.9rem; }
    table.kardex { width:100%; border-collapse:collapse; font-size:.85rem; }
    .kardex th, .kardex td { border:1px solid var(--surface-300); padding:.35rem .6rem; text-align:center; }
    .kardex th { background:var(--surface-200); font-weight:600; }
    .kardex .left { text-align:left; }
    .kardex .right { text-align:right; }
    .kardex td.def { font-weight:700; }
    .kardex .ok  { color:var(--green-600); font-weight:600; }
    .kardex .rep { color:var(--red-600); font-weight:600; }
    .kardex tfoot td { background:var(--surface-100); }
    .nota { font-size:.78rem; color:var(--text-color-secondary); margin-top:.6rem; }
  `],
})
export class KardexComponent implements OnInit {
  private api      = inject(ApiService);
  private notify   = inject(ApexNotificationService);
  private exporter = inject(ExportService);
  private ctx      = inject(ContextService);

  // ── Cascada: Plantel → Semestre → Grupo → Alumno ────────────────────────
  private _grupos  = signal<GrupoRaw[]>([]);
  private _alumnos = signal<AlumnoOpt[]>([]);

  plantelSel  = '';
  semestreSel = '';
  grupoSel    = '';
  alumnoSel   = '';

  cargando     = signal(false);
  descargando  = signal(false);
  data         = signal<Kardex | null>(null);

  readonly isPlantelDisabled = computed(() => (this.ctx.nivelAcceso() ?? 0) > 2);

  // Client-side derived options (single API call for grupos)
  plantelesOpts = computed(() => {
    const seen = new Set<string>();
    return this._grupos()
      .filter(g => { const ok = !seen.has(g.plantel); seen.add(g.plantel); return ok; })
      .map(g => ({ value: g.plantel, label: g.plantel }));
  });

  semestresOpts = computed(() => {
    const seen = new Set<string>();
    return this._grupos()
      .filter(g => g.plantel === this.plantelSel)
      .filter(g => { const ok = !seen.has(g.semestre); seen.add(g.semestre); return ok; })
      .sort((a, b) => a.numero_grado - b.numero_grado)
      .map(g => ({ value: g.semestre, label: g.semestre }));
  });

  gruposOpts = computed(() =>
    this._grupos()
      .filter(g => g.plantel === this.plantelSel && g.semestre === this.semestreSel)
      .map(g => ({ value: g.id, label: `Grupo ${g.nombre_grupo}` }))
  );

  alumnosOpts = computed(() => this._alumnos());

  ngOnInit() {
    this.api.get<GrupoRaw[]>('/reportes/kardex/grupos').subscribe({
      next: g => {
        this._grupos.set(g);
        const ctxPlantel = this.ctx.plantel();
        if (ctxPlantel) this.plantelSel = ctxPlantel.nombre_plantel ?? '';
      },
      error: () => this.notify.error('No se pudieron cargar los grupos UAEMEX'),
    });
  }

  onPlantelChange() {
    this.semestreSel = ''; this.grupoSel = ''; this.alumnoSel = '';
    this._alumnos.set([]); this.data.set(null);
  }

  onSemestreChange() {
    this.grupoSel = ''; this.alumnoSel = '';
    this._alumnos.set([]); this.data.set(null);
  }

  onGrupoChange(grupoId: string) {
    this.alumnoSel = ''; this._alumnos.set([]); this.data.set(null);
    if (!grupoId) return;
    this.api.get<any[]>(`/reportes/kardex/grupos/${grupoId}/alumnos`).subscribe({
      next: list => this._alumnos.set(list.map(a => ({
        value: a['id'], label: `${a['nombre']} (${a['matricula'] ?? '—'})`,
      }))),
      error: () => this.notify.error('No se pudieron cargar los alumnos del grupo'),
    });
  }

  fmt(v: number | null): string {
    return v === null || v === undefined ? '—' : Number(v).toFixed(1);
  }

  cargar() {
    if (!this.alumnoSel) return;
    this.cargando.set(true);
    this.data.set(null);
    this.api.get<Kardex>(`/reportes/kardex/${this.alumnoSel}`).subscribe({
      next: d => { this.data.set(d); this.cargando.set(false); },
      error: e => {
        this.cargando.set(false);
        this.notify.error(e.error?.message ?? e.error?.detail ?? 'No se encontró kardex UAEMEX');
      },
    });
  }

  descargarPdf() {
    if (!this.alumnoSel) return;
    this.descargando.set(true);
    this.api.getBlob(`/boletas/uaemex/${this.alumnoSel}`).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        const k = this.data();
        a.href = url;
        a.download = `constancia_uaemex_${k?.alumno?.matricula ?? this.alumnoSel}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
        this.descargando.set(false);
      },
      error: () => {
        this.notify.error('No se pudo descargar la constancia UAEMEX');
        this.descargando.set(false);
      },
    });
  }

  exportar() {
    const k = this.data();
    if (!k?.alumno) return;
    const columns: ExportColumn[] = [
      { field: 'materia', header: 'Asignatura' },
      { field: 'clave', header: 'Clave' },
      { field: 'ordinario', header: 'Ordinario', format: v => this.fmt(v) },
      { field: 'extraordinario', header: 'Extraordinario', format: v => this.fmt(v) },
      { field: 'definitiva', header: 'Definitiva', format: v => this.fmt(v) },
      { field: 'estatus', header: 'Estatus' },
      { field: 'inasistencias', header: 'Inasistencias' },
    ];
    const data = k.materias.map(m => ({
      ...m, estatus: m.acreditada ? 'Acreditada' : 'No acreditada',
    }));
    this.exporter.toXLSX(data, columns, 'Kardex UAEMEX', `kardex_${k.alumno.matricula}`);
    this.notify.success('Kardex exportado');
  }
}
