#!/usr/bin/env python3
"""
01_ADES_EXPLORER.PY - PORTABLE VERSION
Captura automatizada de módulos ADES (funciona desde cualquier directorio)
"""

import os
import json
import asyncio
import base64
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Any

from playwright.async_api import async_playwright, Page, BrowserContext
from dotenv import load_dotenv
import logging

logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')
logger = logging.getLogger(__name__)

# Detectar directorio actual (PORTABLE)
SCRIPT_DIR = Path(__file__).parent.absolute()
OUTPUT_DIR = SCRIPT_DIR / "captures"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

# Cargar .env desde directorio actual
env_file = SCRIPT_DIR / ".env"
if env_file.exists():
    load_dotenv(env_file)
    logger.info(f"✓ Archivo .env cargado desde {env_file}")
else:
    logger.warning(f"⚠️ Archivo .env no encontrado en {SCRIPT_DIR}")

# Variables de entorno
ADES_USER = os.getenv("ADES_USER", "admin")
ADES_PASSWORD = os.getenv("ADES_PASSWORD", "")
BASE_URL = "https://ades.setag.mx"

logger.info(f"Working directory: {SCRIPT_DIR}")
logger.info(f"Output directory: {OUTPUT_DIR}")


class AdesExplorer:
    def __init__(self):
        self.config = self._load_config()
        self.captures: List[Dict[str, Any]] = []
        self.errors: List[Dict[str, Any]] = []
        self.console_messages: List[Dict[str, Any]] = []
        self.network_requests: List[Dict[str, Any]] = []

    def _load_config(self) -> Dict:
        """Cargar config de módulos."""
        config_path = SCRIPT_DIR / "config_ades_modules.json"
        if not config_path.exists():
            logger.error(f"Config no encontrado en {config_path}")
            raise FileNotFoundError(f"config_ades_modules.json no encontrado en {SCRIPT_DIR}")
        
        with open(config_path) as f:
            return json.load(f)

    async def run(self, phase: int = 1, limit: int = None):
        """
        Ejecutar captura de módulos.
        phase: 1=críticos, 2=medios, 3=bajos
        limit: máximo de módulos a testear (para debug)
        """
        async with async_playwright() as p:
            logger.info(f"Iniciando Playwright en {BASE_URL}")
            browser = await p.chromium.launch(headless=True)
            context = await browser.new_context(viewport={"width": 1920, "height": 1080})
            
            await self._setup_context_listeners(context)
            page = await context.new_page()
            
            try:
                logger.info("Autenticando en ADES...")
                await self._authenticate(page)
                
                modules_to_test = [
                    m for m in self.config["modules"]
                    if self._get_phase(m["risk"]) <= phase
                ]
                modules_to_test.sort(key=lambda m: m["sequence"])
                
                if limit:
                    modules_to_test = modules_to_test[:limit]
                
                logger.info(f"Testeando {len(modules_to_test)} módulos (Fase {phase})")
                
                for i, module in enumerate(modules_to_test, 1):
                    logger.info(f"[{i}/{len(modules_to_test)}] {module['id']}")
                    await self._capture_module(page, module)
                    await asyncio.sleep(0.5)
                
                await self._save_results()
                
            except Exception as e:
                logger.error(f"Error: {e}")
                raise
            finally:
                await context.close()
                await browser.close()

    async def _authenticate(self, page: Page):
        """Autenticar en ADES."""
        try:
            await page.goto(f"{BASE_URL}/login", wait_until="networkidle", timeout=30000)
            await page.fill('input[name="username"]', ADES_USER)
            await page.fill('input[name="password"]', ADES_PASSWORD)
            await page.click('button[type="submit"]')
            await page.wait_for_url(lambda url: "/dashboard" in str(url), timeout=30000)
            await asyncio.sleep(2)
            logger.info("✓ Autenticación exitosa")
        except Exception as e:
            logger.error(f"Error de autenticación: {e}")
            raise

    async def _setup_context_listeners(self, context: BrowserContext):
        """Listeners para errores."""
        async def handle_console(msg):
            self.console_messages.append({"type": msg.type, "text": msg.text})
            if msg.type in ["error", "warning"]:
                logger.warning(f"Console {msg.type}: {msg.text[:100]}")

        async def handle_response(response):
            if response.status >= 400:
                self.network_requests.append({
                    "url": response.url,
                    "status": response.status,
                    "timestamp": datetime.now().isoformat()
                })

        context.on("console", handle_console)
        context.on("response", handle_response)

    async def _capture_module(self, page: Page, module: Dict[str, Any]):
        """Capturar módulo."""
        try:
            url = f"{BASE_URL}{module['path']}"
            await page.goto(url, wait_until="networkidle", timeout=30000)
            await asyncio.sleep(1)
            
            screenshot = await page.screenshot()
            dom_html = await page.content()
            visible_text = await page.evaluate("() => document.body.innerText")
            
            capture = {
                "module_id": module["id"],
                "module_name": module["path"],
                "timestamp": datetime.now().isoformat(),
                "screenshot_base64": base64.b64encode(screenshot).decode(),
                "dom_html": dom_html[:5000],
                "visible_text_sample": visible_text[:1000],
                "console_errors": [m for m in self.console_messages if m["type"] == "error"][-3:],
                "network_errors": [r for r in self.network_requests if r.get("status", 0) >= 400][-3:],
                "success": True
            }
            
            self.captures.append(capture)
            
            screenshot_path = OUTPUT_DIR / f"{module['id']}.png"
            with open(screenshot_path, "wb") as f:
                f.write(screenshot)
            
            logger.info(f"  ✓ Capturado: {screenshot_path}")
            
        except Exception as e:
            logger.error(f"  ✗ Error capturando {module['id']}: {e}")
            self.errors.append({"module_id": module["id"], "error": str(e)})

    async def _save_results(self):
        """Guardar resultados."""
        summary = {
            "timestamp": datetime.now().isoformat(),
            "total_modules": len(self.captures),
            "successful": len([c for c in self.captures if c["success"]]),
            "failed": len(self.errors),
        }
        
        captures_for_json = [
            {
                "module_id": c["module_id"],
                "timestamp": c["timestamp"],
                "visible_text_sample": c["visible_text_sample"],
                "console_errors": c["console_errors"],
                "network_errors": c["network_errors"],
                "success": c["success"]
            }
            for c in self.captures
        ]
        
        results = {"summary": summary, "captures": captures_for_json, "errors": self.errors}
        
        results_path = OUTPUT_DIR / "captures_summary.json"
        with open(results_path, "w") as f:
            json.dump(results, f, indent=2, ensure_ascii=False)
        
        logger.info(f"\n✓ Resultados guardados en {results_path}")
        logger.info(f"  - Módulos capturados: {summary['successful']}/{summary['total_modules']}")
        logger.info(f"  - Errores: {summary['failed']}")

    def _get_phase(self, risk: str) -> int:
        """Fase según riesgo."""
        return {"critico": 1, "alto": 1, "medio": 2, "bajo": 3}.get(risk, 3)


async def main():
    if not ADES_PASSWORD:
        raise ValueError("Variable ADES_PASSWORD no configurada")
    
    explorer = AdesExplorer()
    await explorer.run(phase=1)


if __name__ == "__main__":
    asyncio.run(main())
