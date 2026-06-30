#!/usr/bin/env python3
"""
03_REPORT_GENERATOR.PY - PORTABLE VERSION
Generación de reportes (funciona desde cualquier directorio)
"""

import json
from pathlib import Path
from typing import Dict, List, Any
from datetime import datetime
import logging

logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')
logger = logging.getLogger(__name__)

# PORTABLE: detectar directorio actual
SCRIPT_DIR = Path(__file__).parent.absolute()
ANALYSIS_DIR = SCRIPT_DIR / "analysis"
OUTPUT_DIR = SCRIPT_DIR / "reports"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

logger.info(f"Working directory: {SCRIPT_DIR}")
logger.info(f"Output dir: {OUTPUT_DIR}")


class ReportGenerator:
    def __init__(self):
        self.report_data = self._load_report()
        self.config = self._load_config()

    def _load_report(self) -> Dict:
        report_path = ANALYSIS_DIR / "inconsistencies_report.json"
        if not report_path.exists():
            logger.error(f"No hay análisis. Ejecuta 02_claude_qa_analyzer.py primero")
            return {"inconsistencies": []}
        with open(report_path) as f:
            return json.load(f)

    def _load_config(self) -> Dict:
        config_path = SCRIPT_DIR / "config_ades_modules.json"
        with open(config_path) as f:
            return json.load(f)

    def run(self):
        logger.info("Generando reportes ejecutivos")
        
        self._generate_html_report()
        self._generate_jira_csv()
        self._generate_summary_txt()

    def _generate_html_report(self):
        inconsistencies = self.report_data.get("inconsistencies", [])
        summary = self.report_data.get("by_severity", {})
        
        html = f"""<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ADES Testing - Reporte de Inconsistencias</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <style>
        .severity-critico {{ background-color: #fecaca; color: #991b1b; }}
        .severity-alto {{ background-color: #fed7aa; color: #92400e; }}
        .badge-critico {{ background: #dc2626; color: white; }}
        .badge-alto {{ background: #ea580c; color: white; }}
        .badge-medio {{ background: #eab308; color: black; }}
        .badge-bajo {{ background: #10b981; color: white; }}
    </style>
</head>
<body class="bg-gray-50">
    <div class="container mx-auto px-4 py-8">
        <div class="bg-white shadow rounded-lg p-6 mb-6">
            <h1 class="text-3xl font-bold text-gray-900 mb-2">ADES Nevadi - Testing Exploratorio</h1>
            <p class="text-sm text-gray-500 mt-4">Generado: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}</p>
        </div>
        
        <div class="grid grid-cols-4 gap-4 mb-6">
            <div class="bg-red-50 rounded-lg p-4">
                <div class="text-3xl font-bold text-red-600">{summary.get('critico', 0)}</div>
                <div class="text-sm text-gray-600 mt-1">Críticas</div>
            </div>
            <div class="bg-orange-50 rounded-lg p-4">
                <div class="text-3xl font-bold text-orange-600">{summary.get('alto', 0)}</div>
                <div class="text-sm text-gray-600 mt-1">Altas</div>
            </div>
            <div class="bg-yellow-50 rounded-lg p-4">
                <div class="text-3xl font-bold text-yellow-600">{summary.get('medio', 0)}</div>
                <div class="text-sm text-gray-600 mt-1">Medias</div>
            </div>
            <div class="bg-green-50 rounded-lg p-4">
                <div class="text-3xl font-bold text-green-600">{summary.get('bajo', 0)}</div>
                <div class="text-sm text-gray-600 mt-1">Bajas</div>
            </div>
        </div>
        
        <div class="bg-white shadow rounded-lg overflow-hidden">
            <div class="px-6 py-4 bg-gray-100 border-b">
                <h2 class="text-lg font-semibold text-gray-900">Inconsistencias</h2>
                <p class="text-sm text-gray-600 mt-1">Total: {len(inconsistencies)}</p>
            </div>
            
            <div class="overflow-x-auto">
                <table class="w-full">
                    <thead class="bg-gray-50 border-b">
                        <tr>
                            <th class="px-6 py-3 text-left text-xs font-medium text-gray-700 uppercase">Severidad</th>
                            <th class="px-6 py-3 text-left text-xs font-medium text-gray-700 uppercase">Módulo</th>
                            <th class="px-6 py-3 text-left text-xs font-medium text-gray-700 uppercase">Descripción</th>
                            <th class="px-6 py-3 text-left text-xs font-medium text-gray-700 uppercase">Ubicación</th>
                            <th class="px-6 py-3 text-left text-xs font-medium text-gray-700 uppercase">Sugerencia</th>
                        </tr>
                    </thead>
                    <tbody class="divide-y">
"""
        
        for inc in inconsistencies:
            severity = inc.get("severity", "Bajo").lower()
            severity_class = f"badge-{severity}"
            
            html += f"""
                        <tr class="hover:bg-gray-50">
                            <td class="px-6 py-4 whitespace-nowrap">
                                <span class="px-3 py-1 rounded-full text-xs font-medium {severity_class}">
                                    {inc.get('severity', 'Bajo')}
                                </span>
                            </td>
                            <td class="px-6 py-4 whitespace-nowrap font-mono text-sm text-gray-900">
                                {inc.get('module_id', 'N/A')}
                            </td>
                            <td class="px-6 py-4 text-sm text-gray-700 max-w-md">
                                {inc.get('description', 'N/A')[:100]}
                            </td>
                            <td class="px-6 py-4 text-sm font-mono text-gray-600">
                                {inc.get('location', 'N/A')[:50]}
                            </td>
                            <td class="px-6 py-4 text-sm text-gray-700">
                                {inc.get('suggestion', 'N/A')[:80]}
                            </td>
                        </tr>
"""
        
        html += """
                    </tbody>
                </table>
            </div>
        </div>
    </div>
</body>
</html>
"""
        
        html_path = OUTPUT_DIR / "inconsistencies_report.html"
        with open(html_path, "w", encoding="utf-8") as f:
            f.write(html)
        
        logger.info(f"✓ HTML generado: {html_path}")

    def _generate_jira_csv(self):
        import csv
        
        inconsistencies = self.report_data.get("inconsistencies", [])
        
        csv_path = OUTPUT_DIR / "jira_issues.csv"
        with open(csv_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(
                f,
                fieldnames=["Summary", "Description", "Type", "Priority", "Component", "Labels"]
            )
            writer.writeheader()
            
            priority_map = {"Crítico": "Highest", "Alto": "High", "Medio": "Medium", "Bajo": "Low"}
            
            for inc in inconsistencies:
                writer.writerow({
                    "Summary": f"[{inc.get('module_id', 'N/A')}] {inc.get('description', '')[:100]}",
                    "Description": f"{inc.get('description', '')}\n\nLocation: {inc.get('location', '')}\nImpact: {inc.get('impact', '')}\nSuggestion: {inc.get('suggestion', '')}",
                    "Type": "Bug",
                    "Priority": priority_map.get(inc.get('severity', 'Bajo'), 'Low'),
                    "Component": inc.get('module_id', ''),
                    "Labels": f"ADES,QA-Testing,{inc.get('type', '').replace(' ', '-')}"
                })
        
        logger.info(f"✓ CSV Jira generado: {csv_path}")

    def _generate_summary_txt(self):
        summary = self.report_data.get("by_severity", {})
        total = sum(summary.values())
        
        txt = f"""
╔═════════════════════════════════════════════════════════════════════════╗
║                   ADES NEVADI - TESTING EXPLORATORIO                     ║
║                      REPORTE DE INCONSISTENCIAS                          ║
╚═════════════════════════════════════════════════════════════════════════╝

Generado: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}

RESUMEN DE SEVERIDAD
─────────────────────────────────────────────────────────
  🔴 CRÍTICAS:  {summary.get('critico', 0):3d}  (Bloquean funcionalidad)
  🟠 ALTAS:     {summary.get('alto', 0):3d}  (Afectan UX)
  🟡 MEDIAS:    {summary.get('medio', 0):3d}  (Mejoras)
  🟢 BAJAS:     {summary.get('bajo', 0):3d}  (Sugerencias)
  ─────────────
  TOTAL:        {total:3d}

REPORTES GENERADOS
─────────────────────────────────────────────────────────
✓ inconsistencies_report.html   ← Abrir en navegador
✓ jira_issues.csv               ← Importar a Jira
✓ REPORTE_RESUMEN.txt           ← Este archivo

═════════════════════════════════════════════════════════════════════════
"""
        
        txt_path = OUTPUT_DIR / "REPORTE_RESUMEN.txt"
        with open(txt_path, "w", encoding="utf-8") as f:
            f.write(txt)
        
        logger.info(txt)
        logger.info(f"✓ Resumen generado: {txt_path}")


def main():
    generator = ReportGenerator()
    generator.run()
    logger.info(f"\n✓ Reportes en {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
