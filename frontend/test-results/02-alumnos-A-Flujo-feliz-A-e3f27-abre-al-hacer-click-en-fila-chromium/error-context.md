# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: 02-alumnos.spec.ts >> A. Flujo feliz >> ALU-04 | perfil alumno abre al hacer click en fila
- Location: e2e/tests/02-alumnos.spec.ts:48:7

# Error details

```
Test timeout of 30000ms exceeded.
```

```
Error: locator.click: Test timeout of 30000ms exceeded.
Call log:
  - waiting for locator('tr.data-row, .p-datatable-row, [data-testid="grid-row"]').first()

```

# Page snapshot

```yaml
- generic [ref=e3]:
  - toolbar [ref=e4]:
    - generic [ref=e5]:
      - generic [ref=e6]:
        - generic [ref=e7]: "N"
        - generic [ref=e8]:
          - generic [ref=e9]: ADES
          - generic [ref=e10]: Instituto Nevadi
      - generic [ref=e12] [cursor=pointer]:
        - combobox "— Todo el Instituto —" [ref=e13]
        - button "dropdown trigger" [ref=e14]:
          - img [ref=e15]
      - generic [ref=e17]: /
      - generic [ref=e18] [cursor=pointer]:
        - combobox "TODOS" [ref=e19]
        - button "dropdown trigger" [ref=e20]:
          - img [ref=e21]
      - generic [ref=e23]: /
      - generic [ref=e24] [cursor=pointer]:
        - combobox "2026-2027" [ref=e25]
        - button "dropdown trigger" [ref=e26]:
          - img [ref=e27]
    - generic [ref=e29]:
      - generic [ref=e31] [cursor=pointer]: 
      - generic [ref=e32] [cursor=pointer]:
        - generic [ref=e33]: T
        - generic [ref=e34]:
          - generic [ref=e35]: Test COORDINADOR_ACADEMICO
          - generic [ref=e36]: Coord. Académico
        - generic [ref=e37]: 
  - generic [ref=e38]:
    - navigation [ref=e39]:
      - generic [ref=e40]: Principal
      - list [ref=e41]:
        - listitem [ref=e42]:
          - link " Dashboard" [ref=e43] [cursor=pointer]:
            - /url: /dashboard
            - generic [ref=e44]: 
            - generic [ref=e45]: Dashboard
      - generic [ref=e46]: Académico
      - list [ref=e47]:
        - listitem [ref=e48]:
          - link " Alumnos" [ref=e49] [cursor=pointer]:
            - /url: /alumnos
            - generic [ref=e50]: 
            - generic [ref=e51]: Alumnos
        - listitem [ref=e52]:
          - link " Reinscripción" [ref=e53] [cursor=pointer]:
            - /url: /reinscripcion
            - generic [ref=e54]: 
            - generic [ref=e55]: Reinscripción
        - listitem [ref=e56]:
          - link " Profesores" [ref=e57] [cursor=pointer]:
            - /url: /profesores
            - generic [ref=e58]: 
            - generic [ref=e59]: Profesores
        - listitem [ref=e60]:
          - link " Grupos" [ref=e61] [cursor=pointer]:
            - /url: /grupos
            - generic [ref=e62]: 
            - generic [ref=e63]: Grupos
        - listitem [ref=e64]:
          - link " Aulas" [ref=e65] [cursor=pointer]:
            - /url: /aulas
            - generic [ref=e66]: 
            - generic [ref=e67]: Aulas
        - listitem [ref=e68]:
          - link " Planes de Estudio" [ref=e69] [cursor=pointer]:
            - /url: /planes-estudio
            - generic [ref=e70]: 
            - generic [ref=e71]: Planes de Estudio
        - listitem [ref=e72]:
          - link " Calificaciones" [ref=e73] [cursor=pointer]:
            - /url: /calificaciones
            - generic [ref=e74]: 
            - generic [ref=e75]: Calificaciones
        - listitem [ref=e76]:
          - link " Evaluaciones" [ref=e77] [cursor=pointer]:
            - /url: /evaluaciones
            - generic [ref=e78]: 
            - generic [ref=e79]: Evaluaciones
        - listitem [ref=e80]:
          - link " Asistencias" [ref=e81] [cursor=pointer]:
            - /url: /asistencias
            - generic [ref=e82]: 
            - generic [ref=e83]: Asistencias
        - listitem [ref=e84]:
          - link " Tareas" [ref=e85] [cursor=pointer]:
            - /url: /tareas
            - generic [ref=e86]: 
            - generic [ref=e87]: Tareas
        - listitem [ref=e88]:
          - link " Planeación" [ref=e89] [cursor=pointer]:
            - /url: /planeacion
            - generic [ref=e90]: 
            - generic [ref=e91]: Planeación
      - generic [ref=e92]: Operaciones
      - list [ref=e93]:
        - listitem [ref=e94]:
          - link " Horarios" [ref=e95] [cursor=pointer]:
            - /url: /horarios
            - generic [ref=e96]: 
            - generic [ref=e97]: Horarios
        - listitem [ref=e98]:
          - link " Calendario Escolar" [ref=e99] [cursor=pointer]:
            - /url: /calendario
            - generic [ref=e100]: 
            - generic [ref=e101]: Calendario Escolar
        - listitem [ref=e102]:
          - link " Conducta" [ref=e103] [cursor=pointer]:
            - /url: /conducta
            - generic [ref=e104]: 
            - generic [ref=e105]: Conducta
        - listitem [ref=e106]:
          - link " Expediente Médico" [ref=e107] [cursor=pointer]:
            - /url: /medico
            - generic [ref=e108]: 
            - generic [ref=e109]: Expediente Médico
        - listitem [ref=e110]:
          - link " Condiciones Crónicas" [ref=e111] [cursor=pointer]:
            - /url: /condiciones-cronicas
            - generic [ref=e112]: 
            - generic [ref=e113]: Condiciones Crónicas
        - listitem [ref=e114]:
          - link " Justificaciones Faltas" [ref=e115] [cursor=pointer]:
            - /url: /justificaciones
            - generic [ref=e116]: 
            - generic [ref=e117]: Justificaciones Faltas
        - listitem [ref=e118]:
          - link " Movilidad Estudiantil" [ref=e119] [cursor=pointer]:
            - /url: /movilidad
            - generic [ref=e120]: 
            - generic [ref=e121]: Movilidad Estudiantil
        - listitem [ref=e122]:
          - link " Optativas" [ref=e123] [cursor=pointer]:
            - /url: /optativas
            - generic [ref=e124]: 
            - generic [ref=e125]: Optativas
        - listitem [ref=e126]:
          - link " Admisión" [ref=e127] [cursor=pointer]:
            - /url: /admision
            - generic [ref=e128]: 
            - generic [ref=e129]: Admisión
      - generic [ref=e130]: Comunicación
      - list [ref=e131]:
        - listitem [ref=e132]:
          - link " Comunicados" [ref=e133] [cursor=pointer]:
            - /url: /comunicados
            - generic [ref=e134]: 
            - generic [ref=e135]: Comunicados
        - listitem [ref=e136]:
          - link " Foros y Anuncios" [ref=e137] [cursor=pointer]:
            - /url: /foros
            - generic [ref=e138]: 
            - generic [ref=e139]: Foros y Anuncios
        - listitem [ref=e140]:
          - link " Encuestas" [ref=e141] [cursor=pointer]:
            - /url: /encuestas
            - generic [ref=e142]: 
            - generic [ref=e143]: Encuestas
      - generic [ref=e144]: Gradebook
      - list [ref=e145]:
        - listitem [ref=e146]:
          - link " Gradebook" [ref=e147] [cursor=pointer]:
            - /url: /gradebook
            - generic [ref=e148]: 
            - generic [ref=e149]: Gradebook
        - listitem [ref=e150]:
          - link " Mi Progreso" [ref=e151] [cursor=pointer]:
            - /url: /mi-progreso
            - generic [ref=e152]: 
            - generic [ref=e153]: Mi Progreso
        - listitem [ref=e154]:
          - link " Ponderaciones" [ref=e155] [cursor=pointer]:
            - /url: /ponderacion-config
            - generic [ref=e156]: 
            - generic [ref=e157]: Ponderaciones
      - generic [ref=e158]: Recursos
      - list [ref=e159]:
        - listitem [ref=e160]:
          - link " Rúbricas" [ref=e161] [cursor=pointer]:
            - /url: /rubricas
            - generic [ref=e162]: 
            - generic [ref=e163]: Rúbricas
        - listitem [ref=e164]:
          - link " Insignias" [ref=e165] [cursor=pointer]:
            - /url: /badges
            - generic [ref=e166]: 
            - generic [ref=e167]: Insignias
        - listitem [ref=e168]:
          - link " Portal Alumno" [ref=e169] [cursor=pointer]:
            - /url: /portal
            - generic [ref=e170]: 
            - generic [ref=e171]: Portal Alumno
      - generic [ref=e172]: Inteligencia
      - list [ref=e173]:
        - listitem [ref=e174]:
          - link " Dashboards BI" [ref=e175] [cursor=pointer]:
            - /url: /bi
            - generic [ref=e176]: 
            - generic [ref=e177]: Dashboards BI
        - listitem [ref=e178]:
          - link " Grade Analytics" [ref=e179] [cursor=pointer]:
            - /url: /grade-analytics
            - generic [ref=e180]: 
            - generic [ref=e181]: Grade Analytics
        - listitem [ref=e182]:
          - link " Asistente IA + Datos" [ref=e183] [cursor=pointer]:
            - /url: /ia
            - generic [ref=e184]: 
            - generic [ref=e185]: Asistente IA + Datos
        - listitem [ref=e186]:
          - link " Eval. Docente 360°" [ref=e187] [cursor=pointer]:
            - /url: /eval-docente
            - generic [ref=e188]: 
            - generic [ref=e189]: Eval. Docente 360°
        - listitem [ref=e190]:
          - link " Learning Paths" [ref=e191] [cursor=pointer]:
            - /url: /learning-paths
            - generic [ref=e192]: 
            - generic [ref=e193]: Learning Paths
      - generic [ref=e194]: Reportes
      - list [ref=e195]:
        - listitem [ref=e196]:
          - link " Generador de Reportes" [ref=e197] [cursor=pointer]:
            - /url: /reportes
            - generic [ref=e198]: 
            - generic [ref=e199]: Generador de Reportes
        - listitem [ref=e200]:
          - link " Certificados Digitales" [ref=e201] [cursor=pointer]:
            - /url: /certificados
            - generic [ref=e202]: 
            - generic [ref=e203]: Certificados Digitales
        - listitem [ref=e204]:
          - link " Expediente Digital" [ref=e205] [cursor=pointer]:
            - /url: /expediente-doc
            - generic [ref=e206]: 
            - generic [ref=e207]: Expediente Digital
      - generic [ref=e208]: Ayuda
      - list [ref=e209]:
        - listitem [ref=e210]:
          - link " Manual de Usuario" [ref=e211] [cursor=pointer]:
            - /url: /ayuda
            - generic [ref=e212]: 
            - generic [ref=e213]: Manual de Usuario
    - main [ref=e214]:
      - navigation "Breadcrumb" [ref=e217]:
        - list [ref=e218]:
          - listitem [ref=e219]:
            - link "Home" [ref=e220] [cursor=pointer]:
              - /url: /
          - listitem [ref=e221]:
            - generic [ref=e222]: 
            - generic [ref=e223]: Alumnos
      - generic [ref=e224]:
        - generic [ref=e225]:
          - generic [ref=e226]:
            - heading "Alumnos" [level=2] [ref=e227]
            - paragraph [ref=e228]: 0 alumno(s) registrado(s)
          - generic [ref=e229]:
            - button "" [ref=e232] [cursor=pointer]:
              - generic [ref=e233]: 
            - button " CSV" [ref=e235] [cursor=pointer]:
              - generic [ref=e236]: 
              - generic [ref=e237]: CSV
            - button " Excel" [ref=e239] [cursor=pointer]:
              - generic [ref=e240]: 
              - generic [ref=e241]: Excel
            - generic [ref=e243]:
              - button " Importar CSV/Excel" [ref=e245] [cursor=pointer]:
                - generic [ref=e246]: 
                - generic [ref=e247]: Importar CSV/Excel
              - button " Plantilla" [ref=e249] [cursor=pointer]:
                - generic [ref=e250]: 
                - generic [ref=e251]: Plantilla
            - button " Nuevo alumno" [ref=e253] [cursor=pointer]:
              - generic [ref=e254]: 
              - generic [ref=e255]: Nuevo alumno
        - generic [ref=e258]:
          - generic [ref=e259]: 
          - textbox "Buscar alumno..." [ref=e260]
        - generic [ref=e262]:
          - generic [ref=e263]:
            - generic [ref=e264]:
              - button "" [ref=e266] [cursor=pointer]:
                - generic [ref=e267]: 
              - button "" [ref=e269] [cursor=pointer]:
                - generic [ref=e270]: 
            - generic [ref=e272]: 0 registro(s)
          - generic [ref=e273]:
            - table [ref=e275]:
              - rowgroup [ref=e276]:
                - row "Matrícula Nombre Completo CURP NSS Nivel Grado Grupo Ingreso Acciones" [ref=e277]:
                  - columnheader "Matrícula" [ref=e278] [cursor=pointer]:
                    - generic [ref=e279]:
                      - generic [ref=e280]: Matrícula
                      - img [ref=e282]
                    - generic [ref=e289]:
                      - combobox "Filtrar..." [ref=e290]
                      - button [ref=e291]:
                        - img [ref=e292]
                  - columnheader "Nombre Completo" [ref=e294] [cursor=pointer]:
                    - generic [ref=e295]:
                      - generic [ref=e296]: Nombre Completo
                      - img [ref=e298]
                    - generic [ref=e305]:
                      - combobox "Filtrar..." [ref=e306]
                      - button [ref=e307]:
                        - img [ref=e308]
                  - columnheader "CURP" [ref=e310] [cursor=pointer]:
                    - generic [ref=e311]:
                      - generic [ref=e312]: CURP
                      - img [ref=e314]
                    - generic [ref=e321]:
                      - combobox "Filtrar..." [ref=e322]
                      - button [ref=e323]:
                        - img [ref=e324]
                  - columnheader "NSS" [ref=e326] [cursor=pointer]:
                    - generic [ref=e328]: NSS
                  - columnheader "Nivel" [ref=e329] [cursor=pointer]:
                    - generic [ref=e330]:
                      - generic [ref=e331]: Nivel
                      - img [ref=e333]
                    - generic [ref=e340]:
                      - combobox "Filtrar..." [ref=e341]
                      - button [ref=e342]:
                        - img [ref=e343]
                  - columnheader "Grado" [ref=e345] [cursor=pointer]:
                    - generic [ref=e346]:
                      - generic [ref=e347]: Grado
                      - img [ref=e349]
                    - generic [ref=e356]:
                      - combobox "Filtrar..." [ref=e357]
                      - button [ref=e358]:
                        - img [ref=e359]
                  - columnheader "Grupo" [ref=e361] [cursor=pointer]:
                    - generic [ref=e362]:
                      - generic [ref=e363]: Grupo
                      - img [ref=e365]
                    - generic [ref=e372]:
                      - combobox "Filtrar..." [ref=e373]
                      - button [ref=e374]:
                        - img [ref=e375]
                  - columnheader "Ingreso" [ref=e377] [cursor=pointer]:
                    - generic [ref=e378]:
                      - generic [ref=e379]: Ingreso
                      - img [ref=e381]
                  - columnheader "Acciones" [ref=e387]:
                    - strong [ref=e388]: Acciones
              - rowgroup [ref=e389]:
                - row "Sin registros" [ref=e390]:
                  - cell "Sin registros" [ref=e391]
            - generic [ref=e392]:
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
        - generic "Nuevo Alumno"
```

# Test source

```ts
  1   | import { Page, expect } from '@playwright/test';
  2   | import { BasePage } from './base-page';
  3   | 
  4   | export class AlumnosPage extends BasePage {
  5   |   // Lista principal — apex-interactive-grid
  6   |   readonly table    = this.page.locator('app-interactive-grid, [data-testid="tabla-alumnos"], p-table').first();
  7   |   readonly rows     = this.page.locator('tr.data-row, .p-datatable-row, [data-testid="grid-row"]');
  8   | 
  9   |   // Búsqueda — apex-search component renders <input class="apex-search-input">
  10  |   readonly searchInput = this.page.locator('input.apex-search-input, [data-testid="search-alumnos"]');
  11  | 
  12  |   // Botones
  13  |   readonly newBtn     = this.page.locator('button:has-text("Nuevo alumno"), button:has-text("Nuevo"), [data-testid="btn-nuevo"]');
  14  |   readonly importBtn  = this.page.locator('[data-testid="btn-importar"], button:has-text("Importar")');
  15  | 
  16  |   // Dialog de alta rápida (apex-modal-dialog → .apex-dialog)
  17  |   // Los inputs están en orden: Nombre, Ap. Paterno, Ap. Materno, CURP
  18  |   readonly dlgInputs     = this.page.locator('.apex-dialog input, apex-modal-dialog input');
  19  |   readonly nombreInput   = this.dlgInputs.nth(0);
  20  |   readonly apPaternoInput = this.dlgInputs.nth(1);
  21  |   readonly apMaternoInput = this.dlgInputs.nth(2);
  22  |   readonly curpInput     = this.page.locator('.apex-dialog input[maxlength="18"], input[maxlength="18"]');
  23  | 
  24  |   // Fecha nacimiento — el formulario básico de alta no lo incluye, locator seguro para fuzz
  25  |   readonly fechaNacInput = this.page.locator(
  26  |     'input[type="date"], [data-testid="fecha-nacimiento"], .apex-dialog input[type="date"]'
  27  |   ).first();
  28  | 
  29  |   // Botón Guardar/Crear dentro del dialog
  30  |   readonly saveBtn = this.page.locator(
  31  |     'button:has-text("Crear alumno"), button:has-text("Guardar"), [data-testid="btn-guardar"]'
  32  |   );
  33  | 
  34  |   async navigate() {
  35  |     await this.page.goto('/alumnos');
  36  |     await this.waitSpinner();
  37  |   }
  38  | 
  39  |   async openNewForm() {
  40  |     await this.newBtn.click();
  41  |     // Esperar a que el dialog de apex-modal-dialog aparezca
  42  |     await expect(this.page.locator('.apex-dialog, [role="dialog"]')).toBeVisible({ timeout: 5_000 });
  43  |     await this.page.waitForTimeout(300); // pequeña pausa para animación del dialog
  44  |   }
  45  | 
  46  |   async fillAlumnoForm(data: {
  47  |     curp: string;
  48  |     nombre: string;
  49  |     apellido_paterno: string;
  50  |     apellido_materno?: string;
  51  |     fecha_nacimiento?: string;
  52  |   }) {
  53  |     await this.fillAndBlur(this.nombreInput,    data.nombre);
  54  |     await this.fillAndBlur(this.apPaternoInput, data.apellido_paterno);
  55  |     if (data.apellido_materno) {
  56  |       await this.fillAndBlur(this.apMaternoInput, data.apellido_materno);
  57  |     }
  58  |     await this.fillAndBlur(this.curpInput, data.curp);
  59  |   }
  60  | 
  61  |   async save() {
  62  |     await this.saveBtn.click();
  63  |   }
  64  | 
  65  |   async saveAndExpectSuccess() {
  66  |     await this.save();
  67  |     await this.waitForToast('success');
  68  |   }
  69  | 
  70  |   async saveAndExpectError(message?: string) {
  71  |     await this.save();
  72  |     await this.waitForToast('error');
  73  |     if (message) {
  74  |       await expect(this.page.locator('.p-toast-detail')).toContainText(message);
  75  |     }
  76  |   }
  77  | 
  78  |   async searchFor(query: string) {
  79  |     await this.searchInput.fill(query);
  80  |     await this.page.waitForTimeout(400);
  81  |     await this.waitSpinner();
  82  |   }
  83  | 
  84  |   async expectRowCount(min: number) {
  85  |     const count = await this.rows.count();
  86  |     expect(count).toBeGreaterThanOrEqual(min);
  87  |   }
  88  | 
  89  |   async getFirstRowText(): Promise<string> {
  90  |     return (await this.rows.first().textContent()) ?? '';
  91  |   }
  92  | 
  93  |   async clickFirstRow() {
> 94  |     await this.rows.first().click();
      |                             ^ Error: locator.click: Test timeout of 30000ms exceeded.
  95  |   }
  96  | 
  97  |   async uploadCsv(filePath: string) {
  98  |     const input = this.page.locator('input[type="file"]');
  99  |     await this.importBtn.click();
  100 |     await input.setInputFiles(filePath);
  101 |     await this.page.locator('button:has-text("Importar")').last().click();
  102 |   }
  103 | }
  104 | 
```