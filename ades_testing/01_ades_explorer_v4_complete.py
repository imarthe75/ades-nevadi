#!/usr/bin/env python3
"""
01_ADES_EXPLORER_V4.PY - TESTING COMPLETO DE MODALES
Soporta:
  - Autenticación Authentik (3 pasos)
  - Detección y apertura de modales laterales (side panels)
  - Detección y apertura de modales centrales
  - Iteración sobre pestañas dentro de modales
  - Validación de campos en cada pestaña
  - Captura de estado de cada pestaña
  - Detección de inconsistencias dentro de modales
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

SCRIPT_DIR = Path(__file__).parent.absolute()
OUTPUT_DIR = SCRIPT_DIR / "captures"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

env_file = SCRIPT_DIR / ".env"
if env_file.exists():
    load_dotenv(env_file)
    logger.info(f"✓ Archivo .env cargado")

ADES_USER = os.getenv("ADES_USER", "admin")
ADES_PASSWORD = os.getenv("ADES_PASSWORD", "")
BASE_URL = "https://ades.setag.mx"

logger.info(f"Working directory: {SCRIPT_DIR}")


class AdesExplorerV4:
    def __init__(self):
        self.config = self._load_config()
        self.captures: List[Dict[str, Any]] = []
        self.errors: List[Dict[str, Any]] = []
        # Acumuladores por módulo, reseteados antes de cada navegación
        self._console_errors: List[Dict] = []
        self._network_errors: List[Dict] = []
        # Snapshot del sessionStorage después del auth exitoso
        self._session_snapshot: Dict[str, str] = {}

    def _load_config(self) -> Dict:
        config_path = SCRIPT_DIR / "config_ades_modules.json"
        with open(config_path) as f:
            return json.load(f)

    async def run(self, phase: int = 1, limit: int = None):
        async with async_playwright() as p:
            logger.info(f"Iniciando Playwright en {BASE_URL}")
            browser = await p.chromium.launch(headless=True)
            context = await browser.new_context(viewport={"width": 1920, "height": 1080})
            page = await context.new_page()
            
            try:
                # Registrar listeners de consola y red una sola vez para toda la sesión
                def _on_console(msg):
                    if msg.type in ("error", "warning"):
                        self._console_errors.append({"type": msg.type, "text": msg.text})

                def _on_response(response):
                    if response.status >= 400:
                        self._network_errors.append({
                            "method": response.request.method,
                            "url": response.url,
                            "status": response.status,
                        })

                page.on("console", _on_console)
                page.on("response", _on_response)

                logger.info("Autenticando en ADES (Authentik - 3 pasos)...")
                await self._authenticate_authentik(page)

                modules_to_test = [
                    m for m in self.config["modules"]
                    if self._get_phase(m["risk"]) <= phase
                ]
                modules_to_test.sort(key=lambda m: m["sequence"])
                
                if limit:
                    modules_to_test = modules_to_test[:limit]
                
                logger.info(f"Testeando {len(modules_to_test)} módulos (Fase {phase})")
                
                for i, module in enumerate(modules_to_test, 1):
                    logger.info(f"\n[{i}/{len(modules_to_test)}] {module['id']}")
                    await self._capture_module_with_modals(page, module)
                    await asyncio.sleep(0.5)
                
                await self._save_results()
                
            except Exception as e:
                logger.error(f"Error: {e}")
                raise
            finally:
                await context.close()
                await browser.close()

    async def _authenticate_authentik(self, page: Page):
        """Autenticación Authentik - 3 pasos."""
        try:
            logger.info("Paso 1: Navegando a login y buscando botón de institucion...")
            await page.goto(f"{BASE_URL}/login", wait_until="domcontentloaded", timeout=30000)
            await asyncio.sleep(2)
            
            button_selectors = [
                'button:has-text("Iniciar sesión con cuenta institucional")',
                'button:has-text("Iniciar sesión")',
                'a:has-text("Iniciar sesión con cuenta institucional")'
            ]
            
            for selector in button_selectors:
                try:
                    if await page.query_selector(selector):
                        logger.info(f"  ✓ Botón encontrado")
                        await page.click(selector)
                        await asyncio.sleep(2)
                        break
                except:
                    continue
            
            logger.info("Paso 2: Ingresando username...")
            username_locator = page.locator('input[type="text"], input[type="email"]').first
            await username_locator.wait_for(state="visible", timeout=30000)
            await username_locator.click()
            await username_locator.fill(ADES_USER)
            await username_locator.press('Enter')
            await asyncio.sleep(3)

            logger.info("Paso 3: Ingresando contraseña...")
            password_locator = page.locator('input[type="password"]').first
            await password_locator.wait_for(state="visible", timeout=30000)
            await password_locator.click()
            await password_locator.fill(ADES_PASSWORD)
            await password_locator.press('Enter')
            await asyncio.sleep(3)
            logger.info("Esperando dashboard y token OIDC...")
            try:
                await page.wait_for_url(lambda url: "/dashboard" in str(url), timeout=60000)
            except Exception:
                await page.wait_for_load_state("networkidle", timeout=30000)

            # Esperar que Angular complete el OIDC exchange y guarde ades_token en sessionStorage
            try:
                await page.wait_for_function(
                    "() => !!sessionStorage.getItem('ades_token')",
                    timeout=20000,
                )
                logger.info("  ✓ Token OIDC (ades_token) confirmado en sessionStorage")
            except Exception:
                all_keys = await page.evaluate(
                    "() => ({ls: Object.keys(localStorage), ss: Object.keys(sessionStorage)})"
                )
                logger.warning(f"  ades_token no encontrado — localStorage keys: {all_keys['ls']}, sessionStorage keys: {all_keys['ss']}")
                await asyncio.sleep(5)

            # Guardar snapshot completo del sessionStorage para restaurarlo en cada navegación
            self._session_snapshot = await page.evaluate("""
                () => {
                    const snap = {};
                    for (let i = 0; i < sessionStorage.length; i++) {
                        const k = sessionStorage.key(i);
                        snap[k] = sessionStorage.getItem(k);
                    }
                    return snap;
                }
            """)
            ades_keys = [k for k in self._session_snapshot if k.startswith('ades_')]
            logger.info(f"  ✓ Sesión guardada — keys: {ades_keys}")

            # Inicializar contexto (plantel + ciclo) para que los módulos carguen sus datos
            await self._init_context(page)

            # Registrar init script: restaura sessionStorage ANTES de que Angular cargue
            # en cada navegación futura, evitando redirects a /login
            await self._register_session_init_script(page)

            await asyncio.sleep(1)
            logger.info("✓ Autenticación exitosa")
            
        except Exception as e:
            logger.error(f"✗ Error autenticación: {e}")
            raise

    async def _init_context(self, page: Page):
        """Obtiene el primer plantel y ciclo activo vía API e los inyecta en sessionStorage."""
        token = self._session_snapshot.get('ades_token', '')
        if not token:
            return
        try:
            result = await page.evaluate(f"""
                async () => {{
                    const headers = {{ 'Authorization': 'Bearer {token}' }};
                    const [pRes, cRes] = await Promise.all([
                        fetch('/api/v1/planteles', {{headers}}),
                        fetch('/api/v1/catalogs/ciclos', {{headers}}),
                    ]);
                    const planteles = pRes.ok ? await pRes.json() : [];
                    const ciclosRaw = cRes.ok ? await cRes.json() : [];
                    const plantel = Array.isArray(planteles) ? planteles[0] : (planteles.content?.[0] || planteles);
                    const ciclos = Array.isArray(ciclosRaw) ? ciclosRaw : (ciclosRaw.content || [ciclosRaw]);
                    // Preferir ciclo activo
                    const ciclo = ciclos.find(c => c.vigente || c.activo || c.is_active) || ciclos[0];
                    return {{ plantel, ciclo }};
                }}
            """)
            if result.get('plantel'):
                plantel_json = json.dumps(result['plantel'])
                await page.evaluate(f"() => sessionStorage.setItem('ades_plantel', {json.dumps(plantel_json)})")
                self._session_snapshot['ades_plantel'] = plantel_json
                plantel_nombre = result['plantel'].get('nombre_plantel', result['plantel'].get('nombre', '?'))
                logger.info(f"  ✓ Plantel: {plantel_nombre}")
            if result.get('ciclo'):
                ciclo_json = json.dumps(result['ciclo'])
                await page.evaluate(f"() => sessionStorage.setItem('ades_ciclo', {json.dumps(ciclo_json)})")
                self._session_snapshot['ades_ciclo'] = ciclo_json
                ciclo_nombre = result['ciclo'].get('nombre_ciclo', result['ciclo'].get('nombre', '?'))
                logger.info(f"  ✓ Ciclo: {ciclo_nombre}")
        except Exception as e:
            logger.warning(f"  No se pudo inicializar contexto: {e}")

    async def _register_session_init_script(self, page: Page):
        """Inyecta sessionStorage antes de cada carga de página para mantener la sesión OIDC."""
        if not self._session_snapshot:
            return
        snapshot_json = json.dumps(self._session_snapshot)
        script = f"""
            (function() {{
                var snap = {snapshot_json};
                for (var k in snap) {{
                    if (snap.hasOwnProperty(k)) {{
                        sessionStorage.setItem(k, snap[k]);
                    }}
                }}
            }})();
        """
        await page.add_init_script(script)
        logger.info("  ✓ Init script de sesión registrado")

    async def _capture_module_with_modals(self, page: Page, module: Dict[str, Any]):
        """Capturar módulo + Testing de modales laterales Y centrales."""
        try:
            url = f"{BASE_URL}{module['path']}"
            logger.info(f"  Navegando a {url}")

            # Resetear acumuladores para este módulo
            self._console_errors.clear()
            self._network_errors.clear()

            await page.goto(url, wait_until="networkidle", timeout=45000)
            await asyncio.sleep(3)

            # Detectar si la sesión no se restableció (el init script debería prevenirlo)
            current_url = page.url
            quick_text_check = await page.evaluate("() => document.body.innerText.slice(0, 200)")
            if (
                "/login" in current_url
                or "authentik" in current_url.lower()
                or "Iniciar sesión con cuenta institucional" in quick_text_check
            ):
                logger.warning(f"  ⚠️ Redirect a login detectado en {module['id']} — init script falló")

            # Captura principal
            screenshot = await page.screenshot()
            visible_text = await page.evaluate("() => document.body.innerText")

            # Detectar y testear modales
            modal_info = await self._test_all_modals(page)

            # Filtrar errores de red relevantes (excluir assets estáticos)
            api_errors = [
                e for e in self._network_errors
                if any(kw in e["url"] for kw in ["/api/", "/auth/", "/application/"])
            ]

            capture = {
                "module_id": module["id"],
                "module_path": module["path"],
                "timestamp": datetime.now().isoformat(),
                "screenshot_base64": base64.b64encode(screenshot).decode(),
                "visible_text_sample": visible_text[:4000],
                "console_errors": self._console_errors[:20],
                "network_errors": api_errors[:20],
                "modals_detected": modal_info["count"],
                "side_panels_detected": modal_info["side_panels"],
                "central_modals_detected": modal_info["central_modals"],
                "modals_with_tabs": modal_info["with_tabs"],
                "tab_info": modal_info["tab_details"],
                "modal_inconsistencies": modal_info["inconsistencies"],
                "success": True,
            }

            self.captures.append(capture)

            screenshot_path = OUTPUT_DIR / f"{module['id']}.png"
            with open(screenshot_path, "wb") as f:
                f.write(screenshot)

            summary = "  ✓ Capturado"
            if api_errors:
                summary += f" | {len(api_errors)} errores API"
            if self._console_errors:
                summary += f" | {len(self._console_errors)} errores consola"
            if modal_info["count"] > 0:
                summary += f" | Modales: {modal_info['count']}"
                if modal_info["side_panels"] > 0:
                    summary += f" (laterales: {modal_info['side_panels']}"
                    if modal_info["with_tabs"] > 0:
                        summary += f", con {modal_info['with_tabs']} pestañas"
                    summary += ")"
                if modal_info["central_modals"] > 0:
                    summary += f" (centrales: {modal_info['central_modals']})"
            if modal_info["inconsistencies"]:
                summary += f" | ⚠️ {len(modal_info['inconsistencies'])} inconsistencias"

            logger.info(summary)

        except Exception as e:
            logger.error(f"  ✗ Error: {e}")
            self.errors.append({"module_id": module["id"], "error": str(e)})

    async def _test_all_modals(self, page: Page) -> Dict[str, Any]:
        """Detectar y testear todos los modales (laterales + centrales)."""
        result = {
            "count": 0,
            "side_panels": 0,
            "central_modals": 0,
            "with_tabs": 0,
            "tab_details": [],
            "inconsistencies": []
        }
        
        try:
            # Selectores PrimeNG/Angular: botones con ícono lápiz o título Editar
            EDIT_SELECTORS = [
                'button:has(.pi-pencil)',
                'button[aria-label*="ditar"]',
                'button[title*="ditar"]',
                '[data-pc-name="button"]:has(.pi-pencil)',
                'p-button[icon*="pencil"] button',
                'button.p-button:has(.pi-file-edit)',
            ]

            async def _find_edit_buttons():
                for sel in EDIT_SELECTORS:
                    try:
                        btns = await page.query_selector_all(sel)
                        if btns:
                            logger.debug(f"  Botones encontrados con selector: {sel} ({len(btns)})")
                            return btns
                    except Exception:
                        continue
                return []

            edit_buttons = await _find_edit_buttons()
            logger.debug(f"  Botones de edición encontrados: {len(edit_buttons)}")

            # Límite de 3 modales para no tardar mucho
            max_modals_to_test = min(3, len(edit_buttons))

            for i in range(max_modals_to_test):
                try:
                    # Re-encontrar el botón (puede haber cambios en DOM)
                    edit_buttons = await _find_edit_buttons()
                    
                    if i < len(edit_buttons):
                        button = edit_buttons[i]
                        await button.scroll_into_view()
                        await asyncio.sleep(0.5)
                        await button.click()
                        await asyncio.sleep(1.5)
                        
                        # Detectar tipo de modal abierto
                        modal_type = await self._detect_modal_type(page)
                        
                        if modal_type == "side_panel":
                            result["side_panels"] += 1
                            tab_info = await self._test_tabbed_modal(page)
                            result["tab_details"].append(tab_info)
                            if tab_info["tabs_found"] > 0:
                                result["with_tabs"] += 1
                        elif modal_type == "central":
                            result["central_modals"] += 1
                        
                        result["count"] += 1
                        
                        # Cerrar modal
                        await self._close_modal(page)
                        await asyncio.sleep(0.5)
                        
                except Exception as e:
                    logger.debug(f"  Error testeando modal {i}: {e}")
                    continue
            
        except Exception as e:
            logger.debug(f"Error en _test_all_modals: {e}")
        
        return result

    async def _detect_modal_type(self, page: Page) -> str:
        """Detectar tipo de modal: side_panel o central."""
        try:
            viewport = page.viewport_size or {"width": 1920, "height": 1080}

            # PrimeNG p-drawer (panel lateral) — v21 usa .p-drawer, versiones anteriores .p-sidebar
            for sel in ['.p-drawer', '.p-sidebar', '[data-pc-name="drawer"]']:
                try:
                    el = await page.query_selector(sel)
                    if el and await el.is_visible():
                        return "side_panel"
                except Exception:
                    continue

            # Fallback: dialog con posición lateral (ocupa < 70% del ancho)
            for sel in ['[role="dialog"]', '.p-dialog', '[data-pc-name="dialog"]']:
                try:
                    el = await page.query_selector(sel)
                    if el and await el.is_visible():
                        box = await el.bounding_box()
                        if box and box["width"] < viewport["width"] * 0.7:
                            return "side_panel"
                        return "central"
                except Exception:
                    continue

            return "unknown"
        except Exception:
            return "unknown"

    async def _test_tabbed_modal(self, page: Page) -> Dict[str, Any]:
        """Testear modal con pestañas."""
        tab_info = {
            "tabs_found": 0,
            "tabs_tested": [],
            "fields_per_tab": {},
            "empty_fields": []
        }
        
        try:
            # Selectores PrimeNG v21 para pestañas (p-tabs → [data-pc-name="tab"])
            TAB_SELECTORS = [
                '[data-pc-name="tab"]',
                '[role="tab"]',
                '.p-tab',
                'p-tabpanel > .p-tabpanel-header',
            ]
            tab_buttons = []
            for sel in TAB_SELECTORS:
                try:
                    btns = await page.query_selector_all(sel)
                    if btns:
                        tab_buttons = btns
                        break
                except Exception:
                    continue
            tab_info["tabs_found"] = len(tab_buttons)
            
            for idx, tab_btn in enumerate(tab_buttons[:5]):  # Max 5 pestañas
                try:
                    tab_name = await tab_btn.text_content()
                    await tab_btn.click()
                    await asyncio.sleep(0.5)
                    
                    # Contar campos en esta pestaña
                    fields = await page.query_selector_all('input, textarea, select')
                    empty_fields = []
                    
                    for field in fields:
                        value = await field.input_value() if await field.get_attribute('type') != 'hidden' else ""
                        if not value or value.strip() == "":
                            field_name = await field.get_attribute('name') or await field.get_attribute('id') or f"field_{idx}"
                            empty_fields.append(field_name)
                    
                    tab_info["tabs_tested"].append(tab_name.strip() if tab_name else f"Tab {idx+1}")
                    tab_info["fields_per_tab"][tab_name.strip() if tab_name else f"Tab {idx+1}"] = len(fields)
                    
                    if empty_fields:
                        tab_info["empty_fields"].extend([(tab_name or f"Tab {idx+1}", f) for f in empty_fields])
                
                except Exception as e:
                    logger.debug(f"Error en pestaña {idx}: {e}")
                    continue
        
        except Exception as e:
            logger.debug(f"Error en _test_tabbed_modal: {e}")
        
        return tab_info

    async def _close_modal(self, page: Page):
        """Cerrar modal abierto."""
        try:
            # Buscar botón cerrar
            close_buttons = [
                # PrimeNG v21 drawer y dialog
                '.p-drawer-close-button',
                '.p-dialog-close-button',
                '[data-pc-name="drawer"] button[aria-label*="lose"]',
                '[data-pc-name="dialog"] button[aria-label*="lose"]',
                # Genéricos
                'button[aria-label*="Cerrar"]',
                'button[aria-label*="Close"]',
                'button[aria-label*="close"]',
                '.p-sidebar-close',
                '.close',
            ]
            
            for selector in close_buttons:
                try:
                    btn = await page.query_selector(selector)
                    if btn:
                        await btn.click()
                        await asyncio.sleep(0.5)
                        return
                except:
                    continue
            
            # Si no encuentra botón, presionar Escape
            await page.press('body', 'Escape')
        except:
            pass

    async def _save_results(self):
        """Guardar resultados."""
        summary = {
            "timestamp": datetime.now().isoformat(),
            "total_modules": len(self.captures),
            "successful": len([c for c in self.captures if c["success"]]),
            "modules_with_modals": len([c for c in self.captures if c["modals_detected"] > 0]),
            "modules_with_side_panels": len([c for c in self.captures if c["side_panels_detected"] > 0]),
            "modules_with_tabbed_modals": len([c for c in self.captures if c["modals_with_tabs"] > 0]),
            "modules_with_api_errors": len([c for c in self.captures if c.get("network_errors")]),
            "modules_with_console_errors": len([c for c in self.captures if c.get("console_errors")]),
        }
        
        results = {"summary": summary, "captures": self.captures}
        
        results_path = OUTPUT_DIR / "captures_summary.json"
        with open(results_path, "w", encoding="utf-8") as f:
            json.dump(results, f, indent=2, ensure_ascii=False)
        
        logger.info(f"\n✓ Resultados guardados")
        logger.info(f"  - Módulos: {summary['successful']}/{summary['total_modules']}")
        logger.info(f"  - Con errores API:     {summary['modules_with_api_errors']}")
        logger.info(f"  - Con errores consola: {summary['modules_with_console_errors']}")
        logger.info(f"  - Con modales: {summary['modules_with_modals']}")
        logger.info(f"  - Con side panels: {summary['modules_with_side_panels']}")
        logger.info(f"  - Con pestañas: {summary['modules_with_tabbed_modals']}")

    def _get_phase(self, risk: str) -> int:
        return {"critico": 1, "alto": 1, "medio": 2, "bajo": 3}.get(risk, 3)


async def main():
    if not ADES_PASSWORD:
        raise ValueError("ADES_PASSWORD no configurada")
    
    explorer = AdesExplorerV4()
    
    # OPCIONES DE EJECUCIÓN:
    # phase=1, limit=None  → Todos los módulos críticos/altos (~25) → 30-40 min
    # phase=1, limit=25    → Primeros 25 módulos → 30-40 min
    # phase=1, limit=5     → Test rápido (5 módulos) → 5-10 min
    # phase=1, limit=10    → Test medio (10 módulos) → 15-20 min
    
    logger.info("=== OPCIONES DE EJECUCIÓN ===")
    logger.info("Smoke test (3 módulos):     phase=1, limit=3   →  ~3-5 min")
    logger.info("Fase 1 (critico+alto):      phase=1, limit=None → ~30-40 min (34 módulos)")
    logger.info("Fase 2 (+medio):            phase=2, limit=None → +18 módulos")
    logger.info("Completo (58 módulos):      phase=3, limit=None → ~60-80 min")
    logger.info("=== EJECUTANDO FASE 1 COMPLETA (34 módulos críticos/altos) ===\n")

    await explorer.run(phase=1, limit=None)


if __name__ == "__main__":
    asyncio.run(main())
