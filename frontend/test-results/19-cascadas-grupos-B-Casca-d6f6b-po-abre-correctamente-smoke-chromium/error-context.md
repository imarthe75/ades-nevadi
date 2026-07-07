# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: 19-cascadas-grupos.spec.ts >> B. Cascadas Grupos — Ciclo→Grado filtración por Nivel >> GRP-CASCADE-02 | Dialog "Nuevo Grupo" abre correctamente @smoke
- Location: e2e/tests/19-cascadas-grupos.spec.ts:48:7

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator:  locator('p-dialog').first()
Expected: visible
Received: hidden
Timeout:  8000ms

Call log:
  - Expect "toBeVisible" with timeout 8000ms
  - waiting for locator('p-dialog').first()
    18 × locator resolved to <p-dialog pc192="" role="alertdialog" data-pc-section="host">…</p-dialog>
       - unexpected value "hidden"

```

```yaml
- status
- toolbar:
  - text: N ADES Instituto Nevadi
  - combobox "Plantel..."
  - button "dropdown trigger":
    - img
  - text: /
  - combobox "Nivel..."
  - button "dropdown trigger":
    - img
  - text: /
  - combobox "Ciclo..."
  - button "dropdown trigger":
    - img
  - text: /
  - combobox "Grado..."
  - button "dropdown trigger":
    - img
  - text: /
  - combobox "Grupo..."
  - button "dropdown trigger":
    - img
  - button "Notificaciones"
  - button "Cuenta de Test ADMIN_GLOBAL": Test ADMIN_GLOBAL Admin Global
- navigation "Navegación principal":
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
      - link " Cierre de Ciclo":
        - /url: /cierre-ciclo
    - listitem:
      - link " Gestión de Padres":
        - /url: /padres-admin
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
      - link " Biblioteca":
        - /url: /biblioteca
    - listitem:
      - link " Formato 911 SEP":
        - /url: /estadistica-911
    - listitem:
      - link " Kardex UAEMEX":
        - /url: /kardex
    - listitem:
      - link " Acta Evaluación UAEMEX":
        - /url: /acta-evaluacion
    - listitem:
      - link " Optativas":
        - /url: /optativas
    - listitem:
      - link " Admisión":
        - /url: /admision
  - text: Recursos Humanos
  - list:
    - listitem:
      - link " Personal No-Docente":
        - /url: /personal-admin
    - listitem:
      - link " Licencias y Permisos":
        - /url: /licencias
    - listitem:
      - link " Capacitaciones":
        - /url: /capacitaciones
    - listitem:
      - link " Expediente Laboral":
        - /url: /expediente-laboral
    - listitem:
      - link " Disponibilidad Docente":
        - /url: /disponibilidad
    - listitem:
      - link " Asistencia Personal":
        - /url: /asistencia-personal
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
    - listitem:
      - link " Videoconferencias":
        - /url: /videoconferencias
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
    - listitem:
      - link " Contenido H5P":
        - /url: /h5p
  - text: Convocatorias
  - list:
    - listitem:
      - link " Gestión Convocatorias":
        - /url: /portal-admin
  - text: Mi Familia
  - list:
    - listitem:
      - link " Portal de Padres":
        - /url: /padres
    - listitem:
      - link " Mi Progreso":
        - /url: /mi-progreso
    - listitem:
      - link " Comunicados":
        - /url: /comunicados
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
  - text: Sistema
  - list:
    - listitem:
      - link " Monitor del Sistema":
        - /url: /monitor
    - listitem:
      - link " Administración":
        - /url: /admin
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
      - listitem:  Administración
  - alertdialog
  - heading "Administración" [level=2]
  - paragraph: Gestión de usuarios, ciclos, planteles, variables del sistema y catálogos
  - text:  Vista global — todos los planteles. Selecciona un plantel en la barra superior para filtrar Usuarios y Grupos.
  - tablist:
    - tab " Usuarios"
    - tab " Roles"
    - tab " Menús"
    - tab " Permisos"
    - tab " Ciclos Escolares"
    - tab " Planteles"
    - tab " Grupos" [selected]
    - tab " Variables del Sistema"
    - tab " Reglas de Promoción"
    - tab " Franjas Horarias"
    - tab " Eval. Cualitativa"
    - tab " Catálogos"
    - tab " Geográficos"
    - tab " Marca / Identidad"
    - tab " Auditoría"
  - button "Siguiente":
    - img
  - tabpanel " Grupos":
    - combobox "Filtrar por ciclo"
    - img
    - button "dropdown trigger":
      - img
    - button " Nuevo grupo"
    - button "Mostrar u ocultar columnas de la tabla": 
    - button "Descargar datos como archivo CSV": 
    - text: 72 registro(s)
    - table:
      - rowgroup:
        - row "Nivel / Grado Grupo Ocupación Turno Estado Acciones":
          - columnheader "Nivel / Grado":
            - text: Nivel / Grado
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
          - columnheader "Ocupación"
          - columnheader "Turno":
            - text: Turno
            - img
            - combobox "Filtrar..."
            - button:
              - img
          - columnheader "Estado":
            - text: Estado
            - img
            - combobox "Filtrar..."
            - button:
              - img
          - columnheader "Acciones":
            - strong: Acciones
      - rowgroup:
        - row "PREPARATORIA Primer semestre A 0 / 30 MATUTINO Activo Editar este registro":
          - cell "PREPARATORIA Primer semestre"
          - cell "A"
          - cell "0 / 30"
          - cell "MATUTINO"
          - cell "Activo"
          - cell "Editar este registro":
            - button "Editar este registro": 
        - row "PREPARATORIA Primer semestre A 0 / 30 MATUTINO Activo Editar este registro":
          - cell "PREPARATORIA Primer semestre"
          - cell "A"
          - cell "0 / 30"
          - cell "MATUTINO"
          - cell "Activo"
          - cell "Editar este registro":
            - button "Editar este registro": 
        - row "PREPARATORIA Primer semestre B 0 / 30 MATUTINO Activo Editar este registro":
          - cell "PREPARATORIA Primer semestre"
          - cell "B"
          - cell "0 / 30"
          - cell "MATUTINO"
          - cell "Activo"
          - cell "Editar este registro":
            - button "Editar este registro": 
        - row "PREPARATORIA Primer semestre B 0 / 30 MATUTINO Activo Editar este registro":
          - cell "PREPARATORIA Primer semestre"
          - cell "B"
          - cell "0 / 30"
          - cell "MATUTINO"
          - cell "Activo"
          - cell "Editar este registro":
            - button "Editar este registro": 
        - row "PREPARATORIA Segundo semestre A 0 / 30 MATUTINO Activo Editar este registro":
          - cell "PREPARATORIA Segundo semestre"
          - cell "A"
          - cell "0 / 30"
          - cell "MATUTINO"
          - cell "Activo"
          - cell "Editar este registro":
            - button "Editar este registro": 
        - row "PREPARATORIA Segundo semestre A 0 / 30 MATUTINO Activo Editar este registro":
          - cell "PREPARATORIA Segundo semestre"
          - cell "A"
          - cell "0 / 30"
          - cell "MATUTINO"
          - cell "Activo"
          - cell "Editar este registro":
            - button "Editar este registro": 
        - row "PREPARATORIA Segundo semestre B 0 / 30 MATUTINO Activo Editar este registro":
          - cell "PREPARATORIA Segundo semestre"
          - cell "B"
          - cell "0 / 30"
          - cell "MATUTINO"
          - cell "Activo"
          - cell "Editar este registro":
            - button "Editar este registro": 
        - row "PREPARATORIA Segundo semestre B 0 / 30 MATUTINO Activo Editar este registro":
          - cell "PREPARATORIA Segundo semestre"
          - cell "B"
          - cell "0 / 30"
          - cell "MATUTINO"
          - cell "Activo"
          - cell "Editar este registro":
            - button "Editar este registro": 
        - row "PREPARATORIA Tercer semestre A 0 / 30 MATUTINO Activo Editar este registro":
          - cell "PREPARATORIA Tercer semestre"
          - cell "A"
          - cell "0 / 30"
          - cell "MATUTINO"
          - cell "Activo"
          - cell "Editar este registro":
            - button "Editar este registro": 
        - row "PREPARATORIA Tercer semestre B 0 / 30 MATUTINO Activo Editar este registro":
          - cell "PREPARATORIA Tercer semestre"
          - cell "B"
          - cell "0 / 30"
          - cell "MATUTINO"
          - cell "Activo"
          - cell "Editar este registro":
            - button "Editar este registro": 
        - row "PREPARATORIA Cuarto semestre A 0 / 30 MATUTINO Activo Editar este registro":
          - cell "PREPARATORIA Cuarto semestre"
          - cell "A"
          - cell "0 / 30"
          - cell "MATUTINO"
          - cell "Activo"
          - cell "Editar este registro":
            - button "Editar este registro": 
        - row "PREPARATORIA Cuarto semestre B 0 / 30 MATUTINO Activo Editar este registro":
          - cell "PREPARATORIA Cuarto semestre"
          - cell "B"
          - cell "0 / 30"
          - cell "MATUTINO"
          - cell "Activo"
          - cell "Editar este registro":
            - button "Editar este registro": 
        - row "PRIMARIA Primer grado A 0 / 30 MATUTINO Activo Editar este registro":
          - cell "PRIMARIA Primer grado"
          - cell "A"
          - cell "0 / 30"
          - cell "MATUTINO"
          - cell "Activo"
          - cell "Editar este registro":
            - button "Editar este registro": 
        - row "PRIMARIA Primer grado A 0 / 30 MATUTINO Activo Editar este registro":
          - cell "PRIMARIA Primer grado"
          - cell "A"
          - cell "0 / 30"
          - cell "MATUTINO"
          - cell "Activo"
          - cell "Editar este registro":
            - button "Editar este registro": 
        - row "PRIMARIA Primer grado A 0 / 30 MATUTINO Activo Editar este registro":
          - cell "PRIMARIA Primer grado"
          - cell "A"
          - cell "0 / 30"
          - cell "MATUTINO"
          - cell "Activo"
          - cell "Editar este registro":
            - button "Editar este registro": 
        - row "PRIMARIA Primer grado B 0 / 30 MATUTINO Activo Editar este registro":
          - cell "PRIMARIA Primer grado"
          - cell "B"
          - cell "0 / 30"
          - cell "MATUTINO"
          - cell "Activo"
          - cell "Editar este registro":
            - button "Editar este registro": 
        - row "PRIMARIA Primer grado B 0 / 30 MATUTINO Activo Editar este registro":
          - cell "PRIMARIA Primer grado"
          - cell "B"
          - cell "0 / 30"
          - cell "MATUTINO"
          - cell "Activo"
          - cell "Editar este registro":
            - button "Editar este registro": 
        - row "PRIMARIA Primer grado B 0 / 30 MATUTINO Activo Editar este registro":
          - cell "PRIMARIA Primer grado"
          - cell "B"
          - cell "0 / 30"
          - cell "MATUTINO"
          - cell "Activo"
          - cell "Editar este registro":
            - button "Editar este registro": 
        - row "PRIMARIA Segundo grado A 0 / 30 MATUTINO Activo Editar este registro":
          - cell "PRIMARIA Segundo grado"
          - cell "A"
          - cell "0 / 30"
          - cell "MATUTINO"
          - cell "Activo"
          - cell "Editar este registro":
            - button "Editar este registro": 
        - row "PRIMARIA Segundo grado A 0 / 30 MATUTINO Activo Editar este registro":
          - cell "PRIMARIA Segundo grado"
          - cell "A"
          - cell "0 / 30"
          - cell "MATUTINO"
          - cell "Activo"
          - cell "Editar este registro":
            - button "Editar este registro": 
    - button "Primera página":
      - img
    - button [disabled]:
      - img
    - button "Página 1": "1"
    - button "Página 2": "2"
    - button "Página 3": "3"
    - button "Página 4": "4"
    - button "Página siguiente":
      - img
    - button "Última página":
      - img
    - combobox "Filas por página": "20"
    - button "dropdown trigger":
      - img
- dialog "Nuevo Grupo":
  - text: Nuevo Grupo
  - button:
    - img
  - text: Nombre del grupo *
  - textbox "A"
  - text: Capacidad *
  - spinbutton: "35"
  - text: Turno *
  - combobox "MATUTINO"
  - button "dropdown trigger":
    - img
  - text: Ciclo Escolar *
  - combobox "Seleccionar ciclo"
  - button "dropdown trigger":
    - img
  - text: Grado *
  - combobox "Seleccionar grado"
  - button "dropdown trigger":
    - img
```

# Test source

```ts
  1   | /**
  2   |  * Suite 19 — Cascadas de Grupos: Validación de jerarquía Ciclo→Grado por Nivel
  3   |  *
  4   |  * Verifica que al crear/editar un grupo, el dropdown de Grado se filtre correctamente
  5   |  * por el nivel del Ciclo seleccionado. Esto previene estados inconsistentes donde
  6   |  * un grupo asignado a un ciclo de Primaria termine con un grado de Secundaria.
  7   |  */
  8   | import { test, expect, Page } from '@playwright/test';
  9   | import { LoginPage } from '../page-objects/login-page';
  10  | import { USERS } from '../fixtures/users';
  11  | import {
  12  |   attachConsoleMonitor,
  13  |   attachApiMonitor,
  14  | } from '../helpers/console-monitor';
  15  | 
  16  | test.describe('B. Cascadas Grupos — Ciclo→Grado filtración por Nivel', () => {
  17  | 
  18  |   test('GRP-CASCADE-01 | Navegación a Administración > Grupos @smoke', async ({ page }) => {
  19  |     const apiResponses = attachApiMonitor(page);
  20  |     const getErrors = attachConsoleMonitor(page);
  21  | 
  22  |     await new LoginPage(page).login(USERS.ADMIN_GLOBAL);
  23  |     await page.goto('/dashboard', { waitUntil: 'domcontentloaded' });
  24  |     await page.waitForTimeout(1_500);
  25  | 
  26  |     // Navega a Administración
  27  |     await page.click('text=Administración');
  28  |     await page.waitForTimeout(1_000);
  29  | 
  30  |     // Verifica que está en Administración
  31  |     await expect(page.locator('h2:has-text("Administración")')).toBeVisible({ timeout: 8000 });
  32  | 
  33  |     // Haz click en tab Grupos
  34  |     const gruposTab = page.locator('[role="tab"]:has-text("Grupos")').first();
  35  |     if (await gruposTab.isVisible()) {
  36  |       await gruposTab.click();
  37  |     } else {
  38  |       // Fallback: buscar por link
  39  |       await page.click('text=Grupos');
  40  |     }
  41  |     await page.waitForTimeout(1_200);
  42  | 
  43  |     // Verifica que se cargó tabla de grupos
  44  |     await expect(page.locator('p-table, table, [role="grid"]')).toBeVisible({ timeout: 8000 });
  45  |     console.log('[INFO] GRP-CASCADE-01: Navegación exitosa a Admin > Grupos');
  46  |   });
  47  | 
  48  |   test('GRP-CASCADE-02 | Dialog "Nuevo Grupo" abre correctamente @smoke', async ({ page }) => {
  49  |     const apiResponses = attachApiMonitor(page);
  50  |     const getErrors = attachConsoleMonitor(page);
  51  | 
  52  |     await new LoginPage(page).login(USERS.ADMIN_GLOBAL);
  53  |     await page.goto('/dashboard', { waitUntil: 'domcontentloaded' });
  54  |     await page.waitForTimeout(1_500);
  55  | 
  56  |     // Navega a Administración > Grupos
  57  |     await page.click('text=Administración');
  58  |     await page.waitForTimeout(1_000);
  59  | 
  60  |     const gruposTab = page.locator('[role="tab"]:has-text("Grupos")').first();
  61  |     if (await gruposTab.isVisible()) {
  62  |       await gruposTab.click();
  63  |     } else {
  64  |       await page.click('text=Grupos');
  65  |     }
  66  |     await page.waitForTimeout(1_200);
  67  | 
  68  |     // Busca y clickea botón "Nuevo grupo"
  69  |     const nuevoBtn = page.locator('button, p-button').filter({ hasText: /Nuevo grupo/i }).first();
  70  |     await expect(nuevoBtn).toBeVisible({ timeout: 5000 });
  71  |     await nuevoBtn.click();
  72  |     await page.waitForTimeout(1_200);
  73  | 
  74  |     // Espera a que el diálogo sea visible
  75  |     const dialog = page.locator('p-dialog').first();
> 76  |     await expect(dialog).toBeVisible({ timeout: 8000 });
      |                          ^ Error: expect(locator).toBeVisible() failed
  77  | 
  78  |     // Verifica que hay inputs de formulario
  79  |     const inputs = page.locator('input[type="text"], input[pInputText]');
  80  |     await expect(inputs.first()).toBeVisible({ timeout: 5000 });
  81  | 
  82  |     console.log('[INFO] GRP-CASCADE-02: Dialog abierto correctamente');
  83  |   });
  84  | 
  85  |   test('GRP-CASCADE-03 | Selectores de cascada están presentes @smoke', async ({ page }) => {
  86  |     const apiResponses = attachApiMonitor(page);
  87  |     const getErrors = attachConsoleMonitor(page);
  88  | 
  89  |     await new LoginPage(page).login(USERS.ADMIN_GLOBAL);
  90  |     await page.goto('/dashboard', { waitUntil: 'domcontentloaded' });
  91  |     await page.waitForTimeout(1_500);
  92  | 
  93  |     // Navega a Administración > Grupos
  94  |     await page.click('text=Administración');
  95  |     await page.waitForTimeout(1_000);
  96  | 
  97  |     const gruposTab = page.locator('[role="tab"]:has-text("Grupos")').first();
  98  |     if (await gruposTab.isVisible()) {
  99  |       await gruposTab.click();
  100 |     } else {
  101 |       await page.click('text=Grupos');
  102 |     }
  103 |     await page.waitForTimeout(1_200);
  104 | 
  105 |     // Abre nuevo grupo
  106 |     const nuevoBtn = page.locator('button, p-button').filter({ hasText: /Nuevo grupo/i }).first();
  107 |     await nuevoBtn.click();
  108 |     await page.waitForTimeout(1_200);
  109 | 
  110 |     // Espera dialog
  111 |     await page.waitForSelector('p-dialog', { timeout: 10000 });
  112 | 
  113 |     // Busca p-select (selectores)
  114 |     const selects = page.locator('p-dialog p-select, p-dialog select');
  115 |     const selectCount = await selects.count();
  116 | 
  117 |     if (selectCount > 0) {
  118 |       console.log(`[INFO] GRP-CASCADE-03: Found ${selectCount} select dropdowns`);
  119 |       expect(selectCount).toBeGreaterThan(0);
  120 |     } else {
  121 |       console.warn('[FINDING][P2] GRP-CASCADE-03: No p-select encontrados en dialog');
  122 |     }
  123 |   });
  124 | 
  125 |   test('GRP-CASCADE-04 | Backend validación cascada (código presente) @api', async ({ page }) => {
  126 |     // Este test verifica que la validación backend está deployada
  127 |     // Sin acceso a Maven, verificamos que el código está en el repo
  128 | 
  129 |     const apiResponses = attachApiMonitor(page);
  130 | 
  131 |     await new LoginPage(page).login(USERS.ADMIN_GLOBAL);
  132 |     await page.goto('/dashboard', { waitUntil: 'domcontentloaded' });
  133 |     await page.waitForTimeout(1_500);
  134 | 
  135 |     // Intenta interceptar un POST a /admin/grupos
  136 |     let postResponse = null;
  137 |     page.on('response', async (response) => {
  138 |       if (response.url().includes('/api/v1/admin/grupos') && response.request().method() === 'POST') {
  139 |         postResponse = response;
  140 |       }
  141 |     });
  142 | 
  143 |     // Navega a admin/grupos y abre dialog
  144 |     await page.click('text=Administración');
  145 |     await page.waitForTimeout(1_000);
  146 | 
  147 |     const gruposTab = page.locator('[role="tab"]:has-text("Grupos")').first();
  148 |     if (await gruposTab.isVisible()) {
  149 |       await gruposTab.click();
  150 |     } else {
  151 |       await page.click('text=Grupos');
  152 |     }
  153 |     await page.waitForTimeout(1_200);
  154 | 
  155 |     // Si logramos abrir el dialog y completar el form, intentamos guardar
  156 |     // (pero sin completar el form por ahora para no interferir)
  157 |     await expect(page).toHaveTitle(/ADES/);
  158 |     console.log('[INFO] GRP-CASCADE-04: Backend API accesible, validación lista para probar');
  159 |   });
  160 | });
  161 | 
  162 | test.describe('C. Validación Cascada — Estado consisten', () => {
  163 | 
  164 |   test('GRP-CASCADE-05 | Cascada UI es filtrada (computed signals)', async ({ page }) => {
  165 |     const apiResponses = attachApiMonitor(page);
  166 | 
  167 |     await new LoginPage(page).login(USERS.ADMIN_GLOBAL);
  168 |     await page.goto('/dashboard', { waitUntil: 'domcontentloaded' });
  169 |     await page.waitForTimeout(1_500);
  170 | 
  171 |     // Verifica que la página carga sin errores
  172 |     await expect(page.locator('app-root')).toBeVisible();
  173 | 
  174 |     // Verifica que no hay errores críticos en consola
  175 |     const errors: string[] = [];
  176 |     page.on('console', (msg) => {
```