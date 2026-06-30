#!/usr/bin/env python3
"""
03_REPORT_GENERATOR.PY - Generación de Reportes Ejecutivos
Lee inconsistencies_report.json, genera:
  - HTML interactivo (tabla, filtros, estadísticas)
  - CSV para integración Jira/Azure DevOps
  - Matriz de trazabilidad

Ejecución:
  python 03_report_generator.py
"""

import json
from pathlib import Path
from typing import Dict, List, Any
import logging
from datetime import datetime

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s'
)
logger = logging.getLogger(__name__)

SCRIPT_DIR = Path(__file__).parent.absolute()
ANALYSIS_DIR = SCRIPT_DIR / "analysis"
OUTPUT_DIR = SCRIPT_DIR / "reports"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
CONFIG_PATH = SCRIPT_DIR / "config_ades_modules.json"


class ReportGenerator:
    def __init__(self):
        self.report_data = self._load_report()
        self.config = self._load_config()

    def _load_report(self) -> Dict:
        """Cargar reporte de inconsistencias."""
        report_path = ANALYSIS_DIR / "inconsistencies_report.json"
        if report_path.exists():
            with open(report_path) as f:
                return json.load(f)
        return {"inconsistencies": []}

    def _load_config(self) -> Dict:
        """Cargar configuración de módulos."""
        with open(CONFIG_PATH) as f:
            return json.load(f)

    def run(self):
        """Generar todos los reportes."""
        logger.info("Generando reportes ejecutivos")
        
        self._generate_html_report()
        self._generate_jira_csv()
        self._generate_traceability_matrix()
        self._generate_summary_txt()

    def _generate_html_report(self):
        """Generar HTML interactivo con Tailwind CSS."""
        
        inconsistencies = self.report_data.get("inconsistencies", [])
        summary = self.report_data.get("by_severity", {})
        
        html = f"""<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ADES Testing Exploratorio - Reporte de Inconsistencias</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    <style>
        body {{ font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; }}
        .severity-critico {{ background-color: #fecaca; color: #991b1b; }}
        .severity-alto {{ background-color: #fed7aa; color: #92400e; }}
        .severity-medio {{ background-color: #fef3c7; color: #78350f; }}
        .severity-bajo {{ background-color: #d1fae5; color: #065f46; }}
        
        .badge-critico {{ background: #dc2626; color: white; }}
        .badge-alto {{ background: #ea580c; color: white; }}
        .badge-medio {{ background: #eab308; color: black; }}
        .badge-bajo {{ background: #10b981; color: white; }}
    </style>
</head>
<body class="bg-gray-50">
    <div class="container mx-auto px-4 py-8">
        <!-- Header -->
        <div class="bg-white shadow rounded-lg p-6 mb-6">
            <h1 class="text-3xl font-bold text-gray-900 mb-2">ADES Nevadi - Testing Exploratorio</h1>
            <p class="text-gray-600">Reporte de Inconsistencias Detectadas</p>
            <p class="text-sm text-gray-500 mt-4">Generado: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}</p>
        </div>
        
        <!-- Estadísticas -->
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
        
        <!-- Tabla de Inconsistencias -->
        <div class="bg-white shadow rounded-lg overflow-hidden mb-6">
            <div class="px-6 py-4 bg-gray-100 border-b">
                <h2 class="text-lg font-semibold text-gray-900">Inconsistencias Detectadas</h2>
                <p class="text-sm text-gray-600 mt-1">Total: {len(inconsistencies)} inconsistencias</p>
            </div>
            
            <div class="overflow-x-auto">
                <table class="w-full">
                    <thead class="bg-gray-50 border-b">
                        <tr>
                            <th class="px-6 py-3 text-left text-xs font-medium text-gray-700 uppercase">Severidad</th>
                            <th class="px-6 py-3 text-left text-xs font-medium text-gray-700 uppercase">Módulo</th>
                            <th class="px-6 py-3 text-left text-xs font-medium text-gray-700 uppercase">Tipo</th>
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
                            <td class="px-6 py-4 text-sm text-gray-700">
                                {inc.get('type', 'Unknown')}
                            </td>
                            <td class="px-6 py-4 text-sm text-gray-700 max-w-md">
                                <details class="cursor-pointer">
                                    <summary class="font-medium">{inc.get('description', 'N/A')[:60]}...</summary>
                                    <p class="mt-2 p-2 bg-gray-50 rounded text-xs">{inc.get('description', 'N/A')}</p>
                                    <p class="mt-2 text-xs text-gray-600"><strong>Evidencia:</strong> {inc.get('evidence', 'N/A')[:100]}</p>
                                </details>
                            </td>
                            <td class="px-6 py-4 text-sm font-mono text-gray-600">
                                {inc.get('location', 'N/A')}
                            </td>
                            <td class="px-6 py-4 text-sm text-gray-700">
                                {inc.get('suggestion', 'N/A')}
                            </td>
                        </tr>
"""
        
        html += """
                    </tbody>
                </table>
            </div>
        </div>
        
        <!-- Distribuición por Tipo -->
        <div class="bg-white shadow rounded-lg p-6 mb-6">
            <h2 class="text-lg font-semibold text-gray-900 mb-4">Inconsistencias por Tipo</h2>
            <canvas id="typeChart"></canvas>
        </div>
        
        <!-- Instrucciones de Corrección -->
        <div class="bg-blue-50 border border-blue-200 rounded-lg p-6 mb-6">
            <h2 class="text-lg font-semibold text-blue-900 mb-4">Próximos Pasos</h2>
            <ol class="list-decimal list-inside space-y-2 text-blue-800">
                <li>Priorizar por severidad: Críticas primero</li>
                <li>Agrupar por módulo para correcciones coordinadas</li>
                <li>Crear tasks en backlog (usar CSV para Jira)</li>
                <li>Re-testear con este mismo framework después de correcciones</li>
                <li>Generar baseline de "estado esperado" para futuras ejecuciones</li>
            </ol>
        </div>
        
    </div>
    
    <script>
        const typeData = {json.dumps(self.report_data.get('by_type', {}))};
        
        const ctx = document.getElementById('typeChart');
        if (ctx) {{
            new Chart(ctx, {{
                type: 'bar',
                data: {{
                    labels: Object.keys(typeData),
                    datasets: [{{
                        label: 'Número de Inconsistencias',
                        data: Object.values(typeData),
                        backgroundColor: [
                            '#dc2626', '#ea580c', '#eab308', '#10b981', '#3b82f6'
                        ]
                    }}]
                }},
                options: {{
                    responsive: true,
                    plugins: {{
                        legend: {{ display: false }}
                    }},
                    scales: {{
                        y: {{ beginAtZero: true }}
                    }}
                }}
            }});
        }}
    </script>
</body>
</html>
"""
        
        html_path = OUTPUT_DIR / "inconsistencies_report.html"
        with open(html_path, "w", encoding="utf-8") as f:
            f.write(html)
        
        logger.info(f"✓ HTML generado: {html_path}")

    def _generate_jira_csv(self):
        """Generar CSV para importar a Jira."""
        import csv
        
        inconsistencies = self.report_data.get("inconsistencies", [])
        
        csv_path = OUTPUT_DIR / "jira_issues.csv"
        with open(csv_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(
                f,
                fieldnames=[
                    "Summary", "Description", "Type", "Priority", 
                    "Component", "Labels", "Assignee", "Story Points"
                ]
            )
            writer.writeheader()
            
            priority_map = {
                "Crítico": "Highest",
                "Alto": "High",
                "Medio": "Medium",
                "Bajo": "Low"
            }
            
            for inc in inconsistencies:
                writer.writerow({
                    "Summary": f"[{inc.get('module_id', 'N/A')}] {inc.get('description', '')[:100]}",
                    "Description": f"""{inc.get('description', '')}
                    
Location: {inc.get('location', '')}
Type: {inc.get('type', '')}
Evidence: {inc.get('evidence', '')}
Impact: {inc.get('impact', '')}
Suggestion: {inc.get('suggestion', '')}""",
                    "Type": "Bug",
                    "Priority": priority_map.get(inc.get('severity', 'Bajo'), 'Low'),
                    "Component": inc.get('module_id', ''),
                    "Labels": f"ADES,QA-Testing,{inc.get('type', '').replace(' ', '-')}",
                    "Assignee": "",
                    "Story Points": "3" if inc.get('severity') == 'Crítico' else "2" if inc.get('severity') == 'Alto' else "1"
                })
        
        logger.info(f"✓ CSV Jira generado: {csv_path}")

    def _generate_traceability_matrix(self):
        """Generar matriz de trazabilidad: Módulo vs Inconsistencias."""
        import csv
        
        inconsistencies = self.report_data.get("inconsistencies", [])
        
        # Agrupar por módulo
        by_module = {}
        for inc in inconsistencies:
            module_id = inc.get("module_id", "Unknown")
            if module_id not in by_module:
                by_module[module_id] = {"critico": 0, "alto": 0, "medio": 0, "bajo": 0}
            
            severity_raw = inc.get("severity", "Bajo").lower()
            severity = (severity_raw
                        .replace('í', 'i').replace('á', 'a')
                        .replace('é', 'e').replace('ó', 'o').replace('ú', 'u'))
            by_module[module_id].setdefault(severity, 0)
            by_module[module_id][severity] += 1
        
        # Generar CSV
        csv_path = OUTPUT_DIR / "traceability_matrix.csv"
        with open(csv_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(
                f,
                fieldnames=["Módulo", "Críticas", "Altas", "Medias", "Bajas", "Total", "Estado"]
            )
            writer.writeheader()
            
            for module_id in sorted(by_module.keys()):
                data = by_module[module_id]
                total = sum(data.values())
                status = "🔴 Crítico" if data["critico"] > 0 else "🟠 Alto" if data["alto"] > 0 else "🟡 Medio" if data["medio"] > 0 else "🟢 OK"
                
                writer.writerow({
                    "Módulo": module_id,
                    "Críticas": data["critico"],
                    "Altas": data["alto"],
                    "Medias": data["medio"],
                    "Bajas": data["bajo"],
                    "Total": total,
                    "Estado": status
                })
        
        logger.info(f"✓ Matriz trazabilidad generada: {csv_path}")

    def _generate_summary_txt(self):
        """Generar resumen en texto plano para CLI."""
        
        summary = self.report_data.get("by_severity", {})
        total = sum(summary.values())
        by_type = self.report_data.get("by_type", {})
        
        txt = f"""
╔═════════════════════════════════════════════════════════════════════════════╗
║                   ADES NEVADI - TESTING EXPLORATORIO                         ║
║                      REPORTE DE INCONSISTENCIAS                              ║
╚═════════════════════════════════════════════════════════════════════════════╝

Generado: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}

─────────────────────────────────────────────────────────────────────────────
RESUMEN DE SEVERIDAD
─────────────────────────────────────────────────────────────────────────────
  🔴 CRÍTICAS:  {summary.get('critico', 0):3d}  (Bloquean funcionalidad)
  🟠 ALTAS:     {summary.get('alto', 0):3d}  (Afectan UX significativamente)
  🟡 MEDIAS:    {summary.get('medio', 0):3d}  (Mejoras necesarias)
  🟢 BAJAS:     {summary.get('bajo', 0):3d}  (Sugerencias)
  ─────────────
  TOTAL:        {total:3d}

─────────────────────────────────────────────────────────────────────────────
TIPOS DE INCONSISTENCIAS
─────────────────────────────────────────────────────────────────────────────
"""
        
        for itype, count in sorted(by_type.items(), key=lambda x: x[1], reverse=True):
            txt += f"  • {itype}: {count}\n"
        
        txt += """
─────────────────────────────────────────────────────────────────────────────
PRÓXIMOS PASOS
─────────────────────────────────────────────────────────────────────────────
  1. Revisar HTML ejecutivo: inconsistencies_report.html
  2. Descargar CSV para Jira: jira_issues.csv
  3. Analizar matriz de trazabilidad: traceability_matrix.csv
  4. Priorizar correcciones por severidad
  5. Crear tasks en backlog
  6. Re-ejecutar testing después de correcciones

Reportes generados:
  ✓ HTML interactivo: inconsistencies_report.html
  ✓ CSV para Jira: jira_issues.csv
  ✓ Matriz trazabilidad: traceability_matrix.csv

═════════════════════════════════════════════════════════════════════════════
"""
        
        txt_path = OUTPUT_DIR / "REPORTE_RESUMEN.txt"
        with open(txt_path, "w", encoding="utf-8") as f:
            f.write(txt)
        
        logger.info(txt)
        logger.info(f"✓ Resumen texto generado: {txt_path}")


def main():
    """Punto de entrada."""
    generator = ReportGenerator()
    generator.run()
    
    logger.info(f"\n✓ Todos los reportes generados en {OUTPUT_DIR}/")


if __name__ == "__main__":
    main()
