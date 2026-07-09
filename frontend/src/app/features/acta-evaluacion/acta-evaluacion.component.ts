import { Component, OnDestroy, signal, computed, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { ApexNotificationService } from 'apex-component-library';
import { ExportService, ExportColumn } from '../../core/services/export.service';

interface GrupoRaw {
  id: string; nombre_grupo: string;
  semestre: string; numero_grado: number;
  nivel: string; plantel: string; ciclo: string;
}
interface MateriaOpt { value: string; label: string; }
interface AlumnoActa {
  num: number; alumno: string; matricula: string; curp: string;
  ordinario: number | null; extraordinario: number | null;
  definitiva: number | null; acreditada: boolean; inasistencias: number;
}
interface Cabecera {
  grupo: string; semestre: string; plantel: string;
  ciclo: string; materia: string; clave_materia: string; docente: string;
}
interface Acta {
  cabecera: Cabecera; alumnos: AlumnoActa[];
  total_alumnos: number; acreditados: number; reprobados: number;
  sin_calificacion: number; promedio_grupal: number | null; escala: string;
}

/**
 * Genera el Acta de Evaluación oficial para grupos de Preparatoria Nevadi (UAEMEX).
 * Implementa la cascada de filtros Plantel → Semestre → Grupo → Materia usando
 * `computed()` signals derivados de un único listado de grupos cargado al inicio.
 * Muestra calificaciones ordinario/extraordinario/definitiva con estatus A/NA
 * según escala 0-10 mín 6.0 (RGEMS UAEMEX). Permite exportar a XLSX e imprimir.
 * Requiere nivelAcceso ≥ 3 (docente) en el BFF.
 */
@Component({
  selector: 'app-acta-evaluacion',
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonModule, SelectModule, ToastModule],
  providers: [MessageService],
  template: `
    <p-toast />
    <div class="apex-page">
      <!-- ── Toolbar ───────────────────────────────────────────── -->
      <div class="apex-toolbar">
        <h2 class="apex-title">Acta de Evaluación — UAEMEX</h2>
        <div class="apex-toolbar-actions">

          <!-- Plantel -->
          <p-select [options]="plantelesOpts()" optionLabel="label" optionValue="value"
            placeholder="Plantel…" [(ngModel)]="plantelSel"
            (ngModelChange)="onPlantelChange()" style="min-width:170px" />

          <!-- Semestre -->
          <p-select [options]="semestresOpts()" optionLabel="label" optionValue="value"
            placeholder="Semestre…" [(ngModel)]="semestreSel"
            (ngModelChange)="onSemestreChange()"
            [disabled]="!plantelSel" style="min-width:170px" />

          <!-- Grupo -->
          <p-select [options]="gruposOpts()" optionLabel="label" optionValue="value"
            placeholder="Grupo…" [(ngModel)]="grupoSel"
            (ngModelChange)="onGrupoChange($event)"
            [disabled]="!semestreSel" style="min-width:110px" />

          <!-- Materia -->
          <p-select [options]="materiaOpts()" optionLabel="label" optionValue="value"
            placeholder="Materia…" [(ngModel)]="materiaSel"
            [filter]="true" filterBy="label"
            [disabled]="!grupoSel" style="min-width:220px" />

          <p-button label="Generar" icon="pi pi-file" size="small"
            [loading]="cargando()" [disabled]="!grupoSel || !materiaSel"
            (onClick)="generar()" />

          @if (acta()) {
            <p-button label="Excel" icon="pi pi-file-excel" size="small"
              severity="success" [outlined]="true" (onClick)="exportar()" />
            <p-button label="Imprimir" icon="pi pi-print" size="small"
              severity="secondary" [outlined]="true" (onClick)="imprimir()" />
          }
        </div>
      </div>

      @if (acta(); as a) {
        <!-- ── Cabecera oficial ──────────────────────────────────── -->
        <div class="cab-oficial">
          <div class="cab-title">ACTA DE EVALUACIÓN</div>
          <div class="cab-subtitle">Preparatoria Nevadi — Incorporada UAEMEX</div>
          <div class="cab-grid">
            <div><span class="lbl">Plantel</span><span class="val">{{ a.cabecera.plantel }}</span></div>
            <div><span class="lbl">Ciclo escolar</span><span class="val">{{ a.cabecera.ciclo }}</span></div>
            <div><span class="lbl">Semestre</span><span class="val">{{ a.cabecera.semestre }}</span></div>
            <div><span class="lbl">Grupo</span><span class="val">{{ a.cabecera.grupo }}</span></div>
            <div><span class="lbl">Asignatura</span><span class="val">{{ a.cabecera.materia }}</span></div>
            <div><span class="lbl">Clave</span><span class="val">{{ a.cabecera.clave_materia }}</span></div>
            <div class="col-full"><span class="lbl">Docente</span><span class="val">{{ a.cabecera.docente }}</span></div>
          </div>
        </div>

        <!-- ── Tabla de alumnos ─────────────────────────────────── -->
        <table class="acta-table">
          <thead>
            <tr>
              <th class="center">#</th>
              <th class="left">Nombre del alumno</th>
              <th>Matrícula</th>
              <th>CURP</th>
              <th>Ordinario</th>
              <th>Extraord.</th>
              <th>Definitiva</th>
              <th>Estatus</th>
              <th>Inasist.</th>
            </tr>
          </thead>
          <tbody>
            @for (al of a.alumnos; track al.num) {
              <tr>
                <td class="center muted">{{ al.num }}</td>
                <td class="left">{{ al.alumno }}</td>
                <td class="center">{{ al.matricula }}</td>
                <td class="curp">{{ al.curp }}</td>
                <td class="center">{{ fmt(al.ordinario) }}</td>
                <td class="center">{{ fmt(al.extraordinario) }}</td>
                <td class="center def" [class.rep]="!al.acreditada && al.definitiva !== null">
                  {{ fmt(al.definitiva) }}
                </td>
                <td class="center" [class.ok]="al.acreditada"
                    [class.rep]="!al.acreditada && al.definitiva !== null">
                  {{ al.definitiva === null ? '—' : al.acreditada ? 'A' : 'NA' }}
                </td>
                <td class="center">{{ al.inasistencias }}</td>
              </tr>
            }
          </tbody>
          <tfoot>
            <tr class="stats-row">
              <td colspan="4" class="left">
                Total: <strong>{{ a.total_alumnos }}</strong>
                &nbsp;·&nbsp;
                <span class="ok">Acreditados: <strong>{{ a.acreditados }}</strong></span>
                &nbsp;·&nbsp;
                <span class="rep">No acreditados: <strong>{{ a.reprobados }}</strong></span>
                @if (a.sin_calificacion) {
                  &nbsp;·&nbsp; Sin calificación: <strong>{{ a.sin_calificacion }}</strong>
                }
              </td>
              <td colspan="3" class="center">
                Promedio grupal: <strong>{{ fmt(a.promedio_grupal) }}</strong>
              </td>
              <td colspan="2"></td>
            </tr>
          </tfoot>
        </table>

        <!-- ── Líneas de firma ──────────────────────────────────── -->
        <div class="firmas">
          <div class="firma-box">
            <div class="firma-linea"></div>
            <div class="firma-desc">Firma del Docente</div>
            <div class="firma-nombre">{{ a.cabecera.docente }}</div>
          </div>
          <div class="firma-box">
            <div class="firma-linea"></div>
            <div class="firma-desc">Vo. Bo. Dirección Académica</div>
          </div>
          <div class="firma-box">
            <div class="firma-linea"></div>
            <div class="firma-desc">Control Escolar UAEMEX</div>
          </div>
        </div>

        <p class="nota">{{ a.escala }}. A = Acreditada · NA = No acreditada.
          Definitiva = ordinario si ≥ 6.0; de lo contrario, calificación de extraordinario.</p>

      } @else if (!cargando()) {
        <p class="text-center text-gray-500 py-8">
          Selecciona plantel, semestre, grupo y materia para generar el acta.
        </p>
      }
    </div>
  `,
  styles: [`
    .apex-page { padding: 1rem; }
    .apex-toolbar { display:flex; align-items:flex-start; justify-content:space-between; margin-bottom:1rem; flex-wrap:wrap; gap:.5rem; }
    .apex-title { margin:0; font-size:1.25rem; font-weight:600; }
    .apex-toolbar-actions { display:flex; gap:.5rem; align-items:center; flex-wrap:wrap; }

    .cab-oficial { border:2px solid var(--surface-400); border-radius:6px; padding:.8rem 1rem; margin-bottom:1rem; }
    .cab-title { text-align:center; font-size:1.1rem; font-weight:700; letter-spacing:.08em; text-transform:uppercase; }
    .cab-subtitle { text-align:center; font-size:.82rem; color:var(--text-color-secondary); margin-bottom:.6rem; }
    .cab-grid { display:grid; grid-template-columns:repeat(3, 1fr); gap:.4rem .8rem; }
    .cab-grid .col-full { grid-column:1/-1; }
    .cab-grid .lbl { display:block; font-size:.62rem; text-transform:uppercase; letter-spacing:.04em; color:var(--text-color-secondary); }
    .cab-grid .val { font-weight:600; font-size:.88rem; }

    table.acta-table { width:100%; border-collapse:collapse; font-size:.83rem; margin-bottom:1rem; }
    .acta-table th, .acta-table td { border:1px solid var(--surface-300); padding:.3rem .5rem; }
    .acta-table th { background:var(--surface-200); font-weight:600; text-align:center; }
    .acta-table .left { text-align:left; }
    .acta-table .center { text-align:center; }
    .acta-table .curp { font-size:.72rem; font-family:monospace; text-align:center; }
    .acta-table .muted { color:var(--text-color-secondary); }
    .acta-table td.def { font-weight:700; }
    .acta-table .ok  { color:var(--green-600); font-weight:600; }
    .acta-table .rep { color:var(--red-600);   font-weight:600; }
    .acta-table tfoot td { background:var(--surface-100); font-size:.8rem; }

    .firmas { display:grid; grid-template-columns:repeat(3,1fr); gap:2rem; margin:2rem 0 1rem; }
    .firma-box { text-align:center; }
    .firma-linea { border-top:1px solid var(--surface-600); margin-bottom:.3rem; }
    .firma-desc { font-size:.78rem; font-weight:600; }
    .firma-nombre { font-size:.72rem; color:var(--text-color-secondary); }

    .nota { font-size:.76rem; color:var(--text-color-secondary); }

    @media print {
      .apex-toolbar, .nota { display:none !important; }
      .apex-page { padding:0; }
      .cab-oficial { border-color:#000; }
      .acta-table th { background:#e8e8e8 !important; -webkit-print-color-adjust:exact; }
    }
  `],
})
export class ActaEvaluacionComponent implements OnInit implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private api      = inject(ApiService);
  private notify   = inject(ApexNotificationService);
  private exporter = inject(ExportService);

  // ── Cascada: Plantel → Semestre → Grupo → Materia ────────────────────
  plantelSel  = '';
  semestreSel = '';
  grupoSel    = '';
  materiaSel  = '';

  private _grupos   = signal<GrupoRaw[]>([]);
  private _materias = signal<any[]>([]);
  cargando = signal(false);
  acta     = signal<Acta | null>(null);

  // Derivados client-side — sin peticiones extra
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

  materiaOpts = computed<MateriaOpt[]>(() =>
    this._materias().map(m => ({
      value: m['id'],
      label: `${m['nombre_materia']}${m['clave_materia'] ? ' (' + m['clave_materia'] + ')' : ''}`,
    }))
  );

  ngOnInit() {
    this.api.get<GrupoRaw[]>('/reportes/acta/grupos').subscribe({
      next: g => this._grupos.set(g),
      error: () => this.notify.error('No se pudieron cargar los grupos UAEMEX'),
    });
  }

  onPlantelChange() {
    this.semestreSel = '';
    this.grupoSel    = '';
    this.materiaSel  = '';
    this._materias.set([]);
    this.acta.set(null);
  }

  onSemestreChange() {
    this.grupoSel   = '';
    this.materiaSel = '';
    this._materias.set([]);
    this.acta.set(null);
  }

  onGrupoChange(grupoId: string) {
    this.materiaSel = '';
    this._materias.set([]);
    this.acta.set(null);
    if (!grupoId) return;
    this.api.get<any[]>(`/reportes/acta/grupos/${grupoId}/materias`).subscribe({
      next: m => this._materias.set(m),
      error: () => this.notify.error('No se pudieron cargar las materias del grupo'),
    });
  }

  generar() {
    if (!this.grupoSel || !this.materiaSel) return;
    this.cargando.set(true);
    this.acta.set(null);
    const params = { grupo_id: this.grupoSel, materia_id: this.materiaSel };
    this.api.get<Acta>('/reportes/acta', params).subscribe({
      next: a => { this.acta.set(a); this.cargando.set(false); },
      error: e => {
        this.cargando.set(false);
        this.notify.error(e.error?.message ?? 'No se pudo generar el acta');
      },
    });
  }

  fmt(v: number | null | undefined): string {
    return v == null ? '—' : Number(v).toFixed(1);
  }

  exportar() {
    const a = this.acta();
    if (!a) return;
    const cols: ExportColumn[] = [
      { field: 'num',            header: '#' },
      { field: 'alumno',         header: 'Nombre' },
      { field: 'matricula',      header: 'Matrícula' },
      { field: 'curp',           header: 'CURP' },
      { field: 'ordinario',      header: 'Ordinario',      format: v => this.fmt(v) },
      { field: 'extraordinario', header: 'Extraordinario', format: v => this.fmt(v) },
      { field: 'definitiva',     header: 'Definitiva',     format: v => this.fmt(v) },
      { field: 'estatus',        header: 'Estatus' },
      { field: 'inasistencias',  header: 'Inasistencias' },
    ];
    const data = a.alumnos.map(al => ({
      ...al,
      estatus: al.definitiva == null ? '—' : al.acreditada ? 'Acreditada' : 'No acreditada',
    }));
    const cab = a.cabecera;
    const file = `acta_${cab.semestre}_${cab.grupo}_${cab.materia}`.replace(/\s+/g, '_');
    this.exporter.toXLSX(data, cols, 'Acta UAEMEX', file);
    this.notify.success('Acta exportada');
  }

  imprimir() { window.print(); }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
