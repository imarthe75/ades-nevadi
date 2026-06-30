#!/usr/bin/env python3
"""
01_ADES_EXPLORER.PY - Captura Automatizada Sistémica
Navega todos los módulos ADES, captura:
  - Screenshot (PNG base64)
  - DOM HTML completo
  - Console errors (JS, XHR)
  - Network requests (status codes)
  - Rendered text ("No hay X configurados")
  - Context state (filtros top bar)

Ejecución:
  pip install playwright python-dotenv
  playwright install
  export ADES_USER=admin ADES_PASSWORD=...
  python 01_ades_explorer.py
"""

import os
import json
import asyncio
import base64
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Any

from playwright.async_api import async_playwright, Page, BrowserContext
import logging

# Configuración de logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s'
)
logger = logging.getLogger(__name__)

# Variables de entorno
ADES_USER = os.getenv("ADES_USER", "admin")
ADES_PASSWORD = os.getenv("ADES_PASSWORD", "")
BASE_URL = "https://ades.setag.mx"

# Directorios de salida
OUTPUT_DIR = Path("/home/claude/ades_testing/captures")
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)


class AdesExplorer:
    def __init__(self):
        self.config = self._load_config()
        self.captures: List[Dict[str, Any]] = []
        self.errors: List[Dict[str, Any]] = []
        self.console_messages: List[Dict[str, Any]] = []
        self.network_requests: List[Dict[str, Any]] = []

    def _load_config(self) -> Dict:
        """Cargar config de módulos."""
        config_path = Path("/home/claude/ades_testing/config_ades_modules.json")
        with open(config_path) as f:
            return json.load(f)

    async def run(self, phase: int = 1):
        """
        Ejecutar captura de módulos por fase.
        Fase 1: Módulos críticos (25 primeros)
        Fase 2: Módulos alto/medio riesgo (resto)
        """
        async with async_playwright() as p:
            logger.info(f"Iniciando Playwright en {BASE_URL}")
            browser = await p.chromium.launch(headless=True)
            context = await browser.new_context(
                viewport={"width": 1920, "height": 1080},
                timezone_id="America/Mexico_City"
            )
            
            # Preparar context: listeners para errores de consola/red
            await self._setup_context_listeners(context)
            
            page = await context.new_page()
            
            try:
                # Autenticar
                logger.info("Autenticando en ADES...")
                await self._authenticate(page)
                
                # Navegar módulos por fase
                modules_to_test = [
                    m for m in self.config["modules"]
                    if self._get_phase(m["risk"]) <= phase
                ]
                modules_to_test.sort(key=lambda m: m["sequence"])
                
                logger.info(f"Testeando {len(modules_to_test)} módulos (Fase {phase})")
                
                for i, module in enumerate(modules_to_test, 1):
                    logger.info(f"[{i}/{len(modules_to_test)}] {module['id']}")
                    await self._capture_module(page, module)
                    await asyncio.sleep(1)  # Esperar entre módulos
                
                # Guardar resultados
                await self._save_results()
                
            except Exception as e:
                logger.error(f"Error durante captura: {e}")
                raise
            finally:
                await context.close()
                await browser.close()

    async def _authenticate(self, page: Page):
        """Autenticar en ADES."""
        await page.goto(f"{BASE_URL}/login", wait_until="networkidle")
        
        # Llenar credenciales
        await page.fill('input[name="username"]', ADES_USER)
        await page.fill('input[name="password"]', ADES_PASSWORD)
        await page.click('button[type="submit"]')
        
        # Esperar a que cargue dashboard
        await page.wait_for_url(lambda url: "/dashboard" in str(url), timeout=30000)
        await asyncio.sleep(2)
        
        logger.info("Autenticación exitosa")

    async def _setup_context_listeners(self, context: BrowserContext):
        """Configurar listeners globales para errores."""
        async def handle_console(msg):
            self.console_messages.append({
                "type": msg.type,
                "text": msg.text,
                "timestamp": datetime.now().isoformat()
            })
            if msg.type in ["error", "warning"]:
                logger.warning(f"Console {msg.type}: {msg.text}")

        async def handle_request(request):
            self.network_requests.append({
                "method": request.method,
                "url": request.url,
                "timestamp": datetime.now().isoformat()
            })

        async def handle_response(response):
            if response.status >= 400:
                self.network_requests.append({
                    "method": response.request.method,
                    "url": response.url,
                    "status": response.status,
                    "timestamp": datetime.now().isoformat(),
                    "error": True
                })

        context.on("console", handle_console)
        context.on("request", handle_request)
        context.on("response", handle_response)

    async def _capture_module(self, page: Page, module: Dict[str, Any]):
        """Capturar un módulo específico."""
        try:
            url = f"{BASE_URL}{module['path']}"
            logger.info(f"  Navegando a {url}")
            
            await page.goto(url, wait_until="networkidle", timeout=30000)
            await asyncio.sleep(self.config["system_config"]["capture_delay_ms"] / 1000)
            
            # Captura básicas
            screenshot = await page.screenshot()
            dom_html = await page.content()
            
            # Extraer texto visible (para detectar "No hay X configurados")
            visible_text = await page.evaluate("() => document.body.innerText")
            
            # Extraer estado de filtros (si existen)
            filter_state = await self._extract_filter_state(page)
            
            # Detectar elementos específicos del módulo
            elements_found = await self._detect_elements(page, module)
            
            # Crear objeto de captura
            capture = {
                "module_id": module["id"],
                "module_name": module["path"],
                "timestamp": datetime.now().isoformat(),
                "screenshot_base64": base64.b64encode(screenshot).decode(),
                "dom_html": dom_html[:10000],  # Primeros 10k chars
                "visible_text_sample": visible_text[:2000],
                "filter_state": filter_state,
                "elements_found": elements_found,
                "console_errors": [
                    m for m in self.console_messages 
                    if m["type"] in ["error"]
                ][-5:],  # Últimos 5 errores
                "network_errors": [
                    r for r in self.network_requests
                    if r.get("error")
                ][-5:],  # Últimas 5 respuestas error
                "success": True
            }
            
            self.captures.append(capture)
            
            # Guardar screenshot individual
            screenshot_path = OUTPUT_DIR / f"{module['id']}.png"
            with open(screenshot_path, "wb") as f:
                f.write(screenshot)
            logger.info(f"  ✓ Capturado en {screenshot_path}")
            
        except Exception as e:
            logger.error(f"  ✗ Error capturando {module['id']}: {e}")
            self.errors.append({
                "module_id": module["id"],
                "error": str(e),
                "timestamp": datetime.now().isoformat()
            })

    async def _extract_filter_state(self, page: Page) -> Dict[str, Any]:
        """Extraer estado de filtros del top bar."""
        try:
            filters = await page.evaluate("""
                () => {
                    const state = {};
                    // Buscar selects comunes
                    const selects = document.querySelectorAll('select, [role="combobox"]');
                    selects.forEach((sel, idx) => {
                        const value = sel.value || sel.innerText;
                        state[`filter_${idx}`] = value;
                    });
                    return state;
                }
            """)
            return filters
        except:
            return {}

    async def _detect_elements(self, page: Page, module: Dict[str, Any]) -> Dict[str, bool]:
        """Detectar elementos esperados del módulo."""
        detected = {}
        
        for element in module.get("expected_elements", []):
            # Buscar por texto visible o data attributes
            try:
                found = await page.evaluate(f"""
                    () => {{
                        const text = document.body.innerText;
                        return text.includes('{element}');
                    }}
                """)
                detected[element] = found
            except:
                detected[element] = False
        
        return detected

    async def _save_results(self):
        """Guardar resultados en JSON."""
        # Resumen de capturas
        summary = {
            "timestamp": datetime.now().isoformat(),
            "total_modules": len(self.captures),
            "successful": len([c for c in self.captures if c["success"]]),
            "failed": len(self.errors),
            "total_console_errors": len(self.console_messages),
            "total_network_errors": len([r for r in self.network_requests if r.get("error")])
        }
        
        # Guardar JSON de capturas (sin imágenes base64 por tamaño)
        captures_for_json = [
            {
                "module_id": c["module_id"],
                "timestamp": c["timestamp"],
                "visible_text_sample": c["visible_text_sample"],
                "filter_state": c["filter_state"],
                "elements_found": c["elements_found"],
                "console_errors": c["console_errors"],
                "network_errors": c["network_errors"],
                "success": c["success"]
            }
            for c in self.captures
        ]
        
        results = {
            "summary": summary,
            "captures": captures_for_json,
            "errors": self.errors
        }
        
        results_path = OUTPUT_DIR / "captures_summary.json"
        with open(results_path, "w") as f:
            json.dump(results, f, indent=2, ensure_ascii=False)
        
        logger.info(f"\n✓ Resultados guardados en {results_path}")
        logger.info(f"  - Módulos capturados: {summary['successful']}/{summary['total_modules']}")
        logger.info(f"  - Errores de consola: {summary['total_console_errors']}")
        logger.info(f"  - Errores de red: {summary['total_network_errors']}")
        
        # Guardar también en CSV para rápida revisión
        await self._save_csv_summary()

    async def _save_csv_summary(self):
        """Guardar resumen en CSV."""
        import csv
        
        csv_path = OUTPUT_DIR / "captures_summary.csv"
        with open(csv_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(
                f,
                fieldnames=["module_id", "status", "elements_found", "console_errors", "network_errors"]
            )
            writer.writeheader()
            
            for capture in self.captures:
                elements_count = len([v for v in capture["elements_found"].values() if v])
                writer.writerow({
                    "module_id": capture["module_id"],
                    "status": "OK" if capture["success"] else "ERROR",
                    "elements_found": f"{elements_count}/{len(capture['elements_found'])}",
                    "console_errors": len(capture["console_errors"]),
                    "network_errors": len(capture["network_errors"])
                })
        
        logger.info(f"  - CSV guardado en {csv_path}")

    def _get_phase(self, risk: str) -> int:
        """Determinar fase según riesgo."""
        risk_to_phase = {
            "critico": 1,
            "alto": 1,
            "medio": 2,
            "bajo": 3
        }
        return risk_to_phase.get(risk, 3)


async def main():
    """Punto de entrada."""
    if not ADES_PASSWORD:
        raise ValueError("Variable de entorno ADES_PASSWORD no configurada")
    
    explorer = AdesExplorer()
    
    # Fase 1: Módulos críticos + alto riesgo
    await explorer.run(phase=1)


if __name__ == "__main__":
    asyncio.run(main())
