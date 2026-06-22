import { Component, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { ApexNotificationService } from 'apex-component-library';
import { ExportService, ExportColumn } from '../../core/services/export.service';

interface MateriaKardex {
  materia: string;
  clave: string | null;
  ordinario: number | null;
  extraordinario: number | null;
  definitiva: number | null;
  acreditada: boolean;
  inasistencias: number;
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

@Component({
  selector: 'app-kardex',
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonModule, InputTextModule, ToastModule],
  providers: [MessageService],
  template: `
    <p-toast />
    <div class="apex-page">
      <div class="apex-toolbar">
        <h2 class="apex-title">Kardex / Historial Académico — UAEMEX</h2>
        <div class="apex-toolbar-actions">
          <input pInputText [(ngModel)]="estudianteId" placeholder="UUID del estudiante…"
            style="width:280px" (keyup.enter)="cargar()" />
          <input pInputText [(ngModel)]="cicloId" placeholder="UUID ciclo (opcional)…" style="width:220px" />
          <p-button label="Consultar" icon="pi pi-search" size="small" (onClick)="cargar()"
            [loading]="cargando()" [disabled]="!estudianteId" />
          @if (data()?.alumno) {
            <p-button label="Excel" icon="pi pi-file-excel" size="small" severity="success"
              [outlined]="true" (onClick)="exportar()" />
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
          Introduce el UUID de un estudiante de preparatoria (UAEMEX) y consulta su kardex.
        </p>
      }
    </div>
  `,
  styles: [`
    .apex-page { padding: 1rem; }
    .apex-toolbar { display:flex; align-items:center; justify-content:space-between; margin-bottom:1rem; flex-wrap:wrap; gap:.5rem; }
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
export class KardexComponent {
  private http = inject(HttpClient);
  private notify = inject(ApexNotificationService);
  private exporter = inject(ExportService);

  estudianteId = '';
  cicloId = '';
  cargando = signal(false);
  data = signal<Kardex | null>(null);

  fmt(v: number | null): string {
    return v === null || v === undefined ? '—' : Number(v).toFixed(1);
  }

  cargar() {
    if (!this.estudianteId.trim()) return;
    this.cargando.set(true);
    this.data.set(null);
    const params: any = {};
    if (this.cicloId.trim()) params.ciclo_id = this.cicloId.trim();
    this.http.get<Kardex>(`/api/v1/reportes/kardex/${this.estudianteId.trim()}`, { params }).subscribe({
      next: d => { this.data.set(d); this.cargando.set(false); },
      error: e => {
        this.cargando.set(false);
        this.notify.error(e.error?.message ?? e.error?.detail ?? 'No se encontró kardex UAEMEX');
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
      ...m,
      estatus: m.acreditada ? 'Acreditada' : 'No acreditada',
    }));
    const file = `kardex_${k.alumno.matricula}`;
    this.exporter.toXLSX(data, columns, 'Kardex UAEMEX', file);
    this.notify.success('Kardex exportado');
  }
}
