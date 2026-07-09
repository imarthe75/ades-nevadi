import { Component, OnDestroy, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { ApexNotificationService } from 'apex-component-library';
import { ExportService, ExportColumn } from '../../core/services/export.service';

interface MatrizRow {
  nivel: string;
  grado: number;
  sexo: string;           // 'M' | 'F'
  tipo_ingreso: string;   // 'NUEVO_INGRESO' | 'REPETIDOR'
  edad: number;
  alumnos: number;
}
interface GrupoRow { nivel: string; grado: number; grupos: number; }
interface DiscapacidadRow {
  nivel: string; grado: number;
  tipo_discapacidad: string; sexo: string; alumnos: number;
}

interface Celda { hNI: number; hRep: number; mNI: number; mRep: number; }
interface FilaEdad { edad: string; celdas: Celda[]; total: number; }
interface NivelMatriz {
  nivel: string;
  grados: number[];
  filas: FilaEdad[];
  totalGrado: Celda[];
  granTotal: number;
  grupos: { grado: number; grupos: number }[];
  totalGrupos: number;
}

// Cubetas de edad oficiales por nivel (verificar topes contra el formato 911 vigente).
const BUCKETS: Record<string, { base: number; tope: number }> = {
  PRIMARIA:   { base: 6,  tope: 14 },   // «Menos de 6» … 6-14 … «15 y más»
  SECUNDARIA: { base: 11, tope: 17 },   // «Menos de 11» … 11-17 … «18 y más»
};

/**
 * Generación del Reporte 911 de la SEP México para Primaria y Secundaria.
 * Construye la matriz oficial edad×grado×sexo×tipo_ingreso y la Sección IX
 * de discapacidad. Los rangos de edad siguen las cubetas oficiales del formato
 * vigente (Primaria 6-14, Secundaria 11-17). Permite exportar a XLSX con la
 * estructura requerida para captura en la plataforma estadística de la SEP.
 * Requiere nivelAcceso ≥ 4 (AdminPlantel o AdminSistema).
 */
@Component({
  selector: 'app-estadistica-911',
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonModule, InputTextModule, ToastModule],
  providers: [MessageService],
  template: `
    <p-toast />
    <div class="apex-page">
      <div class="apex-toolbar">
        <h2 class="apex-title">Formato 911 — Inicio de cursos (SEP)</h2>
        <div class="apex-toolbar-actions">
          <input pInputText [(ngModel)]="cicloId" placeholder="UUID ciclo (opcional)…" style="width:260px" />
          <p-button label="Generar" icon="pi pi-refresh" size="small" (onClick)="cargar()" [loading]="cargando()" />
        </div>
      </div>

      <p class="nota">
        Cifras de <strong>inicio de cursos</strong> para transcribir a la plataforma oficial
        <strong>f911</strong> de la SEP. Edad cumplida al <strong>31 de diciembre</strong> del año de
        inicio del ciclo. ADES no realiza la captura oficial; solo pre-calcula.
      </p>

      @for (n of niveles(); track n.nivel) {
        <div class="bloque">
          <div class="bloque-head">
            <h3>{{ n.nivel }}</h3>
            <p-button label="Exportar Excel" icon="pi pi-file-excel" size="small" severity="success"
              [outlined]="true" (onClick)="exportar(n)" />
          </div>

          <!-- IV.1 — Alumnado por grado, sexo, ingreso y edad -->
          <div class="tabla-scroll">
            <table class="t911">
              <thead>
                <tr>
                  <th rowspan="2">Edad</th>
                  @for (g of n.grados; track g) { <th colspan="4">{{ g }}°</th> }
                  <th rowspan="2">Total</th>
                </tr>
                <tr>
                  @for (g of n.grados; track g) {
                    <th class="sub">H·NI</th><th class="sub">H·Rep</th>
                    <th class="sub">M·NI</th><th class="sub">M·Rep</th>
                  }
                </tr>
              </thead>
              <tbody>
                @for (f of n.filas; track f.edad) {
                  <tr>
                    <td class="edad">{{ f.edad }}</td>
                    @for (c of f.celdas; track $index) {
                      <td>{{ c.hNI || '' }}</td><td>{{ c.hRep || '' }}</td>
                      <td>{{ c.mNI || '' }}</td><td>{{ c.mRep || '' }}</td>
                    }
                    <td class="tot">{{ f.total || '' }}</td>
                  </tr>
                }
                <tr class="total-row">
                  <td class="edad">Total</td>
                  @for (c of n.totalGrado; track $index) {
                    <td>{{ c.hNI }}</td><td>{{ c.hRep }}</td><td>{{ c.mNI }}</td><td>{{ c.mRep }}</td>
                  }
                  <td class="tot">{{ n.granTotal }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- IV.2 — Grupos por grado -->
          <div class="grupos">
            <strong>Grupos por grado:</strong>
            @for (g of n.grupos; track g.grado) {
              <span class="chip">{{ g.grado }}°: {{ g.grupos }}</span>
            }
            <span class="chip total">Total: {{ n.totalGrupos }}</span>
          </div>
        </div>
      }

      <!-- IX — Alumnado con discapacidad -->
      @if (discapacidadRows().length > 0) {
        <div class="bloque">
          <div class="bloque-head">
            <h3>Sección IX — Alumnado con discapacidad</h3>
            <p-button label="Exportar Excel" icon="pi pi-file-excel" size="small" severity="success"
              [outlined]="true" (onClick)="exportarDiscapacidad()" />
          </div>
          <div class="tabla-scroll">
            <table class="t911">
              <thead>
                <tr>
                  <th>Nivel</th><th>Grado</th>
                  <th>Tipo de discapacidad</th>
                  <th>Hombres</th><th>Mujeres</th><th>Total</th>
                </tr>
              </thead>
              <tbody>
                @for (r of discapacidadRows(); track r.key) {
                  <tr>
                    <td>{{ r.nivel }}</td>
                    <td>{{ r.grado }}°</td>
                    <td class="edad">{{ r.tipo }}</td>
                    <td>{{ r.hombres || '' }}</td>
                    <td>{{ r.mujeres || '' }}</td>
                    <td class="tot">{{ r.total }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      }

      @if (!cargando() && niveles().length === 0) {
        <p class="text-center text-gray-500 py-6">Sin datos para los filtros seleccionados.</p>
      }
    </div>
  `,
  styles: [`
    .apex-page { padding: 1rem; }
    .apex-toolbar { display:flex; align-items:center; justify-content:space-between; margin-bottom:.5rem; flex-wrap:wrap; gap:.5rem; }
    .apex-title { margin:0; font-size:1.25rem; font-weight:600; }
    .apex-toolbar-actions { display:flex; gap:.5rem; align-items:center; }
    .nota { background:var(--surface-100); border-left:4px solid var(--primary-400); padding:.6rem .8rem; border-radius:4px; font-size:.85rem; margin:.5rem 0 1rem; }
    .bloque { margin-bottom:1.75rem; }
    .bloque-head { display:flex; align-items:center; justify-content:space-between; margin-bottom:.5rem; }
    .bloque-head h3 { margin:0; font-size:1.05rem; font-weight:600; }
    .tabla-scroll { overflow-x:auto; border:1px solid var(--surface-300); border-radius:6px; }
    .t911 { border-collapse:collapse; font-size:.78rem; min-width:100%; }
    .t911 th, .t911 td { border:1px solid var(--surface-300); padding:.25rem .4rem; text-align:center; }
    .t911 thead th { background:var(--surface-200); font-weight:600; white-space:nowrap; }
    .t911 th.sub { font-weight:500; font-size:.7rem; background:var(--surface-100); }
    .t911 td.edad { text-align:left; font-weight:500; white-space:nowrap; background:var(--surface-50); }
    .t911 td.tot { font-weight:700; background:var(--surface-50); }
    .t911 tr.total-row td { font-weight:700; background:var(--surface-200); }
    .grupos { margin-top:.6rem; display:flex; gap:.4rem; align-items:center; flex-wrap:wrap; font-size:.85rem; }
    .chip { background:var(--surface-200); border-radius:12px; padding:.15rem .6rem; }
    .chip.total { background:var(--primary-100); font-weight:600; }
  `],
})
export class Estadistica911Component implements OnInit implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private api = inject(ApiService);
  private notify = inject(ApexNotificationService);
  private exporter = inject(ExportService);

  cicloId = '';
  cargando = signal(false);
  private matriz       = signal<MatrizRow[]>([]);
  private grupos       = signal<GrupoRow[]>([]);
  private discapacidad = signal<DiscapacidadRow[]>([]);

  ngOnInit() { this.cargar(); }

  cargar() {
    this.cargando.set(true);
    const params: any = {};

    if (this.cicloId.trim()) {
      if (!this.isValidUUID(this.cicloId.trim())) {
        this.cargando.set(false);
        this.notify.error('Error', 'El UUID del ciclo no es válido');
        return;
      }
      params.ciclo_id = this.cicloId.trim();
    }

    this.api.get<any>('/reportes/911', params).subscribe({
      next: d => {
        const matriz = d.matricula_por_grado_sexo_ingreso_edad ?? [];

        if (!matriz || matriz.length === 0) {
          this.notify.warning('Advertencia', 'No hay datos de alumnos para generar el reporte');
          this.cargando.set(false);
          return;
        }

        const validationIssues = this.validarMatriz(matriz);
        if (validationIssues.length > 0) {
          this.notify.warning('Advertencia', `${validationIssues.length} registros tienen datos incompletos. El reporte puede no ser exacto.`);
        }

        this.matriz.set(matriz);
        this.grupos.set(d.grupos_por_grado ?? []);
        this.discapacidad.set(d.discapacidad_por_grado_sexo ?? []);
        this.cargando.set(false);
        this.notify.success('Éxito', 'Reporte generado correctamente');
      },
      error: e => {
        this.cargando.set(false);
        this.notify.error('Error', e.error?.detail ?? e.error?.message ?? 'Error al generar el reporte 911');
      },
    });
  }

  private isValidUUID(uuid: string): boolean {
    const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    return uuidRegex.test(uuid);
  }

  private validarMatriz(matriz: MatrizRow[]): string[] {
    const issues: string[] = [];
    for (const row of matriz) {
      if (!row.sexo || (row.sexo !== 'M' && row.sexo !== 'F')) {
        issues.push(`Alumno sin sexo válido: ${row}`);
      }
      if (!row.tipo_ingreso || (row.tipo_ingreso !== 'NUEVO_INGRESO' && row.tipo_ingreso !== 'REPETIDOR')) {
        issues.push(`Alumno sin tipo_ingreso válido: ${row}`);
      }
      if (row.edad == null || row.edad < 0) {
        issues.push(`Alumno con edad inválida: ${row}`);
      }
    }
    return issues.slice(0, 5); // Solo primeros 5 para no saturar
  }

  private bucket(nivel: string, edad: number): string {
    const cfg = BUCKETS[nivel] ?? { base: 6, tope: 14 };
    if (edad < cfg.base) return `Menos de ${cfg.base}`;
    if (edad > cfg.tope) return `${cfg.tope + 1} y más`;
    return `${edad}`;
  }

  private buckets(nivel: string): string[] {
    const cfg = BUCKETS[nivel] ?? { base: 6, tope: 14 };
    const out = [`Menos de ${cfg.base}`];
    for (let e = cfg.base; e <= cfg.tope; e++) out.push(`${e}`);
    out.push(`${cfg.tope + 1} y más`);
    return out;
  }

  readonly niveles = computed<NivelMatriz[]>(() => {
    const rows = this.matriz();
    const grpAll = this.grupos();
    const nivelNames = [...new Set(rows.map(r => r.nivel))].sort();

    return nivelNames.map(nivel => {
      const rs = rows.filter(r => r.nivel === nivel);
      const grados = [...new Set(rs.map(r => r.grado))].sort((a, b) => a - b);
      const edades = this.buckets(nivel);

      // key: grado|sexo|ingreso|bucket -> alumnos
      const m = new Map<string, number>();
      for (const r of rs) {
        const k = `${r.grado}|${r.sexo}|${r.tipo_ingreso}|${this.bucket(nivel, r.edad)}`;
        m.set(k, (m.get(k) ?? 0) + r.alumnos);
      }
      const cell = (g: number, s: string, ti: string, b: string) => m.get(`${g}|${s}|${ti}|${b}`) ?? 0;

      const filas: FilaEdad[] = edades.map(b => {
        const celdas = grados.map<Celda>(g => ({
          hNI:  cell(g, 'M', 'NUEVO_INGRESO', b),
          hRep: cell(g, 'M', 'REPETIDOR', b),
          mNI:  cell(g, 'F', 'NUEVO_INGRESO', b),
          mRep: cell(g, 'F', 'REPETIDOR', b),
        }));
        const total = celdas.reduce((s, c) => s + c.hNI + c.hRep + c.mNI + c.mRep, 0);
        return { edad: b, celdas, total };
      });

      const totalGrado = grados.map<Celda>((g, i) => ({
        hNI:  filas.reduce((s, f) => s + f.celdas[i].hNI, 0),
        hRep: filas.reduce((s, f) => s + f.celdas[i].hRep, 0),
        mNI:  filas.reduce((s, f) => s + f.celdas[i].mNI, 0),
        mRep: filas.reduce((s, f) => s + f.celdas[i].mRep, 0),
      }));
      const granTotal = totalGrado.reduce((s, c) => s + c.hNI + c.hRep + c.mNI + c.mRep, 0);

      const grupos = grpAll.filter(g => g.nivel === nivel)
        .map(g => ({ grado: g.grado, grupos: g.grupos }))
        .sort((a, b) => a.grado - b.grado);
      const totalGrupos = grupos.reduce((s, g) => s + g.grupos, 0);

      return { nivel, grados, filas, totalGrado, granTotal, grupos, totalGrupos };
    });
  });

  readonly discapacidadRows = computed(() => {
    const rows = this.discapacidad();
    const keys = [...new Set(rows.map(r => `${r.nivel}|${r.grado}|${r.tipo_discapacidad}`))].sort();
    return keys.map(key => {
      const [nivel, grado, tipo] = key.split('|');
      const hRow = rows.find(r => r.nivel === nivel && String(r.grado) === grado && r.tipo_discapacidad === tipo && r.sexo === 'M');
      const mRow = rows.find(r => r.nivel === nivel && String(r.grado) === grado && r.tipo_discapacidad === tipo && r.sexo === 'F');
      const hombres = hRow?.alumnos ?? 0;
      const mujeres = mRow?.alumnos ?? 0;
      return { key, nivel, grado: Number(grado), tipo: tipo.replace('DISCAPACIDAD_', '').replace(/_/g, ' '), hombres, mujeres, total: hombres + mujeres };
    });
  });

  exportarDiscapacidad() {
    const columns: ExportColumn[] = [
      { field: 'nivel',   header: 'Nivel' },
      { field: 'grado',   header: 'Grado' },
      { field: 'tipo',    header: 'Tipo de discapacidad' },
      { field: 'hombres', header: 'Hombres' },
      { field: 'mujeres', header: 'Mujeres' },
      { field: 'total',   header: 'Total' },
    ];
    this.exporter.toXLSX(this.discapacidadRows(), columns, '911 Sección IX', '911_discapacidad');
    this.notify.success('Exportado Sección IX discapacidad');
  }

  exportar(n: NivelMatriz) {
    const columns: ExportColumn[] = [{ field: 'edad', header: 'Edad' }];
    for (const g of n.grados) {
      columns.push({ field: `g${g}_hNI`,  header: `${g}° H·NI` });
      columns.push({ field: `g${g}_hRep`, header: `${g}° H·Rep` });
      columns.push({ field: `g${g}_mNI`,  header: `${g}° M·NI` });
      columns.push({ field: `g${g}_mRep`, header: `${g}° M·Rep` });
    }
    columns.push({ field: 'total', header: 'Total' });

    const data = n.filas.map(f => {
      const row: any = { edad: f.edad, total: f.total };
      n.grados.forEach((g, i) => {
        row[`g${g}_hNI`]  = f.celdas[i].hNI;
        row[`g${g}_hRep`] = f.celdas[i].hRep;
        row[`g${g}_mNI`]  = f.celdas[i].mNI;
        row[`g${g}_mRep`] = f.celdas[i].mRep;
      });
      return row;
    });
    const totalRow: any = { edad: 'Total', total: n.granTotal };
    n.grados.forEach((g, i) => {
      totalRow[`g${g}_hNI`]  = n.totalGrado[i].hNI;
      totalRow[`g${g}_hRep`] = n.totalGrado[i].hRep;
      totalRow[`g${g}_mNI`]  = n.totalGrado[i].mNI;
      totalRow[`g${g}_mRep`] = n.totalGrado[i].mRep;
    });
    data.push(totalRow);

    this.exporter.toXLSX(data, columns, `911_${n.nivel}`, `911_${n.nivel.toLowerCase()}`);
    this.notify.success(`Exportado 911 ${n.nivel}`);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
