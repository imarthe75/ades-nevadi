#!/usr/bin/env python3
"""
02_CLAUDE_QA_ANALYZER.PY - PORTABLE VERSION
Análisis cognitivo con Claude (funciona desde cualquier directorio)
"""

import json
import os
from pathlib import Path
from typing import Dict, List, Any
import logging

try:
    from anthropic import Anthropic
except ImportError:
    print("ERROR: pip install anthropic")
    exit(1)

from dotenv import load_dotenv

logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')
logger = logging.getLogger(__name__)

# PORTABLE: detectar directorio actual
SCRIPT_DIR = Path(__file__).parent.absolute()
CAPTURES_DIR = SCRIPT_DIR / "captures"
OUTPUT_DIR = SCRIPT_DIR / "analysis"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

# Cargar .env desde directorio actual
env_file = SCRIPT_DIR / ".env"
if env_file.exists():
    load_dotenv(env_file)
    logger.info(f"✓ Archivo .env cargado desde {env_file}")
else:
    logger.warning(f"⚠️ Archivo .env no encontrado en {SCRIPT_DIR}")

logger.info(f"Working directory: {SCRIPT_DIR}")
logger.info(f"Captures dir: {CAPTURES_DIR}")
logger.info(f"Output dir: {OUTPUT_DIR}")

client = Anthropic()


class AdesQAAnalyzer:
    def __init__(self):
        self.config = self._load_config()
        self.captures_summary = self._load_captures_summary()
        self.inconsistencies: List[Dict[str, Any]] = []

    def _load_config(self) -> Dict:
        config_path = SCRIPT_DIR / "config_ades_modules.json"
        if not config_path.exists():
            raise FileNotFoundError(f"config_ades_modules.json no encontrado")
        with open(config_path) as f:
            return json.load(f)

    def _load_captures_summary(self) -> Dict:
        summary_path = CAPTURES_DIR / "captures_summary.json"
        if not summary_path.exists():
            logger.error(f"No hay capturas. Ejecuta 01_ades_explorer.py primero")
            return {"captures": []}
        with open(summary_path) as f:
            return json.load(f)

    def run(self):
        logger.info("Iniciando análisis cognitivo")
        
        captures = self.captures_summary.get("captures", [])
        if not captures:
            logger.error("No hay capturas para analizar")
            return
        
        logger.info(f"Analizando {len(captures)} capturas")
        
        batch_size = self.config["system_config"]["batch_size_qa"]
        batches = [captures[i:i+batch_size] for i in range(0, len(captures), batch_size)]
        
        for batch_idx, batch in enumerate(batches, 1):
            logger.info(f"\nBatch {batch_idx}/{len(batches)}")
            self._analyze_batch(batch, batch_idx)
        
        self._save_analysis_results()

    def _analyze_batch(self, batch: List[Dict], batch_idx: int):
        modules_info = []
        for capture in batch:
            module_id = capture["module_id"]
            module_config = next(
                (m for m in self.config["modules"] if m["id"] == module_id),
                None
            )
            if module_config:
                modules_info.append({
                    "module_id": module_id,
                    "module_config": module_config,
                    "capture_data": capture
                })
        
        if not modules_info:
            return
        
        prompt = self._build_prompt(modules_info)
        
        logger.info(f"  Llamando Claude API para {len(modules_info)} módulos...")
        
        try:
            message = client.messages.create(
                model="claude-sonnet-4-6",
                max_tokens=4000,
                messages=[{"role": "user", "content": prompt}]
            )
            
            response_text = message.content[0].text
            analysis = self._parse_response(response_text)
            
            for inconsistency in analysis.get("batch_analysis", {}).get("inconsistencies", []):
                self.inconsistencies.append(inconsistency)
            
            logger.info(f"  ✓ Batch: {len(analysis.get('batch_analysis', {}).get('inconsistencies', []))} inconsistencias")
        
        except Exception as e:
            logger.error(f"  ✗ Error: {e}")

    def _build_prompt(self, modules_info: List[Dict]) -> str:
        modules_text = "\n".join([
            f"""
MÓDULO: {info['module_id']}
Descripción: {info['module_config'].get('description', 'N/A')}
Riesgo: {info['module_config'].get('risk', 'desconocido')}
Elementos Esperados: {', '.join(info['module_config'].get('expected_elements', []))}
Texto Visible: {info['capture_data'].get('visible_text_sample', 'N/A')[:300]}
Must Have: {json.dumps(info['module_config'].get('heuristics', {}).get('must_have', []), ensure_ascii=False)}
""" for info in modules_info
        ])
        
        return f"""
ADES Testing Exploratorio - QA Cognitivo

Eres QA Senior con 5+ años. Analiza estos módulos ADES.

MÓDULOS:
{modules_text}

DETECTA:
1. Datos no renderizados (API falla pero UI dice "sin datos")
2. Contexto no propagado (filtros top bar no pre-llenan diálogos)
3. Campos faltantes en formularios
4. Errores API enmascarados
5. Distinción SEP/Nevadi/UAEMEX ambigua
6. Flujos incompletos

RESPONDE SOLO EN JSON:
{{
    "batch_analysis": {{
        "inconsistencies": [
            {{
                "severity": "Crítico|Alto|Medio|Bajo",
                "module_id": "id_módulo",
                "type": "Data Not Rendered|Missing Field|Context Not Propagated|etc",
                "description": "Qué no funciona",
                "location": "Ruta",
                "impact": "Qué usuario no puede hacer",
                "suggestion": "Cómo corregir"
            }}
        ]
    }}
}}
"""

    def _parse_response(self, response_text: str) -> Dict:
        start_idx = response_text.find('{')
        end_idx = response_text.rfind('}') + 1
        
        if start_idx == -1 or end_idx == 0:
            raise ValueError("No JSON en respuesta")
        
        json_str = response_text[start_idx:end_idx]
        return json.loads(json_str)

    def _save_analysis_results(self):
        severity_order = {"Crítico": 0, "Alto": 1, "Medio": 2, "Bajo": 3}
        self.inconsistencies.sort(
            key=lambda x: severity_order.get(x.get("severity", "Bajo"), 4)
        )
        
        report = {
            "timestamp": datetime.now().isoformat(),
            "total_inconsistencies": len(self.inconsistencies),
            "by_severity": {
                "critico": len([i for i in self.inconsistencies if i.get("severity") == "Crítico"]),
                "alto": len([i for i in self.inconsistencies if i.get("severity") == "Alto"]),
                "medio": len([i for i in self.inconsistencies if i.get("severity") == "Medio"]),
                "bajo": len([i for i in self.inconsistencies if i.get("severity") == "Bajo"])
            },
            "inconsistencies": self.inconsistencies
        }
        
        report_path = OUTPUT_DIR / "inconsistencies_report.json"
        with open(report_path, "w", encoding="utf-8") as f:
            json.dump(report, f, indent=2, ensure_ascii=False)
        
        logger.info(f"\n✓ Análisis guardado en {report_path}")
        logger.info(f"  - Total: {report['total_inconsistencies']}")
        logger.info(f"  - Críticas: {report['by_severity']['critico']}")
        logger.info(f"  - Altas: {report['by_severity']['alto']}")


def main():
    from datetime import datetime
    api_key = os.getenv("ANTHROPIC_API_KEY", "")
    if not api_key:
        raise ValueError("ANTHROPIC_API_KEY no configurada")
    
    analyzer = AdesQAAnalyzer()
    analyzer.run()


if __name__ == "__main__":
    main()
