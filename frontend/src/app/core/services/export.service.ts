/**
 * ExportService — Exportación de tablas al estilo Oracle APEX.
 *
 * Formatos soportados:
 *   - CSV  : sin dependencias externas
 *   - XLSX : SheetJS (xlsx@0.20.3), genera libro real de Excel. Corregido
 *            2026-07-16 (docs/hallazgos/2026-07-16_auditoria_gaps_no_revisados.md
 *            #3): 0.18.5 tenía 2 CVE (prototype pollution GHSA-4r6h-8v6p-xvw6,
 *            ReDoS GHSA-5pgg-2g8v-p4x9) sin fix publicado en el registro npm —
 *            SheetJS dejó de publicar parches ahí; el remedio oficial documentado
 *            por el propio proyecto es instalar desde su CDN
 *            (package.json: "xlsx": "https://cdn.sheetjs.com/xlsx-0.20.3/..."),
 *            misma API, sin cambios de código en este archivo.
 *   - PDF  : descarga desde endpoint backend (WeasyPrint)
 *
 * Uso:
 *   export.toCSV(data, columns, 'alumnos')
 *   export.toXLSX(data, columns, 'Alumnos', 'alumnos')
 *   export.toURL('/api/v1/boletas/{id}', 'boleta.pdf')
 */
import { Injectable } from '@angular/core';

export interface ExportColumn {
  field: string;          // dot-path: 'persona.nombre'
  header: string;         // label en el encabezado
  format?: (val: any) => string;  // transformación opcional
}

@Injectable({ providedIn: 'root' })
export class ExportService {

  // ── CSV ─────────────────────────────────────────────────────────────────────

  toCSV(data: any[], columns: ExportColumn[], filename: string): void {
    const header = columns.map(c => `"${c.header}"`).join(',');
    const rows = data.map(row =>
      columns.map(c => {
        const val = this._getDeep(row, c.field);
        const str = c.format ? c.format(val) : (val ?? '');
        return `"${String(str).replace(/"/g, '""')}"`;
      }).join(',')
    );
    this._download([header, ...rows].join('\n'), `${filename}.csv`, 'text/csv;charset=utf-8;');
  }

  // ── XLSX ────────────────────────────────────────────────────────────────────

  async toXLSX(data: any[], columns: ExportColumn[], sheetName: string, filename: string): Promise<void> {
    const XLSX = await import('xlsx');

    const ws_data = [
      columns.map(c => c.header),
      ...data.map(row =>
        columns.map(c => {
          const val = this._getDeep(row, c.field);
          return c.format ? c.format(val) : (val ?? '');
        })
      ),
    ];

    const ws = XLSX.utils.aoa_to_sheet(ws_data);

    // Ancho automático de columnas
    const colWidths = columns.map((c, i) => {
      const maxLen = Math.max(
        c.header.length,
        ...data.map(row => {
          const v = this._getDeep(row, c.field);
          return String(c.format ? c.format(v) : (v ?? '')).length;
        })
      );
      return { wch: Math.min(maxLen + 2, 50) };
    });
    ws['!cols'] = colWidths;

    // Estilo de encabezado (rojo Nevadi)
    const range = XLSX.utils.decode_range(ws['!ref'] || 'A1');
    for (let C = range.s.c; C <= range.e.c; C++) {
      const cellRef = XLSX.utils.encode_cell({ r: 0, c: C });
      if (ws[cellRef]) {
        ws[cellRef].s = {
          font: { bold: true, color: { rgb: 'FFFFFF' } },
          fill: { fgColor: { rgb: 'D02030' } },
          alignment: { horizontal: 'center' },
        };
      }
    }

    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, sheetName);
    XLSX.writeFile(wb, `${filename}.xlsx`);
  }

  // ── PDF desde URL backend ────────────────────────────────────────────────────

  toURL(url: string, filename: string): void {
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.target = '_blank';
    a.click();
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  private _getDeep(obj: any, path: string): any {
    return path.split('.').reduce((acc, key) => acc?.[key], obj);
  }

  private _download(content: string, filename: string, mime: string): void {
    const blob = new Blob(['﻿' + content], { type: mime }); // BOM para Excel UTF-8
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }
}
