# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: 02-alumnos.spec.ts >> A. Flujo feliz >> ALU-02 | crear alumno con datos válidos
- Location: e2e/tests/02-alumnos.spec.ts:38:7

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: locator('.p-toast-message-success')
Expected: visible
Timeout: 8000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 8000ms
  - waiting for locator('.p-toast-message-success')

```

```yaml
- toolbar:
  - text: N ADES Instituto Nevadi
  - combobox "— Todo el Instituto —"
  - button "dropdown trigger":
    - img
  - text: /
  - combobox "TODOS"
  - button "dropdown trigger":
    - img
  - text: /
  - combobox "2026-2027"
  - button "dropdown trigger":
    - img
  - text:  T Test COORDINADOR_ACADEMICO Coord. Académico 
- navigation:
  - text: Principal
  - list:
    - listitem:
      - link " Dashboard":
        - /url: /dashboard
  - text: Académico
  - list:
    - listitem:
      - link " Alumnos":
        - /url: /alumnos
    - listitem:
      - link " Reinscripción":
        - /url: /reinscripcion
    - listitem:
      - link " Profesores":
        - /url: /profesores
    - listitem:
      - link " Grupos":
        - /url: /grupos
    - listitem:
      - link " Aulas":
        - /url: /aulas
    - listitem:
      - link " Planes de Estudio":
        - /url: /planes-estudio
    - listitem:
      - link " Calificaciones":
        - /url: /calificaciones
    - listitem:
      - link " Evaluaciones":
        - /url: /evaluaciones
    - listitem:
      - link " Asistencias":
        - /url: /asistencias
    - listitem:
      - link " Tareas":
        - /url: /tareas
    - listitem:
      - link " Planeación":
        - /url: /planeacion
  - text: Operaciones
  - list:
    - listitem:
      - link " Horarios":
        - /url: /horarios
    - listitem:
      - link " Calendario Escolar":
        - /url: /calendario
    - listitem:
      - link " Conducta":
        - /url: /conducta
    - listitem:
      - link " Expediente Médico":
        - /url: /medico
    - listitem:
      - link " Condiciones Crónicas":
        - /url: /condiciones-cronicas
    - listitem:
      - link " Justificaciones Faltas":
        - /url: /justificaciones
    - listitem:
      - link " Movilidad Estudiantil":
        - /url: /movilidad
    - listitem:
      - link " Optativas":
        - /url: /optativas
    - listitem:
      - link " Admisión":
        - /url: /admision
  - text: Comunicación
  - list:
    - listitem:
      - link " Comunicados":
        - /url: /comunicados
    - listitem:
      - link " Foros y Anuncios":
        - /url: /foros
    - listitem:
      - link " Encuestas":
        - /url: /encuestas
  - text: Gradebook
  - list:
    - listitem:
      - link " Gradebook":
        - /url: /gradebook
    - listitem:
      - link " Mi Progreso":
        - /url: /mi-progreso
    - listitem:
      - link " Ponderaciones":
        - /url: /ponderacion-config
  - text: Recursos
  - list:
    - listitem:
      - link " Rúbricas":
        - /url: /rubricas
    - listitem:
      - link " Insignias":
        - /url: /badges
    - listitem:
      - link " Portal Alumno":
        - /url: /portal
  - text: Inteligencia
  - list:
    - listitem:
      - link " Dashboards BI":
        - /url: /bi
    - listitem:
      - link " Grade Analytics":
        - /url: /grade-analytics
    - listitem:
      - link " Asistente IA + Datos":
        - /url: /ia
    - listitem:
      - link " Eval. Docente 360°":
        - /url: /eval-docente
    - listitem:
      - link " Learning Paths":
        - /url: /learning-paths
  - text: Reportes
  - list:
    - listitem:
      - link " Generador de Reportes":
        - /url: /reportes
    - listitem:
      - link " Certificados Digitales":
        - /url: /certificados
    - listitem:
      - link " Expediente Digital":
        - /url: /expediente-doc
  - text: Ayuda
  - list:
    - listitem:
      - link " Manual de Usuario":
        - /url: /ayuda
- main:
  - navigation "Breadcrumb":
    - list:
      - listitem:
        - link "Home":
          - /url: /
      - listitem:  Alumnos
  - heading "Alumnos" [level=2]
  - paragraph: 0 alumno(s) registrado(s)
  - button ""
  - button " CSV"
  - button " Excel"
  - button " Importar CSV/Excel"
  - button " Plantilla"
  - button " Nuevo alumno"
  - text: 
  - textbox "Buscar alumno..."
  - button ""
  - button ""
  - text: 0 registro(s)
  - table:
    - rowgroup:
      - row "Matrícula Nombre Completo CURP NSS Nivel Grado Grupo Ingreso Acciones":
        - columnheader "Matrícula":
          - text: Matrícula
          - img
          - combobox "Filtrar..."
          - button:
            - img
        - columnheader "Nombre Completo":
          - text: Nombre Completo
          - img
          - combobox "Filtrar..."
          - button:
            - img
        - columnheader "CURP":
          - text: CURP
          - img
          - combobox "Filtrar..."
          - button:
            - img
        - columnheader "NSS"
        - columnheader "Nivel":
          - text: Nivel
          - img
          - combobox "Filtrar..."
          - button:
            - img
        - columnheader "Grado":
          - text: Grado
          - img
          - combobox "Filtrar..."
          - button:
            - img
        - columnheader "Grupo":
          - text: Grupo
          - img
          - combobox "Filtrar..."
          - button:
            - img
        - columnheader "Ingreso":
          - text: Ingreso
          - img
        - columnheader "Acciones":
          - strong: Acciones
    - rowgroup:
      - row "Sin registros":
        - cell "Sin registros"
  - button "Primera página":
    - img
  - button [disabled]:
    - img
  - button "Página siguiente" [disabled]:
    - img
  - button "Última página" [disabled]:
    - img
  - combobox "Filas por página" [disabled]: "20"
  - button "dropdown trigger":
    - img
  - dialog "Nuevo Alumno":
    - text: Nuevo Alumno
    - button:
      - img
    - text: Nombre(s) *
    - textbox: Berta
    - text: Apellido paterno *
    - textbox: Frías de Salinas
    - text: Apellido materno
    - textbox: Caballero Pagan
    - text: CURP (18 caracteres) *
    - textbox: FETWLD060423MVZ63J
    - paragraph: Una vez creado podrás completar el expediente completo desde el perfil.
    - button "Crear alumno"
```

# Test source

```ts
  1   | /**
  2   |  * MÓDULO 5 — Alumnos
  3   |  * ALU-01..12 + CU-03 (Inscripción) + escenarios humanos A/B/C/D
  4   |  */
  5   | import { test, expect, Page } from '@playwright/test';
  6   | import { LoginPage } from '../page-objects/login-page';
  7   | import { AlumnosPage } from '../page-objects/alumnos-page';
  8   | import { USERS } from '../fixtures/users';
  9   | import {
  10  |   alumnoValido, curpValido, curpInvalido,
  11  |   EDGE_STRINGS, faker, fechaFutura,
  12  | } from '../fixtures/data-generators';
  13  | import * as path from 'path';
  14  | import * as fs from 'fs';
  15  | import * as os from 'os';
  16  | 
  17  | test.use({ storageState: undefined });
  18  | 
  19  | // Helper: login + ir a alumnos
  20  | async function setupAlumnos(page: Page) {
  21  |   const lp = new LoginPage(page);
  22  |   await lp.login(USERS.COORDINADOR);
  23  |   const ap = new AlumnosPage(page);
  24  |   await ap.navigate();
  25  |   return ap;
  26  | }
  27  | 
  28  | // ── A. Flujo feliz ────────────────────────────────────────────────────────────
  29  | 
  30  | test.describe('A. Flujo feliz', () => {
  31  |   test('ALU-01 | lista alumnos visible con búsqueda', async ({ page }) => {
  32  |     const ap = await setupAlumnos(page);
  33  |     await expect(ap.table).toBeVisible();
  34  |     await ap.searchFor('García');
  35  |     await ap.waitSpinner();
  36  |   });
  37  | 
  38  |   test('ALU-02 | crear alumno con datos válidos', async ({ page }) => {
  39  |     const ap   = await setupAlumnos(page);
  40  |     const data = alumnoValido();
  41  |     await ap.openNewForm();
  42  |     await ap.fillAlumnoForm(data);
  43  |     await ap.save();
  44  |     // QA-007 corregido — BFF ahora crea Persona+Estudiante correctamente
> 45  |     await expect(page.locator('.p-toast-message-success')).toBeVisible({ timeout: 8_000 });
      |                                                            ^ Error: expect(locator).toBeVisible() failed
  46  |   });
  47  | 
  48  |   test('ALU-04 | perfil alumno abre al hacer click en fila', async ({ page }) => {
  49  |     const ap = await setupAlumnos(page);
  50  |     await ap.clickFirstRow();
  51  |     // p-drawer abre con .perfil-meta visible — único contenido que solo existe cuando el drawer está abierto
  52  |     // (app-alumno-perfil siempre está en el DOM pero oculto hasta que p-drawer se abre)
  53  |     const drawerContent = page.locator('.perfil-meta, .matricula-chip, [data-pc-section="content"] .p-tabs');
  54  |     await drawerContent.first().waitFor({ state: 'visible', timeout: 15_000 });
  55  |     await expect(drawerContent.first()).toBeVisible({ timeout: 15_000 });
  56  |   });
  57  | 
  58  |   test('ALU-07 | panel detalle alumno con pestañas', async ({ page }) => {
  59  |     const ap = await setupAlumnos(page);
  60  |     await ap.clickFirstRow();
  61  |     // Wait for drawer to fully load before accessing tabs
  62  |     await page.locator('.perfil-meta, [data-pc-section="content"]').first().waitFor({ state: 'visible', timeout: 15_000 });
  63  |     const tabs = ['personal', 'domicilio', 'academico', 'salud', 'contactos', 'bajas'];
  64  |     for (const tab of tabs) {
  65  |       const tabEl = page.locator(`[value="${tab}"], .p-tab:has-text("${tab}")`).first();
  66  |       if (await tabEl.isVisible({ timeout: 3_000 }).catch(() => false)) {
  67  |         await tabEl.click();
  68  |         await ap.waitSpinner();
  69  |       }
  70  |     }
  71  |   });
  72  | });
  73  | 
  74  | // ── B. Errores típicos ────────────────────────────────────────────────────────
  75  | 
  76  | test.describe('B. Errores de validación', () => {
  77  |   test('ALU-03 | CURP duplicado → 409 Conflict', async ({ page }) => {
  78  |     const ap = await setupAlumnos(page);
  79  |     // Crear un alumno primero para tener una CURP en el sistema
  80  |     const data = alumnoValido();
  81  |     await ap.openNewForm();
  82  |     await ap.fillAlumnoForm(data);
  83  |     await ap.save();
  84  |     await page.locator('.p-toast-message').first().waitFor({ timeout: 8_000 });
  85  |     await page.keyboard.press('Escape');
  86  |     await page.waitForTimeout(300);
  87  |     // Intentar crear otro con la misma CURP → debe dar error
  88  |     await ap.openNewForm();
  89  |     await ap.fillAlumnoForm(data); // misma CURP
  90  |     await ap.save();
  91  |     await expect(page.locator('.p-toast-message-error')).toBeVisible({ timeout: 8_000 });
  92  |   });
  93  | 
  94  |   test('ALU-05 | inscripción doble en mismo ciclo → error', async ({ page }) => {
  95  |     const ap = await setupAlumnos(page);
  96  |     await ap.clickFirstRow();
  97  |     // Wait for drawer to load
  98  |     await page.locator('[data-pc-section="content"]').first().waitFor({ state: 'visible', timeout: 15_000 });
  99  |     const inscribirBtn = page.locator('button:has-text("Inscribir"), [data-testid="btn-inscribir"]');
  100 |     if (await inscribirBtn.isVisible({ timeout: 3_000 }).catch(() => false)) {
  101 |       await inscribirBtn.click();
  102 |       // Si ya está inscrito debe aparecer error
  103 |       const error = page.locator('.p-toast-message-error, [data-testid="error-inscripcion"]');
  104 |       await error.waitFor({ timeout: 8_000 }).catch(() => undefined);
  105 |     }
  106 |   });
  107 | 
  108 |   test('ALU-11 | submit sin CURP → warning de validación', async ({ page }) => {
  109 |     const ap = await setupAlumnos(page);
  110 |     await ap.openNewForm();
  111 |     // Solo llenar nombre, sin CURP
  112 |     await ap.fillAndBlur(ap.nombreInput, 'Pedro');
  113 |     await ap.fillAndBlur(ap.apPaternoInput, 'López');
  114 |     await ap.save();
  115 |     // El componente usa notify.warning() no ng-invalid
  116 |     await expect(page.locator('.p-toast-message-warn, .p-toast-message-error')).toBeVisible({ timeout: 5_000 });
  117 |   });
  118 | 
  119 |   test('ALU-12 | CURP de 5 chars → warning longitud', async ({ page }) => {
  120 |     const ap = await setupAlumnos(page);
  121 |     await ap.openNewForm();
  122 |     await ap.fillAndBlur(ap.nombreInput, 'Ana');
  123 |     await ap.fillAndBlur(ap.apPaternoInput, 'Pérez');
  124 |     await ap.fillAndBlur(ap.curpInput, '12345');
  125 |     await ap.save();
  126 |     // El componente verifica que CURP tenga exactamente 18 chars
  127 |     await expect(page.locator('.p-toast-message-warn, .p-toast-message-error')).toBeVisible({ timeout: 5_000 });
  128 |   });
  129 | 
  130 |   test('ALU-10 | RBAC plantel — docente solo ve su plantel', async ({ page }) => {
  131 |     const lp = new LoginPage(page);
  132 |     await lp.login(USERS.DOCENTE);
  133 |     const ap = new AlumnosPage(page);
  134 |     await ap.navigate();
  135 |     // No debe poder filtrar por otro plantel
  136 |     const otherPlantelOption = page.locator('.plantel-option', { hasText: 'Tenancingo' });
  137 |     await expect(otherPlantelOption).not.toBeVisible();
  138 |   });
  139 | });
  140 | 
  141 | // ── C. Usuario torpe ──────────────────────────────────────────────────────────
  142 | 
  143 | test.describe('C. Usuario torpe', () => {
  144 |   test('ALU-C1 | navega atrás en medio del formulario de creación', async ({ page }) => {
  145 |     const ap   = await setupAlumnos(page);
```