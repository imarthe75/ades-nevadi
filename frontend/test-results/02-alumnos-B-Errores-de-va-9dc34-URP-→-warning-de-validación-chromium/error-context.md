# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: 02-alumnos.spec.ts >> B. Errores de validación >> ALU-11 | submit sin CURP → warning de validación
- Location: e2e/tests/02-alumnos.spec.ts:108:7

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: locator('.p-toast-message-warn, .p-toast-message-error')
Expected: visible
Error: strict mode violation: locator('.p-toast-message-warn, .p-toast-message-error') resolved to 2 elements:
    1) <div pc137="" role="alert" data-p="error" aria-atomic="true" aria-live="assertive" data-pc-section="message" class="p-toast-message p-toast-message-error">…</div> aka getByRole('alert').filter({ hasText: 'Error No se pudieron cargar' })
    2) <div pc151="" role="alert" data-p="warn" aria-atomic="true" aria-live="assertive" data-pc-section="message" class="p-toast-message p-toast-message-warn p-toast-message-enter-active p-toast-message-enter-to">…</div> aka getByRole('alert').filter({ hasText: 'Campos requeridos Nombre,' })

Call log:
  - Expect "toBeVisible" with timeout 5000ms
  - waiting for locator('.p-toast-message-warn, .p-toast-message-error')

```

# Page snapshot

```yaml
- generic [ref=e3]:
  - generic [ref=e4]:
    - alert [ref=e6]:
      - generic [ref=e7]:
        - img [ref=e8]
        - generic [ref=e11]:
          - generic [ref=e12]: Error
          - generic [ref=e13]: No se pudieron cargar los alumnos
        - button "Cerrar" [ref=e15] [cursor=pointer]:
          - img [ref=e16]
    - alert [ref=e19]:
      - generic [ref=e20]:
        - img [ref=e21]
        - generic [ref=e26]:
          - generic [ref=e27]: Campos requeridos
          - generic [ref=e28]: Nombre, apellido paterno y CURP son obligatorios
        - button "Cerrar" [ref=e30] [cursor=pointer]:
          - img [ref=e31]
  - toolbar [ref=e33]:
    - generic [ref=e34]:
      - generic [ref=e35]:
        - generic [ref=e36]: "N"
        - generic [ref=e37]:
          - generic [ref=e38]: ADES
          - generic [ref=e39]: Instituto Nevadi
      - generic [ref=e41] [cursor=pointer]:
        - combobox "— Todo el Instituto —" [ref=e42]
        - button "dropdown trigger" [ref=e43]:
          - img [ref=e44]
      - generic [ref=e46]: /
      - generic [ref=e47] [cursor=pointer]:
        - combobox "TODOS" [ref=e48]
        - button "dropdown trigger" [ref=e49]:
          - img [ref=e50]
      - generic [ref=e52]: /
      - generic [ref=e53] [cursor=pointer]:
        - combobox "2026-2027" [ref=e54]
        - button "dropdown trigger" [ref=e55]:
          - img [ref=e56]
    - generic [ref=e58]:
      - generic [ref=e60] [cursor=pointer]: 
      - generic [ref=e61] [cursor=pointer]:
        - generic [ref=e62]: T
        - generic [ref=e63]:
          - generic [ref=e64]: Test COORDINADOR_ACADEMICO
          - generic [ref=e65]: Coord. Académico
        - generic [ref=e66]: 
  - generic [ref=e67]:
    - navigation [ref=e68]:
      - generic [ref=e69]: Principal
      - list [ref=e70]:
        - listitem [ref=e71]:
          - link " Dashboard" [ref=e72] [cursor=pointer]:
            - /url: /dashboard
            - generic [ref=e73]: 
            - generic [ref=e74]: Dashboard
      - generic [ref=e75]: Académico
      - list [ref=e76]:
        - listitem [ref=e77]:
          - link " Alumnos" [ref=e78] [cursor=pointer]:
            - /url: /alumnos
            - generic [ref=e79]: 
            - generic [ref=e80]: Alumnos
        - listitem [ref=e81]:
          - link " Reinscripción" [ref=e82] [cursor=pointer]:
            - /url: /reinscripcion
            - generic [ref=e83]: 
            - generic [ref=e84]: Reinscripción
        - listitem [ref=e85]:
          - link " Profesores" [ref=e86] [cursor=pointer]:
            - /url: /profesores
            - generic [ref=e87]: 
            - generic [ref=e88]: Profesores
        - listitem [ref=e89]:
          - link " Grupos" [ref=e90] [cursor=pointer]:
            - /url: /grupos
            - generic [ref=e91]: 
            - generic [ref=e92]: Grupos
        - listitem [ref=e93]:
          - link " Aulas" [ref=e94] [cursor=pointer]:
            - /url: /aulas
            - generic [ref=e95]: 
            - generic [ref=e96]: Aulas
        - listitem [ref=e97]:
          - link " Planes de Estudio" [ref=e98] [cursor=pointer]:
            - /url: /planes-estudio
            - generic [ref=e99]: 
            - generic [ref=e100]: Planes de Estudio
        - listitem [ref=e101]:
          - link " Calificaciones" [ref=e102] [cursor=pointer]:
            - /url: /calificaciones
            - generic [ref=e103]: 
            - generic [ref=e104]: Calificaciones
        - listitem [ref=e105]:
          - link " Evaluaciones" [ref=e106] [cursor=pointer]:
            - /url: /evaluaciones
            - generic [ref=e107]: 
            - generic [ref=e108]: Evaluaciones
        - listitem [ref=e109]:
          - link " Asistencias" [ref=e110] [cursor=pointer]:
            - /url: /asistencias
            - generic [ref=e111]: 
            - generic [ref=e112]: Asistencias
        - listitem [ref=e113]:
          - link " Tareas" [ref=e114] [cursor=pointer]:
            - /url: /tareas
            - generic [ref=e115]: 
            - generic [ref=e116]: Tareas
        - listitem [ref=e117]:
          - link " Planeación" [ref=e118] [cursor=pointer]:
            - /url: /planeacion
            - generic [ref=e119]: 
            - generic [ref=e120]: Planeación
      - generic [ref=e121]: Operaciones
      - list [ref=e122]:
        - listitem [ref=e123]:
          - link " Horarios" [ref=e124] [cursor=pointer]:
            - /url: /horarios
            - generic [ref=e125]: 
            - generic [ref=e126]: Horarios
        - listitem [ref=e127]:
          - link " Calendario Escolar" [ref=e128] [cursor=pointer]:
            - /url: /calendario
            - generic [ref=e129]: 
            - generic [ref=e130]: Calendario Escolar
        - listitem [ref=e131]:
          - link " Conducta" [ref=e132] [cursor=pointer]:
            - /url: /conducta
            - generic [ref=e133]: 
            - generic [ref=e134]: Conducta
        - listitem [ref=e135]:
          - link " Expediente Médico" [ref=e136] [cursor=pointer]:
            - /url: /medico
            - generic [ref=e137]: 
            - generic [ref=e138]: Expediente Médico
        - listitem [ref=e139]:
          - link " Condiciones Crónicas" [ref=e140] [cursor=pointer]:
            - /url: /condiciones-cronicas
            - generic [ref=e141]: 
            - generic [ref=e142]: Condiciones Crónicas
        - listitem [ref=e143]:
          - link " Justificaciones Faltas" [ref=e144] [cursor=pointer]:
            - /url: /justificaciones
            - generic [ref=e145]: 
            - generic [ref=e146]: Justificaciones Faltas
        - listitem [ref=e147]:
          - link " Movilidad Estudiantil" [ref=e148] [cursor=pointer]:
            - /url: /movilidad
            - generic [ref=e149]: 
            - generic [ref=e150]: Movilidad Estudiantil
        - listitem [ref=e151]:
          - link " Optativas" [ref=e152] [cursor=pointer]:
            - /url: /optativas
            - generic [ref=e153]: 
            - generic [ref=e154]: Optativas
        - listitem [ref=e155]:
          - link " Admisión" [ref=e156] [cursor=pointer]:
            - /url: /admision
            - generic [ref=e157]: 
            - generic [ref=e158]: Admisión
      - generic [ref=e159]: Comunicación
      - list [ref=e160]:
        - listitem [ref=e161]:
          - link " Comunicados" [ref=e162] [cursor=pointer]:
            - /url: /comunicados
            - generic [ref=e163]: 
            - generic [ref=e164]: Comunicados
        - listitem [ref=e165]:
          - link " Foros y Anuncios" [ref=e166] [cursor=pointer]:
            - /url: /foros
            - generic [ref=e167]: 
            - generic [ref=e168]: Foros y Anuncios
        - listitem [ref=e169]:
          - link " Encuestas" [ref=e170] [cursor=pointer]:
            - /url: /encuestas
            - generic [ref=e171]: 
            - generic [ref=e172]: Encuestas
      - generic [ref=e173]: Gradebook
      - list [ref=e174]:
        - listitem [ref=e175]:
          - link " Gradebook" [ref=e176] [cursor=pointer]:
            - /url: /gradebook
            - generic [ref=e177]: 
            - generic [ref=e178]: Gradebook
        - listitem [ref=e179]:
          - link " Mi Progreso" [ref=e180] [cursor=pointer]:
            - /url: /mi-progreso
            - generic [ref=e181]: 
            - generic [ref=e182]: Mi Progreso
        - listitem [ref=e183]:
          - link " Ponderaciones" [ref=e184] [cursor=pointer]:
            - /url: /ponderacion-config
            - generic [ref=e185]: 
            - generic [ref=e186]: Ponderaciones
      - generic [ref=e187]: Recursos
      - list [ref=e188]:
        - listitem [ref=e189]:
          - link " Rúbricas" [ref=e190] [cursor=pointer]:
            - /url: /rubricas
            - generic [ref=e191]: 
            - generic [ref=e192]: Rúbricas
        - listitem [ref=e193]:
          - link " Insignias" [ref=e194] [cursor=pointer]:
            - /url: /badges
            - generic [ref=e195]: 
            - generic [ref=e196]: Insignias
        - listitem [ref=e197]:
          - link " Portal Alumno" [ref=e198] [cursor=pointer]:
            - /url: /portal
            - generic [ref=e199]: 
            - generic [ref=e200]: Portal Alumno
      - generic [ref=e201]: Inteligencia
      - list [ref=e202]:
        - listitem [ref=e203]:
          - link " Dashboards BI" [ref=e204] [cursor=pointer]:
            - /url: /bi
            - generic [ref=e205]: 
            - generic [ref=e206]: Dashboards BI
        - listitem [ref=e207]:
          - link " Grade Analytics" [ref=e208] [cursor=pointer]:
            - /url: /grade-analytics
            - generic [ref=e209]: 
            - generic [ref=e210]: Grade Analytics
        - listitem [ref=e211]:
          - link " Asistente IA + Datos" [ref=e212] [cursor=pointer]:
            - /url: /ia
            - generic [ref=e213]: 
            - generic [ref=e214]: Asistente IA + Datos
        - listitem [ref=e215]:
          - link " Eval. Docente 360°" [ref=e216] [cursor=pointer]:
            - /url: /eval-docente
            - generic [ref=e217]: 
            - generic [ref=e218]: Eval. Docente 360°
        - listitem [ref=e219]:
          - link " Learning Paths" [ref=e220] [cursor=pointer]:
            - /url: /learning-paths
            - generic [ref=e221]: 
            - generic [ref=e222]: Learning Paths
      - generic [ref=e223]: Reportes
      - list [ref=e224]:
        - listitem [ref=e225]:
          - link " Generador de Reportes" [ref=e226] [cursor=pointer]:
            - /url: /reportes
            - generic [ref=e227]: 
            - generic [ref=e228]: Generador de Reportes
        - listitem [ref=e229]:
          - link " Certificados Digitales" [ref=e230] [cursor=pointer]:
            - /url: /certificados
            - generic [ref=e231]: 
            - generic [ref=e232]: Certificados Digitales
        - listitem [ref=e233]:
          - link " Expediente Digital" [ref=e234] [cursor=pointer]:
            - /url: /expediente-doc
            - generic [ref=e235]: 
            - generic [ref=e236]: Expediente Digital
      - generic [ref=e237]: Ayuda
      - list [ref=e238]:
        - listitem [ref=e239]:
          - link " Manual de Usuario" [ref=e240] [cursor=pointer]:
            - /url: /ayuda
            - generic [ref=e241]: 
            - generic [ref=e242]: Manual de Usuario
    - main [ref=e243]:
      - navigation "Breadcrumb" [ref=e246]:
        - list [ref=e247]:
          - listitem [ref=e248]:
            - link "Home" [ref=e249] [cursor=pointer]:
              - /url: /
          - listitem [ref=e250]:
            - generic [ref=e251]: 
            - generic [ref=e252]: Alumnos
      - generic [ref=e253]:
        - generic [ref=e254]:
          - generic [ref=e255]:
            - heading "Alumnos" [level=2] [ref=e256]
            - paragraph [ref=e257]: 0 alumno(s) registrado(s)
          - generic [ref=e258]:
            - button "" [ref=e261] [cursor=pointer]:
              - generic [ref=e262]: 
            - button " CSV" [ref=e264] [cursor=pointer]:
              - generic [ref=e265]: 
              - generic [ref=e266]: CSV
            - button " Excel" [ref=e268] [cursor=pointer]:
              - generic [ref=e269]: 
              - generic [ref=e270]: Excel
            - generic [ref=e272]:
              - button " Importar CSV/Excel" [ref=e274] [cursor=pointer]:
                - generic [ref=e275]: 
                - generic [ref=e276]: Importar CSV/Excel
              - button " Plantilla" [ref=e278] [cursor=pointer]:
                - generic [ref=e279]: 
                - generic [ref=e280]: Plantilla
            - button " Nuevo alumno" [ref=e282] [cursor=pointer]:
              - generic [ref=e283]: 
              - generic [ref=e284]: Nuevo alumno
        - generic [ref=e287]:
          - generic [ref=e288]: 
          - textbox "Buscar alumno..." [ref=e289]
        - generic [ref=e291]:
          - generic [ref=e292]:
            - generic [ref=e293]:
              - button "" [ref=e295] [cursor=pointer]:
                - generic [ref=e296]: 
              - button "" [ref=e298] [cursor=pointer]:
                - generic [ref=e299]: 
            - generic [ref=e301]: 0 registro(s)
          - generic [ref=e302]:
            - table [ref=e304]:
              - rowgroup [ref=e305]:
                - row "Matrícula Nombre Completo CURP NSS Nivel Grado Grupo Ingreso Acciones" [ref=e306]:
                  - columnheader "Matrícula" [ref=e307] [cursor=pointer]:
                    - generic [ref=e308]:
                      - generic [ref=e309]: Matrícula
                      - img [ref=e311]
                    - generic [ref=e318]:
                      - combobox "Filtrar..." [ref=e319]
                      - button [ref=e320]:
                        - img [ref=e321]
                  - columnheader "Nombre Completo" [ref=e323] [cursor=pointer]:
                    - generic [ref=e324]:
                      - generic [ref=e325]: Nombre Completo
                      - img [ref=e327]
                    - generic [ref=e334]:
                      - combobox "Filtrar..." [ref=e335]
                      - button [ref=e336]:
                        - img [ref=e337]
                  - columnheader "CURP" [ref=e339] [cursor=pointer]:
                    - generic [ref=e340]:
                      - generic [ref=e341]: CURP
                      - img [ref=e343]
                    - generic [ref=e350]:
                      - combobox "Filtrar..." [ref=e351]
                      - button [ref=e352]:
                        - img [ref=e353]
                  - columnheader "NSS" [ref=e355] [cursor=pointer]:
                    - generic [ref=e357]: NSS
                  - columnheader "Nivel" [ref=e358] [cursor=pointer]:
                    - generic [ref=e359]:
                      - generic [ref=e360]: Nivel
                      - img [ref=e362]
                    - generic [ref=e369]:
                      - combobox "Filtrar..." [ref=e370]
                      - button [ref=e371]:
                        - img [ref=e372]
                  - columnheader "Grado" [ref=e374] [cursor=pointer]:
                    - generic [ref=e375]:
                      - generic [ref=e376]: Grado
                      - img [ref=e378]
                    - generic [ref=e385]:
                      - combobox "Filtrar..." [ref=e386]
                      - button [ref=e387]:
                        - img [ref=e388]
                  - columnheader "Grupo" [ref=e390] [cursor=pointer]:
                    - generic [ref=e391]:
                      - generic [ref=e392]: Grupo
                      - img [ref=e394]
                    - generic [ref=e401]:
                      - combobox "Filtrar..." [ref=e402]
                      - button [ref=e403]:
                        - img [ref=e404]
                  - columnheader "Ingreso" [ref=e406] [cursor=pointer]:
                    - generic [ref=e407]:
                      - generic [ref=e408]: Ingreso
                      - img [ref=e410]
                  - columnheader "Acciones" [ref=e416]:
                    - strong [ref=e417]: Acciones
              - rowgroup [ref=e418]:
                - row "Sin registros" [ref=e419]:
                  - cell "Sin registros" [ref=e420]
            - generic [ref=e421]:
              - button "Primera página":
                - img
              - button [disabled]:
                - img
              - button "Página siguiente" [disabled]:
                - img
              - button "Última página" [disabled]:
                - img
              - generic:
                - combobox "Filas por página" [disabled]: "20"
                - button "dropdown trigger":
                  - img
        - generic "Nuevo Alumno":
          - dialog "Nuevo Alumno" [ref=e423]:
            - generic [ref=e425]:
              - generic [ref=e426]: Nuevo Alumno
              - button [ref=e429] [cursor=pointer]:
                - img [ref=e430]
            - generic [ref=e433]:
              - generic [ref=e434]:
                - generic [ref=e435]: Nombre(s) *
                - textbox [ref=e436]: Pedro
              - generic [ref=e437]:
                - generic [ref=e438]: Apellido paterno *
                - textbox [ref=e439]: López
              - generic [ref=e440]:
                - generic [ref=e441]: Apellido materno
                - textbox [ref=e442]
              - generic [ref=e443]:
                - generic [ref=e444]: CURP (18 caracteres) *
                - textbox [ref=e445]
              - paragraph [ref=e446]: Una vez creado podrás completar el expediente completo desde el perfil.
            - button "Crear alumno" [active] [ref=e449] [cursor=pointer]:
              - generic [ref=e450]: Crear alumno
```

# Test source

```ts
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
  45  |     await expect(page.locator('.p-toast-message-success')).toBeVisible({ timeout: 8_000 });
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
> 116 |     await expect(page.locator('.p-toast-message-warn, .p-toast-message-error')).toBeVisible({ timeout: 5_000 });
      |                                                                                 ^ Error: expect(locator).toBeVisible() failed
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
  146 |     await ap.openNewForm();
  147 |     await ap.fillAndBlur(ap.curpInput, curpValido());
  148 |     await ap.fillAndBlur(ap.nombreInput, 'Ana');
  149 |     // Navega atrás antes de guardar
  150 |     await page.goBack();
  151 |     // Debe mostrar confirmación o simplemente salir sin crash
  152 |     await page.waitForTimeout(1_000);
  153 |     // La app no debe crashear
  154 |     await expect(page).not.toHaveURL(/error/);
  155 |   });
  156 | 
  157 |   test('ALU-C2 | recarga en medio de búsqueda', async ({ page }) => {
  158 |     const ap = await setupAlumnos(page);
  159 |     await ap.searchInput.fill('Garci');
  160 |     await page.reload();
  161 |     // Lista debe cargar correctamente después del reload
  162 |     await ap.waitSpinner();
  163 |     await expect(ap.table).toBeVisible();
  164 |   });
  165 | 
  166 |   test('ALU-C3 | click múltiple en "Nuevo" no duplica el dialog', async ({ page }) => {
  167 |     const ap = await setupAlumnos(page);
  168 |     // Primer click abre el dialog
  169 |     await ap.newBtn.click();
  170 |     await page.waitForTimeout(200);
  171 |     // Clicks adicionales no deben crashear (btn puede estar dentro del dialog)
  172 |     await ap.newBtn.click({ force: true }).catch(() => undefined);
  173 |     await page.waitForTimeout(300);
  174 |     // Solo debe haber 1 dialog abierto (no duplicados)
  175 |     const dialogs = await page.locator('.apex-dialog-wrapper').count();
  176 |     expect(dialogs).toBeLessThanOrEqual(1);
  177 |     // Cerrar el dialog
  178 |     await page.keyboard.press('Escape');
  179 |   });
  180 | 
  181 |   test('ALU-C4 | scroll rápido en tabla con 1000+ filas', async ({ page }) => {
  182 |     const ap = await setupAlumnos(page);
  183 |     await ap.searchFor(''); // Sin filtro para ver todos
  184 |     await ap.waitSpinner();
  185 |     // Scroll rápido hacia abajo y arriba
  186 |     await page.keyboard.press('End');
  187 |     await page.waitForTimeout(300);
  188 |     await page.keyboard.press('Home');
  189 |     await expect(ap.table).toBeVisible();
  190 |   });
  191 | 
  192 |   test('ALU-C5 | pegar en campo CURP y cambiar inmediatamente de campo', async ({ page }) => {
  193 |     const ap = await setupAlumnos(page);
  194 |     await ap.openNewForm();
  195 |     // Pegar CURP válida
  196 |     await ap.curpInput.click();
  197 |     await page.keyboard.type(curpValido());
  198 |     // Cambiar inmediatamente sin esperar validación
  199 |     await ap.nombreInput.click();
  200 |     await ap.nombreInput.fill('Test');
  201 |     // La CURP debe haberse mantenido
  202 |     const curpVal = await ap.curpInput.inputValue();
  203 |     expect(curpVal.length).toBeGreaterThan(10);
  204 |   });
  205 | });
  206 | 
  207 | // ── D. Usuario caótico ────────────────────────────────────────────────────────
  208 | 
  209 | test.describe('D. Usuario caótico — inputs extremos', () => {
  210 |   const extremeInputs = [
  211 |     { label: 'SQL injection',   value: EDGE_STRINGS.SQL_INJECTION },
  212 |     { label: 'XSS básico',      value: EDGE_STRINGS.XSS_BASIC },
  213 |     { label: 'Emojis',          value: EDGE_STRINGS.EMOJIS },
  214 |     { label: '1000 chars',      value: EDGE_STRINGS.LONG_1000 },
  215 |     { label: 'Unicode mix',     value: EDGE_STRINGS.UNICODE_MIX },
  216 |     { label: 'Solo espacios',   value: EDGE_STRINGS.SPACE_ONLY },
```