# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: 02-alumnos.spec.ts >> D. Usuario caótico — inputs extremos >> ALU-D | campo nombre: XSS básico
- Location: e2e/tests/02-alumnos.spec.ts:222:9

# Error details

```
Test timeout of 30000ms exceeded.
```

```
Error: locator.click: Test timeout of 30000ms exceeded.
Call log:
  - waiting for locator('button:has-text("Crear alumno"), button:has-text("Guardar"), [data-testid="btn-guardar"]')
    - locator resolved to <button disabled pc195="" pc196="" pc197="" data-p="" pripple="" type="button" autofocus="true" data-pc-name="button" data-p-disabled="true" data-pc-section="root" class="p-ripple p-button p-component">…</button>
  - attempting click action
    2 × waiting for element to be visible, enabled and stable
      - element is not enabled
    - retrying click action
    - waiting 20ms
    2 × waiting for element to be visible, enabled and stable
      - element is not enabled
    - retrying click action
      - waiting 100ms
    44 × waiting for element to be visible, enabled and stable
       - element is not enabled
     - retrying click action
       - waiting 500ms

```

# Page snapshot

```yaml
- generic [ref=e1]:
  - generic [ref=e3]:
    - status [ref=e4]
    - toolbar [ref=e5]:
      - generic [ref=e6]:
        - generic [ref=e7]:
          - generic [ref=e8]: "N"
          - generic [ref=e9]:
            - generic [ref=e10]: ADES
            - generic [ref=e11]: Instituto Nevadi
        - generic [ref=e13] [cursor=pointer]:
          - combobox "Plantel..." [ref=e14]
          - button "dropdown trigger" [ref=e15]:
            - img [ref=e16]
        - generic [ref=e18]: /
        - generic [ref=e19] [cursor=pointer]:
          - combobox "Nivel..." [ref=e20]
          - button "dropdown trigger" [ref=e21]:
            - img [ref=e22]
        - generic [ref=e24]: /
        - generic [ref=e25] [cursor=pointer]:
          - combobox "Ciclo..." [ref=e26]
          - button "dropdown trigger" [ref=e27]:
            - img [ref=e28]
        - generic [ref=e30]: /
        - generic [ref=e31] [cursor=pointer]:
          - combobox "Grado..." [ref=e32]
          - button "dropdown trigger" [ref=e33]:
            - img [ref=e34]
        - generic [ref=e36]: /
        - generic [ref=e37] [cursor=pointer]:
          - combobox "Grupo..." [ref=e38]
          - button "dropdown trigger" [ref=e39]:
            - img [ref=e40]
      - generic [ref=e42]:
        - button "Notificaciones" [ref=e43] [cursor=pointer]:
          - generic [ref=e44]: 
        - button "Cuenta de Test COORDINADOR_ACADEMICO" [ref=e45] [cursor=pointer]:
          - generic [ref=e46]: T
          - generic [ref=e47]:
            - generic [ref=e48]: Test COORDINADOR_ACADEMICO
            - generic [ref=e49]: Coord. Académico
          - generic [ref=e50]: 
    - generic [ref=e51]:
      - navigation "Navegación principal" [ref=e52]:
        - generic [ref=e53]: Principal
        - list [ref=e54]:
          - listitem [ref=e55]:
            - link " Dashboard" [ref=e56] [cursor=pointer]:
              - /url: /dashboard
              - generic [ref=e57]: 
              - generic [ref=e58]: Dashboard
        - generic [ref=e59]: Académico
        - list [ref=e60]:
          - listitem [ref=e61]:
            - link " Alumnos" [ref=e62] [cursor=pointer]:
              - /url: /alumnos
              - generic [ref=e63]: 
              - generic [ref=e64]: Alumnos
          - listitem [ref=e65]:
            - link " Reinscripción" [ref=e66] [cursor=pointer]:
              - /url: /reinscripcion
              - generic [ref=e67]: 
              - generic [ref=e68]: Reinscripción
          - listitem [ref=e69]:
            - link " Profesores" [ref=e70] [cursor=pointer]:
              - /url: /profesores
              - generic [ref=e71]: 
              - generic [ref=e72]: Profesores
          - listitem [ref=e73]:
            - link " Grupos" [ref=e74] [cursor=pointer]:
              - /url: /grupos
              - generic [ref=e75]: 
              - generic [ref=e76]: Grupos
          - listitem [ref=e77]:
            - link " Aulas" [ref=e78] [cursor=pointer]:
              - /url: /aulas
              - generic [ref=e79]: 
              - generic [ref=e80]: Aulas
          - listitem [ref=e81]:
            - link " Planes de Estudio" [ref=e82] [cursor=pointer]:
              - /url: /planes-estudio
              - generic [ref=e83]: 
              - generic [ref=e84]: Planes de Estudio
          - listitem [ref=e85]:
            - link " Calificaciones" [ref=e86] [cursor=pointer]:
              - /url: /calificaciones
              - generic [ref=e87]: 
              - generic [ref=e88]: Calificaciones
          - listitem [ref=e89]:
            - link " Evaluaciones" [ref=e90] [cursor=pointer]:
              - /url: /evaluaciones
              - generic [ref=e91]: 
              - generic [ref=e92]: Evaluaciones
          - listitem [ref=e93]:
            - link " Asistencias" [ref=e94] [cursor=pointer]:
              - /url: /asistencias
              - generic [ref=e95]: 
              - generic [ref=e96]: Asistencias
          - listitem [ref=e97]:
            - link " Tareas" [ref=e98] [cursor=pointer]:
              - /url: /tareas
              - generic [ref=e99]: 
              - generic [ref=e100]: Tareas
          - listitem [ref=e101]:
            - link " Planeación" [ref=e102] [cursor=pointer]:
              - /url: /planeacion
              - generic [ref=e103]: 
              - generic [ref=e104]: Planeación
        - generic [ref=e105]: Operaciones
        - list [ref=e106]:
          - listitem [ref=e107]:
            - link " Horarios" [ref=e108] [cursor=pointer]:
              - /url: /horarios
              - generic [ref=e109]: 
              - generic [ref=e110]: Horarios
          - listitem [ref=e111]:
            - link " Calendario Escolar" [ref=e112] [cursor=pointer]:
              - /url: /calendario
              - generic [ref=e113]: 
              - generic [ref=e114]: Calendario Escolar
          - listitem [ref=e115]:
            - link " Conducta" [ref=e116] [cursor=pointer]:
              - /url: /conducta
              - generic [ref=e117]: 
              - generic [ref=e118]: Conducta
          - listitem [ref=e119]:
            - link " Expediente Médico" [ref=e120] [cursor=pointer]:
              - /url: /medico
              - generic [ref=e121]: 
              - generic [ref=e122]: Expediente Médico
          - listitem [ref=e123]:
            - link " Condiciones Crónicas" [ref=e124] [cursor=pointer]:
              - /url: /condiciones-cronicas
              - generic [ref=e125]: 
              - generic [ref=e126]: Condiciones Crónicas
          - listitem [ref=e127]:
            - link " Justificaciones Faltas" [ref=e128] [cursor=pointer]:
              - /url: /justificaciones
              - generic [ref=e129]: 
              - generic [ref=e130]: Justificaciones Faltas
          - listitem [ref=e131]:
            - link " Movilidad Estudiantil" [ref=e132] [cursor=pointer]:
              - /url: /movilidad
              - generic [ref=e133]: 
              - generic [ref=e134]: Movilidad Estudiantil
          - listitem [ref=e135]:
            - link " Biblioteca" [ref=e136] [cursor=pointer]:
              - /url: /biblioteca
              - generic [ref=e137]: 
              - generic [ref=e138]: Biblioteca
          - listitem [ref=e139]:
            - link " Formato 911 SEP" [ref=e140] [cursor=pointer]:
              - /url: /estadistica-911
              - generic [ref=e141]: 
              - generic [ref=e142]: Formato 911 SEP
          - listitem [ref=e143]:
            - link " Kardex UAEMEX" [ref=e144] [cursor=pointer]:
              - /url: /kardex
              - generic [ref=e145]: 
              - generic [ref=e146]: Kardex UAEMEX
          - listitem [ref=e147]:
            - link " Acta Evaluación UAEMEX" [ref=e148] [cursor=pointer]:
              - /url: /acta-evaluacion
              - generic [ref=e149]: 
              - generic [ref=e150]: Acta Evaluación UAEMEX
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
          - listitem [ref=e173]:
            - link " Videoconferencias" [ref=e174] [cursor=pointer]:
              - /url: /videoconferencias
              - generic [ref=e175]: 
              - generic [ref=e176]: Videoconferencias
        - generic [ref=e177]: Gradebook
        - list [ref=e178]:
          - listitem [ref=e179]:
            - link " Gradebook" [ref=e180] [cursor=pointer]:
              - /url: /gradebook
              - generic [ref=e181]: 
              - generic [ref=e182]: Gradebook
          - listitem [ref=e183]:
            - link " Mi Progreso" [ref=e184] [cursor=pointer]:
              - /url: /mi-progreso
              - generic [ref=e185]: 
              - generic [ref=e186]: Mi Progreso
          - listitem [ref=e187]:
            - link " Ponderaciones" [ref=e188] [cursor=pointer]:
              - /url: /ponderacion-config
              - generic [ref=e189]: 
              - generic [ref=e190]: Ponderaciones
        - generic [ref=e191]: Recursos
        - list [ref=e192]:
          - listitem [ref=e193]:
            - link " Rúbricas" [ref=e194] [cursor=pointer]:
              - /url: /rubricas
              - generic [ref=e195]: 
              - generic [ref=e196]: Rúbricas
          - listitem [ref=e197]:
            - link " Insignias" [ref=e198] [cursor=pointer]:
              - /url: /badges
              - generic [ref=e199]: 
              - generic [ref=e200]: Insignias
          - listitem [ref=e201]:
            - link " Portal Alumno" [ref=e202] [cursor=pointer]:
              - /url: /portal
              - generic [ref=e203]: 
              - generic [ref=e204]: Portal Alumno
          - listitem [ref=e205]:
            - link " Contenido H5P" [ref=e206] [cursor=pointer]:
              - /url: /h5p
              - generic [ref=e207]: 
              - generic [ref=e208]: Contenido H5P
        - generic [ref=e209]: Inteligencia
        - list [ref=e210]:
          - listitem [ref=e211]:
            - link " Dashboards BI" [ref=e212] [cursor=pointer]:
              - /url: /bi
              - generic [ref=e213]: 
              - generic [ref=e214]: Dashboards BI
          - listitem [ref=e215]:
            - link " Grade Analytics" [ref=e216] [cursor=pointer]:
              - /url: /grade-analytics
              - generic [ref=e217]: 
              - generic [ref=e218]: Grade Analytics
          - listitem [ref=e219]:
            - link " Asistente IA + Datos" [ref=e220] [cursor=pointer]:
              - /url: /ia
              - generic [ref=e221]: 
              - generic [ref=e222]: Asistente IA + Datos
          - listitem [ref=e223]:
            - link " Eval. Docente 360°" [ref=e224] [cursor=pointer]:
              - /url: /eval-docente
              - generic [ref=e225]: 
              - generic [ref=e226]: Eval. Docente 360°
          - listitem [ref=e227]:
            - link " Learning Paths" [ref=e228] [cursor=pointer]:
              - /url: /learning-paths
              - generic [ref=e229]: 
              - generic [ref=e230]: Learning Paths
        - generic [ref=e231]: Reportes
        - list [ref=e232]:
          - listitem [ref=e233]:
            - link " Generador de Reportes" [ref=e234] [cursor=pointer]:
              - /url: /reportes
              - generic [ref=e235]: 
              - generic [ref=e236]: Generador de Reportes
          - listitem [ref=e237]:
            - link " Certificados Digitales" [ref=e238] [cursor=pointer]:
              - /url: /certificados
              - generic [ref=e239]: 
              - generic [ref=e240]: Certificados Digitales
          - listitem [ref=e241]:
            - link " Expediente Digital" [ref=e242] [cursor=pointer]:
              - /url: /expediente-doc
              - generic [ref=e243]: 
              - generic [ref=e244]: Expediente Digital
        - generic [ref=e245]: Ayuda
        - list [ref=e246]:
          - listitem [ref=e247]:
            - link " Manual de Usuario" [ref=e248] [cursor=pointer]:
              - /url: /ayuda
              - generic [ref=e249]: 
              - generic [ref=e250]: Manual de Usuario
      - main [ref=e251]:
        - navigation "Breadcrumb" [ref=e254]:
          - list [ref=e255]:
            - listitem [ref=e256]:
              - link "Home" [ref=e257] [cursor=pointer]:
                - /url: /
            - listitem [ref=e258]:
              - generic [ref=e259]: 
              - generic [ref=e260]: Alumnos
        - generic [ref=e261]:
          - generic [ref=e262]:
            - generic [ref=e263]:
              - heading "Alumnos" [level=2] [ref=e264]
              - paragraph [ref=e265]: 0 alumno(s) registrado(s)
            - generic [ref=e266]:
              - button "" [ref=e269] [cursor=pointer]:
                - generic [ref=e270]: 
              - button " CSV" [ref=e272] [cursor=pointer]:
                - generic [ref=e273]: 
                - generic [ref=e274]: CSV
              - button " Excel" [ref=e276] [cursor=pointer]:
                - generic [ref=e277]: 
                - generic [ref=e278]: Excel
              - generic [ref=e280]:
                - button " Importar CSV/Excel" [ref=e282] [cursor=pointer]:
                  - generic [ref=e283]: 
                  - generic [ref=e284]: Importar CSV/Excel
                - button " Plantilla" [ref=e286] [cursor=pointer]:
                  - generic [ref=e287]: 
                  - generic [ref=e288]: Plantilla
              - button " Asignar grupo" [ref=e290] [cursor=pointer]:
                - generic [ref=e291]: 
                - generic [ref=e292]: Asignar grupo
              - button " Nuevo alumno" [ref=e294] [cursor=pointer]:
                - generic [ref=e295]: 
                - generic [ref=e296]: Nuevo alumno
          - generic [ref=e301]:
            - generic [ref=e302]: 
            - textbox "Buscar alumno..." [ref=e303]
          - generic [ref=e305]:
            - generic [ref=e306]:
              - generic [ref=e307]:
                - button "Mostrar u ocultar columnas de la tabla" [ref=e309] [cursor=pointer]:
                  - generic [ref=e310]: 
                - button "Descargar datos como archivo CSV" [ref=e312] [cursor=pointer]:
                  - generic [ref=e313]: 
              - generic [ref=e315]: 0 registro(s)
            - generic [ref=e316]:
              - table [ref=e318]:
                - rowgroup [ref=e319]:
                  - row "Matrícula Nombre Completo CURP NSS Plantel Nivel Grado Grupo Ingreso Acciones" [ref=e320]:
                    - columnheader "Matrícula" [ref=e321] [cursor=pointer]:
                      - generic [ref=e322]:
                        - generic [ref=e323]: Matrícula
                        - img [ref=e325]
                      - generic [ref=e332]:
                        - combobox "Filtrar..." [ref=e333]
                        - button [ref=e334]:
                          - img [ref=e335]
                    - columnheader "Nombre Completo" [ref=e337] [cursor=pointer]:
                      - generic [ref=e338]:
                        - generic [ref=e339]: Nombre Completo
                        - img [ref=e341]
                      - generic [ref=e348]:
                        - combobox "Filtrar..." [ref=e349]
                        - button [ref=e350]:
                          - img [ref=e351]
                    - columnheader "CURP" [ref=e353] [cursor=pointer]:
                      - generic [ref=e354]:
                        - generic [ref=e355]: CURP
                        - img [ref=e357]
                      - generic [ref=e364]:
                        - combobox "Filtrar..." [ref=e365]
                        - button [ref=e366]:
                          - img [ref=e367]
                    - columnheader "NSS" [ref=e369] [cursor=pointer]:
                      - generic [ref=e371]: NSS
                    - columnheader "Plantel" [ref=e372] [cursor=pointer]:
                      - generic [ref=e373]:
                        - generic [ref=e374]: Plantel
                        - img [ref=e376]
                      - generic [ref=e383]:
                        - combobox "Filtrar..." [ref=e384]
                        - button [ref=e385]:
                          - img [ref=e386]
                    - columnheader "Nivel" [ref=e388] [cursor=pointer]:
                      - generic [ref=e389]:
                        - generic [ref=e390]: Nivel
                        - img [ref=e392]
                      - generic [ref=e399]:
                        - combobox "Filtrar..." [ref=e400]
                        - button [ref=e401]:
                          - img [ref=e402]
                    - columnheader "Grado" [ref=e404] [cursor=pointer]:
                      - generic [ref=e405]:
                        - generic [ref=e406]: Grado
                        - img [ref=e408]
                      - generic [ref=e415]:
                        - combobox "Filtrar..." [ref=e416]
                        - button [ref=e417]:
                          - img [ref=e418]
                    - columnheader "Grupo" [ref=e420] [cursor=pointer]:
                      - generic [ref=e421]:
                        - generic [ref=e422]: Grupo
                        - img [ref=e424]
                      - generic [ref=e431]:
                        - combobox "Filtrar..." [ref=e432]
                        - button [ref=e433]:
                          - img [ref=e434]
                    - columnheader "Ingreso" [ref=e436] [cursor=pointer]:
                      - generic [ref=e437]:
                        - generic [ref=e438]: Ingreso
                        - img [ref=e440]
                    - columnheader "Acciones" [ref=e446]:
                      - strong [ref=e447]: Acciones
                - rowgroup [ref=e448]:
                  - row "Sin registros" [ref=e449]:
                    - cell "Sin registros" [ref=e450]
              - generic [ref=e451]:
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
          - generic "Asignación masiva de grupo"
  - dialog "Nuevo Alumno" [ref=e453]:
    - generic [ref=e455]:
      - generic [ref=e456]: Nuevo Alumno
      - button [ref=e459] [cursor=pointer]:
        - img [ref=e460]
    - generic [ref=e463]:
      - generic [ref=e465]:
        - generic [ref=e466]: Nombre(s) *
        - 'textbox "Ej: Juan Carlos" [active] [ref=e467]': scriptalertscript
        - generic [ref=e468]: ℹ️ Nombre completo del alumno (se permiten hasta 100 caracteres)
        - generic [ref=e469]: 17 / 100 caracteres
      - generic [ref=e471]:
        - generic [ref=e472]: Apellido paterno *
        - 'textbox "Ej: García" [ref=e473]'
        - generic [ref=e474]: ℹ️ Primer apellido del alumno
        - generic [ref=e475]: 0 / 100 caracteres
      - generic [ref=e477]:
        - generic [ref=e478]: Apellido materno
        - 'textbox "Ej: López" [ref=e479]'
        - generic [ref=e480]: ℹ️ Segundo apellido (opcional)
        - generic [ref=e481]: 0 / 100 caracteres
      - generic [ref=e483]:
        - generic [ref=e484]: CURP *
        - textbox "AAAA999999HAAAAA01" [ref=e485]
        - generic [ref=e486]: "ℹ️ 18 caracteres: 4 letras + 6 dígitos fecha + 1 sexo (H/M/X) + 5 letras + 1 letra/dígito + 1 dígito verificador"
        - generic [ref=e487]: 0 / 18 caracteres
      - paragraph [ref=e488]: Una vez creado podrás completar el expediente completo desde el perfil.
    - button "Crear alumno" [disabled] [ref=e491]:
      - generic [ref=e492]: Crear alumno
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
  35  |     await this.page.goto('/alumnos', { waitUntil: 'domcontentloaded' });
  36  |     await this.waitSpinner();
  37  |     // Esperar a que la tabla o el botón "Nuevo" estén presentes antes de continuar
  38  |     await this.page.waitForSelector(
  39  |       'app-interactive-grid, [data-testid="tabla-alumnos"], p-table, button:has-text("Nuevo")',
  40  |       { timeout: 15_000 }
  41  |     );
  42  |   }
  43  | 
  44  |   async openNewForm() {
  45  |     await this.newBtn.first().waitFor({ state: 'visible', timeout: 10_000 });
  46  |     await this.newBtn.first().click();
  47  |     await expect(this.page.locator('.apex-dialog, [role="dialog"]')).toBeVisible({ timeout: 10_000 });
  48  |     await this.page.waitForTimeout(300);
  49  |   }
  50  | 
  51  |   async fillAlumnoForm(data: {
  52  |     curp: string;
  53  |     nombre: string;
  54  |     apellido_paterno: string;
  55  |     apellido_materno?: string;
  56  |     fecha_nacimiento?: string;
  57  |   }) {
  58  |     await this.fillAndBlur(this.nombreInput,    data.nombre);
  59  |     await this.fillAndBlur(this.apPaternoInput, data.apellido_paterno);
  60  |     if (data.apellido_materno) {
  61  |       await this.fillAndBlur(this.apMaternoInput, data.apellido_materno);
  62  |     }
  63  |     await this.fillAndBlur(this.curpInput, data.curp);
  64  |   }
  65  | 
  66  |   async save() {
> 67  |     await this.saveBtn.click();
      |                        ^ Error: locator.click: Test timeout of 30000ms exceeded.
  68  |   }
  69  | 
  70  |   async saveAndExpectSuccess() {
  71  |     await this.save();
  72  |     await this.waitForToast('success');
  73  |   }
  74  | 
  75  |   async saveAndExpectError(message?: string) {
  76  |     await this.save();
  77  |     await this.waitForToast('error');
  78  |     if (message) {
  79  |       await expect(this.page.locator('.p-toast-detail')).toContainText(message);
  80  |     }
  81  |   }
  82  | 
  83  |   async searchFor(query: string) {
  84  |     await this.searchInput.fill(query);
  85  |     await this.page.waitForTimeout(400);
  86  |     await this.waitSpinner();
  87  |   }
  88  | 
  89  |   async expectRowCount(min: number) {
  90  |     const count = await this.rows.count();
  91  |     expect(count).toBeGreaterThanOrEqual(min);
  92  |   }
  93  | 
  94  |   async getFirstRowText(): Promise<string> {
  95  |     return (await this.rows.first().textContent()) ?? '';
  96  |   }
  97  | 
  98  |   async clickFirstRow() {
  99  |     await this.rows.first().click();
  100 |   }
  101 | 
  102 |   async uploadCsv(filePath: string) {
  103 |     const input = this.page.locator('input[type="file"]');
  104 |     await this.importBtn.click();
  105 |     await input.setInputFiles(filePath);
  106 |     await this.page.locator('button:has-text("Importar")').last().click();
  107 |   }
  108 | }
  109 | 
```