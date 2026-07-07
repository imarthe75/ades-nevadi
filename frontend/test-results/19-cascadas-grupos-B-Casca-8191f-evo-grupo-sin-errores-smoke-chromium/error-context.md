# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: 19-cascadas-grupos.spec.ts >> B. Cascadas Grupos - Ciclo a Grado filtracion por Nivel >> GRP-CASCADE-01 | Abre dialog "Nuevo grupo" sin errores @smoke
- Location: e2e/tests/19-cascadas-grupos.spec.ts:20:7

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: locator('[data-testid="btn-nuevo-grupo"]')
Expected: visible
Timeout: 5000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 5000ms
  - waiting for locator('[data-testid="btn-nuevo-grupo"]')

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
```

# Test source

```ts
  1   | /**
  2   |  * Suite 19 — Cascadas de Grupos: Validación de jerarquía Ciclo→Grado por Nivel
  3   |  *
  4   |  * Verifica que al crear/editar un grupo, el dropdown de Grado se filtre correctamente
  5   |  * por el nivel del Ciclo seleccionado. Esto previene estados inconsistentes donde
  6   |  * un grupo asignado a un ciclo de Primaria termine con un grado de Secundaria.
  7   |  *
  8   |  * Tests usando data-testid para 100% coverage
  9   |  */
  10  | import { test, expect, Page } from '@playwright/test';
  11  | import { LoginPage } from '../page-objects/login-page';
  12  | import { USERS } from '../fixtures/users';
  13  | import {
  14  |   attachConsoleMonitor,
  15  |   attachApiMonitor,
  16  | } from '../helpers/console-monitor';
  17  | 
  18  | test.describe('B. Cascadas Grupos - Ciclo a Grado filtracion por Nivel', () => {
  19  | 
  20  |   test('GRP-CASCADE-01 | Abre dialog "Nuevo grupo" sin errores @smoke', async ({ page }) => {
  21  |     const apiResponses = attachApiMonitor(page);
  22  |     const getErrors = attachConsoleMonitor(page);
  23  | 
  24  |     await new LoginPage(page).login(USERS.ADMIN_GLOBAL);
  25  |     await page.goto('/dashboard', { waitUntil: 'domcontentloaded' });
  26  |     await page.waitForTimeout(1_500);
  27  | 
  28  |     // Navega a Administración
  29  |     await page.click('text=Administración');
  30  |     await page.waitForTimeout(1_000);
  31  | 
  32  |     // Verifica que está en Administración
  33  |     await expect(page.locator('h2:has-text("Administración")')).toBeVisible({ timeout: 8000 });
  34  | 
  35  |     // Haz click en tab Grupos
  36  |     const gruposTab = page.locator('[role="tab"]:has-text("Grupos")').first();
  37  |     if (await gruposTab.isVisible()) {
  38  |       await gruposTab.click();
  39  |     }
  40  |     await page.waitForTimeout(1_200);
  41  | 
  42  |     // Busca botón "Nuevo grupo" por data-testid
  43  |     const nuevoBtn = page.locator('[data-testid="btn-nuevo-grupo"]');
> 44  |     await expect(nuevoBtn).toBeVisible({ timeout: 5000 });
      |                            ^ Error: expect(locator).toBeVisible() failed
  45  |     await nuevoBtn.click();
  46  |     await page.waitForTimeout(1_200);
  47  | 
  48  |     // Verifica que el dialog está visible por data-testid
  49  |     const dialog = page.locator('[data-testid="dialog-grupo-admin"]');
  50  |     await expect(dialog).toBeVisible({ timeout: 8000 });
  51  | 
  52  |     // Verifica que el formulario está visible
  53  |     const form = page.locator('[data-testid="grupo-form"]');
  54  |     await expect(form).toBeVisible({ timeout: 5000 });
  55  | 
  56  |     console.log('[INFO] GRP-CASCADE-01: Dialog abierto correctamente');
  57  |   });
  58  | 
  59  |   test('GRP-CASCADE-02 | Campos de cascada están presentes: Ciclo y Grado @smoke', async ({ page }) => {
  60  |     const apiResponses = attachApiMonitor(page);
  61  |     const getErrors = attachConsoleMonitor(page);
  62  | 
  63  |     await new LoginPage(page).login(USERS.ADMIN_GLOBAL);
  64  |     await page.goto('/dashboard', { waitUntil: 'domcontentloaded' });
  65  |     await page.waitForTimeout(1_500);
  66  | 
  67  |     // Navega a Administración > Grupos
  68  |     await page.click('text=Administración');
  69  |     await page.waitForTimeout(1_000);
  70  | 
  71  |     const gruposTab = page.locator('[role="tab"]:has-text("Grupos")').first();
  72  |     if (await gruposTab.isVisible()) {
  73  |       await gruposTab.click();
  74  |     }
  75  |     await page.waitForTimeout(1_200);
  76  | 
  77  |     // Abre nuevo grupo
  78  |     const nuevoBtn = page.locator('[data-testid="btn-nuevo-grupo"]');
  79  |     await nuevoBtn.click();
  80  |     await page.waitForTimeout(1_200);
  81  | 
  82  |     // Verifica que los selectores de cascada están presentes
  83  |     const cicloSelect = page.locator('[data-testid="select-ciclo"]');
  84  |     const gradoSelect = page.locator('[data-testid="select-grado"]');
  85  | 
  86  |     await expect(cicloSelect).toBeVisible({ timeout: 5000 });
  87  |     await expect(gradoSelect).toBeVisible({ timeout: 5000 });
  88  | 
  89  |     console.log('[INFO] GRP-CASCADE-02: Selectores Ciclo y Grado presentes');
  90  |   });
  91  | 
  92  |   test('GRP-CASCADE-03 | Seleccionar Ciclo Primaria muestra grados filtrados @smoke', async ({ page }) => {
  93  |     const apiResponses = attachApiMonitor(page);
  94  |     const getErrors = attachConsoleMonitor(page);
  95  | 
  96  |     await new LoginPage(page).login(USERS.ADMIN_GLOBAL);
  97  |     await page.goto('/dashboard', { waitUntil: 'domcontentloaded' });
  98  |     await page.waitForTimeout(1_500);
  99  | 
  100 |     // Navega a Administración > Grupos
  101 |     await page.click('text=Administración');
  102 |     await page.waitForTimeout(1_000);
  103 | 
  104 |     const gruposTab = page.locator('[role="tab"]:has-text("Grupos")').first();
  105 |     if (await gruposTab.isVisible()) {
  106 |       await gruposTab.click();
  107 |     }
  108 |     await page.waitForTimeout(1_200);
  109 | 
  110 |     // Abre nuevo grupo
  111 |     const nuevoBtn = page.locator('[data-testid="btn-nuevo-grupo"]');
  112 |     await nuevoBtn.click();
  113 |     await page.waitForTimeout(1_200);
  114 | 
  115 |     // Busca selector Ciclo
  116 |     const cicloSelect = page.locator('[data-testid="select-ciclo"]');
  117 |     await expect(cicloSelect).toBeVisible({ timeout: 5000 });
  118 | 
  119 |     // Click en el select para abrir dropdown
  120 |     await cicloSelect.click();
  121 |     await page.waitForTimeout(600);
  122 | 
  123 |     // Busca opción que contenga "Primaria"
  124 |     const primariaCicloOption = page.locator('.p-select-option, [role="option"]')
  125 |       .filter({ hasText: /Primaria/ })
  126 |       .first();
  127 | 
  128 |     // Espera a que sea visible
  129 |     await expect(primariaCicloOption).toBeVisible({ timeout: 5000 });
  130 |     await primariaCicloOption.click();
  131 |     await page.waitForTimeout(800);
  132 | 
  133 |     // Ahora abre el selector Grado y verifica que muestra grados de Primaria
  134 |     const gradoSelect = page.locator('[data-testid="select-grado"]');
  135 |     await expect(gradoSelect).toBeVisible({ timeout: 5000 });
  136 |     await gradoSelect.click();
  137 |     await page.waitForTimeout(600);
  138 | 
  139 |     // Recopila todas las opciones visibles
  140 |     const gradoOptions = page.locator('.p-select-option, [role="option"]');
  141 |     const count = await gradoOptions.count();
  142 |     const gradoLabels: string[] = [];
  143 |     for (let i = 0; i < count; i++) {
  144 |       const text = ((await gradoOptions.nth(i).textContent()) ?? '').trim();
```