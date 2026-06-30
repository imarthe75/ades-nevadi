#!/usr/bin/env python3
"""
02_NIM_QA_ANALYZER.PY - Análisis QA con NVIDIA NIM (OpenAI-compatible)
Lee capturas del explorador, actúa como QA Senior, detecta inconsistencias.

Ejecución:
  python 02_claude_qa_analyzer.py

Requiere en /opt/ades/.env:
  OPENAI_API_KEY=nvapi-...
  OPENAI_BASE_URL=https://integrate.api.nvidia.com/v1
  OPENAI_MODEL=meta/llama-3.1-70b-instruct

Genera: analysis/inconsistencies_report.json + analysis/inconsistencies_report.csv
"""

import json
import os
import csv
from pathlib import Path
from typing import Dict, List, Any
import logging
from dotenv import load_dotenv

SCRIPT_DIR = Path(__file__).parent.absolute()

# Cargar vars locales primero, luego las del sistema sin sobreescribir
load_dotenv(SCRIPT_DIR / ".env")
load_dotenv(Path("/opt/ades/.env"), override=False)

try:
    from openai import OpenAI
except ImportError:
    print("ERROR: pip install openai")
    exit(1)

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

CAPTURES_DIR = SCRIPT_DIR / "captures"
OUTPUT_DIR = SCRIPT_DIR / "analysis"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

NIM_API_KEY = os.getenv("OPENAI_API_KEY", "")
NIM_BASE_URL = os.getenv("OPENAI_BASE_URL", "https://integrate.api.nvidia.com/v1")
NIM_MODEL = os.getenv("OPENAI_MODEL", "meta/llama-3.1-70b-instruct")


class AdesQAAnalyzer:
    def __init__(self):
        self.config = self._load_config()
        self.captures_summary = self._load_captures_summary()
        self.inconsistencies: List[Dict[str, Any]] = []

        self.client = OpenAI(api_key=NIM_API_KEY, base_url=NIM_BASE_URL)
        logger.info(f"Modelo NIM: {NIM_MODEL}")
        logger.info(f"Base URL: {NIM_BASE_URL}")

    def _load_config(self) -> Dict:
        config_path = SCRIPT_DIR / "config_ades_modules.json"
        with open(config_path) as f:
            return json.load(f)

    def _load_captures_summary(self) -> Dict:
        summary_path = CAPTURES_DIR / "captures_summary.json"
        if summary_path.exists():
            with open(summary_path) as f:
                return json.load(f)
        return {"captures": []}

    def run(self):
        captures = self.captures_summary.get("captures", [])
        if not captures:
            logger.error("No hay capturas para analizar. Ejecuta primero 01_ades_explorer_v4_complete.py")
            return

        logger.info(f"Analizando {len(captures)} capturas con {NIM_MODEL}")

        batch_size = self.config["system_config"]["batch_size_qa"]
        batches = [captures[i:i + batch_size] for i in range(0, len(captures), batch_size)]

        for batch_idx, batch in enumerate(batches, 1):
            logger.info(f"\nBatch {batch_idx}/{len(batches)}")
            self._analyze_batch(batch, batch_idx)

        self._save_results()

    def _analyze_batch(self, batch: List[Dict], batch_idx: int):
        modules_info = []
        for capture in batch:
            module_id = capture["module_id"]
            module_config = next(
                (m for m in self.config["modules"] if m["id"] == module_id), None
            )
            if not module_config:
                continue
            modules_info.append({"module_id": module_id, "config": module_config, "capture": capture})

        if not modules_info:
            return

        prompt = self._build_prompt(modules_info)

        try:
            logger.info(f"  Llamando NIM API ({len(modules_info)} módulos)...")
            response = self.client.chat.completions.create(
                model=NIM_MODEL,
                messages=[{"role": "user", "content": prompt}],
                max_tokens=4096,
                temperature=0.2,
            )
            response_text = response.choices[0].message.content

            analysis = self._parse_json(response_text)
            new_inconsistencies = analysis.get("inconsistencies", [])
            self.inconsistencies.extend(new_inconsistencies)
            logger.info(f"  ✓ Batch {batch_idx}: {len(new_inconsistencies)} inconsistencias detectadas")

        except Exception as e:
            logger.error(f"  Error en batch {batch_idx}: {e}")

    def _build_prompt(self, modules_info: List[Dict]) -> str:
        modules_text = ""
        for info in modules_info:
            c = info["capture"]
            cfg = info["config"]
            modal_summary = (
                f"Modales detectados: {c.get('modals_detected', 0)} "
                f"(laterales: {c.get('side_panels_detected', 0)}, "
                f"centrales: {c.get('central_modals_detected', 0)}, "
                f"con pestañas: {c.get('modals_with_tabs', 0)})"
            )
            tab_names = []
            for td in c.get("tab_info", []):
                tab_names.extend(td.get("tabs_tested", []))

            modules_text += f"""
--- MÓDULO: {info['module_id']} ---
Ruta: {cfg['path']} | Riesgo: {cfg['risk']} | Categoría: {cfg['category']}
Descripción: {cfg.get('description', '')}

Elementos Esperados:
{chr(10).join('• ' + e for e in cfg.get('expected_elements', []))}

Heurísticas MUST HAVE:
{chr(10).join('✓ ' + h for h in cfg.get('heuristics', {}).get('must_have', []))}

Heurísticas MUST NOT HAVE:
{chr(10).join('✗ ' + h for h in cfg.get('heuristics', {}).get('must_not_have', []))}

Problemas conocidos frecuentes:
{chr(10).join('- ' + p for p in cfg.get('common_issues', []))}

Observaciones del explorador:
- {modal_summary}
- Pestañas vistas: {', '.join(tab_names) if tab_names else 'ninguna'}
- Texto visible (primeros 600 chars): {c.get('visible_text_sample', '')[:600]}
"""

        return f"""Eres un QA Engineer Senior con 5+ años en sistemas escolares mexicanos (SEP, UAEMEX).
Tu tarea: analizar la información capturada de módulos del sistema ADES Nevadi y detectar inconsistencias reales de negocio.

CONTEXTO DEL SISTEMA:
- ADES es un sistema de administración escolar (3 planteles, primaria SEP + secundaria SEP + preparatoria UAEMEX)
- Frontend Angular 22 + PrimeNG. Backend Spring Boot (BFF) + FastAPI (IA/PDFs)
- El usuario admin tiene rol 1 (máximo acceso)

REGLAS DE NEGOCIO CRÍTICAS:
1. El contexto del top bar (Plantel/Ciclo/Grado) DEBE propagarse a diálogos y formularios
2. Si la BD tiene datos pero la UI muestra "No hay X" → inconsistencia crítica
3. Módulos académicos DEBEN distinguir visualmente SEP vs Nevadi vs UAEMEX
4. Formularios con campos requeridos DEBEN marcarlos explícitamente
5. Errores de API no deben ocultarse como "sin resultados"
6. Calificaciones: SEP escala 6-10, UAEMEX escala 0-10 (mín 6), Nevadi configurable
7. Módulo disponibilidad: franjas horarias DEBEN pre-cargarse de BD
8. p-drawer (panel lateral) con pestañas es el patrón estándar de edición
9. Todos los módulos usan audit trail y optimistic locking (row_version)
10. LOV (listas de valores) dentro de drawers deben funcionar (overlayAppendTo: body)

MÓDULOS A ANALIZAR:
{modules_text}

---

Analiza cada módulo y reporta SOLO inconsistencias reales y concretas.
Para módulos sin inconsistencias detectables, omítelos del listado.

Responde ÚNICAMENTE con JSON válido (sin markdown, sin texto antes ni después):

{{
  "inconsistencies": [
    {{
      "severity": "Crítico|Alto|Medio|Bajo",
      "module_id": "id_del_módulo",
      "type": "Data Not Rendered|Missing Field|Context Not Propagated|Incomplete Flow|Error Hidden|Validation Missing|SEP/Nevadi Ambiguity|Modal/Drawer Issue|API Error",
      "description": "Descripción específica de qué falla",
      "location": "Ruta + componente específico (ej: /alumnos → tabla → botón editar)",
      "evidence": "Qué señal del texto visible o modal data indica el problema",
      "impact": "Qué NO puede hacer el usuario por este problema",
      "suggestion": "Corrección concreta en 1-2 líneas"
    }}
  ]
}}"""

    def _parse_json(self, text: str) -> Dict[str, Any]:
        start = text.find("{")
        end = text.rfind("}") + 1
        if start == -1 or end == 0:
            logger.warning("No se encontró JSON en la respuesta")
            return {"inconsistencies": []}
        try:
            return json.loads(text[start:end])
        except json.JSONDecodeError as e:
            logger.warning(f"Error parseando JSON: {e}")
            return {"inconsistencies": []}

    def _save_results(self):
        severity_order = {"Crítico": 0, "Alto": 1, "Medio": 2, "Bajo": 3}
        self.inconsistencies.sort(
            key=lambda x: severity_order.get(x.get("severity", "Bajo"), 4)
        )

        by_severity = {"critico": 0, "alto": 0, "medio": 0, "bajo": 0}
        for i in self.inconsistencies:
            s = i.get("severity", "").lower()
            if s in by_severity:
                by_severity[s] += 1
            elif "tico" in s:
                by_severity["critico"] += 1

        by_type: Dict[str, int] = {}
        for i in self.inconsistencies:
            t = i.get("type", "Unknown")
            by_type[t] = by_type.get(t, 0) + 1

        report = {
            "model_used": NIM_MODEL,
            "total_inconsistencies": len(self.inconsistencies),
            "by_severity": by_severity,
            "by_type": by_type,
            "inconsistencies": self.inconsistencies,
        }

        report_path = OUTPUT_DIR / "inconsistencies_report.json"
        with open(report_path, "w", encoding="utf-8") as f:
            json.dump(report, f, indent=2, ensure_ascii=False)

        csv_path = OUTPUT_DIR / "inconsistencies_report.csv"
        with open(csv_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(
                f,
                fieldnames=["severity", "module_id", "type", "description", "location", "impact", "suggestion"],
            )
            writer.writeheader()
            for i in self.inconsistencies:
                writer.writerow({k: i.get(k, "") for k in writer.fieldnames})

        logger.info(f"\n✓ Resultados guardados en {OUTPUT_DIR}")
        logger.info(f"  - Total inconsistencias: {report['total_inconsistencies']}")
        logger.info(f"  - Críticas: {by_severity['critico']}")
        logger.info(f"  - Altas: {by_severity['alto']}")
        logger.info(f"  - Medias: {by_severity['medio']}")
        logger.info(f"  - Bajas: {by_severity['bajo']}")
        logger.info(f"  - JSON: {report_path}")
        logger.info(f"  - CSV:  {csv_path}")


def main():
    if not NIM_API_KEY:
        raise ValueError(
            "OPENAI_API_KEY no configurada. "
            "Debe estar en /opt/ades/.env como OPENAI_API_KEY=nvapi-..."
        )
    analyzer = AdesQAAnalyzer()
    analyzer.run()


if __name__ == "__main__":
    main()
